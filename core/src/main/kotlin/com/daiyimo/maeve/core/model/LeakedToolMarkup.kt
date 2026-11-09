package com.daiyimo.maeve.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * 有些模型不走 tool_calls 通道，而是把调用标记直接吐进正文：DeepSeek V3.2 / V4 的
 * DSML 形式，以及 Qwen 一类的 tool_call 代码块。原样显示就是一堆乱码，
 * 还会被当成正文记进历史。
 *
 * 处理分两步，顺序是先还原、再剥离：
 * 1. 认得出是调用的就还原成交式引擎认识的调用（参数按 string 标志决定收成字符串还是 JSON 值）；
 * 2. 还原不出的 DSML 只剥掉标记、留下标记之外的正文；还原不出的 tool_call 块原样保留，
 *    由 AgentLoop 的漏调用检查（LeakedToolCall）退回一次，不当成最终回答交出去。
 *
 * 只在正文里真的出现标记时才动手，正常回答一个字符都不改（见 repair 的 changed 判断）。
 * 回答里成对写出、里面是合法调用 JSON 的 tool_call 块会被当成调用，这是有意的取舍：
 * 把乱码显示给用户的代价更大；调了不存在的工具时，工具引擎照常回报错误。
 */
object LeakedToolMarkup {
    data class Invocation(val name: String, val argsJson: String)

    /** [text] 是剥掉标记后剩下的正文；[calls] 是从标记里还原出来的工具调用（可能为空）。 */
    data class Scan(val text: String, val calls: List<Invocation>)

    /** 竖线有两种写法：全角｜和半角 |，泄漏时还会写成一到两根。 */
    private const val B = "[｜|]"

    /** DSML 开标记，例如 <｜｜DSML｜｜ ；prefix 指最外层那个尖括号。 */
    private fun dsmlOpen(prefix: String) = prefix + B + "{1,2}\\s*DSML\\s*" + B + "{1,2}"

    /** DSML 闭标记，例如 </｜｜DSML｜｜ invoke ；tag 指闭标记里那层标签名。 */
    private fun dsmlClose(tag: String) = "</" + B + "{1,2}\\s*DSML\\s*" + B + "{1,2}\\s*" + tag + "\\s*>"

    private val INVOKE = Regex(
        dsmlOpen("<") + "\\s*invoke\\s+name\\s*=\\s*\"([^\"]*)\"\\s*>(.*?)" + dsmlClose("invoke"),
        RegexOption.DOT_MATCHES_ALL,
    )
    private val PARAM = Regex(
        dsmlOpen("<") + "\\s*parameter\\s+name\\s*=\\s*\"([^\"]*)\"(?:\\s+string\\s*=\\s*\"(true|false)\")?\\s*>(.*?)" +
            dsmlClose("parameter"),
        RegexOption.DOT_MATCHES_ALL,
    )

    /** 剩下的成对或落单标记（外层 calls、function_calls 之类）一律去掉。 */
    private val TAG = Regex("</?" + B + "{1,2}\\s*DSML\\s*" + B + "{1,2}[^>]*>")

    /** Qwen 一类：tool_call 代码块，块里是 name 加 arguments 的 JSON。 */
    private val TOOL_CALL_BLOCK = Regex(
        "<tool_call>\\s*(.*?)\\s*</tool_call>",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val PIPE_TOOL_CALL_BLOCK = Regex(
        "<" + B + "{1,2}tool_call" + B + "{1,2}\\s*>(.*?)</" + B + "{1,2}tool_call" + B + "{1,2}\\s*>",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
    /**
     * 流式里常见：标记刚开了个头（<｜、<｜｜DSML、<｜｜DSML｜｜ invoke name="pho）。
     * 只认竖线开头、接着是 DSML 前几个字母的半个标记；正常文字里的尖括号不算，
     * 比如 a<b 这段讲 DSML。
     */
     */
    private val PARTIAL_TAIL = Regex("^/?" + B + "{1,2}\\s*(?:D(?:S(?:M(?:L.*)?)?)?)?$", RegexOption.DOT_MATCHES_ALL)

    /** 折叠三个以上的连续换行:标记剥掉后留下的空行最多留一个。 */
    private val BLANK_RUNS = Regex("\n{3,}")

    /** 流式预览用：每来一帧就把已经收到的那部分标记剥掉，别让用户看着乱码闪过去。 */
    fun stripMarkup(text: String): String = scan(text).text

    /** 扫一段正文:剥标记、还原调用。没有标记时原样返回,调用为空。 */
    fun scan(text: String): Scan {
        if (text.isEmpty()) return Scan(text, emptyList())
        val calls = mutableListOf<Invocation>()
        var rest = recoverDsml(text, calls)
        rest = extractJsonBlocks(rest, TOOL_CALL_BLOCK, calls)
        rest = extractJsonBlocks(rest, PIPE_TOOL_CALL_BLOCK, calls)
        rest = hidePartialTail(rest)
        if (calls.isEmpty() && rest == text) return Scan(text, emptyList())
        return Scan(tidyBlankLines(rest), calls)
    }

    /** DSML 调用整块还原成 Invocation,剩下的标记一律剥掉。 */
    private fun recoverDsml(text: String, calls: MutableList<Invocation>): String {
        if (!text.contains("DSML", ignoreCase = true)) return text
        for (m in INVOKE.findAll(text)) {
            val name = m.groupValues[1].trim()
            if (name.isEmpty()) continue
            calls += Invocation(name, paramsJson(m.groupValues[2]))
        }
        var out = text
        if (calls.isNotEmpty()) out = out.replace(INVOKE, " ")
        return TAG.replace(out, " ")
    }

    /** 流式预览:末尾还没收全的半个标记(<｜｜DSML｜、<｜…)也别显示出来。 */
    private fun hidePartialTail(text: String): String {
        val lt = text.lastIndexOf('<')
        if (lt < 0) return text
        val tail = text.substring(lt + 1)
        if (tail.contains('>') || !PARTIAL_TAIL.matches(tail)) return text
        return text.substring(0, lt)
    }

    /** 标记剥掉后留下的空行收拾干净;正文内部的换行不动。 */
    private fun tidyBlankLines(text: String): String =
        text.lines().joinToString("\n") { it.trimEnd() }.replace(BLANK_RUNS, "\n\n").trim()

    /** 把一个回复里的正文清洗掉；还原出调用时补上 [Part.ToolCall]，结束时标成 ToolUse。 */
    fun repair(reply: ModelReply): ModelReply {
        if (reply.parts.none { it is Part.Text || it is Part.Thinking }) return reply
        val out = mutableListOf<Part>()
        val calls = mutableListOf<Part.ToolCall>()
        var changed = false
        for (p in reply.parts) {
            when (p) {
                is Part.Thinking -> {
                    // 思考内容里漏出来的标记只剥掉、不当成调用：调用只能从正文里认
                    val cleaned = stripMarkup(p.text)
                    if (cleaned != p.text) changed = true
                    if (cleaned.isNotEmpty()) out += Part.Thinking(cleaned)
                }
                is Part.Text -> {
                    val scan = scan(p.text)
                    if (scan.text != p.text || scan.calls.isNotEmpty()) changed = true
                    if (scan.text.isNotEmpty()) out += Part.Text(scan.text)
                    scan.calls.forEach { c -> calls += Part.ToolCall(callId(c, calls.size), c.name, c.argsJson) }
                }
                else -> out += p
            }
        }
        if (!changed) return reply
        out += calls
        return ModelReply(out, if (calls.isEmpty()) reply.stopReason else StopReason.ToolUse, reply.usage)
    }

    /** 参数块 → 工具参数 JSON。string="false" 的按 JSON 值收，其余按字符串。 */
    private fun paramsJson(block: String): String {
        val matches = PARAM.findAll(block).toList()
        if (matches.isEmpty()) return "{}"
        return buildJsonObject {
            for (m in matches) {
                val name = m.groupValues[1].trim()
                if (name.isEmpty()) continue
                val raw = m.groupValues[3]
                val isString = m.groupValues[2] != "false"
                put(name, if (isString) JsonPrimitive(raw) else jsonValue(raw))
            }
        }.toString()
    }

    private fun jsonValue(raw: String) = try {
        json.parseToJsonElement(raw)
    } catch (e: Exception) {
        JsonPrimitive(raw)
    }

    /** 名字加参数做稳定的调用编号（同一条消息里再按序号区分），不引随机数，便于单测。 */
    private fun callId(c: Invocation, index: Int): String =
        "leaked_" + Integer.toHexString((c.name + "\u0000" + c.argsJson).hashCode()) + "_" + index

    /**
     * 剥掉成对的 tool_call 块：块里能解析出调用的就还原，解析不了的原样留着，
     * 交给 AgentLoop 的漏调用检查（LeakedToolCall）退回重来——免得剥掉之后只剩
     * 前面一句「我查一下」，被当成最终回答交出去。
     */
    private fun extractJsonBlocks(text: String, block: Regex, calls: MutableList<Invocation>): String =
        block.replace(text) { m ->
            val found = invocationsOf(m.groupValues[1])
            if (found.isEmpty()) m.value else " ".also { calls += found }
        }

    /** 块里的 JSON 可能是一个调用对象，也可能是一组；认不出名字的一项直接丢掉。 */
    private fun invocationsOf(body: String): List<Invocation> {
        val items: List<JsonElement> = try {
            when (val element = json.parseToJsonElement(body)) {
                is JsonArray -> element.toList()
                is JsonObject -> listOf(element)
                else -> return emptyList()
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return items.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            Invocation(name, argsJsonOf(obj))
        }
    }

    /** arguments 可能是对象、JSON 字符串或干脆没有,统一收成参数 JSON 文本。 */
    private fun argsJsonOf(obj: JsonObject): String {
        val args = obj["arguments"] ?: obj["parameters"]
        return when (args) {
            null -> "{}"
            is JsonPrimitive -> args.contentOrNull ?: "{}"
            else -> args.toString()
        }
    }
}

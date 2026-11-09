package com.daiyimo.maeve.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 读模型按要求交回的 JSON(核查结论、路线匹配、记忆变更……)。不同模型交回的样子不一样:
 * 套代码块、前后带几句话、先想一段再给、一个对象后面又跟一段带花括号的说明、键名大小写或写法不同。
 * 这里统一容忍这些;实在读不出来时再问一次,明确只要 JSON([completeParsed])。
 */
object ModelJson {
    private val lenient = Json { isLenient = true; ignoreUnknownKeys = true }

    /** 回复里第一个能读成 JSON 对象的、括号配平的片段(字符串里的括号不算);没有为 null。 */
    fun firstObject(text: String): JsonObject? {
        var from = text.indexOf('{')
        while (from >= 0) {
            balancedEnd(text, from).takeIf { it > from }?.let { end ->
                parseObject(text, from, end)?.let { return it }
            }
            from = text.indexOf('{', from + 1)
        }
        return null
    }

    /** 把 [from, end] 这段(含两端括号)按宽松 JSON 读;读不成对象或干脆解析失败都为 null。 */
    private fun parseObject(text: String, from: Int, end: Int): JsonObject? =
        try {
            lenient.parseToJsonElement(text.substring(from, end + 1)) as? JsonObject
        } catch (e: Exception) {
            null
        }

    /** 从 [start] 的 { 起,配平的 } 的位置;配不平为 -1。 */
    private fun balancedEnd(s: String, start: Int): Int {
        val scan = BraceScan()
        for (i in start until s.length) {
            val c = s[i]
            if (scan.inString) scan.stepInsideString(c) else if (scan.stepOutsideString(c)) return i
        }
        return -1
    }

    /** 配平扫描走到一半的状态:括号深度、在不在字符串里、字符串里上一个字符是不是反斜杠。 */
    private class BraceScan {
        var depth = 0
        var inString = false
        var escaped = false
    }

    /** 字符串内的一个字符:反斜杠让下一个字符失去转义作用,遇到不带转义的引号则出串。 */
    private fun BraceScan.stepInsideString(c: Char) {
        when {
            escaped -> escaped = false
            c == '\\' -> escaped = true
            c == '"' -> inString = false
        }
    }

    /** 字符串外的一个字符:引号则入串,{ 加深一层,} 浅一层;返回值是这一步是否把最外层对象合上。 */
    private fun BraceScan.stepOutsideString(c: Char): Boolean = when (c) {
        '"' -> {
            inString = true
            false
        }
        '{' -> {
            depth++
            false
        }
        '}' -> {
            depth--
            depth == 0
        }
        else -> false
    }

    /** 键名比对前去掉大小写、下划线、连字符和空格(verdict、Verdict、final_verdict 里的写法差异)。 */
    private fun key(k: String) = k.lowercase().filter { it.isLetterOrDigit() }

    /** 按 [names] 依次找(写法不同也认);都没有为 null。 */
    fun JsonObject.field(vararg names: String): JsonElement? {
        for (n in names) {
            this[n]?.let { return it }
            spelledDifferently(n)?.let { return it }
        }
        return null
    }

    /** 精确名没对上时,按归一化键名再找一遍。 */
    private fun JsonObject.spelledDifferently(name: String): JsonElement? {
        val want = key(name)
        return entries.firstOrNull { key(it.key) == want }?.value
    }

    /** 第一次读不出来时补的一句。 */
    const val JSON_ONLY = "上一条回复读不出 JSON。请只输出一个 JSON 对象,不要代码块标记,不要任何其他文字,格式照系统提示。"

    /**
     * 调一次模型并用 [parse] 读回复;读不出来就把那次回复和 [JSON_ONLY] 接在后面再问一次。两次都不行为 null。
     * 模型出错(异常)照常抛给调用方。
     */
    suspend fun <T : Any> completeParsed(model: ChatModel, system: String, messages: List<Message>, parse: (String) -> T?): T? {
        val first = model.complete(system, messages, emptyList())
        val firstText = visibleText(first)
        parse(firstText)?.let { return it }
        val retry = messages + Message(Role.Assistant, listOf(Part.Text(firstText.ifBlank { "(空)" })), Author.Maeve) +
            Message(Role.User, listOf(Part.Text(JSON_ONLY)), Author.Maeve)
        val second = model.complete(system, retry, emptyList())
        return parse(visibleText(second))
    }

    /** 把回复里的文字段按顺序拼成全文;思考、工具调用等非文字段不计入。 */
    private fun visibleText(reply: ModelReply): String =
        reply.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
}

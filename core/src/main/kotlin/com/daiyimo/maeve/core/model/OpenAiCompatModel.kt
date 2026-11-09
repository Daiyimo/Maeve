package com.daiyimo.maeve.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** SSE 的最后一件:收到它才算说完。 */
private const val DONE_MARKER = "[DONE]"

/**
 * OpenAI Chat Completions 兼容端点(DeepSeek/Qwen/Kimi/GLM/OpenRouter、本地自建服务等),各家差异只体现在 [capabilities] 上。
 *
 * 思考内容放在哪里各家不同:`reasoning_content`、`reasoning` 两个消息字段,或者 `content` 是分块数组、思考在 `thinking` 块里。
 * 收到思考时记下它来自哪个地址、哪种位置([ReasoningVia],写进 [Part.Thinking.opaque]);带工具的请求要原样放回原处——
 * 有服务商要求工具调用前后的思考接着回传,少传就拒请求。只回传从这个地址收到的,别家给的思考不外发
 * (不认这个字段的服务商自然也收不到)。[reasoningField] 是模型清单里写明的思考字段(`reasoning_content` 或 `reasoning`):
 * 写明了这一项时,来历不明的思考(旧版本存下的历史)也按它回传。
 * 用户选了思考强度才另发 `reasoning_effort`;列表里没写档位的服务商,这里也拿不到值。
 */
class OpenAiCompatModel(
    baseUrl: String,
    private val apiKey: String?,
    private val model: String,
    override val capabilities: Capabilities,
    private val http: OkHttpClient,
    reasoningField: String? = null,
    /** 每个请求都带的头(客户端标识、服务商要的会话号等,见 [ClientHeaders]);Key 的头由适配器自己加。 */
    private val headers: Map<String, String> = emptyMap(),
    /** 用户选的思考强度(`reasoning_effort`);null 不发。 */
    private val reasoningEffort: String? = null,
) : StreamingChatModel {
    override val name: String get() = model
    private val chatUrl = baseUrl.trimEnd('/') + "/chat/completions"
    private val fallbackVia = ReasoningVia.ofField(reasoningField)
    private val codec = Json { ignoreUnknownKeys = true }

    private companion object {
        /** 能标缓存的消息角色;assistant 可能只有工具调用,没有正文可标,不在内。 */
        val CACHEABLE_ROLES = setOf("system", "user", "tool")
        /** 显式缓存标记的内容(`cache_control` 的值)。 */
        val CACHE_MARK = buildJsonObject { put("type", "ephemeral") }
    }

    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
        val response = HttpSupport.execute(http, buildRequest(system, messages, tools, streaming = false))
        val (status, retryAfter, text) = response
        if (status !in 200..299) throw errorFor(status, retryAfter, text)
        // 有的兼容端点不按请求里的 stream 字段回 JSON,而是直接吐 SSE(本地、局域网上的自建服务常见):
        // 按流读完再拼,别把 `data:` 当成坏 JSON。
        if (!isEventStream(text)) return parseReply(text)
        val stream = ChatStream({}, ::usageOf, ::thinkingPart)
        response.replay(splitEvents(text), stream::accept)
        return stream.finish()
    }

    override suspend fun completeStreaming(
        system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit,
    ): ModelReply {
        var stream = ChatStream(onText, ::usageOf, ::thinkingPart)
        var response = HttpSupport.stream(http, buildRequest(system, messages, tools, streaming = true), stream::accept)
        if (response.status == 400 && response.body.contains("stream_options")) {
            // 一些兼容端点能流式生成,却没有用量选项:去掉这项再发一次。
            stream = ChatStream(onText, ::usageOf, ::thinkingPart)
            response = HttpSupport.stream(http, buildRequest(system, messages, tools, streaming = true, includeUsage = false), stream::accept)
        }
        val (status, retryAfter, text) = response
        if (status !in 200..299) {
            // 不支持流式传输的兼容端点仍可按原接口完成任务。
            if (status == 400 && text.contains("stream", ignoreCase = true)) {
                return complete(system, messages, tools).also { emitText(it, onText) }
            }
            throw errorFor(status, retryAfter, text)
        }
        if (text.isNotEmpty()) return parseReply(text).also { emitText(it, onText) }
        return stream.finish()
    }

    /** 非流式路径也要把可见文字播给界面:拼出全篇文字,非空才播。 */
    private fun emitText(reply: ModelReply, onText: (String) -> Unit) {
        reply.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
            .takeIf(String::isNotEmpty)?.let(onText)
    }

    private suspend fun buildRequest(
        system: String, messages: List<Message>, tools: List<ToolSpec>, streaming: Boolean, includeUsage: Boolean = true,
    ): Request {
        val payload = payloadOf(system, messages, tools, streaming, includeUsage)
        return Request.Builder().url(chatUrl)
            .withHeaders(headers)
            .header("Accept", if (streaming) "text/event-stream" else "application/json")
            .apply { if (!apiKey.isNullOrEmpty()) header("Authorization", "Bearer $apiKey") }
            .postInference(InferenceProtocol.Chat, payload)
            .build()
    }

    private fun payloadOf(
        system: String, messages: List<Message>, tools: List<ToolSpec>, streaming: Boolean, includeUsage: Boolean,
    ): JsonObject = buildJsonObject {
        put("model", model)
        reasoningEffort?.let { put("reasoning_effort", it) }
        // 思考只在带工具的请求里回传(工具调用一步接一步时要接着想);不带工具的旁路调用不需要。
        val replayReasoning = tools.isNotEmpty()
        val systemMessage = buildJsonObject {
            put("role", "system")
            put("content", system)
        }
        val wire = listOf(systemMessage) + messages.flatMap { toWireMessages(it, replayReasoning) }
        put("messages", JsonArray(if (capabilities.promptCaching == PromptCaching.Explicit) markCacheable(wire) else wire))
        if (tools.isNotEmpty()) putJsonArray("tools") { tools.forEach { add(toolPayload(it)) } }
        if (streaming) {
            put("stream", true)
            if (includeUsage) putJsonObject("stream_options") { put("include_usage", true) }
        } else {
            // 写明不要流式:省掉这一项时有的兼容端点默认按 SSE 回。
            put("stream", false)
        }
    }

    /**
     * 显式缓存:标在系统提示词上(每次都一样),再标在最后一条能标的消息上(缓存到这次为止的整段对话,
     * 下一次请求的开头与它相同时就从缓存取)。服务端从标记往回找相同的开头,所以较早的消息不能被改写。
     * 内容是字符串的换成一段文字块再标;助手消息(可能只有工具调用)不标。
     */
    private fun markCacheable(wire: List<JsonObject>): List<JsonObject> {
        val last = wire.indexOfLast { (it["role"] as? JsonPrimitive)?.contentOrNull in CACHEABLE_ROLES && it["content"] !is JsonNull }
        return wire.mapIndexed { i, m -> if (i == 0 || i == last) withCacheMark(m) else m }
    }

    /** 消息正文拆成块:字符串正文当成一个文字块;已经是块的(分块数组)原样给;别的(没有正文)为 null。 */
    private fun contentBlocks(m: JsonObject): List<JsonObject>? = when (val c = m["content"]) {
        is JsonPrimitive -> listOf(buildJsonObject {
            put("type", "text")
            put("text", c.content)
        })
        is JsonArray -> c.map { it.jsonObject }
        else -> null
    }

    private fun withCacheMark(m: JsonObject): JsonObject {
        val blocks = contentBlocks(m) ?: return m
        if (blocks.isEmpty()) return m
        val tail = JsonObject(blocks.last() + ("cache_control" to CACHE_MARK))
        return JsonObject(m + ("content" to JsonArray(blocks.dropLast(1) + tail)))
    }

    private fun errorFor(status: Int, retryAfter: String?, body: String): ModelError =
        ModelHttp.openAiError(status, retryAfter, body, model)

    private fun toolPayload(t: ToolSpec): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", t.name)
            put("description", t.description)
            put("parameters", try { codec.parseToJsonElement(t.parametersJsonSchema) } catch (e: Exception) {
                throw IllegalArgumentException("工具 ${t.name} 的参数 schema 不是合法 JSON", e)
            })
        }
    }

    /** 一条内部消息可能展开成多条线上消息(tool 结果各占一条,图片只能放 user 消息)。 */
    private fun toWireMessages(m: Message, replayReasoning: Boolean): List<JsonObject> {
        if (m.role == Role.Assistant) return listOf(assistantMessageJson(m, replayReasoning))
        val out = mutableListOf<JsonObject>()
        m.parts.filterIsInstance<Part.ToolResult>().forEach { r -> out += toolResultJson(r) }
        val rest = m.parts.filter { it is Part.Text || it is Part.Image }
        if (rest.isNotEmpty()) out += contentMessageJson(m, rest)
        return out
    }

    private fun toolResultJson(r: Part.ToolResult): JsonObject = buildJsonObject {
        put("role", "tool"); put("tool_call_id", r.callId); put("content", r.content)
    }

    /** 文字(和图片)合成一条 user/system 消息;模型不能看图时,图片换成一句说明。 */
    private fun contentMessageJson(m: Message, rest: List<Part>): JsonObject {
        val role = if (m.role == Role.System) "system" else "user"
        val withImages = rest.any { it is Part.Image } && capabilities.vision
        return buildJsonObject {
            put("role", role)
            if (!withImages) {
                put("content", rest.joinToString("\n") { plainTextOf(it) })
            } else {
                putJsonArray("content") {
                    rest.forEach { p ->
                        add(buildJsonObject {
                            if (p is Part.Image) {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", "data:${p.mime};base64,${p.pngBase64}") }
                            } else {
                                put("type", "text"); put("text", plainTextOf(p))
                            }
                        })
                    }
                }
            }
        }
    }

    private fun plainTextOf(p: Part): String = when (p) {
        is Part.Text -> p.text
        is Part.Image -> "[screenshot omitted: the current model cannot see images]"
        else -> ""
    }

    /** 收到的一段思考:记下这个地址和收到它的位置,回放时认得。 */
    private fun thinkingPart(text: String, via: ReasoningVia) = Part.Thinking(text, via.tag(chatUrl))

    /** 这条助手消息里能回传给这个地址的思考和放回哪里:从这里收到的按收到的位置;来历不明的按列表写明的字段;都没有为 null。 */
    private fun replayableThinking(m: Message): Pair<String, ReasoningVia>? {
        val parts = m.parts.filterIsInstance<Part.Thinking>()
        val own = parts.mapNotNull { p -> ReasoningVia.of(p.opaque, chatUrl)?.let { p.text to it } }
        if (own.isNotEmpty()) return own.joinToString("") { it.first } to own.first().second
        val declared = fallbackVia ?: return null
        val untagged = parts.filter { it.opaque == null }.joinToString("") { it.text }
        return if (untagged.isEmpty()) null else untagged to declared
    }

    private fun assistantMessageJson(m: Message, replayReasoning: Boolean): JsonObject {
        val text = m.parts.filterIsInstance<Part.Text>().joinToString("\n") { it.text }
        val calls = m.parts.filterIsInstance<Part.ToolCall>()
        val replay = if (replayReasoning) replayableThinking(m) else null
        return buildJsonObject {
            put("role", "assistant")
            when {
                // 分块的那家要整条原样放回:思考块在前,文字块在后。
                replay?.second == ReasoningVia.ThinkingChunks -> putJsonArray("content") {
                    addJsonObject {
                        put("type", "thinking")
                        putJsonArray("thinking") { addJsonObject { put("type", "text"); put("text", replay.first) } }
                    }
                    if (text.isNotEmpty()) addJsonObject { put("type", "text"); put("text", text) }
                }
                text.isEmpty() && calls.isNotEmpty() -> put("content", JsonNull)
                else -> put("content", text)
            }
            replay?.second?.field?.let { put(it, replay.first) }
            if (calls.isNotEmpty()) putJsonArray("tool_calls") { calls.forEach { add(toolCallJson(it)) } }
        }
    }

    private fun toolCallJson(c: Part.ToolCall): JsonObject = buildJsonObject {
        put("id", c.id); put("type", "function")
        putJsonObject("function") { put("name", c.name); put("arguments", c.argsJson) }
    }

    /** 整份回复是 SSE(`data: …` 一件件)还是一份 JSON。 */
    private fun isEventStream(body: String): Boolean {
        val head = body.trimStart()
        return head.startsWith("data:") || head.startsWith("event:")
    }

    /** 把整份 SSE 回复拆成一件件事件;内容已经收完,缺结束标记时补一个,当作正常说完。 */
    private fun splitEvents(body: String): List<String> {
        val out = mutableListOf<String>()
        val data = mutableListOf<String>()
        fun dispatch() {
            if (data.isNotEmpty()) {
                out += data.joinToString("\n")
                data.clear()
            }
        }
        body.lineSequence().forEach { line ->
            when {
                line.isEmpty() -> dispatch()
                line.startsWith("data:") -> data += line.removePrefix("data:").removePrefix(" ")
            }
        }
        dispatch()
        if (out.lastOrNull() != DONE_MARKER) out += DONE_MARKER
        return out
    }

    private fun parseReply(text: String): ModelReply {
        try {
            val root = codec.parseToJsonElement(text).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw ModelError.BadResponse("缺少 choices")
            val msg = choice["message"]?.jsonObject ?: throw ModelError.BadResponse("缺少 message")
            val parts = mutableListOf<Part>()
            ReasoningVia.fields.firstNotNullOfOrNull { via ->
                (msg[via.field!!] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { it to via }
            }?.let { (t, via) -> parts += thinkingPart(t, via) }
            readContent(msg["content"], parts)
            (msg["tool_calls"] as? JsonArray)?.forEach { el -> parts += toolCallOf(el.jsonObject) }
            val stop = stopReasonOf((choice["finish_reason"] as? JsonPrimitive)?.contentOrNull, parts.any { it is Part.ToolCall })
            // 服务商的内容审核拦了正文(finish_reason=content_filter,或 refusal 字段说了话):这不是「模型没给回答」,
            // 直说被拒,免得用户看到一个看不出原因的空回复。
            if (refused(msg, choice) && parts.none { it is Part.Text && it.text.isNotBlank() } && parts.none { it is Part.ToolCall }) {
                throw ModelError.ContentRefused()
            }
            // 有的模型会把工具调用标记直接吐进正文:先还原成调用,还原不了就剥掉,别把乱码交给界面(见 LeakedToolMarkup)。
            return LeakedToolMarkup.repair(ModelReply(parts, stop, usageOf(root["usage"])))
        } catch (e: ModelError) {
            throw e
        } catch (e: Exception) {
            throw ModelError.BadResponse(e.message.orEmpty().take(200), e)
        }
    }

    /** content 可能是分块数组(文字块、thinking 块),也可能是一段字符串;别的形状不管。 */
    private fun readContent(content: JsonElement?, parts: MutableList<Part>) {
        when (content) {
            is JsonArray -> {
                val blocks = ChunkedContent.of(content)
                if (blocks.thinking.isNotEmpty() && parts.isEmpty()) {
                    parts += thinkingPart(blocks.thinking, ReasoningVia.ThinkingChunks)
                }
                if (blocks.text.isNotEmpty()) parts += Part.Text(blocks.text)
            }
            is JsonPrimitive -> content.contentOrNull?.takeIf { it.isNotEmpty() }?.let { parts += Part.Text(it) }
            else -> {}
        }
    }

    /** 内容审核拦了:refusal 字段说了话,或 finish_reason 是 content_filter。 */
    private fun refused(msg: JsonObject, choice: JsonObject): Boolean =
        (msg["refusal"] as? JsonPrimitive)?.contentOrNull?.isNotEmpty() == true ||
            (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull == "content_filter"

    /** 一条 tool_calls 条目;没有参数的工具,有的适配器给空串,当成 `{}`,否则调用方按 JSON 解析会失败。 */
    private fun toolCallOf(c: JsonObject): Part.ToolCall {
        val fn = c["function"]!!.jsonObject
        return Part.ToolCall(
            c["id"]!!.jsonPrimitive.content,
            fn["name"]!!.jsonPrimitive.content,
            fn["arguments"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "{}",
        )
    }

    private fun usageOf(el: JsonElement?): Usage? {
        val u = el as? JsonObject ?: return null
        // 兼容接口有两种命中字段名;按字段是否存在取值。
        val hit = u.intField("prompt_cache_hit_tokens")
        val miss = u.intField("prompt_cache_miss_tokens")
        val inTokens = u.intField("prompt_tokens") ?: if (hit != null && miss != null) hit + miss else return null
        val outTokens = u.intField("completion_tokens") ?: 0
        val cached = maxOf((u["prompt_tokens_details"] as? JsonObject)?.intField("cached_tokens") ?: 0, hit ?: 0).coerceAtMost(inTokens)
        // completion_tokens 含思考;有的服务商在 completion_tokens_details.reasoning_tokens 里单独报。
        val reasoning = ((u["completion_tokens_details"] as? JsonObject)?.intField("reasoning_tokens") ?: 0).coerceIn(0, outTokens)
        return Usage(inTokens, outTokens, cached, reasoning)
    }
}

/** 结束原因:认得出的照旧;认不出时按这一回复里有没有工具调用猜。 */
private fun stopReasonOf(finishReason: String?, hasToolCalls: Boolean): StopReason = when (finishReason) {
    "stop" -> StopReason.EndTurn
    "tool_calls", "function_call" -> StopReason.ToolUse
    "length" -> StopReason.MaxTokens
    else -> if (hasToolCalls) StopReason.ToolUse else StopReason.Other
}

/** JSON 里的整数字段;没有或不是整数为 null。 */
private fun JsonObject.intField(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

/** Chat Completions 的 delta 可以把同一个工具的名称与参数拆成多帧。 */
private class ChatStream(
    private val onText: (String) -> Unit,
    private val usageParser: (JsonElement?) -> Usage?,
    private val thinkingParser: (String, ReasoningVia) -> Part.Thinking,
) {
    private data class Call(val id: StringBuilder = StringBuilder(), val name: StringBuilder = StringBuilder(),
        val args: StringBuilder = StringBuilder())

    private val codec = Json { ignoreUnknownKeys = true }
    private val visible = StringBuilder()
    private val thinking = StringBuilder()
    private var thinkingVia: ReasoningVia? = null
    private val calls = sortedMapOf<Int, Call>()
    private var usage: Usage? = null
    private var reason: String? = null
    private var done = false
    private var chunks = 0

    fun accept(data: String): Boolean {
        if (data == DONE_MARKER) { done = true; return true }
        val root = try { codec.parseToJsonElement(data).jsonObject }
        catch (e: Exception) { throw ModelError.BadResponse("流式响应不是合法 JSON", e) }
        // 有的服务流式请求一开头就出错时回 200,在事件流里给一帧错误;说的是超长,和 400 一样按超长处理。
        root["error"]?.toString()?.let { e ->
            throw if (ModelHttp.saysContextTooLong(e)) ModelError.ContextTooLong() else ModelError.BadResponse(e.take(200))
        }
        chunks++
        usageParser(root["usage"])?.let { usage = it }
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return false
        (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotEmpty)?.let { reason = it }
        val delta = choice["delta"] as? JsonObject ?: return false
        ReasoningVia.fields.forEach { via ->
            (delta[via.field!!] as? JsonPrimitive)?.contentOrNull?.let { appendThinking(it, via) }
        }
        when (val content = delta["content"]) {
            is JsonArray -> {
                val blocks = ChunkedContent.of(content)
                if (blocks.thinking.isNotEmpty()) appendThinking(blocks.thinking, ReasoningVia.ThinkingChunks)
                appendVisible(blocks.text)
            }
            is JsonPrimitive -> content.contentOrNull?.let(::appendVisible)
            else -> {}
        }
        (delta["tool_calls"] as? JsonArray)?.forEach { readToolCallDelta(it) }
        return done
    }

    /** 一帧工具调用增量:同一个调用的编号、名称、参数可能拆成多帧,按 index 归并。 */
    private fun readToolCallDelta(item: JsonElement) {
        val obj = item as? JsonObject ?: return
        val index = (obj["index"] as? JsonPrimitive)?.intOrNull ?: return
        val call = calls.getOrPut(index, ::Call)
        (obj["id"] as? JsonPrimitive)?.contentOrNull?.let { call.id.append(it) }
        val fn = obj["function"] as? JsonObject
        (fn?.get("name") as? JsonPrimitive)?.contentOrNull?.let { call.name.append(it) }
        (fn?.get("arguments") as? JsonPrimitive)?.contentOrNull?.let { call.args.append(it) }
    }

    /** 一次回复里思考只按最先收到的位置记(同一家不会换位置;两个字段都给时只记一份)。 */
    private fun appendThinking(t: String, via: ReasoningVia) {
        if (t.isEmpty()) return
        if (thinkingVia == null) thinkingVia = via
        if (via == thinkingVia) thinking.append(t)
    }

    private fun appendVisible(t: String) {
        if (t.isEmpty()) return
        visible.append(t)
        // 预览也先剥一遍:模型把工具调用标记吐进正文时,别让用户看着乱码流过去。
        onText(LeakedToolMarkup.stripMarkup(visible.toString()))
    }

    fun finish(): ModelReply {
        // 收到 finish_reason 就算回复完整:有的兼容端点不发 [DONE]。两样都没有是连接中途被断开。
        if (!done && reason == null) throw ModelError.StreamCut()
        if (chunks == 0) throw ModelError.BadResponse("流式响应没有内容")
        val parts = buildList<Part> {
            thinkingVia?.takeIf { thinking.isNotEmpty() }?.let { add(thinkingParser(thinking.toString(), it)) }
            if (visible.isNotEmpty()) add(Part.Text(visible.toString()))
            calls.values.forEach { c ->
                if (c.name.isEmpty() || c.id.isEmpty()) throw ModelError.BadResponse("工具调用流缺少名称或编号")
                add(Part.ToolCall(c.id.toString(), c.name.toString(), c.args.toString().ifEmpty { "{}" }))
            }
        }
        return LeakedToolMarkup.repair(ModelReply(parts, stopReasonOf(reason, calls.isNotEmpty()), usage))
    }
}

/**
 * 兼容接口把思考放在哪:[field] 是助手消息上的字段名;[ThinkingChunks] 是 `content` 为分块数组、思考在 `thinking` 块里。
 * 记在 [Part.Thinking.opaque] 里的是 `{"endpoint":…,"via":…}`。
 */
internal enum class ReasoningVia(val wire: String, val field: String?) {
    ReasoningContent("reasoning_content", "reasoning_content"),
    Reasoning("reasoning", "reasoning"),
    ThinkingChunks("thinking_chunks", null);

    fun tag(endpoint: String): String = buildJsonObject { put("endpoint", endpoint); put("via", wire) }.toString()

    companion object {
        /** 放在消息字段上的几种,按这个次序认(两个都给时取 `reasoning_content`)。 */
        val fields = listOf(ReasoningContent, Reasoning)

        fun ofField(name: String?): ReasoningVia? = fields.firstOrNull { it.field == name }

        /** [opaque] 是从 [endpoint] 收到的思考时给出它的位置;别处来的、认不出的为 null。 */
        fun of(opaque: String?, endpoint: String): ReasoningVia? {
            val o = opaque?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
            if ((o["endpoint"] as? JsonPrimitive)?.contentOrNull != endpoint) return null
            val via = (o["via"] as? JsonPrimitive)?.contentOrNull
            return entries.firstOrNull { it.wire == via }
        }
    }
}

/** `content` 是分块数组时:`text` 块拼成回答,`thinking` 块(里面又是 `text` 块)拼成思考。别的块不认。 */
private class ChunkedContent(val text: String, val thinking: String) {
    companion object {
        fun of(content: JsonArray): ChunkedContent {
            val text = StringBuilder()
            val thinking = StringBuilder()
            content.forEach { el ->
                val c = el as? JsonObject ?: return@forEach
                when ((c["type"] as? JsonPrimitive)?.contentOrNull) {
                    "text" -> (c["text"] as? JsonPrimitive)?.contentOrNull?.let(text::append)
                    "thinking" -> when (val t = c["thinking"]) {
                        is JsonArray -> t.forEach { inner ->
                            ((inner as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull?.let(thinking::append)
                        }
                        is JsonPrimitive -> t.contentOrNull?.let(thinking::append)
                        else -> {}
                    }
                }
            }
            return ChunkedContent(text.toString(), thinking.toString())
        }
    }
}

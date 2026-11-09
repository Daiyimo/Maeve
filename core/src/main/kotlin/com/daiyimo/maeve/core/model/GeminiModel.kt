package com.daiyimo.maeve.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Gemini generateContent 原生接口的适配器。
 * - 角色只有 user / model,相邻同角色的合并;历史里的 System 消息并进 systemInstruction。
 * - Gemini 不给调用编号,自己合成 "gemini-<序号>-<名称>"(响应带了 id 就用它);
 *   回传 functionResponse 时按 callId 到历史里的 ToolCall 找函数名。
 * - thought 部分解析成 Thinking;选了思考强度时用 `generationConfig.thinkingConfig.thinkingLevel` 控制深度。
 */

/** Google 给的占位签名:调用不是 Gemini 自己产生的(带不出原签名)时,发它跳过签名校验。 */
internal const val SKIP_SIGNATURE = "skip_thought_signature_validator"

class GeminiModel(
    baseUrl: String = "https://generativelanguage.googleapis.com",
    private val apiKey: String,
    private val model: String,
    override val capabilities: Capabilities,
    private val http: OkHttpClient,
    /** 每个请求都带的头(见 [ClientHeaders]);Key 的头由适配器自己加。 */
    private val headers: Map<String, String> = emptyMap(),
    /** 回复最多多少 token(`generationConfig.maxOutputTokens`);null 不限,按服务端默认。 */
    private val maxOutputTokens: Int? = null,
    /** 用户选的思考档位;null 不发,`none` 尽量关掉思考,其余作为 `thinkingConfig.thinkingLevel` 发出。 */
    private val reasoningEffort: String? = null,
) : StreamingChatModel {
    override val name: String get() = model
    private val endpoint = baseUrl.trimEnd('/') + "/v1beta/models/$model:generateContent"
    private val streamEndpoint = baseUrl.trimEnd('/') + "/v1beta/models/$model:streamGenerateContent?alt=sse"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
        val raw = HttpSupport.execute(http, request(system, messages, tools, stream = false))
        return parse(raw.orThrow())
    }

    override suspend fun completeStreaming(
        system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit,
    ): ModelReply {
        val stream = GeminiStream(onText, ::parse, ::parseUsage)
        val raw = HttpSupport.stream(http, request(system, messages, tools, stream = true), stream::accept)
        val text = raw.orThrow()
        if (text.isNotEmpty()) return parse(text).also { reply ->
            reply.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }.takeIf(String::isNotEmpty)?.let(onText)
        }
        return stream.finish()
    }

    /** 回复可用性检查:不是 2xx 就把状态和内容映射成 [ModelError] 抛出,2xx 返回内容。 */
    private fun RawResponse.orThrow(): String {
        if (status !in 200..299) throw mapError(status, retryAfter, body)
        return body
    }

    private suspend fun request(system: String, messages: List<Message>, tools: List<ToolSpec>, stream: Boolean): Request {
        val systemText = systemPrompt(system, messages)
        val payload = buildJsonObject {
            if (systemText.isNotBlank()) put("systemInstruction", systemInstruction(systemText))
            if (tools.isNotEmpty()) put("tools", toolDeclarations(tools))
            put("contents", buildContents(messages))
            generationConfig()?.let { put("generationConfig", it) }
        }
        return Request.Builder().url(if (stream) streamEndpoint else endpoint)
            .withHeaders(headers)
            .header("x-goog-api-key", apiKey)
            .postInference(InferenceProtocol.Gemini, payload)
            .build()
    }

    private fun mapError(status: Int, retryAfter: String?, body: String): ModelError = ModelHttp.geminiError(status, retryAfter, body, model)

    /** 系统提示词:传入的加上历史里的 System 消息,空段略过,段间空一行。 */
    private fun systemPrompt(system: String, messages: List<Message>): String {
        val history = messages.filter { it.role == Role.System }
            .map { m -> m.parts.filterIsInstance<Part.Text>().joinToString("\n") { it.text } }
        return (listOf(system) + history).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    /** systemInstruction 字段:一段文字。 */
    private fun systemInstruction(text: String): JsonObject = buildJsonObject {
        putJsonArray("parts") { add(buildJsonObject { put("text", text) }) }
    }

    /** tools 字段:一层 functionDeclarations 包着所有工具。 */
    private fun toolDeclarations(tools: List<ToolSpec>): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("functionDeclarations", buildJsonArray { tools.forEach { add(toolJson(it)) } })
        })
    }

    /** 生成配置:上限和思考档位都没选就不发这个字段。 */
    private fun generationConfig(): JsonObject? {
        if (maxOutputTokens == null && reasoningEffort == null) return null
        return buildJsonObject {
            maxOutputTokens?.let { put("maxOutputTokens", it) }
            reasoningEffort?.let { put("thinkingConfig", thinkingConfig(it)) }
        }
    }

    /** 思考配置:`none` 用零预算关掉,其余把档位大写后当 `thinkingLevel` 发,同时要求把思考部分带回。 */
    private fun thinkingConfig(effort: String): JsonObject = buildJsonObject {
        if (effort == "none") {
            put("thinkingBudget", 0)
        } else {
            put("includeThoughts", true)
            put("thinkingLevel", effort.uppercase())
        }
    }

    // Gemini 不接受 additionalProperties / $schema / $id 等 OpenAPI 子集之外的键
    private val rejectedKeys = setOf("additionalProperties", "\$schema", "\$id", "\$defs")

    // 只在 schema 位置剔除;properties 是 名称->schema 的映射,名称本身不动
    private fun sanitize(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.filterKeys { it !in rejectedKeys }.mapValues { (k, v) ->
            if (k == "properties" && v is JsonObject) JsonObject(v.mapValues { sanitize(it.value) }) else sanitize(v)
        })
        is JsonArray -> JsonArray(el.map { sanitize(it) })
        else -> el
    }

    private fun toolJson(t: ToolSpec): JsonObject = buildJsonObject {
        put("name", t.name)
        put("description", t.description)
        // 无参数工具:Gemini 拒绝没有 properties 的 object schema,省略 parameters
        parametersOf(t)?.let { put("parameters", it) }
    }

    /** 清掉 Gemini 不认的键以后的参数 schema;没有 properties 的(无参数工具)为 null,整个字段省掉。 */
    private fun parametersOf(t: ToolSpec): JsonElement? {
        val schema = try { json.parseToJsonElement(t.parametersJsonSchema) } catch (e: Exception) {
            throw IllegalArgumentException("工具 ${t.name} 的参数 schema 不是合法 JSON", e)
        }
        val clean = sanitize(schema)
        val hasProps = (clean as? JsonObject)?.get("properties")?.let { it is JsonObject && it.isNotEmpty() } == true
        return clean.takeIf { hasProps }
    }

    private fun buildContents(messages: List<Message>): JsonArray = buildJsonArray {
        mergeTurns(messages).forEach { (role, parts) ->
            add(buildJsonObject { put("role", role); put("parts", JsonArray(parts)) })
        }
    }

    /**
     * 相邻同角色的消息并成一条(Gemini 要求 user/model 交替);system 消息已并进 systemInstruction,跳过;没有部分的消息跳过。
     * 顺手建一份 callId 到函数名的对照:回传 functionResponse 时要按 callId 找函数名。
     */
    private fun mergeTurns(messages: List<Message>): List<Pair<String, MutableList<JsonObject>>> {
        val nameById = HashMap<String, String>()
        messages.forEach { m -> m.parts.filterIsInstance<Part.ToolCall>().forEach { nameById[it.id] = it.name } }
        val merged = mutableListOf<Pair<String, MutableList<JsonObject>>>()
        for (m in messages) {
            if (m.role == Role.System) continue
            val role = if (m.role == Role.Assistant) "model" else "user"
            val parts = if (role == "model") modelParts(m) else userParts(m, nameById)
            if (parts.isEmpty()) continue
            val last = merged.lastOrNull()
            if (last != null && last.first == role) last.second += parts else merged += role to parts.toMutableList()
        }
        return merged
    }

    private fun userParts(m: Message, nameById: Map<String, String>): List<JsonObject> {
        // functionResponse 紧跟在 functionCall 之后,排在其它部分之前
        val results = m.parts.filterIsInstance<Part.ToolResult>().map { r -> functionResponse(r, nameById) }
        val rest = m.parts.mapNotNull { p -> userPart(p) }
        return results + rest
    }

    /** 工具结果:按 callId 找函数名(找不到就用 callId 顶),出错的在 response 里标出来。 */
    private fun functionResponse(r: Part.ToolResult, nameById: Map<String, String>): JsonObject = buildJsonObject {
        putJsonObject("functionResponse") {
            put("name", nameById[r.callId] ?: r.callId)
            putJsonObject("response") {
                put("content", r.content)
                if (r.isError) put("is_error", true)
            }
        }
    }

    /** functionResponse 之外的部分:文字原样,图片按模型能不能看图给 inlineData 或占位文字。 */
    private fun userPart(p: Part): JsonObject? = when (p) {
        is Part.Text -> if (p.text.isEmpty()) null else buildJsonObject { put("text", p.text) }
        is Part.Image -> if (capabilities.vision) inlineData(p) else buildJsonObject { put("text", "[screenshot omitted: the current model cannot see images]") }
        else -> null
    }

    private fun inlineData(p: Part.Image): JsonObject = buildJsonObject {
        putJsonObject("inlineData") { put("mimeType", p.mime); put("data", p.pngBase64) }
    }

    private fun modelParts(m: Message): List<JsonObject> {
        // 一轮里只有第一个调用带签名(并行调用的其余几个本来就没有);第一个没有签名说明调用不是本适配器产生的
        // (换了模型、或回放补的调用),按 Google 的说明发跳过校验的占位签名
        val firstCall = m.parts.firstOrNull { it is Part.ToolCall }
        return m.parts.mapNotNull { p -> modelPart(p, first = p === firstCall) }
    }

    private fun modelPart(p: Part, first: Boolean): JsonObject? =
        when (p) {
            is Part.Text -> if (p.text.isEmpty()) null else buildJsonObject { put("text", p.text) }
            is Part.ToolCall -> buildJsonObject {
                (p.opaque ?: SKIP_SIGNATURE.takeIf { first })?.let { put("thoughtSignature", it) }
                putJsonObject("functionCall") {
                    put("name", p.name)
                    put("args", try { json.parseToJsonElement(p.argsJson).jsonObject } catch (e: Exception) { JsonObject(emptyMap()) })
                }
            }
            else -> null
        }

    private fun parse(text: String): ModelReply {
        try {
            val root = json.parseToJsonElement(text).jsonObject
            val feedback = root["promptFeedback"] as? JsonObject
            val blockReason = (feedback?.get("blockReason") as? JsonPrimitive)?.contentOrNull
            if (blockReason != null) throw ModelError.ContentRefused()
            val cand = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: throw ModelError.BadResponse("缺少 candidates")
            val parts = candidateParts(cand)
            val calls = parts.count { it is Part.ToolCall }
            val stop = geminiStopReason(
                (cand["finishReason"] as? JsonPrimitive)?.contentOrNull,
                calls,
                parts.any { it !is Part.Thinking },
            )
            return ModelReply(parts, stop, parseUsage(root["usageMetadata"]))
        } catch (e: ModelError) {
            throw e
        } catch (e: Exception) {
            throw ModelError.BadResponse(e.message.orEmpty().take(200), e)
        }
    }

    /** 候选里的部分:工具调用、思考、文字各归各;工具调用的编号按它在这轮里排第几合成。 */
    private fun candidateParts(cand: JsonObject): List<Part> {
        val parts = mutableListOf<Part>()
        var calls = 0
        val content = cand["content"] as? JsonObject
        content?.get("parts")?.jsonArray?.forEach { el ->
            val p = el.jsonObject
            val fc = p["functionCall"] as? JsonObject
            val t = (p["text"] as? JsonPrimitive)?.contentOrNull
            when {
                fc != null -> {
                    parts += functionCallPart(fc, p, calls)
                    calls++
                }
                t.isNullOrEmpty() -> {}
                (p["thought"] as? JsonPrimitive)?.booleanOrNull == true -> parts += Part.Thinking(t)
                else -> parts += Part.Text(t)
            }
        }
        return parts
    }

    /** 工具调用:名字不能少;没带 id 就按它在这轮里排第几和函数名合成;签名原样存进 opaque,回放时带上。 */
    private fun functionCallPart(fc: JsonObject, p: JsonObject, index: Int): Part.ToolCall {
        val name = (fc["name"] as? JsonPrimitive)?.contentOrNull ?: throw ModelError.BadResponse("functionCall 缺少 name")
        val id = (fc["id"] as? JsonPrimitive)?.contentOrNull ?: "gemini-$index-$name"
        val sig = (p["thoughtSignature"] as? JsonPrimitive)?.contentOrNull
        return Part.ToolCall(id, name, (fc["args"] ?: JsonObject(emptyMap())).toString(), sig)
    }

    private fun parseUsage(el: JsonElement?): Usage? {
        val u = el as? JsonObject ?: return null
        fun n(k: String) = (u[k] as? JsonPrimitive)?.intOrNull
        val inT = n("promptTokenCount") ?: return null
        // promptTokenCount 已含缓存命中部分,和统一口径一致;思考 token 计入 output,另单独报一份
        val thoughts = n("thoughtsTokenCount") ?: 0
        return Usage(inT, (n("candidatesTokenCount") ?: 0) + thoughts, n("cachedContentTokenCount") ?: 0, thoughts)
    }
}

/** 流式回复的收集器:一帧一个候选,文字累积后全文回调,收齐后拼成 [ModelReply]。 */
private class GeminiStream(
    private val onText: (String) -> Unit,
    private val parseChunk: (String) -> ModelReply,
    private val usageOf: (JsonElement?) -> Usage?,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val visibleText = StringBuilder()
    private val thinkingText = StringBuilder()
    private val toolCalls = mutableListOf<Part.ToolCall>()
    private var usage: Usage? = null
    private var finishReason: String? = null

    /** 收一帧;返回 true 表示可以收尾(见了 finishReason 且这帧带了用量),读循环就此收尾。 */
    fun accept(data: String): Boolean {
        val root = try { json.parseToJsonElement(data).jsonObject }
        catch (e: Exception) { throw ModelError.BadResponse("流式响应不是合法 JSON", e) }
        var usageInEvent = false
        val reported = usageOf(root["usageMetadata"])
        if (reported != null) {
            usage = reported
            usageInEvent = true
        }
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
        if (candidate == null) {
            // finishReason 后可能另有一帧只带用量;收到它才算完整的终态,不能提前关流把它丢掉
            return finishReason != null && usageInEvent
        }
        (candidate["finishReason"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotEmpty)?.let { finishReason = it }
        val reply = parseChunk(data)
        var chunkCalls = 0
        reply.parts.forEach { part ->
            when (part) {
                is Part.Text -> { visibleText.append(part.text); onText(visibleText.toString()) }
                is Part.Thinking -> thinkingText.append(part.text)
                is Part.ToolCall -> toolCalls += renumbered(part, chunkCalls++)
                else -> Unit
            }
        }
        // 当前帧收到用量且已见 finishReason 时可立即收尾;否则继续等可能的 usage-only 帧或 EOF
        return finishReason != null && usageInEvent
    }

    /** 这一帧里按帧内序号合成的 id 换成全局序号(带了 id 的原样保留):帧与帧之间的调用编号要连着。 */
    private fun renumbered(part: Part.ToolCall, indexInChunk: Int): Part.ToolCall =
        if (part.id == "gemini-$indexInChunk-${part.name}") part.copy(id = "gemini-${toolCalls.size}-${part.name}") else part

    fun finish(): ModelReply {
        // Gemini 的流没有单独的结束标记,带 finishReason 的那一帧就是收尾;没等到它是连接中途被断开
        if (finishReason == null) throw ModelError.StreamCut()
        val parts = buildList<Part> {
            if (thinkingText.isNotEmpty()) add(Part.Thinking(thinkingText.toString()))
            if (visibleText.isNotEmpty()) add(Part.Text(visibleText.toString()))
            addAll(toolCalls)
        }
        return ModelReply(parts, stopReason(), usage)
    }

    /** 收尾原因:只认得出 STOP 和 MAX_TOKENS,STOP 时看有没有工具调用。 */
    private fun stopReason(): StopReason = when (finishReason) {
        "STOP" -> if (toolCalls.isNotEmpty()) StopReason.ToolUse else StopReason.EndTurn
        "MAX_TOKENS" -> StopReason.MaxTokens
        else -> StopReason.Other
    }
}

/**
 * Gemini 的停止原因映射成统一的 [StopReason]:
 * 没有原因或 `STOP` 时看有没有工具调用;安全一类的原因有可用内容算 [StopReason.Other],没有则算拒绝;
 * 其余没见过的原因带了工具调用算 [StopReason.ToolUse],否则算 [StopReason.Other]。
 */
private fun geminiStopReason(fr: String?, calls: Int, usable: Boolean): StopReason = when (fr) {
    null, "STOP" -> if (calls > 0) StopReason.ToolUse else StopReason.EndTurn
    "MAX_TOKENS" -> StopReason.MaxTokens
    "SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII" ->
        if (usable) StopReason.Other else throw ModelError.ContentRefused()
    else -> if (calls > 0 && fr.isNotEmpty()) StopReason.ToolUse else StopReason.Other
}

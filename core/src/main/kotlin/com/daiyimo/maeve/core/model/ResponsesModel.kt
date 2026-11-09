package com.daiyimo.maeve.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 请求时随附的访问令牌。[fresh] 为真表示上一个请求刚被拒(HTTP 401),必须先换一个新的再发;
 * 换不到就抛 [ModelError]。没有令牌的本地服务可以直接是 null。[refreshable] 为假的令牌(固定的
 * API Key)被拒之后不再尝试更换。
 */
interface BearerToken {
    val refreshable: Boolean get() = false
    suspend fun get(fresh: Boolean): String?

    companion object {
        /** 永远返回同一个值的令牌,给固定的 API Key 用。 */
        fun fixed(key: String?): BearerToken = object : BearerToken {
            override suspend fun get(fresh: Boolean): String? = key
        }
    }
}

/**
 * OpenAI Responses 协议的适配器,统一往 `POST {base}/responses` 发流式请求。
 *
 * 这个协议不在服务端留存对话(`store:false`),每一轮都要把要用的上下文整个放进 `input` 数组:
 * 系统提示词单独放 `instructions`;对话里出现的系统消息改写成 developer 消息(协议不收 system 角色的条目)。
 * 工具声明成 function 形式(`strict:false`——Maeve 的参数 schema 不是按严格模式写的);
 * 工具的执行结果按 `function_call_output` 条目回给模型。
 *
 * 请求体默认只带基本几项和 `include`;用户在设置里选了思考档位时才追加 `reasoning.effort`。
 * 温度和输出上限一律不发(走套餐额度的那条链路不收这两个字段)。
 *
 * 推理模型的思考按加密内容取回(`include: reasoning.encrypted_content`),原样收在本回合第一个工具调用的
 * [Part.ToolCall.opaque] 里;回放历史时放回那个工具调用前面,模型就能接着上一步的思路往下做。
 * 只有文字、没有工具调用的回合不需要接着想,也就不存。服务端明确不收 `include` 时,去掉它再发一次。
 *
 * 只有收到 `response.completed`(或 `response.incomplete`) 才算这轮正常收尾;流在这之前断掉按网络故障算(上层会重试)。
 * [chatGptPlan] 为真时花的是用户的 ChatGPT 套餐额度:HTTP 429 是额度到了上限、403 是账号不能这么用,都不重试。
 * [accountLogin] 是借别家账号(如 xAI)登录时那一家的名字:此时的 HTTP 403 说明订阅档位用不了这个接口
 * ([ModelError.AccountPlanNotAllowed])。
 */
class ResponsesModel(
    baseUrl: String,
    private val token: BearerToken,
    private val model: String,
    override val capabilities: Capabilities,
    private val http: OkHttpClient,
    private val chatGptPlan: Boolean = false,
    /** 每个请求都带的头(见 [ClientHeaders]);令牌的头由适配器自己加。这个接口不给回复上限(套餐那条路不收)。 */
    private val headers: Map<String, String> = emptyMap(),
    /** null 使用服务端默认;服务端拒绝所选档位时照常报错,不悄悄换档重试。 */
    private val reasoningEffort: String? = null,
    private val accountLogin: String? = null,
) : StreamingChatModel {
    override val name: String get() = model
    private val responsesUrl = baseUrl.trim().trimEnd('/') + "/responses"
    private val codec = Json { ignoreUnknownKeys = true }

    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply =
        completeStreaming(system, messages, tools) {}

    override suspend fun completeStreaming(
        system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit,
    ): ModelReply {
        // 有两种失败允许改一改请求再发:401 换新令牌;服务端不认 include 时去掉加密思考
        var includeReasoning = true
        var fresh = false
        while (true) {
            val reader = EventReader(onText, ::errorForCode)
            val bearer = token.get(fresh)
            val (status, retryAfter, text) = HttpSupport.stream(
                http, buildRequest(bearer, system, messages, tools, includeReasoning), reader::accept,
            )
            if (status in 200..299) {
                // 有的服务端不听 stream:true,直接把整个 response 对象回来了
                if (text.isNotEmpty()) return reader.fromBody(text).also { reply -> visibleTextOf(reply)?.let(onText) }
                return reader.finish()
            }
            val err = errorBodyOf(text)
            when {
                status == 401 && token.refreshable && !fresh -> fresh = true
                status == 400 && includeReasoning && (err?.param == "include" || text.contains(ENCRYPTED_REASONING)) ->
                    includeReasoning = false
                else -> throw httpFailure(status, retryAfter, text, err)
            }
        }
    }

    internal fun body(system: String, messages: List<Message>, tools: List<ToolSpec>, includeReasoning: Boolean = true): JsonObject =
        buildJsonObject {
            put("model", model)
            if (system.isNotEmpty()) put("instructions", system)
            put("input", JsonArray(messages.flatMap(::inputItemsOf)))
            if (tools.isNotEmpty()) putJsonArray("tools") { tools.forEach { add(toolPayload(it)) } }
            put("store", false)
            put("stream", true)
            reasoningEffort?.let { effort -> putJsonObject("reasoning") { put("effort", effort) } }
            if (includeReasoning) putJsonArray("include") { add(ENCRYPTED_REASONING) }
        }

    private suspend fun buildRequest(
        bearer: String?, system: String, messages: List<Message>, tools: List<ToolSpec>, includeReasoning: Boolean,
    ): Request =
        Request.Builder().url(responsesUrl)
            .withHeaders(headers)
            .apply { if (!bearer.isNullOrEmpty()) header("Authorization", "Bearer $bearer") }
            .header("Accept", "text/event-stream")
            .postInference(InferenceProtocol.Responses, body(system, messages, tools, includeReasoning)) {
                if (chatGptPlan || responsesUrl.toHttpUrlOrNull()?.host == "api.openai.com") "openai" else "openai_compatible"
            }
            .build()

    private fun toolPayload(t: ToolSpec): JsonObject = buildJsonObject {
        put("type", "function")
        put("name", t.name)
        put("description", t.description)
        put(
            "parameters",
            try {
                codec.parseToJsonElement(t.parametersJsonSchema)
            } catch (e: Exception) {
                throw IllegalArgumentException("工具 ${t.name} 的参数 schema 不是合法 JSON", e)
            },
        )
        put("strict", false)
    }

    /** 一条内部消息展开成若干 `input` 条目:助手的话、思考、工具调用各占条目;工具结果各占一条;文字和图片合成一条消息。 */
    private fun inputItemsOf(m: Message): List<JsonObject> {
        if (m.role == Role.Assistant) return assistantInputItems(m)
        val out = mutableListOf<JsonObject>()
        m.parts.filterIsInstance<Part.ToolResult>().forEach { r -> out += toolResultItem(r) }
        val rest = m.parts.filter { it is Part.Text || it is Part.Image }
        if (rest.isNotEmpty()) out += messageItem(m, rest)
        return out
    }

    private fun toolResultItem(r: Part.ToolResult): JsonObject = buildJsonObject {
        put("type", "function_call_output")
        put("call_id", r.callId)
        put("output", r.content)
    }

    /** 文字和图片合成一条 message 条目;系统消息在这个接口里要报 developer 角色。 */
    private fun messageItem(m: Message, rest: List<Part>): JsonObject = buildJsonObject {
        put("type", "message")
        put("role", if (m.role == Role.System) "developer" else "user")
        putJsonArray("content") {
            rest.forEach { p ->
                addJsonObject {
                    if (p is Part.Image && capabilities.vision) {
                        put("type", "input_image")
                        put("image_url", "data:${p.mime};base64,${p.pngBase64}")
                    } else {
                        put("type", "input_text")
                        put("text", if (p is Part.Text) p.text else IMAGE_OMITTED)
                    }
                }
            }
        }
    }

    private fun assistantInputItems(m: Message): List<JsonObject> {
        val calls = m.parts.filterIsInstance<Part.ToolCall>()
        val reasoning = encryptedReasoningOf(calls)
        val text = m.parts.filterIsInstance<Part.Text>().joinToString("\n") { it.text }
        return buildList {
            addAll(reasoning)
            if (text.isNotEmpty()) add(assistantTextItem(text))
            calls.forEach { c -> add(toolCallItem(c)) }
        }
    }

    /** 上一回合存进工具调用 opaque 的加密思考(JSON 数组),原样取出供回放;存得不对就当时没有。 */
    private fun encryptedReasoningOf(calls: List<Part.ToolCall>): List<JsonObject> =
        calls.mapNotNull { it.opaque }.flatMap { o ->
            runCatching { codec.parseToJsonElement(o) as? JsonArray }.getOrNull().orEmpty().filterIsInstance<JsonObject>()
        }

    private fun assistantTextItem(text: String): JsonObject = buildJsonObject {
        put("type", "message")
        put("role", "assistant")
        putJsonArray("content") {
            addJsonObject {
                put("type", "output_text")
                put("text", text)
            }
        }
    }

    private fun toolCallItem(c: Part.ToolCall): JsonObject = buildJsonObject {
        put("type", "function_call")
        put("call_id", c.id)
        put("name", c.name)
        put("arguments", c.argsJson)
    }

    internal data class ErrorBody(val code: String?, val message: String, val param: String?)

    private fun errorBodyOf(text: String): ErrorBody? {
        val root = runCatching { codec.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val e = root["error"] as? JsonObject
        if (e != null) return ErrorBody(e.text("code") ?: e.text("type"), e.text("message").orEmpty(), e.text("param"))
        return root.text("detail")?.let { ErrorBody(null, it, null) }
    }

    /** 流里的 `response.failed` / `error`,以及 HTTP 出错时内容里的 `error.code`。认不出的代码为 null。 */
    private fun errorForCode(code: String?, message: String, param: String?): ModelError? = when (code) {
        "subscription_sharing_usage_limit_exceeded" -> ModelError.ChatGptLimitReached()
        "subscription_sharing_user_not_eligible" -> ModelError.ChatGptNotEligible(message.take(200))
        // 服务端一时不可用:保留登录,过一会儿再试
        "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable" -> ModelError.Server(503)
        "subscription_sharing_unsupported_capability" -> ModelError.BadResponse("服务端不支持请求里的 ${param ?: "某项内容"}")
        "subscription_sharing_route_not_supported" -> ModelError.BadResponse("服务端不支持这个接口")
        "subscription_sharing_invalid_user", "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context" ->
            ModelError.Auth()
        "context_length_exceeded" -> ModelError.ContextTooLong()
        "insufficient_quota" -> ModelError.QuotaExceeded()
        "rate_limit_exceeded" -> if (chatGptPlan) ModelError.ChatGptLimitReached() else ModelError.RateLimited(null)
        "model_not_found" -> ModelError.ModelNotFound(model, ModelAccessErrors.clip(message))
        "server_error" -> ModelError.Server(500)
        else -> null
    }

    private fun httpFailure(status: Int, retryAfter: String?, body: String, err: ErrorBody?): ModelError {
        err?.let { errorForCode(it.code, it.message, it.param) }?.let { return it }
        // 账号登录已经核对过身份:没有认得出的错误代码的 403 是档位不够,不当成 Key 不对或某个模型没开通
        if (status == 403 && accountLogin != null) {
            return ModelError.AccountPlanNotAllowed(accountLogin, err?.message.orEmpty().take(200))
        }
        ModelAccessErrors.of(status, body, model)?.let { return it }
        return when {
            status == 401 -> ModelError.Auth()
            status == 403 -> if (chatGptPlan) ModelError.ChatGptNotEligible(err?.message.orEmpty().take(200)) else ModelError.Auth()
            status == 429 -> if (chatGptPlan) ModelError.ChatGptLimitReached() else ModelError.RateLimited(HttpSupport.retryAfterMs(retryAfter))
            status == 402 -> ModelError.QuotaExceeded()
            ModelHttp.contextTooLong(status, body) -> ModelError.ContextTooLong()
            status == 404 -> ModelError.ModelNotFound(model, ModelAccessErrors.jsonDetail(body))
            status in 500..599 -> ModelError.Server(status)
            else -> ModelError.BadResponse("HTTP $status ${body.take(200)}")
        }
    }

    /** 完整回复里能给用户看的文字(流式回调已经发过的不重复发)。 */
    private fun visibleTextOf(reply: ModelReply): String? =
        reply.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }.takeIf(String::isNotEmpty)

    private companion object {
        const val ENCRYPTED_REASONING = "reasoning.encrypted_content"
        const val IMAGE_OMITTED = "[screenshot omitted: the current model cannot see images]"
    }
}

private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.intField(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

/**
 * 读 Responses 的事件流:`response.output_text.delta` 是逐段文字,`response.output_item.done` 是做完的一项
 * (消息、工具调用、思考),`response.completed` / `response.incomplete` 收尾,`response.failed` / `error` 是失败。
 */
private class EventReader(
    private val onText: (String) -> Unit,
    private val errorForCode: (String?, String, String?) -> ModelError?,
) {
    private val codec = Json { ignoreUnknownKeys = true }
    private val streamed = StringBuilder()
    private val items = mutableListOf<JsonObject>()
    private var final: JsonObject? = null
    private var incomplete = false

    fun accept(data: String): Boolean {
        if (data == "[DONE]") return false
        val root = try { codec.parseToJsonElement(data) as? JsonObject } catch (e: Exception) { null }
            ?: throw ModelError.BadResponse("流式响应不是合法 JSON")
        when (root.text("type")) {
            "response.output_text.delta" -> root.text("delta")?.takeIf { it.isNotEmpty() }?.let {
                streamed.append(it)
                onText(streamed.toString())
            }
            "response.output_item.done" -> (root["item"] as? JsonObject)?.let { items += it }
            "response.completed" -> final = root["response"] as? JsonObject ?: JsonObject(emptyMap())
            "response.incomplete" -> {
                final = root["response"] as? JsonObject ?: JsonObject(emptyMap())
                incomplete = true
            }
            "response.failed" -> throw failureFrom((root["response"] as? JsonObject)?.get("error") as? JsonObject)
            "error" -> throw failureFrom(root["error"] as? JsonObject ?: root)
        }
        return final != null
    }

    private fun failureFrom(e: JsonObject?): ModelError {
        val code = e?.text("code")
        val message = e?.text("message").orEmpty()
        return errorForCode(code, message, e?.text("param"))
            ?: ModelError.BadResponse(listOfNotNull(code, message.take(200)).joinToString(" "))
    }

    /** 服务端没按流式回、直接给了整个 response 对象。 */
    fun fromBody(text: String): ModelReply {
        val root = try { codec.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null }
            ?: throw ModelError.BadResponse("返回内容不是合法 JSON")
        (root["error"] as? JsonObject)?.let { throw failureFrom(it) }
        final = root
        incomplete = root.text("status") == "incomplete"
        return finish()
    }

    fun finish(): ModelReply {
        // 没等到 response.completed / response.incomplete 是连接中途被断开
        val response = final ?: throw ModelError.StreamCut()
        val output = items.ifEmpty { (response["output"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty() }
        val reasoning = mutableListOf<JsonObject>()
        val thinking = StringBuilder()
        val emitted = StringBuilder()
        var refusal = false
        val calls = mutableListOf<Part.ToolCall>()
        output.forEach { item ->
            when (item.text("type")) {
                "reasoning" -> {
                    (item["summary"] as? JsonArray)?.forEach { s -> (s as? JsonObject)?.text("text")?.let(thinking::append) }
                    // 回放用:去掉只在服务端有意义的编号和状态,留下加密的思考
                    replayableOf(item)?.let { reasoning += it }
                }
                "message" -> (item["content"] as? JsonArray)?.forEach { c ->
                    val o = c as? JsonObject ?: return@forEach
                    when (o.text("type")) {
                        "output_text" -> o.text("text")?.let(emitted::append)
                        "refusal" -> refusal = true
                    }
                }
                "function_call" -> calls += readToolCall(item)
            }
        }
        if (emitted.isEmpty() && streamed.isNotEmpty()) emitted.append(streamed)
        val reason = (response["incomplete_details"] as? JsonObject)?.text("reason")
        if ((refusal || (incomplete && reason == "content_filter")) && emitted.isEmpty() && calls.isEmpty()) {
            throw ModelError.ContentRefused()
        }
        val withReasoning = if (reasoning.isNotEmpty() && calls.isNotEmpty()) {
            listOf(calls.first().copy(opaque = JsonArray(reasoning).toString())) + calls.drop(1)
        } else calls
        val parts = buildList {
            if (thinking.isNotEmpty()) add(Part.Thinking(thinking.toString()))
            if (emitted.isNotEmpty()) add(Part.Text(emitted.toString()))
            addAll(withReasoning)
        }
        val stop = when {
            calls.isNotEmpty() -> StopReason.ToolUse
            incomplete && reason == "max_output_tokens" -> StopReason.MaxTokens
            incomplete -> StopReason.Other
            else -> StopReason.EndTurn
        }
        return ModelReply(parts, stop, usageFrom(response["usage"]))
    }

    /** 回放用的思考条目:编号和状态只在服务端有意义,剥掉之后留下加密内容;没有加密内容就不回放。 */
    private fun replayableOf(item: JsonObject): JsonObject? =
        if (item["encrypted_content"] != null) JsonObject(item - "id" - "status") else null

    private fun readToolCall(item: JsonObject): Part.ToolCall {
        val id = item.text("call_id") ?: throw ModelError.BadResponse("工具调用缺少编号")
        val name = item.text("name") ?: throw ModelError.BadResponse("工具调用缺少名称")
        return Part.ToolCall(id, name, item.text("arguments")?.ifEmpty { null } ?: "{}")
    }

    private fun usageFrom(el: JsonElement?): Usage? {
        val u = el as? JsonObject ?: return null
        val input = u.intField("input_tokens") ?: return null
        val cached = (u["input_tokens_details"] as? JsonObject)?.intField("cached_tokens") ?: 0
        val output = u.intField("output_tokens") ?: 0
        val reasoning = ((u["output_tokens_details"] as? JsonObject)?.intField("reasoning_tokens") ?: 0).coerceIn(0, output)
        return Usage(input, output, cached.coerceAtMost(input), reasoning)
    }
}

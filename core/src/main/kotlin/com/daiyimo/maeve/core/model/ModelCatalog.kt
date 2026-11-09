package com.daiyimo.maeve.core.model

import com.daiyimo.maeve.core.net.ProxyFailure
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 服务商清单里的一个模型条目。能力一栏为 null 表示清单没说,由调用方拿自己的初值补,这里不替它猜。
 *
 * 同一个 Key 底下的模型有时分走不同协议,清单会用 `api` 表把每个模型的协议和地址写清楚。[endpoint] 是其中
 * Maeve 说得通的那一种,真正调用时按它挑适配器、拼地址;为 null 时按服务商整体的接口类型来。
 * [unsupportedApis] 是清单写明、而 Maeve 一种也说不来的协议名:照它调用必然失败,因此不进可选范围(见 [usable])。
 * [chat] 记录清单有没有说明它是不是对话模型(`type` 写 chat / embedding / rerank…,或看输出模态里有没有文字);没说就是 null。
 */
data class ListedModel(
    val id: String,
    val displayName: String = "",
    val toolCalls: Boolean? = null,
    val vision: Boolean? = null,
    val contextTokens: Int? = null,
    val reasoning: Boolean? = null,
    val endpoint: ModelEndpoint? = null,
    val unsupportedApis: List<String> = emptyList(),
    val chat: Boolean? = null,
    /** 服务商点明的可选思考档位;null 是没提,空列表是明确没有。档位次序保持服务商给出的原样。 */
    val reasoningEfforts: List<String>? = null,
    /**
     * 服务商点明的思考方式(Anthropic 清单 `capabilities.thinking.types` 里支持的那几种,
     * 如 `adaptive`、`enabled`、`disabled`);没提为 null。
     */
    val thinkingTypes: List<String>? = null,
    /**
     * 清单点明这个模型能走哪几种协议(聚合服务常用 `supported_endpoints` 给出,如 `/chat/completions`、
     * `/messages`);没提为 null。用没列出的接口类型去调必然失败,见 [ModelProtocol.ofPath]。
     */
    val protocols: List<ModelProtocol>? = null,
) {
    val usable: Boolean get() = endpoint != null || unsupportedApis.isEmpty()
}

/** 模型清单 `api` 表里认得的接口协议;[wire] 即表里的键名。 */
enum class ModelProtocol(val wire: String) {
    /** OpenAI 的 Chat Completions 协议,由 [OpenAiCompatModel] 发起调用。 */
    ChatCompletions("chat-completions"),
    /** Anthropic 的 Messages 协议,由 [AnthropicModel] 发起调用。 */
    Messages("messages"),
    /** Gemini 的 generateContent 协议,由 [GeminiModel] 发起调用。 */
    GenerateContent("generate-content"),
    /** OpenAI 的 Responses 协议,由 [ResponsesModel] 发起调用。放在枚举末尾:模型同时写了前几种时,仍旧优先用前几种。 */
    Responses("responses");

    companion object {
        fun of(wire: String?): ModelProtocol? = entries.firstOrNull { it.wire == wire }

        /**
         * 从端点路径反推协议:聚合服务用 `supported_endpoints` 告诉每个模型能走哪些端点
         * (`/chat/completions`、`/messages`、`/responses`、`/v1beta/models/{模型}:generateContent`),只有路径没有协议名。推不出为 null。
         *
         * Gemini 只认 `…:generateContent` 这一种生成端点。裸的 `/models`(含 `/v1/models`、`/provider/v1/models`)
         * 是列模型的端点:列得出来不等于调得通,拿它当「能生成」会让不该试的模型白试一次,所以宁可推不出。
         */
        fun ofPath(path: String?): ModelProtocol? {
            val p = path?.trim()?.trimEnd('/')?.lowercase() ?: return null
            return when {
                p.endsWith("/chat/completions") -> ChatCompletions
                p.endsWith("/messages") -> Messages
                p.endsWith("/responses") -> Responses
                p.endsWith(":generatecontent") -> GenerateContent
                else -> null
            }
        }
    }
}

/**
 * 清单给单个模型单配的接口:协议、该协议的根地址([baseUrl],形如 `…/anthropic/v1`),
 * 以及兼容协议把思考内容放在哪个字段([reasoningField],如 `reasoning_content`;没写为 null)。
 */
data class ModelEndpoint(val protocol: ModelProtocol, val baseUrl: String, val reasoningField: String? = null) {
    /**
     * 交给适配器的 Base URL。版本路径由适配器自己接:[AnthropicModel] 接 `/v1/messages`、[GeminiModel] 接
     * `/v1beta/models/…`,清单地址末尾自带的这一段要先剥掉,否则会重复;Chat Completions 只接 `/chat/completions`、
     * Responses 只接 `/responses`,地址原样交出。
     */
    val adapterBaseUrl: String
        get() {
            val url = baseUrl.trim().trimEnd('/')
            return when (protocol) {
                ModelProtocol.ChatCompletions, ModelProtocol.Responses -> url
                ModelProtocol.Messages -> url.removeSuffix("/v1")
                ModelProtocol.GenerateContent -> url.removeSuffix("/v1beta")
            }
        }
}

/** 列模型为什么失败;[text] 是给人看的原因。 */
enum class ListFailure(val text: String) {
    BadUrl("接口地址不对,要以 http:// 或 https:// 开头"),
    Auth("API Key 无效或没有权限"),
    Quota("账户额度已用完"),
    RateLimited("请求太频繁,请稍后再试"),
    Network("连不上这个地址,请检查网络和接口地址"),
    ProxyUnreachable("连不上网络代理,或代理没有转发这个请求;请检查设置 › 网络代理"),
    ProxyAuth("网络代理拒绝了账号或密码,请检查设置 › 网络代理"),
    NotSupported("这个服务不提供模型列表"),
    Server("服务暂时出错,请稍后再试"),
    BadResponse("返回的内容看不懂,请检查接口地址和接口类型"),
    ;

    companion object {
        /** 请求压根没发出去:错在代理那一段的就照实说,其余都算连不上。 */
        fun of(e: Throwable): ListFailure = when ((e as? ModelError.Proxy)?.failure) {
            ProxyFailure.Unreachable -> ProxyUnreachable
            ProxyFailure.AuthFailed -> ProxyAuth
            null -> Network
        }
    }
}

sealed interface ModelListResult {
    data class Ok(val models: List<ListedModel>) : ModelListResult
    data class Failed(val reason: ListFailure, val detail: String = "") : ModelListResult {
        val message: String get() = if (detail.isEmpty()) reason.text else "${reason.text}($detail)"
    }
}

/**
 * 三家服务商的「列出模型」客户端,各自打一个公开的 GET 接口:
 *
 * - OpenAI 兼容(`GET {base}/models`,Bearer 认证):多数列表只给 id,能力一栏留空、交给调用方的初值;
 *   给了 `context_length`、`architecture.input_modalities`、`supported_parameters` 的(常见于聚合服务)才据实填。
 *   另一种常见写法也认:`capabilities.tools.supported`、`capabilities.reasoning.supported`、`limit.context`、
 *   `modalities.input`(含 `image/…` 即能看图),以及给每个模型单配协议地址的 `api` 表(见 [ListedModel.endpoint])。
 *   ChatGPT 账号列出来的形状不同,是 `models` 表:id 在 `slug`、显示名在 `display_name`,只收 `visibility` 为 `list`
 *   的并保持服务端给出的次序;`input_modalities`、`context_window`、`supported_reasoning_levels` 给了就填。
 * - Anthropic(`GET {base}/v1/models`,用 `after_id` 翻页):`max_input_tokens` 是上下文,
 *   `capabilities.image_input` / `capabilities.thinking` 分别是看图和推理;工具调用一概不给。
 *   可选思考档位照 `capabilities.effort` 加 `capabilities.thinking.types` 逐项搬过来(见 [effortLevels])。
 * - Gemini(`GET {base}/v1beta/models`,用 `pageToken` 翻页):只收支持 `generateContent` 的;
 *   `inputTokenLimit` 是上下文、`thinking` 是推理。工具调用、看图和思考档位都不给。
 */
class ModelCatalog(private val http: OkHttpClient) {
    // 各方法的 headers:每个请求都带的头(见 [ClientHeaders]);Key 的头由这里自己加
    private val codec = Json { ignoreUnknownKeys = true }

    suspend fun openAiCompat(baseUrl: String, apiKey: String?, headers: Map<String, String> = emptyMap()): ModelListResult = run {
        val url = listUrl(baseUrl, "/models") ?: return@run badUrl()
        val root = fetchJson(bearerRequest(url, apiKey, headers)).getOrElse { return@run it.asListFailure() }
        val data = (root["data"] ?: root["models"]) as? JsonArray ?: return@run ModelListResult.Failed(ListFailure.BadResponse)
        ModelListResult.Ok(data.mapNotNull { (it as? JsonObject)?.let(::openAiListItem) }.distinctBy { it.id })
    }

    suspend fun anthropic(baseUrl: String, apiKey: String, headers: Map<String, String> = emptyMap()): ModelListResult = run {
        val base = listUrl(baseUrl, "/v1/models") ?: return@run badUrl()
        val all = mutableListOf<ListedModel>()
        var after: String? = null
        repeat(PAGE_LIMIT) {
            val root = fetchJson(anthropicRequest(anthropicPageUrl(base, after), apiKey, headers))
                .getOrElse { return@run it.asListFailure() }
            val data = root["data"] as? JsonArray ?: return@run ModelListResult.Failed(ListFailure.BadResponse)
            all += data.mapNotNull { (it as? JsonObject)?.let(::anthropicListItem) }
            after = root.text("last_id")
            if (root.flag("has_more") != true || after == null) return@run ModelListResult.Ok(all.distinctBy { it.id })
        }
        ModelListResult.Ok(all.distinctBy { it.id })
    }

    suspend fun gemini(baseUrl: String, apiKey: String, headers: Map<String, String> = emptyMap()): ModelListResult = run {
        val base = listUrl(baseUrl, "/v1beta/models") ?: return@run badUrl()
        val all = mutableListOf<ListedModel>()
        var token: String? = null
        repeat(PAGE_LIMIT) {
            val root = fetchJson(geminiRequest(geminiPageUrl(base, token), apiKey, headers))
                .getOrElse { return@run it.asListFailure() }
            val data = root["models"] as? JsonArray ?: JsonArray(emptyList())
            all += data.mapNotNull { (it as? JsonObject)?.let(::geminiListItem) }
            token = root.text("nextPageToken")?.takeIf { it.isNotEmpty() }
            if (token == null) return@run ModelListResult.Ok(all.distinctBy { it.id })
        }
        ModelListResult.Ok(all.distinctBy { it.id })
    }

    private fun openAiListItem(o: JsonObject): ListedModel? {
        // ChatGPT 账号的清单(`models` 表)id 在 `slug`、显示名在 `display_name`,只收 `visibility` 为 list 的
        o.text("visibility")?.let { if (it != "list") return null }
        val id = (o.text("id") ?: o.text("slug"))?.takeIf { it.isNotBlank() } ?: return null
        val arch = o["architecture"] as? JsonObject
        val modalityLine = arch?.text("modality")
        val inputs = arch?.get("input_modalities").strings()
        val modality = modalityLine?.substringBefore("->")
        val outputs = outputsOf(arch, o, modalityLine)
        val params = o["supported_parameters"].strings()
        val caps = o["capabilities"] as? JsonObject
        val modalIn = (o.child("modalities")?.get("input") ?: o["input_modalities"]).strings()
        val levels = (o["supported_reasoning_levels"] ?: caps?.child("reasoning")?.get("effort")) as? JsonArray
        val api = o.child("api")
        // 没给 `api` 表的聚合服务改用 `supported_endpoints` 标出这个模型能走的端点
        val protocols = o["supported_endpoints"].strings()
            ?.mapNotNull { ModelProtocol.ofPath(it) }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
        // 一个模型写了多种协议时,按 Chat Completions、Messages、generateContent、Responses 的优先次序取第一种说得通的
        val endpoint = endpointOf(api)
        return ListedModel(
            id = id,
            displayName = (o.text("name") ?: o.text("display_name")).orEmpty(),
            toolCalls = toolCallsOf(params, caps),
            vision = visionOf(inputs, modalIn, modality, o, caps),
            contextTokens = contextOf(o),
            reasoning = reasoningOf(params, caps, o, levels),
            endpoint = endpoint,
            reasoningEfforts = effortsOf(levels),
            unsupportedApis = if (endpoint == null) api?.keys?.toList().orEmpty() else emptyList(),
            protocols = protocols,
            chat = isChat(o, outputs),
        )
    }

    /** 输出模态:直接给了 `output_modalities` / `modalities.output` 数组的照取,否则从 `text->image` 这类写法里拆。 */
    private fun outputsOf(arch: JsonObject?, o: JsonObject, modalityLine: String?): List<String>? =
        (arch?.get("output_modalities") ?: o.child("modalities")?.get("output")).strings()
            ?: modalityLine?.takeIf { "->" in it }?.substringAfter("->")?.split('+')

    /** 服务商点名的可选思考档位:`supported_reasoning_levels`(或 `capabilities.reasoning.effort`)里的档位名,去重保序。 */
    private fun effortsOf(levels: JsonArray?): List<String>? = levels?.mapNotNull { level ->
        (((level as? JsonObject)?.get("effort") ?: level) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }
    }?.distinct()

    /** 会不会调用工具:`supported_parameters` 里列了、或 `capabilities` 里几种写法之一标了支持。 */
    private fun toolCallsOf(params: List<String>?, caps: JsonObject?): Boolean? =
        params?.let { "tools" in it } ?: caps?.child("tools")?.flag("supported")
            ?: caps?.flag("function_calling") ?: caps?.flag("tool_calls")

    /** 能不能看图:输入模态里有图(`image` 或 `image/…`)、或服务商另给字段标了。 */
    private fun visionOf(inputs: List<String>?, modalIn: List<String>?, modality: String?, o: JsonObject, caps: JsonObject?): Boolean? =
        inputs?.let { "image" in it } ?: modalIn?.let { m -> m.any { it == "image" || it.startsWith("image/") } }
            ?: modality?.let { "image" in it } ?: o.flag("supports_image_in") ?: caps?.flag("vision")

    private fun contextOf(o: JsonObject): Int? =
        o.number("context_length") ?: o.number("context_window") ?: o.number("max_context_length")
            ?: o.child("limit")?.number("context")

    private fun reasoningOf(params: List<String>?, caps: JsonObject?, o: JsonObject, levels: JsonArray?): Boolean? =
        params?.let { "reasoning" in it || "include_reasoning" in it } ?: caps?.child("reasoning")?.flag("supported")
            ?: o.flag("supports_reasoning") ?: levels?.let { it.isNotEmpty() }

    /** 是不是对话模型:`type` 认得出的直接照说;认不出的再看输出模态——只产图、产声音的不算,产文字的不另作标记。 */
    private fun isChat(o: JsonObject, outputs: List<String>?): Boolean? = when (o.text("type")?.lowercase()) {
        in CHAT_MODEL_TYPES -> true
        in NON_CHAT_MODEL_TYPES -> false
        else -> outputs?.takeIf { "text" !in it }?.let { false }
    }

    /** 按协议逐个给地址的 `api` 表:取 Maeve 会说的第一种协议(枚举次序即优先级)。 */
    private fun endpointOf(api: JsonObject?): ModelEndpoint? = api?.let { a ->
        ModelProtocol.entries.firstNotNullOfOrNull { p ->
            val e = a.child(p.wire) ?: return@firstNotNullOfOrNull null
            val url = e.text("base_url")?.takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
            ModelEndpoint(p, url, e.child("compatibility")?.text("reasoning_field"))
        }
    }

    private fun anthropicListItem(o: JsonObject): ListedModel? {
        val id = o.text("id")?.takeIf { it.isNotBlank() } ?: return null
        val caps = o["capabilities"] as? JsonObject
        val thinking = caps?.child("thinking")
        val types = thinking?.child("types")?.keysMarkedSupported()
        return ListedModel(
            id = id,
            displayName = o.text("display_name").orEmpty(),
            vision = (caps?.get("image_input") as? JsonObject)?.flag("supported"),
            contextTokens = o.number("max_input_tokens"),
            reasoning = thinking?.flag("supported"),
            reasoningEfforts = types?.let { effortLevels(caps?.child("effort"), it) },
            thinkingTypes = types,
        )
    }

    /**
     * Anthropic 模型的可选思考档位:只有接受自适应思考(`adaptive`,档位通过 `output_config.effort` 传)的才给得出,
     * 取 `capabilities.effort` 里标了支持的那几级;接受关掉思考(`disabled`)的,前面再补一档 `none`。
     * 只接受按预算思考(`enabled` + `budget_tokens`)或压根不分档的,可选档位为空。
     */
    private fun effortLevels(effort: JsonObject?, types: List<String>): List<String> {
        val levels = effort?.takeIf { it.flag("supported") == true }?.keysMarkedSupported().orEmpty()
            .sortedBy { EFFORT_DEPTH.indexOf(it).takeIf { i -> i >= 0 } ?: EFFORT_DEPTH.size }
        if ("adaptive" !in types || levels.isEmpty()) return emptyList()
        return listOfNotNull("none".takeIf { "disabled" in types }) + levels
    }

    /** `{"a":{"supported":true},"b":{"supported":false},"supported":true}` 这类对象里标了支持的子项名:`[a]`。 */
    private fun JsonObject.keysMarkedSupported(): List<String> = keys.filter { (this[it] as? JsonObject)?.flag("supported") == true }

    private fun geminiListItem(o: JsonObject): ListedModel? {
        val id = o.text("name")?.removePrefix("models/")?.takeIf { it.isNotBlank() } ?: return null
        val methods = o["supportedGenerationMethods"].strings()
        if (methods != null && "generateContent" !in methods) return null
        return ListedModel(
            id = id,
            displayName = o.text("displayName").orEmpty(),
            contextTokens = o.number("inputTokenLimit"),
            reasoning = o.flag("thinking"),
        )
    }

    /** OpenAI 兼容清单:Bearer 认证,Key 为空(本地服务)就不带头。 */
    private fun bearerRequest(url: HttpUrl, apiKey: String?, headers: Map<String, String>): Request =
        Request.Builder().url(url).get().withHeaders(headers)
            .apply { if (!apiKey.isNullOrEmpty()) header("Authorization", "Bearer $apiKey") }
            .build()

    private fun anthropicRequest(url: HttpUrl, apiKey: String, headers: Map<String, String>): Request =
        Request.Builder().url(url).get().withHeaders(headers)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()

    private fun geminiRequest(url: HttpUrl, apiKey: String, headers: Map<String, String>): Request =
        Request.Builder().url(url).get().withHeaders(headers)
            .header("x-goog-api-key", apiKey)
            .build()

    /** Anthropic 按 `after_id` 翻页:每页 1000 条,`after` 为 null 的是第一页。 */
    private fun anthropicPageUrl(base: HttpUrl, after: String?): HttpUrl =
        base.newBuilder().addQueryParameter("limit", "1000")
            .apply { if (after != null) addQueryParameter("after_id", after) }
            .build()

    /** Gemini 按 `pageToken` 翻页:每页 1000 条,`token` 为 null 的是第一页。 */
    private fun geminiPageUrl(base: HttpUrl, token: String?): HttpUrl =
        base.newBuilder().addQueryParameter("pageSize", "1000")
            .apply { if (token != null) addQueryParameter("pageToken", token) }
            .build()

    private class ListingFailed(val result: ModelListResult.Failed) : Exception(result.message)

    private fun Throwable.asListFailure(): ModelListResult.Failed =
        (this as? ListingFailed)?.result ?: ModelListResult.Failed(ListFailure.Network)

    private suspend fun fetchJson(req: Request): Result<JsonObject> {
        val raw = try {
            HttpSupport.execute(http, req)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(ListingFailed(ModelListResult.Failed(ListFailure.of(e))))
        }
        if (raw.status !in 200..299) return Result.failure(ListingFailed(failureForStatus(raw.status, raw.body)))
        val root = try { codec.parseToJsonElement(raw.body).jsonObject } catch (e: Exception) { null }
            ?: return Result.failure(ListingFailed(ModelListResult.Failed(ListFailure.BadResponse)))
        return Result.success(root)
    }

    private fun failureForStatus(status: Int, body: String): ModelListResult.Failed = when {
        status == 401 || status == 403 -> ModelListResult.Failed(ListFailure.Auth)
        // Gemini 对无效 Key 回 400 INVALID_ARGUMENT,原因写在内容里
        status == 400 && (body.contains("API_KEY_INVALID") || body.contains("API key not valid")) -> ModelListResult.Failed(ListFailure.Auth)
        status == 402 || body.contains("insufficient_quota") -> ModelListResult.Failed(ListFailure.Quota)
        status == 429 -> ModelListResult.Failed(ListFailure.RateLimited)
        status == 404 || status == 405 || status == 501 -> ModelListResult.Failed(ListFailure.NotSupported)
        status in 500..599 -> ModelListResult.Failed(ListFailure.Server, "HTTP $status")
        else -> ModelListResult.Failed(ListFailure.BadResponse, "HTTP $status")
    }

    private fun badUrl() = ModelListResult.Failed(ListFailure.BadUrl)

    private fun listUrl(baseUrl: String, path: String) = (baseUrl.trim().trimEnd('/') + path).toHttpUrlOrNull()

    private fun JsonObject.text(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun JsonObject.child(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.flag(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.number(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }

    /** JSON 数组里的全部字符串条目(数字、布尔、null 都不算);本身不是数组时为 null。 */
    private fun JsonElement?.strings(): List<String>? =
        (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private companion object {
        /** 翻页次数上限,防着服务端一直吐下一页。 */
        const val PAGE_LIMIT = 10
        /** Anthropic `capabilities.effort` 里的档位按字母排;界面要按由浅到深,认不出的档位垫底。 */
        val EFFORT_DEPTH = listOf("low", "medium", "high", "xhigh", "max")
        /** 清单 `type` 一栏的常见取值:算对话模型的,和不算的(向量、重排、画图、语音、审核)。 */
        val CHAT_MODEL_TYPES = setOf("chat", "llm", "language", "text", "vlm", "code")
        val NON_CHAT_MODEL_TYPES = setOf(
            "embedding", "embeddings", "rerank", "reranker", "image", "images", "video", "audio", "tts", "stt", "asr",
            "speech", "transcription", "moderation",
        )
    }
}

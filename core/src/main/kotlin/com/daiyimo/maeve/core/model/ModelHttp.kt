package com.daiyimo.maeve.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 对话适配器以外也要照同一套说法报错的地方(比如语音转文字)用:发一个请求,把 HTTP 状态和报错内容转成 [ModelError]。
 * 映射本身从各适配器原样搬来,适配器改成调这里,避免两份各改各的。
 */
object ModelHttp {
    class Reply(val status: Int, val retryAfter: String?, val body: String) {
        /** 2xx 算这次请求成功了。 */
        val ok: Boolean get() = status in 200..299
    }

    /** 发出请求并读完回复;网络失败抛 [ModelError.Network],取消协程会取消请求。 */
    suspend fun send(http: OkHttpClient, request: Request): Reply =
        HttpSupport.execute(http, request).let { Reply(it.status, it.retryAfter, it.body) }

    /**
     * OpenAI 兼容接口(对话、转写同一套)的错误映射。[model] 是请求的模型名。
     * 先问 [ModelAccessErrors] 认不认得出「这个模型用不了」,认得出就用它的结论;
     * 认不出才按状态码归(见 [openAiStatusError]),顺序即优先级。
     */
    fun openAiError(status: Int, retryAfter: String?, body: String, model: String): ModelError =
        ModelAccessErrors.of(status, body, model) ?: openAiStatusError(status, retryAfter, body, model)

    private fun openAiStatusError(status: Int, retryAfter: String?, body: String, model: String): ModelError = when {
        // 服务商的内容审核拦了这次请求(有的服务商按「法律原因不可用」回它)。重发同一个请求没用,换个说法或换模型
        status == CONTENT_REFUSED -> ModelError.ContentRefused()
        status == UNAUTHORIZED -> ModelError.Auth()
        status == PAYMENT_REQUIRED || body.contains("insufficient_quota") -> ModelError.QuotaExceeded()
        status == TOO_MANY_REQUESTS -> ModelError.RateLimited(HttpSupport.retryAfterMs(retryAfter))
        contextTooLong(status, body) -> ModelError.ContextTooLong()
        status == NOT_FOUND -> ModelError.ModelNotFound(model, ModelAccessErrors.jsonDetail(body))
        status in SERVER_ERRORS -> ModelError.Server(status)
        status == FORBIDDEN -> ModelError.Auth()
        else -> ModelError.BadResponse("HTTP $status ${body.take(200)}")
    }

    /** HTTP 400 的报错说请求超出了模型的上下文(见 [saysContextTooLong])。OpenAI 兼容、Responses、Anthropic 三种接口共用;本机跑的服务三种接口都可能开。 */
    fun contextTooLong(status: Int, body: String): Boolean = status == BAD_REQUEST && saysContextTooLong(body)

    /**
     * 报错内容说请求超出了模型的上下文。各家原话不同(`context_length_exceeded`、「maximum context length」、
     * 「exceeds the available context size」、「is longer than the model's context length」、「exceeds the context window」、
     * 「is greater than the context length」、带图时的「is longer than the maximum model length」…):
     * 认「超出 / 长于 / 大于 / max」后面不远处(同一句里)跟着上下文的长度、大小、上限、窗口或模型长度,不分大小写;
     * 原来认的 `context_length`、「maximum context」照旧;另认一个本机服务旧版的「loaded with context length of only」。
     * 只提到「context window」这类词不算:报错里可能回显了请求里的话。
     */
    internal fun saysContextTooLong(text: String): Boolean = CONTEXT_TOO_LONG.containsMatchIn(text)

    private val CONTEXT_TOO_LONG = Regex(
        """(?:exceed|longer than|larger than|greater than|max)[^.\n]{0,40}?(?:context[ _](?:length|size|limit|window)|model[ _]length)|context_length|maximum context|context length of only""",
        RegexOption.IGNORE_CASE,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Gemini 原生接口的错误映射:先认「这个模型用不了」,再按状态码;429 还要看它 error.status 是不是 RESOURCE_EXHAUSTED。 */
    fun geminiError(status: Int, retryAfter: String?, body: String, model: String): ModelError {
        val err = geminiErrorObject(body)
        val st = (err?.get("status") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val msg = (err?.get("message") as? JsonPrimitive)?.contentOrNull ?: body
        val lower = msg.lowercase()
        ModelAccessErrors.of(status, body, model)?.let { return it }
        return when {
            status == UNAUTHORIZED || status == FORBIDDEN -> ModelError.Auth()
            // 这个模型额度为零的 429 上面已认成用不了;其余 429 按可重试限流处理(其限流与额度文案混用,无法可靠区分)
            status == TOO_MANY_REQUESTS || st == "RESOURCE_EXHAUSTED" -> ModelError.RateLimited(HttpSupport.retryAfterMs(retryAfter))
            status == NOT_FOUND -> ModelError.ModelNotFound(model, ModelAccessErrors.jsonDetail(body))
            status == PAYLOAD_TOO_LARGE || saysTokenOverflow(status, lower) -> ModelError.ContextTooLong()
            status in SERVER_ERRORS -> ModelError.Server(status)
            else -> ModelError.BadResponse("HTTP $status ${body.take(200)}")
        }
    }

    /** Gemini 的 `error` 对象;正文不是 JSON、或没有这一层时算没有,后续按正文整段处理。 */
    private fun geminiErrorObject(body: String): JsonObject? =
        try { json.parseToJsonElement(body).jsonObject["error"] as? JsonObject } catch (e: Exception) { null }

    /** 400 且报错提到 token 超限:Gemini 把上下文超长也说成 token 的事(exceed / too long / maximum 都认)。 */
    private fun saysTokenOverflow(status: Int, lower: String): Boolean =
        status == BAD_REQUEST && lower.contains("token") &&
            (lower.contains("exceed") || lower.contains("too long") || lower.contains("maximum"))

    /** 两套映射都要用的 HTTP 状态码,取个名字,免得在两处看花眼。 */
    private const val BAD_REQUEST = 400
    private const val UNAUTHORIZED = 401
    private const val PAYMENT_REQUIRED = 402
    private const val FORBIDDEN = 403
    private const val NOT_FOUND = 404
    private const val PAYLOAD_TOO_LARGE = 413
    private const val TOO_MANY_REQUESTS = 429
    private const val CONTENT_REFUSED = 451
    private val SERVER_ERRORS = 500..599
}

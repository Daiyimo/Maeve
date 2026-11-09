package com.daiyimo.maeve.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 把服务商的报错正文翻译成「这个模型还能不能用」的结论,各适配器共用。
 *
 * 只看 HTTP 400 / 403 / 404 的正文,外加 429 的一种特例。报错说的不是 Key 写错、
 * 也不是请求本身不对,而是这个模型用不了时,认出是哪一种:
 * - 不支持调用工具(「does not support tools」「function calling is not supported」「不支持工具」…)
 *   → [ModelError.ToolsUnsupported];
 * - 这个模型不能走当前这个接口(服务商要求另一套接口形状,如「Model … must be called via /messages」、
 *   「unsupported_model」…,见 [ENDPOINT_MISMATCH])→ [ModelError.ModelNotAvailable],带上服务端的原话:
 *   换一个模型或换接口类型才行,和「这个账号用不了它」一样处理,接着试下一个;
 * - 这个账号用不了这个模型(「not activated」「not authorized to access this model」
 *   「does not have access to the model」「Model.AccessDenied」「未开通」「无权限」…),或这个模型已经下线
 *   (「no longer available」「has been retired」「model … is deprecated」「已下线」…)
 *   → [ModelError.ModelNotAvailable],带上服务端的原话;
 * - 没有这个模型(「model_not_found」「model not found」「model does not exist」
 *   「not found for API version」「模型不存在」…)→ [ModelError.ModelNotFound],带上 JSON 里的原话(见 [jsonDetail])。
 *
 * 404 没说前两类时由调用方照旧按找不到模型处理(同样带上 [jsonDetail]);认不出为 null,调用方按状态码处理。
 * 401 一律是 Key 的问题,不看。
 *
 * HTTP 429 一般是限流或额度用完,由调用方处理;只有内容说这个账号在这个模型上的额度是零
 * (「limit: 0」「doesn't have a free quota tier」…,见 [QUOTA_ZERO])时算这个模型用不了,
 * 给 [ModelError.ModelNotAvailable]:等多久也不会好,换一个模型才行。
 */
object ModelAccessErrors {
    /** 附带的原话最长保留这么多字,超出截断并加「…」。 */
    private const val DETAIL_MAX = 160

    /** 只在这些状态码上认「模型用不了」;别的状态码交给调用方各自的映射。 */
    private val ACCESS_STATUS = setOf(400, 403, 404)

    private val json = Json { ignoreUnknownKeys = true }

    private val TOOLS_UNSUPPORTED = listOf(
        "does not support tools", "does not support tool", "doesn't support tools", "tools is not supported", "tools are not supported",
        "tool use is not supported", "tool calling is not supported", "function calling is not supported",
        "does not support function calling", "不支持工具", "不支持函数调用", "不支持 function call",
    )

    /**
     * 说「这个模型不能走这个接口」的:一家服务商把模型按接口形状分开放(如某一系模型只挂在 `/messages`,
     * 在 OpenAI 兼容的那条路上调用它一律 400 「must be called via /provider/v1/messages」),换一种接口类型、
     * 或换下面一个模型才通。这不是 Key、地址或网络的问题,当这个模型用不了,接着试下一个。
     * 只在报错说的是模型时才算(见 [aboutTheModel]):「Your API key is not supported for this endpoint」这类说的是 Key,
     * 照旧交给调用方按状态码处理(403 是 Key 的问题,整次保存照旧挡住)。
     */
    private val ENDPOINT_MISMATCH = listOf(
        "unsupported_model",
        "must be called via",
        "only supported in v1/",
        "not supported for this endpoint",
        "unsupported for this endpoint",
    )

    private val NOT_AVAILABLE = listOf(
        "not activated", "not been activated", "model.accessdenied", "model access denied", "accessdenied.unpurchased",
        "not authorized to access this model", "not authorized to use this model", "not authorized to access the model",
        "do not have access to the model", "does not have access to the model", "does not have access to model",
        "do not have access to model", "no access to model", "no permission to access the model", "no permission to use the model",
        "model_not_available", "model is not available", "model not available", "not available for your account",
        "未开通", "没有开通", "尚未开通", "无权限", "没有权限使用", "无权访问", "无权使用",
        // 模型已经下线
        "no longer available", "has been retired", "has been discontinued", "has been shut down",
        "已下线", "已停用", "已下架", "已停止服务",
    )

    /** 说「模型 … 已弃用 / 已下线」:单说 deprecated 也可能是某个参数弃用了,要和 model 连着说才算。 */
    private val MODEL_RETIRED = Regex("""\bmodels?\b[^;,\n]{0,80}?\b(?:is|has been|was)\s+(?:deprecated|retired)|\bdeprecated\s+models?\b""")

    /** 429 里说这个账号在这个模型上的额度为零:数到 `limit: 0`、`"quotaValue": "0"`,或明说没有免费额度档。 */
    private val QUOTA_ZERO = Regex(
        """\blimit\s*:\s*0(?![\d.])|"quotavalue"\s*:\s*"?0"?\s*[,}]|(?:doesn't|does not|don't|do not) have (?:a |any )?free quota tier|has no free quota tier""",
    )

    private val NOT_FOUND = listOf(
        "model_not_found", "model not found", "model does not exist", "model not exist", "no such model", "unknown model",
        "not found for api version", "模型不存在", "找不到模型",
    )

    /**
     * 判别入口。[model] 是本次请求的模型名,返回 null 表示这里认不出,由调用方按状态码处理。
     * 分支顺序即优先级:先看工具支持,再看接口形状和账号权限,最后才是「没有这个模型」。
     */
    fun of(status: Int, body: String, model: String): ModelError? {
        val lower = body.lowercase()
        if (status == 429) return zeroQuota(body, lower, model)
        if (status !in ACCESS_STATUS) return null
        return when {
            hasAny(lower, TOOLS_UNSUPPORTED) -> ModelError.ToolsUnsupported(model)
            hasAny(lower, ENDPOINT_MISMATCH) && aboutTheModel(lower, body, model) -> ModelError.ModelNotAvailable(model, detail(body))
            hasAny(lower, NOT_AVAILABLE) || MODEL_RETIRED.containsMatchIn(lower) -> ModelError.ModelNotAvailable(model, detail(body))
            status != 404 && hasAny(lower, NOT_FOUND) -> ModelError.ModelNotFound(model, jsonDetail(body))
            else -> null
        }
    }

    /** 429 的免费额度为零特例:等多久都不会好,只能换模型,所以算这个模型用不了。 */
    private fun zeroQuota(body: String, lower: String, model: String): ModelError? =
        if (QUOTA_ZERO.containsMatchIn(lower)) ModelError.ModelNotAvailable(model, detail(body)) else null

    /** 报错说的是模型本身:提到 model(含 `unsupported_model`、`"param":"model"`),或原话里直接点了请求的模型名。 */
    private fun aboutTheModel(lower: String, body: String, model: String): Boolean =
        "model" in lower || (model.isNotBlank() && model.lowercase() in (said(body) ?: body).lowercase())

    /** 小写后的正文里出现短语表里的任一条。 */
    private fun hasAny(lower: String, phrases: List<String>): Boolean = phrases.any { it in lower }

    /** 服务端的原话:JSON 里的(见 [said]),不是 JSON 就是整段;太长的截短(见 [clip])。 */
    internal fun detail(body: String): String = clip(said(body) ?: body)

    /** 同 [detail],但只要 JSON 里的原话:不是 JSON 的(地址不对时反向代理回的 HTML 页、空内容)为空。 */
    internal fun jsonDetail(body: String): String = said(body)?.let(::clip).orEmpty()

    /** 去掉首尾空白;超过 [DETAIL_MAX] 个字时截短,末尾加「…」,看得出后面还有。 */
    internal fun clip(text: String): String =
        text.trim().let { if (it.length <= DETAIL_MAX) it else it.take(DETAIL_MAX).trimEnd() + "…" }

    /** JSON 里的原话:依次看 error.message、error(是字符串时)、message、msg、detail,取第一个有的。 */
    private fun said(body: String): String? = rootObject(body)?.errorMessage()

    /** 根对象(有的服务回数组,取第一个)里服务端的那句话;都没有为 null。 */
    private fun JsonObject.errorMessage(): String? {
        val err = this["error"]
        return (err as? JsonObject)?.str("message") ?: (err as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?: str("message") ?: str("msg") ?: str("detail")
    }

    /** 把正文解析成 JSON 对象:数组取第一个元素,解析失败或别的形状算没有。 */
    private fun rootObject(body: String): JsonObject? {
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull()
        return parsed as? JsonObject ?: (parsed as? JsonArray)?.firstOrNull() as? JsonObject
    }

    /** 取 JSON 对象里 [k] 这一项的字符串值;不是字符串算没有。 */
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}

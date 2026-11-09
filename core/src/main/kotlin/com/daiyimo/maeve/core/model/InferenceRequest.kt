package com.daiyimo.maeve.core.model

import com.daiyimo.maeve.core.trace.TraceCall
import com.daiyimo.maeve.core.usage.ModelHttpAttempts
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Maeve 接得上的四种推理接口形状。请求体怎么搭(系统提示放哪个键、消息列表叫什么)、
 * token 账怎么读,都按这一家一套;provider 是这一家的服务商标记。
 */
internal enum class InferenceProtocol(val provider: String, val instruction: String?, val messages: String) {
    Chat("openai_compatible", null, "messages"),
    Responses("openai_compatible", "instructions", "input"),
    Anthropic("anthropic", "system", "messages"),
    Gemini("gcp.gemini", "systemInstruction", "contents");

    /** 从响应包里读这一家的 token 账;账本摆放的位置各家不同,读不出来为 null。 */
    fun usageIn(packet: JsonObject): ReportedTokens? {
        val u = when (this) {
            Chat -> packet["usage"]
            Responses -> (packet["response"] as? JsonObject)?.get("usage") ?: packet["usage"]
            Anthropic -> (packet["message"] as? JsonObject)?.get("usage") ?: packet["usage"]
            Gemini -> packet["usageMetadata"]
        } as? JsonObject ?: return null
        return when (this) {
            Chat -> u.chatUsage()
            Responses -> u.responsesUsage()
            Anthropic -> u.anthropicUsage()
            Gemini -> u.geminiUsage()
        }
    }

    /** Chat Completions 口径:缓存命中/未命中分开报,输入总数没给时用两者相加;缓存数取命中与明细里的较大值。 */
    private fun JsonObject.chatUsage(): ReportedTokens {
        val hit = intField("prompt_cache_hit_tokens")
        val miss = intField("prompt_cache_miss_tokens")
        val input = intField("prompt_tokens") ?: if (hit != null && miss != null) hit + miss else null
        val cache = listOfNotNull(hit, nestedInt("prompt_tokens_details", "cached_tokens")).maxOrNull()
        return ReportedTokens(input, intField("completion_tokens"), cache)
    }

    /** Responses 口径:input/output 直接给,缓存数在 input_tokens_details 里。 */
    private fun JsonObject.responsesUsage(): ReportedTokens =
        ReportedTokens(intField("input_tokens"), intField("output_tokens"), nestedInt("input_tokens_details", "cached_tokens"))

    /** Anthropic 口径:缓存读入和缓存写入都算输入;缓存数列单独报出的读入数。 */
    private fun JsonObject.anthropicUsage(): ReportedTokens {
        val cache = intField("cache_read_input_tokens")
        val input = intField("input_tokens")?.plus((cache ?: 0) + (intField("cache_creation_input_tokens") ?: 0))
        return ReportedTokens(input, intField("output_tokens"), cache)
    }

    /** Gemini 口径:候选与思考分开报,输出是两者之和(都没有才算没有);缓存数单列。 */
    private fun JsonObject.geminiUsage(): ReportedTokens {
        val text = intField("candidatesTokenCount")
        val thoughts = intField("thoughtsTokenCount")
        val output = if (text == null && thoughts == null) null else (text ?: 0) + (thoughts ?: 0)
        return ReportedTokens(intField("promptTokenCount"), output, intField("cachedContentTokenCount"))
    }

    /** 计数字段:读不成整数(缺字段、null、带引号的串)都算没有。 */
    private fun JsonObject.intField(name: String): Int? = (this[name] as? JsonPrimitive)?.intOrNull

    /** 嵌套对象里的计数字段(如 prompt_tokens_details 里的 cached_tokens)。 */
    private fun JsonObject.nestedInt(objectName: String, field: String): Int? =
        ((this[objectName] as? JsonObject)?.get(field) as? JsonPrimitive)?.intOrNull
}

/**
 * 一次失败流里可能只报出了部分计数器:能读出的填上,读不出的留 null。
 * 正常的模型回复不走这里,用各家适配器自己的 Usage 解析。
 */
internal data class ReportedTokens(val input: Int?, val output: Int?, val cached: Int?)

/**
 * 打在推理请求上的标记。同一个协程里还会跑令牌刷新、目录拉取这些非推理请求,
 * 靠这个标记把两者分开:只有带标记的才算模型调用。
 */
internal data class InferenceRequest(val protocol: InferenceProtocol, val payload: JsonObject, val provider: String)

/** 把推理请求体(带标记)post 出去;provider 在打标记时才求值,默认用协议自带的服务商标记。 */
internal suspend inline fun Request.Builder.postInference(
    protocol: InferenceProtocol, payload: JsonObject, provider: () -> String = { protocol.provider },
): Request.Builder {
    val context = currentCoroutineContext()
    // 追踪链路和请求计数都只数带标记的请求；同协程里的令牌刷新、目录请求不带标记，
    // 不会被算成模型调用。
    if (context[TraceCall] != null || context[ModelHttpAttempts] != null) {
        tag(InferenceRequest::class.java, InferenceRequest(protocol, payload, provider()))
    }
    return post(payload.toString().toRequestBody("application/json".toMediaType()))
}

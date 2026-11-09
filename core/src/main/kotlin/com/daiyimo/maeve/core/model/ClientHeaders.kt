package com.daiyimo.maeve.core.model

import okhttp3.Request
import java.util.UUID

/**
 * 一段对话对外的会话号:同一段对话的每个请求(主模型、看图、轻量、起标题、核查、
 * 意图判断、停止后的交代)都用同一个,服务商据此把请求路由到一处、复用提示词缓存。
 * 号由对话 id 推出(UUID v3,不可逆),不把对话 id 本身发出去。
 * 不属于任何对话的请求(连接测试、列模型)每次用 [fresh] 的新号。
 */
@JvmInline
value class ModelSession(
    /** 服务商看到的会话号:不透明字符串,原样作为会话头的值发出去,不解析、不改写。 */
    val id: String,
) {
    companion object {
        /** 由对话 id 稳定推出会话号:同一个对话 id 永远得到同一个号。 */
        fun of(conversationId: String): ModelSession =
            ModelSession(UUID.nameUUIDFromBytes(conversationSalt(conversationId)).toString())

        /** 每次都不重样的新会话号,给不属于任何对话的请求用。 */
        fun fresh(): ModelSession = ModelSession(UUID.randomUUID().toString())
    }
}

/** 推导会话号用的盐:固定前缀拼上对话 id,保证只依赖对话 id、且推不出对话 id 原文。 */
private fun conversationSalt(conversationId: String): ByteArray = "maeve-conversation:$conversationId".toByteArray()

/** 发给模型服务的每个请求都带的头;Key 的头由各适配器自己加,不在这里。 */
object ClientHeaders {
    /**
     * 拼出公共头。[userAgent] 标明是 Maeve 哪个版本(由 App 给,core 不写版本号);
     * [sessionHeader] 是服务商声明要放会话号的头名,没声明(null 或空)就不发会话号——
     * 声明了就以去掉首尾空白后的名字作头名,值放 [ModelSession.id]。
     */
    fun of(userAgent: String, sessionHeader: String?, session: ModelSession): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        declaredSessionHeader(sessionHeader)?.let { put(it, session.id) }
    }

    /** 服务商真声明了头名才返回去掉空白后的名字;null 或纯空白都算没声明。 */
    private fun declaredSessionHeader(sessionHeader: String?): String? =
        sessionHeader?.takeIf { it.isNotBlank() }?.trim()
}

/**
 * 把头逐个设进 OkHttp 请求。OkHttp 的 header() 按名覆盖,所以每个键设一次即为最终值,
 * 同一个键重复设置时后者赢。
 */
internal fun Request.Builder.withHeaders(headers: Map<String, String>): Request.Builder =
    apply { headers.forEach { (k, v) -> header(k, v) } }

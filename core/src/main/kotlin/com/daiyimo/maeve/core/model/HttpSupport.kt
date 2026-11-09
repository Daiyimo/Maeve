package com.daiyimo.maeve.core.model

import com.daiyimo.maeve.core.net.NetProxy
import com.daiyimo.maeve.core.net.ProxyFailure
import com.daiyimo.maeve.core.trace.TraceCall
import com.daiyimo.maeve.core.usage.ModelHttpAttempts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 一次 HTTP 交互的结果:[status] 是状态码,[retryAfter] 是 Retry-After 头原文(没有为 null),
 * [body] 是正文全文(流式路径收完事件后为空)。
 */
internal data class RawResponse(val status: Int, val retryAfter: String?, val body: String, private val onPacket: ((String) -> Unit)? = null) {
    /** 有的聊天端点没要流式也会带回一包 SSE 事件:原样重放给 [accept],并照会一次 trace。 */
    fun replay(events: List<String>, accept: (String) -> Unit) {
        for (event in events) {
            onPacket?.invoke(event)
            accept(event)
        }
    }
}

/** 各适配器共用的 HTTP 执行层:enqueue 发请求、协程取消时取消 HTTP,以及 Retry-After 头的解析。 */
internal object HttpSupport {
    /** 发一次非流式请求,读完整个回复。 */
    suspend fun execute(http: OkHttpClient, req: Request): RawResponse =
        observe(req) { attempt -> executeRaw(http, req, attempt) }.also(::requireProxyAccepted)

    /** HTTP 407 只会是途中的代理要账号密码(或拒了给的),不是服务商的回复。 */
    private fun requireProxyAccepted(raw: RawResponse) {
        if (raw.status == PROXY_AUTH_REQUIRED) throw ModelError.Proxy(ProxyFailure.AuthFailed)
    }

    /** 连接失败:走了代理且错在代理那一段的说成代理的问题,别的照旧算网络失败。 */
    fun networkError(http: OkHttpClient, req: Request, e: IOException): ModelError =
        NetProxy.failure(http, req.url, e)?.let { ModelError.Proxy(it, e) } ?: ModelError.Network(e)

    /**
     * 包一层 trace:请求带了 [InferenceRequest]、当前协程又有 [TraceCall] 时,先开一个 attempt,
     * 把 HTTP 状态和收到的事件记上去;非 2xx 直接把这次尝试标成 error。两样缺一样就原样发。
     */
    private suspend inline fun observe(req: Request, send: (TraceCall.Attempt?) -> RawResponse): RawResponse {
        val wire = req.tag(InferenceRequest::class.java) ?: return send(null)
        val trace = currentCoroutineContext()[TraceCall] ?: return send(null)
        val attempt = trace.sending(wire) ?: return send(null)
        val response = send(attempt)
        if (response.status !in 200..299) {
            trace.finish("error", null)
        } else if (response.body.isNotEmpty()) {
            attempt.packet(response.body)
            return response.copy(onPacket = attempt::packet)
        }
        return response
    }

    /** 带 [InferenceRequest] 的请求才记一次模型 HTTP 尝试,供用量统计。 */
    private suspend fun countAttempt(req: Request) {
        if (req.tag(InferenceRequest::class.java) != null) {
            currentCoroutineContext()[ModelHttpAttempts]?.start()
        }
    }

    /**
     * 发请求、等回复的公共骨架:先记一次尝试,enqueue,协程取消时取消 HTTP。
     * 连接失败和收到响应两个回调由 [respond] 现造——读完整回复和读流式事件各有一套。
     */
    private suspend fun dispatch(
        http: OkHttpClient,
        req: Request,
        respond: (CancellableContinuation<RawResponse>) -> Callback,
    ): RawResponse {
        countAttempt(req)
        return suspendCancellableCoroutine { cont ->
            val call = http.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(respond(cont))
        }
    }

    private suspend fun executeRaw(http: OkHttpClient, req: Request, attempt: TraceCall.Attempt?): RawResponse =
        dispatch(http, req) { cont ->
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeNetworkFailure(http, req, e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        attempt?.httpStatus(it.code)
                        try {
                            if (cont.isActive) cont.resume(it.readFully())
                        } catch (e: IOException) {
                            if (cont.isActive) cont.resumeWithException(ModelError.Network(e))
                        }
                    }
                }
            }
        }

    /**
     * SSE 按事件交付;取消协程会同时取消 HTTP 请求。[onEvent] 返回 true 表示协议层已到
     * 终态(如 OpenAI 的 [DONE]、Anthropic 的 message_stop),读循环就此收尾并关闭连接——
     * 有的服务器发完终态后并不主动关流(经隧道时生成完却静默挂到读超时),
     * 此时不能干等 EOF。调用者仍按各自协议判断有没有正常收尾,没收尾算 [ModelError.StreamCut]。
     * 一个事件都没有、又不是事件流格式的成功响应(比如地址指到了网页)算 [ModelError.BadResponse]。
     */
    suspend fun stream(http: OkHttpClient, req: Request, onEvent: (String) -> Boolean): RawResponse =
        observe(req) { attempt -> streamRaw(http, req, attempt, onEvent) }.also(::requireProxyAccepted)

    private suspend fun streamRaw(http: OkHttpClient, req: Request, attempt: TraceCall.Attempt?, onEvent: (String) -> Boolean): RawResponse =
        dispatch(http, req) { cont ->
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeNetworkFailure(http, req, e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        attempt?.httpStatus(it.code)
                        try {
                            val raw = when {
                                it.code !in 200..299 -> it.readFully()
                                it.header("Content-Type")?.contains("application/json", ignoreCase = true) == true -> it.readFully()
                                else -> readEvents(it, attempt, onEvent) { cont.isActive }
                            }
                            if (cont.isActive) cont.resume(raw)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(if (e is IOException) ModelError.Network(e) else e)
                        }
                    }
                }
            }
        }

    /**
     * 把 SSE 流读成事件并逐个交付:空行分隔事件,`data:` 开头的是数据(行首的 "data:" 和紧随的一个空格去掉),
     * 其余行忽略。读到协议终态或流结束为止;协程被取消([active] 为 false)就停手,后面交给调用方。
     */
    private fun readEvents(response: Response, attempt: TraceCall.Attempt?, onEvent: (String) -> Boolean, active: () -> Boolean): RawResponse {
        val source = response.body?.source() ?: throw ModelError.BadResponse("流式响应没有内容")
        val data = mutableListOf<String>()
        var events = 0
        /** 交付一个攒齐的事件;true 表示协议层已到终态,读循环就此收尾,不再等连接关闭。 */
        fun flush(): Boolean {
            if (data.isEmpty()) return false
            events++
            val event = data.joinToString("\n")
            attempt?.packet(event)
            val terminal = onEvent(event)
            data.clear()
            return terminal
        }
        while (active()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                if (flush()) break
            } else if (line.startsWith("data:")) {
                data += line.removePrefix("data:").removePrefix(" ")
            }
        }
        if (!active()) return RawResponse(response.code, response.header("retry-after"), "")
        flush()
        val eventStream = response.header("Content-Type")?.contains("event-stream", ignoreCase = true) == true
        if (events == 0 && !eventStream) {
            throw ModelError.BadResponse("返回的不是流式内容(Content-Type: ${response.header("Content-Type").orEmpty()})")
        }
        return RawResponse(response.code, response.header("retry-after"), "")
    }

    /** 把整个响应读成 [RawResponse]:状态码、Retry-After 头、正文全文。 */
    private fun Response.readFully(): RawResponse = RawResponse(code, header("retry-after"), body?.string().orEmpty())

    /** 连接失败时收尾:协程还活着,就把分好类的网络/代理错误抛回去。 */
    private fun CancellableContinuation<RawResponse>.resumeNetworkFailure(http: OkHttpClient, req: Request, e: IOException) {
        if (isActive) resumeWithException(networkError(http, req, e))
    }

    /** Retry-After 头(秒,可能带小数)换成毫秒;不是数字算没有。 */
    fun retryAfterMs(header: String?): Long? = header?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong() }

    /** 407:途中的代理要账号密码(或拒了给的)。 */
    private const val PROXY_AUTH_REQUIRED = 407
}

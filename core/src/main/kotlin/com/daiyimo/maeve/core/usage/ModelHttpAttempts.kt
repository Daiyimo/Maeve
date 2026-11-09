package com.daiyimo.maeve.core.usage

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 一次逻辑模型调用里实际交给 HTTP 客户端的推理请求数。
 *
 * 适配器内部的兼容重试与外层重试都经过同一个计数点；计数只随协程上下文传递，
 * 调用之间互不影响，也不写入运行记录。
 */
internal class ModelHttpAttempts : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ModelHttpAttempts>

    private val requests = AtomicInteger()

    /** 已经发出去的请求数。 */
    val started: Int
        get() = requests.get()

    /** 记下又发出去一个请求。 */
    fun start() = requests.incrementAndGet()
}

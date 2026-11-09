package com.daiyimo.maeve.core.usage

import com.daiyimo.maeve.core.model.Usage
import com.daiyimo.maeve.core.record.RunEvent
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 一次运行里经计量的模型调用与实际 HTTP 请求合计。运行把它放进自己的协程上下文,[metered] 套上的模型每次调用结束顺手加一笔,
 * 所以主循环之外的旁路调用(意图判断、核查、看图、压缩……)和失败重试的每一次都算在这次运行名下;
 * 适配器内部的兼容重发增加实际请求数,但不增加逻辑调用数。不在这个上下文里的调用(交出回答后的结局判定、记忆提取)不算,
 * Token 用量页照样都记。只在内存里加,多线程安全。
 */
class RunUsageTally : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RunUsageTally>

    private val logical = AtomicInteger()
    private val requests = AtomicInteger()
    private val logicalWithoutUsage = AtomicInteger()
    private val requestsWithoutUsage = AtomicInteger()
    private val inputTokens = AtomicLong()
    private val outputTokens = AtomicLong()
    private val cachedTokens = AtomicLong()

    /** 记一次逻辑调用及其 [httpAttempts] 次实际请求;只有最终成功回复的 [usage] 可记 token。 */
    fun add(usage: Usage?, httpAttempts: Int) {
        logical.incrementAndGet()
        if (usage == null) logicalWithoutUsage.incrementAndGet()

        val sent = httpAttempts.coerceAtLeast(0)
        requests.addAndGet(sent)
        val reportedRequests = if (usage == null) 0 else 1
        requestsWithoutUsage.addAndGet((sent - reportedRequests).coerceAtLeast(0))

        if (usage == null) return
        inputTokens.addAndGet(usage.inputTokens.toLong())
        outputTokens.addAndGet(usage.outputTokens.toLong())
        cachedTokens.addAndGet(usage.cachedInputTokens.toLong())
    }

    /** 此刻的合计,作为运行记录里的一条 [RunEvent.ModelCalls]。 */
    fun event(atMs: Long) = RunEvent.ModelCalls(
        atMs,
        logical.get(),
        logicalWithoutUsage.get(),
        inputTokens.get(),
        outputTokens.get(),
        cachedTokens.get(),
        requests.get(),
        requestsWithoutUsage.get(),
    )
}

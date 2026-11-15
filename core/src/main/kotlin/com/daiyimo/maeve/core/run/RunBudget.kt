package com.daiyimo.maeve.core.run

import com.daiyimo.maeve.core.model.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** HTTP 适配器内部兼容重试也必须从同一任务预算扣款。 */
internal class RunBudgetContext(val budget: RunBudget) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RunBudgetContext>
}

/** 父/子任务、重试、压缩及辅助模型共用；单调时钟只扣主动执行时间。 */
class RunBudget(
    val maxSteps: Int = 60,
    val maxRequests: Int = 120,
    private val activeMs: Long = 10 * 60_000L,
    private val maxHttpAttempts: Int = maxRequests,
    private val maxReportedTokens: Long = 1_000_000,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    class Exceeded(val limit: String) : IllegalStateException("budget_exceeded:$limit")
    private var steps = 0
    private var requests = 0
    private var httpAttempts = 0
    private var reportedTokens = 0L
    private var missingUsage = false
    private var elapsedNanos = 0L
    private var started = nanoClock()
    private var paused = false
    init { require(maxSteps > 0 && maxRequests > 0 && activeMs > 0 && maxHttpAttempts > 0 && maxReportedTokens > 0) }

    @Synchronized fun pause(waiting: Boolean) {
        if (paused == waiting) return
        val now = nanoClock()
        if (!paused) elapsedNanos += now - started
        started = now
        paused = waiting
    }
    @Synchronized fun remainingMs(): Long = (activeMs - (elapsedNanos + if (paused) 0 else nanoClock() - started) / 1_000_000).coerceAtLeast(0)
    @Synchronized fun check() {
        if (remainingMs() == 0L) throw Exceeded("active_time")
        if (reportedTokens >= maxReportedTokens) throw Exceeded("reported_tokens")
    }
    @Synchronized fun step() { check(); if (steps >= maxSteps) throw Exceeded("steps"); steps++ }
    @Synchronized fun request() { check(); if (requests >= maxRequests) throw Exceeded("requests"); requests++ }
    @Synchronized fun httpAttempt() { check(); if (httpAttempts >= maxHttpAttempts) throw Exceeded("http_requests"); httpAttempts++ }
    @Synchronized private fun report(usage: Usage?) {
        if (usage == null) missingUsage = true
        else reportedTokens += usage.inputTokens.coerceAtLeast(0).toLong() + usage.outputTokens.coerceAtLeast(0).toLong()
    }
    data class Snapshot(val steps: Int, val logicalCalls: Int, val httpAttempts: Int, val reportedTokens: Long, val usageIncomplete: Boolean)
    @Synchronized fun snapshot() = Snapshot(steps, requests, httpAttempts, reportedTokens, missingUsage || httpAttempts > requests)

    fun wrap(model: ChatModel): ChatModel = object : StreamingChatModel {
        override val capabilities get() = model.capabilities
        override val name get() = model.name
        override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply =
            limited { model.complete(system, messages, tools) }
        override suspend fun completeStreaming(system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit): ModelReply =
            limited { if (model is StreamingChatModel) model.completeStreaming(system, messages, tools, onText) else model.complete(system, messages, tools) }
        private suspend fun limited(call: suspend () -> ModelReply): ModelReply {
            request()
            return withContext(RunBudgetContext(this@RunBudget)) {
                val reply = try {
                    withTimeoutOrNull(minOf(remainingMs(), 120_000)) { call() } ?: throw Exceeded("request_time")
                } catch (failure: Throwable) {
                    report(null)
                    throw failure
                }
                report(reply.usage)
                reply
            }
        }
    }
}

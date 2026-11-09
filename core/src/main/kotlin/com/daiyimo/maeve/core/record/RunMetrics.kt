package com.daiyimo.maeve.core.record

import kotlinx.serialization.Serializable

/**
 * 一次运行的量化小结。所有字段都能仅凭运行事件重新算出([of]),写入后独立于
 * 事件文件保留([RunMetricsStore]),因此事件被清理也不影响历史统计。
 *
 * 时间零点取 [RunEvent.TaskStarted],此前加载知识、连接设备等准备不算:
 *
 * - [totalMs]:到 [RunEvent.TaskEnded];未结束的运行算到最后一个事件。
 * - [firstFeedbackMs]:到用户第一次看见实际进展——最早一条真正执行的模型回复
 *   到达([RunEvent.ModelReply.repliedAtMs],旧记录退化为它的 [RunEvent.atMs]),
 *   或最早一条活动记录(回放不经过模型时,第一步完成)。被改派掉的轻量模型
 *   回复用户看不到,不计;回答分段显示时首段没有单独事件,也不计。
 * - [modelCalls]:模型回复条数,即主循环请求次数;其中轻量模型回复计入
 *   [lightCalls],服务商没报用量的计入 [callsWithoutUsage]。[inputTokens]
 *   已经包含 [cachedTokens]。
 * - [allCalls] 等:运行期间全部经计量的外层逻辑调用([RunEvent.ModelCalls]),
 *   含旁路调用与外层重试;[allHttpCalls] 是实际 HTTP 请求数,旧记录没有则为空。
 * - [modelMs]:各次回复的请求耗时之和;[retryWaitMs] 单独存失败请求与退避。
 * - [observeMs]/[actMs]:顶层工具调用的执行耗时。只读类动作(读屏、读网页、
 *   列元素、截图、查资料)算观察,其余算操作。一批动作或页面脚本的外层调用
 *   里,子步中的读计入观察,其余计入操作,不重复计;没有活动的控制类调用
 *   (等待、问用户、请确认、交给用户、说做不成)两边都不算。[uiWaitMs] 是
 *   显式等待界面的时间:单个工具里端口报告的稳定等待(仍属于那次读或操作),
 *   加上一批动作中按条件等界面的时间;后者落在 [otherMs],不算操作。
 * - [otherMs]:总时长减去模型、观察、操作,即等用户、退避、控制调用、核查等。
 * - [steps]:真正执行的回复中的最大步号;[observeCalls]/[actCalls] 是读、操作
 *   各发生几次,含子步。
 * - [outcome]:结束事件的结论,未结束为 unfinished;[failKind] 是失败类别;
 *   [judged] 是交付回答后另判的结局。
 */
@Serializable
data class RunMetrics(
    val runId: String = "",
    val goal: String = "",
    val startedAtMs: Long = 0,
    val totalMs: Long = 0,
    val ended: Boolean = false,
    val firstFeedbackMs: Long? = null,
    val modelCalls: Int = 0,
    val lightCalls: Int = 0,
    val callsWithoutUsage: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedTokens: Long = 0,
    val allCalls: Int? = null,
    val allHttpCalls: Int? = null,
    val allInputTokens: Long? = null,
    val allOutputTokens: Long? = null,
    val modelMs: Long = 0,
    val retryWaitMs: Long = 0,
    val observeMs: Long = 0,
    val actMs: Long = 0,
    val uiWaitMs: Long = 0,
    val observeCalls: Int = 0,
    val actCalls: Int = 0,
    val steps: Int = 0,
    val outcome: String = UNFINISHED,
    val failKind: String? = null,
    val judged: String? = null,
) {
    /** 既不属于模型、观察,也不属于操作的时间;不会为负。 */
    val otherMs: Long get() = (totalMs - modelMs - observeMs - actMs).coerceAtLeast(0)

    /** 输入加输出(缓存命中已经含在输入里)。 */
    val tokens: Long get() = inputTokens + outputTokens

    /** 是否办成:结束结论成功(未核实也算),且事后判定没有说只办到一部分或办不到。 */
    val succeeded: Boolean get() = outcome in SUCCESS_OUTCOMES && judged !in INCOMPLETE_JUDGMENTS

    /**
     * 按事件顺序累计,随时可以 [build]。运行记录写线程用它边写边算,
     * 收尾时不必重读整份记录。内部只保留工具时间数字与活动的种类,不存原文。
     */
    class Builder(private val runId: String = "") {
        private data class ToolSpan(
            val step: Int,
            val source: String,
            val callId: String?,
            val durationMs: Long,
            val waitMs: Long,
        )

        /** (来源, 步号, 调用 id) 定位一次工具调用;活动记录给出它是读还是操作。 */
        private data class CallKey(val source: String, val step: Int, val callId: String?)

        private var startedAt: Long? = null
        private var latestAt = 0L
        private var goalText = ""
        private var finished: RunEvent.TaskEnded? = null
        private var firstFeedbackAt: Long? = null
        private var replyCount = 0
        private var lightReplyCount = 0
        private var noUsageReplies = 0
        private var inputTotal = 0L
        private var outputTotal = 0L
        private var cachedTotal = 0L
        private var allCallsEvent: RunEvent.ModelCalls? = null
        private var modelTime = 0L
        private var retryTime = 0L
        private var highestStep = 0
        private var judgedOutcome: String? = null
        private val toolSpans = mutableListOf<ToolSpan>()
        private val activityKinds = HashMap<CallKey, String>()

        fun add(e: RunEvent) {
            latestAt = maxOf(latestAt, e.atMs)
            when (e) {
                is RunEvent.TaskStarted -> onTaskStarted(e)
                is RunEvent.ModelReply -> onModelReply(e)
                is RunEvent.ToolResult -> toolSpans += ToolSpan(
                    step = e.step,
                    source = e.source,
                    callId = e.callId,
                    durationMs = e.durationMs.coerceAtLeast(0),
                    waitMs = e.waitMs.coerceAtLeast(0),
                )
                is RunEvent.Activity -> onActivity(e)
                is RunEvent.TaskEnded -> if (finished == null) finished = e
                is RunEvent.ModelCalls -> allCallsEvent = e
                is RunEvent.OutcomeJudged -> judgedOutcome = e.outcome
                else -> Unit
            }
        }

        private fun onTaskStarted(e: RunEvent.TaskStarted) {
            if (startedAt != null) return
            startedAt = e.atMs
            goalText = e.goal.take(GOAL_MAX)
        }

        private fun onModelReply(e: RunEvent.ModelReply) {
            replyCount++
            if (e.role == LIGHT_ROLE || !e.acted) lightReplyCount++
            if (e.inputTokens == null && e.outputTokens == null) noUsageReplies++
            inputTotal += e.inputTokens ?: 0
            outputTotal += e.outputTokens ?: 0
            cachedTotal += e.cachedTokens ?: 0
            modelTime += e.latencyMs.coerceAtLeast(0)
            retryTime += e.retryWaitMs.coerceAtLeast(0)
            if (!e.acted) return
            highestStep = maxOf(highestStep, e.step)
            markFeedback(e.repliedAtMs ?: e.atMs)
        }

        private fun onActivity(e: RunEvent.Activity) {
            activityKinds[CallKey(e.source, e.step, e.callId)] = e.kind
            markFeedback(e.atMs)
        }

        private fun markFeedback(at: Long) {
            val previous = firstFeedbackAt
            if (previous == null || at < previous) firstFeedbackAt = at
        }

        /** 没有开始事件(记录创建了却没跑起来)时返回 null。 */
        fun build(): RunMetrics? {
            val start = startedAt ?: return null
            val end = finished
            val kindOf = { span: ToolSpan ->
                activityKinds[CallKey(span.source, span.step, span.callId)]
            }
            val (outer, nested) = toolSpans.partition { it.source !in NESTED_SOURCES }
            var observeTime = 0L
            var actTime = 0L
            for (span in outer) {
                val kind = kindOf(span)
                val children = nested.filter { it.step == span.step }
                // 外层调用耗时已包含子步;先把子步里的读挑出来,剩下的才归操作
                val nestedObserve = children
                    .filter { kindOf(it) == READ_KIND }
                    .sumOf { it.durationMs }
                    .coerceAtMost(span.durationMs)
                when {
                    kind == READ_KIND -> observeTime += span.durationMs
                    children.isNotEmpty() -> {
                        observeTime += nestedObserve
                        actTime += (span.durationMs - nestedObserve - span.waitMs).coerceAtLeast(0)
                    }
                    // 外层 waitMs 是子步之后按条件等界面的时间,不属于操作
                    kind != null -> actTime += (span.durationMs - span.waitMs).coerceAtLeast(0)
                    else -> Unit
                }
            }
            val kinds = activityKinds.values
            return RunMetrics(
                runId = runId,
                goal = goalText,
                startedAtMs = start,
                totalMs = ((end?.atMs ?: latestAt) - start).coerceAtLeast(0),
                ended = end != null,
                firstFeedbackMs = firstFeedbackAt?.let { (it - start).coerceAtLeast(0) },
                modelCalls = replyCount,
                lightCalls = lightReplyCount,
                callsWithoutUsage = noUsageReplies,
                inputTokens = inputTotal,
                outputTokens = outputTotal,
                cachedTokens = cachedTotal,
                allCalls = allCallsEvent?.calls,
                allHttpCalls = allCallsEvent?.httpCalls,
                allInputTokens = allCallsEvent?.inputTokens,
                allOutputTokens = allCallsEvent?.outputTokens,
                modelMs = modelTime,
                retryWaitMs = retryTime,
                observeMs = observeTime,
                actMs = actTime,
                uiWaitMs = outer.sumOf { it.waitMs },
                observeCalls = kinds.count { it == READ_KIND },
                actCalls = kinds.count { it == ACT_KIND },
                steps = highestStep,
                outcome = end?.outcome ?: UNFINISHED,
                failKind = end?.kind,
                judged = judgedOutcome,
            )
        }
    }

    companion object {
        const val UNFINISHED = "unfinished"
        const val GOAL_MAX = 80
        private const val READ_KIND = "observe"
        private const val ACT_KIND = "act"
        private const val LIGHT_ROLE = "light"

        /** 包在外层调用里的子步:一批动作中的一步,或页面脚本里的一个动作。 */
        private val NESTED_SOURCES = setOf("batch", "script")
        private val SUCCESS_OUTCOMES = setOf("succeeded", "unverified")
        private val INCOMPLETE_JUDGMENTS = setOf("partial", "not_possible")

        /** 一次运行的小结;没有开始事件时为空。 */
        fun of(events: List<RunEvent>, runId: String = ""): RunMetrics? {
            val builder = Builder(runId)
            for (event in events) builder.add(event)
            return builder.build()
        }
    }
}

/**
 * 多次运行的汇总。计算成功率、用时与首次反馈的中位数、平均模型往返次数和
 * token,并给出时间去向(各项是各次之和,与总时长之和同口径)。首次反馈只在
 * 有这项数据的运行里取中位数;一次都没有则为空。
 */
data class RunStats(
    val runs: Int,
    val succeeded: Int,
    val medianTotalMs: Long,
    val medianFirstFeedbackMs: Long?,
    val avgModelCalls: Double,
    val avgTokens: Double,
    val totalMs: Long,
    val modelMs: Long,
    val observeMs: Long,
    val actMs: Long,
    val otherMs: Long,
) {
    val successRate: Double get() = if (runs == 0) 0.0 else succeeded.toDouble() / runs

    companion object {
        fun of(runs: List<RunMetrics>): RunStats = RunStats(
            runs = runs.size,
            succeeded = runs.count { it.succeeded },
            medianTotalMs = median(runs.map { it.totalMs }) ?: 0,
            medianFirstFeedbackMs = median(runs.mapNotNull { it.firstFeedbackMs }),
            avgModelCalls = if (runs.isEmpty()) 0.0 else runs.sumOf { it.modelCalls }.toDouble() / runs.size,
            avgTokens = if (runs.isEmpty()) 0.0 else runs.sumOf { it.tokens }.toDouble() / runs.size,
            totalMs = runs.sumOf { it.totalMs },
            modelMs = runs.sumOf { it.modelMs },
            observeMs = runs.sumOf { it.observeMs },
            actMs = runs.sumOf { it.actMs },
            otherMs = runs.sumOf { it.otherMs },
        )

        /** 中位数;偶数个取中间两个的平均并向下取整,空表为空。 */
        fun median(xs: List<Long>): Long? {
            if (xs.isEmpty()) return null
            val sorted = xs.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
        }
    }
}

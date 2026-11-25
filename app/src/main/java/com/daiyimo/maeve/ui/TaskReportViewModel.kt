package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.record.RunStore
import java.io.File

/**
 * 一次操作在回放里的一格。[before] 是实际派发输入前存下的那张画面([RunEvent.Pointer] 自带落点),
 * [after] 是操作生效后的画面([RunEvent.ToolResult.screenshotId],没有时用同一步的 [RunEvent.Media] 兜底)。
 *
 * 和示意标记不同,这里的落点是执行入口真实报出来的像素坐标,不做归一化、也不推测。
 * 旧记录没有 Pointer,只有画面,按 [legacy] 退化成一格结果画面。
 */
data class PlaybackStep(
    val step: Int,
    /** 给人看的中文说法;判断阶段要用 [rawTool]。 */
    val tool: String?,
    val rawTool: String? = null,
    val before: RunEvent.Pointer? = null,
    val afterId: Int? = null,
    val succeeded: Boolean = true,
    val legacy: Boolean = false,
    val callId: String? = null,
    val failure: String? = null,
) {
    /** 没有采集的帧不是丢失文件,也不生成空白播放项。 */
    val frames: List<PlaybackFrame>
        get() = if (legacy) listOfNotNull(afterId?.let { PlaybackFrame(it, null) })
        else listOfNotNull(
            before?.screenshotId?.let { PlaybackFrame(it, false, before) },
            afterId?.let { PlaybackFrame(it, true) },
        )
}

/** [after] 为 false 是操作前、true 是操作后、null 是旧记录里只有一张的画面。 */
data class PlaybackFrame(val screenshotId: Int, val after: Boolean?, val pointer: RunEvent.Pointer? = null) {
    val resultOnly: Boolean get() = after != false
}

/** 播放序列里的一帧;[stepIndex] 指回 [PlaybackStep] 的下标。 */
data class ReplayFrame(val stepIndex: Int, val frame: PlaybackFrame) {
    val resultOnly: Boolean get() = frame.resultOnly
}

data class ReportPage(val title: String, val stepIndex: Int, val screenshotId: Int)
data class PlaybackStage(val stepIndex: Int, val title: String, val screenshotId: Int? = null)

data class TaskReport(
    val header: TimelineHeader,
    val answer: String,
    val playback: List<PlaybackStep>,
    val frames: List<ReplayFrame> = emptyList(),
    val pages: List<ReportPage> = emptyList(),
    val stages: List<PlaybackStage> = emptyList(),
    val missingFrames: Int = 0,
)

/**
 * 只读运行记录,不持有工具分发器、设备端口或任务执行入口。
 *
 * 画面序列按 (step, tool, callId) 把「派发前的落点」和「执行后的结果」配成一格:
 * 批里的子步 callId 形如 `b.1`,外层批调用在生产环境不存画面,所以外层不占格。
 */
class TaskReportViewModel(
    private val store: RunStore,
    val runId: String,
    private val toolLabels: ToolLabels = ToolLabels(com.daiyimo.maeve.core.assembly.knownTools()),
) {
    fun load(): TaskReport? {
        val timeline = RunTimelineViewModel(store, runId, toolLabels).load() as? TimelineUi.Ready ?: return null
        val events = store.events(runId)
        val answer = store.summary(runId)?.answerOrReason
            ?: events.filterIsInstance<RunEvent.TaskEnded>().lastOrNull()?.answerOrReason.orEmpty()
        val steps = playbackSteps(events, toolLabels::label)
        val all = steps.flatMapIndexed { index, step -> step.frames.map { ReplayFrame(index, it) } }
        val available = all.filter { screen(it.frame.screenshotId)?.length()?.let { n -> n > 0 } == true }
        val pages = keyPages(events, answer, steps, available)
        return TaskReport(timeline.header, answer, steps, available, pages,
            stages(steps, available, pages), all.size - available.size)
    }

    fun screen(id: Int): File? = store.screen(runId, id)

    companion object {
        const val FRAME_DURATION_MS = 2_400L
        const val TRANSITION_MS = 360
        private const val BEFORE_DURATION_MS = 1_200L

        internal fun playbackSteps(events: List<RunEvent>, toolLabel: (String) -> String = { it }): List<PlaybackStep> {
            val pointers = events.filterIsInstance<RunEvent.Pointer>()
            val results = events.filterIsInstance<RunEvent.ToolResult>()
            val media = events.filterIsInstance<RunEvent.Media>().distinctBy { it.mediaId }

            // 先按调用配对;callId 是执行入口写进记录的,不靠步号猜批次子动作。
            val paired = LinkedHashMap<String, PlaybackStep>()
            fun key(step: Int, tool: String, callId: String?) = "$step|$tool|${callId.orEmpty()}"
            for (p in pointers) {
                val k = key(p.step, p.tool, p.callId)
                paired[k] = (paired[k] ?: PlaybackStep(p.step, toolLabel(p.tool), p.tool, callId = p.callId)).copy(before = p)
            }
            for (r in results) {
                val k = key(r.step, r.tool, r.callId)
                val failure = RunTimelineViewModel.failureLabel(r.resultJson)
                paired[k] = (paired[k] ?: PlaybackStep(r.step, toolLabel(r.tool), r.tool, callId = r.callId)).copy(
                    afterId = r.screenshotId,
                    succeeded = failure == null,
                    failure = failure,
                )
            }

            // 每张存下的画面只归一格:ToolResult 已认领的先扣掉,剩下的按步号补给同一步里还没画面的调用。
            // 批外层在生产环境不存画面,不能把子步的画面借过来重复播一遍。
            val claimed = paired.values.mapNotNull { it.afterId }.toSet()
            val free = media.filter { it.mediaId !in claimed }.groupBy { it.step }
            val steps = paired.values.map { s ->
                if (s.afterId != null || s.before != null) s
                else s.copy(afterId = free[s.step]?.firstOrNull()?.mediaId)
            }.filter { it.before != null || it.afterId != null }.toMutableList()

            // 剩下没人认领的画面(旧记录、只读屏幕的步)单独成格,只承诺这是当时存下的画面。
            val taken = steps.flatMap { listOfNotNull(it.afterId, it.before?.screenshotId) }.toSet()
            steps += media.filter { it.mediaId !in taken }
                .map { PlaybackStep(it.step, null, afterId = it.mediaId, legacy = true) }
            val callOrder = mutableMapOf<String, Int>()
            val mediaOrder = mutableMapOf<Int, Int>()
            events.forEachIndexed { index, event ->
                when (event) {
                    is RunEvent.Pointer -> event.callId?.let { callOrder.getOrPut(it) { index } }
                    is RunEvent.ToolResult -> event.callId?.let { callOrder.getOrPut(it) { index } }
                    is RunEvent.Media -> mediaOrder.getOrPut(event.mediaId) { index }
                    else -> Unit
                }
            }
            fun order(step: PlaybackStep): Int = step.callId?.let(callOrder::get)
                ?: step.afterId?.let(mediaOrder::get)
                ?: Int.MAX_VALUE
            return steps.sortedWith(compareBy({ it.step }, { order(it) }, { it.afterId ?: Int.MAX_VALUE }))
        }

        /** 关键页和阶段按证据截图定位,不能用打开它之前的动作画面代替。 */
        internal fun framePosition(frames: List<ReplayFrame>, screenshotId: Int?, stepIndex: Int = 0): Int {
            val exact = frames.indexOfFirst { it.frame.screenshotId == screenshotId }
            return if (exact >= 0) exact else frames.indexOfFirst { it.stepIndex >= stepIndex }.coerceAtLeast(0)
        }

        internal fun frameDuration(frame: ReplayFrame): Long =
            if (frame.frame.after == false) BEFORE_DURATION_MS else FRAME_DURATION_MS

        /**
         * 关键页优先用代码判定的回答来源([RunEvent.Sources]),它比在正文里找字符串更可靠;
         * 旧记录没有 Sources 时,退回「观测编号 → 实际调用 → 结果画面」并核对正文确实引用过。
         */
        internal fun keyPages(
            events: List<RunEvent>,
            answer: String,
            steps: List<PlaybackStep>,
            frames: List<ReplayFrame>,
        ): List<ReportPage> {
            val sources = events.filterIsInstance<RunEvent.Sources>().lastOrNull()?.items.orEmpty()
            val observations = events.filterIsInstance<RunEvent.Observed>().associateBy { it.id }
            val activities = events.filterIsInstance<RunEvent.Activity>()
            val fromSources = sources.mapNotNull { source ->
                val observed = observations[source.obsId]
                val title = pageTitle(source.title ?: observed?.title, observed?.text.orEmpty(), answer)
                    ?: return@mapNotNull null
                // 画面编号直中时,格号由帧自己给出;否则才按观测所在步/实际调用去找。
                val hit = source.mediaIds.lastOrNull { id -> frames.any { it.frame.screenshotId == id } }
                if (hit != null) {
                    val frame = frames.last { it.frame.screenshotId == hit }
                    return@mapNotNull ReportPage(title, frame.stepIndex, hit)
                }
                val index = steps.indexOfFirst { it.step == source.step }.takeIf { it >= 0 }
                    ?: observed?.let { o -> activities.firstOrNull { o.id > 0 && o.id in it.obsIds }
                        ?.let { a -> steps.indexOfFirst { it.step == a.step && it.callId == a.callId } } }
                    ?: return@mapNotNull null
                val shot = frames.lastOrNull { it.stepIndex == index && it.resultOnly }?.frame?.screenshotId
                    ?: return@mapNotNull null
                ReportPage(title, index, shot)
            }.distinctBy { normalize(it.title) }.takeLast(4)
            if (fromSources.isNotEmpty()) return fromSources

            val normalizedAnswer = normalize(answer)
            val pages = events.filterIsInstance<RunEvent.Observed>().mapNotNull { obs ->
                val activity = activities.firstOrNull { obs.id > 0 && obs.id in it.obsIds && it.callId != null }
                    ?: return@mapNotNull null
                val index = steps.indexOfFirst { it.callId == activity.callId && it.step == activity.step }
                if (index < 0) return@mapNotNull null
                val frame = frames.lastOrNull { it.stepIndex == index && it.resultOnly } ?: return@mapNotNull null
                val title = candidates(obs.title, obs.text).map(String::trim)
                    .filter { it.length in TITLE_RANGE && '\t' !in it && !it.startsWith(APP_PREFIX) }
                    .filter { normalize(it).length >= 8 && normalize(it) in normalizedAnswer }
                    .maxByOrNull { normalize(it).length } ?: return@mapNotNull null
                ReportPage(title, index, frame.frame.screenshotId)
            }.distinctBy { normalize(it.title) }.takeLast(4)
            if (pages.isNotEmpty()) return pages
            // 没有可确认的引用页面,只承诺这是记录结束时的画面。
            return frames.lastOrNull()
                ?.let { listOf(ReportPage(tr(R.string.report_final_page), it.stepIndex, it.frame.screenshotId)) }.orEmpty()
        }

        /** 来源标题优先;标题不够说明问题时,从观测正文里挑一句正文引用过的。 */
        private fun pageTitle(title: String?, text: String, answer: String): String? {
            val normalizedAnswer = normalize(answer)
            title?.trim()?.takeIf { it.length in TITLE_RANGE && '\t' !in it && normalize(it).length >= 4 }?.let { return it }
            return candidates(title, text).map(String::trim)
                .filter { it.length in TITLE_RANGE && '\t' !in it && !it.startsWith(APP_PREFIX) }
                .filter { normalize(it).length >= 8 && normalize(it) in normalizedAnswer }
                .maxByOrNull { normalize(it).length }
        }

        private val TITLE_RANGE = 8..90
        private const val APP_PREFIX = "当前 App:"

        private fun candidates(title: String?, text: String): List<String> =
            listOfNotNull(title) + text.lineSequence().toList()

        internal fun stages(steps: List<PlaybackStep>, frames: List<ReplayFrame>, pages: List<ReportPage>): List<PlaybackStage> {
            val available = frames.map { it.stepIndex }.distinct()
            if (available.isEmpty()) return emptyList()
            val result = mutableListOf(PlaybackStage(available.first(), tr(R.string.playback_stage_start)))
            available.firstOrNull { steps[it].rawTool in INPUT_TOOLS }
                ?.let { result += PlaybackStage(it, tr(R.string.playback_stage_input)) }
            result += pages.map { PlaybackStage(it.stepIndex, it.title, it.screenshotId) }
            // 同一步既是开始/输入阶段又有证据页时,保留能直达证据的入口。
            return result.asReversed().distinctBy { it.stepIndex }.sortedBy { it.stepIndex }
        }

        private val INPUT_TOOLS = setOf("phone_type_text", "phone_clear_text", "web_type_text")

        private fun normalize(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

        internal fun excerpt(answer: String): String {
            val text = answer.trim()
            return if (text.length <= 240) text else text.take(240).trimEnd() + "…"
        }
    }
}


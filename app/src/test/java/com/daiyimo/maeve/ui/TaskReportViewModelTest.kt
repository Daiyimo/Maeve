package com.daiyimo.maeve.ui

import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.verify.AnswerSource
import com.daiyimo.maeve.i18n.ChineseTexts
import com.daiyimo.maeve.record.RunStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TaskReportViewModelTest {
    @get:Rule val texts = ChineseTexts()

    @get:Rule val tmp = TemporaryFolder()
    private val store by lazy { RunStore(tmp.root.resolve("files"), { null }, 30, { 1L }) { it } }

    private fun pointer(step: Int, tool: String, shot: Int, callId: String? = null) =
        RunEvent.Pointer(1, step, tool, 1080, 2400, "touch", x = 540, y = 1200,
            screenshotId = shot, callId = callId)

    private fun result(step: Int, tool: String, shot: Int?, callId: String? = null, ok: Boolean = true) =
        RunEvent.ToolResult(1, step, tool, if (ok) """{"ok":true}""" else """{"ok":false,"error":"ref_expired"}""",
            true, shot, callId = callId)

    /** 操作前的落点帧和操作后的结果帧配成一格,顺序是先前后后。 */
    @Test fun pairsBeforePointerWithAfterResult() {
        val events = listOf(
            pointer(1, "phone_tap", 1, "a"),
            result(1, "phone_tap", 2, "a"),
            RunEvent.Media(1, 2, 1, "com.example"),
        )
        val steps = TaskReportViewModel.playbackSteps(events)
        val step = steps.single()
        assertEquals(1, step.before?.screenshotId)
        assertEquals(2, step.afterId)
        assertEquals(listOf(1, 2), step.frames.map { it.screenshotId })
        assertEquals(listOf(false, true), step.frames.map { it.after })
        assertTrue("落点只画在操作前那一帧", step.frames.first().pointer != null)
        assertNull(step.frames.last().pointer)
    }

    /** 批里的子步按 callId 各自成格;外层批调用没有画面,不占格也不借子步的帧。 */
    @Test fun batchSubStepsKeepTheirOwnFramesAndOuterBatchTakesNone() {
        val events = listOf(
            pointer(3, "phone_tap", 5, "b.1"),
            result(3, "phone_tap", 6, "b.1"),
            pointer(3, "phone_tap", 7, "b.2"),
            result(3, "phone_tap", 8, "b.2"),
            result(3, "do_steps", null, "b"),
            RunEvent.Media(1, 6, 3, null),
            RunEvent.Media(1, 8, 3, null),
        )
        val steps = TaskReportViewModel.playbackSteps(events)
        assertEquals(listOf("b.1", "b.2"), steps.map { it.callId })
        assertEquals(listOf(5, 6, 7, 8), steps.flatMap { it.frames.map { f -> f.screenshotId } })
    }

    /** 失败说明和时间线共用同一套错误说法,不另编一套。 */
    @Test fun failureUsesTheTimelineWording() {
        val events = listOf(pointer(1, "phone_tap", 1, "a"), result(1, "phone_tap", 2, "a", ok = false))
        val step = TaskReportViewModel.playbackSteps(events).single()
        assertFalse(step.succeeded)
        assertEquals(RunTimelineViewModel.failureLabel("""{"ok":false,"error":"ref_expired"}"""), step.failure)
    }

    /** 旧记录只有画面:退化成结果帧,不编造落点。 */
    @Test fun legacyRecordsDegradeToResultOnlyFrames() {
        val events = listOf(RunEvent.Media(1, 4, 1, null), RunEvent.Media(1, 5, 2, null))
        val steps = TaskReportViewModel.playbackSteps(events)
        assertEquals(listOf(4, 5), steps.map { it.afterId })
        assertTrue(steps.all { it.legacy && it.before == null })
        assertTrue(steps.flatMap { it.frames }.all { it.after == null && it.pointer == null })
    }

    /** 关键页优先用代码判定的回答来源,画面编号直中时格号由帧自己给出。 */
    @Test fun keyPagesComeFromRecordedSources() {
        val run = store.open("查余额")
        run.event(RunEvent.Observed(1, 2, "页面上显示余额 128 元", id = 7, title = "账户余额 128 元"))
        run.event(pointer(2, "phone_tap", 1, "a"))
        run.event(result(2, "phone_tap", 2, "a"))
        run.media(1, byteArrayOf(1, 2, 3))
        run.media(2, byteArrayOf(1, 2, 3))
        run.event(RunEvent.Sources(3, listOf(AnswerSource(7, "read", "com.example.bank", "账户余额 128 元", 2, listOf(2)))))
        run.event(RunEvent.TaskEnded(4, "succeeded", "账户余额 128 元"))
        run.close()
        val report = TaskReportViewModel(store, run.runId).load()!!
        assertEquals("账户余额 128 元", report.answer)
        val page = report.pages.single()
        assertEquals("账户余额 128 元", page.title)
        assertEquals(2, page.screenshotId)
        // 操作前后两帧同属一格;关键页指向那一格,不是帧序号。
        assertEquals(0, page.stepIndex)
        assertEquals(listOf(1, 2), report.frames.map { it.frame.screenshotId })
        // 同一步既是开始阶段又有证据页时,保留能直达证据的那个入口。
        assertEquals(listOf("账户余额 128 元"), report.stages.map { it.title })
        assertEquals(listOf(2), report.stages.map { it.screenshotId })
    }

    /** 没有可确认的引用页面时,只承诺这是记录结束时的画面。 */
    @Test fun withoutSourcesTheOnlyPromiseIsTheFinalPage() {
        val run = store.open("打开设置")
        run.event(pointer(1, "phone_tap", 1, "a"))
        run.event(result(1, "phone_tap", 2, "a"))
        run.media(1, byteArrayOf(1, 2, 3))
        run.media(2, byteArrayOf(1, 2, 3))
        run.event(RunEvent.TaskEnded(3, "succeeded", "已打开"))
        run.close()
        val report = TaskReportViewModel(store, run.runId).load()!!
        assertEquals(listOf("结束时页面"), report.pages.map { it.title })
        assertEquals(report.frames.last().frame.screenshotId, report.pages.single().screenshotId)
    }

    /** 没存下或已被清理的画面不进播放序列,只报跳过几张。 */
    @Test fun missingFilesAreSkippedNotPlayed() {
        val run = store.open("看页面")
        run.event(pointer(1, "phone_tap", 1, "a"))
        run.event(result(1, "phone_tap", 2, "a"))
        run.media(1, byteArrayOf(1, 2, 3))
        // 编号 2 没有落盘
        run.event(RunEvent.TaskEnded(3, "succeeded", "好了"))
        run.close()
        val report = TaskReportViewModel(store, run.runId).load()!!
        assertEquals(listOf(1), report.frames.map { it.frame.screenshotId })
        assertEquals(1, report.missingFrames)
    }

    /** 记录被清理后报告为空,页面据此说「已过期」。 */
    @Test fun expiredRecordHasNoReport() {
        assertNull(TaskReportViewModel(store, "20260101-000000-abcdef").load())
    }

    /** 操作前那一帧停得短一些,让人看清落点再看结果。 */
    @Test fun beforeFramesAreShorterThanResultFrames() {
        val before = ReplayFrame(0, PlaybackFrame(1, false))
        val after = ReplayFrame(0, PlaybackFrame(2, true))
        assertTrue(TaskReportViewModel.frameDuration(before) < TaskReportViewModel.frameDuration(after))
        assertEquals(TaskReportViewModel.FRAME_DURATION_MS, TaskReportViewModel.frameDuration(after))
    }

    /** 阶段直达优先它自己那张证据画面。 */
    @Test fun stagePositionPrefersItsOwnEvidenceFrame() {
        val frames = listOf(ReplayFrame(0, PlaybackFrame(1, false)), ReplayFrame(0, PlaybackFrame(2, true)),
            ReplayFrame(1, PlaybackFrame(3, true)))
        assertEquals(2, TaskReportViewModel.framePosition(frames, 3))
        assertEquals(2, TaskReportViewModel.framePosition(frames, 99, stepIndex = 1))
        assertEquals(0, TaskReportViewModel.framePosition(frames, null))
    }

    /** 输入阶段按工具原名认,不按中文说法。 */
    @Test fun inputStageUsesTheRawToolName() {
        val steps = listOf(
            PlaybackStep(1, "点按", "phone_tap", afterId = 1),
            PlaybackStep(2, "输入文字", "phone_type_text", afterId = 2),
        )
        val frames = listOf(ReplayFrame(0, PlaybackFrame(1, true)), ReplayFrame(1, PlaybackFrame(2, true)))
        val stages = TaskReportViewModel.stages(steps, frames, emptyList())
        assertEquals(listOf("开始任务", "输入与查找"), stages.map { it.title })
        assertEquals(listOf(0, 1), stages.map { it.stepIndex })
    }
}

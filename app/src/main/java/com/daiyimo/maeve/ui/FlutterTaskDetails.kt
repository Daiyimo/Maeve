package com.daiyimo.maeve.ui

/** Preserve the native execution evidence; a count is not an execution log. */
internal object FlutterTaskDetails {
    fun exchange(ex: Exchange): Map<String, Any?> = mapOf(
        "runId" to ex.runId?.takeUnless { it.startsWith("interrupted:") },
        "executionSteps" to ex.steps.map { step ->
            mapOf("step" to step.step, "title" to step.title, "detail" to step.subtitle,
                "state" to step.state.name.lowercase(java.util.Locale.ROOT), "reason" to step.reason,
                "actions" to ex.stepActions[step.step].orEmpty().map { action ->
                    mapOf("label" to action.label, "target" to action.target,
                        "effect" to action.effect, "failed" to action.failed)
                })
        },
        "waitingForModel" to ex.waitingForModel,
        "confirmationKey" to (ex.status as? ExchangeStatus.NeedsConfirmation)?.key,
        "confirmationTitle" to (ex.status as? ExchangeStatus.NeedsConfirmation)?.title,
        "options" to (ex.status as? ExchangeStatus.NeedsInput)?.options,
        "resumable" to (ex.status as? ExchangeStatus.Paused)?.resumable,
    )

    fun timeline(ui: TimelineUi): Map<String, Any?> = when (ui) {
        is TimelineUi.Ready -> mapOf(
            "available" to true, "running" to ui.header.running,
            "goal" to ui.header.goal, "outcome" to ui.header.outcome,
            "verdict" to ui.header.verdict, "duration" to ui.header.duration,
            "tokens" to ui.header.tokens?.toMap(),
            "timingSummary" to RunTimelineViewModel.timingLabel(StepTiming(
                ui.steps.mapNotNull { it.timing.modelMs }.takeIf { it.isNotEmpty() }?.sum(),
                ui.steps.mapNotNull { it.timing.toolMs }.takeIf { it.isNotEmpty() }?.sum(),
                ui.steps.sumOf { it.timing.retryMs }, ui.steps.sumOf { it.timing.waitMs })),
            "steps" to ui.steps.map { step -> mapOf(
                "step" to step.step, "title" to (step.tool ?: "模型回复"),
                "reason" to step.said, "detail" to listOfNotNull(step.detail, step.result).joinToString("\n"),
                "state" to if (step.tags.any { it.tone == TagTone.Bad } || step.failed) "wait" else "dim",
                "failed" to step.failed,
                "screenshotId" to step.screenshotId,
                "timingLabel" to RunTimelineViewModel.timingLabel(step.timing),
                "tokens" to step.tokens?.toMap(),
                "timing" to mapOf("modelMs" to step.timing.modelMs, "toolMs" to step.timing.toolMs, "waitMs" to step.timing.waitMs, "retryMs" to step.timing.retryMs),
                "declaredPoint" to step.declaredPoint.takeIf { step.pointers.isEmpty() },
                "pointers" to step.pointers.map { p -> mapOf("x" to p.x, "y" to p.y,
                    "width" to p.width, "height" to p.height, "bounds" to p.bounds, "method" to p.method,
                    "clicks" to p.clicks, "screenshotId" to p.screenshotId) },
                "tags" to step.tags.map { mapOf("text" to it.text, "tone" to it.tone.name.lowercase(java.util.Locale.ROOT)) },
                "actions" to step.activities.map { a -> mapOf("label" to a.label, "target" to a.target, "effect" to a.effect, "failed" to a.failed) },
            ) },
        )
        else -> mapOf("available" to false, "running" to false, "steps" to emptyList<Any>())
    }

    /** 任务报告:结局 + 回答正文 + 关键页 + 可播放的画面序列。落点是执行入口报出的真实像素。 */
    fun report(report: TaskReport?): Map<String, Any?> {
        if (report == null) return mapOf("available" to false)
        return mapOf(
            "available" to true,
            "running" to report.header.running,
            "goal" to report.header.goal,
            "outcome" to report.header.outcome,
            "verdict" to report.header.verdict,
            "duration" to report.header.duration,
            "criteria" to report.header.criteria,
            "answer" to report.answer,
            "missingFrames" to report.missingFrames,
            "pages" to report.pages.map { mapOf("title" to it.title, "stepIndex" to it.stepIndex, "screenshotId" to it.screenshotId) },
            "stages" to report.stages.map { mapOf("title" to it.title, "stepIndex" to it.stepIndex, "screenshotId" to it.screenshotId) },
            "frames" to report.frames.map { f -> mapOf(
                "stepIndex" to f.stepIndex, "screenshotId" to f.frame.screenshotId,
                "after" to f.frame.after, "durationMs" to TaskReportViewModel.frameDuration(f),
                "step" to report.playback[f.stepIndex].step,
                "title" to report.playback[f.stepIndex].tool,
                "failure" to report.playback[f.stepIndex].failure,
                "pointer" to f.frame.pointer?.let { pointer(it) },
            ) },
        )
    }

    private fun pointer(p: com.daiyimo.maeve.core.record.RunEvent.Pointer): Map<String, Any?> = mapOf(
        "x" to p.x, "y" to p.y, "width" to p.width, "height" to p.height,
        "bounds" to p.bounds, "method" to p.method, "clicks" to p.clicks, "screenshotId" to p.screenshotId,
    )
}

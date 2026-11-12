package com.daiyimo.maeve.ui

/** Preserve the native execution evidence; a count is not an execution log. */
internal object FlutterTaskDetails {
    fun exchange(ex: Exchange): Map<String, Any?> = mapOf(
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
            "steps" to ui.steps.map { step -> mapOf(
                "step" to step.step, "title" to (step.tool ?: "模型回复"),
                "reason" to step.said, "detail" to listOfNotNull(step.detail, step.result).joinToString("\n"),
                "state" to if (step.tags.any { it.tone == TagTone.Bad } || step.failed) "wait" else "dim",
                "failed" to step.failed,
                "screenshotId" to step.screenshotId,
                "tags" to step.tags.map { mapOf("text" to it.text, "tone" to it.tone.name.lowercase(java.util.Locale.ROOT)) },
                "actions" to step.activities.map { a -> mapOf("label" to a.label, "target" to a.target, "effect" to a.effect, "failed" to a.failed) },
            ) },
        )
        else -> mapOf("available" to false, "running" to false, "steps" to emptyList<Any>())
    }
}

package com.daiyimo.maeve.core.tool

import kotlinx.serialization.Serializable

/** 模型显式报告的任务计划;currentStep 是 1-based,界面只展示到当前步骤。 */
@Serializable
data class TaskPlan(
    val title: String,
    val steps: List<PlanStep>,
    val currentStep: Int,
) {
    val visibleSteps: List<PlanStep> get() = steps.take(currentStep)

    fun stopped(): TaskPlan = copy(steps = steps.mapIndexed { index, step ->
        val n = index + 1
        val status = when {
            n < currentStep -> PlanStepStatus.Done
            n > currentStep -> PlanStepStatus.Pending
            else -> PlanStepStatus.Stopped
        }
        step.copy(status = status)
    })

    fun finished(success: Boolean): TaskPlan = copy(steps = steps.mapIndexed { index, step ->
        val n = index + 1
        val status = when {
            n < currentStep -> PlanStepStatus.Done
            n > currentStep -> PlanStepStatus.Pending
            success -> PlanStepStatus.Done
            else -> PlanStepStatus.Failed
        }
        step.copy(status = status)
    })

    companion object {
        fun of(title: String, steps: List<String>, currentStep: Int): TaskPlan? {
            val cleanTitle = title.trim()
            val cleanSteps = steps.map(String::trim)
            if (cleanTitle.length !in 2..60 || cleanSteps.size !in 1..8 || currentStep !in 1..cleanSteps.size) return null
            if (cleanSteps.any { it.length !in 2..240 }) return null
            return TaskPlan(cleanTitle, cleanSteps.mapIndexed { index, text ->
                PlanStep(text, if (index + 1 < currentStep) PlanStepStatus.Done else if (index + 1 == currentStep) PlanStepStatus.Current else PlanStepStatus.Pending)
            }, currentStep)
        }
    }
}

@Serializable
data class PlanStep(val text: String, val status: PlanStepStatus)

@Serializable
enum class PlanStepStatus { Pending, Current, Done, Failed, Waiting, Stopped }

package com.daiyimo.maeve.core.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPlanTest {
    @Test fun currentStepIsExplicitAndOnlyVisibleStepsAreExposed() {
        val plan = TaskPlan.of("整理资料", listOf("打开文件", "读取内容", "汇总答案"), 2)!!
        assertEquals("整理资料", plan.title)
        assertEquals(2, plan.currentStep)
        assertEquals(listOf("打开文件", "读取内容"), plan.visibleSteps.map { it.text })
        assertEquals(listOf(PlanStepStatus.Done, PlanStepStatus.Current, PlanStepStatus.Pending), plan.steps.map { it.status })
    }

    @Test fun invalidPlansAreRejected() {
        assertNull(TaskPlan.of("", listOf("步骤一"), 1))
        assertNull(TaskPlan.of("标题", emptyList(), 1))
        assertNull(TaskPlan.of("标题", listOf(" "), 1))
        assertNull(TaskPlan.of("标题", listOf("步骤一"), 0))
        assertNull(TaskPlan.of("标题", listOf("步骤一"), 2))
        assertNull(TaskPlan.of("标题", List(9) { "步骤$it" }, 1))
    }

    @Test fun terminalStatesNeverMarkFutureStepsDone() {
        val plan = TaskPlan.of("整理资料", listOf("打开文件", "读取内容", "汇总答案"), 2)!!
        assertEquals(listOf(PlanStepStatus.Done, PlanStepStatus.Failed, PlanStepStatus.Pending), plan.finished(false).steps.map { it.status })
        assertEquals(listOf(PlanStepStatus.Done, PlanStepStatus.Stopped, PlanStepStatus.Pending), plan.stopped().steps.map { it.status })
        assertEquals(
            listOf(PlanStepStatus.Done, PlanStepStatus.Done, PlanStepStatus.Pending),
            plan.finished(true).steps.map { it.status },
        )
        assertTrue(plan.finished(true).steps.none { it.status == PlanStepStatus.Current })
    }
}

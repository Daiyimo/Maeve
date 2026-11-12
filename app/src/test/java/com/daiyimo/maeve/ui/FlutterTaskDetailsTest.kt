package com.daiyimo.maeve.ui

import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.i18n.ChineseTexts
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FlutterTaskDetailsTest {
    @get:Rule val texts = ChineseTexts()

    @Test fun liveActionsRetainTheirFailureAndReason() {
        val ex = Exchange("打开设置", listOf(StepRow(1, "点击系统设置", "没有生效", StepState.Wait, "查看权限")),
            ExchangeStatus.Working, true, stepActions = mapOf(1 to listOf(StepAction("点击", "设置", "没有生效", true))))
        val rows = FlutterTaskDetails.exchange(ex)["executionSteps"] as List<*>
        val first = rows.single() as Map<*, *>
        assertEquals("点击系统设置", first["title"])
        assertEquals("查看权限", first["reason"])
        assertEquals(true, ((first["actions"] as List<*>).single() as Map<*, *>)["failed"])
    }

    @Test fun modelWaitingIsNotFabricatedAsAnExecutedStep() {
        val waiting = FlutterTaskDetails.exchange(Exchange("你好", emptyList(), ExchangeStatus.Working, true, waitingForModel = true))
        assertEquals(emptyList<Any>(), waiting["executionSteps"])
        assertEquals(true, waiting["waitingForModel"])
    }

    @Test fun persistedActionFailuresReachTheFlutterTimeline() {
        val steps = RunTimelineViewModel.steps(listOf(RunEvent.Activity(1, 1, "act", "点击", "设置", "operation_failed", failed = true)))
        val header = TimelineHeader("打开设置", emptyList(), "失败", false, "", null, 1)
        val dto = FlutterTaskDetails.timeline(TimelineUi.Ready(header, steps))
        val row = (dto["steps"] as List<*>).single() as Map<*, *>
        assertEquals("wait", row["state"])
        assertEquals(true, ((row["actions"] as List<*>).single() as Map<*, *>)["failed"])
    }

    @Test fun confirmationCardsKeepTheNativeRequestKey() {
        val dto = FlutterTaskDetails.exchange(Exchange("继续", emptyList(), ExchangeStatus.NeedsConfirmation("需要确认", "目标", 42), true))
        assertEquals(42, dto["confirmationKey"])
        assertEquals("需要确认", dto["confirmationTitle"])
    }

    @Test fun toolFailureWithoutAnActivityStillMarksItsTimelineStepFailed() {
        val steps = RunTimelineViewModel.steps(listOf(
            RunEvent.ToolResult(1, 1, "do_steps", "{\"ok\":false,\"error\":\"invalid_arguments\"}", false, null)))
        val header = TimelineHeader("打开设置", emptyList(), "", false, "", null, 1)
        val dto = FlutterTaskDetails.timeline(TimelineUi.Ready(header, steps))
        val row = (dto["steps"] as List<*>).single() as Map<*, *>
        assertEquals(true, row["failed"])
    }
}

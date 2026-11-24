package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.tool.execute
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁屏恢复不是普通 guard warning:旧屏幕准备好的动作必须证明未投递。 */
class ScreenRecoveryTest {
    private class TapScreen : ScreenPort by FakeScreen() {
        val taps = mutableListOf<String>()
        private val shown = listOf(
            UiElement("@e1", "button", "设置", 900, 2300, listOf(800, 940, 999, 999), null, true, false, false),
        )
        override suspend fun snapshot() = ScreenSnapshot("s1", "com.chat", shown, 1080, 2400)
        override suspend fun peek() = snapshot()
        override suspend fun tapRef(ref: String, snapshotId: String): ActionResult =
            ActionResult.Ok(changed = true).also { taps += ref }
    }

    private val sink = object : RunSink { override fun event(e: com.daiyimo.maeve.core.record.RunEvent) {} }

    @Test fun lockCycleBetweenPreparationAndDispatchDoesNotSendTap() = runTest {
        val screen = TapScreen()
        val tools = toolDispatcher(screen)
        tools.execute(Part.ToolCall("observe", "phone_observe", "{}"))
        var generation = 0L
        var gates = 0
        val executor = ActionExecutor(
            tools, OperationGuards(), null,
            gate = { gates++; if (gates == 2) generation++ },
            recorder = ActionRecorder(sink), screenGeneration = { generation },
        )

        val out = executor.run(Part.ToolCall("tap", "phone_tap", """{"ref":"@e1","snapshotId":"s1"}"""), Origin.Model(1)) as ActionOutcome.Ran

        assertEquals(2, gates)
        assertEquals(JsonPrimitive(false), out.content["executed"])
        assertEquals(JsonPrimitive("not_sent"), out.content["delivery"])
        assertEquals(JsonPrimitive("screen_changed_during_lock"), out.content["error"])
        assertTrue(screen.taps.isEmpty())
    }

    @Test fun initialUnlockAlsoInvalidatesTargetsResolvedBeforeTheGate() = runTest {
        val screen = TapScreen()
        val tools = toolDispatcher(screen)
        tools.execute(Part.ToolCall("observe", "phone_observe", "{}"))
        var generation = 0L
        val executor = ActionExecutor(
            tools, OperationGuards(), null,
            gate = { generation++ }, recorder = ActionRecorder(sink), screenGeneration = { generation },
        )

        val out = executor.run(Part.ToolCall("tap", "phone_tap", """{"ref":"@e1","snapshotId":"s1"}"""), Origin.Model(1)) as ActionOutcome.Ran

        assertEquals(JsonPrimitive("screen_changed_during_lock"), out.content["error"])
        assertTrue(screen.taps.isEmpty())
    }

    @Test fun replayAlsoRejectsTargetsWhenGenerationChangesBeforeDispatch() = runTest {
        val screen = TapScreen()
        val tools = toolDispatcher(screen)
        tools.execute(Part.ToolCall("observe", "phone_observe", "{}"))
        var reads = 0
        val executor = ActionExecutor(
            tools, OperationGuards(), null, gate = { error("replay gate is owned by caller") },
            recorder = ActionRecorder(sink), screenGeneration = { if (reads++ == 0) 0L else 1L },
        )

        val out = executor.run(Part.ToolCall("tap", "phone_tap", """{"ref":"@e1","snapshotId":"s1"}"""), Origin.Replay) as ActionOutcome.Ran

        assertEquals(JsonPrimitive("screen_changed_during_lock"), out.content["error"])
        assertEquals(JsonPrimitive("not_sent"), out.content["delivery"])
        assertTrue(screen.taps.isEmpty())
    }

    @Test fun unchangedScreenRunsAfterBothGateChecks() = runTest {
        val screen = TapScreen()
        val tools = toolDispatcher(screen)
        tools.execute(Part.ToolCall("observe", "phone_observe", "{}"))
        var gates = 0
        val executor = ActionExecutor(
            tools, OperationGuards(), null,
            gate = { gates++ }, recorder = ActionRecorder(sink),
        )

        val out = executor.run(Part.ToolCall("tap", "phone_tap", """{"ref":"@e1","snapshotId":"s1"}"""), Origin.Model(1)) as ActionOutcome.Ran

        assertEquals(2, gates)
        assertEquals(JsonPrimitive(true), out.content["ok"])
        assertEquals(listOf("@e1"), screen.taps)
    }
}

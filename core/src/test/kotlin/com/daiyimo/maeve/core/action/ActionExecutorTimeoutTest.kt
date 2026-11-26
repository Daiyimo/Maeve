package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutorTimeoutTest {
    @Test fun scriptActionCapIsEnforced() = runTest {
        val screen = object : ScreenPort by FakeScreen() {
            override suspend fun tapXY(x: Int, y: Int): ActionResult {
                delay(60_000)
                return ActionResult.Ok(changed = true)
            }
        }
        val executor = ActionExecutor(toolDispatcher(screen), OperationGuards(), null,
            gate = {}, recorder = ActionRecorder(RunSink.NONE))
        val outcome = executor.run(Part.ToolCall("c", "phone_tap", """{"x":1,"y":1}"""), Origin.Script(1))
        assertTrue("$outcome", outcome is ActionOutcome.Ran)
        val content = (outcome as ActionOutcome.Ran).exec.content
        assertEquals("action_timeout", (content["error"] as? JsonPrimitive)?.content)
    }
}

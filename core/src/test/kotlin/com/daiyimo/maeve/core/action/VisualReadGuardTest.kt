package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.access.AppAccessPolicy
import com.daiyimo.maeve.core.access.AppAccessState
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.VisualFrame
import com.daiyimo.maeve.core.uistate.VisibleTextLine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Visual-first reads have no element refs; their OCR text is the screen fingerprint. */
class VisualReadGuardTest {
    private class VisualScreen : ScreenPort by FakeScreen() {
        override val appAccess = AppAccessPolicy { AppAccessState() }
        var text = "first screen"
        override suspend fun currentPackage() = "com.tencent.mm"
        override suspend fun viewport() = 1080 to 2400
        override suspend fun isProtected() = false
        override suspend fun visualFrame(withImage: Boolean, timeoutMs: Long) =
            VisualFrame(null, listOf(VisibleTextLine(text, listOf(0, 0, 100, 100))), 1080, 2400)
    }

    @Test fun changingOcrIsProgressEvenWhenElementsAreEmpty() = runTest {
        val screen = VisualScreen()
        val tools = toolDispatcher(screen)
        val guards = OperationGuards()
        val executor = ActionExecutor(tools, guards, null, gate = {}, recorder = ActionRecorder(RunSink.NONE))
        repeat(8) { index ->
            screen.text = "screen $index"
            val out = executor.run(Part.ToolCall("look-$index", "phone_observe", "{}"), Origin.Model(index + 1))
            assertTrue("$index: $out", out is ActionOutcome.Ran)
            assertNull("screen $index must not be a repeated screen: ${guards.stop}", guards.stop)
        }
        assertTrue("new OCR screens must count as progress", guards.progressVersion >= 8)
    }

    @Test fun sameOcrWithNewSnapshotIdsStillStopsRepeatedLooks() = runTest {
        val screen = VisualScreen()
        val tools = toolDispatcher(screen)
        val guards = OperationGuards()
        val executor = ActionExecutor(tools, guards, null, gate = {}, recorder = ActionRecorder(RunSink.NONE))
        repeat(5) { index ->
            val out = executor.run(Part.ToolCall("look-$index", "phone_observe", "{}"), Origin.Model(index + 1))
            assertTrue(out is ActionOutcome.Ran)
            assertNull(guards.stop)
        }
        val sixth = executor.run(Part.ToolCall("look-5", "phone_observe", "{}"), Origin.Model(6))
        assertTrue(sixth is ActionOutcome.Ran)
        assertEquals("repeating_action", guards.stop?.code)
    }
}

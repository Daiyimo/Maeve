package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.PointerCapture
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.testing.button
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PointerEvidenceTest {
    private suspend fun execute(pointer: PointerCapture, byRef: Boolean = false): Pair<List<RunEvent>, Map<Int, ByteArray>> {
        val events = mutableListOf<RunEvent>()
        val images = mutableMapOf<Int, ByteArray>()
        val sink = object : RunSink { override fun event(e: RunEvent) { events += e } }
        val screen = object : ScreenPort by FakeScreen() {
            override suspend fun snapshot() = ScreenSnapshot("s1", "com.test", listOf(button("@e1", "按钮", y = 300)), 1200, 2670)
            override suspend fun peek() = snapshot()
            override suspend fun tapXY(x: Int, y: Int) = ActionResult.Ok(changed = true, pointer = pointer)
            override suspend fun tapRef(ref: String, snapshotId: String) = ActionResult.Ok(changed = true, pointer = pointer)
        }
        val executor = ActionExecutor(toolDispatcher(screen), OperationGuards(), null, gate = {}, autonomous = true,
            recorder = ActionRecorder(sink, saveScreen = { id, bytes -> images[id] = bytes }))
        val args = if (byRef) """{"element":"按钮"}""" else """{"x":250,"y":300,"target":"按钮"}"""
        val result = executor.run(Part.ToolCall("actual", "phone_tap", args), Origin.Model(1))
        assertEquals(JsonPrimitive(true), (result as ActionOutcome.Ran).content["ok"])
        assertFalse("Evidence must not add model input", result.content.containsKey("pointer"))
        return events to images
    }

    @Test fun thePipelineRecordsActualJitteredPixelsAndTheBeforeFrame() = runTest {
        val (events, images) = execute(PointerCapture(1200, 2670, "touch", 301, 802, beforePng = byteArrayOf(7, 8)))
        val pointer = events.filterIsInstance<RunEvent.Pointer>().single()
        assertEquals(301, pointer.x); assertEquals(802, pointer.y)
        assertArrayEquals(byteArrayOf(7, 8), images[pointer.screenshotId])
        assertEquals(pointer, RunEvent.decode(RunEvent.encode(pointer)))
    }

    @Test fun accessibilityInputRecordsBoundsWithoutInventingATouchPoint() = runTest {
        val (events, _) = execute(PointerCapture(1200, 2670, "accessibility", bounds = listOf(20, 30, 300, 100)))
        val pointer = events.filterIsInstance<RunEvent.Pointer>().single()
        assertNull(pointer.x); assertNull(pointer.y)
        assertEquals(listOf(20, 30, 300, 100), pointer.bounds)
        assertNull(pointer.screenshotId)
    }

    @Test fun stableRefTranslationRetainsTheInputBeforeFrame() = runTest {
        val (events, images) = execute(PointerCapture(1200, 2670, "accessibility", bounds = listOf(20, 30, 300, 100), beforePng = byteArrayOf(9)), byRef = true)
        val p = events.filterIsInstance<RunEvent.Pointer>().single()
        assertArrayEquals(byteArrayOf(9), images[p.screenshotId])
    }

    @Test fun invalidInputEvidenceIsNotPlottedOrAssignedAScreenshot() = runTest {
        val (events, images) = execute(PointerCapture(1200, 2670, "touch", -1, 10, beforePng = byteArrayOf(7)))
        assertTrue(events.none { it is RunEvent.Pointer })
        assertTrue(images.isEmpty())
    }
}

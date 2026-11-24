package com.daiyimo.maeve.core.cap.phone

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.ActionDelivery
import com.daiyimo.maeve.core.tool.execute
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneGestureTimingTest {

    @Test fun dragForwardsHoldAndRestAsOneNativeTimeline() = runTest {
        val screen = RecordingScreen()
        val result = toolDispatcher(screen).execute(Part.ToolCall("g", "phone_drag",
            """{"startX":100,"startY":200,"endX":800,"endY":700,"durationMs":1000,"holdMs":200,"restMs":300}"""))
        assertEquals(ActionDelivery.Sent, result.delivery)
        assertEquals(listOf(1000L), screen.durations)
        assertEquals(listOf(200L), screen.holds)
        assertEquals(listOf(300L), screen.rests)
        assertEquals(1, screen.paths.size)
    }

    @Test fun holdAndRestMustLeaveTimeForMovement() = runTest {
        val screen = RecordingScreen()
        val result = toolDispatcher(screen).execute(Part.ToolCall("g", "phone_drag",
            """{"startX":100,"startY":200,"endX":800,"endY":700,"durationMs":500,"holdMs":300,"restMs":300}"""))
        assertEquals(JsonPrimitive("invalid_arguments"), result.content["error"])
        assertTrue(screen.paths.isEmpty())
    }

    @Test fun zeroDistanceDragIsRejectedWithoutDispatch() = runTest {
        val screen = RecordingScreen()
        val result = toolDispatcher(screen).execute(Part.ToolCall("g", "phone_drag", """{"startX":100,"startY":200,"endX":100,"endY":200}"""))
        assertEquals(JsonPrimitive("invalid_arguments"), result.content["error"])
        assertTrue(screen.paths.isEmpty())
    }
}

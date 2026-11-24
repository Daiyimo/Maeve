package com.daiyimo.maeve.core.cap.phone

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.ActionDelivery
import com.daiyimo.maeve.core.tool.execute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneGestureToolsTest {
    @Test fun longPressUsesOneContinuousHold() = runTest {
        val screen = RecordingScreen()
        val result = toolDispatcher(screen).execute(Part.ToolCall("g", "phone_long_press", """{"x":500,"y":500}"""))
        assertEquals(listOf(600L), screen.durations)
        assertEquals(1, screen.paths.size)
        assertEquals(1, screen.paths.single().size)
        val path = screen.paths.single().single()
        val point = path.first()
        assertEquals(2, path.size)
        assertTrue(path.all { it == point })
        assertEquals(540, point.first)
        assertTrue(point.second in 1200..1201)
        assertEquals(ActionDelivery.Sent, result.delivery)
    }

    @Test fun dragUsesContinuousPathAndRestingDefaultDuration() = runTest {
        val screen = RecordingScreen()
        val result = toolDispatcher(screen).execute(Part.ToolCall("g", "phone_drag", """{"startX":100,"startY":200,"endX":800,"endY":700}"""))
        assertEquals(listOf(800L), screen.durations)
        assertEquals(listOf(0L), screen.holds)
        assertEquals(listOf(0L), screen.rests)
        assertEquals(3, screen.paths.single().single().size)
        assertEquals(ActionDelivery.Sent, result.delivery)
    }

}

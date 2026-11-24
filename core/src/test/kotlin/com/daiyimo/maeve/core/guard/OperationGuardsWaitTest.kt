package com.daiyimo.maeve.core.guard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationGuardsWaitTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun repeatedWaitCallsOnTheLastScreenStopAtSix() {
        for ((tool, args) in listOf(
            "wait" to obj("""{"seconds":0.2}"""),
            "phone_wait" to obj("""{"condition":"stable","timeoutMs":0}"""),
        )) {
            val g = OperationGuards()
            val perCall = (1..6).map { g.beforeAction(tool, args) + g.frame("$tool-$it", null) }
            assertTrue("$tool", perCall.dropLast(1).flatten().isEmpty())
            val stop = perCall.last().single()
            assertEquals("repeating_action", stop.code)
            assertEquals(Severity.Stop, stop.severity)
            assertEquals(6, stop.evidence["repeats"]!!.jsonPrimitive.int)
        }
    }

    @Test fun ownerWaitResetsOnlyCallHistory() {
        val g = OperationGuards()
        val args = obj("""{"direction":"down"}""")
        repeat(5) { i -> assertTrue((g.beforeAction("phone_scroll", args) + g.frame("a$i", 1L)).isEmpty()) }
        g.noteOwnerWait()
        repeat(5) { i -> assertTrue((g.beforeAction("phone_scroll", args) + g.frame("b$i", 1L)).isEmpty()) }
        assertNull(g.stop)
        val stop = (g.beforeAction("phone_scroll", args) + g.frame("b5", 1L)).single()
        assertEquals("repeating_action", stop.code)
        assertEquals(Severity.Stop, stop.severity)
    }

    @Test fun ownerWaitDoesNotResetReadLoop() {
        val g = OperationGuards()
        g.firstSeen(1L)
        repeat(4) { assertTrue(g.read("phone_observe", g.firstSeen(1L)).isEmpty()) }
        g.noteOwnerWait()
        val fifth = g.read("phone_observe", g.firstSeen(1L)).single()
        assertEquals("read_loop", fifth.code)
        assertEquals(Severity.Warn, fifth.severity)
    }

    @Test fun ordinaryWaitDoesNotResetCallHistory() {
        val g = OperationGuards()
        val args = obj("""{"direction":"down"}""")
        repeat(5) { i -> assertTrue((g.beforeAction("phone_scroll", args) + g.frame("a$i", 1L)).isEmpty()) }
        assertTrue(g.beforeAction("phone_wait", obj("""{"condition":"stable"}""")).isEmpty())
        assertTrue(g.frame("wait", null).isEmpty())
        assertNull(g.stop)
        // 普通等待也进入调用窗口,但不重置历史:它只打断连续同调用。
        repeat(5) { i -> assertTrue((g.beforeAction("phone_scroll", args) + g.frame("b$i", 1L)).isEmpty()) }
        assertNull(g.stop)
        val stop = (g.beforeAction("phone_scroll", args) + g.frame("b5", 1L)).single()
        assertEquals("repeating_action", stop.code)
        assertEquals(Severity.Stop, stop.severity)
    }
}

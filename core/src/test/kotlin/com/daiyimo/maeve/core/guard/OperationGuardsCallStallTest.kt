package com.daiyimo.maeve.core.guard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationGuardsCallStallTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun exactCallSignatureOnlyIgnoresObjectKeyOrder() {
        assertEquals(
            exactCallSignature("phone_tap", obj("""{"x":101,"y":149,"reason":"one"}""")),
            exactCallSignature("phone_tap", obj("""{"reason":"one","y":149,"x":101}""")),
        )
        assertNotEquals(
            exactCallSignature("phone_tap", obj("""{"x":101,"y":149,"reason":"one"}""")),
            exactCallSignature("phone_tap", obj("""{"x":105,"y":149,"reason":"one"}""")),
        )
        assertNotEquals(
            exactCallSignature("phone_tap", obj("""{"x":101,"y":149,"reason":"one"}""")),
            exactCallSignature("phone_tap", obj("""{"x":101,"y":149,"reason":"two"}""")),
        )
        assertNotEquals(
            exactCallSignature("phone_swipe", obj("""{"startX":10,"startY":90,"endX":15,"endY":10}""")),
            exactCallSignature("phone_swipe", obj("""{"startX":30,"startY":70,"endX":31,"endY":20}""")),
        )
    }

    @Test fun sameCallWatcherUsesFullArgumentsRatherThanNormalizedHeuristics() {
        val g = OperationGuards()
        val verdicts = (1..6).map { i ->
            val args = obj("""{"x":${if (i % 2 == 1) 101 else 105},"y":149,"reason":"$i"}""")
            g.beforeAction("phone_tap", args) + g.frame("s$i", 1L)
        }.flatten()
        assertTrue(verdicts.toString(), verdicts.none { it.code == "repeating_action" })
        assertNull(g.stop)
    }

    @Test fun sameCallOnSameScreenStopsAtSix() {
        val g = OperationGuards()
        val args = obj("""{"direction":"down"}""")
        val perCall = (1..6).map { g.beforeAction("phone_scroll", args) + g.frame("s$it", 1L) }
        assertTrue(perCall.dropLast(1).flatten().isEmpty())
        val stop = perCall.last().single()
        assertEquals("repeating_action", stop.code)
        assertEquals(Severity.Stop, stop.severity)
        assertEquals(6, stop.evidence["repeats"]!!.jsonPrimitive.int)
        assertEquals("true", stop.evidence["sameScreen"]!!.jsonPrimitive.content)
        assertNull(stop.nudge)
        assertEquals(stop, g.stop)
    }

    @Test fun sameCallOnChangingScreensKeepsGoing() {
        val g = OperationGuards()
        val args = obj("""{"direction":"down"}""")
        val verdicts = (1..40).map { g.beforeAction("phone_scroll", args) + g.frame("s$it", it.toLong()) }.flatten()
        assertTrue(verdicts.toString(), verdicts.isEmpty())
        assertNull(g.stop)
    }

    @Test fun alternatingCallsOnSameScreenStopAtEight() {
        val g = OperationGuards()
        val tap = obj("""{"x":100,"y":200}""")
        val look = JsonObject(emptyMap())
        val perCall = (1..8).map { i ->
            val tool = if (i % 2 == 1) "phone_tap" else "phone_observe"
            val args = if (i % 2 == 1) tap else look
            g.beforeAction(tool, args) + g.frame("s$i", 1L)
        }
        assertTrue(perCall.dropLast(1).flatten().isEmpty())
        val stop = perCall.last().single()
        assertEquals("repeating_action", stop.code)
        assertEquals(Severity.Stop, stop.severity)
        assertEquals("abab", stop.evidence["pattern"]!!.jsonPrimitive.content)
        assertEquals(8, stop.evidence["steps"]!!.jsonPrimitive.int)
        assertEquals(stop, g.stop)
    }

    @Test fun differentArgumentsBreakTheAbabPattern() {
        val g = OperationGuards()
        val verdicts = (1..8).map { i ->
            val tool = if (i % 2 == 1) "phone_tap" else "phone_observe"
            val args = if (i % 2 == 1) obj("""{"x":${if (i <= 5) 100 else 300},"y":200}""") else JsonObject(emptyMap())
            g.beforeAction(tool, args) + g.frame("s$i", 1L)
        }.flatten()
        assertTrue(verdicts.toString(), verdicts.isEmpty())
        assertNull(g.stop)
    }

    @Test fun alternatingCallsWithScreenChangesDoNotStop() {
        val g = OperationGuards()
        val tap = obj("""{"x":100,"y":200}""")
        val look = JsonObject(emptyMap())
        val verdicts = (1..40).map { i ->
            val tool = if (i % 2 == 1) "phone_tap" else "phone_observe"
            val args = if (i % 2 == 1) tap else look
            g.beforeAction(tool, args) + g.frame("s$i", i.toLong())
        }.flatten()
        assertTrue(verdicts.toString(), verdicts.isEmpty())
        assertNull(g.stop)
    }

    @Test fun freshContentResetsSameCallHistory() {
        val g = OperationGuards()
        val args = obj("""{"direction":"down"}""")
        repeat(12) { i ->
            val frame = g.beforeAction("phone_scroll", args) + g.frame("s$i", 1L)
            assertTrue(frame.toString(), frame.none { it.code == "repeating_action" })
            assertTrue(g.read("phone_observe", fresh = true, fingerprint = 1000L + i).isEmpty())
        }
        assertNull(g.stop)
    }

    @Test fun explicitPageChangeCancelsProvisionalCallStall() {
        val g = OperationGuards()
        val args = obj("""{"direction":"down"}""")
        repeat(5) { assertTrue((g.beforeAction("phone_scroll", args) + g.frame("a$it", 1L)).isEmpty()) }
        assertEquals("repeating_action", (g.beforeAction("phone_scroll", args) + g.frame("a5", 1L)).single().code)
        assertEquals("repeating_action", g.stop?.code)
        g.pageChanged(resetCalls = true)
        assertNull(g.stop)
    }

    @Test fun unknownChangedDoesNotResetCallHistory() {
        val g = OperationGuards()
        val args = obj("""{"direction":"down"}""")
        repeat(5) { assertTrue((g.beforeAction("phone_scroll", args) + g.frame("a$it", 1L)).isEmpty()) }
        g.pageChanged() // 旧工具 changed=null:只清读计数,不能当作 poki 进展
        val stop = (g.beforeAction("phone_scroll", args) + g.frame("a5", 1L)).single()
        assertEquals("repeating_action", stop.code)
        assertEquals(Severity.Stop, stop.severity)
    }

    @Test fun callsWithoutFramesDoNotEnterTheWindow() {
        val g = OperationGuards()
        val args = obj("""{"direction":"down"}""")
        repeat(10) { assertTrue(g.beforeAction("phone_scroll", args).isEmpty()) }
        assertTrue((g.beforeAction("phone_scroll", args) + g.frame("observed", 1L)).isEmpty())
        assertNull(g.stop)
    }
}

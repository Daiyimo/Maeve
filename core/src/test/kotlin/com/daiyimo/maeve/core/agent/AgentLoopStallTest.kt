package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.StopReason
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.testing.answerReply
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopStallTest {
    private var sequence = 0
    private fun tool(name: String): ModelReply {
        val call = Part.ToolCall("stall-${++sequence}", name, "{}")
        return ModelReply(listOf(call), StopReason.ToolUse, null)
    }

    private suspend fun run(vararg names: String): Pair<TaskState, List<RunEvent>> {
        val events = mutableListOf<RunEvent>()
        val model = FakeModel(*names.map(::tool).toTypedArray())
        val loop = AgentLoop(model, toolDispatcher(FakeScreen()), "BASE", maxSteps = 50, sink = object : RunSink {
            override fun event(e: RunEvent) { events += e }
        })
        return loop.run("keep looking") to events
    }

    @Test fun sameCallOnSameScreenEndsTheRealLoopAtSix() = runTest {
        val (end, events) = run(*(Array(6) { "phone_observe" }))
        assertTrue("$end", end is TaskState.Failed)
        assertEquals(FailKind.Guard, (end as TaskState.Failed).kind)
        assertEquals(6, events.filterIsInstance<RunEvent.ToolResult>().count { it.source == "model" })
        assertTrue(events.filterIsInstance<RunEvent.State>().any { it.name == "loop_finalized" } ||
            end.reason.contains("repeating_action"))
    }

    @Test fun alternatingCallsOnSameScreenEndTheRealLoopAtEight() = runTest {
        val names = Array(8) { if (it % 2 == 0) "phone_observe" else "phone_list_elements" }
        val (end, events) = run(*names)
        assertTrue("$end", end is TaskState.Failed)
        assertEquals(FailKind.Guard, (end as TaskState.Failed).kind)
        assertEquals(8, events.filterIsInstance<RunEvent.ToolResult>().count { it.source == "model" })
        assertTrue(end.reason.contains("repeating_action"))
    }

    @Test fun noNewStateFinalTurnDoesNotClaimTheStepBudgetWasUsed() = runTest {
        val elements = (1..8).map { i -> UiElement("@e$i", "button", "按钮$i", 500, i * 100, listOf(0, i * 100 - 20, 999, i * 100 + 20), null, true, false, false) }
        val screen = object : ScreenPort by FakeScreen() {
            override suspend fun snapshot() = ScreenSnapshot("s1", "com.x", elements, 1080, 2400)
            override suspend fun peek() = snapshot()
            override suspend fun tapRef(ref: String, snapshotId: String) = com.daiyimo.maeve.core.uistate.ActionResult.Ok(changed = true)
        }
        val script = mutableListOf<Any>(tool("phone_observe"))
        repeat(8) { i ->
            script += ModelReply(listOf(Part.ToolCall("tap-$i", "phone_tap", """{"ref":"@e${i + 1}","snapshotId":"p1"}""")), StopReason.ToolUse, null)
        }
        script += answerReply("已完成能确认的部分")
        val model = FakeModel(*script.toTypedArray())
        val events = mutableListOf<RunEvent>()
        val end = AgentLoop(model, toolDispatcher(screen, inlineScreen = true), "BASE", maxSteps = 500, sink = object : RunSink {
            override fun event(e: RunEvent) { events += e }
        }).run("继续查看")

        assertTrue("$end", end is TaskState.Succeeded)
        assertTrue("$end\n$events", events.filterIsInstance<RunEvent.State>().any { it.name == "loop_finalized" && it.detail == "no_new_state" })
        val finalRequest = model.requests.last().flatMap { it.parts }.filterIsInstance<Part.Text>().joinToString("\n") { it.text }
        assertTrue(finalRequest, "Step: 10 of at most 500" in finalRequest)
        assertFalse(finalRequest, Feedback["final_turn"] in finalRequest)
        assertFalse(finalRequest, "out of steps" in finalRequest)
    }

    @Test fun defaultLoopRunsFiveHundredProgressingTurnsThenAcceptsTheFinalAnswer() = runTest {
        val screen = object : ScreenPort by FakeScreen() {
            var reads = 0
            override suspend fun snapshot() = ScreenSnapshot(
                "s${reads}", "com.example", listOf(UiElement("@e1", "text", "Screen ${reads++}", 0, 0,
                    listOf(0, 0, 100, 100), null, true, false, false)), 1080, 2400,
            )
        }
        val script = mutableListOf<Any>()
        repeat(500) { script += tool("phone_observe") }
        script += answerReply("done")
        val model = FakeModel(*script.toTypedArray())
        val end = AgentLoop(model, toolDispatcher(screen), "BASE").run("read every page")
        assertTrue("$end", end is TaskState.Succeeded)
        assertEquals(501, model.requests.size)
    }
}

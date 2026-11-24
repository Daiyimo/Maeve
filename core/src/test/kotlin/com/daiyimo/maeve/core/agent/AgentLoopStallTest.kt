package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.StopReason
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.testing.answerReply
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.tool.FakeScreen
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitCallTrackingTest {
    @Test fun fixedAndConditionalWaitsEnterTheSameCallWindow() = runTest {
        val calls = listOf(
            "wait" to """{"seconds":0.2}""",
            "phone_wait" to """{"condition":"stable","timeoutMs":0}""",
        )
        for ((name, args) in calls) {
            val tools = toolDispatcher(FakeScreen())
            val guards = OperationGuards()
            val executor = ActionExecutor(tools, guards, null, gate = {}, recorder = ActionRecorder(RunSink.NONE))
            repeat(5) { i ->
                val out = executor.run(Part.ToolCall("$name-$i", name, args), Origin.Model(1))
                assertTrue("$name $i: $out", out is ActionOutcome.Ran)
                assertNull("$name $i", guards.stop)
            }
            val sixth = executor.run(Part.ToolCall("$name-5", name, args), Origin.Model(1))
            assertTrue("$name: $sixth", sixth is ActionOutcome.Ran)
            assertEquals("repeating_action", guards.stop?.code)
            assertTrue((sixth as ActionOutcome.Ran).after.any { it.code == "repeating_action" })
        }
    }
}

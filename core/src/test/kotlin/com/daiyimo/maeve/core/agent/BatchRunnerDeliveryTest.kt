package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.action.ActionExecutor
import com.daiyimo.maeve.core.action.ActionRecorder
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.tool.Until
import com.daiyimo.maeve.core.tool.execute
import com.daiyimo.maeve.core.assembly.toolDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRunnerDeliveryTest {
    private val home = listOf("搜索框" to listOf(0, 0, 999, 80), "返回" to listOf(0, 900, 100, 999), "提交订单" to listOf(800, 900, 999, 999))
    private val events = mutableListOf<RunEvent>()
    private val sink = object : RunSink { override fun event(e: RunEvent) { events += e } }
    private var gates = 0
    private fun runner(d: ToolDispatcher, guards: OperationGuards = OperationGuards(), confirmations: ConfirmationGate? = ConfirmationGate()) =
        BatchRunner(d, guards, ActionExecutor(d, guards, confirmations, gate = { gates++ }, recorder = ActionRecorder(sink)))
    private fun steps(vararg s: Triple<String, String, String?>) = s.map { BatchStep(it.first, it.second, it.third?.let { e -> Until(appears = e) }) }
    private fun st(tool: String, args: String = "{}", expect: String? = null) = Triple(tool, args, expect)
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun allStepsPassReturnsOk() = runTest {
        val s = BatchScreen(home)
        val d = toolDispatcher(s).also { it.execute(com.daiyimo.maeve.core.model.Part.ToolCall("o", "phone_observe", "{}")) }
        val r = runner(d).run(steps(st("phone_tap", """{"ref":"@e1","snapshotId":"p1"}"""), st("phone_type_text", """{"text":"咖啡"}"""), st("phone_back")), "c1", 3).content
        assertEquals("true", r.s("ok")); assertEquals(3, r["completed"]!!.jsonPrimitive.int); assertEquals(3, r["total"]!!.jsonPrimitive.int)
        assertEquals(listOf("tapRef @e1 p1", "type 咖啡", "back"), s.calls)
        assertEquals(6, gates) // 每个写动作投递前后各一次
        val results = events.filterIsInstance<RunEvent.ToolResult>()
        assertEquals(listOf("batch", "batch", "batch"), results.map { it.source })
        assertEquals(listOf("c1.1", "c1.2", "c1.3"), results.map { it.callId })
        val entries = r["steps"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("sent", "sent", "sent"), entries.map { it.s("delivery") })
        assertEquals(listOf("true", "true", "true"), entries.map { it.s("executed") })
        assertEquals(RunEvent.Batch(events.filterIsInstance<RunEvent.Batch>().single().atMs, 3, 3, false, null), events.filterIsInstance<RunEvent.Batch>().single())
    }

    // 第一步做完时用户插了话:后面的子步不做,各给一条「为新消息跳过」的结果,整批交回循环去送插话

    @Test fun newUserMessageStopsBatchAtTheNextBoundary() = runTest {
        val s = BatchScreen(home)
        val d = toolDispatcher(s)
        var waiting = false
        val r = BatchRunner(d, OperationGuards(), ActionExecutor(d, OperationGuards(), ConfirmationGate(), gate = { gates++; waiting = true }, recorder = ActionRecorder(sink)),
            steerWaiting = { waiting })
            .run(steps(st("phone_back"), st("phone_back"), st("phone_back")), "c1").content
        assertEquals(listOf("back"), s.calls)
        assertEquals("false", r.s("ok"))
        assertEquals(BatchRunner.NEW_MESSAGE, r.s("reason"))
        assertEquals("false", r.s("counts_as_failure"))
        assertEquals(1, r["completed"]!!.jsonPrimitive.int)
        val entries = r["steps"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, entries.size)
        assertEquals("true", entries[0].s("ok"))
        assertEquals(listOf(BatchRunner.NEW_MESSAGE, BatchRunner.NEW_MESSAGE), entries.drop(1).map { it.s("error") })
        assertEquals(listOf("false", "false"), entries.drop(1).map { it.s("executed") })
        assertEquals(listOf("sent", "not_sent", "not_sent"), entries.map { it.s("delivery") })
        assertTrue(events.filterIsInstance<RunEvent.Batch>().single().stopped)
    }

    // 子步里的等待被插话截断:这一步算没做,和后面的一起跳过,不等满也不接着做

    @Test fun steerCuttingAWaitStepSkipsItAndTheRest() = runTest {
        val s = BatchScreen(home)
        val inbox = com.daiyimo.maeve.core.run.RunInbox()
        launch { delay(1_000); inbox.send(com.daiyimo.maeve.core.run.RunInput.Steer("m1", "停一下")) }
        val r = withContext(inbox.signal) {
            runner(toolDispatcher(s)).run(steps(st("phone_back"), st("wait", """{"seconds":10}"""), st("phone_back")), "c1").content
        }
        assertEquals(1_000L, currentTime)
        assertEquals(listOf("back"), s.calls)
        assertEquals(BatchRunner.NEW_MESSAGE, r.s("reason"))
        assertEquals(1, r["completed"]!!.jsonPrimitive.int)
        val entries = r["steps"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("false", "false"), entries.drop(1).map { it.s("executed") })
        assertEquals(listOf("sent", "not_sent", "not_sent"), entries.map { it.s("delivery") })
    }

    @Test fun stopsBeforeIrreversible() = runTest {
        val s = BatchScreen(home)
        val d = toolDispatcher(s).also { it.execute(com.daiyimo.maeve.core.model.Part.ToolCall("o", "phone_observe", "{}")) }
        val r = runner(d).run(steps(st("phone_tap", """{"ref":"@e1","snapshotId":"p1"}"""), st("phone_type_text", """{"text":"你好"}"""),
            st("phone_tap", """{"ref":"@e3","snapshotId":"p1"}""")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertEquals(2, r["completed"]!!.jsonPrimitive.int)
        assertEquals("false", r["steps"]!!.jsonArray.last().jsonObject.s("executed"))
        assertEquals("not_sent", r["steps"]!!.jsonArray.last().jsonObject.s("delivery"))
        assertEquals(listOf("sent", "sent", "not_sent"), r["steps"]!!.jsonArray.map { it.jsonObject.s("delivery") })
        assertEquals(listOf("tapRef @e1 p1", "type 你好"), s.calls)
        assertEquals(5, gates) // 前两步各两次;第三步先过门禁,判出不可逆后不再投递
        assertNotNull(events.filterIsInstance<RunEvent.Confirmation>().singleOrNull())
    }

    @Test fun concretePreDispatchFailuresAreNotSentAndNotExecuted() = runTest {
        for (reason in listOf(
            "no_app_open", "invalid_gesture", "launch_failed", "no_history", "not_editable",
            "offscreen", "obscured", "devtools_unavailable", "phone_unavailable",
        )) {
            val s = BatchScreen(home).apply { results += ActionResult.Failed(reason) }
            val result = runner(toolDispatcher(s)).run(steps(st("phone_back")), "c-$reason")
            val r = result.content
            assertEquals(reason, "not_sent", result.delivery?.wire)
            assertEquals(reason, "not_sent", r.s("delivery"))
            val entry = r["steps"]!!.jsonArray.single().jsonObject
            assertEquals(reason, entry.s("error"))
            assertEquals("not_sent", entry.s("delivery"))
            assertEquals("false", entry.s("executed"))
            assertTrue(reason, "was not run ($reason)" in r.s("message"))
            assertEquals(reason, listOf("back"), s.calls)
        }
    }

    @Test fun postDispatchAmbiguityIsUnknownButCountsAsExecuted() = runTest {
        for ((reason, delivery) in listOf(
            "action_failed" to "unknown", "dispatch_unknown" to "unknown", "focus_moved" to "unknown",
            "unclassified_failure" to "unknown", "app_not_in_front" to "sent", "opened_elsewhere" to "sent",
            "keyboard_still_up" to "sent", "point_not_typed" to "sent", "select_not_applied" to "sent",
        )) {
            val s = BatchScreen(home).apply { results += ActionResult.Failed(reason) }
            val r = runner(toolDispatcher(s)).run(steps(st("phone_back")), "c-$reason").content
            val entry = r["steps"]!!.jsonArray.single().jsonObject
            assertEquals(reason, entry.s("error"))
            assertEquals(delivery, entry.s("delivery"))
            assertEquals("true", entry.s("executed"))
            assertTrue(reason, "was run but did not pass its check ($reason)" in r.s("message"))
        }
    }

    @Test fun aggregateDeliveryDoesNotEraseAnEarlierSentStep() = runTest {
        val s = BatchScreen(home).apply {
            results += ActionResult.Ok(changed = true)
            results += ActionResult.Failed("no_app_open")
        }
        val result = runner(toolDispatcher(s)).run(steps(st("phone_back"), st("phone_back")), "c1")
        val entries = result.content["steps"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("sent", "not_sent"), entries.map { it.s("delivery") })
        assertEquals(listOf("true", "false"), entries.map { it.s("executed") })
        assertEquals("sent", result.delivery?.wire)
        assertEquals("sent", result.content.s("delivery"))
    }
}

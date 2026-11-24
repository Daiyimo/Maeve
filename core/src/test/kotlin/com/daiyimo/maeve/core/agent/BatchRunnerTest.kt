package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.tool.execute
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.action.ActionExecutor
import com.daiyimo.maeve.core.action.ActionRecorder
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.tool.Until
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.daiyimo.maeve.core.testing.BATCHABLE
import com.daiyimo.maeve.core.assembly.toolDispatcher

class BatchRunnerTest {
    private val home = listOf("搜索框" to listOf(0, 0, 999, 80), "返回" to listOf(0, 900, 100, 999), "提交订单" to listOf(800, 900, 999, 999))
    private val events = mutableListOf<RunEvent>()
    private val sink = object : RunSink { override fun event(e: RunEvent) { events += e } }
    private var gates = 0

    private var onGate: () -> Unit = {}
    private fun runner(d: ToolDispatcher, guards: OperationGuards = OperationGuards(), confirmations: ConfirmationGate? = ConfirmationGate()) =
        BatchRunner(d, guards, ActionExecutor(d, guards, confirmations, gate = { gates++; onGate() }, recorder = ActionRecorder(sink)))
    private fun steps(vararg s: Triple<String, String, String?>) = s.map { BatchStep(it.first, it.second, it.third?.let { e -> Until(appears = e) }) }
    private fun st(tool: String, args: String = "{}", expect: String? = null) = Triple(tool, args, expect)
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun unchangedScreenAloneDoesNotStopKnownSteps() = runTest {
        val s = BatchScreen(home).apply { results += ActionResult.Ok(changed = true); results += ActionResult.Ok(changed = false) }
        val d = toolDispatcher(s)
        val r = runner(d).run(steps(st("phone_back"), st("phone_back"), st("phone_back")), "c1").content
        assertEquals("true", r.s("ok"))
        assertEquals(3, r["completed"]!!.jsonPrimitive.int)
        assertEquals(3, r["steps"]!!.jsonArray.size)
        assertEquals(listOf("back", "back", "back"), s.calls)
        assertTrue(!events.filterIsInstance<RunEvent.Batch>().single().stopped)
    }

    @Test fun stopsOnExpectationUnmet() = runTest {
        val s = BatchScreen(home)
        val r = runner(toolDispatcher(s)).run(steps(st("phone_back", expect = "搜索结果"), st("phone_back")), "c1").content
        assertEquals("expectation_unmet", r.s("reason"))
        val last = r["steps"]!!.jsonArray.last().jsonObject
        assertEquals("搜索结果", last.s("expect"))
        assertEquals(listOf("back"), s.calls)
        // 屏上有的文字就算对上
        val ok = runner(toolDispatcher(BatchScreen(home))).run(steps(st("phone_back", expect = "搜索"), st("phone_back")), "c2").content
        assertEquals("true", ok.s("ok"))
    }

    // 网站说到上限了:这一步之后的不再做,结果里带着那句话
    @Test fun stopsWhenALimitIsReported() = runTest {
        val s = BatchScreen(home)
        val port = object : ScreenPort by s {
            override suspend fun back(): ActionResult { s.replaced = home + ("今日次数已用完" to listOf(0, 400, 999, 460)); return s.back() }
        }
        val r = runner(toolDispatcher(port)).run(steps(st("phone_back"), st("phone_back")), "c1").content
        assertEquals("limit_reached", r.s("reason"))
        assertEquals("今日次数已用完", r["steps"]!!.jsonArray.last().jsonObject.s("limitReached"))
        assertEquals(listOf("back"), s.calls)
    }

    @Test fun waitMayBeUnchanged() = runTest {
        val s = BatchScreen(home)
        val r = runner(toolDispatcher(s)).run(steps(st("wait", """{"seconds":0.5}"""), st("phone_back")), "c1").content
        assertEquals("true", r.s("ok")); assertEquals(listOf("back"), s.calls)
    }

    @Test fun coordinateTapOnSendStopsBefore() = runTest {
        val s = BatchScreen(home)
        val r = runner(toolDispatcher(s)).run(steps(st("phone_back"), st("phone_tap", """{"x":900,"y":950}""")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertTrue("提交订单" in r.s("matched"))
        assertEquals(listOf("back"), s.calls)
    }

    @Test fun blindCoordinateTapStops() = runTest {
        val s = BatchScreen(listOf("返回" to listOf(0, 900, 100, 999)))
        val r = runner(toolDispatcher(s)).run(steps(st("phone_back"), st("phone_tap", """{"x":500,"y":500}""")), "c1").content
        assertEquals("target_unresolved", r.s("reason")); assertEquals(listOf("back"), s.calls)
    }

    @Test fun laterRefTranslatedThroughStabilizer() = runTest {
        // 第一步之后屏幕变了:端口给「确定」换了编号,模型编号仍是第一屏的
        val after = listOf("提示" to listOf(0, 300, 999, 400), "搜索框" to listOf(0, 0, 999, 80), "确定" to listOf(400, 500, 600, 560))
        val before = listOf("搜索框" to listOf(0, 0, 999, 80), "确定" to listOf(400, 500, 600, 560))
        val s = BatchScreen(before, after)
        val d = toolDispatcher(s, inlineScreen = true)
        d.execute(com.daiyimo.maeve.core.model.Part.ToolCall("o", "phone_observe", "{}"))
        val r = runner(d).run(steps(st("phone_tap", """{"ref":"@e1","snapshotId":"p1"}"""), st("phone_tap", """{"ref":"@e2","snapshotId":"p1"}""")), "c1").content
        assertEquals("true", r.s("ok"))
        assertEquals(listOf("tapRef @e1 p1", "tapRef @e3 p2"), s.calls)
        assertEquals("true", r["steps"]!!.jsonArray[1].jsonObject.s("ok"))
        assertNotNull(r["screen"])
    }

    @Test fun adaptiveBatchRunsKnownStepsWithOneFinalTextScreen() = runTest {
        val form = listOf("输入名称" to listOf(0, 100, 900, 180), "输入金额" to listOf(0, 250, 900, 330))
        val s = BatchScreen(form)
        val d = toolDispatcher(s, visionEnabled = true, adaptiveVision = true, inlineScreen = true)
        assertTrue(d.execute(com.daiyimo.maeve.core.model.Part.ToolCall("o", "phone_observe", "{}")).images.isEmpty())
        val result = runner(d).run(steps(
            st("phone_tap", """{"ref":"@e1","snapshotId":"p1"}"""),
            st("phone_type_text", """{"text":"午餐"}"""),
            st("phone_tap", """{"ref":"@e2","snapshotId":"p1"}"""),
            st("phone_type_text", """{"text":"350"}"""),
        ), "c1")
        assertEquals("true", result.content.s("ok"))
        assertEquals(listOf("tapRef @e1 p1", "type 午餐", "tapRef @e2 p3", "type 350"), s.calls)
        assertTrue(result.images.isEmpty())
        assertNotNull(result.content["screen"])
    }

    @Test fun adaptiveBatchStopsBeforeNextStepWhenScreenNeedsImage() = runTest {
        val unlabeled = listOf("" to listOf(0, 0, 100, 100), "" to listOf(120, 0, 220, 100))
        val s = BatchScreen(home, unlabeled)
        val d = toolDispatcher(s, visionEnabled = true, adaptiveVision = true, inlineScreen = true)
        d.execute(com.daiyimo.maeve.core.model.Part.ToolCall("o", "phone_observe", "{}"))
        val result = runner(d).run(steps(st("phone_back"), st("phone_back")), "c1")
        assertEquals("screen_needs_review", result.content.s("reason"))
        assertEquals(listOf("back"), s.calls)
        assertEquals(1, result.images.size)
    }

    @Test fun rejectsNonBatchableTool() {
        val avail = BATCHABLE.toSet() + "finish"
        val (none, msg) = BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{}},{"tool":"finish","args":{"answer":"x"}}]}""", avail, BATCHABLE)
        assertNull(none); assertTrue(msg!!, "steps[2].tool" in msg && "finish" in msg)
        assertTrue("steps" in BatchRunner.parse("""{"steps":[]}""", avail, BATCHABLE).second!!)
        assertEquals(1, BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{}}]}""", avail, BATCHABLE).first!!.size)
        assertTrue("expect" in BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{},"expect":"${"x".repeat(201)}"},{"tool":"phone_back","args":{}}]}""", avail, BATCHABLE).second!!)
        // 此刻没提供的工具(开关关着的 phone_open_link)也不行
        assertTrue("phone_open_link" in BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{}},{"tool":"phone_open_link","args":{}}]}""", setOf("phone_back"), BATCHABLE).second!!)
        assertEquals(2, BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{}},{"tool":"phone_back"}]}""", avail, BATCHABLE).first!!.size)
    }

    @Test fun gateBeforeIrreversibleCheck() = runTest {
        // 等锁屏/被拿走的关卡期间屏幕换了:同一位置现在是「提交订单」,按过关卡之后的屏幕判,不点
        val s = BatchScreen(listOf("确定" to listOf(400, 450, 600, 550), "返回" to listOf(0, 900, 100, 999)))
        onGate = { if (gates == 2) s.replaced = listOf("提交订单" to listOf(400, 450, 600, 550)) }
        val r = runner(toolDispatcher(s)).run(steps(st("phone_back"), st("phone_tap", """{"x":500,"y":500}""")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertEquals(listOf("back"), s.calls)
    }

    @Test fun waitsForStableScreenBetweenSteps() = runTest {
        val s = BatchScreen(home)
        runner(toolDispatcher(s)).run(steps(st("phone_back"), st("phone_back", expect = "搜索"), st("phone_back")), "c1")
        // 步与步之间等两次;端口在动作里已等过界面(报了变没变),判效果时不再另等
        assertEquals(2, s.settles)
    }

    @Test fun stopEntriesSayWhetherTheStepRan() = runTest {
        val ran = runner(toolDispatcher(BatchScreen(home))).run(steps(st("phone_back", expect = "没有的字"), st("phone_back")), "c1").content
        assertEquals("true", ran["steps"]!!.jsonArray.last().jsonObject.s("executed"))
        assertTrue(ran.s("message"), "was run but did not pass its check (expectation_unmet)" in ran.s("message"))
        val notRun = runner(toolDispatcher(BatchScreen(listOf("返回" to listOf(0, 900, 100, 999))))).run(steps(st("phone_back"), st("phone_tap", """{"x":500,"y":500}""")), "c2").content
        assertEquals("false", notRun["steps"]!!.jsonArray.last().jsonObject.s("executed"))
        assertTrue("was not run (target_unresolved)" in notRun.s("message"))
    }

    @Test fun withoutConfirmationToolMessageDoesNotAskForIt() = runTest {
        val r = runner(toolDispatcher(BatchScreen(home)), confirmations = null).run(steps(st("phone_back"), st("phone_tap", """{"x":900,"y":950}""")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertTrue("request_confirmation" !in r.s("message"))
        val with = runner(toolDispatcher(BatchScreen(home))).run(steps(st("phone_back"), st("phone_tap", """{"x":900,"y":950}""")), "c2").content
        assertTrue("request_confirmation" in with.s("message"))
    }
}

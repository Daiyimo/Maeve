package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.agent.FakeModel
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.StopReason
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.testing.field
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.Verification
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import com.daiyimo.maeve.core.vision.LocScreen
import com.daiyimo.maeve.core.vision.VisionService
import com.daiyimo.maeve.core.vision.uiEl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 按描述指目标(输入框或要点的东西),而树读不出这一屏:改看图找,之后和传 @vN 一样核对、判不可逆、执行。 */
class DescribedBySightTest {
    private val sink = object : RunSink { override fun event(e: RunEvent) {} }

    /** 一屏(默认树里什么都没有,即树不能用);记下端口收到的打字和点击。 */
    private class Blind(val inner: LocScreen = LocScreen()) : ScreenPort by inner {
        val calls = mutableListOf<String>()
        val taps: List<String> get() = inner.taps
        override suspend fun typeText(ref: String?, snapshotId: String?, text: String): ActionResult { calls += "type $ref $text"; return ActionResult.Ok(changed = true) }
        override suspend fun typeAt(x: Int, y: Int, text: String): ActionResult {
            calls += "typeAt $x,$y $text"; return ActionResult.Ok(changed = true, verification = Verification.MATCH)
        }
        override suspend fun pressKey(keyCode: Int): ActionResult { calls += "key $keyCode"; return ActionResult.Ok() }
    }

    private fun model(vararg replies: String) = FakeModel(
        *replies.map { ModelReply(listOf(Part.Text(it)), StopReason.EndTurn, null) }.toTypedArray(), groundingVerified = false)

    private fun executor(screen: ScreenPort, vision: FakeModel) = ActionExecutor(
        toolDispatcher(screen, vision = VisionService(vision, JvmTestImageOps)), OperationGuards(), ConfirmationGate(), gate = {},
        recorder = ActionRecorder(sink))

    private suspend fun type(e: ActionExecutor, args: String) = e.run(Part.ToolCall("c", "phone_type_text", args), Origin.Model(1))

    private suspend fun tap(e: ActionExecutor, args: String) = e.run(Part.ToolCall("c", "phone_tap", args), Origin.Model(1))

    private fun content(o: ActionOutcome): JsonObject = (o as ActionOutcome.Ran).content
    private fun JsonObject.s(k: String) = (this[k] as JsonPrimitive).content

    @Test fun blindScreenFindsTheBoxBySightAndTypesThere() = runTest {
        val screen = Blind()
        val vision = model("""{"bbox_2d":[0,880,800,940],"seen":"输入框"}""", "是")
        val c = content(type(executor(screen, vision), """{"element":"底部的消息输入框","text":"明天见"}"""))
        assertEquals(c.toString(), "true", c.s("ok"))
        // 0–999 的框中心换成像素,点一下再粘贴
        assertEquals(listOf("typeAt 432,2183 明天见"), screen.calls)
        assertEquals("true", c.s("foundBySight"))
        assertEquals("底部的消息输入框", c.s("resolvedFrom"))
        assertEquals("输入框", c.s("seen"))
        // 只靠看图、没有佐证:照样标出来
        assertEquals("true", c.s("unverifiedPoint"))
        assertEquals(2, vision.requests.size)
    }

    @Test fun notSeenIsReportedAndNothingIsTyped() = runTest {
        val screen = Blind()
        val c = content(type(executor(screen, model("null")), """{"element":"底部的消息输入框","text":"明天见"}"""))
        assertEquals("element_not_found", c.s("error"))
        assertTrue(c.s("message"), "not_found" in c.s("message") && Feedback["described.not_seen.retry"] in c.s("message"))
        assertTrue(screen.calls.isEmpty())
    }

    @Test fun boxFoundOnASendButtonIsUsedWithoutAsking() = runTest {
        // 判过不可逆:是发送,不花钱,不先问
        val screen = Blind()
        val vision = model("""{"bbox_2d":[800,880,999,940],"seen":"发送"}""", "是")
        val o = type(executor(screen, vision), """{"element":"右下角的框","text":"明天见"}""")
        assertTrue("$o", o is ActionOutcome.Ran)
        assertTrue(screen.calls.isNotEmpty())
    }

    @Test fun readableTreeWithoutAMatchDoesNotLookBySight() = runTest {
        val inner = LocScreen(List(8) { i -> uiEl("@e$i", "设置项$i", listOf(0, i * 100, 999, i * 100 + 80)) } +
            field("@e9", "搜索", y = 900))
        val screen = Blind(inner)
        val vision = model()
        val c = content(type(executor(screen, vision), """{"element":"消息框","text":"明天见"}"""))
        assertEquals("element_not_found", c.s("error"))
        assertTrue(c.s("message"), "phone_observe" in c.s("message"))
        assertTrue(vision.requests.isEmpty())
        assertTrue(screen.calls.isEmpty())
    }

    @Test fun blindScreenFindsTheButtonBySightAndTapsIt() = runTest {
        val screen = Blind()
        // 第一次:看图找;第二次:红框复看;第三次:点了之后核对界面变了没有
        val vision = model("""{"bbox_2d":[500,300,700,360],"seen":"更多热搜"}""", "是", "page")
        val c = content(tap(executor(screen, vision), """{"element":"更多热搜"}"""))
        assertEquals(c.toString(), "true", c.s("ok"))
        // 0–999 的框中心换成像素,点那一下
        assertEquals(listOf("648,792"), screen.taps)
        assertEquals("true", c.s("foundBySight"))
        assertEquals("更多热搜", c.s("resolvedFrom"))
        assertEquals("更多热搜", c.s("seen"))
        // 只靠看图、没有佐证:照样标出来
        assertEquals("true", c.s("unverifiedPoint"))
        assertEquals(3, vision.requests.size)
    }

    @Test fun notSeenIsReportedAndNothingIsTapped() = runTest {
        val screen = Blind()
        val c = content(tap(executor(screen, model("null")), """{"element":"更多热搜"}"""))
        assertEquals("element_not_found", c.s("error"))
        assertTrue(c.s("message"), "not_found" in c.s("message") && Feedback["described.not_seen.retry"] in c.s("message"))
        assertTrue(screen.taps.isEmpty())
    }

    @Test fun readableTreeWithoutAMatchDoesNotLookBySightForTap() = runTest {
        val inner = LocScreen(List(8) { i -> uiEl("@e$i", "设置项$i", listOf(0, i * 100, 999, i * 100 + 80)) } +
            field("@e9", "搜索", y = 900))
        val screen = Blind(inner)
        val vision = model()
        val c = content(tap(executor(screen, vision), """{"element":"更多热搜"}"""))
        assertEquals("element_not_found", c.s("error"))
        assertTrue(c.s("message"), "phone_observe" in c.s("message"))
        assertTrue(vision.requests.isEmpty())
        assertTrue(screen.taps.isEmpty())
    }
}

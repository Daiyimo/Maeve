package com.daiyimo.maeve.wechat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SystemAmooReplyTest {
    @Test fun phoneResultUsesOnlyTheIdAssignedToThisWechatMessage() {
        val id = wechatPhoneRequestId("abc".repeat(24))
        assertTrue(id.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        assertEquals(WechatPhoneResult(running = true), parseWechatPhoneResult(id, """{"id":"$id","status":"running"}"""))
        assertEquals("签到成功", parseWechatPhoneResult(id, """{"id":"$id","status":"done","answer":"签到成功"}""")?.text)
        assertEquals("停在登录页", parseWechatPhoneResult(id, """{"id":"$id","status":"failed","note":"停在登录页"}""")?.text)
        assertNull(parseWechatPhoneResult(id, """{"id":"other","status":"done","answer":"其他任务"}"""))
    }

    @Test fun waitingForAUserStepIsDistinguishedFromExecuting() {
        val receipt = parseWechatPhoneResult("wx-test", """{"id":"wx-test","status":"running","waiting_for":"confirmation"}""")
        assertEquals(WechatPhoneResult(running = true, waitingForUser = true), receipt)
        assertNull(selectWechatAmooReply(receipt, "系统 Amoo 已转交"))
    }

    @Test fun terminalPhoneReceiptCanBeDeliveredWhileTheChatIsStillSettling() {
        val receipt = WechatPhoneResult(running = false, text = "本机执行结果")
        assertEquals(WechatAmooReply("本机执行结果", settleChat = true), selectWechatAmooReply(receipt, null))
        assertEquals(WechatAmooReply("本机执行结果"), selectWechatAmooReply(receipt, "已完成聊天"))
        assertEquals(WechatAmooReply("普通聊天回复"), selectWechatAmooReply(null, "普通聊天回复"))
    }

    @Test fun completedPhoneTaskCanLeaveAmooGeneratingBeyondTheOldGracePeriod() = runTest {
        val old = listOf("旧回答", "复制")
        val question = "本轮请求"
        val chat = old + listOf(question, "正在生成")
        val settled = awaitAmooChatCompletion(now = { testScheduler.currentTime }, timeoutMs = 10_000) {
            val busy = testScheduler.currentTime < 5_000
            val current = if (busy) chat else old + listOf(question, "本轮答案", "复制")
            SystemAmooTranscript.reply(current, old, question, busy)
        }
        assertTrue(settled)
        assertEquals(5_750L, testScheduler.currentTime)
    }

    @Test fun missingOrUnfinishedTranscriptDoesNotAuthorizeKeepingTheChatTask() = runTest {
        val settled = awaitAmooChatCompletion(now = { testScheduler.currentTime }, timeoutMs = 3_000) { null }
        assertFalse(settled)
        assertEquals(3_000L, testScheduler.currentTime)
    }
    @Test fun staleGeneratingMarkerDoesNotBlockTheCurrentReply() {
        val old = listOf("旧问题", "正在生成")
        assertEquals("本轮答案", SystemAmooTranscript.reply(old + listOf("本轮问题", "本轮答案", "复制"), old, "本轮问题", false))
    }
    @Test fun recoveryOnlyReusesTheAnswerForTheSameRequestAndSession() {
        assertEquals("已完成", recoveredSystemAmooReply("r1", "s1", "r1", "s1", "已完成"))
        assertNull(recoveredSystemAmooReply("r2", "s1", "r1", "s1", "已完成"))
        assertNull(recoveredSystemAmooReply("r1", "s2", "r1", "s1", "已完成"))
    }
}

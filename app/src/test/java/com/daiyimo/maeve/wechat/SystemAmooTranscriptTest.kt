package com.daiyimo.maeve.wechat

import org.junit.Assert.*
import org.junit.Test

class SystemAmooTranscriptTest {
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
    @Test fun staleGeneratingMarkerDoesNotBlockTheCurrentReply() {
        val old = listOf("旧问题", "正在生成")
        assertEquals("本轮答案", SystemAmooTranscript.reply(old + listOf("本轮问题", "本轮答案", "复制"), old, "本轮问题", false))
    }
    @Test fun recoveryOnlyReusesTheAnswerForTheSameRequestAndSession() {
        assertEquals("已完成", recoveredSystemAmooReply("r1", "s1", "r1", "s1", "已完成"))
        assertNull(recoveredSystemAmooReply("r2", "s1", "r1", "s1", "已完成"))
        assertNull(recoveredSystemAmooReply("r1", "s2", "r1", "s1", "已完成"))
    }
    @Test fun waitsForCompletionAndNeverReturnsStreamingText() {
        assertNull(SystemAmooTranscript.reply(listOf("问题", "半句话"), emptyList(), "问题", false))
        assertNull(SystemAmooTranscript.reply(listOf("问题", "半句话", "复制"), emptyList(), "问题", true))
        assertNull(SystemAmooTranscript.reply(listOf("问题", "正在思考", "复制"), emptyList(), "问题", false))
    }

    @Test fun onlyReturnsTheCurrentAnswerAndPreservesItsParagraphs() {
        val old = listOf("旧问题", "旧答案", "复制")
        assertEquals("第一段\n\n第二段", SystemAmooTranscript.reply(
            old + listOf("新问题", "第一段", "第二段", "已完成 3秒", "复制", "点赞"), old, "新问题", false))
    }

    @Test fun repeatedQuestionsAndIdenticalAnswersStillBelongToTheLatestTurn() {
        val old = listOf("你好", "你好呀", "复制")
        assertEquals("你好呀", SystemAmooTranscript.reply(old + old, old, "你好", false))
    }

    @Test fun scrolledOffUserMessageDoesNotLeakThePreviousAnswer() {
        val old = listOf("旧答案", "复制")
        assertNull(SystemAmooTranscript.reply(old, old, "新问题", false))
        assertEquals("新答案", SystemAmooTranscript.reply(old + listOf("新答案", "复制"), old, "新问题", false))
    }

    @Test fun numericAnswerIsNotDiscardedAsTimingMetadata() {
        assertEquals("42", SystemAmooTranscript.reply(listOf("问题", "42", "复制"), emptyList(), "问题", false))
    }

    @Test fun answerMayDiscussThinkingWithoutBeingMistakenForAStatus() {
        assertEquals("我正在思考这个问题的含义。", SystemAmooTranscript.reply(
            listOf("问题", "我正在思考这个问题的含义。", "复制"), emptyList(), "问题", false))
    }
}

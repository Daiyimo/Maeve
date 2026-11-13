package com.daiyimo.maeve.wechat

import org.junit.Assert.*
import org.junit.Test

class SystemAmooTranscriptTest {
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

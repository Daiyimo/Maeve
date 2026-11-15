package com.daiyimo.maeve.chat

import com.daiyimo.maeve.core.agent.Turn
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationAtomicPersistenceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun failedTurnReplacementKeepsOldCompleteConversation() {
        val store = ConversationStore(temp.root)
        store.append("one", Turn("old", "answer", "succeeded"))
        val old = File(temp.root, "one.json").readText()
        File(temp.root, "one.json.tmp").mkdir()
        assertTrue(runCatching { store.append("one", Turn("new", "answer", "succeeded")) }.isFailure)
        assertEquals(old, File(temp.root, "one.json").readText())
        assertEquals(listOf("old"), ConversationStore(temp.root).turns("one").map { it.userText })
    }

    @Test fun titleAndPinFailuresNeverReportSuccessOrTruncateOldValues() {
        val store = ConversationStore(temp.root)
        assertTrue(store.setTitle("one", "原来的标题"))
        assertTrue(store.setPinned("one", true, at = 1))
        File(temp.root, "one.title.tmp").mkdir(); File(temp.root, "one.pin.tmp").mkdir()
        assertFalse(store.rename("one", "未保存的新标题"))
        assertFalse(store.setPinned("one", true, at = 2))
        assertEquals("原来的标题", store.title("one"))
        assertEquals(1L, store.pinnedAt("one"))
    }

    @Test fun failedCurrentWriteDoesNotPublishAnUnsavedCurrentConversation() {
        val store = ConversationStore(temp.root)
        store.setCurrent(CurrentChat.Open("old"))
        File(temp.root, "current.tmp").mkdir()
        assertTrue(runCatching { store.setCurrent(CurrentChat.Open("new")) }.isFailure)
        assertEquals(CurrentChat.Open("old"), store.currentChat())
        assertEquals(CurrentChat.Open("old"), ConversationStore(temp.root).currentChat())
    }
}

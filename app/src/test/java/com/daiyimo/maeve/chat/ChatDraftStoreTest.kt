package com.daiyimo.maeve.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatDraftStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun state(text: String = "正文") = """{"schemaVersion":1,"generation":1,"activeKey":"new:1","aliases":{},"drafts":{"new:1":{"text":"$text","quote":{"text":"引用","fromUser":true}}},"pending":{"new:1":{"id":"ui_1794700000000000_42","text":"正文","quote":null,"fromUser":false}}}"""

    @Test fun restartPreservesDraftQuoteAndOriginalRequestId() {
        val file = File(temp.root, "drafts.json")
        assertNull(ChatDraftStore(file).load())
        ChatDraftStore(file).save(state())
        assertEquals(state(), ChatDraftStore(file).load())
    }

    @Test fun corruptStateIsPreservedAndCannotBeOverwritten() {
        val file = File(temp.root, "drafts.json").apply { writeText("{") }
        assertTrue(runCatching { ChatDraftStore(file).save(state()) }.isFailure)
        assertEquals("{", file.readText())
    }

    @Test fun invalidOrOversizeStateCannotEraseOldDrafts() {
        val file = File(temp.root, "drafts.json")
        val store = ChatDraftStore(file)
        store.save(state())
        listOf(state("x".repeat(16001)), state().replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            state().replace("ui_1794700000000000_42", "new-id")).forEach {
            assertTrue(runCatching { store.save(it) }.isFailure)
            assertEquals(state(), store.load())
        }
    }

    @Test fun failedReplaceKeepsPreviousState() {
        val file = File(temp.root, "drafts.json")
        val store = ChatDraftStore(file)
        store.save(state())
        File(temp.root, "drafts.json.tmp").mkdir()
        assertTrue(runCatching { store.save(state("新输入")) }.isFailure)
        assertEquals(state(), store.load())
    }

    @Test fun oldUiCannotOverwriteNewerDraftOrPendingRequest() {
        val file = File(temp.root, "drafts.json")
        val store = ChatDraftStore(file)
        assertEquals(0L, store.loadVersioned().first)
        assertEquals(1L, store.save(state(), expectedRevision = 0))
        assertEquals(2L, store.save(state("新界面的草稿"), expectedRevision = 1))
        assertTrue(runCatching { store.save(state("旧界面迟到的保存"), expectedRevision = 1) }.isFailure)
        assertEquals(state("新界面的草稿"), store.load())
        assertEquals(2L, ChatDraftStore(file).loadVersioned().first)
    }

    @Test fun removedConversationCannotBeRestoredByStaleDraftSave() {
        var blocked = false
        val store = ChatDraftStore(File(temp.root, "drafts.json")) { blocked }
        val source = state().replace("\"aliases\":{}", "\"aliases\":{\"conversation\":\"new:1\"}")
        store.save(source)
        blocked = true
        store.save(source)
        assertFalse(store.load()!!.contains("ui_1794700000000000_42"))
        assertFalse(store.load()!!.contains("正文"))
    }
}

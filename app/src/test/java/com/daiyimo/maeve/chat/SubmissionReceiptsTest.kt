package com.daiyimo.maeve.chat

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubmissionReceiptsTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_800_000_000_000L
    private fun id(n: Int = 1) = "ui_${now * 1000}_$n"
    private fun ledger(file: File, capacity: Int = 4096) = SubmissionReceipts(file, capacity) { now }

    @Test fun lostReplyIsResolvedAfterRestartWithoutNewSubmission() {
        val file = File(tmp.root, "receipts.json")
        val id = id()
        ledger(file).begin(id, "digest", "conversation")
        ledger(file).settle(id, true)
        assertEquals(true, ledger(file).begin(id, "digest", "conversation").accepted)
        assertEquals(false, ledger(file).find(id(2)) != null)
    }

    @Test fun interruptedSubmissionRemainsUnknownAndCannotChangePayload() {
        val file = File(tmp.root, "receipts.json")
        val id = id()
        ledger(file).begin(id, "digest", "conversation")
        assertNull(ledger(file).begin(id, "digest", "conversation").accepted)
        assertEquals("request_conflict", runCatching { ledger(file).begin(id, "other", "conversation") }.exceptionOrNull()?.message)
    }

    @Test fun corruptedLedgerIsPreserved() {
        val file = File(tmp.root, "receipts.json").apply { writeText("{broken") }
        assertTrue(runCatching { ledger(file).begin(id(), "digest", "c") }.isFailure)
        assertEquals("{broken", file.readText())
    }

    @Test fun capacityNeverEvictsUnknownAndExpiredIdsCannotReplay() {
        val file = File(tmp.root, "receipts.json")
        val original = id()
        ledger(file, 1).begin(original, "digest", "c")
        assertEquals("receipt_capacity", runCatching { ledger(file, 1).begin(id(2), "digest", "c") }.exceptionOrNull()?.message)
        assertNull(ledger(file).find(original)?.accepted)
        now += SubmissionReceipts.RETENTION_MS + 1
        ledger(file, 1).begin(id(3), "new", "c")
        assertNull(ledger(file).find(original))
        assertEquals("request_expired", runCatching { ledger(file).begin(original, "digest", "c") }.exceptionOrNull()?.message)
    }
}

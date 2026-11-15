package com.daiyimo.maeve.intercept

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileBridgeReceiptsTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun restartKeepsTheSameTaskAndDoesNotClaimANewSubmission() {
        val first = FileBridgeReceipts(tmp.root).begin("one", "goal", false)
        assertTrue(first.second)
        val restarted = FileBridgeReceipts(tmp.root).begin("one", "goal", false)
        assertEquals(first.first, restarted.first)
        assertFalse(restarted.second)
        assertTrue(runCatching { FileBridgeReceipts(tmp.root).begin("one", "changed goal", false) }.isFailure)
    }
    @Test fun disabledRequestCannotStartAfterTheSwitchIsEnabled() {
        FileBridgeReceipts(tmp.root).begin("one", "goal", true)
        val resumed = FileBridgeReceipts(tmp.root).begin("one", "goal", false)
        assertTrue(resumed.first.rejected)
        assertFalse(resumed.second)
    }
    @Test fun capacityAndCorruptionNeverDropUnknownReceipts() {
        val ledger = FileBridgeReceipts(tmp.root, capacity = 1)
        ledger.begin("one", "goal", false)
        assertEquals("receipt_capacity", runCatching { ledger.begin("two", "goal", false) }.exceptionOrNull()?.message)
        val file = tmp.root.listFiles()!!.single()
        file.writeText("{broken")
        assertTrue(runCatching { ledger.begin("one", "goal", false) }.isFailure)
        assertEquals("{broken", file.readText())
    }
    @Test fun strictRequestRejectsAmbiguousOversizeAndExpiredInputs() {
        for (body in listOf("""{"goal":"one","goal":"two"}""", """{"goal":"one","g\u006fal":"two"}""",
            """{"goal":{"nested":"no"}}""", """{"goal":"one","schemaVersion":"bad"}""", """{"goal":"${"x".repeat(4001)}"}""")) assertNull(body.take(70), parseAmooRequest(body))
        val versioned = """{"schemaVersion":1,"goal":"one","createdAt":1000,"expiresAt":2000}"""
        assertNotNull(parseAmooRequest(versioned, now = 1500))
        assertNull(parseAmooRequest(versioned, now = 3000))
        assertNotNull(parseAmooRequest(versioned, now = 3000, allowExpired = true))
    }
    @Test fun queueAdmissionSurvivesRestartAndOnlyTerminalResultsReleaseTheSlot() {
        val first = FileBridgeReceipts(tmp.root, pendingLimit = 2)
        assertFalse(first.begin("one", "goal", false).first.rejected)
        assertFalse(first.begin("two", "goal", false).first.rejected)
        val restarted = FileBridgeReceipts(tmp.root, pendingLimit = 2)
        val overflow = restarted.begin("three", "goal", false).first
        assertTrue(overflow.rejected)
        assertEquals("queue_full", overflow.rejectionCode)
        restarted.finish("one")
        // 被拒的同一个 ID 不因释放容量而突然执行。
        assertTrue(restarted.begin("three", "goal", false).first.rejected)
        assertFalse(restarted.begin("four", "goal", false).first.rejected)
        val settled = FileBridgeReceipts(tmp.root, pendingLimit = 2).begin("one", "goal", false)
        assertTrue(settled.first.finished)
        assertFalse(settled.second)
    }
}

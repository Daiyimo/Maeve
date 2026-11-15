package com.daiyimo.maeve.intercept

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MailboxScannerTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun limitedBatchesEventuallyReachAllClaimsWithoutRevisitingTheFirstBatch() {
        repeat(250) { File(temp.root, "$it.claimed").writeText("{}") }
        MailboxScanner().use { scanner ->
            val first = scanner.batch(temp.root)
            val second = scanner.batch(temp.root)
            val third = scanner.batch(temp.root)
            assertEquals(100, first.size); assertEquals(100, second.size); assertEquals(50, third.size)
            assertEquals(250, (first + second + third).map { it.name }.distinct().size)
        }
    }
    @Test fun invalidEntriesAlsoConsumeTheInspectionBudgetAndFoldersAreNeverRequests() {
        repeat(20) { File(temp.root, "$it.unrelated").writeText("x") }
        File(temp.root, "directory.json").mkdir()
        File(temp.root, "valid.json").writeText("{}")
        MailboxScanner().use { scanner ->
            val files = (1..8).flatMap { scanner.batch(temp.root, 3) }
            assertEquals(listOf("valid.json"), files.map { it.name })
        }
    }
}

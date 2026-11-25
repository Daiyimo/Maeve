package com.daiyimo.maeve.task

import com.daiyimo.maeve.core.run.RunPhase
import com.daiyimo.maeve.core.run.RunStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UiSubmissionQueueTest {
    private data class Run(val conversationId: String, val goal: String) {
        val key = "$conversationId/$goal"
    }

    @get:Rule val tmp = TemporaryFolder()

    @Test fun queuedUiSubmissionIsRecordedAndDeduplicatedAcrossRestart() {
        val file = File(tmp.root, "runs.json")
        fun center(store: RunStore) =
            TaskCenter({ ParallelLimit(1, null) }, Run::conversationId, Run::key, store, create = ::Run)
        val first = center(FileRunStateStore(file))
        first.tryStart("same", "previous")
        assertTrue(first.tryStart("same", "submitted", queueInConversation = true,
            submissionId = "request") is StartResult.Queued)
        val restored = FileRunStateStore(file)
        val run = restored.all().single { it.submissionId == "request" }
        assertEquals(RunPhase.Queued, run.phase)
        assertEquals("submitted", run.goal)
        assertSame(StartResult.DuplicateSubmission, center(restored).tryStart("same", "submitted",
            queueInConversation = true, submissionId = "request"))
    }
}

package com.daiyimo.maeve.ui

import com.daiyimo.maeve.core.record.RunEvent
import org.junit.Assert.*
import org.junit.Test

class RunTokensTest {
    @Test fun wholeRunCountsReplaceLoopCountsAndDoNotAddCachedInputTwice() {
        val rows = listOf(
            RunEvent.ModelReply(1, 1, "", null, null, 100, 20, 30),
            RunEvent.ModelCalls(2, 3, 1, 240, 60, 80),
        )
        val usage = RunTokens.of(rows)!!
        assertEquals(300L, usage.input + usage.output)
        assertEquals(80L, usage.cached)
        assertEquals(1, usage.unreported)
        assertEquals("run", usage.scope)
        assertEquals(120L, RunTokens.replies(rows.filterIsInstance<RunEvent.ModelReply>())!!.let { it.input + it.output })
    }
    @Test fun missingUsageIsNotPresentedAsMeasuredZero() {
        assertNull(RunTokens.of(emptyList()))
        val missing = RunTokens.of(listOf(RunEvent.ModelReply(1, 1, "", null, null, null, null, null)))!!
        assertFalse(missing.reported)
        assertEquals(1, missing.unreported)
        assertFalse(RunTokens.of(listOf(RunEvent.ModelCalls(1, 2, 2, 0, 0, 0)))!!.reported)
    }
}

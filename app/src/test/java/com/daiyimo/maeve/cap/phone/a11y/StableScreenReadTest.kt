package com.daiyimo.maeve.cap.phone.a11y

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StableScreenReadTest {
    private val a = StableScreenRead.Reading(1, 10, "com.example")
    private val b = a.copy(content = 2)

    private class Fake {
        var now = 0L
        var reads = 0
        suspend fun run(timeoutMs: Long, read: () -> StableScreenRead.Reading): Boolean {
            reads = 0
            return StableScreenRead.await(timeoutMs, { now }, { now += it }) { reads++; read() }
        }
    }

    @Test fun identicalReadsNeedAQuietInterval() = runTest {
        val f = Fake()
        assertTrue(f.run(2_000) { a })
        assertTrue("stable at ${f.now}", f.now >= StableScreenRead.QUIET_MS)
        assertTrue("stable at ${f.now}", f.now <= StableScreenRead.QUIET_MS + StableScreenRead.POLL_MS)
        assertTrue("needs repeated reads, got ${f.reads}", f.reads >= 3)
    }

    @Test fun contentChangeRestartsTheQuietInterval() = runTest {
        val f = Fake()
        val reads = ArrayDeque(listOf(a, a, b, b, b))
        assertTrue(f.run(2_000) { reads.removeFirstOrNull() ?: b })
        assertTrue("stable too early at ${f.now}", f.now >= 800)
    }

    @Test fun treeOrAppChangeAlsoRestartsTheQuietInterval() = runTest {
        for (changed in listOf(a.copy(tree = 11), a.copy(topPackage = "com.other"))) {
            val f = Fake()
            val reads = ArrayDeque(listOf(a, a, changed, changed, changed))
            assertTrue(f.run(2_000) { reads.removeFirstOrNull() ?: changed })
            assertTrue("stable too early at ${f.now}", f.now >= 800)
        }
    }

    @Test fun continuouslyChangingReadsTimeOut() = runTest {
        val f = Fake()
        var n = 0
        assertFalse(f.run(1_000) { if (n++ % 2 == 0) a else b })
        assertTrue("timed out at ${f.now}", f.now >= 1_000)
        assertTrue("timed out at ${f.now}", f.now < 1_000 + StableScreenRead.POLL_MS)
    }

    @Test fun zeroTimeoutDoesNotReportStable() = runTest {
        val f = Fake()
        assertFalse(f.run(0) { a })
        assertEquals(1, f.reads)
    }

    @Test fun pollingIsCancellable() = runTest {
        val sleeping = CompletableDeferred<Unit>()
        val job = launch {
            StableScreenRead.await(10_000, { 0L }, { sleeping.complete(Unit); awaitCancellation() }) { a }
        }
        sleeping.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}

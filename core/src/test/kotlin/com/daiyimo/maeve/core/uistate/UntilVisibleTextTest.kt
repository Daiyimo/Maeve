package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.testing.button
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.tool.Until
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UntilVisibleTextTest {
    private fun snapshot(protected: Boolean = false) = ScreenSnapshot("s1", "com.calc", listOf(button("@e1", "C")), 1200, 2670, protected = protected)

    @Test fun renderedTextCanSatisfyAPositiveConditionWhenTheTreeOmitsIt() = runTest {
        val screen = object : ScreenPort by FakeScreen() {
            override suspend fun readVisibleText() = listOf(VisibleTextLine("37", listOf(10, 20, 100, 50)))
        }
        val result = UntilWait(screen, { snapshot() }, { testScheduler.currentTime }, { false }).run(Until(appears = "37", timeoutMs = 8000))
        assertTrue("Visible 37 should not cause an 8-second timeout and repeat an executed key", result.met)
        assertTrue(result.waitedMs < 8000)
    }

    @Test fun protectedPagesNeverUseTheVisualTextFallback() = runTest {
        var reads = 0
        val screen = object : ScreenPort by FakeScreen() {
            override suspend fun readVisibleText(): List<VisibleTextLine> { reads++; return listOf(VisibleTextLine("37", emptyList())) }
        }
        val result = UntilWait(screen, { snapshot(true) }, { testScheduler.currentTime }, { false }).run(Until(appears = "37", timeoutMs = 400))
        assertFalse(result.met); assertEquals(0, reads)
    }

    @Test fun anEmptyOcrResultDoesNotProveThatTextDisappeared() = runTest {
        var reads = 0
        val frame = snapshot().copy(elements = listOf(button("@e1", "37")))
        val screen = object : ScreenPort by FakeScreen() {
            override suspend fun readVisibleText(): List<VisibleTextLine> { reads++; return emptyList() }
        }
        val result = UntilWait(screen, { frame }, { testScheduler.currentTime }, { false }).run(Until(gone = "37", timeoutMs = 400))
        assertFalse(result.met); assertEquals(0, reads)
    }

    @Test fun secretInputFieldsAlsoPreventTheVisualTextFallback() = runTest {
        var reads = 0
        val frame = snapshot().copy(elements = listOf(button("@e1", "密码").copy(secret = true)))
        val screen = object : ScreenPort by FakeScreen() {
            override suspend fun readVisibleText(): List<VisibleTextLine> { reads++; return listOf(VisibleTextLine("37", emptyList())) }
        }
        val result = UntilWait(screen, { frame }, { testScheduler.currentTime }, { false }).run(Until(appears = "37", timeoutMs = 400))
        assertFalse(result.met); assertEquals(0, reads)
    }
}

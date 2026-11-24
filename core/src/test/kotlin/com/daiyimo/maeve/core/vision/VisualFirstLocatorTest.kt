package com.daiyimo.maeve.core.vision

import com.daiyimo.maeve.core.agent.FakeModel
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualFirstLocatorTest {
    @Test fun exactOcrTargetKeepsRoleAmbiguityAndSurfaceEvidence() {
        fun text(y: Int) = com.daiyimo.maeve.core.uistate.VisibleTextLine("搜索", listOf(100, y, 300, y + 40))
        val registry = LocateRegistry()
        val result = registry.locateTextFrame("搜索", 11L, listOf(text(80)), "com.xingin.xhs", 720 to 1600) as LocateResult.Found
        org.junit.Assert.assertEquals("ocr", result.source)
        org.junit.Assert.assertTrue(registry.matchesSurface(result.ref, result.snapshotId, "com.xingin.xhs", 720 to 1600))
        org.junit.Assert.assertFalse(registry.matchesSurface(result.ref, result.snapshotId, "com.tencent.mm", 720 to 1600))
        org.junit.Assert.assertFalse(registry.matchesSurface(result.ref, result.snapshotId, "com.xingin.xhs", 1600 to 720))
        val ambiguous = registry.locateTextFrame("搜索", 11L, listOf(text(80), text(250)), "com.xingin.xhs", 720 to 1600) as LocateResult.NotFound
        org.junit.Assert.assertEquals("element_ambiguous", ambiguous.reason)
    }

    private fun locator(screen: ScreenPort, model: FakeModel) =
        Locator(screen, VisionService(model, JvmTestImageOps), LocateRegistry(), ScreenFamily("phone", "p"))

    @Test fun visualFirstLocateNeverReadsTree() = runTest {
        val base = LocScreen()
        var snapshotReads = 0
        var peekReads = 0
        val screen = object : ScreenPort by base {
            override suspend fun currentPackage(): String? = "com.xingin.xhs"
            override suspend fun snapshot(): ScreenSnapshot {
                snapshotReads++
                return base.snapshot()
            }
            override suspend fun peek(): ScreenSnapshot {
                peekReads++
                return base.peek()
            }
        }
        val model = says("""{"bbox_2d":[100,100,300,200],"seen":"搜索"}""", "yes")
        val result = locator(screen, model).locate("搜索", "搜索")

        assertTrue(result is LocateResult.Found)
        assertEquals("vision", (result as LocateResult.Found).source)
        assertEquals(0, snapshotReads)
        assertEquals(0, peekReads)
        assertEquals(2, model.requests.size)
    }

    @Test fun visualFirstMissingScreenshotUsesPackageWithoutPeek() = runTest {
        val base = LocScreen(emptyList(), null)
        val screen = object : ScreenPort by base {
            override suspend fun currentPackage(): String? = "com.xingin.xhs"
        }
        val result = locator(screen, says()).locate("搜索", "搜索")

        assertEquals("screenshot_unavailable", (result as LocateResult.NotFound).reason)
        assertEquals(0, base.peeks)
    }
}

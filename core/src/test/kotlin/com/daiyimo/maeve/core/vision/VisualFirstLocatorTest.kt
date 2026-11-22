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

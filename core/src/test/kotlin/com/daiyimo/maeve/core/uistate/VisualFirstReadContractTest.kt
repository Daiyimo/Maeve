package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.access.AppAccessPolicy
import com.daiyimo.maeve.core.access.AppAccessState
import com.daiyimo.maeve.core.agent.FakeModel
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import com.daiyimo.maeve.core.vision.LocateRegistry
import com.daiyimo.maeve.core.vision.LocateResult
import com.daiyimo.maeve.core.vision.Locator
import com.daiyimo.maeve.core.vision.VisionService
import com.daiyimo.maeve.core.vision.says
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualFirstReadContractTest {
    private class Screen : ScreenPort by FakeScreen() {
        override val appAccess = AppAccessPolicy { AppAccessState() }
        var rawShots = 0
        var stableShots = 0
        var snapshots = 0
        var peeks = 0
        var ocr: List<VisibleTextLine>? = null
        val raw = JvmTestImageOps.encode(108, 240) { _, _ -> 0 }
        val stable = JvmTestImageOps.encode(108, 240) { _, _ -> 255 }
        override suspend fun currentPackage() = "com.tencent.jkchess"
        override suspend fun viewport() = 1080 to 2400
        override suspend fun snapshot(): ScreenSnapshot { snapshots++; return FakeScreen().snapshot() }
        override suspend fun peek(): ScreenSnapshot { peeks++; return FakeScreen().peek() }
        override suspend fun screenshotPng(): ByteArray { rawShots++; return raw }
        override suspend fun screenshotPngStable(): ByteArray { stableShots++; return stable }
        override suspend fun readVisibleText() = ocr
        override suspend fun visualFrame(withImage: Boolean, timeoutMs: Long) =
            VisualFrame(if (withImage) stable else null, readVisibleText(), 1080, 2400)
    }

    @Test fun screenshotUsesStableFrameWithoutTree() = runTest {
        val screen = Screen()
        val result = toolDispatcher(screen, visionEnabled = true)
            .executeRead(Part.ToolCall("shot", "phone_screenshot", "{}"))

        assertArrayEquals(screen.stable, result.image)
        assertEquals(1, screen.stableShots)
        assertEquals(0, screen.rawShots)
        assertEquals(0, screen.snapshots)
        assertEquals(0, screen.peeks)
    }

    @Test fun findVisuallyUsesOcrForTabLabel() = runTest {
        val screen = Screen().apply { ocr = listOf(VisibleTextLine("召唤", listOf(100, 100, 300, 160))) }
        val model = FakeModel()
        val dispatcher = toolDispatcher(screen, visionEnabled = true, vision = VisionService(model, JvmTestImageOps))
        val result = dispatcher.executeRead(
            Part.ToolCall("find", "phone_find_visually", """{"target":"召唤页签","text":"召唤"}"""),
        ).content

        assertEquals("true", result["found"]!!.jsonPrimitive.content)
        assertEquals("ocr", result["source"]!!.jsonPrimitive.content)
        assertTrue(model.requests.isEmpty())
    }

    @Test fun visualLocatorUsesStableFrame() = runTest {
        val screen = Screen()
        val vision = VisionService(says("""{"bbox_2d":[0,0,100,100],"seen":"开始游戏"}""", "是"), JvmTestImageOps)
        val locator = Locator(screen, vision, LocateRegistry(), ScreenFamily("phone", "p"))
        val result = locator.locate("开始游戏", "开始游戏")

        assertTrue(result is LocateResult.Found)
        assertEquals(1, screen.stableShots)
        assertEquals(0, screen.rawShots)
    }

    @Test fun describeIsSkippedWhenMainModelSeesImage() = runTest {
        val screen = Screen()
        val model = FakeModel()
        val dispatcher = toolDispatcher(screen, visionEnabled = true, vision = VisionService(model, JvmTestImageOps))
        val result = dispatcher.executeRead(
            Part.ToolCall("desc", "phone_describe", """{"task":"看看页面","reason":"测试"}"""),
        )

        assertEquals("visual_first", result.content["error"]!!.jsonPrimitive.content)
        assertTrue(model.requests.isEmpty())
        assertEquals(0, screen.rawShots)
        assertEquals(0, screen.stableShots)
    }
}

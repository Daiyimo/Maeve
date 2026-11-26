package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.access.AppAccessPolicy
import com.daiyimo.maeve.core.access.AppAccessState
import com.daiyimo.maeve.core.action.ActionExecutor
import com.daiyimo.maeve.core.action.ActionOutcome
import com.daiyimo.maeve.core.action.ActionRecorder
import com.daiyimo.maeve.core.action.Origin
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.agent.FakeModel
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.execute
import com.daiyimo.maeve.core.tool.okObj
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import com.daiyimo.maeve.core.vision.VisionService
import com.daiyimo.maeve.core.vision.says
import com.daiyimo.maeve.core.visual.FrameGrid
import com.daiyimo.maeve.core.visual.Shots
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class VisualFirstTest {
    private class CountingScreen(
        var packageName: String? = "com.xingin.xhs",
        private val ocr: List<VisibleTextLine>? = listOf(VisibleTextLine("搜索", listOf(100, 100, 300, 160), .9f)),
    ) : ScreenPort by FakeScreen() {
        var access = AppAccessState()
        override val appAccess = AppAccessPolicy { access }
        private var result = ActionResult.Ok(changed = true)
        var snapshots = 0
        var peeks = 0
        var shots = 0
        var ocrReads = 0
        var viewportReads = 0
        var frameGrids = 0
        var visualWaits = 0
        var treeWaits = 0
        var scrolls = 0
        val swipes = mutableListOf<List<Int>>()
        val taps = mutableListOf<Pair<Int, Int>>()

        override suspend fun currentPackage(): String? = packageName
        override suspend fun viewport(): Pair<Int, Int> {
            viewportReads++
            return 1080 to 2400
        }
        override suspend fun snapshot(): ScreenSnapshot {
            snapshots++
            return tree()
        }
        override suspend fun peek(): ScreenSnapshot {
            peeks++
            return tree()
        }
        private fun tree() = ScreenSnapshot("tree", packageName,
            listOf(UiElement("@e1", "button", "搜索", 540, 120, listOf(0, 0, 999, 100), null, true, false, false)),
            1080, 2400)
        override suspend fun screenshotPng(): ByteArray {
            shots++
            return JvmTestImageOps.encode(108, 240) { _, _ -> 128 }
        }
        override suspend fun screenshotPngStable(): ByteArray = screenshotPng()
        override suspend fun readVisibleText(): List<VisibleTextLine>? {
            ocrReads++
            return ocr
        }
        override suspend fun visualFrame(withImage: Boolean, timeoutMs: Long): VisualFrame =
            VisualFrame(if (withImage) screenshotPng() else null, readVisibleText(), 1080, 2400)

        override suspend fun frameGrid(): FrameGrid? {
            frameGrids++
            return null
        }
        override suspend fun waitVisualStable(timeoutMs: Long): Boolean {
            visualWaits++
            return true
        }
        override suspend fun waitStable(timeoutMs: Long): Boolean {
            treeWaits++
            return true
        }
        override suspend fun scroll(ref: String?, snapshotId: String?, down: Boolean): ActionResult {
            scrolls++
            return result
        }
        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): ActionResult {
            swipes += listOf(x1, y1, x2, y2)
            return result
        }
        override suspend fun tapXY(x: Int, y: Int): ActionResult {
            taps += x to y
            return result
        }
    }

    private fun session(screen: ScreenPort, visionEnabled: Boolean = false) = ScreenSession(
        surface = Surface(ScreenFamily("phone", "p"), screen, { it }, "app"),
        stableRefs = false,
        visionEnabled = visionEnabled,
        recordFrames = false,
        lastRead = { null },
        shots = Shots(),
    )

    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.flag(key: String) = this[key]!!.jsonPrimitive.boolean

    @Test fun policyUsesGlobalDefaultAndPerAppOverride() {
        val defaults = AppAccessState()
        assertTrue(VisualFirstPolicy.applies("com.example.app", defaults))
        assertTrue(VisualFirstPolicy.applies("  COM.TENCENT.JKCHESS  ", defaults))
        assertFalse(VisualFirstPolicy.applies(null, defaults))
        val custom = AppAccessState(visualFirstDefault = false,
            visualFirstOverrides = mapOf("com.tencent.mm" to true, "com.example.app" to false))
        assertTrue(VisualFirstPolicy.applies("com.tencent.mm", custom))
        assertFalse(VisualFirstPolicy.applies("com.example.app", custom))
        assertFalse(VisualFirstPolicy.applies("com.tencent.jkchess", custom))
    }

    @Test fun visualModeSwitchAppliesToAnExistingSession() = runTest {
        val screen = CountingScreen(packageName = "com.example.app")
        screen.access = AppAccessState(visualFirstDefault = false)
        val state = session(screen)
        state.observe(withImage = false)
        assertEquals(1, screen.snapshots)

        screen.access = AppAccessState(visualFirstDefault = true, revision = screen.access.revision + 1)
        state.checkAppAccess(false)
        assertNull(state.lastSnapshot)
        assertFalse(state.treeUnusable())
        val visual = state.observe(withImage = false)
        assertTrue(visual.content.flag("ocrOnly"))
        assertEquals(1, screen.snapshots)

        screen.access = AppAccessState(visualFirstDefault = false, revision = screen.access.revision + 1)
        state.checkAppAccess(false)
        state.observe(withImage = false)
        assertEquals(2, screen.snapshots)
    }

    @Test fun visualModeSwitchThroughReadToolsClearsPreviousModeCache() = runTest {
        val screen = CountingScreen(packageName = "com.example.app")
        screen.access = AppAccessState(visualFirstDefault = false)
        val dispatcher = toolDispatcher(screen, visionEnabled = true,
            vision = VisionService(says(), JvmTestImageOps))
        dispatcher.executeRead(Part.ToolCall("tree", "phone_observe", "{}"))
        assertEquals(1, screen.snapshots)

        screen.access = AppAccessState(visualFirstDefault = true, revision = screen.access.revision + 1)
        val firstVisual = dispatcher.executeRead(Part.ToolCall("visual-1", "phone_observe", "{}"))
        assertNotNull(firstVisual.image)
        assertEquals(1, screen.snapshots)

        screen.access = AppAccessState(visualFirstDefault = false, revision = screen.access.revision + 1)
        dispatcher.executeRead(Part.ToolCall("tree-2", "phone_observe", "{}"))
        assertEquals(2, screen.snapshots)

        screen.access = AppAccessState(visualFirstDefault = true, revision = screen.access.revision + 1)
        val secondVisual = dispatcher.executeRead(Part.ToolCall("visual-2", "phone_observe", "{}"))
        assertNotNull("switching back to visual mode must not reuse the old frame digest", secondVisual.image)
        assertFalse((secondVisual.content["screen"] as? JsonObject)?.flag("imageUnchanged") == true)
        assertEquals(2, screen.snapshots)
    }

    @Test fun visualObserveUsesScreenshotAndOcrWithoutTree() = runTest {
        val screen = CountingScreen()
        val state = session(screen, visionEnabled = true)
        val result = state.observe(withImage = true)

        assertEquals(0, screen.snapshots)
        assertEquals(0, screen.peeks)
        assertEquals(1, screen.shots)
        assertEquals(1, screen.ocrReads)
        assertTrue(result.content.flag("visualOnly"))
        assertEquals("vision", result.content.text("readMode"))
        assertEquals("unusable", result.content.text("treeQuality"))
        assertEquals("com.xingin.xhs", result.content.text("currentApp"))
        assertTrue(result.content["elements"]!!.jsonArray.isEmpty())
        assertFalse("tree" in result.content)
        assertEquals(listOf("搜索"), result.content["visibleText"]!!.jsonArray.map {
            (it as JsonObject).text("text")
        })
        assertNotNull(result.image)
        assertTrue(state.treeUnusable())
        assertTrue(state.lastSnapshot!!.elements.isEmpty())
        assertTrue(state.lastSnapshot!!.windows.isEmpty())
    }

    @Test fun visualFrameIsDiscardedIfTheForegroundChangesDuringCapture() = runTest {
        val screen = object : ScreenPort by FakeScreen() {
            override val appAccess = AppAccessPolicy { AppAccessState() }
            private var pkg = "com.xingin.xhs"
            override suspend fun currentPackage() = pkg
            override suspend fun visualFrame(withImage: Boolean, timeoutMs: Long): VisualFrame {
                pkg = "com.tencent.mm"
                return VisualFrame(byteArrayOf(1), listOf(VisibleTextLine("旧页面", listOf(0, 0, 500, 100))), 100, 200)
            }
        }
        val result = session(screen, visionEnabled = true).observe(withImage = true)
        assertEquals("com.tencent.mm", result.content.text("currentApp"))
        assertNull(result.image)
        assertNull(result.content["visibleText"])
    }

    @Test fun automaticObservationDoesNotResendAnIdenticalFrameWithReadableText() = runTest {
        val screen = CountingScreen()
        val state = session(screen, visionEnabled = true)
        assertNotNull(state.observe(withImage = true).image)
        val after = state.withScreen(ToolExecution(okObj { put("ok", true) }))
        assertNull(after.image)
        assertTrue((after.content["screen"] as JsonObject).flag("imageUnchanged"))
        assertNotNull(state.observe(withImage = true).image)
    }

    @Test fun noVisionModelReturnsExplicitOcrOnlyWithoutTreeFallback() = runTest {
        val screen = CountingScreen()
        val result = session(screen).observe(withImage = false)

        assertEquals(0, screen.snapshots)
        assertEquals(0, screen.peeks)
        assertNull(result.image)
        assertTrue(result.content.flag("ocrOnly"))
        assertTrue(result.content.text("hint").contains("OCR-only"))
        assertTrue(result.content.text("hint").contains("do not call list_elements"))
    }

    @Test fun nonVisualAppKeepsTreeObserveBehavior() = runTest {
        val screen = CountingScreen(packageName = "com.example.app")
        screen.access = AppAccessState(visualFirstDefault = false)
        val result = session(screen).observe(withImage = false)

        assertEquals(1, screen.snapshots)
        assertEquals(0, screen.peeks)
        assertFalse("visualOnly" in result.content)
        assertFalse(result.content["elements"]!!.jsonArray.isEmpty())
    }

    @Test fun visualProbeAndWithScreenDoNotReadTree() = runTest {
        val screen = CountingScreen()
        val state = session(screen, visionEnabled = true)
        state.observe(withImage = true)
        val probe = state.probe(withPng = true)
        val updated = state.withScreen(ToolExecution(okObj { put("ok", true) }, screenSettled = true))

        assertNotNull(probe.png)
        assertTrue(probe.before!!.elements.isEmpty())
        assertTrue(screen.frameGrids > 0)
        val glance = updated.content["screen"] as JsonObject
        assertTrue(glance.flag("visualOnly"))
        assertTrue(glance["elements"]!!.jsonArray.isEmpty())
        assertEquals(0, screen.snapshots)
        assertEquals(0, screen.peeks)
    }

    @Test fun defaultVisualScrollUsesGestureAndNonVisualScrollUsesAccessibility() = runTest {
        val visual = CountingScreen()
        val scrollArgs = buildJsonObject { put("direction", "down") }.toString()
        toolDispatcher(visual).execute(Part.ToolCall("s1", "phone_scroll", scrollArgs))
        assertEquals(0, visual.scrolls)
        assertEquals(1, visual.swipes.size)
        assertEquals(0, visual.snapshots)
        assertEquals(0, visual.peeks)
        assertTrue(visual.viewportReads > 0)

        val normal = CountingScreen(packageName = "com.example.app")
        normal.access = AppAccessState(visualFirstDefault = false)
        toolDispatcher(normal).execute(Part.ToolCall("s2", "phone_scroll", scrollArgs))
        assertEquals(1, normal.scrolls)
        assertTrue(normal.swipes.isEmpty())
    }

    @Test fun coordinateTapUsesViewportWithoutTree() = runTest {
        val screen = CountingScreen()
        val tapArgs = buildJsonObject {
            put("x", 500)
            put("y", 999)
            put("target", "搜索")
        }.toString()
        toolDispatcher(screen).execute(Part.ToolCall("t", "phone_tap", tapArgs))

        assertEquals(listOf(540 to 2399), screen.taps)
        assertTrue(screen.viewportReads > 0)
        assertEquals(0, screen.snapshots)
        assertEquals(0, screen.peeks)
    }

    @Test fun diagnoseDoesNotReadTree() = runTest {
        val screen = CountingScreen()
        val result = toolDispatcher(screen).execute(Part.ToolCall("d", "phone_diagnose", "{}"))
        assertEquals("true", result.content["usable"]!!.jsonPrimitive.content)
        assertEquals(0, screen.snapshots)
        assertEquals(0, screen.peeks)
    }

    @Test fun findVisuallyUsesSameFrameOcrBeforeVisionModel() = runTest {
        val screen = CountingScreen()
        val model = FakeModel()
        val dispatcher = toolDispatcher(screen, visionEnabled = true, vision = VisionService(model, JvmTestImageOps))
        val result = dispatcher.executeRead(Part.ToolCall("find", "phone_find_visually",
            """{"target":"搜索","text":"搜索"}""")).content

        assertEquals(result.toString(), "true", result["found"]?.jsonPrimitive?.content)
        assertEquals("ocr", result["source"]?.jsonPrimitive?.content)
        assertTrue(model.requests.isEmpty())
    }

    @Test fun findVisuallyDoesNotUseOcrWhenTargetAddsSpatialConstraint() = runTest {
        val screen = CountingScreen()
        val model = says("null")
        val dispatcher = toolDispatcher(screen, visionEnabled = true, vision = VisionService(model, JvmTestImageOps))
        val result = dispatcher.executeRead(Part.ToolCall("find", "phone_find_visually",
            """{"target":"右上角搜索图标","text":"搜索"}""")).content
        assertEquals("false", result["found"]!!.jsonPrimitive.content)
        assertTrue(model.requests.isNotEmpty())
    }

    @Test fun describedVisualRefBypassesRepeatedResolution() = runTest {
        val screen = CountingScreen()
        val dispatcher = toolDispatcher(screen, visionEnabled = true)
        val args = buildJsonObject {
            put("element", "搜索")
            put("ref", "@v1")
            put("snapshotId", "loc_existing")
        }
        val described = dispatcher.ctx.interfaces.described(dispatcher.declared("phone_tap"), args) as Described.Resolved

        assertFalse("element" in described.args)
        assertFalse("role" in described.args)
        assertEquals("@v1", described.args["ref"]!!.jsonPrimitive.content)
        assertEquals(0, screen.shots)
        assertEquals(0, screen.ocrReads)
    }

    @Test fun locatedRefWithElementLabelExecutesWithoutReResolving() = runTest {
        val screen = CountingScreen()
        val model = FakeModel()
        val dispatcher = toolDispatcher(screen, visionEnabled = true, vision = VisionService(model, JvmTestImageOps))
        val found = dispatcher.executeRead(Part.ToolCall("find", "phone_find_visually",
            """{"target":"搜索","text":"搜索"}""")).content
        val args = buildJsonObject {
            put("element", "搜索"); put("ref", found["ref"]!!.jsonPrimitive.content)
            put("snapshotId", found["snapshotId"]!!.jsonPrimitive.content)
        }
        val executor = ActionExecutor(dispatcher, OperationGuards(), ConfirmationGate(), gate = {}, recorder = ActionRecorder(RunSink.NONE))
        val outcome = executor.run(Part.ToolCall("tap", "phone_tap", args.toString()), Origin.Model(1))
        assertTrue(outcome.toString(), outcome is ActionOutcome.Ran)
        assertEquals(1, screen.taps.size)
    }

    @Test fun listElementsInVisualModeDoesNotCaptureScreenOrReadTree() = runTest {
        val screen = CountingScreen()
        val dispatcher = toolDispatcher(screen, visionEnabled = true)
        dispatcher.executeRead(Part.ToolCall("observe", "phone_observe", "{}"))
        val result = dispatcher.executeRead(Part.ToolCall("list", "phone_list_elements", "{}")).content

        assertTrue(result.flag("visualOnly"))
        assertEquals("vision", result.text("readMode"))
        assertTrue(result["elements"]!!.jsonArray.isEmpty())
        assertEquals(0, screen.snapshots)
        assertEquals(0, screen.peeks)
        assertEquals(1, screen.shots)
        assertEquals(1, screen.ocrReads)
    }

    @Test fun visualTreeReadDoesNotReuseAnotherAppsCachedText() = runTest {
        val screen = CountingScreen()
        val dispatcher = toolDispatcher(screen, visionEnabled = true)
        dispatcher.executeRead(Part.ToolCall("list-1", "phone_list_elements", "{}"))
        screen.packageName = "com.tencent.mm"
        val result = dispatcher.executeRead(Part.ToolCall("list-2", "phone_list_elements", "{}")).content
        assertEquals("com.tencent.mm", result.text("currentApp"))
        assertTrue(screen.ocrReads >= 2)
    }

    @Test fun screenshotToolDoesNotReadTree() = runTest {
        val screen = CountingScreen()
        val dispatcher = toolDispatcher(screen, visionEnabled = true, vision = VisionService(says(), JvmTestImageOps))
        val result = dispatcher.executeRead(Part.ToolCall("shot", "phone_screenshot", "{}"))

        assertNotNull(result.image)
        assertEquals(0, screen.snapshots)
        assertEquals(0, screen.peeks)
    }
}

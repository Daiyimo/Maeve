package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.agent.FakeModel
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.ModelError
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.UiElement
import com.daiyimo.maeve.core.vision.FRAME_A
import com.daiyimo.maeve.core.vision.FRAME_B
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import com.daiyimo.maeve.core.vision.LocScreen
import com.daiyimo.maeve.core.vision.VisionService
import com.daiyimo.maeve.core.vision.says
import com.daiyimo.maeve.core.vision.uiEl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 点击前后的视觉核查走真实执行链(点前拦可疑落点、点后按视觉改判效果):
 * 和模型自己传 @vN 一样经 [ActionExecutor] + 分发器,视觉模型是脚本化的假件。
 */
class TapVisionFlowTest {
    private val sink = object : RunSink { override fun event(e: RunEvent) {} }

    /** 点了就换一屏元素:树能判出「明显变了」(点后视觉核对要在这个基础上改判)。 */
    private class SwapScreen(val inner: LocScreen, private val after: List<UiElement>) : ScreenPort by inner {
        val taps: List<String> get() = inner.taps
        override suspend fun tapXY(x: Int, y: Int): ActionResult {
            inner.elements = after
            return inner.tapXY(x, y)
        }
    }

    // 点后视觉核对异步化:测试注入 runTest 的 backgroundScope,advanceUntilIdle 后核对确定性跑完。
    private fun executor(screen: ScreenPort, vision: FakeModel, scope: CoroutineScope) = ActionExecutor(
        toolDispatcher(screen, vision = VisionService(vision, JvmTestImageOps), visionScope = scope), OperationGuards(), ConfirmationGate(), gate = {},
        recorder = ActionRecorder(sink))

    private suspend fun observe(e: ActionExecutor) = e.run(Part.ToolCall("c", "phone_observe", "{}"), Origin.Model(1))
    private suspend fun tap(e: ActionExecutor, args: String) = e.run(Part.ToolCall("c", "phone_tap", args), Origin.Model(1))
    private fun content(o: ActionOutcome): JsonObject = (o as ActionOutcome.Ran).content
    private fun JsonObject.s(k: String) = (this[k] as JsonPrimitive).content
    private fun JsonObject.effect(k: String) = ((this["effect"] as? JsonObject)?.get(k) as? JsonPrimitive)?.content

    /** 一屏上占满全屏、名字为空的大容器:几何中心靠不住,点它之前要视觉核查。 */
    private val blankBox = uiEl("@e1", "", listOf(0, 0, 999, 999))
    /** 名字长到像是屏上几段文字拼起来的:同样可疑。 */
    private val stitchedName = "热搜榜单这一块写着好几段文字拼起来的说明文字"
    private val stitchedBox = uiEl("@e2", stitchedName, listOf(0, 0, 999, 999))
    private val smallButton = uiEl("@e3", "设置", listOf(0, 100, 999, 360))

    private fun rows(vararg names: String) = names.mapIndexed { i, n -> uiEl("@e$i", n, listOf(0, 100 + i * 60, 999, 150 + i * 60)) }

    // ---- 第 1 层:点之前的落点核查 ----

    @Test fun blankBoxOnUnusableTreeSkipsPreTapAudit() = runTest {
        // 自绘界面(树读不出内容):点前落点核查是纯阻塞浪费,据此跳过;点后画面变化核对才是防「假成功」的真守卫
        val screen = LocScreen(listOf(blankBox), FRAME_A, FRAME_B)
        val vision = says("""{"at": null}""", "page")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e))
        screen.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"ref":"@e1","snapshotId":"s"}"""))
        assertEquals("点前核查跳过,点击放行", "true", c.s("ok"))
        assertFalse("点前核查没拦", "error" in c)
        assertEquals(listOf("ref @e1"), screen.taps)
        advanceUntilIdle()
    }

    @Test fun bigBoxPointingAtSomethingElseIsNotTapped() = runTest {
        val screen = LocScreen(listOf(stitchedBox), FRAME_A, FRAME_B)
        val vision = says("""{"at": "设置"}""")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e))
        screen.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"ref":"@e2","snapshotId":"s"}"""))
        assertEquals("tap_point_off_target", c.s("error"))
        assertTrue(c.s("message"), "「设置」" in c.s("message") && "「$stitchedName」" in c.s("message"))
        assertTrue(screen.taps.isEmpty())
    }

    @Test fun coordinateTapOnTheWrongThingIsNotTapped() = runTest {
        val screen = LocScreen(listOf(smallButton), FRAME_A, FRAME_B)
        val vision = says("""{"at": "视频"}""")
        val c = content(tap(executor(screen, vision, backgroundScope), """{"x":500,"y":300,"target":"更多热搜"}"""))
        assertEquals("tap_point_off_target", c.s("error"))
        assertTrue(c.s("message"), "「视频」" in c.s("message") && "「更多热搜」" in c.s("message"))
        assertTrue(screen.taps.isEmpty())
    }

    @Test fun coordinateTapOnTheRightThingGoesThrough() = runTest {
        val screen = LocScreen(listOf(smallButton), FRAME_A, FRAME_B)
        val vision = says("""{"at": "更多热搜"}""", "page")
        val e = executor(screen, vision, backgroundScope)
        val c = content(tap(e, """{"x":500,"y":300,"target":"更多热搜"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals(listOf("540,720"), screen.taps)
        assertFalse("点后核对异步,点击结果当下不带 visionCheck", "visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    @Test fun coordinateTapOnATextlessIconGoesThrough() = runTest {
        // 微信首页放大镜搜索图标:无文字,旧版预检当空白拒点(tap_point_blank)让模型卡死;现在报图标就放行
        val screen = LocScreen(listOf(smallButton), FRAME_A, FRAME_B)
        val vision = says("""{"at": null, "icon": "magnifier search icon"}""", "page")
        val e = executor(screen, vision, backgroundScope)
        val c = content(tap(e, """{"x":500,"y":300,"target":"搜索图标"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals(listOf("540,720"), screen.taps)
        assertFalse("visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    @Test fun cannotSeeThePointLetsTheTapThrough() = runTest {
        // 视觉核查是兜底:看不了的时候不拦路
        val screen = LocScreen(listOf(smallButton), FRAME_A, FRAME_B)
        val vision = says(ModelError.Network(), "minor")
        val e = executor(screen, vision, backgroundScope)
        val c = content(tap(e, """{"x":500,"y":300,"target":"更多热搜"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals(listOf("540,720"), screen.taps)
        assertFalse("visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    @Test fun smallNamedElementIsNotAudited() = runTest {
        // 名字短、占屏小:几何中心就是它,不麻烦视觉模型
        val screen = LocScreen(listOf(smallButton), FRAME_A, FRAME_B)
        val vision = says("page")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e))
        screen.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"ref":"@e3","snapshotId":"s"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals(listOf("ref @e3"), screen.taps)
        assertFalse("visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    @Test fun unnamedElementOnUnusableTreeSkipsPreTapAudit() = runTest {
        // 自绘界面(树读不出内容):无名小元素、模型自报了要点什么,点前核查也跳过(纯阻塞浪费);
        // 点后画面变化核对兜底。有名字的树里这类元素仍照查(见 coordinateTapOnTheWrongThingIsNotTapped)。
        val hamburger = uiEl("@e4", "", listOf(886, 582, 999, 636))
        val screen = LocScreen(listOf(hamburger), FRAME_A, FRAME_B)
        val vision = says("""{"at": "菜单"}""", "page")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e))
        screen.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"ref":"@e4","snapshotId":"s","reason":"点击\"更多热搜\"进入热搜榜单页面"}"""))
        assertEquals("点前核查跳过,点击放行", "true", c.s("ok"))
        assertFalse("点前核查没拦", "error" in c)
        assertEquals(listOf("ref @e4"), screen.taps)
        advanceUntilIdle()
    }

    @Test fun unnamedElementPointingAtTheStatedLabelGoesThrough() = runTest {
        // 同一个无名元素,视觉说那一点确实是自报的那个东西:照点
        val link = uiEl("@e4", "", listOf(778, 200, 916, 235))
        val screen = LocScreen(listOf(link), FRAME_A, FRAME_B)
        val vision = says("""{"at": "更多热搜"}""", "page")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e))
        screen.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"ref":"@e4","snapshotId":"s","reason":"点击\"更多热搜\"进入热搜榜单页面"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals(listOf("ref @e4"), screen.taps)
        assertFalse("visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    @Test fun smallUnnamedElementWithoutAStatedLabelIsNotAudited() = runTest {
        // 没说要点什么的无名小元素:无从核对,不麻烦视觉(点后核对照旧兜底)
        val icon = uiEl("@e4", "", listOf(886, 582, 999, 636))
        val screen = LocScreen(listOf(icon), FRAME_A, FRAME_B)
        val vision = says("page")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e))
        screen.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"ref":"@e4","snapshotId":"s","reason":"点右下角那个图标"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals(listOf("ref @e4"), screen.taps)
        advanceUntilIdle()
    }

    @Test fun coordinateTapWithoutATargetIsNotAudited() = runTest {
        // 坐标是模型自己算的、又没说点的是什么:无从核对,只按点后核对兜底
        val screen = LocScreen(listOf(smallButton), FRAME_A, FRAME_B)
        val vision = says("none")
        val e = executor(screen, vision, backgroundScope)
        val c = content(tap(e, """{"x":500,"y":300}"""))
        assertEquals("true", c.s("ok"))
        assertFalse("点后核对异步,当下不带 visionCheck", "visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    // ---- 第 2 层:点了之后的界面变化核查 ----

    @Test fun visionSeesNoChangeWarnsOnTheNextStep() = runTest {
        // 树判明显变了、视觉异步核对说一动不动:点击当下先按树判放行,核对结论晚一步提醒模型可能没点上
        val inner = LocScreen(rows("按钮1", "按钮2", "按钮3", "按钮4", "按钮5", "按钮6"), FRAME_A, FRAME_B)
        val screen = SwapScreen(inner, rows("换过的1", "换过的2", "换过的3", "按钮4", "按钮5", "按钮6"))
        val vision = says("""{"at": "按钮1"}""", "none")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e)); inner.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"x":500,"y":130,"target":"按钮1"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals("done", c.effect("kind"))
        assertFalse("点后核对异步,点击结果当下不带 visionCheck", "visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    @Test fun visionSeesOnlyASmallChangeWarnsOnTheNextStep() = runTest {
        val inner = LocScreen(rows("按钮1", "按钮2", "按钮3", "按钮4", "按钮5", "按钮6"), FRAME_A, FRAME_B)
        val screen = SwapScreen(inner, rows("换过的1", "换过的2", "换过的3", "按钮4", "按钮5", "按钮6"))
        val vision = says("""{"at": "按钮1"}""", "minor")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e)); inner.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"x":500,"y":130,"target":"按钮1"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals("done", c.effect("kind"))
        assertFalse("点后核对异步,点击结果当下不带 visionCheck", "visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    @Test fun visionSeesANewPageSoDoneStands() = runTest {
        val inner = LocScreen(rows("按钮1", "按钮2", "按钮3", "按钮4", "按钮5", "按钮6"), FRAME_A, FRAME_B)
        val screen = SwapScreen(inner, rows("换过的1", "换过的2", "换过的3", "按钮4", "按钮5", "按钮6"))
        val vision = says("""{"at": "按钮1"}""", "page")
        val e = executor(screen, vision, backgroundScope)
        content(observe(e)); inner.show(FRAME_A, FRAME_B)
        val c = content(tap(e, """{"x":500,"y":130,"target":"按钮1"}"""))
        assertEquals("done", c.effect("kind"))
        assertFalse("点后核对异步,点击结果当下不带 visionCheck", "visionCheck" in (c["effect"] as JsonObject))
        advanceUntilIdle()
    }

    @Test fun visionCannotSeeKeepsTheTreeVerdict() = runTest {
        val screen = LocScreen(listOf(smallButton), FRAME_A, FRAME_B)
        val vision = says("""{"at": "设置"}""", ModelError.Server(500))
        val c = content(tap(executor(screen, vision, backgroundScope), """{"x":500,"y":300,"target":"设置"}"""))
        assertEquals("small_change", c.effect("kind"))
        assertFalse("visionCheck" in (c["effect"] as JsonObject))
    }

    @Test fun noScreenshotKeepsTheTreeVerdict() = runTest {
        // 截不到图(受保护、还没开 App):点前不查、点后也不查,照树的判
        val screen = LocScreen(listOf(smallButton), null)
        val vision = FakeModel()
        val c = content(tap(executor(screen, vision, backgroundScope), """{"x":500,"y":300,"target":"设置"}"""))
        assertEquals("true", c.s("ok"))
        assertEquals("small_change", c.effect("kind"))
        assertTrue(vision.requests.isEmpty())
    }
}

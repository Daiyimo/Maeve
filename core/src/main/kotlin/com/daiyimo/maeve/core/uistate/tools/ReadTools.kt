package com.daiyimo.maeve.core.uistate.tools

import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.HINT_PROTECTED
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.int
import com.daiyimo.maeve.core.tool.okObj
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.lastSnapshot
import com.daiyimo.maeve.core.uistate.screen
import com.daiyimo.maeve.core.uistate.screenText
import com.daiyimo.maeve.core.uistate.session
import com.daiyimo.maeve.core.visual.Recall
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 受保护内容的错误码:系统防截屏的页面。 */
private const val PROTECTED_CODE = "protected_content"

/**
 * 读这块屏的三个工具:[Observe] 给元素表并按配置决定带不带图,[ListElements] 只要元素表,
 * [Screenshot] 单独要一张图(主模型能看图才提供)。
 */
internal class Observe(f: ScreenFamily) : ScreenTool(f, "observe") {
    override val verb = Verb.Observe
    override val label = "看屏幕"
    override val risk = Risk.Read
    override val def by lazy { ToolDef(name, describe()) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val vision = ctx.config.visionEnabled
        val adaptive = ctx.config.adaptiveVision
        return ctx.session.observe(
            // 元素优先(adaptiveVision)时树够用就不发图,树不够用时自动补;否则每次读屏都带图
            withImage = vision && !adaptive,
            adaptiveImage = vision && adaptive,
        )
    }
}

/** 只要元素表,不要图:读得快,也不占画面预算。 */
internal class ListElements(f: ScreenFamily) : ScreenTool(f, "list_elements") {
    override val verb = Verb.ListElements
    override val label = "读取界面元素"
    override val risk = Risk.Read
    override val def by lazy { ToolDef(name, describe()) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution =
        if (ctx.session.visualFirst()) ctx.session.visualTreeRead()
            ?: ctx.session.observe(withImage = false, recordFrame = false)
        else ctx.session.observe(withImage = false, recordFrame = false)
}

/** 主模型能看图才提供;受保护的页面(系统防截屏)截不到,按 protected_content 回。 */
internal class Screenshot(f: ScreenFamily) : ScreenTool(f, "screenshot") {
    override val verb = Verb.Screenshot
    override val label = "截图"
    override val risk = Risk.Read
    override fun usable(ctx: ToolContext): Boolean = ctx.config.visionEnabled
    override val def by lazy { ToolDef(name, describe(), mapOf(
        "x" to int("Crop left edge on the latest screen; provide x, y, w and h together.", 0, 999),
        "y" to int("Crop top edge on the latest screen.", 0, 999),
        "w" to int("Crop width on the latest screen.", 1, 999),
        "h" to int("Crop height on the latest screen.", 1, 999),
    )) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val device = ctx.screen
        if (device.isProtected()) return protectedScreenResult()
        val png = (if (ctx.session.visualFirst()) device.screenshotPngStable() else device.screenshotPng()) ?: return fail("screenshot_unavailable", Feedback["screenshot_unavailable"])
        val snap = ctx.lastSnapshot
        val x = a.int("x", 0, 999)
        val y = a.int("y", 0, 999)
        val w = a.int("w", 1, 999)
        val h = a.int("h", 1, 999)
        val cropArgs = listOf(x, y, w, h)
        val image = if (cropArgs.all { it == null }) png else {
            if (cropArgs.any { it == null }) return fail("invalid_arguments", "Provide x, y, w and h together, or omit all four for a full screenshot.")
            val right = x!! + w!!
            val bottom = y!! + h!!
            if (right > 999 || bottom > 999) return fail("invalid_region", "The crop must stay inside the 0-999 screen bounds.")
            val ops = ctx.config.imageOps ?: return fail("crop_unavailable", "This screen cannot be cropped here; use the full screenshot and state any unreadable symbols.")
            try { ops.crop(png, intArrayOf(x, y, right, bottom)) }
            catch (e: Exception) { return fail("crop_failed", "The screenshot region could not be cropped: ${e.message}") }
        }
        val size = ctx.config.imageOps?.size(image) ?: snap?.let { it.width to it.height }
        val shotId = recallFrame(ctx, snap, image)
        return ToolExecution(okObj {
            size?.let { put("width", it.first); put("height", it.second) }
            put("screenshotId", shotId)
            if (cropArgs.all { it != null }) put("crop", buildJsonObject {
                put("x", x!!); put("y", y!!); put("w", w!!); put("h", h!!)
            })
        }, image = image, imageIds = listOf(shotId))
    }

    /** 和读屏一样登记进回看:旧记录被裁掉之后,模型还能凭 screenshotId 找回这一张;返回这一张的编号。 */
    private fun recallFrame(ctx: ToolContext, snap: ScreenSnapshot?, png: ByteArray): Int {
        val shotId = ctx.shots.next()
        ctx.shots.remember(Recall(shotId, ctx.currentStep, snap?.packageName, png, snap?.let(::screenText).orEmpty()))
        return shotId
    }
}

/** 系统防截屏的页面:截不到内容,说清楚并且标记可恢复。 */
private fun protectedScreenResult(): ToolExecution {
    val body = buildJsonObject {
        put("ok", false); put("error", PROTECTED_CODE); put("protected", true)
        put("message", HINT_PROTECTED); put("recoverable", true)
    }
    return ToolExecution(body)
}

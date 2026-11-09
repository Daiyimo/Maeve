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
import com.daiyimo.maeve.core.tool.failCode
import com.daiyimo.maeve.core.tool.ok
import com.daiyimo.maeve.core.tool.str
import com.daiyimo.maeve.core.uistate.Located
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.described
import com.daiyimo.maeve.core.uistate.locatedInfo
import com.daiyimo.maeve.core.uistate.locator
import com.daiyimo.maeve.core.vision.LocateResult
import com.daiyimo.maeve.core.vision.ScreenUnavailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** describe 参数 task 的说明:此刻想在这块屏上做什么。 */
private const val TASK_NOTE = "What you want to do on this screen right now, 1-1000 characters."

/** describe 参数 reason 的说明:为什么光看元素表不够。 */
private const val REASON_NOTE = "Why the element list is not enough, 1-300 characters."

/** find_visually 参数 target 的说明:要找的是个什么。 */
private const val FIND_TARGET_NOTE = "What the element is, e.g. the search icon at the top right. 1-120 characters."

/** find_visually 参数 text 的说明:它在屏上的原文。 */
private const val FIND_TEXT_NOTE = "The element's exact on-screen label, if it has one. 1-60 characters."

/** 看图模型配没配:两个看图工具提供不提供都看这一项。 */
private fun visionConfigured(ctx: ToolContext) = ctx.config.vision != null

/**
 * 让看图模型描述这块屏:要设置里有看图模型(视觉角色)才提供,和主模型能不能看图无关。
 */
internal class Describe(f: ScreenFamily) : ScreenTool(f, "describe") {
    override val verb = Verb.Describe
    override val risk = Risk.Read
    // 只问看图模型、不动界面:用户插话时撤掉,先读那句话
    override val yieldsToSteer = true
    override val def by lazy { ToolDef(name,
        describe(),
        mapOf(
            "task" to str(TASK_NOTE),
            "reason" to str(REASON_NOTE),
        ), listOf("task", "reason")) }

    override fun usable(ctx: ToolContext) = visionConfigured(ctx)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val ask = a.str("task", max = 1000)!!
        a.str("reason", max = 300)
        val caption = try {
            ctx.locator!!.describe(ask)
        } catch (e: ScreenUnavailable) {
            return unavailableResult(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return modelError(e)
        }
        // 看过 describe 之后:这期间的坐标点击按看图得来的点对待
        ctx.described = true
        return ok { put("description", caption) }
    }
}

/** 画面拿不到、不能给视觉模型看时给模型的结果;[ScreenUnavailable.reason] 是 protected_content、no_app_open 等。 */
private fun unavailableResult(e: ScreenUnavailable): ToolExecution = when (e.reason) {
    "protected_content" -> protectedVision()
    "no_app_open" -> failCode("no_app_open")
    else -> fail(e.reason, e.message ?: Feedback["screen_unavailable"])
}

/** 受系统保护的页面(防截屏):视觉模型也看不了,标记可恢复,让模型换别的路。 */
private fun protectedVision() = ToolExecution(buildJsonObject {
    put("ok", false); put("error", "protected_content"); put("protected", true)
    put("message", HINT_PROTECTED); put("recoverable", true)
})

/** 看图模型自己抛的错(不是画面拿不到):按 vision_unavailable 回,带上它的说法。 */
private fun modelError(e: Exception) =
    fail("vision_unavailable", Feedback.format("vision_unavailable", "error" to (e.message ?: e::class.simpleName)))

/** 在屏上按描述找一个点:给了原文时先要它,找到的点登记成 @vN,之后点、打字都要核对画面变没变。 */
internal class FindVisually(f: ScreenFamily) : ScreenTool(f, "find_visually") {
    override val verb = Verb.FindVisually
    override val risk = Risk.Read
    override val yieldsToSteer = true
    override val def by lazy { ToolDef(name,
        describe(),
        mapOf(
            "target" to str(FIND_TARGET_NOTE),
            "text" to str(FIND_TEXT_NOTE),
        ), listOf("target")) }

    override fun usable(ctx: ToolContext) = visionConfigured(ctx)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val looking = a.str("target", max = 120)!!
        val label = a.str("text", max = 60)
        return when (val r = ctx.locator!!.locate(looking, label?.trim())) {
            is LocateResult.NotFound -> notFoundResult(r)
            is LocateResult.Found -> {
                ctx.locatedInfo[r.ref] = Located(looking, label?.trim(), r.seen, r.unverified)
                foundResult(r)
            }
        }
    }
}

/** 没找到:说清为什么,附上视觉模型的解释。 */
private fun notFoundResult(r: LocateResult.NotFound) = ok {
    put("found", false); put("reason", r.reason); put("message", r.message)
}

/** 找到时的结果:点在哪、视觉模型看到什么、有没有佐证。 */
private fun foundResult(hit: LocateResult.Found) = ok {
    put("found", true); put("ref", hit.ref); put("snapshotId", hit.snapshotId)
    put("x", hit.x); put("y", hit.y)
    hit.box?.let { b -> put("box", JsonArray(b.map(::JsonPrimitive))) }
    hit.seen?.let { put("seen", it) }
    put("source", hit.source)
    if (hit.unverified) put("unverified", true)
    hit.note?.let { put("note", it) }
}

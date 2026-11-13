package com.daiyimo.maeve.core.uistate.tools

import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.risk.GateTarget
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.COORD
import com.daiyimo.maeve.core.tool.LOCATED_REF
import com.daiyimo.maeve.core.tool.REF
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.SNAP
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.argStr
import com.daiyimo.maeve.core.tool.int
import com.daiyimo.maeve.core.tool.refExpired
import com.daiyimo.maeve.core.tool.str
import com.daiyimo.maeve.core.uistate.DESCRIBED_ELEMENT
import com.daiyimo.maeve.core.uistate.DESCRIBED_ROLE
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.action
import com.daiyimo.maeve.core.uistate.described
import com.daiyimo.maeve.core.uistate.lastOrSnapshot
import com.daiyimo.maeve.core.uistate.lastSnapshot
import com.daiyimo.maeve.core.uistate.located
import com.daiyimo.maeve.core.uistate.locatedInfo
import com.daiyimo.maeve.core.uistate.screen
import com.daiyimo.maeve.core.uistate.session
import com.daiyimo.maeve.core.uistate.toPx
import com.daiyimo.maeve.core.uistate.withPortRef
import com.daiyimo.maeve.core.vision.LOCATE_SCREEN_CHANGE
import com.daiyimo.maeve.core.vision.hamming
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 坐标点击时 target 参数的说明。 */
private const val TAP_TARGET_NOTE = "Short label of what is being tapped (coordinate taps)."

/** clicks 参数的说明。 */
private const val CLICKS_NOTE = "1 taps once (default), 2 double-taps."

internal class Tap(f: ScreenFamily) : ScreenTool(f, "tap") {
    override val verb = Verb.Tap
    override val label = "点击"
    override val risk = Risk.External
    override val gateTarget = GateTarget.Tap
    override val batchable = true
    override val routable = f.routable
    override val showsScreenAfter = true
    override val reportsEffect = true
    override val logged = true
    override val def by lazy { ToolDef(name,
        describe(),
        mapOf(
            "ref" to str(REF), "snapshotId" to str(SNAP),
            "x" to int(COORD, 0, 999), "y" to int(COORD, 0, 999),
            "target" to str(TAP_TARGET_NOTE),
            DESCRIBED_ELEMENT to str(DESCRIBE_TAP), DESCRIBED_ROLE to str(DESCRIBE_ROLE),
            "clicks" to int(CLICKS_NOTE, 1, 2),
        )) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val ref = a.ref(located = true)
        val x = a.coord("x"); val y = a.coord("y")
        val clicks = a.int("clicks", 1, 2) ?: 1
        val label = a.str("target", max = 80)
        rejectDescribed(a)
        if (ref == null && ctx.described && label == null) throw targetRequired()
        if (ref != null && (x != null || y != null)) throw ArgError("x", "give either ref+snapshotId or x+y, not both")
        if (ref != null) return tapByRef(ctx, ref, clicks == 2)
        if (x == null || y == null) throw ArgError(if (x == null) "x" else "y", if (x == null) "required with y when no ref is given" else "required with x when no ref is given")
        return tapByPoint(ctx, x, y, clicks == 2)
    }

    /**
     * 执行入口已把描述换成编号;走到这里还带着描述说明没经执行入口,不猜,
     * 按参数错拒掉,让模型改回 ref+snapshotId。
     */
    private fun rejectDescribed(a: Args) {
        a.str(DESCRIBED_ELEMENT)?.let { throw ArgError(DESCRIBED_ELEMENT, "could not be resolved to an element here; use ref+snapshotId") }
    }

    /** describe 之后的坐标点击必须说清点的是什么,不然动作清单里不知道点去了哪。 */
    private fun targetRequired(): ArgError =
        ArgError("target", "required for a coordinate tap after ${family.tool("describe")}: say what you are tapping")

    /** 按编号点:@vN 是先核对画面再点,其余经稳定编号翻译后点(可按 clicks 双击)。 */
    private suspend fun tapByRef(ctx: ToolContext, ref: Pair<String, String>, doubleTap: Boolean): ToolExecution =
        if (LOCATED_REF.matches(ref.first)) tapLocated(ref.first, ref.second, doubleTap, ctx)
        else ctx.withPortRef(ref) { r ->
            ctx.action(if (doubleTap) ctx.screen.doubleTapRef(r!!.first, r.second) else ctx.screen.tapRef(r!!.first, r.second))
        }

    /** 按坐标点:像素换算按此刻的一屏(读不到再退回最近一次)。 */
    private suspend fun tapByPoint(ctx: ToolContext, x: Int, y: Int, doubleTap: Boolean): ToolExecution {
        val snap = ctx.lastOrSnapshot()
        return ctx.action(tapAt(ctx, toPx(x, snap.width), toPx(y, snap.height), doubleTap))
    }

    private suspend fun tapAt(ctx: ToolContext, x: Int, y: Int, doubleTap: Boolean) =
        if (doubleTap) ctx.screen.doubleTapXY(x, y) else ctx.screen.tapXY(x, y)

    /** 点 find_visually 找到的点:先重新截图,和找到时的画面差太多(或截不到)就不点。 */
    private suspend fun tapLocated(ref: String, snapshotId: String, doubleTap: Boolean, ctx: ToolContext): ToolExecution {
        val (px, py) = locatedPixel(ctx, ref, snapshotId) ?: return refExpired(Feedback["located_stale"], "located_stale")
        val meta = ctx.locatedInfo[ref]
        val found = meta?.target
        val outcome = ctx.action(tapAt(ctx, px, py, doubleTap))
        if (found == null || outcome.content["ok"] != JsonPrimitive(true)) return outcome
        // 没佐证的点:点下去有没有用只能看效果,提示里先说清楚
        val notes = listOfNotNull(Feedback["located.unverified"].takeIf { meta.unverified }, (outcome.content["hint"] as? JsonPrimitive)?.content)
        val adds = buildMap<String, JsonElement> {
            put("located", JsonPrimitive(found))
            if (meta.unverified) put("unverifiedPoint", JsonPrimitive(true))
            if (notes.isNotEmpty()) put("hint", JsonPrimitive(notes.joinToString(" ")))
        }
        return outcome.with(content = JsonObject(outcome.content + adds))
    }

    override fun logTarget(o: JsonObject, r: ToolExecution, ctx: ToolContext): String {
        val named = refTarget(o, r, ctx.lastSnapshot)
        return named ?: o.argStr("target") ?: "${o["x"]},${o["y"]}"
    }
}

/**
 * find_visually 找到的点([ref] 是 @vN)此刻的像素位置:先重新截图,
 * 和找到时的画面差太多(或截不到)为 null,不往那里点。
 * 换算像素按此刻的一屏(读不到再退回最近一次)。
 */
internal suspend fun locatedPixel(ctx: ToolContext, ref: String, snapshotId: String): Pair<Int, Int>? {
    val (point, seenHash) = ctx.located.get(ref, snapshotId) ?: return null
    val device = ctx.screen
    val frame = if (device.isProtected()) null else device.screenshotPng()
    if (frame == null || hamming(ctx.config.vision!!.ops.aHash(frame), seenHash) > LOCATE_SCREEN_CHANGE) return null
    val snap = ctx.session.peek() ?: ctx.lastOrSnapshot()
    return toPx(point.x, snap.width) to toPx(point.y, snap.height)
}

/** 按编号操作时动作清单里写的对象:看图找到的点写它找的是什么,其余写元素的名字,都没有写编号。 */
internal fun refTarget(o: JsonObject, r: ToolExecution, snap: ScreenSnapshot?): String? = o.argStr("ref")?.let { ref ->
    (r.content["located"] as? JsonPrimitive)?.content
        ?: snap?.elements?.firstOrNull { it.ref == ref }?.name?.takeIf(String::isNotBlank) ?: ref
}

internal const val DESCRIBE_TAP = "Instead of ref+snapshotId: the element's visible text or accessible name, as shown on the screen. It must match exactly one element on the screen right now."
internal const val DESCRIBE_ROLE = "Optional with element: the element's role as shown in the element list (e.g. button, link, textbox), to tell same-named elements apart."

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
import com.daiyimo.maeve.core.tool.bool
import com.daiyimo.maeve.core.tool.int
import com.daiyimo.maeve.core.tool.okObj
import com.daiyimo.maeve.core.tool.refExpired
import com.daiyimo.maeve.core.tool.str
import com.daiyimo.maeve.core.tool.switchHint
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.DESCRIBED_ELEMENT
import com.daiyimo.maeve.core.uistate.DESCRIBED_ROLE
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.Verification
import com.daiyimo.maeve.core.uistate.action
import com.daiyimo.maeve.core.uistate.lastOrSnapshot
import com.daiyimo.maeve.core.uistate.lastSnapshot
import com.daiyimo.maeve.core.uistate.locatedInfo
import com.daiyimo.maeve.core.uistate.screen
import com.daiyimo.maeve.core.uistate.toPx
import com.daiyimo.maeve.core.uistate.withPortRef
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 回车键的键值;输入后按它,有可能是发送。 */
private const val ENTER_KEY = 66

/** 打字参数 text 的说明。 */
private const val TEXT_NOTE = "Text to enter, 1-10000 characters."

/** 打字参数 pressEnter 的说明。 */
private const val PRESS_ENTER_NOTE = "Press Enter after typing (default false)."

/** 按描述指输入框时 element 参数的说明。 */
private const val DESCRIBED_FIELD_NOTE = "Instead of ref+snapshotId: the input field's label, accessible name or placeholder, as shown on the screen. It must match exactly one input field on the screen right now."

/** 输入、清空另收的参数:一个点的坐标(ref 也可以是看图找到的 @vN),树读不到输入框时直接往那一点打字。 */
private val POINT_PARAMS = mapOf(
    "x" to int("Instead of a ref: the input box's point (e.g. a visibleText center), tapped before typing. $COORD", 0, 999),
    "y" to int(COORD, 0, 999),
)

/** 往哪儿打字:树里的元素(编号),或一个点(像素;[located] 是看图找到的 @vN,不是为 null)。 */
private sealed interface FieldAim {
    data class Ref(val ref: Pair<String, String>?) : FieldAim
    data class Point(val x: Int, val y: Int, val located: String?) : FieldAim
}

/** 输入(带回车时可能是发送,要按目标判不可逆)。 */
internal class TypeText(f: ScreenFamily) : ScreenTool(f, "type_text") {
    override val verb = Verb.TypeText
    override val label = "输入文字"
    override val risk = Risk.External
    override val gateTarget = GateTarget.Enter
    override val batchable = true
    override val routable = f.routable
    override val showsScreenAfter = true
    override val reportsEffect = true
    override val logged = true
    override val def by lazy { ToolDef(name,
        describe(),
        mapOf(
            "text" to buildJsonObject { put("type", "string"); put("description", TEXT_NOTE); put("minLength", 1); put("maxLength", 10000) },
            "pressEnter" to bool(PRESS_ENTER_NOTE),
            "ref" to str(REF), "snapshotId" to str(SNAP),
            DESCRIBED_ELEMENT to str(DESCRIBED_FIELD_NOTE),
            DESCRIBED_ROLE to str(DESCRIBE_ROLE),
        ) + POINT_PARAMS, listOf("text")) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        if (a.str(DESCRIBED_ELEMENT) != null) throw ArgError(DESCRIBED_ELEMENT, "could not be resolved to an input field here; use ref+snapshotId")
        val input = a.str("text", max = 10000)!!
        val withEnter = a.bool("pressEnter", false)
        val (field, rejected) = resolveTarget(a, ctx)
        rejected?.let { return it }
        return when (field) {
            is FieldAim.Point -> annotateLocated(ctx, field, typeInto(ctx, input, withEnter) { ctx.screen.typeAt(field.x, field.y, input) })
            else -> ctx.withPortRef((field as FieldAim.Ref).ref) { ref -> typeInto(ctx, input, withEnter) { ctx.screen.typeText(ref?.first, ref?.second, input) } }
        }
    }

    private suspend fun typeInto(ctx: ToolContext, input: String, withEnter: Boolean, type: suspend () -> ActionResult): ToolExecution {
        val typed = type()
        if (typed !is ActionResult.Ok) return ctx.action(typed)
        val readback = typed.verification
        if (readback == Verification.MISMATCH) return notApplied(readback)
        val movedTo = typed.appSwitched
        // 读不回来就不按回车:可能把没打进去的内容提交出去;前台被切走时回车会打到别的 App 上
        val sendEnter = withEnter && readback != Verification.UNKNOWN && movedTo == null
        if (sendEnter) {
            val k = ctx.screen.pressKey(ENTER_KEY)
            if (k !is ActionResult.Ok) return ctx.action(k)
        }
        return ToolExecution(typedResult(typed, input, readback, sendEnter, withEnter), changed = typed.changed)
    }

    override fun logTarget(o: JsonObject, r: ToolExecution, ctx: ToolContext) = refTarget(o, r, ctx.lastSnapshot) ?: pointTarget(o)

    // 只有端口明确说不是密码框才写出输入的字
    override fun logTyped(o: JsonObject, r: ToolExecution) =
        o.argStr("text")?.takeIf { r.content["secret"] == JsonPrimitive(false) }?.let { "「${it.take(40)}」" }
}

internal class ClearText(f: ScreenFamily) : ScreenTool(f, "clear_text") {
    override val verb = Verb.ClearText
    override val label = "清空输入框"
    override val risk = Risk.Write
    override val batchable = true
    override val routable = f.routable
    override val showsScreenAfter = true
    override val reportsEffect = true
    override val logged = true
    override val def by lazy { ToolDef(name, describe(),
        mapOf("ref" to str(REF), "snapshotId" to str(SNAP)) + POINT_PARAMS) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val (field, rejected) = resolveTarget(a, ctx)
        rejected?.let { return it }
        return when (field) {
            is FieldAim.Point -> annotateLocated(ctx, field, ctx.action(ctx.screen.clearAt(field.x, field.y)))
            else -> ctx.withPortRef((field as FieldAim.Ref).ref) { r -> ctx.action(ctx.screen.clearText(r?.first, r?.second)) }
        }
    }

    override fun logTarget(o: JsonObject, r: ToolExecution, ctx: ToolContext) = refTarget(o, r, ctx.lastSnapshot) ?: pointTarget(o)
}

/**
 * 按参数定往哪儿打字:没给点时是编号(或焦点所在的框);给了坐标或 @vN 时是那一点,@vN 要和找到时的画面对得上。
 * 不能用的 @vN 返回给模型的失败结果。
 */
private suspend fun resolveTarget(a: Args, ctx: ToolContext): Pair<FieldAim?, ToolExecution?> {
    val ref = a.ref(located = true)
    val x = a.coord("x")
    val y = a.coord("y")
    if (ref != null && (x != null || y != null)) throw ArgError("x", "give either ref+snapshotId or x+y, not both")
    if ((x == null) != (y == null)) throw ArgError(if (x == null) "x" else "y", "x and y go together")
    if (ref != null && LOCATED_REF.matches(ref.first)) {
        val point = locatedPixel(ctx, ref.first, ref.second) ?: return null to refExpired(Feedback["located_stale"], "located_stale")
        return FieldAim.Point(point.first, point.second, ref.first) to null
    }
    if (x != null && y != null) {
        val snap = ctx.lastOrSnapshot()
        return FieldAim.Point(toPx(x, snap.width), toPx(y, snap.height), null) to null
    }
    return FieldAim.Ref(ref) to null
}

/** 往看图找到的点上打字、清空时结果里写上找的是什么;没佐证的点标出来。 */
private fun annotateLocated(ctx: ToolContext, field: FieldAim?, r: ToolExecution): ToolExecution {
    val locatedRef = (field as? FieldAim.Point)?.located ?: return r
    val meta = ctx.locatedInfo[locatedRef] ?: return r
    val adds = listOfNotNull(meta.target?.let { "located" to JsonPrimitive(it) }, ("unverifiedPoint" to JsonPrimitive(true)).takeIf { meta.unverified })
    return if (adds.isEmpty()) r else r.with(content = JsonObject(r.content + adds))
}

/** 读回来和打进去的不一致:没算打进去,标记可恢复,让模型换个办法。 */
private fun notApplied(v: Verification) = ToolExecution(buildJsonObject {
    put("ok", false); put("error", "action_not_applied"); put("verification", v.wire)
    put("message", Feedback["type.not_applied"])
    put("recoverable", true)
})

/** 输入成功的结果:打了多少字、按没按回车、是不是密码框,外加该提示的话。 */
private fun typedResult(typed: ActionResult.Ok, input: String, readback: Verification?, sendEnter: Boolean, withEnter: Boolean) = okObj {
    put("typed", input.length); put("pressedEnter", sendEnter)
    put("secret", typed.secret)
    if (typed.relocated) put("relocated", true)
    typed.relocatedTo?.takeIf { typed.relocated }?.let { put("relocatedTo", it) }
    readback?.let { put("verification", it.wire) }
    if (withEnter && !sendEnter) put("enterSkipped", true)
    typed.appSwitched?.let { put("app_switched", it) }
    val hints = typeHints(typed, readback, withEnter)
    if (hints.isNotEmpty()) put("hint", hints.joinToString(" "))
}

/** 输入结果的提示:端口的补充、读不回来(没佐证)时的一句、被切到别的 App 时的一句。 */
private fun typeHints(typed: ActionResult.Ok, readback: Verification?, withEnter: Boolean): List<String> = listOfNotNull(
    typed.hint,
    (if (withEnter) Feedback.format("type.unverified_enter", "unverified" to Feedback["type.unverified"]) else Feedback["type.unverified"])
        .takeIf { readback == Verification.UNKNOWN },
    typed.appSwitched?.let { switchHint(it, typed.switchedByTarget).let { h -> if (withEnter) Feedback.format("type.switched_enter", "switch" to h) else h } },
)

/** 往坐标点上打字、清空时动作清单里写的对象。 */
private fun pointTarget(o: JsonObject): String? = o["x"]?.let { x -> "${x},${o["y"]}" }

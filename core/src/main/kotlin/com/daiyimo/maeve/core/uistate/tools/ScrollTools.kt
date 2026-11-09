package com.daiyimo.maeve.core.uistate.tools

import com.daiyimo.maeve.core.risk.GateTarget
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.COORD
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.argStr
import com.daiyimo.maeve.core.tool.int
import com.daiyimo.maeve.core.tool.str
import com.daiyimo.maeve.core.uistate.DEFAULT_SWIPE_MS
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.action
import com.daiyimo.maeve.core.uistate.lastOrSnapshot
import com.daiyimo.maeve.core.uistate.screen
import com.daiyimo.maeve.core.uistate.toPx
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** direction 允许的取值,和 schema 里的 enum 一致。 */
private val DIRECTIONS = listOf("up", "down", "left", "right")

/** 纵向的两个方向:手机这一套上先走无障碍滚动。 */
private val VERTICAL = setOf("up", "down")

/** 格数的下限。 */
private const val AMOUNT_MIN = 1

/** 格数的上限。 */
private const val AMOUNT_MAX = 20

/** 不给 amount 时的格数。 */
private const val DEFAULT_AMOUNT = 3

/** scroll 参数 direction 的说明。 */
private const val DIRECTION_NOTE = "Where the content you want to see is: down = further down the page, up = further up."

/** scroll 参数 amount 的说明。 */
private const val AMOUNT_NOTE = "How far, 1-20 (default 3)."

/** swipe 参数 durationMs 的说明。 */
private const val DURATION_NOTE = "Gesture duration 50-5000 ms (default 400)."

/** 滑动:横滑可能是「滑动删除」,要按目标判不可逆 */
internal class Scroll(f: ScreenFamily) : ScreenTool(f, "scroll") {
    override val verb = Verb.Scroll
    override val label = "滚动"
    override val risk = Risk.External
    override val gateTarget = GateTarget.Swipe
    override val batchable = true
    override val routable = f.routable
    override val showsScreenAfter = true
    override val reportsEffect = true
    override val logged = true
    override val def by lazy { ToolDef(name,
        describe(),
        mapOf(
            "direction" to str(DIRECTION_NOTE, DIRECTIONS),
            "amount" to int(AMOUNT_NOTE, AMOUNT_MIN, AMOUNT_MAX),
            "x" to int(COORD, 0, 999), "y" to int(COORD, 0, 999),
        ), listOf("direction")) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val direction = a.str("direction")!!
        if (direction !in DIRECTIONS) throw ArgError("direction", "must be up, down, left or right")
        val amount = a.int("amount", AMOUNT_MIN, AMOUNT_MAX) ?: DEFAULT_AMOUNT
        val x = a.coord("x"); val y = a.coord("y")
        if ((x == null) != (y == null)) throw ArgError("x", "give both x and y or neither")
        val result = performScroll(ctx, direction, amount, x, y)
        if (result.content["ok"] != JsonPrimitive(true)) return result
        return withDirection(result, direction)
    }

    /**
     * 发出这次滚动:手机默认的纵向滚动(不给点、3 格)先让无障碍控件执行,端口找不到可滚控件时它自己退到手势;
     * 其余按方向和格数算一段滑动手势,落点用归一化坐标、换算像素按此刻的一屏。
     */
    private suspend fun performScroll(ctx: ToolContext, direction: String, amount: Int, x: Int?, y: Int?): ToolExecution {
        if (family.id == "phone" && direction in VERTICAL && x == null && amount == DEFAULT_AMOUNT)
            return ctx.action(ctx.screen.scroll(null, null, direction == "down"))
        val midX = x ?: 500; val midY = y ?: 500
        // direction 是内容移动方向,手指反向滑动
        val travel = min(650, amount * 100)
        val dx = when (direction) { "left" -> travel; "right" -> -travel; else -> 0 }
        val dy = when (direction) { "up" -> travel; "down" -> -travel; else -> 0 }
        return swipeBetween(ctx, bound(midX - dx / 2.0), bound(midY - dy / 2.0), bound(midX + dx / 2.0), bound(midY + dy / 2.0))
    }

    override fun logTarget(o: JsonObject, r: ToolExecution, ctx: ToolContext): String? {
        return o.argStr("direction")
    }
}

/** 按起点终点发一次滑动手势;时长不给时用默认值。 */
internal class Swipe(f: ScreenFamily) : ScreenTool(f, "swipe") {
    override val verb = Verb.Swipe
    override val label = "滑动"
    override val risk = Risk.External
    override val gateTarget = GateTarget.Swipe
    override val batchable = true
    override val routable = f.routable
    override val showsScreenAfter = true
    override val reportsEffect = true
    override val logged = true
    override val def by lazy { ToolDef(name,
        describe(),
        mapOf(
            "startX" to int(COORD, 0, 999), "startY" to int(COORD, 0, 999),
            "endX" to int(COORD, 0, 999), "endY" to int(COORD, 0, 999),
            "durationMs" to int(DURATION_NOTE, 50, 5000),
        ), listOf("startX", "startY", "endX", "endY")) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val fromX = a.coord("startX")!!; val fromY = a.coord("startY")!!
        val toX = a.coord("endX")!!; val toY = a.coord("endY")!!
        val ms = a.int("durationMs", 50, 5000)?.toLong() ?: DEFAULT_SWIPE_MS
        return swipeBetween(ctx, fromX, fromY, toX, toY, ms)
    }
}

/** 结果里补上内容方向和手指方向;没动(多半到头了)由分发器按实际效果判,这里只标注。 */
private fun withDirection(r: ToolExecution, direction: String): ToolExecution {
    val finger = when (direction) { "up" -> "down"; "down" -> "up"; "left" -> "right"; else -> "left" }
    val content = r.content + mapOf("direction" to JsonPrimitive(direction), "fingerMoved" to JsonPrimitive(finger))
    return ToolExecution(JsonObject(content), changed = r.changed)
}

/** 手势落点夹在屏幕中段:别从边缘起手,系统边缘手势会抢。 */
private fun bound(v: Double) = max(50, min(949, v.roundToInt()))

/** 按 0–999 归一化坐标发一次滑动手势;像素换算按此刻的一屏。 */
private suspend fun swipeBetween(ctx: ToolContext, fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Long = DEFAULT_SWIPE_MS): ToolExecution {
    val s = ctx.lastOrSnapshot()
    return ctx.action(ctx.screen.swipe(toPx(fromX, s.width), toPx(fromY, s.height), toPx(toX, s.width), toPx(toY, s.height), durationMs))
}

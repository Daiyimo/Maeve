package com.daiyimo.maeve.core.cap.phone

import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.COORD
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.int
import com.daiyimo.maeve.core.uistate.action
import com.daiyimo.maeve.core.uistate.lastOrSnapshot
import com.daiyimo.maeve.core.uistate.screen
import com.daiyimo.maeve.core.uistate.toPx

/** 原生单指长按:同一点保持 down 到 durationMs 后再 up,不是连续 click。 */
internal object PhoneLongPress : PhoneTool {
    override val verb = Verb.PhoneGesture; override val label = "长按"; override val risk = Risk.Write
    override val batchable = true; override val showsScreenAfter = true
    override val reportsEffect = true; override val logged = true
    override val def = ToolDef("phone_long_press", Prompts.tool("phone_long_press"), mapOf(
        "x" to int(COORD, 0, 999), "y" to int(COORD, 0, 999),
        "durationMs" to int("Hold duration, 100-5000 ms (default 600).", 100, 5000),
    ), listOf("x", "y"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val x = a.int("x", 0, 999) ?: throw ArgError("x", "required")
        val y = a.int("y", 0, 999) ?: throw ArgError("y", "required")
        val screen = ctx.lastOrSnapshot()
        val point = toPx(x, screen.width) to toPx(y, screen.height)
        return ctx.action(ctx.screen.gesture(listOf(listOf(point, point)), (a.int("durationMs", 100, 5000) ?: 600).toLong()))
    }
}

/** 原生慢拖:down → 连续 move → 静止后 up;默认 800ms 走 resting-drag 时间线。 */
internal object PhoneDrag : PhoneTool {
    override val verb = Verb.PhoneGesture; override val label = "拖动"; override val risk = Risk.Write
    override val batchable = true; override val showsScreenAfter = true
    override val reportsEffect = true; override val logged = true
    override val def = ToolDef("phone_drag", Prompts.tool("phone_drag"), mapOf(
        "startX" to int(COORD, 0, 999), "startY" to int(COORD, 0, 999),
        "endX" to int(COORD, 0, 999), "endY" to int(COORD, 0, 999),
        "durationMs" to int("Total drag duration including hold/rest, 100-3000 ms (default 800).", 100, 3000),
        "holdMs" to int("Hold at the start before moving, 0-2000 ms (default 0).", 0, 2000),
        "restMs" to int("Rest at the end before lifting, 0-2000 ms (default 0).", 0, 2000),
    ), listOf("startX", "startY", "endX", "endY"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val sx = a.int("startX", 0, 999) ?: throw ArgError("startX", "required")
        val sy = a.int("startY", 0, 999) ?: throw ArgError("startY", "required")
        val ex = a.int("endX", 0, 999) ?: throw ArgError("endX", "required")
        val ey = a.int("endY", 0, 999) ?: throw ArgError("endY", "required")
        if (sx == ex && sy == ey) throw ArgError("endX", "drag end must differ from start; use phone_long_press for a hold")
        val screen = ctx.lastOrSnapshot()
        val start = toPx(sx, screen.width) to toPx(sy, screen.height)
        val end = toPx(ex, screen.width) to toPx(ey, screen.height)
        val middle = (start.first + end.first) / 2 to (start.second + end.second) / 2
        val duration = (a.int("durationMs", 100, 3000) ?: 800).toLong()
        val hold = (a.int("holdMs", 0, 2000) ?: 0).toLong()
        val rest = (a.int("restMs", 0, 2000) ?: 0).toLong()
        if (hold + rest >= duration) throw ArgError("durationMs", "holdMs + restMs must leave time for movement")
        return ctx.action(ctx.screen.gesture(listOf(listOf(start, middle, end)), duration, hold, rest))
    }
}

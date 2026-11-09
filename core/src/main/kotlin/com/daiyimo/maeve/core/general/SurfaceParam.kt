package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.argStr
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.JsonObject
import com.daiyimo.maeve.core.uistate.focus

/** surface 参数的名字:通用工具靠它让模型说清「说的是哪块屏」。 */
private const val SURFACE = "surface"

/**
 * 说的是哪块屏的通用工具(请求确认、交给用户、报障碍都实现它)。
 *
 * 只有一块屏时用不到这个参数;本任务有两块以上的屏,发给模型的定义才多一个 surface 参数让模型指定是哪一块,
 * 不给时指的是最近一次屏幕工具操作的那块(由分发器记住)。[aimAt] 让分发器在调用前就知道这次指向哪块屏,
 * [checkSurface] 在工具执行时核对模型给的 surface 确实是本任务的一块屏,不是就当参数错拒掉。
 */
internal interface SurfaceAware : AgentTool {
    /** 两块以上的屏才要模型说清是哪一块;只有一块(或没有)时参数多余,直接沿用基础定义。 */
    override fun def(ctx: ToolContext): ToolDef {
        val screenIds = ctx.focus.surfaces.map { it.id }
        if (screenIds.size < 2) return def
        val base = def
        return ToolDef(base.name, base.description, base.props + (SURFACE to str(SURFACE_NOTE, screenIds)), base.required)
    }

    /** 这次调用指向哪块屏:模型给了 surface 就按它,没给为 null(分发器沿用当前那块)。 */
    override fun aimAt(o: JsonObject): String? = o.argStr(SURFACE)

    /**
     * 执行前核对:模型给了 surface 时,它必须是本任务的一块屏;没给(退回当前屏)不拦。
     */
    fun checkSurface(a: Args, ctx: ToolContext) {
        val requested = a.str(SURFACE) ?: return
        if (ctx.focus[requested] == null) {
            val known = ctx.focus.surfaces.joinToString("|") { it.id }
            throw ArgError(SURFACE, "must be one of $known")
        }
    }

    companion object {
        const val SURFACE_NOTE = "Which screen this is about. Default: the screen your latest screen tool acted on."
    }
}

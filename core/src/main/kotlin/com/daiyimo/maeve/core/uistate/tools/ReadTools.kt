package com.daiyimo.maeve.core.uistate.tools

import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.session

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

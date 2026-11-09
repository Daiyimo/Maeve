package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.ok
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 让任务按节奏停一下再读屏(等加载、等动画)。可等的秒数有上下限:少于 0.2 秒等于没等,
 * 超过 10 秒容易让循环空等卡住,越界当参数错。用户插话一到就撤掉([AgentTool.yieldsToSteer]):
 * 撤掉后模型先读那句提示、还要等再调一次。能并进一次多步(batchable),也记进动作清单(logged)。
 */
internal object Wait : AgentTool {
    /** 一次可等的秒数上下限;schema 的范围和这里的校验共用,避免两处写歪。 */
    private const val MIN_SECONDS = 0.2
    private const val MAX_SECONDS = 10

    override val verb = Verb.Wait
    override val batchable = true
    override val label = "等待"
    override val risk = Risk.Read
    override val logged = true
    override val yieldsToSteer = true

    override val def = ToolDef("wait", Prompts.tool("wait"),
        mapOf("seconds" to buildJsonObject {
            put("type", "number")
            put("description", "0.2-10 seconds.")
            put("minimum", MIN_SECONDS)
            put("maximum", MAX_SECONDS)
        }),
        listOf("seconds"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val seconds = a.number("seconds")!!
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) throw ArgError("seconds", "must be between 0.2 and 10")
        // 毫秒级停顿:截断到整数毫秒,比这更短的等待交给循环节奏兜底
        delay((seconds * 1000).toLong())
        // 结果回的是模型要求的秒数,不是实测耗时;被插话撤掉时压根不产生结果
        return ok { put("waitedSeconds", seconds) }
    }
}

package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.TaskPlan
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.int
import com.daiyimo.maeve.core.tool.ok
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 模型显式报告 1–8 步计划与当前步骤;状态由代码校验并驱动界面,不从工具调用数猜。 */
internal object ReportPlan : AgentTool {
    override val verb = Verb.ReportPlan
    override val label = "更新计划"
    override val risk = Risk.Read
    override val def = ToolDef("report_plan", Prompts.tool("report_plan"), mapOf(
        "title" to buildJsonObject {
            put("type", "string"); put("minLength", 2); put("maxLength", 60)
            put("description", "Short plan title, 2-60 characters. Send with the first plan; later updates may omit it.")
        },
        "steps" to buildJsonObject {
            put("type", "array"); put("minItems", 1); put("maxItems", 8)
            put("description", "Ordered plan steps, each 2-240 characters.")
            put("items", buildJsonObject { put("type", "string"); put("minLength", 2); put("maxLength", 240) })
        },
        "currentStep" to int("Current 1-based step; must be within steps.", 1, 8),
    ), listOf("steps", "currentStep"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val title = a.str("title", 2, 60) ?: ctx.taskPlan?.title ?: throw ArgError("title", "required for the first plan")
        val steps = a.strList("steps", 1, 8, itemMax = 240)!!
        val current = a.int("currentStep", 1, 8)!!
        val plan = TaskPlan.of(title, steps, current) ?: throw ArgError("currentStep", "plan must have 1-8 valid steps and a current step within them")
        ctx.taskPlan = plan
        return ok {
            put("title", plan.title); put("currentStep", plan.currentStep)
            put("steps", buildJsonArray { plan.steps.forEach { add(JsonPrimitive(it.text)) } })
        }
    }
}

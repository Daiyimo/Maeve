package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.child.ChildPhase
import com.daiyimo.maeve.core.child.ChildResult
import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.Trust
import com.daiyimo.maeve.core.observation.Untrusted
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.run.Attendance
import com.daiyimo.maeve.core.run.RunId
import com.daiyimo.maeve.core.run.RunOrigin
import com.daiyimo.maeve.core.run.RunProvenance
import com.daiyimo.maeve.core.run.RunSpec
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.int
import com.daiyimo.maeve.core.tool.str
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 派一个子 agent 做有界的研究(只用网页和沙箱读,不碰手机屏),把结果文字带回来。
 * 是离开屏幕的动作(offScreen):不过锁屏关卡、不进屏幕守卫;结果按字符上限截短,超了标出原长。
 */
internal object SpawnAgent : AgentTool {
    /** 子任务默认的步数、步数上限。 */
    const val DEFAULT_STEPS = 15
    const val MAX_STEPS = 40
    /** 子任务结果默认留多少字、最多留多少字、最少留多少字。 */
    const val DEFAULT_RESULT_CHARS = 4_000
    const val MAX_RESULT_CHARS = 8_000
    const val MIN_RESULT_CHARS = 500
    private const val MAX_STEPS_NOTE = "Optional step budget for the child, 1-$MAX_STEPS (default $DEFAULT_STEPS); larger values are lowered to $MAX_STEPS."
    private const val MAX_RESULT_CHARS_NOTE = "Optional length limit of the returned result in characters, $MIN_RESULT_CHARS-$MAX_RESULT_CHARS (default $DEFAULT_RESULT_CHARS); values outside are moved into range."

    override val verb = Verb.SpawnAgent
    override val risk = Risk.Write
    override val offScreen = true

    override val def = ToolDef("spawn_agent", Prompts.tool("spawn_agent",
        "steps" to DEFAULT_STEPS, "maxSteps" to MAX_STEPS, "chars" to DEFAULT_RESULT_CHARS, "maxChars" to MAX_RESULT_CHARS), mapOf(
        "goal" to str("A bounded research question for the child agent, using web and sandbox reads only."),
        "format" to str("Optional output format.", listOf("text", "json")),
        "maxSteps" to int(MAX_STEPS_NOTE, 1),
        "maxResultChars" to int(MAX_RESULT_CHARS_NOTE, 1),
    ), listOf("goal"))

    /** 要能派子任务(有子任务宿主),也知道自己是哪个任务派出去的(有运行规格)才提供。 */
    override fun available(ctx: ToolContext) = ctx.config.childHost != null && ctx.config.runSpec != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val parentRun = ctx.config.runSpec ?: error("no parent RunSpec")
        val host = ctx.config.childHost ?: error("no child host")
        val question = a.str("goal", max = 4000)!!
        val format = a.str("format")
        // 只校验是整数;超出范围的拉回范围内,不当错误(子任务照样能派)
        val budget = (a.int("maxSteps", Int.MIN_VALUE, Int.MAX_VALUE) ?: DEFAULT_STEPS).coerceIn(1, MAX_STEPS)
        val charLimit = (a.int("maxResultChars", Int.MIN_VALUE, Int.MAX_VALUE) ?: DEFAULT_RESULT_CHARS).coerceIn(MIN_RESULT_CHARS, MAX_RESULT_CHARS)
        return report(host.spawn(childSpec(parentRun, question, budget), format), charLimit, budget)
    }

    /** 子任务的运行规格:挂在本任务线程下,无人值守、只读,只能用网页和沙箱,步数按给的预算。 */
    private fun childSpec(parentRun: RunSpec, question: String, budget: Int) = RunSpec(
        RunId(UUID.randomUUID().toString()), question, parentRun.threadId,
        provenance = RunProvenance(RunOrigin.Child, Attendance.Unattended),
        capabilities = setOf("web", "sandbox"), readOnly = true, parent = parentRun.id, stepBudget = budget,
    )

    /** 把子任务结果交给模型:截到上限、超了标出原长;原文作为不可信观察交核查。 */
    private fun report(child: ChildResult, charLimit: Int, budget: Int): ToolExecution {
        val body = child.text.take(charLimit)
        val content = buildJsonObject {
            put("ok", child.phase == ChildPhase.Completed)
            put("childRunId", child.id.value)
            put("status", child.phase.name.lowercase())
            put("maxSteps", budget)
            put("result", Untrusted.wrap(body))
            if (child.text.length > charLimit) {
                put("truncated", true)
                put("fullLength", child.text.length)
            }
        }
        return ToolExecution(content, observations = listOf(Observation(ObsKind.Reply, body, source = "child", trust = Trust.Untrusted)))
    }
}

package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.action.ActionExecutor
import com.daiyimo.maeve.core.action.ActionOutcome
import com.daiyimo.maeve.core.action.ExplicitLaunchOrigin
import com.daiyimo.maeve.core.action.execution
import com.daiyimo.maeve.core.model.Author
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.Role
import com.daiyimo.maeve.core.observation.tag
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.tool.Verb
import kotlinx.serialization.json.JsonPrimitive

/** Opens the explicitly requested host app. The model still searches and uses the mini program. */
internal object ExplicitWeChatLaunch {
    private const val PACKAGE = "com.tencent.mm"
    private val instruction = Regex(
        """(?:(?:请帮我|帮我|请)\s*)?(?:去微信\s*|打开微信[\s，,]*(?:然后\s*)?)(?:搜索|搜|查找|找|进入|打开)\s*[\p{L}\p{N} ·《》「」_-]{1,32}小程序(?:[\s，,。；;]+[^\r\n]{0,160})?""",
    )
    private val ambiguous = Regex(
        """[？?\r\n]|(?:不要|先别|不用|不必|不需要|不打开|不能|别执行|取消|如何|怎么|能不能|可以吗|如果|假如|例如|比如|演示|示例|讨论|解释|方案|流程|步骤|教程|以后|下次|再说)""",
    )

    fun call(task: String, installed: Map<String, String>, launchTool: String?): Part.ToolCall? {
        val request = task.trim()
        if (launchTool == null || PACKAGE !in installed || !instruction.matches(request) || ambiguous.containsMatchIn(request)) return null
        return Part.ToolCall("explicit_wechat_launch", launchTool, """{"package":"$PACKAGE","reason":"按要求打开微信，准备搜索小程序"}""")
    }

    suspend fun execute(call: Part.ToolCall, executor: ActionExecutor, tools: ToolDispatcher,
        history: MutableList<Message>): ActionOutcome {
        val outcome = executor.run(call, ExplicitLaunchOrigin)
        if (outcome is ActionOutcome.Cancelled) return outcome
        val exec = outcome.execution()
        val content = (outcome as? ActionOutcome.Ran)?.content ?: exec.content
        history += Message(Role.Assistant, listOf(call), Author.Maeve)
        history += Message(Role.Tool, listOf(Part.ToolResult(call.id, content.toString(),
            isError = content["ok"] != JsonPrimitive(true), exec.observations.map { it.tag })), Author.Maeve)
        if (exec.images.isNotEmpty()) {
            history += Message(Role.User, listOf(Part.Text(NoticePolicy.imageNote(Verb.LaunchApp, false))) +
                exec.images.mapIndexed { i, image -> tools.modelImage(image, exec.imageIds.getOrNull(i)) }, Author.Maeve)
        }
        return outcome
    }
}

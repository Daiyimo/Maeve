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

/** 明确点名已安装 App 时先本地拉起,模型继续完成后面的搜索、导航和读取。 */
internal object ExplicitAppLaunch {
    internal data class Request(val appName: String, val call: Part.ToolCall)

    private const val WECHAT_PACKAGE = "com.tencent.mm"
    private val weChatInstruction = Regex(
        """(?:(?:请帮我|帮我|请)\s*)?(?:去微信\s*|打开微信[\s，,]*(?:然后\s*)?)(?:搜索|搜|查找|找|进入|打开)\s*[\p{L}\p{N} ·《》「」_-]{1,32}小程序(?:[\s，,。；;]+[^\r\n]{0,160})?""",
    )
    private val openInstruction = Regex(
        """^(?:(?:现在|立刻|马上|请帮我|帮我|请)\s*)*(?:打开|启动|进入)\s*(?:手机\s*)?(?:App\s*)?([\p{L}\p{N}·《》「」“”‘’"'_ -]{1,32})""",
        RegexOption.IGNORE_CASE,
    )
    private val genericAppWords = setOf("应用", "游戏", "软件", "程序")
    private val appPrefixes = setOf("系统", "手机", "腾讯")
    private val shorthandBlockedSuffixes = setOf(
        "读书", "精选", "极速版", "简洁版", "国际版", "lite", "pro", "测试",
        "音乐", "视频", "地图", "浏览器", "输入法", "管家", "卫士", "助手",
    )
    private val ambiguous = Regex(
        """[？?\r\n]|(?:不要|先别|不用|不必|不需要|不打开|不能|别执行|取消|如何|怎么|能否|是否|为什么|可不可以|行吗|会怎样|有什么用|好不好|同意吗|能不能|可以吗|如果|假如|例如|比如|演示|示例|讨论|解释|方案|流程|步骤|教程|以后|下次|再说)""",
    )

    fun call(task: String, installed: Map<String, String>, launchTool: String?): Request? {
        val request = task.trim()
        if (launchTool == null || request.length > 500 || ambiguous.containsMatchIn(request)) return null
        if (WECHAT_PACKAGE in installed && weChatInstruction.matches(request)) {
            val name = installed[WECHAT_PACKAGE] ?: "微信"
            return Request(name, Part.ToolCall("explicit_app_launch", launchTool,
                """{"package":"$WECHAT_PACKAGE","reason":"按要求打开$name，准备搜索小程序"}"""))
        }
        val match = openInstruction.find(request) ?: return null
        val app = findInstalled(match.groupValues[1], installed) ?: return null
        return Request(app.second, Part.ToolCall("explicit_app_launch", launchTool,
            """{"package":"${app.first}","reason":"按要求打开${app.second}"}"""))
    }

    private fun findInstalled(raw: String, installed: Map<String, String>): Pair<String, String>? {
        val apps = installed.entries.mapNotNull { (pkg, name) -> normalize(name).takeIf { it.isNotEmpty() }?.let { it to (pkg to name) } }
        val words = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (count in words.size downTo 1) {
            val query = normalize(words.take(count).joinToString(" "))
            if (query.length < 2) continue
            apps.singleOrNull { it.first == query }?.let { return it.second }
            if (query in genericAppWords) continue
            val prefixMatches = apps.filter { (label, _) -> label.startsWith(query) }
            if (prefixMatches.size > 1) return null
            prefixMatches.singleOrNull()?.let { (label, app) -> if (matchesAppLabel(label, query)) return app }
            apps.filter { (label, _) -> !label.startsWith(query) && matchesAppLabel(label, query) }
                .singleOrNull()?.let { return it.second }
        }
        return null
    }

    private fun matchesAppLabel(label: String, query: String): Boolean = when {
        label in genericAppWords -> false
        label.startsWith(query) -> query.length >= 3 &&
            shorthandBlockedSuffixes.none { label.removePrefix(query).contains(it) }
        query.endsWith(label) -> query.removeSuffix(label) in appPrefixes
        else -> false
    }

    private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit).removeSuffix("app")

    suspend fun execute(request: Request, executor: ActionExecutor, tools: ToolDispatcher,
        history: MutableList<Message>): ActionOutcome {
        val call = request.call
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

package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.Place
import com.daiyimo.maeve.core.observation.Trust
import com.daiyimo.maeve.core.observation.Untrusted
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Concurrency
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.okObj
import com.daiyimo.maeve.core.tool.str
import com.daiyimo.maeve.core.userscreen.UserScreen
import com.daiyimo.maeve.core.userscreen.UserScreenMessage
import com.daiyimo.maeve.core.userscreen.UserScreenPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import java.time.ZoneId

const val VIEW_USER_SCREEN = "view_user_screen"

/**
 * 按编号回看用户在这个对话里发来的某一屏:给出 App、时刻和屏上的文字。
 * 主模型看得见图就连截图一起给;看不见图但有看图模型时,带着问题问它那张存下来的截图
 * (和读屏共用同一个看图模型,只是看的是这张旧的,不是此刻的屏)。只在本次或之前几轮带过用户屏幕时才提供。
 */
internal object ViewUserScreen : AgentTool {
    override val verb = Verb.ViewUserScreen
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe

    override val def = ToolDef(VIEW_USER_SCREEN,
        Prompts.tool(VIEW_USER_SCREEN),
        mapOf(
            "id" to str("The screen's id, as given with the screen (Screen id) or in the earlier turns."),
            "question" to str("What you want to know about it, 1-300 characters. Needed when you cannot see images: the vision model answers it from the screenshot."),
        ), listOf("id"))

    private val ROLE: String by lazy { Prompts.role("user_screen") }

    /** 要存着用户发来的屏,而且这次任务确实带过这样的屏(开局目标或之前几轮)才提供。 */
    override fun available(ctx: ToolContext): Boolean {
        if (ctx.config.userScreens == null) return false
        val spec = ctx.config.runSpec ?: return false
        return UserScreenMessage.has(spec.goal) || spec.priorTurns.any { turn ->
            UserScreenMessage.has(turn.userText) || turn.steers.any(UserScreenMessage::has)
        }
    }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val screenId = a.str("id", max = 64)!!.trim()
        val asked = a.str("question", max = 300)?.trim()
        val store = ctx.config.userScreens!!
        val shot = read { store.screen(screenId) } ?: return fail("screen_not_found", Feedback["user_screen_not_found"])
        val tz = ZoneId.systemDefault()
        // 被拦下的一屏(保护页、付款页)只告诉它在,不给内容也不给图
        if (shot.blocked) return ToolExecution(blocked(screenId, shot, tz))
        val image = read { store.image(screenId) }
        val showImage = ctx.config.visionEnabled && image != null
        val visionModel = ctx.config.vision
        val visionAnswer = if (!showImage && asked != null && image != null && visionModel != null)
            read { visionModel.ask(ROLE, asked, listOf(image)) } else null
        val onScreen = textOf(shot)
        val content = okObj {
            put("id", screenId); put("app", shot.app); shot.packageName?.let { put("package", it) }
            put("time", UserScreenPrompt.time(shot.takenAt, tz))
            put("text", Untrusted.wrap(onScreen))
            if (showImage) put("imageAttached", true)
            visionAnswer?.let { put("seen", Untrusted.wrap(it)) }
            noteWhy(showImage, visionAnswer, image != null, visionModel != null, asked)
        }
        val observation = Observation(ObsKind.Read, listOfNotNull(onScreen, visionAnswer).joinToString("\n"),
            place = shot.packageName?.let(Place::app), trust = Trust.Untrusted)
        return ToolExecution(content, images = if (showImage) listOf(image!!) else emptyList(), observations = listOf(observation))
    }

    /** 屏上的字,一行一个,前面是中心点。 */
    fun textOf(screen: UserScreen): String = screen.lines.joinToString("\n") { "(${it.x},${it.y}) ${it.text}" }

    /** 被拦下的一屏的回应:只说它在,附上固定说明,不给内容也不给图。 */
    private fun blocked(screenId: String, shot: UserScreen, tz: ZoneId) = okObj {
        put("id", screenId); put("app", shot.app); put("time", UserScreenPrompt.time(shot.takenAt, tz)); put("blocked", true)
        put("message", Feedback["user_screen.blocked_reply"])
    }

    /** 看图没成时补一句说明:是没图、没看图模型、没给问题,还是看图模型这次没答上来。 */
    private fun JsonObjectBuilder.noteWhy(showImage: Boolean, answered: String?, hasImage: Boolean, hasVision: Boolean, asked: String?) {
        when {
            showImage || answered != null -> Unit
            !hasImage -> put("note", Feedback["user_screen_no_image"])
            !hasVision -> put("note", Feedback["user_screen_text_only"])
            asked == null -> put("note", Feedback["user_screen_ask"])
            else -> put("note", Feedback["user_screen_vision_failed"])
        }
    }

    private inline fun <T> read(block: () -> T?): T? =
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
}

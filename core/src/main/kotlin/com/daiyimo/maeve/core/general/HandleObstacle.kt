package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.BARRIER_KINDS
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.ok
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.put

/**
 * 说出挡住当前 App 或网页的是什么,换回一条下一步该怎么处理的提示。
 * 只读、也不越障:按障碍种类给一条 feedback,告诉模型该关闭、该登录还是该交给用户;
 * 属于「说的是哪块屏」的那一类通用工具([SurfaceAware])。
 */
internal object HandleObstacle : SurfaceAware {
    override val verb = Verb.HandleObstacle
    override val label = "遇到障碍"
    override val risk = Risk.Read
    override val withScreens = true

    override val def = ToolDef("handle_obstacle", Prompts.tool("handle_obstacle"), mapOf(
        "kind" to str(KIND_DOC, BARRIER_KINDS),
        "reason" to str("What on the screen shows it."),
    ), listOf("kind"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        checkSurface(a, ctx)
        val barrier = barrierKind(a)
        // reason 只作说明留给界面和记录,不影响给哪条提示
        a.str("reason", max = 300)
        return ok { put("kind", barrier); put("hint", advice(barrier)) }
    }

    /** 取出并校验 kind:不在 [BARRIER_KINDS] 里当参数错,原样返回供结果回显。 */
    private fun barrierKind(a: Args): String = a.str("kind")!!.also { kind ->
        if (kind !in BARRIER_KINDS) throw ArgError("kind", "must be one of ${BARRIER_KINDS.joinToString("|")}")
    }

    /** 应对提示就是 feedback 里 barrier.<kind> 那一条;kind 已校验过,只可能是 [BARRIER_KINDS] 里的几种。 */
    private fun advice(kind: String): String = when (kind) {
        "popup" -> Feedback["barrier.popup"]
        "login" -> Feedback["barrier.login"]
        "captcha" -> Feedback["barrier.captcha"]
        "access_blocked" -> Feedback["barrier.access_blocked"]
        else -> Feedback["barrier.payment"]
    }
}

/** kind 参数的说明:每种障碍是什么、能不能自己关掉,写全好让模型选对种类。 */
private const val KIND_DOC = "What is in the way: popup = a promotion, guide or update dialog that can be closed; login = a sign-in wall; captcha = a human-verification puzzle; access_blocked = the app or site is limiting access (操作频繁, 环境异常, or a verification that keeps coming back); payment = paying, entering a password or an identity check."

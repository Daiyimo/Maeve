package com.daiyimo.maeve.core.uistate.tools

import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.HINT_PROTECTED
import com.daiyimo.maeve.core.tool.HINT_UNSUPPORTED_BROWSER
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.ok
import com.daiyimo.maeve.core.uistate.ScreenCheck
import com.daiyimo.maeve.core.uistate.ScreenDiagnosis
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.ScreenState
import com.daiyimo.maeve.core.uistate.screen
import com.daiyimo.maeve.core.uistate.session
import kotlinx.serialization.json.put

/** 诊断这块屏:空白、还在加载、锁屏或受保护;要过锁屏关卡(锁着屏诊断不出东西)。 */
internal class Diagnose(f: ScreenFamily) : ScreenTool(f, "diagnose") {
    override val verb = Verb.Diagnose
    override val label = "检查屏幕"
    override val risk = Risk.Write
    override val def by lazy { ToolDef(name, describe()) }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        // 端口给得出判定就先按它报:现状、能不能用、要不要人接,并附上下一步的提示
        ctx.screen.checkScreen()?.let { return report(it) }
        // 端口判不了(网页一类):按最近一次读屏的元素估「能不能用」
        return reportFromSnapshot(ctx)
    }

    /** 端口判定 [c] 对应的结果;hint 按状态分别给,没有可说的状态就不给这个字段。 */
    private fun report(c: ScreenCheck): ToolExecution {
        val place = family.placeKey
        return ok {
            put(place, c.currentApp)
            put("state", c.state.wire)
            put("usable", c.state == ScreenState.NORMAL)
            put("needsHuman", c.needsHuman)
            c.takeoverReason?.let { put("takeoverReason", it) }
            c.cause?.let { put("cause", it) }
            hintFor(c)?.let { put("hint", it) }
        }
    }

    /** 端口判定不了时按一屏元素估的结果:有可读元素才算能用,并说清为什么不能用。 */
    private suspend fun reportFromSnapshot(ctx: ToolContext): ToolExecution {
        val place = family.placeKey
        val s = ctx.session.snapshot()
        val usable = s.elements.any { it.meaningful }
        return ok {
            put(place, s.packageName ?: s.unreadableApp)
            put("usable", usable)
            put("needsHuman", false)
            if (!usable) {
                put("hint", s.unreadableApp?.let { Feedback.format("unreadable_window", "app" to it) } ?: Feedback["screen.no_elements"])
            }
        }
    }

    private fun hintFor(c: ScreenCheck): String? = when (c.state) {
        ScreenState.NORMAL -> HINT_UNSUPPORTED_BROWSER.takeIf { c.takeoverReason == ScreenDiagnosis.UNSUPPORTED_BROWSER }
        ScreenState.BLANK -> when {
            // 前台 App 在,只是窗口读不到:不是没打开,不要再启动
            c.currentApp == null && c.cause == ScreenDiagnosis.NO_WINDOW -> Feedback[family.emptyHint]
            c.currentApp != null && c.cause == ScreenDiagnosis.UNREADABLE_WINDOW -> Feedback.format("unreadable_window", "app" to c.currentApp)
            else -> Feedback["screen.blank"]
        }
        ScreenState.LOADING -> Feedback["screen.loading"]
        ScreenState.PROTECTED -> HINT_PROTECTED
        // 被限制访问:除了交给用户,还要别马上重试
        ScreenState.NEEDS_HUMAN -> if (c.takeoverReason == "access_blocked") Feedback["barrier.access_blocked"]
        else Feedback.format("screen.needs_human", "reason" to c.takeoverReason)
    }
}

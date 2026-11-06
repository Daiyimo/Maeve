package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.gate.Irreversible
import com.daiyimo.maeve.core.record.Redactor
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.daiyimo.maeve.core.tool.Verb

/**
 * 动作的运行记录:工具结果、确认关卡、画面。截图编号在这里统一发(循环自己存的画面也从这里取号)。
 * 记录失败不影响任务。[saveScreen] 为 null 时子步画面不存(编号也不发)。
 */
class ActionRecorder(
    private val sink: RunSink,
    private val clock: () -> Long = System::currentTimeMillis,
    private val saveScreen: ((Int, ByteArray) -> Unit)? = null,
) {
    private var shots = 0

    val savesScreens: Boolean get() = saveScreen != null

    fun now(): Long = clock()

    fun nextShot(): Int = ++shots

    fun event(e: RunEvent) {
        try {
            sink.event(e)
        } catch (x: CancellationException) {
            throw x
        } catch (x: Throwable) {
            // 记录失败不影响任务
        }
    }

    fun confirmation(hit: Irreversible, decision: String) =
        event(RunEvent.Confirmation(clock(), hit.action.name, RunEvent.clip(Redactor.text(hit.matched)), null, null, decision))

    /**
     * 最近一次改了状态的动作(界面上改东西、对外、不可逆,或沙箱里的写操作;报了失败、也没说界面变了的不算)执行之前证据账本的位置;
     * 之后没有重新读过,回答交出前要另请核查(见 AnswerReview)。还没有这样的动作为 null。
     */
    var lastChangeMark: Int? = null
        private set

    fun noteChange(ledgerMark: Int) { lastChangeMark = ledgerMark }

    /** 本任务里放行了几个对外或不可逆的动作(发送、提交、下单、删除……);回答交出前据此决定要不要请核查模型。 */
    var outwardActs = 0
        private set

    /**
     * 本任务里真的点过、滚过、划过几次屏(被拦下、参数没执行的不算)。
     * 点击可能点歪:界面没大动、树的变化也够判 Done,回答却可能建立在那一下上,收尾核查要看得更严(见 [com.daiyimo.maeve.core.agent.AnswerReview])。
     */
    var navActs = 0
        private set

    fun approval(origin: Origin, tool: String, hit: Irreversible, risk: Risk, decision: String) {
        if (decision == "allowed" && risk.level.outward) outwardActs++
        event(RunEvent.Approval(clock(), origin.step, origin.policy.source, tool, hit.action.name, RunEvent.clip(Redactor.text(hit.matched)),
            risk.level.name, risk.payment, decision))
    }

    /** 一次调用的结果;[content] 是交给模型(或调用方)的那份,[extra] 是只进运行记录的字段。 */
    fun result(
        step: Int, tool: String, verb: Verb?, content: JsonObject, extra: JsonObject, changed: Boolean?, shotId: Int?, durationMs: Long,
        source: String, callId: String, waitMs: Long = 0,
    ) {
        if (verb in NAV_VERBS && "error" !in content) navActs++
        // 用户的回答可能是验证码一类,不进运行记录
        val shown = if (verb == Verb.AskUser && content.containsKey("answer")) JsonObject(content + ("answer" to JsonPrimitive(HIDDEN_ANSWER))) else content
        val recorded = Redactor.result(verb, shown).let { if (extra.isEmpty()) it else JsonObject(it + extra) }
        event(RunEvent.ToolResult(clock(), step, tool, RunEvent.clipResult(Redactor.text(recorded.toString())), changed, shotId, durationMs,
            waitMs = waitMs, source = source, callId = callId))
    }

    /** 当场存一张子步画面,返回截图编号;不存画面时为 null。 */
    fun saveFrame(png: ByteArray): Int? {
        val save = saveScreen ?: return null
        val id = nextShot()
        try {
            save(id, png)
        } catch (x: CancellationException) {
            throw x
        } catch (x: Throwable) {
            // 同上
        }
        return id
    }

    private companion object {
        const val HIDDEN_ANSWER = "(用户的回答,不记录)"

        /** 在界面上导航的动作:点了、滚了、划过。 */
        private val NAV_VERBS = setOf(Verb.Tap, Verb.Scroll, Verb.Swipe)
    }
}

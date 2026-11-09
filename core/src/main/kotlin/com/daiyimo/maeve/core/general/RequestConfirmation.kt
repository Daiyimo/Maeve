package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.gate.ConfirmationRequest
import com.daiyimo.maeve.core.risk.IrrevAction
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.CONFIRM_ACTIONS
import com.daiyimo.maeve.core.tool.CONFIRM_FIELD_MAX
import com.daiyimo.maeve.core.tool.HINT_PAYMENT_TAKEOVER
import com.daiyimo.maeve.core.tool.Outcome
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.capField
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.okObj
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.put
import com.daiyimo.maeve.core.uistate.confirmTarget
import com.daiyimo.maeve.core.uistate.focus
import com.daiyimo.maeve.core.uistate.lastSnapshot
import com.daiyimo.maeve.core.uistate.session

/**
 * 花钱的那一步(下单、屏上有应付金额时的提交等)前请用户同意;询问时只有这一种要问。付款本身不在这里,交给用户亲手付。
 * 申报的前台核对用此刻重新读的一屏(不登记编号,不动模型手里的 ref)。
 */
internal object RequestConfirmation : SurfaceAware {
    override val verb = Verb.RequestConfirmation
    override val label = "请你同意"
    override val risk = Risk.Read

    override val def = ToolDef("request_confirmation", Prompts.tool("request_confirmation"), mapOf(
        "action" to str(ACTION_NOTE, CONFIRM_ACTIONS),
        "target" to str(TARGET_NOTE),
        "content" to str(CONTENT_NOTE),
        "amount" to str(AMOUNT_NOTE),
        "app" to str(APP_NOTE),
    ), listOf("action", "target", "content", "amount", "app"))

    /** app 参数写什么按本任务的屏说:一块屏时写那块的写法,几块屏时各写一句。 */
    override fun def(ctx: ToolContext): ToolDef {
        val base = super.def(ctx)
        val screens = ctx.focus.surfaces
        val hint = if (screens.size == 1) screens.single().targetNote
        else screens.joinToString("; ") { "on the ${it.id} screen, ${it.targetNote}" }
        return ToolDef(base.name, base.description, base.props + ("app" to str("$APP_NOTE: $hint.")), base.required)
    }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        checkSurface(a, ctx)
        val kind = requireAction(a)
        // 付款不在确认里办:先把申报记下(交给用户付时补商品和金额),再让模型改走接管
        if (kind == IrrevAction.pay) {
            ctx.refusedPay = request(kind, a, a.str("app")!!.trim())
            return fail("payment_requires_takeover", HINT_PAYMENT_TAKEOVER)
        }
        requireSpendableAmount(a)
        val confirm = request(kind, a, ctx.confirmTarget(a.str("app")!!.trim()))
        // 申报时读到的前台包名一起交上去,供之后核对
        val foreground = ctx.session.peek()?.packageName ?: ctx.lastSnapshot?.packageName
        return ToolExecution(okObj { put("asked", true) }, outcome = Outcome.Confirm(confirm, foreground))
    }

    /** 取出 action 并换成不可逆动作;不在 [CONFIRM_ACTIONS] 里当参数错。 */
    private fun requireAction(a: Args): IrrevAction =
        a.str("action")!!.trim().let { name -> IrrevAction.entries.firstOrNull { it.name == name } }
            ?: throw ArgError("action", "must be one of ${CONFIRM_ACTIONS.joinToString("|")}")

    /** 只有花钱要问:写不出金额的操作不用申请,直接做。 */
    private fun requireSpendableAmount(a: Args) {
        if (a.str("amount")?.any(Char::isDigit) != true) throw ArgError("amount",
            "required: the total the user will pay, as shown on screen. An action that spends no money needs no approval: do it directly")
    }

    /** 从参数拼出申报:target/content 截到界面原样显示得下,金额可选,[app] 是这一步发生在哪。 */
    private fun request(kind: IrrevAction, a: Args, app: String) = ConfirmationRequest(
        kind, capField(a.str("target", max = 10000)!!), capField(a.str("content", max = 10000)!!),
        a.str("amount", max = 10000)?.let(::capField), app,
    )
}

private const val APP_NOTE = "Where the action happens, as on screen now"
private const val ACTION_NOTE = "Kind of action that spends the money (order for placing an order or booking)."
private const val TARGET_NOTE = "Who or where the money goes (shop, seller, service), as shown on screen. At most $CONFIRM_FIELD_MAX characters."
private const val CONTENT_NOTE = "What is bought: item, spec, quantity. At most $CONFIRM_FIELD_MAX characters."
private const val AMOUNT_NOTE = "Amount and currency the user will pay, as shown on screen."

package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.FAILURE_SUBTYPES
import com.daiyimo.maeve.core.tool.Outcome
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.TAKEOVER_REASONS
import com.daiyimo.maeve.core.tool.TAKEOVER_REASON_ALIASES
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.bool
import com.daiyimo.maeve.core.tool.capField
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.okObj
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 明说这事没办成:说清卡在哪、办到哪、屏现在什么样,并给循环一个失败结局。 */
internal object EndNotDone : AgentTool {
    override val verb = Verb.EndNotDone
    override val label = "说明没办成的原因"
    override val risk = Risk.Read

    override val def = ToolDef("end_not_done",
        Prompts.tool("end_not_done"),
        mapOf("message" to str(BLOCKER_NOTE),
            "subtype" to str(FAILURE_KIND_NOTE, FAILURE_SUBTYPES),
            "recoverable" to bool(RETRY_NOTE)),
        listOf("message"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val message = a.str("message", max = 8000)!!
        val subtype = a.str("subtype") ?: "interaction_failed"
        if (subtype !in FAILURE_SUBTYPES) throw ArgError("subtype", "must be one of ${FAILURE_SUBTYPES.joinToString("|")}")
        return failure(message, subtype, a.bool("recoverable", false))
    }

    /** 失败结局连同结果一起给;报失败本身不算又一次失败计数。 */
    private fun failure(message: String, subtype: String, recoverable: Boolean) = ToolExecution(buildJsonObject {
        put("ok", false); put("reported", true); put("subtype", subtype); put("recoverable", recoverable); put("counts_as_failure", false)
    }, outcome = Outcome.Failure(message, subtype, recoverable))
}

/** 停下来问用户一个问题;给了选项时就成「选一个」。 */
internal object AskUser : AgentTool {
    override val verb = Verb.AskUser
    override val label = "问你一个问题"
    override val risk = Risk.Read

    override val def = ToolDef("ask_user",
        Prompts.tool("ask_user"),
        mapOf("prompt" to str(QUESTION_NOTE),
            "options" to buildJsonObject {
                put("type", "array"); put("description", CHOICES_NOTE); put("minItems", 2); put("maxItems", 8)
                put("items", buildJsonObject { put("type", "string") })
            }),
        listOf("prompt"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val prompt = a.str("prompt", max = 1000)!!.trim()
        return ToolExecution(okObj { put("asked", true) }, outcome = Outcome.Input(prompt, parseOptions(a)))
    }

    /** 选项可省;给了就去空白、去重,至少留两个不同项,否则「选一个」没有意义。 */
    private fun parseOptions(a: Args): List<String>? {
        val options = a.strList("options", 2, 8)?.map(String::trim)?.distinct() ?: return null
        if (options.size < 2) throw ArgError("options", "must have at least 2 different choices")
        return options
    }
}

/** 交给用户亲手操作屏幕;要过锁屏关卡(用户要在屏上操作)。 */
internal object HandToUser : SurfaceAware {
    override val verb = Verb.HandToUser
    override val label = "请你接手"
    override val risk = Risk.Write
    override val withScreens = true

    override val def = ToolDef("hand_to_user",
        Prompts.tool("hand_to_user"),
        mapOf("reason" to str(TAKEOVER_WHY_NOTE, TAKEOVER_REASONS),
            "message" to str(TAKEOVER_WHAT_NOTE),
            "item" to str(PAY_ITEM_NOTE),
            "amount" to str(PAY_AMOUNT_NOTE),
            "address" to str(PAY_ADDRESS_NOTE)),
        listOf("reason", "message"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        checkSurface(a, ctx)
        val requested = a.str("reason")!!
        val reason = TAKEOVER_REASON_ALIASES[requested] ?: requested
        if (reason !in TAKEOVER_REASONS) throw ArgError("reason", "must be one of ${TAKEOVER_REASONS.joinToString("|")}")
        val message = a.str("message", max = 1000)!!
        // 付款:模型没写的项用之前被拒的付款申报里的;只给紧接着的那次付款接管用,取用即清,之后无关的付款不串用
        val prior = ctx.refusedPay.takeIf { reason == "payment" }?.also { ctx.refusedPay = null }
        return ToolExecution(okObj { put("handedOver", true) }, outcome = Outcome.Takeover(
            reason, message, field(a, "item", prior?.content), field(a, "amount", prior?.amount), field(a, "address", null)))
    }

    /** 付款项:模型写了就用模型的,没写退回之前被拒的付款申报里记的([fallback]);都截到界面显示得下。 */
    private fun field(a: Args, key: String, fallback: String?) = a.str(key, max = 10000)?.let(::capField) ?: fallback
}

// 下面是各工具参数的说明原文,逐字发给模型,抽成常量免得定义里排一长串。

private const val BLOCKER_NOTE = "The concrete blocker, what was and was not completed, and the screen's current state."
private const val FAILURE_KIND_NOTE = "Failure kind (default interaction_failed)."
private const val RETRY_NOTE = "Whether a retry could still succeed (default false)."
private const val QUESTION_NOTE = "The question to show the user."
private const val CHOICES_NOTE = "Optional 2-8 answer choices."
private const val TAKEOVER_WHY_NOTE = "Why the user is needed."
private const val TAKEOVER_WHAT_NOTE = "Tell the user what to do and where the task stopped. For payment: the item, spec, quantity, the amount shown on screen and the address."
private const val PAY_ITEM_NOTE = "For payment: the item, spec and quantity, as shown on screen."
private const val PAY_AMOUNT_NOTE = "For payment: the amount the user will pay, with currency, as shown on screen."
private const val PAY_ADDRESS_NOTE = "For payment: the delivery address shown on screen, if any."

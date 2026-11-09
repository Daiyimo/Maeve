package com.daiyimo.maeve.core.gate

import com.daiyimo.maeve.core.action.ConfirmChoice
import com.daiyimo.maeve.core.risk.IrrevAction
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.uistate.Hant
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import java.math.BigDecimal
import kotlin.math.abs

/** 模型申报的一次要花钱的操作;字段已去首尾空白、截短,界面上照此显示。[app] 是包名。 */
data class ConfirmationRequest(val action: IrrevAction, val target: String, val content: String, val amount: String?, val app: String)

sealed interface DeclareResult {
    /** 申报的 App 和前台不一致(或读不到前台),不交给用户。 */
    data object Mismatch : DeclareResult
    /** 本任务里用户已经拒绝过同一 App 的同类操作,不再问。 */
    data object Declined : DeclareResult
    /** 付款不走这里,走接管。 */
    data object Unsupported : DeclareResult
    data object Pending : DeclareResult
}

sealed interface Permit {
    data object Allowed : Permit
    /** 没有同意。 */
    data object NoGrant : Permit
    /** 有同意,但和这一步对不上(或已过期);这次同意已作废。 */
    data class Mismatch(val reason: String) : Permit
}

/** 申报里的那个数:只取第一段数字。 */
private val DECLARED_NUMBER = Regex("\\d+(\\.\\d+)?")

/** 带币种的金额:¥25、NT$1,290、25元。 */
private val MONEY = Regex("[¥￥$＄]\\s*(\\d+(?:\\.\\d+)?)|(\\d+(?:\\.\\d+)?)\\s*元")

/** 数字里的千分位逗号:1,290 先读成 1290 再比。 */
private val THOUSAND_SEPARATOR = Regex("(?<=\\d),(?=\\d{3}(?!\\d))")

/** 紧挨着这些字的金额是优惠、运费一类,不是这次应付的钱。 */
private val DISCOUNTS = listOf("优惠", "减", "抵扣", "运费", "券", "红包", "已省")
private const val DISCOUNT_WINDOW = 4

/** 和风险分类共用同一份合计标签。 */
private val TOTAL_WORDS = IrreversibleDetector.TOTAL_LABELS

/** 把同意的那一步做出来的两种写操作:打字、清空输入框,不作废同意。 */
private val GRANT_KEPT_VERBS = setOf(Verb.TypeText, Verb.ClearText)

private fun topOf(e: UiElement): Int = if (e.bounds.size == 4) e.bounds[1] else 0

private fun centerY(e: UiElement): Int = if (e.bounds.size == 4) (e.bounds[1] + e.bounds[3]) / 2 else 0

private fun inside(child: UiElement, parent: UiElement): Boolean {
    val inner = child.bounds
    val outer = parent.bounds
    return inner.size == 4 && outer.size == 4 &&
        inner[0] >= outer[0] && inner[1] >= outer[1] && inner[2] <= outer[2] && inner[3] <= outer[3]
}

/** 比对文字用:去掉空白、不分大小写。 */
private fun canonical(text: String): String = text.filterNot { it.isWhitespace() }.lowercase()

/**
 * 用户对一次要花钱的操作的同意(询问时只有付款要问):只放行紧接着的那一个匹配操作。
 * 同意绑定申报内容:同一前台 App、同类动作、对象出现在屏上(发送时在标题栏),发送时要发的字与申报相等,
 * 应付上下文里申报的金额在屏上;普通页面上的价格、待遇和区间不是应付金额,不参与核对;
 * 下一次命中的不可逆操作无论对不对得上都用掉它。中间做了打字以外的写操作(包括返回、打开 App)、
 * 或过了 [MAX_STEPS] 步,同意作废。只能同意这一次;只在一次任务内有效,任务开始和结束时 [reset]。
 * 任务循环和界面回答可能在不同线程,读写都加锁。
 */
class ConfirmationGate {
    /** 一次可用的同意:用户答应的那条申报,以及回答发生在第几步。 */
    private class Approval(val request: ConfirmationRequest, val step: Int)

    /** 已经交给用户、还在等他回答的那条申报。 */
    private var awaiting: ConfirmationRequest? = null

    /** 用户答应了、还没被哪一步用掉的那一次。 */
    private var approval: Approval? = null

    /** 本任务里用户拒绝过的(动作, App):同一类不再问。 */
    private val refused = mutableSetOf<Pair<IrrevAction, String>>()

    @Synchronized
    fun declare(req: ConfirmationRequest, foregroundApp: String?): DeclareResult {
        if (req.action == IrrevAction.pay) return DeclareResult.Unsupported
        if ((req.action to req.app) in refused) return DeclareResult.Declined
        if (foregroundApp == null || foregroundApp != req.app) return DeclareResult.Mismatch
        awaiting = req
        return DeclareResult.Pending
    }

    /** [step] 是回答所在的步数,过期按它算。 */
    fun decide(approved: Boolean, step: Int = 0) {
        decide(if (approved) ConfirmChoice.Once else ConfirmChoice.Deny, step)
    }

    /** 按用户的选择记一次性同意。 */
    @Synchronized
    fun decide(choice: ConfirmChoice, step: Int) {
        val req = awaiting ?: return
        awaiting = null
        if (!choice.approved) {
            refused += req.action to req.app
            return
        }
        approval = Approval(req, step)
    }

    /** 有一次已同意、还没用掉的操作。 */
    val hasGrant: Boolean @Synchronized get() = approval != null

    /** 用户同意了、还没被用掉的那次操作;没有为 null。 */
    val granted: ConfirmationRequest? @Synchronized get() = approval?.request

    /** [verb]:刚执行的写操作做的动作;输入、清空不作废同意(同意的就是要输入的这一步)。 */
    @Synchronized
    fun noteWrite(verb: Verb?) {
        if (verb !in GRANT_KEPT_VERBS) approval = null
    }

    @Synchronized
    fun reset() {
        awaiting = null
        approval = null
        refused.clear()
    }

    /**
     * [screen] 是此刻的一屏;[typing] 是这一步要输入并回车的文字(type_text 时)。
     * [seen] 是看图找到的点复看确认过的所见文字:读屏里没有文字(自绘界面)时,对象可以对它核,
     * 要发的字读不到也就不核(画面上的字核不了)。有同意时不论结果都用掉它。付款永远不放行。
     */
    @Synchronized
    fun permits(hit: Irreversible, screen: ScreenSnapshot?, step: Int, typing: String? = null, seen: List<String> = emptyList()): Permit {
        val held = approval ?: return Permit.NoGrant
        approval = null
        val r = held.request
        if (hit.action == IrrevAction.pay) return Permit.Mismatch("payment is never approved here")
        if (step - held.step > MAX_STEPS) return Permit.Mismatch(expiredReason())
        if (hit.action != r.action) return Permit.Mismatch("approved ${r.action.name} but this step is ${hit.action.name}")
        val onScreen = screen ?: return Permit.Mismatch(appReason(r, null))
        if (onScreen.packageName != r.app) return Permit.Mismatch(appReason(r, onScreen.packageName))
        // 读屏一个带字的元素都没有、又有复看确认过的所见文字时,对象和内容都按那份文字核
        val bySight = textless(onScreen) && seenShows(r, seen)
        if (!bySight && !targetShown(r, onScreen)) return Permit.Mismatch(targetReason(r))
        if (r.action == IrrevAction.send && !bySight && !contentMatches(r.content, onScreen, typing)) {
            return Permit.Mismatch("the text to send is not exactly what was approved")
        }
        if (bindsAmount(hit, onScreen) && !AmountCheck(onScreen, hit.matched).agrees(r.amount)) {
            return Permit.Mismatch("the approved amount ${r.amount ?: "(none)"} is not on the screen")
        }
        return Permit.Allowed
    }

    /**
     * 不在屏上的一步(工具声明了要问,如会改动东西的 root 命令):同意的就是这一条,申报一字不差才放行。
     * 有同意时不论结果都用掉它。
     */
    @Synchronized
    fun permitsExactly(ask: ConfirmationRequest, step: Int): Permit {
        val held = approval ?: return Permit.NoGrant
        approval = null
        if (step - held.step > MAX_STEPS) return Permit.Mismatch(expiredReason())
        if (held.request != ask) return Permit.Mismatch("approved a different action than this one")
        return Permit.Allowed
    }

    private fun expiredReason(): String = "the approval expired ($MAX_STEPS steps)"

    private fun appReason(r: ConfirmationRequest, foreground: String?): String =
        "approved in ${r.app} but the screen shows ${foreground ?: "unknown"}"

    /**
     * 对象要在屏上不可编辑的文字里(输入框里打的不算),至少两个字;
     * 发送时只认屏幕顶部的标题栏(聊天对象的名字在那里),其余动作屏上任何地方都行。
     */
    private fun targetShown(r: ConfirmationRequest, s: ScreenSnapshot): Boolean {
        val needle = canonical(r.target).take(MATCH_CHARS)
        if (needle.length < 2) return false
        return s.elements.any { e ->
            !e.editable && needle in canonical(e.name) &&
                (r.action != IrrevAction.send || (e.bounds.size == 4 && e.bounds[1] <= TITLE_BAND))
        }
    }

    private fun targetReason(r: ConfirmationRequest): String =
        "the target \"${r.target}\" is not shown " + (if (r.action == IrrevAction.send) "in the title area" else "on the screen")

    /**
     * 要发的就是同意的那段话:点发送时某个输入框里的字和它相等,type_text 回车时要输入的字和它相等。
     * 申报被截短(以 … 结尾)时比前缀。
     */
    private fun contentMatches(content: String, s: ScreenSnapshot, typing: String?): Boolean {
        val truncated = content.endsWith("…")
        val approved = canonical(content.removeSuffix("…"))
        fun sameText(candidate: String) = canonical(candidate).let { if (truncated) it.startsWith(approved) else it == approved }
        if (typing != null) return sameText(typing)
        return s.elements.any { it.editable && !it.hint && sameText(it.name) }
    }

    /** 读屏里一个带字的元素都没有(自绘界面、画布)。 */
    private fun textless(s: ScreenSnapshot): Boolean = s.elements.none { it.name.isNotBlank() }

    /** 申报的对象出现在复看确认过的所见文字里(至少两个字)。 */
    private fun seenShows(r: ConfirmationRequest, seen: List<String>): Boolean {
        val needle = canonical(r.target).take(MATCH_CHARS)
        return needle.length >= 2 && seen.any { needle in canonical(it) }
    }

    /**
     * 金额是否参与这次核对,和风险分类使用同一个应付上下文。明确下单、工具声明的金额动作始终要金额;
     * 普通提交、删除只有屏上有合计或付款面板时才要,避免把页面里的区间或待遇当成付款并反复要权。
     */
    private fun bindsAmount(hit: Irreversible, s: ScreenSnapshot): Boolean = when (hit.action) {
        IrrevAction.order, IrrevAction.pay, IrrevAction.other -> true
        IrrevAction.send -> false
        IrrevAction.submit, IrrevAction.delete -> IrreversibleDetector.hasPayableAmount(s)
    }

    /**
     * 一屏上的金额核对。申报的金额只和屏上带币种(¥/￥、元、$)的数比:「已售25件」「满25减3」不算;
     * 输入框和提示语里的不算;紧挨着优惠、减、抵扣、运费、券、红包、已省(或带负号)的金额不算。
     * 有合计/实付/应付/总计时:可滚动区外的每个合计都要等于申报的金额;都在滚动区里时看离按钮最近的那个;
     * 合计字样在、金额读不出(同一行后面几段拼起来也读不出)时不放行。
     * 没有合计时要等于可滚动区之外最大的那个金额。申报里没有数字不放行。
     */
    private class AmountCheck(s: ScreenSnapshot, private val pressed: String) {
        private val visible = s.elements.filter { !it.editable && !it.hint }
        private val scrollables = s.elements.filter { it.scrollable }
        private val totals = visible.filter { e -> TOTAL_WORDS.any { it in Hant.fold(e.name) } }

        fun agrees(amount: String?): Boolean {
            val declared = declaredIn(amount) ?: return false
            return if (totals.isEmpty()) largestOutsideScrollables(declared) else totalsAgree(declared)
        }

        private fun largestOutsideScrollables(declared: BigDecimal): Boolean {
            val largest = visible.filter(::outsideScrollables).flatMap { amountsIn(it.name) }.maxOrNull() ?: return false
            return sameValue(largest, declared)
        }

        private fun totalsAgree(declared: BigDecimal): Boolean {
            val rows = totals.map { it to totalAfter(it) }
            if (rows.any { it.second == null }) return false
            val outside = rows.filter { outsideScrollables(it.first) }
            if (outside.isNotEmpty()) return outside.all { sameValue(it.second!!, declared) }
            val button = visible.firstOrNull { it.name.trim() == pressed }
            val nearest = rows.minByOrNull { (row, _) ->
                if (button == null) -topOf(row) else abs(centerY(row) - centerY(button))
            } ?: return false
            return sameValue(nearest.second!!, declared)
        }

        /** 合计标签后面的金额:先看它自己,再把同一行右边的几段按横向顺序拼上去看。 */
        private fun totalAfter(label: UiElement): BigDecimal? {
            fun afterLabel(text: String): BigDecimal? = TOTAL_WORDS
                .mapNotNull { word -> text.indexOf(word).takeIf { it >= 0 }?.let { text.substring(it + word.length) } }
                .firstNotNullOfOrNull { amountsIn(it).firstOrNull() }
            return afterLabel(Hant.fold(label.name)) ?: afterLabel(Hant.fold(IrreversibleDetector.rowText(label, visible)))
        }

        private fun outsideScrollables(e: UiElement): Boolean = scrollables.none { inside(e, it) }

        private fun declaredIn(amount: String?): BigDecimal? =
            amount?.replace(THOUSAND_SEPARATOR, "")?.let { DECLARED_NUMBER.find(it)?.value }?.toBigDecimalOrNull()

        private fun sameValue(shown: BigDecimal, declared: BigDecimal): Boolean = shown.compareTo(declared) == 0

        /** 带币种的金额,去掉紧挨着优惠一类字样或带负号的。 */
        private fun amountsIn(name: String): List<BigDecimal> {
            val text = Hant.fold(name).replace(THOUSAND_SEPARATOR, "")
            return MONEY.findAll(text).filter { match ->
                val before = text.substring(maxOf(0, match.range.first - DISCOUNT_WINDOW), match.range.first)
                DISCOUNTS.none { it in before } && !before.trimEnd().endsWith("-")
            }.mapNotNull { match -> match.groupValues[1].ifEmpty { match.groupValues[2] }.toBigDecimalOrNull() }.toList()
        }
    }

    companion object {
        /** 同意后过了这么多步还没用就作废。 */
        const val MAX_STEPS = 3
        const val MATCH_CHARS = 20
        /** 发送对象要在这条线(0–999)以上:屏幕顶部约 15% 的标题栏。 */
        const val TITLE_BAND = 150
    }
}

package com.daiyimo.maeve.core.gate

import com.daiyimo.maeve.core.risk.GateTarget
import com.daiyimo.maeve.core.risk.IrrevAction
import com.daiyimo.maeve.core.tool.LOCATED_REF
import com.daiyimo.maeve.core.uistate.CdpKeys
import com.daiyimo.maeve.core.uistate.ElementContext
import com.daiyimo.maeve.core.uistate.Hant
import com.daiyimo.maeve.core.uistate.KeyCombo
import com.daiyimo.maeve.core.uistate.KeyModifier
import com.daiyimo.maeve.core.uistate.PressKeys
import com.daiyimo.maeve.core.uistate.ScreenDiagnosis
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** [matched]:命中的那段文字(按钮名、资源 ID、target 参数、输入框或滑动的起点)。 */
data class Irreversible(val action: IrrevAction, val matched: String)

/**
 * 写操作执行前的词表兜底:只认按钮名、回车/确认键、横滑列表行这几种信号,不做语义理解。
 * 拿不准时宁可多拦(模型补一次 request_confirmation 就能继续),只有 submit 故意只认完全相等。
 *
 * 读屏:[classify] 的 snapshot 是 ref 所属的那次读屏(没有 ref 时是最近一次),fresh 是此刻重新读的一屏。
 * 坐标、回车、滑动按 fresh 判(读不到才退回 snapshot);ref 两份都判,任一命中就算。
 */
object IrreversibleDetector {

    // ------------------------------------------------------------------ 词表

    /**
     * 名字里出现就算(短名字,或拼起来的名字里的每一段);但前面是「待」「已」、
     * 后面是 [STATUS_SUFFIXES] 的是订单状态或入口(待付款、付款成功、购买记录),不算。
     */
    val STRONG: Map<String, IrrevAction> = linkedMapOf(
        "支付" to IrrevAction.pay, "付款" to IrrevAction.pay, "转账" to IrrevAction.pay,
        "删除" to IrrevAction.delete, "撤回" to IrrevAction.delete,
        "下单" to IrrevAction.order, "购买" to IrrevAction.order, "结算" to IrrevAction.order, "结账" to IrrevAction.order,
        "取消订单" to IrrevAction.order, "确认收货" to IrrevAction.order, "退款" to IrrevAction.order,
        "转发" to IrrevAction.send,
    )
    private const val STRONG_MAX_LEN = 12
    private val STATUS_PREFIXES = listOf('待', '已')
    val STATUS_SUFFIXES = listOf("成功", "记录", "售后", "明细", "方式", "列表", "中心", "入口", "码")

    /** 这些付款词只认按钮本身,或前面是「立即」「确认」「去」(「充值中心」「话费充值」「优惠买单」是入口,不算)。 */
    val EXACT_PAY = listOf("收银台", "买单", "充值", "储值", "提现", "转出", "打赏", "缴费", "捐赠", "捐款")
    private val EXACT_PAY_PREFIXES = listOf("", "立即", "确认", "去")

    /** 红包只认按钮上的这几种说法(「微信红包」一类是聊天里的气泡)。 */
    val RED_PACKET = listOf("红包", "发红包", "塞钱进红包")

    /** 中文:等于词,或以词开头且只多出几个字(如「发送(1)」)。 */
    val PREFIX_WORDS: Map<IrrevAction, List<String>> = linkedMapOf(
        IrrevAction.send to listOf("发送", "传送", "发布", "发表"),
        IrrevAction.delete to listOf("清空", "移除", "注销", "退出群聊"),
        IrrevAction.order to listOf("提交订单"),
    )
    private const val PREFIX_SLACK = 4

    /** 等于词,或后面只跟标点(「评论(23)」「回复 3」是入口和计数,不算)。 */
    val PUNCT_WORDS: Map<IrrevAction, List<String>> = linkedMapOf(IrrevAction.send to listOf("评论", "回复"))

    /** 等于词,或前面是「立即」「确认」。 */
    val BOOKING_WORDS = listOf("预约", "报名")
    private val BOOKING_PREFIXES = listOf("", "立即", "确认")

    /** 只认完全相等:「提交搜索」一类不拦。 */
    val SUBMIT_WORDS = listOf("提交", "确认提交", "送出", "submit")

    /**
     * 一点就替用户联系别人、投出申请的按钮(点下去网站就把招呼语、简历发给对方),按提交算:
     * 等于词,或前面是「立即」「马上」「一键」「去」,后面只跟标点。
     * 「继续沟通」「已投递」「沟通记录」「联系客服」「联系我们」「私信」「消息」不算:是已有的对话、记录、客服页,
     * 或只是打开写消息的地方(真发出去的是之后的发送)。
     */
    val CONTACT_WORDS = listOf(
        "沟通", "打招呼", "打个招呼", "发起聊天", "发起沟通", "联系ta", "联系他", "联系她", "联系对方",
        "投递", "投简历", "投递简历", "应聘",
    )
    private val CONTACT_PREFIXES = listOf("", "立即", "马上", "一键", "去")

    /** 「申请」本身太泛(申请退款、申请权限):只认这几种说法。 */
    val APPLY_WORDS = listOf("立即申请", "一键申请", "申请职位", "申请岗位", "申请该职位")

    /** 英文:整个名字等于这些(不分大小写、空白合并、去掉末尾标点);单独的 Apply(筛选面板的应用)、Contact、Message 不算。 */
    val CONTACT_ENGLISH = listOf(
        "say hi", "say hello", "send greeting", "apply now", "easy apply", "quick apply", "apply for this job", "apply to this job",
        "submit application", "contact now", "contact recruiter", "contact employer", "contact seller", "contact host", "contact owner",
        "contact landlord", "contact agent",
    )

    /** 英文:名字以这个词开头(按单词边界,不分大小写),不限长度,如「Send message」「Delete conversation」。 */
    val ENGLISH: Map<String, IrrevAction> = linkedMapOf(
        "send" to IrrevAction.send, "post" to IrrevAction.send, "reply" to IrrevAction.send,
        "pay" to IrrevAction.pay, "donate" to IrrevAction.pay,
        "delete" to IrrevAction.delete, "remove" to IrrevAction.delete, "clear all" to IrrevAction.delete,
        "place order" to IrrevAction.order, "buy now" to IrrevAction.order, "checkout" to IrrevAction.order,
    )

    /** 弹窗、底部面板上的通用确认:同一屏上有强词或金额时才算。 */
    val GENERIC = listOf("确认", "确定", "ok", "完成", "confirm")

    /** 带这些字的金额是合计,不论在不在可滚动区里都算上下文。 */
    val TOTAL_LABELS = listOf("合计", "实付", "应付", "总计")

    /** 同屏有金额时,这些字说明是付款面板(付款方式、收银台、验密)。 */
    val PAY_SHEET_WORDS = listOf("支付方式", "付款方式", "支付宝", "银行卡", "收银台", "免密")

    /** 这几个字在普通页面上也常见(账户余额、登录密码):只在下半屏、同一区域有确认/支付一类按钮时才算付款面板。 */
    val WEAK_PAY_SHEET_WORDS = listOf("余额", "密码", "指纹", "零钱")

    /** 先从名字里去掉,再判强词:付款方式的选项不是付款动作。 */
    val EXCLUDED = listOf("支付宝", "支付方式", "支付设置")

    // ------------------------------------------------------------------ 判定用的词形与常量

    private val AMOUNT = Regex("[¥￥$＄]\\s*\\d|\\d+(\\.\\d+)?\\s*元")
    /** 介绍付款方式的字样(商品页、规格面板上常见),不是付款动作,先去掉再判。 */
    private val PAYMENT_ADS = Regex("支持[^\\s，,。]{0,8}?(?:付款|支付)|货到付款|先用后付|花呗|分期|付款方式说明")
    /** 页面上有这些下单按钮时,是商品页或规格面板:不按付款面板字样判。 */
    private val ORDER_CONTEXT = listOf("立即购买", "加入购物车", "立即下单")
    private val PAY_CONTEXT_WORDS = listOf("支付", "付款")
    /** 0–999 里这条线以下算屏幕底部区域(付款面板、底部弹层)。 */
    private const val BOTTOM_REGION = 500
    private val PAY_SHEET_ENGLISH = Regex("(?<![A-Za-z0-9_])(pay|payment|checkout total)(?![A-Za-z0-9_])", RegexOption.IGNORE_CASE)

    /** 资源 ID 按 _ - . 和驼峰拆开后的单词;submit、post 不在这里(资源 ID 里太常见)。 */
    private val RESOURCE_WORDS: Map<String, IrrevAction> = mapOf(
        "send" to IrrevAction.send, "reply" to IrrevAction.send,
        "pay" to IrrevAction.pay,
        "delete" to IrrevAction.delete, "remove" to IrrevAction.delete,
        "checkout" to IrrevAction.order,
    )
    private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")

    /** 输入框明确是搜索框才不算发送:其余输入框里回车一律按发送拦。提示语里找这些字,资源 ID 里按单词找 search。 */
    private val SEARCH_FIELD = listOf("search", "搜索", "搜寻")
    private val SEARCH_BUTTONS = setOf("搜索", "搜寻", "search")

    private const val KEY_ENTER = 66
    private const val KEY_NUMPAD_ENTER = 160
    private const val KEY_DPAD_CENTER = 23
    private const val KEY_SPACE = 62
    private const val KEY_DEL = 67
    private const val KEY_FORWARD_DEL = 112
    private val ACTIVATING_KEYS = setOf(KEY_ENTER, KEY_NUMPAD_ENTER, KEY_DPAD_CENTER, KEY_SPACE)

    /** 横滑超过屏宽这个比例(0–999)、从列表行上起手,按「滑动删除」算。 */
    private const val SWIPE_DELETE_MIN = 400
    /** 宽度超过这个(0–999)的可点元素算列表行。 */
    private const val ROW_MIN_WIDTH = 600
    /** 比这高(0–999)的元素不算「同一行」(容器、大段文字)。 */
    private const val ROW_MAX_HEIGHT = 150
    /** 比这矮(0–999)的可滚动区按轮播算。 */
    private const val CAROUSEL_MAX_HEIGHT = 400

    /** 输入框旁「清空」一类按钮的名字(去空白、小写)。 */
    private val FIELD_CLEAR_WORDS = setOf(
        "清空", "清除", "清空输入", "清除输入", "清空搜索", "清除搜索", "清空内容", "清除内容",
        "clear", "cleartext", "clearsearch", "clearquery", "×", "✕", "x",
    )
    /** 清空键离输入框左右边至多这么远(0–999)。 */
    private const val FIELD_NEAR = 80

    /** 改选中项的键:方向键、Home/End、翻页。 */
    private val SELECTION_KEYS = setOf(19, 20, 21, 22, 122, 123, 92, 93)
    /** 改了选中项就可能触发页面动作(onchange 提交)的控件。 */
    private val SELECTION_ROLES = setOf(
        "combobox", "listbox", "option", "radio", "radiogroup", "menu", "menubar", "menuitem",
        "menuitemradio", "menuitemcheckbox", "tree", "treeitem",
    )
    private val OPTION_ROLES = setOf("option", "radio", "menuitem", "menuitemradio", "menuitemcheckbox", "treeitem")

    private val DIALOG_SEPARATORS = Regex("[\\s，,。.！!？?：:；;]+")
    private val WHITESPACE_RUN = Regex("\\s+")

    // ------------------------------------------------------------------ 入口

    /** 这次调用要不要判(要判才去读屏)。 */
    fun needsCheck(target: GateTarget?, args: JsonObject): Boolean = when (target) {
        // 上传要点文件控件,和点击一样判
        GateTarget.Tap, GateTarget.FileControl -> true
        // 打字带回车时判;往一点上打字时那一下点击也判(点到的可能是发送一类的按钮)
        GateTarget.Enter -> argTrue(args, "pressEnter") || isPointTarget(args)
        GateTarget.Key -> keyCodeOf(args) in ACTIVATING_KEYS
        GateTarget.Swipe -> swipeOrigin(args) != null
        GateTarget.Interact -> interactNeedsCheck(args)
        null -> false
    }

    /** null = 不是不可逆操作。 */
    fun classify(target: GateTarget?, args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot? = null): Irreversible? {
        if (!needsCheck(target, args)) return null
        return when (target) {
            GateTarget.Tap, GateTarget.FileControl -> judgeTap(args, snapshot, fresh)
            GateTarget.Enter -> judgeTyping(args, snapshot, fresh)
            GateTarget.Key -> judgeEnter(args, snapshot, fresh ?: snapshot, keyCodeOf(args)!!)
            GateTarget.Swipe -> judgeSwipe(args, fresh ?: snapshot)
            GateTarget.Interact -> judgeInteract(args, snapshot, fresh)
            null -> null
        }
    }

    /**
     * 这一屏是付款页或付款面板:有确认支付、支付密码一类字样,或有金额且像付款面板
     * (商品页、规格面板不算)。返回命中的依据;不是为 null。
     */
    fun paymentPage(s: ScreenSnapshot): String? {
        ScreenDiagnosis.paymentText(s.elements.map { it.name })?.let { return it }
        val visible = visibleElements(s)
        val amount = visible.any { hasAmountMarker(it, visible) }
        return if (amount && looksLikePaySheet(visible)) "payment sheet with an amount" else null
    }

    /**
     * 这一屏有没有和动作相连的应付金额上下文:合计/实付/应付/总计,或带金额的付款页、付款面板。
     * 单独出现的价格、待遇、区间只是页面内容;明确付款、下单动作不经过这里,始终按金额动作判。
     */
    fun hasPayableAmount(s: ScreenSnapshot): Boolean {
        val visible = visibleElements(s)
        if (visible.none { AMOUNT.containsMatchIn(rowText(it, visible)) }) return false
        val labelled = visible.any { e -> TOTAL_LABELS.any { it in Hant.fold(e.name) } }
        return labelled || ScreenDiagnosis.paymentText(visible.map { it.name }) != null || looksLikePaySheet(visible)
    }

    /** 点击找不到作用在哪个元素上(读屏缺失、编号不属于这次读屏、坐标处没有元素);供运行记录留痕。 */
    fun unresolved(target: GateTarget?, args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot? = null): Boolean =
        target == GateTarget.Tap && tapCandidates(args, snapshot, fresh).isEmpty()

    /**
     * 按钮名匹配词表;[screen] 给了时,通用的「确认/确定」看同屏有没有强词或金额。
     * [hit] 是被点的元素(知道时):可滚动区里的金额只在被点的元素不在那个区里时才不算。
     */
    fun matchName(name: String, screen: ScreenSnapshot? = null, hit: UiElement? = null): IrrevAction? {
        val folded = Hant.fold(name).trim()
        if (folded.isEmpty()) return null
        val compact = compactName(folded)
        return submitAction(compact)
            ?: contactAction(folded)
            ?: payWordAction(compact)
            ?: strongAction(folded)
            ?: prefixAction(compact)
            ?: punctuationAction(compact)
            ?: bookingAction(compact)
            ?: englishAction(folded)
            ?: genericAction(compact, screen, hit)
    }

    // ------------------------------------------------------------------ 分派

    private fun interactNeedsCheck(args: JsonObject): Boolean = when (argString(args, "action")) {
        "key" -> parsedCombo(args) != null
        // 拖动按起点和终点、下拉选择按下拉框和选中的那一项(页面可能一改就提交)
        "drag", "select" -> true
        // 点页面弹框的「确定」可能就是提交(确认删除、确认付款):按弹框的字判;取消不判
        "dialog" -> argString(args, "answer") == "accept"
        else -> false
    }

    private fun judgeTyping(args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot?): Irreversible? =
        (if (isPointTarget(args)) judgeTap(args, snapshot, fresh) else null)
            ?: if (argTrue(args, "pressEnter")) judgeEnter(args, snapshot, fresh ?: snapshot, KEY_ENTER) else null

    private fun judgeInteract(args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot?): Irreversible? =
        when (argString(args, "action")) {
            "key" -> judgeCombo(args, snapshot, fresh ?: snapshot)
            "select" -> judgeSelect(args, snapshot, fresh)
            "dialog" -> judgeDialog(fresh ?: snapshot)
            else -> judgeDrag(args, snapshot, fresh)
        }

    // ------------------------------------------------------------------ 点击

    private fun judgeTap(args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot?): Irreversible? {
        val aims = tapCandidates(args, snapshot, fresh)
        // 点的是输入框或它的清空键:编辑,不看模型写的 target(「清空搜索框」)
        if (aims.isNotEmpty() && aims.all { editsField(it.element, it.screen) }) return null
        aims.firstNotNullOfOrNull { judgeElement(it.element, it.screen) }?.let { return it }
        // 坐标点上没有带名字的元素(自绘界面、没名字的图标,看图找到的点常这样):按同一行的文字判
        if (isRowFallback(args, aims)) {
            val screen = fresh ?: snapshot
            val y = argInt(args, "y")
            if (screen != null && y != null) judgeRow(screen, y)?.let { return it }
        }
        // 元素找不到、或是没有文字的图标时,模型自己写的 target 也算数;
        // 看图找到的点另带找的原文(text)和模型看到的字(seen),一样判
        return spokenHints(args, fresh ?: snapshot)
    }

    private fun isRowFallback(args: JsonObject, aims: List<Aim>): Boolean =
        argString(args, "ref") == null &&
            aims.all { elementWithTextChildren(it.element, it.screen).all { child -> child.name.isBlank() } }

    private fun spokenHints(args: JsonObject, screen: ScreenSnapshot?): Irreversible? =
        listOf("target", "text", "seen").firstNotNullOfOrNull { key ->
            argString(args, key)?.let { word -> matchName(word, screen)?.let { Irreversible(it, word.trim()) } }
        }

    /**
     * 点击作用的元素(连同它所在的那一屏):ref → 原读屏里的元素,加上此刻它中心位置上的元素;
     * 坐标 → 此刻那一点上最小的可点元素。
     */
    private fun tapCandidates(args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot?): List<Aim> {
        val ref = argString(args, "ref")
        if (ref != null) {
            val read = snapshot?.takeIf { it.snapshotId == argString(args, "snapshotId") } ?: return emptyList()
            val target = read.elements.firstOrNull { it.ref == ref } ?: return emptyList()
            val moved = fresh?.let { now ->
                centerOf(target)?.let { (x, y) -> deepestAt(now, x, y) }?.let { Aim(it, now) }
            }
            return listOfNotNull(Aim(target, read), moved)
        }
        val screen = fresh ?: snapshot ?: return emptyList()
        val x = argInt(args, "x") ?: return emptyList()
        val y = argInt(args, "y") ?: return emptyList()
        return listOfNotNull(deepestAt(screen, x, y)?.let { Aim(it, screen) })
    }

    private fun judgeRow(screen: ScreenSnapshot, y: Int): Irreversible? =
        elementsOnRow(screen, y)
            .filterNot { editsField(it, screen) }
            .firstNotNullOfOrNull { e -> matchName(e.name, screen, e)?.let { Irreversible(it, e.name.trim()) } }

    /** 纵向跨过 [y] 的矮元素(一行里的文字、按钮)。 */
    private fun elementsOnRow(screen: ScreenSnapshot, y: Int): List<UiElement> = screen.elements.filter { e ->
        e.name.isNotBlank() && !e.editable && e.bounds.size == 4 && y in e.bounds[1]..e.bounds[3] && heightOf(e) <= ROW_MAX_HEIGHT
    }

    private fun judgeElement(e: UiElement, screen: ScreenSnapshot): Irreversible? {
        for (candidate in elementWithTextChildren(e, screen)) {
            if (editsField(candidate, screen)) continue
            matchName(candidate.name, screen, candidate)?.let { return Irreversible(it, candidate.name.trim()) }
            candidate.resourceId?.let { id -> actionFromResourceId(id)?.let { return Irreversible(it, id) } }
        }
        return null
    }

    /** 没有名字的可点容器连同落在它里面、不可点的文字子元素一起判;不可点的空白节点(如整屏的根)不展开。 */
    private fun elementWithTextChildren(e: UiElement, screen: ScreenSnapshot): List<UiElement> {
        if (e.name.isNotBlank() || !e.clickable) return listOf(e)
        val labels = screen.elements.filter { it !== e && !it.clickable && it.name.isNotBlank() && insideBounds(it, e) }
        return listOf(e) + labels
    }

    private fun deepestAt(screen: ScreenSnapshot, x: Int, y: Int): UiElement? {
        val under = screen.elements.filter { containsPoint(it, x, y) }
        return under.filter { it.clickable }.minByOrNull(::areaOf) ?: under.minByOrNull(::areaOf)
    }

    /** 确定页面弹框:弹框的字按按钮名判(通用的「确定」看同屏上下文),再按整句里的强词判。 */
    private fun judgeDialog(screen: ScreenSnapshot?): Irreversible? {
        val message = screen?.web?.dialog?.message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val action = matchName(message, screen)
            ?: message.split(DIALOG_SEPARATORS).firstNotNullOfOrNull { strongAction(it) }
            ?: return null
        return Irreversible(action, "dialog:${message.take(40)}")
    }

    /** 下拉选择:下拉框按点击判,选中的那一项的文字按按钮名判。 */
    private fun judgeSelect(args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot?): Irreversible? {
        judgeTap(args, snapshot, fresh)?.let { return it }
        val option = argString(args, "option") ?: return null
        return matchName(option, fresh ?: snapshot)?.let { Irreversible(it, "option:${option.trim()}") }
    }

    /** 拖动的起点、终点各按一次点击来判(编号共用 snapshotId)。 */
    private fun judgeDrag(args: JsonObject, snapshot: ScreenSnapshot?, fresh: ScreenSnapshot?): Irreversible? {
        fun endpoint(refKey: String, xKey: String, yKey: String): JsonObject = buildJsonObject {
            val ref = argString(args, refKey)
            if (ref != null) {
                put("ref", ref)
                argString(args, "snapshotId")?.let { put("snapshotId", it) }
            } else {
                args[xKey]?.let { put("x", it) }
                args[yKey]?.let { put("y", it) }
            }
        }
        val passed = listOf(endpoint("ref", "x", "y"), endpoint("toRef", "toX", "toY"))
            .firstNotNullOfOrNull { judgeTap(it, snapshot, fresh) } ?: return null
        return passed.copy(matched = "drag:${passed.matched}")
    }

    // ------------------------------------------------------------------ 按键

    /**
     * 回车、确认键、空格作用在焦点上:焦点是输入框时,除非明确是搜索框,一律按发送算(空格只是打字,不算);
     * 焦点是按钮时按词表判;看不出焦点在哪时,屏上的输入框全是搜索框才放行,否则算命中。
     * type_text 给了 ref 时,作用的就是那个输入框。输入法的「搜索」回车键类型读不到,不作依据。
     */
    private fun judgeEnter(args: JsonObject, snapshot: ScreenSnapshot?, current: ScreenSnapshot?, key: Int): Irreversible? {
        val keyName = if (key == KEY_SPACE) "空格" else "回车"
        val namedRef = argString(args, "ref")
        val referenced = if (namedRef == null) {
            null
        } else {
            snapshot?.takeIf { it.snapshotId == argString(args, "snapshotId") }?.elements?.firstOrNull { it.ref == namedRef }
        }
        val focus = referenced ?: current?.elements?.firstOrNull { it.focused }
        if (focus != null) {
            if (focus.editable) {
                if (key == KEY_SPACE || isSearchField(focus, current ?: snapshot)) return null
                return Irreversible(IrrevAction.send, "$keyName:${displayLabel(focus)}")
            }
            return (current ?: snapshot)?.let { judgeElement(focus, it) }
        }
        val fields = current?.elements?.filter { it.editable }.orEmpty()
        if (fields.isNotEmpty() && fields.all { isSearchField(it, current) }) return null
        return Irreversible(IrrevAction.send, "$keyName:焦点不明")
    }

    /**
     * 组合键按它能对焦点做什么来判:
     * 回车、空格(带不带修饰键)会激活焦点,按 press_key 的回车/空格规则判(和点它一样,输入框里的空格是打字);
     * 方向键、Home/End、翻页会改选中项,只在焦点是选择类控件时判,按控件和它能选到的项判(同 select);
     * 焦点不在输入框时,页面可能把删除、退格、单个字母数字、Ctrl/Alt/Meta+字母数字当命令:焦点在元素上按点它判
     * (选择类控件上的字母是按首字跳选,按控件和可选项判);焦点在页面本身(没有焦点元素)时删除、退格一律拦,
     * 其余命令键只在页面声明了它是哪个控件的快捷键(aria-keyshortcuts、accesskey)时按那个控件判;
     * Tab、Escape 等只挪焦点或关闭,输入框里的键是编辑,都不判。
     */
    private fun judgeCombo(args: JsonObject, snapshot: ScreenSnapshot?, current: ScreenSnapshot?): Irreversible? {
        val combo = parsedCombo(args)!!
        val code = combo.key.androidKeyCode
        if (code == KEY_ENTER || code == KEY_SPACE) return judgeEnter(args, snapshot, current, code)
        val screen = current ?: snapshot ?: return null
        val focus = screen.elements.firstOrNull { it.focused }
        if (focus?.editable == true) return null
        // 选择控件上改选中项的键(方向键、按首字跳选)按控件和它能选到的项判
        if (code in SELECTION_KEYS) {
            val control = focus?.takeIf { it.role in SELECTION_ROLES } ?: return null
            return selectionHit(control, screen)
        }
        val deleting = code == KEY_DEL || code == KEY_FORWARD_DEL
        val producesText = combo.key.text != null && combo.key.text != " "
        if (!deleting && !producesText) return null
        val typeAhead = producesText && combo.modifiers.all { it == KeyModifier.Shift }
        if (focus != null) {
            val own = if (typeAhead && focus.role in SELECTION_ROLES) selectionHit(focus, screen) else judgeElement(focus, screen)
            return own ?: judgeShortcutTarget(combo, screen)
        }
        if (deleting) return Irreversible(IrrevAction.delete, "${combo.key.key}:焦点不明")
        return judgeShortcutTarget(combo, screen)
    }

    /** 选择类控件和它能选到的项:控件里面的选项,焦点本身是选项时还有同类的其它选项(单选组、菜单)。 */
    private fun selectionCandidates(control: UiElement, screen: ScreenSnapshot): List<UiElement> {
        val inside = screen.elements.filter { it !== control && it.role in OPTION_ROLES && insideBounds(it, control) }
        val peers = if (control.role in OPTION_ROLES) {
            screen.elements.filter { it !== control && it.role == control.role }
        } else {
            emptyList()
        }
        return listOf(control) + inside + peers
    }

    private fun selectionHit(control: UiElement, screen: ScreenSnapshot): Irreversible? =
        selectionCandidates(control, screen).firstNotNullOfOrNull { judgeElement(it, screen) }

    /** 页面声明了这个组合键的控件(aria-keyshortcuts、accesskey),按点它判。 */
    private fun judgeShortcutTarget(combo: KeyCombo, screen: ScreenSnapshot): Irreversible? = screen.elements
        .filter { e -> e.keyShortcuts.any { declared -> comboEquals(CdpKeys.parseCombo(declared), combo) } }
        .firstNotNullOfOrNull { judgeElement(it, screen) }

    private fun comboEquals(a: KeyCombo?, b: KeyCombo): Boolean =
        a != null && a.key.code == b.key.code && a.modifiers.toSet() == b.modifiers.toSet()

    // ------------------------------------------------------------------ 滑动

    /**
     * 横滑够长、起手点在列表行(宽的可点元素,本身不可滚动)上:很多 App 的列表这样删除一行。
     * 读屏里没有容器方向信息:只认出矮的可滚动区(轮播);整屏高的横向容器上的长滑仍会被拦(提示词里让模型改用短滑)。
     */
    private fun judgeSwipe(args: JsonObject, screenArg: ScreenSnapshot?): Irreversible? {
        val (fromX, fromY) = swipeOrigin(args) ?: return null
        val screen = screenArg ?: return null
        // 起手点在矮的可滚动区里(横向轮播一类),是翻页不是删除
        if (screen.elements.any { it.scrollable && containsPoint(it, fromX, fromY) && heightOf(it) < CAROUSEL_MAX_HEIGHT }) return null
        val row = screen.elements
            .filter { containsPoint(it, fromX, fromY) && it.clickable && !it.scrollable && widthOf(it) >= ROW_MIN_WIDTH }
            .minByOrNull(::areaOf) ?: return null
        return Irreversible(IrrevAction.delete, "swipe on ${displayLabel(row)}")
    }

    /** 够长的横滑的起点(0–999);不是横滑或不够长为 null。scroll 按分发器的换算算出手指起点。 */
    private fun swipeOrigin(args: JsonObject): Swipe? {
        if (args.containsKey("startX")) {
            val x1 = argInt(args, "startX") ?: return null
            val y1 = argInt(args, "startY") ?: return null
            val x2 = argInt(args, "endX") ?: return null
            val y2 = argInt(args, "endY") ?: return null
            val dx = abs(x2 - x1)
            return if (dx >= SWIPE_DELETE_MIN && dx > abs(y2 - y1)) Swipe(x1, y1) else null
        }
        val dir = argString(args, "direction") ?: return null
        if (dir != "left" && dir != "right") return null
        val distance = min(650, (argInt(args, "amount") ?: 3) * 100)
        if (distance < SWIPE_DELETE_MIN) return null
        val cx = argInt(args, "x") ?: 500
        val cy = argInt(args, "y") ?: 500
        val dx = if (dir == "left") distance else -distance
        return Swipe(max(50, min(949, (cx - dx / 2.0).roundToInt())), cy)
    }

    // ------------------------------------------------------------------ 输入框里的编辑

    /**
     * 改输入框里的字本身不会提交任何东西:点输入框(只是聚焦,框里的字是打进去的内容)、点框里没名字的小图标
     * (清空 ×、语音)或清空键,或紧挨着框、同一行、名字是清空一类的按钮,都是编辑。提交的是之后的回车、发送、搜索。
     */
    internal fun editsField(e: UiElement, screen: ScreenSnapshot): Boolean {
        if (e.editable) return true
        val box = fourBox(e) ?: return false
        val fields = screen.elements.filter { it.editable && fourBox(it) != null && it !== e }
        val compact = compactName(Hant.fold(e.name))
        val clears = compact in FIELD_CLEAR_WORDS || e.resourceId?.let { "clear" in splitResourceId(it) } == true
        // 框里没名字的小图标(清空 ×、语音):资源 ID 也说不出是发送一类时才算编辑;有名字的按钮(网页编辑区里的「发送」)照常判
        val namelessIcon = compact.isEmpty() && e.resourceId?.let(::actionFromResourceId) == null
        if ((clears || namelessIcon) && fields.any { insideBounds(e, it) }) return true
        if (!clears) return false
        val midY = (box[1] + box[3]) / 2
        return fields.any { field ->
            val fieldBox = fourBox(field)!!
            midY in fieldBox[1]..fieldBox[3] &&
                box[0] >= fieldBox[0] - FIELD_NEAR && box[0] <= fieldBox[2] + FIELD_NEAR
        }
    }

    /**
     * 按元素自己的语义认搜索框:端口认出的搜索框控件、角色里有 search(网页 searchbox,安卓的搜索框控件类)、
     * 提示语(打了字也还在的那份,或此刻显示的)或资源 ID 里有搜索字样,或同一行有可点的「搜索」按钮。
     * 输入框里有字时名字就是打进去的字(「帮我搜索一下」是消息),只有提示语才看名字。在搜索框里回车是查找,可以撤回,不是发送。
     */
    private fun isSearchField(e: UiElement, screen: ScreenSnapshot?): Boolean {
        // 同一行有发送一类的按钮:是发消息的框(「搜索或提问」的聊天框),提示语说搜索也照样按发送判
        if (screen != null && ElementContext.row(e, screen).any { it.clickable && !it.editable && isSendButtonName(it.name) }) return false
        if (e.searchField || "search" in e.role.lowercase()) return true
        val hints = Hant.fold(listOfNotNull(e.name.takeIf { e.hint }, e.placeholder).joinToString(" ")).lowercase()
        if (SEARCH_FIELD.any { it in hints } || e.resourceId?.let { "search" in splitResourceId(it) } == true) return true
        val current = screen ?: return false
        val box = fourBox(e) ?: return false
        return current.elements.any { button ->
            val buttonBox = fourBox(button) ?: return@any false
            button !== e && button.clickable && !button.editable &&
                compactName(Hant.fold(button.name)) in SEARCH_BUTTONS &&
                (buttonBox[1] + buttonBox[3]) / 2 in box[1]..box[3]
        }
    }

    /** 按钮上是发送一类的字(发送、发布、发表、send)。 */
    private fun isSendButtonName(name: String): Boolean {
        val compact = compactName(Hant.fold(name))
        if (compact.isEmpty()) return false
        val sendWords = PREFIX_WORDS.getValue(IrrevAction.send)
        return sendWords.any { compact.startsWith(it) && compact.length <= it.length + PREFIX_SLACK } ||
            compact == "send" || compact == "post"
    }

    // ------------------------------------------------------------------ 名字匹配

    private fun submitAction(compact: String): IrrevAction? =
        if (compact in SUBMIT_WORDS) IrrevAction.submit else null

    /** 名字是联系别人、投申请的按钮(见 [CONTACT_WORDS])。 */
    private fun contactAction(folded: String): IrrevAction? = if (isContactLabel(folded)) IrrevAction.submit else null

    private fun isContactLabel(folded: String): Boolean {
        val compact = compactName(folded).trimEnd { !it.isLetterOrDigit() }
        if (compact in APPLY_WORDS || CONTACT_WORDS.any { word -> CONTACT_PREFIXES.any { compact == it + word } }) return true
        val english = folded.lowercase().trim().trimEnd { !it.isLetterOrDigit() }.replace(WHITESPACE_RUN, " ")
        return english in CONTACT_ENGLISH
    }

    private fun payWordAction(compact: String): IrrevAction? {
        if (compact in RED_PACKET) return IrrevAction.pay
        val exact = EXACT_PAY.any { word -> EXACT_PAY_PREFIXES.any { prefix -> compact == prefix + word } }
        return if (exact) IrrevAction.pay else null
    }

    /** 短名字整体、或拼起来的名字的每一段里出现强词;先去掉付款方式一类的词。 */
    private fun strongAction(folded: String): IrrevAction? {
        for (part in listOf(folded) + folded.split(WHITESPACE_RUN)) {
            var text = part.filterNot(Char::isWhitespace).replace(PAYMENT_ADS, "")
            EXCLUDED.forEach { excluded -> text = text.replace(excluded, "") }
            if (text.isEmpty() || text.length > STRONG_MAX_LEN) continue
            STRONG.entries.firstOrNull { (word, _) -> allIndexesOf(text, word).any { countsAsAction(text, word, it) } }
                ?.let { return it.value }
        }
        return null
    }

    private fun prefixAction(compact: String): IrrevAction? {
        for ((action, words) in PREFIX_WORDS) {
            for (word in words) {
                if (compact.startsWith(word) && compact.length <= word.length + PREFIX_SLACK) return action
            }
        }
        return null
    }

    private fun punctuationAction(compact: String): IrrevAction? {
        for ((action, words) in PUNCT_WORDS) {
            for (word in words) {
                if (compact.startsWith(word) && compact.drop(word.length).none { it.isLetterOrDigit() }) return action
            }
        }
        return null
    }

    private fun bookingAction(compact: String): IrrevAction? =
        if (BOOKING_WORDS.any { word -> BOOKING_PREFIXES.any { compact == it + word } }) IrrevAction.order else null

    private fun englishAction(folded: String): IrrevAction? {
        val lower = folded.lowercase()
        for ((word, action) in ENGLISH) {
            if (lower.startsWith(word) && lower.getOrNull(word.length)?.isLetter() != true) return action
        }
        return null
    }

    /** 通用的「确认/确定/完成」:同一屏上有强词或金额时才算,算成哪种由 [actionFromContext] 定。 */
    private fun genericAction(compact: String, screen: ScreenSnapshot?, hit: UiElement?): IrrevAction? {
        if (screen == null) return null
        val bare = compact.trimEnd { !it.isLetterOrDigit() }
        return if (bare in GENERIC) actionFromContext(screen, hit) else null
    }

    private fun allIndexesOf(text: String, word: String): Sequence<Int> {
        val first = text.indexOf(word)
        if (first < 0) return emptySequence()
        return generateSequence(first) { from -> text.indexOf(word, from + 1).takeIf { it >= 0 } }
    }

    /** 这一处是动作,不是状态:前面不是「待」「已」,后面(跳过标点)不是状态词。 */
    private fun countsAsAction(text: String, word: String, at: Int): Boolean {
        if (at > 0 && text[at - 1] in STATUS_PREFIXES) return false
        val rest = text.substring(at + word.length).dropWhile { !it.isLetterOrDigit() }
        return STATUS_SUFFIXES.none { rest.startsWith(it) }
    }

    private fun actionFromResourceId(id: String): IrrevAction? =
        splitResourceId(id).firstNotNullOfOrNull { RESOURCE_WORDS[it] }

    /** 资源 ID 按 _ - . 和驼峰拆成的单词。 */
    private fun splitResourceId(id: String): List<String> =
        id.substringAfterLast('/').replace(CAMEL_BOUNDARY, "$1_$2").lowercase().split('_', '-', '.')

    // ------------------------------------------------------------------ 同屏上下文

    /**
     * 同屏(弹窗、底部面板)的上下文:有付款词就是付款;否则按第一个非下单类强词(如删除确认框);
     * 有金额且同屏有付款方式、收银台、验密一类字样(或任意长度的一段里有支付/付款)也是付款;
     * 再否则有下单词或金额,按下单(下单的同意要和屏上金额对得上,见 [ConfirmationGate]);都没有就不是。
     * 金额:合计一类总算;别的金额在可滚动区里、而被点的元素不在那个区里时不算(筛选面板的价格档、商品列表)。
     */
    private fun actionFromContext(screen: ScreenSnapshot, hit: UiElement?): IrrevAction? {
        val found = screen.elements.mapNotNull { strongAction(it.name) }
        if (IrrevAction.pay in found) return IrrevAction.pay
        found.firstOrNull { it != IrrevAction.order }?.let { return it }
        val visible = visibleElements(screen)
        val scrollables = screen.elements.filter { it.scrollable }
        val amount = visible.any { e -> carriesPayableAmount(e, visible, scrollables, hit) }
        if (amount && looksLikePaySheet(visible)) return IrrevAction.pay
        return if (IrrevAction.order in found || amount) IrrevAction.order else null
    }

    /**
     * 这个元素的金额算不算上下文:合计一类总算(金额可能拆在后面几段里,也可能还没加载出数);
     * 别的按整行拼起来认金额,可滚动区里的、而点的元素不在那个区里时不算。
     */
    private fun carriesPayableAmount(e: UiElement, visible: List<UiElement>, scrollables: List<UiElement>, hit: UiElement?): Boolean {
        if (TOTAL_LABELS.any { it in Hant.fold(e.name) }) return true
        if (!AMOUNT.containsMatchIn(rowText(e, visible))) return false
        return scrollables.none { region -> insideBounds(e, region) && hit != null && !insideBounds(hit, region) }
    }

    /**
     * 有金额的这一屏像不像付款面板:付款方式、收银台、验密一类字样,或任意长度的一段里有支付/付款
     * (先去掉「支持花呗付款」「货到付款」一类介绍);页面上有立即购买、加入购物车时不按这些判。
     * 余额、密码、指纹、零钱只在下半屏、且下半屏有确认/支付一类按钮时才算。
     */
    private fun looksLikePaySheet(visible: List<UiElement>): Boolean {
        if (visible.any { e -> ORDER_CONTEXT.any { it in Hant.fold(e.name).filterNot(Char::isWhitespace) } }) return false
        val bottomButton = visible.any { b ->
            b.clickable && topOf(b) >= BOTTOM_REGION && isPayButtonName(compactName(Hant.fold(b.name)))
        }
        return visible.any { e ->
            val text = Hant.fold(e.name).filterNot(Char::isWhitespace).replace(PAYMENT_ADS, "")
            PAY_SHEET_WORDS.any { it in text } ||
                PAY_SHEET_ENGLISH.containsMatchIn(e.name) ||
                PAY_CONTEXT_WORDS.any { word -> allIndexesOf(text, word).any { countsAsAction(text, word, it) } } ||
                (bottomButton && topOf(e) >= BOTTOM_REGION && WEAK_PAY_SHEET_WORDS.any { it in text })
        }
    }

    private fun isPayButtonName(compact: String): Boolean {
        val bare = compact.trimEnd { !it.isLetterOrDigit() }
        return bare in GENERIC || "支付" in compact || "付款" in compact
    }

    private fun visibleElements(s: ScreenSnapshot): List<UiElement> = s.elements.filter { !it.editable && !it.hint }

    /** 这个元素的文字里有没有合计一类的字。 */
    private fun hasAmountMarker(e: UiElement, visible: List<UiElement>): Boolean =
        TOTAL_LABELS.any { it in Hant.fold(e.name) } || AMOUNT.containsMatchIn(rowText(e, visible))

    /**
     * 从 [start] 起同一行、在它右边的各段按横向顺序拼起来(「实付 | ¥ | 39.90」)。
     * 同一行:两者的纵向中心都落在对方的上下边之间,整屏的大容器不会把别的行拼进来。
     */
    internal fun rowText(start: UiElement, elements: List<UiElement>): String {
        val box = fourBox(start) ?: return start.name
        val midY = (box[1] + box[3]) / 2
        val row = elements.filter { other ->
            val otherBox = fourBox(other) ?: return@filter false
            other !== start && otherBox[0] >= box[0] &&
                (otherBox[1] + otherBox[3]) / 2 in box[1]..box[3] && midY in otherBox[1]..otherBox[3]
        }.sortedBy { it.bounds[0] }
        return start.name + row.joinToString("") { it.name.filterNot(Char::isWhitespace) }
    }

    // ------------------------------------------------------------------ 参数与几何

    private fun parsedCombo(args: JsonObject): KeyCombo? = argString(args, "key")?.let(CdpKeys::parseCombo)

    private fun keyCodeOf(args: JsonObject): Int? = argString(args, "key")?.let(PressKeys::resolve)

    /** 打字给的是一个点(坐标,或换成坐标之前的 @vN),不是树里的元素。 */
    private fun isPointTarget(args: JsonObject): Boolean =
        args["x"] != null || argString(args, "ref")?.let(LOCATED_REF::matches) == true

    private fun compactName(folded: String): String = folded.filterNot(Char::isWhitespace).lowercase()

    private fun displayLabel(e: UiElement): String = e.name.trim().ifEmpty { e.resourceId ?: e.ref }

    private fun fourBox(e: UiElement): List<Int>? = e.bounds.takeIf { it.size == 4 }

    private fun centerOf(e: UiElement): Pair<Int, Int>? {
        val box = fourBox(e) ?: return null
        return (box[0] + box[2]) / 2 to (box[1] + box[3]) / 2
    }

    private fun containsPoint(e: UiElement, x: Int, y: Int): Boolean {
        val box = fourBox(e) ?: return false
        return x in box[0]..box[2] && y in box[1]..box[3]
    }

    private fun insideBounds(child: UiElement, parent: UiElement): Boolean {
        val inner = fourBox(child) ?: return false
        val outer = fourBox(parent) ?: return false
        return inner[0] >= outer[0] && inner[1] >= outer[1] && inner[2] <= outer[2] && inner[3] <= outer[3]
    }

    private fun heightOf(e: UiElement): Int = fourBox(e)?.let { it[3] - it[1] } ?: 0

    private fun widthOf(e: UiElement): Int = fourBox(e)?.let { it[2] - it[0] } ?: 0

    private fun topOf(e: UiElement): Int = fourBox(e)?.get(1) ?: 0

    private fun areaOf(e: UiElement): Long =
        fourBox(e)?.let { (it[2] - it[0]).toLong() * (it[3] - it[1]) } ?: Long.MAX_VALUE

    private fun argString(o: JsonObject, key: String): String? = (o[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** 和 tap 一样容忍 [a,b](取中点)。 */
    private fun argInt(o: JsonObject, key: String): Int? {
        fun asDouble(e: JsonElement?): Double? = (e as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
        val value = o[key]
        if (value is JsonArray && value.size == 2) {
            val a = asDouble(value[0])
            val b = asDouble(value[1])
            if (a != null && b != null) return ((a + b) / 2).roundToInt()
        }
        return asDouble(value)?.roundToInt()
    }

    private fun argTrue(o: JsonObject, key: String): Boolean =
        (o[key] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase() == "true"

    /** 点击作用的元素和它所在的那一屏。 */
    private data class Aim(val element: UiElement, val screen: ScreenSnapshot)

    /** 横滑的起手点(0–999)。 */
    private data class Swipe(val fromX: Int, val fromY: Int)
}

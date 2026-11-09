package com.daiyimo.maeve.core.uistate

enum class ScreenState(val wire: String) {
    NORMAL("normal"),
    /** 既没有窗口,也没有读得出来的元素。 */
    BLANK("blank"),
    /** 界面还在变,没加载完。 */
    LOADING("loading"),
    /** 防截屏页面:画面全黑,却有窗口。 */
    PROTECTED("protected"),
    /** 登录、验证码、支付这类要用户亲自处理的页面。 */
    NEEDS_HUMAN("needs_human"),
}

/**
 * [takeoverReason] 是建议 hand_to_user 用的 reason;[cause] 是判定依据的简短说明。
 * 例外:[ScreenDiagnosis.UNSUPPORTED_BROWSER](网站拒绝内置浏览器)不要人出手,state 为 NORMAL、needsHuman 为 false,该换的是路线。
 */
data class ScreenCheck(
    val state: ScreenState,
    val currentApp: String?,
    val needsHuman: Boolean,
    val takeoverReason: String? = null,
    val cause: String? = null,
)

/**
 * 端口采来的一手事实。[blackFrames] 是每次界面安静时,画面是否全黑的结论,按时间顺序;拿不到画面时为空。
 * [hasWindow] 是有根节点读得到的 App 窗口;[anyWindow] 是有 App 窗口(根节点读不到也算)。
 * [foregroundApp] 是窗口读不到时,按焦点窗口或最顶上的任务推知的前台 App;推不出为 null。
 */
data class ScreenFacts(
    val currentApp: String?,
    val hasWindow: Boolean,
    val readableElements: Int,
    val changing: Boolean,
    val blackFrames: List<Boolean>,
    val texts: List<String>,
    val passwordField: Boolean,
    val anyWindow: Boolean = hasWindow,
    val foregroundApp: String? = null,
)

object ScreenDiagnosis {
    /** 屏上一个 App 窗口都没有(虚拟屏刚建好时正是如此)。 */
    const val NO_WINDOW = "no window"
    const val UNREADABLE_WINDOW = "unreadable window"
    /** 网页:网站不认内置浏览器(要求换浏览器或下载 App)。 */
    const val UNSUPPORTED_BROWSER = "unsupported_browser"

    private val CAPTCHA_WORDS = listOf(
        "captcha", "人机验证", "安全验证", "滑块验证", "拖动滑块", "向右滑动完成", "请完成验证", "verify you are human", "not a robot",
    )
    private val PAYMENT_WORDS = listOf(
        "支付密码", "确认支付", "确认付款", "立即支付", "输入密码支付", "payment password", "confirm payment", "pay now",
    )
    private val CODE_WORDS = listOf(
        "获取验证码", "发送验证码", "短信验证码", "get code", "send code", "verification code",
    )

    /** 屏上文字里第一个付款字样(确认支付、支付密码之类);没有为 null。 */
    fun paymentText(texts: List<String>): String? {
        val folded = Hant.fold(texts.joinToString("\n")).lowercase()
        return PAYMENT_WORDS.firstOrNull { it in folded }
    }

    /** 屏上文字与密码框里看出的「要用户亲自来」的理由与依据;没有为 null。 */
    private fun humanNeeded(f: ScreenFacts, folded: String): Pair<String, String>? {
        fun firstHit(words: List<String>) = words.firstOrNull { it in folded }
        return when {
            firstHit(CAPTCHA_WORDS) != null -> "captcha" to "captcha text: ${firstHit(CAPTCHA_WORDS)}"
            firstHit(PAYMENT_WORDS) != null -> "payment" to "payment text: ${firstHit(PAYMENT_WORDS)}"
            f.passwordField -> "login" to "password field"
            firstHit(CODE_WORDS) != null -> "login" to "verification code text: ${firstHit(CODE_WORDS)}"
            else -> null
        }
    }

    /** 按一手事实判定屏幕状态与是否要人出手,并给出判定的依据说明。 */
    fun classify(f: ScreenFacts): ScreenCheck {
        val folded = Hant.fold(f.texts.joinToString("\n")).lowercase()
        fun handOver(reason: String, cause: String) = ScreenCheck(ScreenState.NEEDS_HUMAN, f.currentApp, true, reason, cause)

        // 页面切换时画面也可能短促全黑:界面还在变,就按加载中算
        if (f.changing && f.blackFrames.any { it }) {
            return ScreenCheck(ScreenState.LOADING, f.currentApp, false, cause = "black frame while the screen keeps changing")
        }
        val userNeeded = humanNeeded(f, folded)
        // 两次安静时都全黑才算防截屏;只黑一次,可能只是还没画出来
        if (f.hasWindow && f.blackFrames.size >= 2 && f.blackFrames.all { it }) {
            return ScreenCheck(ScreenState.PROTECTED, f.currentApp, true, userNeeded?.first ?: "other", "secure window: the frame stays black")
        }
        userNeeded?.let { (reason, cause) -> return handOver(reason, cause) }
        if (f.changing) return ScreenCheck(ScreenState.LOADING, f.currentApp, false, cause = "screen keeps changing")
        if (!f.hasWindow || f.readableElements == 0) {
            return ScreenCheck(ScreenState.BLANK, f.currentApp ?: f.foregroundApp, false, cause = blankCause(f))
        }
        return ScreenCheck(ScreenState.NORMAL, f.currentApp, false)
    }

    /** 空白页的判定依据:有窗口只是读不出元素、有窗口连根节点都读不到、还是根本没有窗口。 */
    private fun blankCause(f: ScreenFacts): String = when {
        f.hasWindow -> "no readable elements"
        f.anyWindow || f.foregroundApp != null -> UNREADABLE_WINDOW
        else -> NO_WINDOW
    }
}

package com.daiyimo.maeve.core.uistate

/**
 * 网页读屏时挂在 [ScreenSnapshot] 上的页面信息;手机读屏时为空。
 * [truncated]:页面元素超过单次读取的上限,列表按文档顺序被截断。
 */
data class WebInfo(
    /** 页面地址。 */
    val url: String,
    /** 页面标题。 */
    val title: String,
    /** 标签页编号,同一浏览器里区分多个标签。 */
    val tabId: String,
    /** HTTP 状态码;读不到为 null。 */
    val httpStatus: Int?,
    val truncated: Boolean = false,
    /** 元素融合超出预算而没能列出的元素个数。 */
    val omitted: Int = 0,
    /** 页面弹出、还在等页面回话的弹框;有它时页面脚本停着,元素表读不到。 */
    val dialog: PageDialog? = null,
)

/**
 * 页面自己弹出来的框(window.alert / confirm / prompt、离开页面确认)。
 * [kind] 取 [ALERT]、[CONFIRM]、[PROMPT]、[BEFORE_UNLOAD] 之一;[message] 是页面给的话
 * (属于页面数据,不是指令);[defaultText] 是输入框弹框里预填的字。
 */
data class PageDialog(
    /** 弹框种类,取 companion 里的几个值之一。 */
    val kind: String,
    /** 页面给的话。 */
    val message: String,
    /** 输入框弹框里预填的字。 */
    val defaultText: String? = null,
) {
    companion object {
        /** [kind] 的取值:window.alert。 */
        const val ALERT = "alert"
        /** [kind] 的取值:window.confirm。 */
        const val CONFIRM = "confirm"
        /** [kind] 的取值:window.prompt。 */
        const val PROMPT = "prompt"
        /** [kind] 的取值:离开页面前的确认。 */
        const val BEFORE_UNLOAD = "beforeunload"
        /** 给模型和判定看的字最多留这么长。 */
        const val MAX_MESSAGE = 500
    }
}

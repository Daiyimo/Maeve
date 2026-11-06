package com.daiyimo.maeve.intercept

/**
 * amoo 聊天「用户发了什么」检测状态机:纯逻辑,不碰 Android 类,方便单测。
 *
 * 判定链路(三层闸门,任一不过就不算一次可截获的发送):
 * 1. 前台是 amoo([isAmooChat]),className 含 "chat"——挡住 amoo 里别的页面(设置、搜索等);
 * 2. 输入框出现过非空草稿且足够新([pendingDraft] 的 maxAgeMs)——挡住很久以前随手打过的字;
 * 3. 同一文本在 [markIntercepted] 之后 [wasIntercepted] 窗口内不再算——同一条气泡会触发
 *    几十次窗口事件,不记这一次就一锅端了。
 *
 * 草稿来自扫描时读到的「聚焦可编辑节点文本」([noteInputText]),不跟踪 TYPE_VIEW_TEXT_CHANGED:
 * 那个事件对聊天列表里的 TextView 同样会发(气泡上场),拿它当草稿会把 AI 的回复也截进来。
 */
class AmooSendDetector(private val now: () -> Long = System::currentTimeMillis) {

    /** 一条草稿的寿命:超过这么久还没出现同文本的气泡,就当用户删稿/放弃,不再截获。 */
    data class Foreground(val pkg: String?, val className: String?)

    private var foreground: Foreground? = null
    private var draft: String? = null
    private var draftAtMs = 0L

    /** 最近截获过的文本 -> 时间;同文本在 [DEDUP_WINDOW_MS] 内不重复截获。 */
    private val intercepted = ArrayDeque<Pair<String, Long>>()

    val inAmooChat: Boolean get() = isAmooChat(foreground)

    fun onWindowStateChanged(pkg: String?, className: String?) {
        foreground = Foreground(pkg, className)
    }

    /**
     * 服务刚连上时主动同步:无障碍服务被系统断开重连是常事(StepOSFreeze ~20s 一次),
     * 断开期间的窗口切换事件全丢,不主动补一笔就永远等不到下一次切换。
     * className 未知传 null,由 [isAmooChat] 的包名兜底。
     */
    fun noteAmooForeground() {
        // 已经知道是 amoo 的具体页面(比如设置页、搜索页)就不要改成「包名兜底」:
        // 那会让 isAmooChat 对 amoo 的任何页面都返回 true,闸门 1 名存实亡。
        // 只在完全不知道前台、或前台还是别的 App 时补记(服务重连后就是这种情形)。
        val fg = foreground
        if (fg != null && fg.pkg == AMOO_PACKAGE && fg.className != null) return
        foreground = Foreground(AMOO_PACKAGE, null)
    }

    /** 扫描时读到输入框文本;只在 amoo 聊天前台时记(调用方一般已闸过一次,这里双保险)。 */
    fun noteInputText(text: String?) {
        if (!inAmooChat) return
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return
        if (t != draft) {
            draft = t
            draftAtMs = now()
        }
    }

    /** 当前可拿去认领气泡的草稿;没有、太旧、或已认领过同一文本时返回 null。 */
    fun pendingDraft(maxAgeMs: Long = MAX_DRAFT_AGE_MS): String? {
        val d = draft ?: return null
        if (now() - draftAtMs > maxAgeMs) return null
        if (wasIntercepted(d)) return null
        return d
    }

    /** 记下这条文本已截获;[wasIntercepted] 在去重窗口内对它返回 true。 */
    fun markIntercepted(text: String) {
        val at = now()
        intercepted.addLast(text to at)
        while (intercepted.size > MAX_REMEMBER) intercepted.removeFirst()
    }

    fun wasIntercepted(text: String, withinMs: Long = DEDUP_WINDOW_MS): Boolean {
        val cutoff = now() - withinMs
        return intercepted.any { (t, at) -> t == text && at >= cutoff }
    }

    /** 关掉截获时清状态。离开 amoo 不清:残留草稿靠 [MAX_DRAFT_AGE_MS] 过期,免得 amoo 内部弹窗把状态冲掉反而漏截。 */
    fun reset() {
        foreground = null
        draft = null
        draftAtMs = 0L
        intercepted.clear()
    }

    companion object {
        const val AMOO_PACKAGE = "com.stepos.ailauncher"

        /** 草稿超过 10 秒还没变成气泡,就当用户没发。 */
        const val MAX_DRAFT_AGE_MS = 10_000L

        /** 同一文本截获后 60 秒内不重复截(用户重发同样的话要等这个窗口过去)。 */
        const val DEDUP_WINDOW_MS = 60_000L

        private const val MAX_REMEMBER = 32

        /** 前台是不是 amoo 的聊天页;className 为空时按是聊天页算(闸门 3 会兜底)。 */
        fun isAmooChat(fg: Foreground?): Boolean {
            if (fg?.pkg != AMOO_PACKAGE) return false
            val cls = fg.className ?: return true
            return cls.contains("chat", ignoreCase = true)
        }
    }
}

package com.daiyimo.maeve.core.userscreen

import com.daiyimo.maeve.core.observation.Untrusted
import com.daiyimo.maeve.core.prompt.Feedback
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 用户屏幕相关文字的出口。截图画面本身仍走附件那条路(是文件),这里只管怎么说:
 * 发送时把 App、时刻和附言说成一句;正文列出屏上的字和位置,屏上的字按不可信内容处理;
 * 旧轮次只留一行摘要,要细节时按编号回看。
 */
object UserScreenPrompt {
    /** 旧轮次摘要里保留的屏上字数。 */
    const val GIST_CHARS = 40

    /** 时刻一律按英文月份写,不随系统语言变。 */
    private const val TIME_FORMAT = "MMM d HH:mm"
    private const val ELLIPSIS = "…"

    /** 屏上太短的碎片(单个字)不放进摘要。 */
    private const val MIN_GIST_PIECE = 2

    private val clock = DateTimeFormatter.ofPattern(TIME_FORMAT, Locale.ENGLISH)

    fun time(at: Long, zone: ZoneId): String = clock.format(Instant.ofEpochMilli(at).atZone(zone))

    /** 用户带着这一屏说话:[screen] 为 null 表示截图已经失效,只剩编号。 */
    fun said(id: String, screen: UserScreen?, words: String, zone: ZoneId): String {
        if (screen == null) {
            return joinNonBlank(Feedback.format("user_screen.missing", "id" to id), words)
        }
        return Feedback.format(
            sentenceKey(screen, words),
            "app" to screen.app,
            "time" to time(screen.takenAt, zone),
            "words" to words,
        )
    }

    /** 从四种说法里挑一种:页面禁止截图、有没有附言各自分岔。 */
    private fun sentenceKey(screen: UserScreen, words: String): String = when {
        screen.blocked && words.isEmpty() -> "user_screen.blocked"
        screen.blocked -> "user_screen.blocked_with_words"
        words.isEmpty() -> "user_screen.sent"
        else -> "user_screen.sent_with_words"
    }

    private fun joinNonBlank(first: String, second: String): String =
        listOf(first, second).filter(String::isNotBlank).joinToString("\n")

    /**
     * 这一屏的正文:编号、App、时刻、屏上的字。屏上的字由 App 或别人写,因此套上不可信标记;
     * [noWords] 时补上「只有屏幕、先问一句」的规矩,[askVision] 时给出问看图模型的办法。
     * 页面禁止截图时只说看不到、该怎么回。
     */
    fun detail(screen: UserScreen, noWords: Boolean, askVision: Boolean, zone: ZoneId): String {
        if (screen.blocked) return Feedback["user_screen.blocked_reply"]
        val text = if (screen.lines.isEmpty()) {
            Feedback["user_screen.no_text"]
        } else {
            Untrusted.wrap(screen.lines.joinToString("\n") { "(${it.x},${it.y}) ${it.text}" })
        }
        val head = Feedback.format(
            "user_screen.detail",
            "id" to screen.id,
            "app" to screen.app,
            "package" to packageNote(screen),
            "time" to time(screen.takenAt, zone),
            "lines" to text,
        )
        val notes = ArrayList<String>(2)
        if (askVision) notes += Feedback["user_screen.ask_vision"]
        if (noWords) notes += Feedback["user_screen.no_words"]
        return (listOf(head) + notes).joinToString("\n\n")
    }

    /** 包名和显示名不同时,在 App 后面补上包名。 */
    private fun packageNote(screen: UserScreen): String {
        val packageName = screen.packageName ?: return ""
        return if (packageName == screen.app) "" else "($packageName)"
    }

    /** 旧轮次里的一屏压成一行;截图没了、禁止截图、没读到字各有各的说法。 */
    fun earlier(id: String, screen: UserScreen?, zone: ZoneId): String {
        if (screen == null) return Feedback.format("user_screen.missing", "id" to id)
        val common = arrayOf("id" to id, "app" to screen.app, "time" to time(screen.takenAt, zone))
        if (screen.blocked) return Feedback.format("user_screen.earlier_blocked", *common)
        val opening = gist(screen) ?: return Feedback.format("user_screen.earlier_blank", *common)
        return Feedback.format("user_screen.earlier", *common, "gist" to opening)
    }

    /** 屏上开头的一小段字;没有可用的字时返回 null。 */
    fun gist(screen: UserScreen): String? {
        val opening = screen.lines.asSequence()
            .map { it.text }
            .filter { it.length >= MIN_GIST_PIECE }
            .joinToString(" ")
        if (opening.isEmpty()) return null
        return if (opening.length <= GIST_CHARS) opening else opening.take(GIST_CHARS) + ELLIPSIS
    }

    /** 把旧轮次里的屏幕句压成一行:屏幕换成摘要,附言去掉换行接在后面;不带屏幕的句子原样返回。 */
    fun collapse(text: String, lookup: (String) -> UserScreen?, zone: ZoneId): String {
        val parsed = UserScreenMessage.parse(text)
        if (parsed.screens.isEmpty()) return text
        val summaries = parsed.screens.map { earlier(it, lookup(it), zone) }
        val attached = parsed.words.replace('\n', ' ')
        return (summaries + attached).filter(String::isNotBlank).joinToString(" ")
    }
}

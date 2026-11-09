package com.daiyimo.maeve.core.uistate

/**
 * 动作之后界面上新冒出来的「到上限了」一类说法:今天的次数用完了、操作太频繁、明天再来。
 * 只认动作之后才出现的短文字(提示条、弹窗、页面弹框里的话):动作前就在屏上的(说明文字、帮助页)不算,
 * 长篇大论不算;认说法不认来源,不管来自哪个网站或 App。
 */
object LimitNotice {
    /** 比对时统一去掉空白、不区分大小写。 */
    val WORDS = listOf(
        "次数已用完", "次数用完", "机会已用完", "额度已用完", "已达上限", "已达到上限", "达到上限", "超过上限", "超出上限",
        "明天再来", "明日再来", "明天再试", "明日再试", "操作过于频繁", "操作太频繁", "操作频繁", "请求过于频繁", "访问过于频繁",
        "limitreached", "reachedthelimit", "reachedyourlimit", "reachedthedailylimit", "reachedyourdailylimit", "dailylimit",
        "tryagaintomorrow", "comebacktomorrow", "toomanyrequests", "doingthattoooften",
    )

    /** 「已达到沟通上限」「超过每日投递上限」这种中间夹几个字的说法。 */
    private val REACHED = Regex("(已达|达到|超过|超出)[^，,。.!！?？]{0,6}上限")

    /** 「短文字」的字数上限(去掉空白后):提示条、弹窗里的一两句话。 */
    private const val MAX_CHARS = 120

    /** [before] 动作前的一屏(读不到为 null,这时屏上的都算新出现的);返回新出现的那句话,没有为 null。 */
    fun find(before: ScreenSnapshot?, after: ScreenSnapshot?): String? {
        after ?: return null
        val earlier = before?.let(::texts).orEmpty().map(::compact).toSet()
        return texts(after).firstOrNull { limitLike(it, earlier) }?.trim()
    }

    /** 像一句到上限的提示:够短、动作前没有、说法对得上。 */
    private fun limitLike(t: String, earlier: Set<String>): Boolean {
        val c = compact(t)
        return c.length <= MAX_CHARS && c !in earlier && (WORDS.any { it in c } || REACHED.containsMatchIn(c))
    }

    /** 一屏上可能冒出提示的文字:不是输入框、不是提示语、有名字的元素,加上网页弹框的话。 */
    private fun texts(s: ScreenSnapshot): List<String> = buildList {
        s.elements.forEach { e -> if (!e.editable && !e.hint && e.name.isNotBlank()) add(e.name) }
        s.web?.dialog?.message?.let { add(it) }
    }

    /** 比对用的样子:去掉空白、转小写。 */
    private fun compact(t: String) = t.filterNot(Char::isWhitespace).lowercase()
}

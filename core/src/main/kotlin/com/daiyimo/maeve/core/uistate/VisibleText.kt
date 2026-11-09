package com.daiyimo.maeve.core.uistate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** OCR 只补无障碍树没有的可见文字,不把图上的字误认为可点击控件。 */
internal object VisibleText {
    /** ML Kit 给了置信度、又低于这个值的行不要;没给的照留。 */
    private const val MIN_CONFIDENCE = 0.4f
    /** 以中文为主的屏上,不含中文、字母数字不超过这么多个的零碎串才可能是认错的。 */
    private const val FRAGMENT = 4

    /**
     * 去掉认错的行:置信度低的;一个字母或数字也没有的;符号比字还多的;以中文为主的屏上不含中文的零碎串
     * (单个字母,或 [FRAGMENT] 个以内、字母和数字或符号混在一起的,多半是头像、图标被认成了字)。纯数字(时间、数量)和完整的短词照留。
     */
    fun legible(lines: List<VisibleTextLine>): List<VisibleTextLine> {
        val letters = lines.flatMap { line -> line.text.filter(Char::isLetter).toList() }
        val cjkScreen = letters.isNotEmpty() && mostlyCjk(letters)
        return lines.filterNot { noise(it, cjkScreen) }
    }

    /** 字母里中日常用字占一半以上,这一屏就算以中文为主。 */
    private fun mostlyCjk(letters: List<Char>): Boolean = letters.count(::cjk) * 2 >= letters.size

    /** 这一行是不是认错的零碎。 */
    private fun noise(line: VisibleTextLine, cjkScreen: Boolean): Boolean {
        if (line.confidence != null && line.confidence < MIN_CONFIDENCE) return true
        val text = line.text.filterNot(Char::isWhitespace)
        val word = text.filter(Char::isLetterOrDigit)
        if (word.isEmpty() || text.length - word.length > word.length) return true
        if (!cjkScreen || word.any(::cjk) || word.all(Char::isDigit)) return false
        return word.length == 1 || word.length <= FRAGMENT && (word.any(Char::isDigit) || text.length > word.length)
    }

    /** 中日韩文字:汉字、平假名、片假名、谚文都算。 */
    private fun cjk(c: Char) = when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
        else -> false
    }

    /**
     * 无障碍树里还没有的那些可见文字:和元素表对得上、落在打码区里、或是重复的行都不要,
     * 按从上到下(再从左到右)排;一屏里重复的行只留文字最长的那个。
     */
    fun missing(screen: ScreenSnapshot, recognized: List<VisibleTextLine>): List<VisibleTextLine> {
        val secret = secretBounds(screen)
        val known = screen.elements.filter { !it.secret && it.name.isNotBlank() }
        val unique = mutableListOf<VisibleTextLine>()
        for (line in recognized.sortedWith(compareBy({ it.bounds.getOrElse(1) { 0 } }, { it.bounds.getOrElse(0) { 0 } }))) {
            val value = alnum(line.text)
            if (value.isEmpty() || !validLine(line)) continue
            if (secret.any { cover(line.bounds, it) >= 0.3 }) continue
            if (known.any { cover(line.bounds, it.bounds) >= 0.5 && alike(value, alnum(it.name)) }) continue
            val duplicate = unique.indexOfFirst { cover(line.bounds, it.bounds) >= 0.65 && alike(value, alnum(it.text)) }
            if (duplicate >= 0) keepLonger(unique, duplicate, line, value) else unique += line
        }
        return unique
    }

    /** 重复的行只留文字更长的那个。 */
    private fun keepLonger(unique: MutableList<VisibleTextLine>, at: Int, line: VisibleTextLine, value: String) {
        if (value.length > alnum(unique[at].text).length) unique[at] = line
    }

    /** 一行得是四个数、右边在下边之上,才算一块能用的区域。 */
    private fun validLine(line: VisibleTextLine): Boolean =
        line.bounds.size == 4 && line.bounds[2] > line.bounds[0] && line.bounds[3] > line.bounds[1]

    /** 打码区的框:元素表里标了 secret 的,加上各窗口节点树里标了的。 */
    private fun secretBounds(screen: ScreenSnapshot): List<List<Int>> {
        val out = screen.elements.filter { it.secret }.map { it.bounds }.toMutableList()
        for (window in screen.windows) collectSecret(window.nodes, out)
        return out
    }

    /** 把节点树里标了 secret 的框都收进来。 */
    private fun collectSecret(nodes: List<UiTreeNode>, out: MutableList<List<Int>>) {
        for (node in nodes) {
            if (node.secret) out += node.bounds
            collectSecret(node.children, out)
        }
    }

    /** 给模型看的 OCR 行数组:文字、边界、中心点(0–999),标明来自 OCR。 */
    fun json(lines: List<VisibleTextLine>): JsonArray = buildJsonArray {
        for (line in lines) add(buildJsonObject {
            put("text", line.text)
            put("bounds", JsonArray(line.bounds.map(::JsonPrimitive)))
            // center 与 tap 的 x/y 同一套 0–999 坐标,模型不必自己换算
            put("center", JsonArray(listOf(JsonPrimitive((line.bounds[0] + line.bounds[2]) / 2), JsonPrimitive((line.bounds[1] + line.bounds[3]) / 2))))
            put("source", "ocr")
        })
    }

    /** 比对用的样子:只要字母和数字,转小写。 */
    private fun alnum(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    /** 两串字算一样:完全相同,或长的那串以短的开头(至少三个字符)。 */
    private fun alike(a: String, b: String) = a == b || a.length >= 3 && b.contains(a) || b.length >= 3 && a.contains(b)

    /** 交集占 [a] 本身面积的比例;大容器节点不会靠面积相等掩盖行文字。 */
    private fun cover(a: List<Int>, b: List<Int>): Double {
        if (b.size != 4) return 0.0
        val width = (minOf(a[2], b[2]) - maxOf(a[0], b[0])).coerceAtLeast(0)
        val height = (minOf(a[3], b[3]) - maxOf(a[1], b[1])).coerceAtLeast(0)
        val area = (a[2] - a[0]).toLong() * (a[3] - a[1])
        return if (area > 0) width.toLong() * height / area.toDouble() else 0.0
    }
}

package com.daiyimo.maeve.core.userscreen

import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.TreeAdequacy
import com.daiyimo.maeve.core.uistate.TreeQuality
import com.daiyimo.maeve.core.uistate.UiElement
import com.daiyimo.maeve.core.uistate.VisibleText
import com.daiyimo.maeve.core.uistate.VisibleTextLine
import com.daiyimo.maeve.core.uistate.normalize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 用户屏幕上的字有两个来源:先读前台 App 的无障碍树;树不够用时(判断与任务读屏同一套,[TreeAdequacy])
 * 再补画面识别(OCR),只补树里没有的行并丢掉明显认错的。每行都带中心坐标(0–999),
 * 看不到图的模型也能判断谁在左边、谁在右边(聊天里对方和自己的气泡)。
 */
object UserScreenText {
    /** 一屏最多保留的行数、每行最多字符数:信息够用,又不至于把上下文撑大。 */
    const val MAX_LINES = 120
    const val MAX_CHARS = 200

    /** 同一行的纵向容差(0–999):中心高度相差不到这个值就算同一行,再按横向排序。 */
    private const val SAME_ROW = 12

    /** 去重时把坐标按这个大小分格,相邻像素上的同一句只留一条。 */
    private const val SAME_CELL = 20

    /** 树明显不够用、又不是防截屏页面时,需要再识别一遍画面上的字。 */
    fun needsOcr(s: ScreenSnapshot): Boolean {
        val quality = TreeAdequacy.ofTree(s).quality
        return quality != TreeQuality.Adequate && quality != TreeQuality.Protected
    }

    /**
     * 按从上到下、同高度从左到右给出屏上的字:树里有名字的元素(密码框不算)与 [ocr] 里树外的行合并,
     * 识别结果只保留纵向在 [top]–[bottom](0–999)之间的行,状态栏和导航条上的时间、电量不要。
     * 第二个返回值表示最终结果里是否用到了识别出来的字。
     */
    fun lines(
        s: ScreenSnapshot,
        ocr: List<VisibleTextLine>?,
        top: Int = 0,
        bottom: Int = 999,
    ): Pair<List<ScreenLine>, Boolean> {
        val named = s.elements.filter { !it.secret && it.name.isNotBlank() }
        val fromTree = named
            .filterNot { joinsOnlyChildren(it, named) }
            .map { ScreenLine(clip(it.name), normalize(it.centerX, s.width), normalize(it.centerY, s.height)) }
        val fromOcr = recognizedLines(s, ocr, top, bottom)
        val merged = (fromTree + fromOcr)
            .distinctBy { cell(it) }
            .sortedWith(compareBy({ it.y / SAME_ROW }, { it.x }))
            .take(MAX_LINES)
        return merged to fromOcr.any { merged.contains(it) }
    }

    /** 识别行里树中没有的那些;坐标已是 0–999,直接取框的中心。 */
    private fun recognizedLines(s: ScreenSnapshot, ocr: List<VisibleTextLine>?, top: Int, bottom: Int): List<ScreenLine> {
        val kept = ocr?.let { VisibleText.missing(s, VisibleText.legible(it)) }.orEmpty()
        return kept.map { line ->
            val bounds = line.bounds
            ScreenLine(clip(line.text), (bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2)
        }.filter { it.y in top..bottom }
    }

    /**
     * [e] 是否只是把框内那几段字串起来(整行设置项、整块消息):去掉这些子串后不剩字母或数字。
     * 是的话留里面各段(各有各的位置),名字里有自己的字时仍留 [e]。
     */
    private fun joinsOnlyChildren(e: UiElement, all: List<UiElement>): Boolean {
        val pieces = all.filter { it !== e && boxContains(e.bounds, it.bounds) && boxArea(it.bounds) < boxArea(e.bounds) }
        if (pieces.isEmpty()) return false
        val stripped = pieces.map { it.name.trim() }
            .sortedByDescending { it.length }
            .fold(e.name) { rest, piece -> rest.replace(piece, "") }
        return stripped.none(Char::isLetterOrDigit)
    }

    private fun boxContains(outer: List<Int>, inner: List<Int>): Boolean =
        inner.size == 4 && outer.size == 4 &&
            inner[0] >= outer[0] && inner[1] >= outer[1] && inner[2] <= outer[2] && inner[3] <= outer[3]

    private fun boxArea(bounds: List<Int>): Long =
        if (bounds.size == 4) (bounds[2] - bounds[0]).toLong() * (bounds[3] - bounds[1]) else 0L

    /** 去重用的格子:同一句、同一个坐标格只留一条。 */
    private data class Cell(val text: String, val column: Int, val row: Int)

    private fun cell(line: ScreenLine): Cell = Cell(line.text, line.x / SAME_CELL, line.y / SAME_CELL)

    private fun clip(text: String): String {
        val singleLine = text.replace('\n', ' ').trim()
        return if (singleLine.length <= MAX_CHARS) singleLine else singleLine.take(MAX_CHARS) + "…"
    }
}

/** 一屏的说明与截图一起存成 JSON;解析不了时返回 null。 */
object UserScreenJson {
    fun encode(s: UserScreen): String = buildJsonObject {
        put("id", s.id)
        put("takenAt", s.takenAt)
        s.packageName?.let { put("package", it) }
        s.appLabel?.let { put("label", it) }
        if (s.blocked) put("blocked", true)
        if (s.fromOcr) put("ocr", true)
        put("lines", buildJsonArray { s.lines.forEach { add(lineJson(it)) } })
    }.toString()

    private fun lineJson(line: ScreenLine): JsonObject = buildJsonObject {
        put("t", line.text)
        put("x", line.x)
        put("y", line.y)
    }

    fun decode(text: String): UserScreen? = runCatching {
        val root = Json.parseToJsonElement(text) as JsonObject
        UserScreen(
            id = root.stringValue("id") ?: return null,
            takenAt = root.longValue("takenAt") ?: return null,
            packageName = root.stringValue("package"),
            appLabel = root.stringValue("label"),
            blocked = root.flagValue("blocked"),
            lines = root.screenLines(),
            fromOcr = root.flagValue("ocr"),
        )
    }.getOrNull()

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.longValue(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.intValue(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.flagValue(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.screenLines(): List<ScreenLine> =
        (this["lines"] as? JsonArray).orEmpty().mapNotNull { element ->
            val line = element as? JsonObject ?: return@mapNotNull null
            val content = line.stringValue("t") ?: return@mapNotNull null
            ScreenLine(content, line.intValue("x"), line.intValue("y"))
        }
}

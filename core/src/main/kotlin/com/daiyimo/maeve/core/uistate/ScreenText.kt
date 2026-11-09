package com.daiyimo.maeve.core.uistate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ScreenText {
    /**
     * 一屏的元素表渲染成文字:一行一个,形如 `@e3 button "搜索" (500,50)`;中心点换成与点按相同的 0–999 坐标;
     * 超出 [maxChars] 就截断,末尾注明还剩几个节点。
     */
    fun render(s: ScreenSnapshot, maxChars: Int = 6000): String {
        val lines = s.elements.filter { it.meaningful }.map { line(it, s) }
        val out = StringBuilder()
        var shown = 0
        for (l in lines) {
            if (!fits(out.length, l, lines.size - shown - 1, maxChars)) break
            out.append(l).append('\n')
            shown++
        }
        val hidden = lines.size - shown
        if (hidden > 0) out.append(footer(hidden))
        return out.toString().trimEnd('\n').let { if (it.length > maxChars) it.take(maxChars) else it }
    }

    /** 再塞一行加换行、再给页脚留好位置,会不会超出 [maxChars]。 */
    private fun fits(used: Int, l: String, remaining: Int, maxChars: Int): Boolean {
        val reserve = if (remaining > 0) footer(remaining).length + 1 else 0
        return used + l.length + 1 + reserve <= maxChars
    }

    /** 末尾那行:还有多少个节点没列出来,提示滚动或缩小范围。 */
    private fun footer(n: Int) = "…$n more nodes not shown; scroll or narrow the range"

    private fun line(e: UiElement, s: ScreenSnapshot): String {
        val name = quoted(e.name)
        return "${e.ref} ${e.role}$name${if (e.hint) " hint" else ""} (${normalize(e.centerX, s.width)},${normalize(e.centerY, s.height)})"
    }

    /** 名字不为空时才带引号;名字里的换行换成空格,免得撑破一行一个的格式。 */
    private fun quoted(raw: String) = if (raw.isBlank()) "" else " \"${raw.replace('\n', ' ')}\""
}

object ScreenJson {
    /** observe 的元素数组;center 换成与 tap 坐标相同的 0–999(像素只在端口内部用);返回数组与被截掉的元素数。 */
    fun elements(s: ScreenSnapshot, maxElements: Int): Pair<JsonArray, Int> {
        val visible = s.elements.filter { it.meaningful }
        val arr = buildJsonArray { for (e in visible.take(maxElements)) add(element(e, s)) }
        return arr to (visible.size - arr.size).coerceAtLeast(0)
    }

    /** 一个元素的 JSON:引用、角色、名字、中心点(0–999)、边界;资源 ID 和提示语有才带。 */
    private fun element(e: UiElement, s: ScreenSnapshot): JsonObject = buildJsonObject {
        put("ref", e.ref)
        put("role", e.role)
        put("name", e.name)
        put("center", buildJsonObject { put("x", normalize(e.centerX, s.width)); put("y", normalize(e.centerY, s.height)) })
        put("bounds", JsonArray(e.bounds.map { JsonPrimitive(it) }))
        e.resourceId?.let { put("resourceId", it) }
        if (e.hint) put("hint", true)
    }
}

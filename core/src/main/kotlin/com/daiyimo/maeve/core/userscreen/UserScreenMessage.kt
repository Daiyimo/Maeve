package com.daiyimo.maeve.core.userscreen

/**
 * 用户的一句话里带着他此刻的屏幕时的书写格式。
 *
 * 屏幕单独占一行，写成 `[[screen:<编号>]]`，其余各行是附言（可以没有）。
 * 这句话照常存进对话、交给任务、作为插话或回答；对话页与卡片据此画成截图气泡，
 * 提供给模型时据此换成说明（见 `UserScreenPrompt`）。
 */
object UserScreenMessage {
    private val LINE = Regex("^\\[\\[screen:([A-Za-z0-9_-]{1,64})]]$")

    /**
     * 一句话拆开后的结果。
     *
     * @property screens 里面带的屏幕编号，按出现先后排列
     * @property words 附言，已去掉首尾空白；没有附言时为空串
     */
    data class Parsed(val screens: List<String>, val words: String) {
        val hasScreen: Boolean
            get() = screens.isNotEmpty()
    }

    /** 渲染单个屏幕标记行；编号不合法时直接抛错，避免写出解析不回来的文本。 */
    fun line(id: String): String {
        val rendered = "[[screen:$id]]"
        require(LINE.matches(rendered)) { "bad screen id" }
        return rendered
    }

    /** 屏幕那一行在前，附言在后；附言为空时只有那一行。 */
    fun compose(id: String, words: String): String {
        val parts = listOf(line(id), words.trim())
        return parts.filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** 拆开一句话：屏幕行收进 [Parsed.screens]，其余行拼成 [Parsed.words]。 */
    fun parse(text: String): Parsed {
        val screens = ArrayList<String>()
        val words = ArrayList<String>()
        for (line in text.lines()) {
            val match = LINE.matchEntire(line.trim())
            if (match == null) words += line else screens += match.groupValues[1]
        }
        // 整句都没有屏幕标记时，附言就是原句本身（保留用户原本的换行）
        val rest = if (screens.isEmpty()) text.trim() else words.joinToString("\n").trim()
        return Parsed(screens, rest)
    }

    fun has(text: String): Boolean = text.lineSequence().any { LINE.matches(it.trim()) }
}

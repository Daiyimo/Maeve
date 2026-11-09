package com.daiyimo.maeve.core.uistate

/**
 * 一个元素自己的上下文:判它做的是什么时只看这些,不看整屏——
 * 同一行上的文字(附着的标签、行里的说明)。
 */
object ElementContext {
    /**
     * 和 [e] 同一行的元素:它的中心落在 [e] 的上下范围里,
     * 或 [e] 的中心落在它的上下范围里。
     */
    fun row(e: UiElement, s: ScreenSnapshot): List<UiElement> {
        if (e.bounds.size != 4) return emptyList()
        val top = e.bounds[1]
        val bottom = e.bounds[3]
        val ownMiddle = (top + bottom) / 2
        return s.elements.filter { o ->
            o !== e && o.bounds.size == 4 && sharesRow(o.bounds, top, bottom, ownMiddle)
        }
    }
}

/** 另一段文字的上下范围里,有它自己的中心或 [e] 的中心。 */
private fun sharesRow(bounds: List<Int>, top: Int, bottom: Int, ownMiddle: Int): Boolean {
    val middle = (bounds[1] + bounds[3]) / 2
    return middle in top..bottom || ownMiddle in bounds[1]..bounds[3]
}

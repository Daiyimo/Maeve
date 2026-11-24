package com.daiyimo.maeve.core.uistate

import kotlin.math.hypot

/** 稳定编号配对用的元素特征,和编号生命周期分开。 */
internal object RefLocators {
    /** 配对用的键:角色、名字、资源 ID 合起来,分隔符用 \0 隔开,免得拼撞。 */
    fun key(e: UiElement) = e.role + "\u0000" + e.name + "\u0000" + (e.resourceId ?: "")

    /** 两个元素中心点的直线距离。 */
    fun shift(a: ElementLocator, b: ElementLocator) =
        hypot((a.centerX - b.centerX).toDouble(), (a.centerY - b.centerY).toDouble())

    /**
     * 和端口读屏时一样的特征:上下文是遍历顺序里前一个有名字的元素;
     * 中心落在别的可滚动元素范围里的,算在列表里(端口快照里没有父子关系,按范围推断)。
     */
    fun locators(s: ScreenSnapshot): List<ElementLocator> {
        val panes = s.elements.filter { it.scrollable && it.bounds.size == 4 }
        var last: String? = null
        return s.elements.map { e ->
            val x = normalize(e.centerX, s.width); val y = normalize(e.centerY, s.height)
            val inList = panes.any { p -> p !== e && x in p.bounds[0]..p.bounds[2] && y in p.bounds[1]..p.bounds[3] }
            ElementLocator(e.role, e.name, e.resourceId, e.centerX, e.centerY, last, e.hint, inList)
                .also { if (e.name.isNotBlank()) last = e.name }
        }
    }
}

package com.daiyimo.maeve.core.uistate

/** 一块屏上的一个无障碍窗口;根节点读不到时窗口本身仍保留,让模型看得出有遮挡或还在加载的界面。 */
data class UiWindowTree(
    /** 窗口编号。 */
    val id: Int,
    /** 窗口类型(如 application、system)。 */
    val type: String,
    /** 窗口层级,数值越大越靠上。 */
    val layer: Int,
    /** 窗口标题;没有为 null。 */
    val title: String?,
    /** 窗口所属的包名。 */
    val packageName: String?,
    /** 是否是当前活跃的窗口。 */
    val active: Boolean,
    /** 是否持有输入焦点。 */
    val focused: Boolean,
    /** 窗口里的节点树;根节点读不到时为空。 */
    val nodes: List<UiTreeNode>,
)

/** 可见的无障碍节点;只有能从这一屏的元素表解析的节点才带 [ref]。 */
data class UiTreeNode(
    /** 角色(类名,如 Button、TextView)。 */
    val role: String,
    /** 边界,四个数:左、上、右、下(0–999)。 */
    val bounds: List<Int>,
    /** 稳定编号;对不上元素表时为 null。 */
    val ref: String? = null,
    /** 节点上的字。 */
    val text: String? = null,
    /** 无障碍描述。 */
    val description: String? = null,
    /** 输入框的提示语。 */
    val hintText: String? = null,
    /** 资源 ID。 */
    val resourceId: String? = null,
    /** 能不能点。 */
    val clickable: Boolean = false,
    /** 能不能输入。 */
    val editable: Boolean = false,
    /** 能不能滚。 */
    val scrollable: Boolean = false,
    /** 有没有勾选态。 */
    val checkable: Boolean = false,
    /** 是否已勾选。 */
    val checked: Boolean = false,
    /** 是否选中。 */
    val selected: Boolean = false,
    /** 是否可用。 */
    val enabled: Boolean = true,
    /** 是否持有输入焦点。 */
    val focused: Boolean = false,
    /** 是否是打码内容(密码一类,不读出)。 */
    val secret: Boolean = false,
    /** 看上去是滚轮选择器(一格一格转的时间、日期、地区……):用转滚轮的工具一次转到值。 */
    val wheel: Boolean = false,
    /** 子节点。 */
    val children: List<UiTreeNode> = emptyList(),
) {
    /** 稳定编号只替换模型地址;节点本身与 Android 端的引用表不动。 */
    fun withRefs(refs: Map<String, String>): UiTreeNode = copy(
        ref = ref?.let { remapped(it, refs) },
        children = children.map { it.withRefs(refs) },
    )

    /** 换上稳定编号;这个节点不在换号表里就沿用原编号。 */
    private fun remapped(ref: String, refs: Map<String, String>): String = refs[ref] ?: ref
}

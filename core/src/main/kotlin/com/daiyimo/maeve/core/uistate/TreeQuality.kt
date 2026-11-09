package com.daiyimo.maeve.core.uistate

/**
 * 这一屏的无障碍树能不能撑起模型操作;不够用时结论写进读屏结果的 treeQuality 字段。
 * [Partial]:树缺了一部分(控件大多没有名字,或屏上有不少文字树里找不到);[Unusable]:树里几乎读不到内容;
 * [Protected]:防截屏页面,画面同样看不到(照旧按 protected 处理)。
 */
internal enum class TreeQuality(val wire: String) {
    Adequate("adequate"), Partial("partial"), Unusable("unusable"), Protected("protected"),
}

/** 一次判断的结论。[quality] 为 null 表示光看树下不了结论,要再读屏上文字(OCR)来定;读不到字就一直是 null。[reason] 是不够用的原因,附画面时它就是 imageReason。 */
internal data class TreeCheck(val quality: TreeQuality?, val reason: String? = null) {
    /** 树缺东西或不能用了:模型得靠画面或屏上文字来补。 */
    val poor: Boolean get() = quality == TreeQuality.Partial || quality == TreeQuality.Unusable
}

/**
 * 先用便宜的树判断,判不准再读屏上文字(OCR)比一比。只有树「明显够用」才不读字:有名字的元素足够多、控件大多有名字、
 * App 窗口里没有一大块什么都没有的区域(画布、地图、图片、读不到内容的网页)。其余拿不准的一律读字,宁可多读一次。
 */
internal object TreeAdequacy {
    /** 有名字的元素少于这个数就判不动:几个字的简单页面和缺了一大块的树看起来一样。 */
    private const val MIN_NAMED = 6
    /** 子树里一个文字也没有的节点占到屏幕这么大(0–1)的比例,就是一块盲区。 */
    private const val BLIND_SHARE = 0.2
    /** 盲区里落到这么多个有名字的元素,说明它只是垫在下面的底板(背景图、容器),不算盲区。 */
    private const val NAMED_ON_TOP = 3
    /** OCR 找出这么多行树里没有的文字,就认定树缺了一块。 */
    private const val MISSING_ROWS = 3
    private const val APP_WINDOW = "application"

    fun ofTree(s: ScreenSnapshot): TreeCheck {
        if (s.protected) return TreeCheck(TreeQuality.Protected)
        if (s.unreadableApp != null && s.elements.none { it.meaningful }) return TreeCheck(TreeQuality.Unusable, "unreadable_window")
        if (!s.hasWindow && s.web == null) return TreeCheck(TreeQuality.Adequate)
        if (s.elements.isEmpty()) return TreeCheck(TreeQuality.Unusable, "empty_elements")
        val named = s.elements.filter { it.name.isNotBlank() }
        val controls = s.elements.filter { it.clickable || it.editable }
        // resourceId 多是开发者内部编号,不能当作用户认得的控件名称。
        val labelled = controls.count { it.name.isNotBlank() || !it.placeholder.isNullOrBlank() }
        if (controls.isNotEmpty() && (labelled == 0 || (controls.size >= 4 && labelled * 3 <= controls.size)))
            return TreeCheck(if (named.isEmpty()) TreeQuality.Unusable else TreeQuality.Partial, "unlabeled_controls")
        if (named.isEmpty()) return TreeCheck(TreeQuality.Unusable, "no_text")
        if (named.size < MIN_NAMED || blindArea(s, named)) return TreeCheck(null)
        return TreeCheck(TreeQuality.Adequate)
    }

    /** 树判不准时,按 OCR 找出的树里没有的文字([missing],已滤掉认错的行)定结论;没读到字([missing] 为 null)照旧判不准。 */
    fun withText(tree: TreeCheck, missing: List<VisibleTextLine>?): TreeCheck {
        if (tree.quality != null || missing == null) return tree
        return if (missing.size >= MISSING_ROWS) TreeCheck(TreeQuality.Partial, "text_outside_tree") else TreeCheck(TreeQuality.Adequate)
    }

    /** App 窗口里存在盲区:一棵子树一个文字也没有、面积够大,而且不是垫在有名字的元素下面的底板。 */
    private fun blindArea(s: ScreenSnapshot, named: List<UiElement>): Boolean {
        var blind = false
        // 返回这棵子树里有没有文字;一遍走完,每个节点只看一次
        fun carries(node: UiTreeNode): Boolean {
            var words = !node.text.isNullOrBlank() || !node.description.isNullOrBlank() || !node.hintText.isNullOrBlank()
            for (child in node.children) if (carries(child)) words = true
            if (!words && !blind && area(node.bounds) >= BLIND_SHARE && named.count { inside(it.bounds, node.bounds) } < NAMED_ON_TOP) blind = true
            return words
        }
        s.windows.filter { it.type == APP_WINDOW }.forEach { w -> w.nodes.forEach { carries(it) } }
        return blind
    }

    private fun area(b: List<Int>): Double =
        if (b.size != 4) 0.0 else (b[2] - b[0]).coerceAtLeast(0).toDouble() * (b[3] - b[1]).coerceAtLeast(0) / (999.0 * 999.0)

    private fun inside(e: List<Int>, b: List<Int>): Boolean {
        if (e.size != 4 || b.size != 4) return false
        val x = (e[0] + e[2]) / 2; val y = (e[1] + e[3]) / 2
        return x in b[0]..b[2] && y in b[1]..b[3]
    }
}

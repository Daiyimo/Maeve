package com.daiyimo.maeve.core.uistate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OCR 与无障碍树的空间关联。原始 tree / visibleText 仍是依据；这里不改节点，也不推断图标的含义。
 * 只关联确实落进可操作节点里的文字；重叠控件说不清归属时，不输出关联。
 */
internal object ScreenMap {
    /** 树里的一个节点，连同它的路径、所在窗口层与面积。 */
    private data class PlacedNode(val path: String, val node: UiTreeNode, val layer: Int) {
        /** 节点边界框覆盖的面积(平方像素)。 */
        val area: Long get() = areaOf(node.bounds)
    }

    fun json(screen: ScreenSnapshot, ocr: List<VisibleTextLine>): JsonObject? {
        if (screen.web != null || screen.windows.isEmpty() || ocr.isEmpty()) return null
        val nodes = buildList<PlacedNode> {
            fun walk(path: String, node: UiTreeNode, layer: Int) {
                if (areaOf(node.bounds) > 0) add(PlacedNode(path, node, layer))
                node.children.forEachIndexed { i, child -> walk("$path/$i", child, layer) }
            }
            screen.windows.forEach { window -> window.nodes.forEachIndexed { i, node -> walk("w${window.id}/$i", node, window.layer) } }
        }
        val controls = nodes.filter { !it.node.secret && it.node.enabled && (it.node.clickable || it.node.editable || it.node.checkable) }
        if (controls.isEmpty()) return null

        val links = linkedMapOf<String, Pair<PlacedNode, MutableList<Int>>>()
        ocr.forEachIndexed { index, line ->
            // 被上层窗口遮住的下层控件，即便坐标重合，也不是这行可见文字的主人。
            val top = nodes.asSequence().filter { covers(it.node.bounds, line.bounds) }.maxOfOrNull { it.layer }
                ?: return@forEachIndexed
            val candidates = controls.filter { it.layer == top && covers(it.node.bounds, line.bounds) }
                .sortedWith(compareBy<PlacedNode> { it.area }.thenByDescending { it.path.count { c -> c == '/' } })
            val best = candidates.firstOrNull() ?: return@forEachIndexed
            // 不相关的两个控件覆盖同一行、大小又相近时，不把文字武断地挂到其中一个上。
            if (ambiguous(candidates, best)) return@forEachIndexed
            links.getOrPut(best.path) { best to mutableListOf() }.second += index
        }
        if (links.isEmpty()) return null
        return buildJsonObject {
            put("ocrToControl", buildJsonArray {
                links.values.forEach { (control, indexes) ->
                    add(buildJsonObject {
                        put("treePath", control.path)
                        control.node.ref?.let { put("ref", it) }
                        put("bounds", JsonArray(control.node.bounds.map(::JsonPrimitive)))
                        put("ocrIndexes", JsonArray(indexes.map(::JsonPrimitive)))
                    })
                }
            })
        }
    }

    /** 除 [best] 外还有谁也覆盖这一行、大小相近、又谈不上谁套着谁:这行文字的归属说不清。 */
    private fun ambiguous(candidates: List<PlacedNode>, best: PlacedNode): Boolean =
        candidates.drop(1).any { other -> other.area <= best.area * 12 / 10 &&
            !isAncestor(other.path, best.path) && !isAncestor(best.path, other.path) }

    /** [box] 是否大致装下了 [line](约九成面积)。 */
    private fun covers(box: List<Int>, line: List<Int>): Boolean {
        if (areaOf(box) == 0L || areaOf(line) == 0L) return false
        val width = (minOf(box[2], line[2]) - maxOf(box[0], line[0])).coerceAtLeast(0)
        val height = (minOf(box[3], line[3]) - maxOf(box[1], line[1])).coerceAtLeast(0)
        return width.toLong() * height * 10 >= areaOf(line) * 9
    }

    /** 由 [left,top,right,bottom] 算面积;缺边或负边都算 0。 */
    private fun areaOf(bounds: List<Int>): Long = if (bounds.size != 4) 0 else
        (bounds[2] - bounds[0]).coerceAtLeast(0).toLong() * (bounds[3] - bounds[1]).coerceAtLeast(0)

    /** [parent] 是否在树路径上是 [child] 的祖先。 */
    private fun isAncestor(parent: String, child: String) = child.startsWith("$parent/")
}

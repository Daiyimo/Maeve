package com.daiyimo.maeve.core.uistate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 给模型看的手机窗口层级:节点完整保留,只省去空字段和默认状态,省流量也让模型少读废话。
 * 路径形如「窗口号/第几个子节点/…」,同一路径的属性变化只发该节点,新增节点发完整子树。
 */
object UiTreeJson {
    /** 一屏的窗口数组,按层级从后到前;每个窗口带自己的节点树。 */
    fun windows(s: ScreenSnapshot): JsonArray = buildJsonArray {
        for (window in s.windows) {
            add(buildJsonObject {
                put("id", window.id)
                put("type", window.type)
                put("layer", window.layer)
                window.title?.let { put("title", it) }
                window.packageName?.let { put("packageName", it) }
                if (window.active) put("active", true)
                if (window.focused) put("focused", true)
                put("nodes", JsonArray(window.nodes.map(::node)))
            })
        }
    }

    /** 一个节点:自身字段见 [shallow],有子节点时再加 children。 */
    private fun node(n: UiTreeNode): JsonObject = JsonObject(shallow(n) +
        (if (n.children.isEmpty()) emptyMap() else mapOf("children" to JsonArray(n.children.map(::node)))))

    /** 节点自身的字段:空值和默认状态一律省去,只留这一屏真正有的信息。 */
    private fun shallow(n: UiTreeNode): JsonObject = buildJsonObject {
        put("role", n.role)
        n.ref?.let { put("ref", it) }
        n.text?.let { put("text", it) }
        n.description?.let { put("description", it) }
        n.hintText?.let { put("hintText", it) }
        n.resourceId?.let { put("resourceId", it) }
        put("bounds", JsonArray(n.bounds.map(::JsonPrimitive)))
        if (n.clickable) put("clickable", true)
        if (n.editable) put("editable", true)
        if (n.scrollable) put("scrollable", true)
        if (n.checkable) put("checked", n.checked)
        if (n.selected) put("selected", true)
        if (!n.enabled) put("enabled", false)
        if (n.focused) put("focused", true)
        if (n.secret) put("secret", true)
        if (n.wheel) put("wheel", true)
    }

    /**
     * 两次读屏之间窗口层级的变化。[before] 是动作前,[current] 是动作后。
     * 路径按窗口和子节点序号定位;同一路径的属性变化只发该节点,新增节点发完整子树。
     */
    fun changes(before: ScreenSnapshot, current: ScreenSnapshot): JsonObject {
        val old = index(before.windows)
        val now = index(current.windows)
        val updated = buildJsonArray {
            for ((path, n) in now) {
                val prior = old[path]
                if (prior == null && covered(path, old, now)) continue // 新父节点已经带上完整子树
                if (prior == null || shallow(prior) != shallow(n)) add(buildJsonObject {
                    put("path", path)
                    put("node", if (prior == null) node(n) else shallow(n))
                })
            }
        }
        val gone = old.keys.filterTo(LinkedHashSet()) { it !in now }
        val removed = prune(gone)
        return buildJsonObject {
            put("updated", updated)
            put("removed", removed)
        }
    }

    /** 新节点且父节点也是新的:父节点那条会带上完整子树,不用单发。 */
    private fun covered(path: String, old: Map<String, UiTreeNode>, now: Map<String, UiTreeNode>): Boolean =
        path !in old && parent(path) in now && parent(path) !in old

    /** 消失的节点里,父节点还在的才单发;整棵子树消失由父节点那条代表。 */
    private fun prune(gone: Set<String>): JsonArray = buildJsonArray {
        for (path in gone) if (parent(path) !in gone) add(JsonPrimitive(path))
    }

    /** 两屏的窗口列表是否一模一样(编号、类型、层号、标题、包名、活跃、焦点)。 */
    fun sameWindows(a: ScreenSnapshot, b: ScreenSnapshot): Boolean =
        a.windows.map(::shape) == b.windows.map(::shape)

    /** 一个窗口的身份:编号、类型、层号、标题、包名和活跃、焦点状态。 */
    private fun shape(w: UiWindowTree) = listOf(w.id, w.type, w.layer, w.title, w.packageName, w.active, w.focused)

    /** 按路径把每层节点都记一份,父路径在前;路径形如「w窗口号/第几个/…」。 */
    private fun index(windows: List<UiWindowTree>): LinkedHashMap<String, UiTreeNode> = LinkedHashMap<String, UiTreeNode>().also { out ->
        windows.forEach { walk(out, "w${it.id}", it.nodes) }
    }

    /** 递归记下这一层每个节点的路径,再往下走它的子节点。 */
    private fun walk(out: MutableMap<String, UiTreeNode>, prefix: String, nodes: List<UiTreeNode>) {
        nodes.forEachIndexed { i, node ->
            val path = "$prefix/$i"
            out[path] = node
            walk(out, path, node.children)
        }
    }

    /** 去掉最后一段,得到父节点路径。 */
    private fun parent(path: String): String = path.substringBeforeLast('/', "")
}

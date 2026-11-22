package com.daiyimo.maeve.core.uistate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.IdentityHashMap

/** 模块里的一张卡片;[title] 取卡片首个可见文本,空时才回退到聚合名。 */
data class ScreenItem(val ref: String, val title: String)

/** 一个可滚动内容区里的模块;没有局部标题时 [title] 回退到当前选中的 tab。 */
data class ScreenModule(
    val title: String,
    val scrollRef: String?,
    val items: List<ScreenItem>,
    val headingRef: String?,
) {
    val itemRefs: List<String> get() = items.map { it.ref }
}

/** 某个元素属于哪个模块;只有模块里的元素才有这份元数据。 */
data class ElementOutline(
    val section: String?,
    val sectionRef: String?,
    val scrollRef: String?,
    val item: Boolean,
)

/**
 * 屏幕的轻量模块摘要。完整树仍负责精确层级,这里只把可直接理解的内容边界提到 elements 旁边。
 * [sectionRef] 优先指向局部标题;回退到 selected tab 时指向该 tab。[item] 只为卡片根节点为 true。
 */
data class ScreenOutline(
    val modules: List<ScreenModule>,
    private val meta: Map<String, ElementOutline> = emptyMap(),
) {
    fun elementMeta(ref: String): ElementOutline? = meta[ref]

    /** 供 screen.modules 使用的紧凑 JSON;items 带标题,itemRefs 保持兼容且按文档顺序。 */
    fun json(): JsonObject = buildJsonObject {
        put("modules", JsonArray(modules.map { module ->
            buildJsonObject {
                put("title", module.title)
                module.headingRef?.let { put("headingRef", it) }
                module.scrollRef?.let { put("scrollRef", it) }
                put("items", JsonArray(module.items.map { item ->
                    buildJsonObject {
                        put("ref", item.ref)
                        put("title", item.title)
                    }
                }))
                put("itemRefs", JsonArray(module.itemRefs.map(::JsonPrimitive)))
            }
        }))
    }

    companion object {
        fun from(snapshot: ScreenSnapshot): ScreenOutline = Analyzer(snapshot).run()
    }
}

private val WHITESPACE = Regex("\\s+")
private val CONTAINER_ROLES = setOf("recyclerview", "listview", "gridview", "scrollview", "viewpager")
private const val MAX_HEADING_CHARS = 32
private const val MAX_HEADING_TEXTS = 2
private const val MAX_HEADING_NODES = 6
private const val MAX_HEADING_AREA_RATIO = 0.20
private const val MIN_CARD_AREA_RATIO = 0.20
private const val MAX_SECTION_CHARS = 80
private const val MAX_ITEM_TITLE_CHARS = 160
private const val MAX_LABEL_TEXTS = 4

private data class ContainerInfo(val node: UiTreeNode, val depth: Int)
private data class WindowInfo(val containers: List<ContainerInfo>, val selected: List<UiTreeNode>)
private data class DefaultSection(val title: String, val ref: String?)
private data class Heading(val title: String, val ref: String?)
private data class Section(
    val title: String,
    val sectionRef: String?,
    val headingRef: String?,
    val children: MutableList<UiTreeNode> = mutableListOf(),
)
private data class NodeInfo(
    val ownLabel: String,
    val firstTexts: List<String>,
    val textCount: Int,
    val nodeCount: Int,
    val refs: List<String>,
) {
    val shortLabel: String get() = ownLabel.ifBlank { firstTexts.take(MAX_HEADING_TEXTS).joinToString(" ") }
}

private class Analyzer(private val snapshot: ScreenSnapshot) {
    private val elements = snapshot.elements.associateBy { it.ref }
    private val infoCache = IdentityHashMap<UiTreeNode, NodeInfo>()
    private val order = IdentityHashMap<UiTreeNode, Int>()
    private var nextOrder = 0

    fun run(): ScreenOutline {
        val modules = mutableListOf<ScreenModule>()
        val meta = mutableMapOf<String, ElementOutline>()
        val metaDepth = mutableMapOf<String, Int>()
        for (window in windows()) {
            val fallback = selectedSection(window.selected)
            for (container in window.containers) {
                for (section in sections(container.node, fallback)) {
                    val items = section.children.mapNotNull { cardItem(it, container.node) }
                        .distinctBy { it.ref }
                    if (items.isEmpty()) continue
                    val title = section.title.take(MAX_SECTION_CHARS)
                    modules += ScreenModule(title, container.node.ref, items, section.headingRef)
                    val itemRefs = items.mapTo(mutableSetOf()) { it.ref }
                    for (child in section.children) {
                        for (ref in info(child).refs) {
                            // 外层容器先遍历;更深的内容容器有更具体的模块,可以覆盖外层归属。
                            if ((metaDepth[ref] ?: -1) > container.depth) continue
                            meta[ref] = ElementOutline(
                                section = title.takeIf { it.isNotBlank() },
                                sectionRef = section.sectionRef,
                                scrollRef = container.node.ref,
                                item = ref in itemRefs,
                            )
                            metaDepth[ref] = container.depth
                        }
                    }
                }
            }
        }
        return ScreenOutline(modules.toList(), meta.toMap())
    }

    private fun windows(): List<WindowInfo> = snapshot.windows.map { window ->
        val containers = mutableListOf<ContainerInfo>()
        val selected = mutableListOf<UiTreeNode>()
        walk(window.nodes, 0, containers, selected)
        WindowInfo(containers, selected)
    }

    private fun walk(
        nodes: List<UiTreeNode>,
        depth: Int,
        containers: MutableList<ContainerInfo>,
        selected: MutableList<UiTreeNode>,
    ) {
        for (node in nodes) {
            order[node] = nextOrder++
            if (isContainer(node)) containers += ContainerInfo(node, depth)
            if (node.selected || elements[node.ref]?.states?.contains(UiStates.SELECTED) == true) selected += node
            walk(node.children, depth + 1, containers, selected)
        }
    }

    private fun selectedSection(candidates: List<UiTreeNode>): DefaultSection {
        data class Candidate(val node: UiTreeNode, val title: String)
        val selected = candidates.mapNotNull { node ->
            if (node.editable || node.secret || node.wheel) return@mapNotNull null
            val element = node.ref?.let(elements::get)
            val title = clean(info(node).shortLabel.ifBlank { element?.name.orEmpty() })
            if (title.isBlank()) null else Candidate(node, title)
        }.minWithOrNull(
            compareBy<Candidate> { it.title.length }
                .thenBy { info(it.node).nodeCount }
                .thenBy { it.node.bounds.getOrNull(1) ?: Int.MAX_VALUE }
                .thenBy { order[it.node] ?: Int.MAX_VALUE }
        )
        return selected?.let { DefaultSection(it.title.take(MAX_SECTION_CHARS), it.node.ref ?: info(it.node).refs.firstOrNull()) }
            ?: DefaultSection("", null)
    }

    private fun sections(container: UiTreeNode, fallback: DefaultSection): List<Section> {
        val out = mutableListOf<Section>()
        var current = Section(fallback.title, fallback.ref, null)
        for (child in container.children) {
            val heading = heading(child, container)
            if (heading != null) {
                if (current.children.isNotEmpty()) out += current
                current = Section(heading.title, heading.ref, heading.ref)
            }
            current.children += child
        }
        if (current.children.isNotEmpty()) out += current
        return out
    }

    private fun heading(node: UiTreeNode, container: UiTreeNode): Heading? {
        if (isContainer(node) || node.editable || node.secret || node.wheel) return null
        val element = node.ref?.let(elements::get)
        val nodeInfo = info(node)
        if (nodeInfo.textCount !in 1..MAX_HEADING_TEXTS || nodeInfo.nodeCount > MAX_HEADING_NODES) return null
        if (areaRatio(node, container) >= MAX_HEADING_AREA_RATIO) return null
        if (isCard(node, element, nodeInfo, container)) return null
        val raw = nodeInfo.shortLabel.ifBlank { element?.name.orEmpty() }
        val title = clean(raw)
        if (title.isBlank() || title.length > MAX_HEADING_CHARS) return null
        return Heading(title, node.ref ?: nodeInfo.refs.firstOrNull())
    }

    private fun cardItem(node: UiTreeNode, container: UiTreeNode): ScreenItem? {
        if (isContainer(node)) return null
        val ref = node.ref ?: return null
        val element = elements[ref] ?: return null
        val nodeInfo = info(node)
        if (!isCard(node, element, nodeInfo, container)) return null
        val rawTitle = cardTitle(element.name, nodeInfo.firstTexts)
        return ScreenItem(ref, clean(rawTitle).take(MAX_ITEM_TITLE_CHARS))
    }

    /** 聚合名常是“类型,标题,来自作者,互动量”;取第一段像内容的文字,不把元数据当标题。 */
    private fun cardTitle(aggregate: String, texts: List<String>): String {
        val parts = aggregate.split(Regex("[,，|]")).map(::clean).filter { it.isNotEmpty() }
        if (parts.size > 1) {
            val content = parts.firstOrNull { it.length > 2 && !metaSegment(it) }
            if (content != null) return content
        }
        return texts.firstOrNull { it.length > 2 && !metaSegment(it) }
            ?: texts.firstOrNull()
            ?: aggregate
    }

    private fun metaSegment(text: String): Boolean {
        val lower = text.lowercase()
        return lower in setOf("笔记", "视频", "文章", "帖子", "商品", "note", "post", "video", "article", "product", "reel") ||
            listOf("来自", "作者", "赞", "点赞", "评论", "收藏", "分享", "使用", "posted", "author", "likes", "comments", "views").any { lower.startsWith(it) }
    }

    private fun isCard(node: UiTreeNode, element: UiElement?, nodeInfo: NodeInfo, container: UiTreeNode): Boolean {
        if (!node.clickable || node.editable || node.secret || element == null) return false
        val aggregated = element.name.isNotBlank() && clean(element.name) != nodeInfo.ownLabel
        return nodeInfo.textCount >= 2 || aggregated || areaRatio(node, container) >= MIN_CARD_AREA_RATIO
    }

    private fun info(node: UiTreeNode): NodeInfo = infoCache.getOrPut(node) {
        val children = node.children.map(::info)
        val ownLabel = listOf(node.text, node.description, node.hintText)
            .firstNotNullOfOrNull { clean(it.orEmpty()).takeIf(String::isNotBlank) }
            .orEmpty()
        val texts = buildList<String> {
            if (ownLabel.isNotEmpty()) add(ownLabel)
            for (child in children) addAll(child.firstTexts.take((MAX_LABEL_TEXTS - size).coerceAtLeast(0)))
        }
        NodeInfo(
            ownLabel = ownLabel,
            firstTexts = texts.take(MAX_LABEL_TEXTS),
            textCount = (if (ownLabel.isEmpty()) 0 else 1) + children.sumOf { it.textCount },
            nodeCount = 1 + children.sumOf { it.nodeCount },
            refs = listOfNotNull(node.ref) + children.flatMap { it.refs },
        )
    }

    private fun isContainer(node: UiTreeNode): Boolean =
        node.scrollable || node.role.substringAfterLast('.').substringAfterLast('$').lowercase() in CONTAINER_ROLES

    private fun areaRatio(node: UiTreeNode, container: UiTreeNode): Double {
        val width = (container.bounds.getOrNull(2) ?: 0) - (container.bounds.getOrNull(0) ?: 0)
        val height = (container.bounds.getOrNull(3) ?: 0) - (container.bounds.getOrNull(1) ?: 0)
        if (width <= 0 || height <= 0) return 0.0
        val nodeWidth = ((node.bounds.getOrNull(2) ?: 0) - (node.bounds.getOrNull(0) ?: 0)).coerceAtLeast(0)
        val nodeHeight = ((node.bounds.getOrNull(3) ?: 0) - (node.bounds.getOrNull(1) ?: 0)).coerceAtLeast(0)
        return nodeWidth.toDouble() * nodeHeight / (width.toDouble() * height)
    }

    private fun clean(raw: String): String = WHITESPACE.replace(raw, " ").trim()
}

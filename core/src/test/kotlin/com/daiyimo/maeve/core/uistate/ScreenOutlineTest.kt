package com.daiyimo.maeve.core.uistate

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOutlineTest {
    private fun node(
        ref: String,
        role: String,
        text: String? = null,
        bounds: List<Int> = listOf(0, 0, 999, 999),
        clickable: Boolean = false,
        scrollable: Boolean = false,
        selected: Boolean = false,
        children: List<UiTreeNode> = emptyList(),
    ) = UiTreeNode(
        role = role,
        bounds = bounds,
        ref = ref,
        text = text,
        clickable = clickable,
        scrollable = scrollable,
        selected = selected,
        children = children,
    )

    private fun card(
        ref: String,
        titleRef: String,
        detailRef: String,
        title: String,
        detail: String,
        bounds: List<Int> = listOf(0, 100, 999, 400),
    ) = node(
        ref = ref,
        role = "FrameLayout",
        clickable = true,
        bounds = bounds,
        children = listOf(
            node(titleRef, "TextView", title, listOf(20, 120, 700, 160)),
            node(detailRef, "TextView", detail, listOf(20, 180, 700, 220)),
        ),
    )

    private fun label(node: UiTreeNode): String {
        val own = listOf(node.text, node.description, node.hintText).firstOrNull { !it.isNullOrBlank() }
        return own ?: node.children.joinToString(" ") { label(it) }
    }

    private fun elementsFor(vararg roots: UiTreeNode): List<UiElement> = buildList {
        fun walk(node: UiTreeNode) {
            val ref = node.ref ?: return
            add(UiElement(
                ref = ref,
                role = node.role,
                name = label(node),
                centerX = node.bounds.getOrElse(2) { 999 } / 2,
                centerY = node.bounds.getOrElse(3) { 999 } / 2,
                bounds = node.bounds,
                resourceId = null,
                clickable = node.clickable,
                editable = node.editable,
                scrollable = node.scrollable,
                states = if (node.selected) setOf(UiStates.SELECTED) else emptySet(),
            ))
            node.children.forEach(::walk)
        }
        roots.forEach(::walk)
    }

    private fun screen(vararg roots: UiTreeNode) = ScreenSnapshot(
        snapshotId = "s1",
        packageName = "com.example",
        elements = elementsFor(*roots),
        width = 1000,
        height = 2000,
        windows = listOf(UiWindowTree(1, "application", 1, null, "com.example", true, true, roots.toList())),
    )

    @Test fun localHeadingOverridesSelectedTab() {
        val tab = node("@e1", "TextView", "笔记", listOf(0, 0, 200, 50), selected = true)
        val heading = node("@e2", "TextView", "Skill 5", listOf(0, 60, 200, 100))
        val skill = card("@e3", "@e4", "@e5", "sun-writing", "128人使用")
        val list = node("@e6", "RecyclerView", scrollable = true, children = listOf(heading, skill))

        val outline = ScreenOutline.from(screen(tab, list))
        val module = outline.modules.single()
        assertEquals("Skill 5", module.title)
        assertEquals("@e2", module.headingRef)
        assertEquals("@e6", module.scrollRef)
        assertEquals(listOf("@e3"), module.itemRefs)
        assertEquals(ScreenItem("@e3", "sun-writing"), module.items.single())
        val meta = outline.elementMeta("@e3")!!
        assertEquals("Skill 5", meta.section)
        assertEquals("@e2", meta.sectionRef)
        assertEquals("@e6", meta.scrollRef)
        assertTrue(meta.item)
        val wire = ScreenJson.elements(screen(tab, list), 10, outline).first.first { it.jsonObject["ref"]!!.jsonPrimitive.content == "@e3" }.jsonObject
        assertEquals("Skill 5", wire["section"]!!.jsonPrimitive.content)
        assertEquals("@e6", wire["scrollRef"]!!.jsonPrimitive.content)
        assertEquals("true", wire["item"]!!.jsonPrimitive.content)
        assertEquals("true", wire["clickable"]!!.jsonPrimitive.content)
        assertNull(outline.elementMeta("@e1"))
    }

    @Test fun selectedTabIsDefaultWhenThereIsNoLocalHeading() {
        val tab = node("@e1", "TextView", "作品", listOf(0, 0, 200, 50), selected = true)
        val item = card("@e2", "@e3", "@e4", "第一条", "2天前")
        val list = node("@e5", "RecyclerView", scrollable = true, children = listOf(item))
        val shot = screen(tab, list).let { s ->
            // 只保留树里的 selected,验证 elements.states 不是唯一来源。
            s.copy(elements = s.elements.map { if (it.ref == "@e1") it.copy(states = emptySet()) else it })
        }

        val outline = ScreenOutline.from(shot)
        val module = outline.modules.single()
        assertEquals("作品", module.title)
        assertNull(module.headingRef)
        assertEquals("@e5", module.scrollRef)
        assertEquals("@e1", outline.elementMeta("@e2")!!.sectionRef)
    }

    @Test fun noteAndSkillCardsBelongToDifferentSections() {
        val tab = node("@e1", "TextView", "笔记", listOf(0, 0, 200, 50), selected = true)
        val note = card("@e2", "@e3", "@e4", "最近笔记", "2天前", listOf(0, 60, 480, 500))
        val heading = node("@e5", "TextView", "Skill 5", listOf(0, 520, 200, 560))
        val skill = card("@e6", "@e7", "@e8", "human writing", "97人使用", listOf(0, 580, 999, 760))
        val list = node("@e9", "RecyclerView", scrollable = true, children = listOf(note, heading, skill))

        val outline = ScreenOutline.from(screen(tab, list))
        assertEquals(listOf("笔记", "Skill 5"), outline.modules.map { it.title })
        assertTrue(outline.modules.all { it.scrollRef == "@e9" })
        assertEquals(listOf("@e2"), outline.modules[0].itemRefs)
        assertEquals(listOf("@e6"), outline.modules[1].itemRefs)
        assertEquals("笔记", outline.elementMeta("@e2")!!.section)
        assertEquals("Skill 5", outline.elementMeta("@e6")!!.section)
        assertEquals("笔记", outline.elementMeta("@e4")!!.section)
        assertFalse(outline.elementMeta("@e4")!!.item)
        assertEquals("Skill 5", outline.elementMeta("@e7")!!.section)
        assertFalse(outline.elementMeta("@e7")!!.item)
    }

    @Test fun toolbarButtonsOutsideContainersAreNotItems() {
        val search = node("@e1", "Button", "搜索", listOf(700, 0, 999, 50), clickable = true)
        val back = node("@e2", "Button", "返回", listOf(0, 0, 100, 50), clickable = true)
        val tab = node("@e3", "TextView", "笔记", listOf(0, 60, 200, 100), selected = true)
        val item = card("@e4", "@e5", "@e6", "卡片", "3天前")
        val list = node("@e7", "RecyclerView", scrollable = true, children = listOf(item))

        val outline = ScreenOutline.from(screen(search, back, tab, list))
        assertEquals(listOf("@e4"), outline.modules.single().itemRefs)
        assertNull(outline.elementMeta("@e1"))
        assertNull(outline.elementMeta("@e2"))
    }

    @Test fun emptyTreeFallsBackToNoModules() {
        val shot = ScreenSnapshot(
            snapshotId = "s1",
            packageName = "com.example",
            elements = listOf(UiElement(
                ref = "@e1", role = "Button", name = "搜索", centerX = 500, centerY = 20,
                bounds = listOf(0, 0, 999, 50), resourceId = null,
                clickable = true, editable = false, scrollable = false,
            )),
            width = 1000,
            height = 2000,
            windows = emptyList(),
        )

        val outline = ScreenOutline.from(shot)
        assertTrue(outline.modules.isEmpty())
        assertNull(outline.elementMeta("@e1"))
        assertTrue(outline.json()["modules"]!!.jsonArray.isEmpty())
    }

    @Test fun roleBasedContainerAndLargeShortCardStillProduceItem() {
        val item = node(
            "@e1", "FrameLayout", clickable = true,
            children = listOf(node("@e2", "TextView", "只有一个标题", listOf(0, 100, 999, 200))),
        )
        val list = node("@e3", "ListView", children = listOf(item))

        val outline = ScreenOutline.from(screen(list))
        val module = outline.modules.single()
        assertEquals("@e3", module.scrollRef)
        assertEquals(listOf("@e1"), module.itemRefs)
        assertTrue(outline.elementMeta("@e1")!!.item)
    }

    @Test fun itemTitleSkipsShortLeadingBadgeText() {
        val item = node(
            "@e1", "FrameLayout", clickable = true,
            children = listOf(
                node("@e2", "TextView", "笔记", listOf(0, 100, 100, 130)),
                node("@e3", "TextView", "最近笔记", listOf(0, 140, 700, 180)),
                node("@e4", "TextView", "2天前", listOf(0, 190, 200, 220)),
            ),
        )
        val list = node("@e5", "RecyclerView", scrollable = true, children = listOf(item))

        val outline = ScreenOutline.from(screen(list))
        assertEquals(ScreenItem("@e1", "最近笔记"), outline.modules.single().items.single())
    }

    @Test fun aggregateCardNameUsesContentTitleNotMetadata() {
        val item = node(
            "@e1", "FrameLayout", "笔记,DSH 插件区也太好逛了吧,来自数字生命卡兹克,288赞，",
            clickable = true,
            children = listOf(node("@e2", "TextView", "2天前", listOf(0, 180, 200, 220))),
        )
        val list = node("@e3", "RecyclerView", scrollable = true, children = listOf(item))

        val outline = ScreenOutline.from(screen(list))
        assertEquals(ScreenItem("@e1", "DSH 插件区也太好逛了吧"), outline.modules.single().items.single())
    }

    @Test fun modulesJsonIsCompactAndKeepsDocumentOrder() {
        val tab = node("@e1", "TextView", "笔记", listOf(0, 0, 200, 50), selected = true)
        val heading = node("@e2", "TextView", "Skill 5", listOf(0, 60, 200, 100))
        val first = card("@e3", "@e4", "@e5", "first", "1人使用")
        val second = card("@e6", "@e7", "@e8", "second", "2人使用")
        val list = node("@e9", "RecyclerView", scrollable = true, children = listOf(heading, first, second))

        val module = ScreenOutline.from(screen(tab, list)).json()["modules"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("title", "headingRef", "scrollRef", "items", "itemRefs"), module.keys)
        assertEquals("Skill 5", module["title"]!!.jsonPrimitive.content)
        assertEquals("@e2", module["headingRef"]!!.jsonPrimitive.content)
        assertEquals("@e9", module["scrollRef"]!!.jsonPrimitive.content)
        assertEquals(listOf("@e3", "@e6"), module["itemRefs"]!!.jsonArray.map { it.jsonPrimitive.content })
        val items = module["items"]!!.jsonArray
        assertEquals("@e3", items[0].jsonObject["ref"]!!.jsonPrimitive.content)
        assertEquals("first", items[0].jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals("@e6", items[1].jsonObject["ref"]!!.jsonPrimitive.content)
        assertEquals("second", items[1].jsonObject["title"]!!.jsonPrimitive.content)
    }
}

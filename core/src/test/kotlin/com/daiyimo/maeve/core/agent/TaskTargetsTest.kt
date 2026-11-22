package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.tool.Verb
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTargetsTest {
    private fun modules(vararg modules: Pair<String, List<String>>) = buildJsonObject {
        put("modules", JsonArray(modules.mapIndexed { moduleIndex, (title, items) -> buildJsonObject {
            put("title", title)
            put("items", JsonArray(items.mapIndexed { itemIndex, name -> buildJsonObject {
                put("ref", "@e${moduleIndex + 1}-${itemIndex + 1}")
                put("title", name)
            } }))
        } }))
    }

    private fun ocr(text: String, y: Int, x: Int = 100, width: Int = 500, height: Int = 24) = buildJsonObject {
        put("text", text)
        put("bounds", buildJsonArray {
            add(JsonPrimitive(x)); add(JsonPrimitive(y))
            add(JsonPrimitive(x + width)); add(JsonPrimitive(y + height))
        })
        put("center", buildJsonArray {
            add(JsonPrimitive(x + width / 2)); add(JsonPrimitive(y + height / 2))
        })
        put("source", "ocr")
    }

    private fun visualLines(vararg lines: JsonObject) = buildJsonObject {
        put("visualOnly", true)
        put("visibleText", buildJsonArray { lines.forEach { add(it) } })
    }

    private fun visual(vararg lines: String): JsonObject =
        visualLines(*lines.mapIndexed { index, text -> ocr(text, 100 + index * 40) }.toTypedArray())

    private val scroll = buildJsonObject { put("direction", "down") }
    private val changed = buildJsonObject { put("ok", true); put("changed", true) }

    @Test fun profileNullIsNoOpAfterObservationAndAction() {
        val targets = TaskTargets("打开设置")
        targets.observe(modules("笔记" to listOf("第一篇笔记", "第二篇笔记")), 1)
        targets.action(Verb.Tap, buildJsonObject { put("target", "Wi-Fi") }, changed, 2)
        assertNull(targets.render())
        assertNull(targets.scrollBlockReason(scroll))
        assertNull(targets.completionNotice())
    }

    @Test fun noteCandidatesBlockScrollingUntilOpened() {
        val targets = TaskTargets("去小红书上看看 数字生命卡兹克 最近发了什么，点进去他的几篇笔记")
        targets.observe(modules(
            "笔记" to listOf("DSH 插件区也太好逛了吧", "现在的 AI belike"),
            "Skill 5" to listOf("sun-writing", "human writing"),
        ), 10)
        val state = targets.render().orEmpty()
        assertTrue(state, "DSH 插件区也太好逛了吧" in state)
        assertTrue(state, "Current section: 笔记" in state)
        assertFalse(state, "sun-writing" in state)
        assertNotNull(targets.scrollBlockReason(scroll))
        assertNotNull(targets.completionNotice())
        assertNull(targets.completionNotice())
    }

    @Test fun openedNoteIsKeptAndCountsTowardCompletion() {
        val targets = TaskTargets("阅读两篇笔记")
        targets.observe(modules("笔记" to listOf("第一篇笔记", "第二篇笔记")), 1)
        targets.action(Verb.Tap, buildJsonObject { put("target", "第一篇笔记") }, changed, 2)
        val state = targets.render().orEmpty()
        assertTrue(state, "Opened target items: 1" in state)
        assertTrue(state, "第一篇笔记" in state)
        assertNull(targets.scrollBlockReason(scroll))
        targets.action(Verb.Back, buildJsonObject { }, changed, 3)
        targets.observe(modules("笔记" to listOf("第一篇笔记", "第二篇笔记")), 4)
        assertNotNull(targets.scrollBlockReason(scroll))
    }

    @Test fun skillSectionIsNotTreatedAsNotesAndOldVisibleCandidatesAreCleared() {
        val targets = TaskTargets("看看最近发了什么，点几篇笔记")
        targets.observe(modules("笔记" to listOf("真实笔记标题")), 1)
        targets.observe(modules("Skill 5" to listOf("sun-writing", "human writing")), 2)
        val state = targets.render().orEmpty()
        assertTrue(state, "Current section: Skill 5" in state)
        assertTrue(state, "not the target module" in state)
        assertFalse(state, "Visible target candidates" in state)
        assertFalse(state, "真实笔记标题" in state)
        assertTrue(targets.scrollBlockReason(scroll)!!.contains("Go back"))
    }

    @Test fun visualSelectedTabAndRecencyKeepNoteCandidates() {
        val targets = TaskTargets("点进去他的几篇笔记看看发了什么")
        targets.observe(visualLines(
            ocr("已选定笔记", 100),
            ocr("笔记", 140),
            ocr("作品", 180),
            ocr("搜索", 220),
            ocr("DSH 插件区也太好逛了吧", 260),
            ocr("来自数字生命卡兹克", 300),
            ocr("2天前", 320),
            ocr("现在的 AI belike", 360),
            ocr("来自数字生命卡兹克", 400),
            ocr("3天前", 420),
        ), 4)
        val state = targets.render().orEmpty()
        assertTrue(state, "Current section: 笔记" in state)
        assertTrue(state, "DSH 插件区也太好逛了吧" in state)
        assertTrue(state, "现在的 AI belike" in state)
        assertNotNull(targets.scrollBlockReason(scroll))
    }

    @Test fun visualSkillHeadingOverridesStickySelectedTab() {
        val targets = TaskTargets("点进去他的几篇笔记看看发了什么")
        targets.observe(visual("已选定笔记", "笔记", "作品", "搜索", "Skill 5", "sun-writing", "128人使用"), 5)
        val state = targets.render().orEmpty()
        assertTrue(state, "Current section: Skill 5" in state)
        assertFalse(state, "Visible target candidates" in state)
        assertFalse(state, "Visible text candidates" in state)
        assertFalse(state, "sun-writing" in state)
    }

    @Test fun headerOcrDoesNotPolluteNoteCandidates() {
        val targets = TaskTargets("点进几篇笔记")
        targets.observe(visualLines(
            ocr("数字生命卡兹克", 40),
            ocr("15.4万粉丝", 80),
            ocr("希望能激发你对AI的好奇", 120),
            ocr("已选定笔记", 220),
            ocr("笔记", 250),
            ocr("作品", 280),
            ocr("DSH 插件区也太好逛了吧", 360),
            ocr("2天前", 400),
            ocr("Skill 5", 460),
            ocr("sun-writing", 500),
            ocr("128人使用", 540),
        ), 6)
        val state = targets.render().orEmpty()
        assertTrue(state, "DSH 插件区也太好逛了吧" in state)
        assertFalse(state, "数字生命卡兹克" in state)
        assertFalse(state, "15.4万粉丝" in state)
        assertFalse(state, "希望能激发你对AI的好奇" in state)
        assertFalse(state, "sun-writing" in state)
    }

    @Test fun notesAndSkillOnSameScreenPreferTargetWhileCandidatesRemain() {
        val targets = TaskTargets("阅读两篇笔记")
        val screen = modules(
            "笔记" to listOf("第一篇笔记", "第二篇笔记"),
            "Skill 5" to listOf("sun-writing"),
        )
        targets.observe(screen, 1)
        assertTrue(targets.render().orEmpty().contains("Current section: 笔记"))
        targets.action(Verb.Tap, buildJsonObject { put("ref", "@e1-1") }, changed, 2)
        targets.action(Verb.Tap, buildJsonObject { put("ref", "@e1-2") }, changed, 3)
        targets.action(Verb.Back, buildJsonObject { }, changed, 4)
        targets.observe(screen, 5)
        assertTrue(targets.render().orEmpty().contains("Current section: Skill 5"))
    }

    @Test fun descriptiveTargetWithRefUsesRealCandidateTitle() {
        val targets = TaskTargets("阅读两篇笔记")
        targets.observe(modules("笔记" to listOf("真实笔记标题", "第二篇笔记")), 1)
        targets.action(
            Verb.Tap,
            buildJsonObject { put("target", "第一个用户的头像"); put("ref", "@e1-1") },
            changed,
            2,
        )
        val state = targets.render().orEmpty()
        assertTrue(state, "Opened target items: 1" in state)
        assertTrue(state, "真实笔记标题" in state)
        assertFalse(state, "第一个用户的头像" in state)
    }

    @Test fun ordinarySearchOnlyGetsSoftState() {
        val targets = TaskTargets("搜索笔记")
        targets.observe(modules("笔记" to listOf("第一篇笔记", "第二篇笔记")), 1)
        val state = targets.render().orEmpty()
        assertTrue(state, "reference only" in state)
        assertNull(targets.scrollBlockReason(scroll))
        assertNull(targets.completionNotice())
    }

    @Test fun summarizingCurrentNoteOnlyGetsSoftState() {
        val targets = TaskTargets("总结当前这篇笔记")
        targets.observe(modules("笔记" to listOf("当前笔记", "另一篇笔记")), 1)
        assertNull(targets.scrollBlockReason(scroll))
        assertNull(targets.completionNotice())
    }

    @Test fun detailScrollIsAllowed() {
        val targets = TaskTargets("阅读两篇笔记")
        targets.observe(modules("笔记" to listOf("第一篇笔记", "第二篇笔记")), 1)
        targets.action(Verb.Tap, buildJsonObject { put("ref", "@e1-1") }, changed, 2)
        targets.observe(visualLines(ocr("第一篇笔记的正文内容", 100), ocr("2天前", 140)), 3)
        assertNull(targets.scrollBlockReason(scroll))
    }

    @Test fun backClearsVisibleCandidatesAndCurrentSection() {
        val targets = TaskTargets("阅读两篇笔记")
        targets.observe(modules("笔记" to listOf("第一篇笔记", "第二篇笔记")), 1)
        targets.action(Verb.Back, buildJsonObject { }, changed, 2)
        val state = targets.render().orEmpty()
        assertFalse(state, "Visible target candidates" in state)
        assertFalse(state, "Current section" in state)
        assertNull(targets.scrollBlockReason(scroll))
    }

    @Test fun latestOneAndChineseTensCountsAreCorrect() {
        assertTrue(TaskTargets("阅读最近一条笔记").render().orEmpty().contains("open about 1"))
        assertTrue(TaskTargets("阅读二十篇笔记").render().orEmpty().contains("open about 20"))
        assertTrue(TaskTargets("阅读二十一篇笔记").render().orEmpty().contains("open about 21"))
    }

    @Test fun softOcrHintsDoNotDriveHardGuard() {
        val targets = TaskTargets("看看有哪些笔记")
        targets.observe(visual("已选定笔记", "笔记", "作品", "搜索", "DSH 插件区也太好逛了吧", "2天前"), 1)
        assertTrue(targets.render().orEmpty().contains("DSH 插件区也太好逛了吧"))
        assertNull(targets.scrollBlockReason(scroll))
        assertNull(targets.completionNotice())
    }

    @Test fun ocrWithoutRecencyOnlyProducesSoftHints() {
        val targets = TaskTargets("点进几篇笔记")
        targets.observe(visual("已选定笔记 笔记", "搜索", "DSH 插件区也太好逛了吧"), 1)
        val state = targets.render().orEmpty()
        assertTrue(state, "Visible text candidates" in state)
        assertNull(targets.scrollBlockReason(scroll))
        assertNull(targets.completionNotice())
    }

    @Test fun realXhsGeometryKeepsSelectedNotesWhenRecencyExists() {
        val targets = TaskTargets("点进几篇笔记")
        targets.observe(visualLines(
            ocr("已选定笔记", 490, height = 20),
            ocr("DSH 插件区也太好逛了吧", 520, height = 20),
            ocr("Skill 5", 542, height = 20),
            ocr("数字生命卡兹克", 580, height = 20),
            ocr("2天前", 600, height = 20),
            ocr("现在的 AI belike", 650, height = 20),
            ocr("3天前", 700, height = 20),
        ), 1)
        val state = targets.render().orEmpty()
        assertTrue(state, "Current section: 笔记" in state)
        assertTrue(state, "DSH 插件区也太好逛了吧" in state)
        assertTrue(state, "现在的 AI belike" in state)
        assertFalse(state, "数字生命卡兹克" in state)
        assertFalse(state, "Skill 5" in state)
    }

    @Test fun realXhsGeometryUsesSkillHeadingWithoutRecency() {
        val targets = TaskTargets("点进几篇笔记")
        targets.observe(visualLines(
            ocr("已选定笔记", 490, height = 20),
            ocr("Skill 5", 542, height = 20),
            ocr("sun-writing", 580, height = 20),
            ocr("128人使用", 620, height = 20),
        ), 1)
        val state = targets.render().orEmpty()
        assertTrue(state, "Current section: Skill 5" in state)
        assertFalse(state, "Visible target candidates" in state)
        assertFalse(state, "Visible text candidates" in state)
    }

    @Test fun searchResultsWithoutSelectedTabStillRequireOpeningMoreNotes() {
        val targets = TaskTargets("去小红书上看看 数字生命卡兹克 最近发了什么")
        targets.observe(visualLines(
            ocr("数字生命卡兹克", 70),
            ocr("帖子", 360),
            ocr("DSH 插件区也太好逛了吧", 430),
            ocr("3天前", 480),
        ), 10)
        targets.action(Verb.Tap, buildJsonObject { put("target", "DSH 插件区也太好逛了吧 帖子") }, changed, 12)
        targets.observe(visualLines(ocr("DeepSeek Harness发布", 300), ocr("作者:数字生命卡兹克", 360)), 12)
        assertNotNull(targets.render(), targets.completionNotice())
    }

    @Test fun authorAndBackTapsDoNotCountAsOpenedNotes() {
        val targets = TaskTargets("去小红书上看看 数字生命卡兹克 最近发了什么")
        targets.observe(visualLines(ocr("帖子", 360), ocr("一篇笔记", 430), ocr("3天前", 480)), 1)
        targets.action(Verb.Tap, buildJsonObject { put("target", "数字生命卡兹克") }, changed, 2)
        targets.action(Verb.Tap, buildJsonObject { put("target", "左上角返回按钮") }, changed, 3)
        val state = targets.render().orEmpty()
        assertTrue(state, "Opened target items: 0" in state)
        assertFalse(state, "数字生命卡兹克" in state)
    }

    @Test fun explicitCoordinatesAreNotBlockedByTargetScrollPolicy() {
        val targets = TaskTargets("看看几篇笔记")
        targets.observe(modules("笔记" to listOf("第一篇笔记", "第二篇笔记")), 1)
        assertNull(targets.scrollBlockReason(buildJsonObject { put("x", 500); put("y", 700) }))
    }
}

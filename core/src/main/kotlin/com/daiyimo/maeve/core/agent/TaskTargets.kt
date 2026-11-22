package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.tool.Verb
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 任务目标的可执行状态:目标种类、当前屏候选、已经打开的条目、当前所在模块。
 * 内容只从工具结果与动作参数确定性地提取,不另调模型,也不读屏幕。
 * 它和用户原话分开保存,压缩旧往返时仍把最新状态固定带给模型。
 */
internal class TaskTargets(goal: String) {
    private val goal = goal
    private val profile = TargetProfile.of(goal)
    private val seenCandidates = linkedMapOf<String, TargetItem>()
    private val visibleCandidates = linkedMapOf<String, TargetItem>()
    private val opened = linkedMapOf<String, TargetItem>()
    private var visualCandidates: List<String> = emptyList()
    private var currentSection: String? = null
    private var sawTargetModule = false
    private var viewingDetail = false
    private var completionNudged = false

    data class TargetItem(
        val title: String,
        val section: String?,
        val ref: String?,
        val source: String,
        val seenAt: Int,
    )

    /** 看过一次工具结果后更新当前屏候选与当前模块;动作结果里的 screen 与顶层读屏结果都认。 */
    fun observe(content: JsonObject, step: Int) {
        val target = profile ?: return
        clearCurrentScreen()
        val screen = content["screen"] as? JsonObject ?: content
        val modules = screen["modules"] as? JsonArray
        if (modules != null) {
            observeModules(target, modules, step)
        } else {
            observeOcr(target, screen, step)
        }
    }

    /** 点击成功后把“点了什么”记入已打开条目;无变化的点击不算读到一个条目。 */
    fun action(verb: Verb?, args: JsonObject, content: JsonObject, step: Int) {
        if (profile == null) return
        if (content.bool("ok") == false) return
        val ref = args.str("ref")
        val described = args.str("target")?.clean()
            ?: args.str("text")?.clean()
            ?: args.str("element")?.clean()
        val changed = content.bool("changed") == true
        if (verb == Verb.Back || (verb == Verb.Tap && changed && described?.let { isBackLabel(it) } == true)) {
            clearCurrentScreen()
            viewingDetail = false
            return
        }
        if (verb != Verb.Tap || !changed) return
        val known = ref?.let { value ->
            visibleCandidates.values.firstOrNull { it.ref == value }
                ?: seenCandidates.values.firstOrNull { it.ref == value }
        } ?: described?.let { value ->
            visibleCandidates.values.firstOrNull { it.title.key() == value.key() }
                ?: seenCandidates.values.firstOrNull { it.title.key() == value.key() }
        }
        val named = known?.title ?: described ?: content.str("located")?.clean() ?: return
        if (!worthCandidate(named)) return
        // 点任务里点名的用户/作者只是导航,不是打开了一篇内容。
        if (named.length <= 20 && goal.contains(named, ignoreCase = true)) return
        val item = TargetItem(
            named,
            known?.section ?: currentSection,
            ref ?: known?.ref,
            known?.source ?: "action",
            step,
        )
        remember(item, visible = false)
        opened[item.title.key()] = item
        visibleCandidates.remove(item.title.key())
        viewingDetail = matches(item)
        if (viewingDetail) sawTargetModule = true
    }

    /** 当前状态接到每一步运行时上下文里;没有可执行目标时不注入。 */
    fun render(): String? {
        val target = profile ?: return null
        val out = StringBuilder()
        out.appendLine("Task target state (deterministic; keep using it after compression):")
        out.appendLine(
            if (target.hardGuards) {
                "- Target: " + target.label + "; open about " + target.desired + " item(s) when available."
            } else {
                "- Target: " + target.label + "; reference only, do not open items unless the user asked."
            },
        )
        val visible = visibleTargets()
        if (visible.isNotEmpty()) {
            out.appendLine(
                if (target.hardGuards) "- Visible target candidates not opened yet:"
                else "- Visible target candidates (reference only):",
            )
            visible.take(8).forEach {
                out.appendLine("  - " + it.title + it.sectionSuffix() + it.refSuffix())
            }
        } else if (visualCandidates.isNotEmpty()) {
            out.appendLine("- Visible text candidates (verify in the screenshot; soft hints only):")
            visualCandidates.take(8).forEach { out.appendLine("  - " + it) }
        }
        val read = opened.values.filter(::matches).takeLast(8)
        out.appendLine("- Opened target items: " + read.size)
        read.forEach { out.appendLine("  - " + it.title + it.sectionSuffix()) }
        val other = opened.values.filterNot(::matches).takeLast(4)
        if (other.isNotEmpty()) {
            out.appendLine("- Opened outside the target module (do not summarize these as target items):")
            other.forEach { out.appendLine("  - " + it.title + " [" + (it.section ?: "unknown section") + "]") }
        }
        currentSection?.let { out.appendLine("- Current section: " + it) }
        if (target.hardGuards && leftTargetSection()) {
            out.appendLine("- The current section is not the target module. Return to the target module before continuing.")
        }
        return out.toString().trimEnd()
    }

    /** 明确要求点开/阅读目标项时,当前屏还有候选就不允许无目标滚动;详情内部滚动始终允许。 */
    fun scrollBlockReason(args: JsonObject): String? {
        val target = profile ?: return null
        if (!target.hardGuards || viewingDetail) return null
        if (args.has("ref") || args.has("x") || args.has("y")) return null
        val remaining = visibleTargets().isNotEmpty() && opened.values.count(::matches) < target.desired
        if (remaining) {
            return "Visible target candidates are already on screen. Open and read them before scrolling to another module."
        }
        if (leftTargetSection()) {
            return "The current section is '" + (currentSection ?: "unknown") + "', not the target '" + target.label + "'. Go back to the target module instead of scrolling further."
        }
        return null
    }

    /** 最终回答前只提醒一次:发现了目标候选却还没读够,不能拿别的模块冒充完成。 */
    fun completionNotice(): String? {
        val target = profile ?: return null
        if (!target.hardGuards || completionNudged) return null
        val read = opened.values.count(::matches)
        if (read >= target.desired) return null
        val pending = visibleTargets().isNotEmpty()
        if (!pending && !viewingDetail && !leftTargetSection()) return null
        completionNudged = true
        return "The task target is " + target.label + ", but only " + read + " of about " + target.desired +
            " target items have been opened. Return to or continue reading the target items before answering; do not substitute cards from other sections."
    }

    private fun observeModules(target: TargetProfile, modules: JsonArray, step: Int) {
        var targetWithRemaining: String? = null
        var lastTitle: String? = null
        for (module in modules) {
            val obj = module as? JsonObject ?: continue
            val title = obj.str("title")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            lastTitle = title
            val isTarget = target.matches(title)
            if (isTarget) sawTargetModule = true
            val items = obj["items"] as? JsonArray ?: continue
            for (value in items) {
                val item = value as? JsonObject ?: continue
                val name = item.str("title")?.clean() ?: continue
                if (!worthCandidate(name)) continue
                val candidate = TargetItem(name, title, item.str("ref"), "module", step)
                remember(candidate, visible = true)
                if (isTarget && candidate.title.key() !in opened) targetWithRemaining = title
            }
        }
        currentSection = targetWithRemaining ?: lastTitle
        if (targetWithRemaining != null) viewingDetail = false
    }

    private fun observeOcr(target: TargetProfile, screen: JsonObject, step: Int) {
        val values = screen["visibleText"] as? JsonArray ?: return
        val lines = values.mapNotNull { value -> (value as? JsonObject)?.ocrLine() }
            .sortedWith(compareBy({ it.y }, { it.x }))
        if (lines.isEmpty()) return
        val selectedIndex = lines.indexOfFirst { it.text.contains("已选定") }
        val selected = lines.getOrNull(selectedIndex)
        val selectedLabel = selected?.let { line ->
            line.text.substringAfter("已选定").clean().split(Regex("\\s+")).distinct().joinToString(" ")
        }
        if (selectedIndex < 0 || selectedLabel == null) {
            currentSection = lines.lastOrNull { knownSection(it.text) }?.text?.clean()
            if (currentSection?.let(target::matches) == true) sawTargetModule = true
            return
        }
        val height = lines.maxOf { maxOf(it.y, it.bottom) }.coerceAtLeast(999)
        val tabRowBottom = selected.bottom + (height * 0.02).roundToInt().coerceAtLeast(12)
        val headings = lines.mapIndexedNotNull { index, line ->
            if (index > selectedIndex && line.y > tabRowBottom && knownSection(line.text)) index to line else null
        }
        val hasRecency = lines.any { isRecency(it.text) }
        val selectedIsTarget = target.matches(selectedLabel)
        val heading = headings.lastOrNull()
        val sectionTitle = if (selectedIsTarget && hasRecency) selectedLabel else heading?.second?.text?.clean() ?: selectedLabel
        val startIndex = if (selectedIsTarget && hasRecency) selectedIndex else heading?.first ?: selectedIndex
        currentSection = sectionTitle
        if (selectedIsTarget || target.matches(sectionTitle)) sawTargetModule = true
        if (!target.matches(sectionTitle)) return
        val pick = ocrPick(OcrSection(sectionTitle, startIndex, lines.size), lines.subList(startIndex + 1, lines.size), step)
        visualCandidates = pick.soft
        val visible = pick.hard.filter { it.title.key() !in opened }
        visible.forEach { remember(it, visible = true) }
        if (visible.isNotEmpty()) viewingDetail = false
    }

    private fun ocrPick(section: OcrSection, region: List<OcrLine>, step: Int): OcrPick {
        val soft = region.asSequence()
            .filter { worthOcrTitle(it.text) && !isRecency(it.text) }
            .map { it.text }
            .distinct()
            .take(12)
            .toList()
        if (region.isEmpty()) return OcrPick(section, emptyList(), soft)
        val width = region.maxOf { maxOf(it.x, it.right) }.coerceAtLeast(999)
        val height = region.maxOf { maxOf(it.y, it.bottom) }.coerceAtLeast(999)
        val horizontalLimit = (width * 0.25).roundToInt().coerceAtLeast(80)
        val verticalLimit = (height * 0.35).roundToInt().coerceAtLeast(80)
        val hard = mutableListOf<TargetItem>()
        for (time in region.filter { isRecency(it.text) }) {
            val title = region.asSequence()
                .filter { line ->
                    line.y < time.y &&
                        time.y - line.y >= 24 &&
                        time.y - line.y <= verticalLimit &&
                        line.horizontallyClose(time, horizontalLimit) &&
                        worthOcrTitle(line.text) &&
                        !isRecency(line.text)
                }
                .maxByOrNull { it.y }
                ?: continue
            hard += TargetItem(title.text.clean(), section.title, null, "ocr", step)
        }
        return OcrPick(section, hard.distinctBy { it.title.key() }, soft)
    }

    private fun visibleTargets(): List<TargetItem> =
        visibleCandidates.values.filter { matches(it) && it.title.key() !in opened }

    private fun matches(item: TargetItem): Boolean =
        item.section?.let { profile?.matches(it) == true } ?: (profile != null && item.source != "module")

    private fun leftTargetSection(): Boolean {
        if (profile == null || currentSection == null || !sawTargetModule || viewingDetail) return false
        return !profile.matches(currentSection!!)
    }

    private fun remember(item: TargetItem, visible: Boolean) {
        seenCandidates[item.title.key()] = item
        while (seenCandidates.size > MAX_ITEMS) seenCandidates.remove(seenCandidates.keys.first())
        if (visible) visibleCandidates[item.title.key()] = item
    }

    private fun clearCurrentScreen() {
        visibleCandidates.clear()
        visualCandidates = emptyList()
        currentSection = null
    }

    private data class TargetProfile(
        val label: String,
        val aliases: List<String>,
        val desired: Int,
        val hardGuards: Boolean,
    ) {
        fun matches(title: String): Boolean {
            val norm = title.norm()
            return aliases.any { norm.contains(it.norm()) }
        }

        companion object {
            fun of(goal: String): TargetProfile? {
                val g = goal.lowercase()
                val desired = count(goal)
                val hard = hardGuards(goal)
                return when {
                    "笔记" in g || Regex("\b(notes?|posts?|articles?)\b").containsMatchIn(g) ->
                        TargetProfile("notes/posts", listOf("笔记", "note", "post", "article"), desired, hard)
                    "视频" in g || "video" in g ->
                        TargetProfile("videos", listOf("视频", "video"), desired, hard)
                    "商品" in g || "product" in g ->
                        TargetProfile("products", listOf("商品", "product"), desired, hard)
                    "文章" in g || "article" in g ->
                        TargetProfile("articles", listOf("文章", "article"), desired, hard)
                    "帖子" in g || "post" in g ->
                        TargetProfile("posts", listOf("帖子", "动态", "post"), desired, hard)
                    "文件" in g || "文档" in g || "file" in g || "document" in g ->
                        TargetProfile("files/documents", listOf("文件", "文档", "file", "document"), desired, hard)
                    "邮件" in g || "email" in g ->
                        TargetProfile("emails", listOf("邮件", "email", "mail"), desired, hard)
                    "用户" in g || "联系人" in g || "profile" in g || "user" in g ->
                        TargetProfile("users/profiles", listOf("用户", "联系人", "user", "profile"), desired, hard)
                    "最近" in g && listOf("发了什么", "内容", "动态", "作品").any { it in g } ->
                        TargetProfile(
                            "recent content",
                            listOf("笔记", "动态", "帖子", "文章", "作品", "视频", "note", "post", "article", "update", "video"),
                            desired,
                            hard,
                        )
                    else -> null
                }
            }

            private fun count(goal: String): Int {
                if (Regex("(最新|最近)(的)?(一|1)(篇|条|个)?").containsMatchIn(goal) ||
                    Regex("\\b(?:latest|recent)\\s+(?:one|1)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal)
                ) return 1
                Regex("([0-9]+|[一二两三四五六七八九十百]+)\\s*[篇条个]").find(goal)?.groupValues?.get(1)?.let {
                    return it.toIntOrNull() ?: chineseNumber(it) ?: 1
                }
                Regex("\\b([0-9]+)\\s+(notes?|posts?|articles?|videos?)\\b", RegexOption.IGNORE_CASE)
                    .find(goal)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
                return if ("几" in goal || "最近" in goal || "最新" in goal || "recent" in goal.lowercase() || "latest" in goal.lowercase()) 3 else 1
            }

            private fun chineseNumber(raw: String): Int? {
                if (raw.isEmpty()) return null
                val hundred = raw.indexOf('百')
                if (hundred >= 0) {
                    val head = raw.substring(0, hundred)
                    val headDigit = head.singleOrNull()
                    val hundreds = when {
                        head.isEmpty() -> 1
                        headDigit == null -> return null
                        else -> DIGITS[headDigit] ?: return null
                    }
                    val tail = raw.substring(hundred + 1)
                    return hundreds * 100 + (if (tail.isEmpty()) 0 else chineseNumber(tail) ?: return null)
                }
                val ten = raw.indexOf('十')
                if (ten >= 0) {
                    if (raw.indexOf('十', ten + 1) >= 0) return null
                    val head = raw.substring(0, ten)
                    val headDigit = head.singleOrNull()
                    val tens = when {
                        head.isEmpty() -> 1
                        headDigit == null -> return null
                        else -> DIGITS[headDigit] ?: return null
                    }
                    val tail = raw.substring(ten + 1)
                    val tailDigit = tail.singleOrNull()
                    val ones = when {
                        tail.isEmpty() -> 0
                        tailDigit == null -> return null
                        else -> DIGITS[tailDigit] ?: return null
                    }
                    return tens * 10 + ones
                }
                return raw.map { DIGITS[it] ?: return null }.fold(0) { acc, digit -> acc * 10 + digit }
            }

            private fun hardGuards(goal: String): Boolean {
                val g = goal.lowercase()
                val recent = Regex("最近.{0,8}(发了什么|发布了什么|发布.{0,4}什么|动态|内容|作品)").containsMatchIn(g)
                val explicit = Regex("(点进|点开|阅读|读完|读一下|读读)").containsMatchIn(g)
                val soft = listOf(
                    "搜索", "查找", "列举", "列出", "列表", "总结", "概括", "当前这",
                    "search", "list", "summarize", "summarise",
                ).any { g.contains(it) }
                if (soft && !explicit && !recent) return false
                if (recent || explicit) return true
                if (Regex("(打开|开启|open)").containsMatchIn(g) &&
                    Regex("(笔记|帖子|文章|视频|商品|文件|文档|邮件|用户|联系人|notes?|posts?|articles?|videos?|products?|files?|documents?|emails?|users?|profiles?)").containsMatchIn(g)
                ) return true
                return Regex("(看|读|view).{0,6}(几|[0-9一二两三四五六七八九十百]+)[篇条个]")
                    .containsMatchIn(g)
            }

            private val DIGITS = mapOf(
                '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
                '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
            )
        }
    }

    private companion object {
        const val MAX_ITEMS = 24
        const val MAX_OCR_TITLE_CHARS = 80
        val SECTION_WORDS = listOf(
            "skill", "合集", "collection", "评论", "comment", "收藏", "favorite", "点赞", "like",
            "商品", "product", "用户", "user", "视频", "video", "文章", "article", "笔记", "note",
            "帖子", "post", "动态", "update", "作品", "work",
        )
        val CHROME = listOf(
            "返回", "搜索", "关注", "粉丝", "赞", "评论", "收藏", "分享", "已选定", "来自", "人使用",
            "天前", "小时前", "分钟前", "刚刚", "首页", "消息", "我的", "更多", "展开", "收起", ".skill",
        )
        val HEADER_WORDS = listOf(
            "头像", "群聊", "个人页", "小红书号", "获赞", "原创内容", "简介", "ip：", "ip ",
            "followers", "following", "likes", "bio",
        )
        val RECENCY = listOf(
            "天前", "小时前", "分钟前", "周前", "月前", "年前",
            "day ago", "days ago", "hour ago", "hours ago", "week ago", "month ago",
        )

        fun String.clean(): String = replace(Regex("\\s+"), " ").trim().take(120)
        fun String.norm(): String = lowercase().filter(Char::isLetterOrDigit)
        fun String.key(): String = norm()
        fun knownSection(text: String): Boolean {
            val norm = text.norm()
            if (norm.length > 24 || text.length > 32) return false
            return SECTION_WORDS.any { word ->
                val alias = word.norm()
                norm == alias || norm.startsWith(alias) && norm.removePrefix(alias).all(Char::isDigit)
            }
        }

        fun worthCandidate(text: String): Boolean {
            val norm = text.norm()
            if (norm.length !in 3..100 || text.length > 120) return false
            if (CHROME.any { text.contains(it, ignoreCase = true) }) return false
            if (knownSection(text)) return false
            return text.any(Char::isLetter)
        }

        fun worthOcrTitle(text: String): Boolean =
            text.length <= MAX_OCR_TITLE_CHARS &&
                worthCandidate(text) &&
                HEADER_WORDS.none { text.contains(it, ignoreCase = true) } &&
                !isRecency(text)

        fun isRecency(text: String): Boolean = RECENCY.any { text.contains(it, ignoreCase = true) }
        fun isBackLabel(text: String): Boolean {
            val norm = text.norm()
            return "返回" in text || norm == "back" || norm == "backbutton"
        }

        fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
        fun JsonObject.has(key: String): Boolean = this[key] != null
        fun JsonArray.intAt(index: Int): Int? =
            (getOrNull(index) as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

        fun JsonObject.ocrLine(): OcrLine? {
            val text = str("text")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val bounds = this["bounds"] as? JsonArray
            val center = this["center"] as? JsonArray
            val left = bounds?.intAt(0)
            val top = bounds?.intAt(1)
            val right = bounds?.intAt(2)
            val bottom = bounds?.intAt(3)
            val x = center?.intAt(0) ?: if (left != null && right != null) (left + right) / 2 else return null
            val y = center?.intAt(1) ?: if (top != null && bottom != null) (top + bottom) / 2 else return null
            return OcrLine(text, x, y, left ?: x, top ?: y, right ?: x, bottom ?: y)
        }
    }

    private data class OcrLine(
        val text: String,
        val x: Int,
        val y: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun horizontallyClose(other: OcrLine, limit: Int): Boolean =
            abs(x - other.x) <= limit || (left < other.right && right > other.left)
    }

    private data class OcrSection(val title: String, val start: Int, val end: Int)
    private data class OcrPick(val section: OcrSection, val hard: List<TargetItem>, val soft: List<String>)

    private fun TargetItem.sectionSuffix(): String = section?.let { " [" + it + "]" }.orEmpty()
    private fun TargetItem.refSuffix(): String = ref?.let { " " + it }.orEmpty()
}

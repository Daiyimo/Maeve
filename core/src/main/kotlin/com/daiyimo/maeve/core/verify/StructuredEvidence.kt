package com.daiyimo.maeve.core.verify

import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.ReadRegion
import java.text.Normalizer
import kotlin.math.abs

/** 逐行复核列表和表格。数值与对应条目须在同一次读取里对上。 */
internal object StructuredEvidence {
    data class Result(
        val prose: String, val matched: Int, val total: Int, val unbound: Boolean, val missing: List<String> = emptyList(),
    )

    private data class Row(val title: String, val fields: List<Field>)
    private data class Field(val label: String, val value: String)
    private data class Box(val raw: String, val left: Double, val top: Int, val right: Double, val bottom: Int) {
        val height get() = bottom - top
        val x get() = (left + right) / 2
        val y get() = (top + bottom) / 2.0

        fun span(start: Int, end: Int): Box {
            val width = right - left
            return copy(left = left + width * start / raw.length, right = left + width * end / raw.length)
        }

        fun rowWith(other: Box): Boolean =
            abs(y - other.y) <= maxOf(height, other.height) * 0.55 + 2

        fun directlyBelow(other: Box): Boolean {
            val distance = y - other.y
            return distance > maxOf(height, other.height) * 0.55 &&
                distance <= maxOf(height, other.height) * 6 &&
                x >= other.left - other.height / 2.0 && x <= other.right + other.height / 2.0
        }
    }

    private data class Numeric(val key: String, val box: Box)

    fun check(answer: String, reads: List<Observation>): Result {
        val (prose, rows) = split(answer)
        if (rows.isEmpty()) return Result(prose, 0, 0, false)

        val screens = reads.map { read -> Screen(read.regions, read.text) }
        val anchorsByRow = rows.map { row -> screens.map { it.anchors(row.title) } }
        val hasRanking = rows.size > 1 && RANKING.containsMatchIn(answer)
        val ordered = hasRanking && screens.indices.any { i ->
            val positions = anchorsByRow.map { reads -> reads[i].filter(Anchor::exact).mapNotNull { it.box?.y }.distinct() }
            positions.all { it.size == 1 } && positions.zipWithNext().all { (a, b) -> a.single() < b.single() }
        }
        var matched = if (ordered) 1 else 0
        var unbound = false
        val missing = mutableListOf<String>()
        for ((index, row) in rows.withIndex()) {
            val anchors = screens.zip(anchorsByRow[index])
            val titleMatched = anchors.any { (_, candidates) -> candidates.any(Anchor::exact) }
            if (titleMatched) matched++
            val missingFields = row.fields.filterNot { field ->
                anchors.any { (screen, candidates) -> screen.matches(field, row.fields, candidates) }
            }
            matched += row.fields.size - missingFields.size
            if (missingFields.any { field -> screens.any { field.value in it.values } }) unbound = true
            if (!titleMatched || missingFields.isNotEmpty()) {
                val title = canon(row.title)
                val labels = missingFields.map { it.label }.filter(String::isNotBlank).distinct()
                missing += if (!titleMatched || labels.isEmpty()) title else title + " (" + labels.joinToString(", ") + ")"
            }
        }
        return Result(prose, matched, rows.sumOf { it.fields.size + 1 } + if (hasRanking) 1 else 0, unbound, missing.distinct())
    }

    private data class Anchor(val box: Box?, val text: String, val exact: Boolean)

    private class Screen(regions: List<ReadRegion>, text: String) {
        private val boxes = regions.mapNotNull { region ->
            val b = region.bounds
            if (b.size != 4 || b[2] <= b[0] || b[3] <= b[1] || region.text.isBlank()) null
            else Box(canon(region.text), b[0].toDouble(), b[1], b[2].toDouble(), b[3])
        }
        private val lines = text.lineSequence()
            .filterNot { it.startsWith(Grounding.APP_HEADER) || it.startsWith("当前网页:") }
            .map(::canon).filter { it.isNotBlank() }.distinct().toList()
        private val numbers = boxes.flatMap { box ->
            VALUE.findAll(box.raw).map { Numeric(numberKey(it), box.span(it.range.first, it.range.last + 1)) }.toList()
        }
        val values = lines.flatMap { line -> VALUE.findAll(line).map(::numberKey).toList() }.toSet()

        fun anchors(title: String): List<Anchor> {
            val key = normalized(title)
            if (key.length < 2) return emptyList()
            val located = boxes.mapNotNull { box ->
                anchor(key, box.raw)?.let { (span, exact) ->
                    Anchor(box.span(span.first, span.last + 1), box.raw, exact)
                }
            }
            val locatedLines = located.map { it.text }.toSet()
            return located + lines.filterNot { it in locatedLines }.mapNotNull { line ->
                anchor(key, line)?.let { (_, exact) -> Anchor(null, line, exact) }
            }
        }

        fun matches(field: Field, fields: List<Field>, anchors: List<Anchor>): Boolean {
            val sameUnit = fields.filter { unit(it.value) == unit(field.value) }
            val headings = sameUnit.flatMap { candidate ->
                if (candidate.label.isEmpty()) emptyList()
                else boxes.filter { labelIn(candidate.label, it.raw) && VALUE.find(it.raw) == null }
                    .map { candidate.label to it }
            }
            return anchors.any { anchor ->
                val title = anchor.box
                if (title == null) {
                    // 无位置的网页、读屏只认可同一原文行里的关系。
                    val inline = VALUE.findAll(anchor.text).map(::numberKey).filter { unit(it) == unit(field.value) }.toList()
                    field.value in inline && inline.size == 1 &&
                        (field.label.isEmpty() || labelIn(field.label, anchor.text))
                } else {
                    val horizontal = numbers.filter { it.box.rowWith(title) }
                    val vertical = numbers.filter { it.box.directlyBelow(title) }
                    (horizontal + vertical).filter { it.key == field.value }.any { candidate ->
                        val comparable = if (candidate in horizontal) horizontal else vertical
                        val inBand = comparable.filter { unit(it.key) == unit(field.value) }.distinctBy { it.key }
                        val column = headings.filter { (_, header) ->
                            header.y < candidate.box.y && candidate.box.y - header.y <= header.height * 28
                        }.minByOrNull { (_, header) -> abs(header.x - candidate.box.x) }
                        val columnMatches = column?.let { (label, header) ->
                            label == field.label &&
                                abs(header.x - candidate.box.x) <= maxOf(header.height * 2.5, (header.right - header.left) / 2)
                        } == true
                        when {
                            sameUnit.size > 1 -> columnMatches
                            columnMatches -> true
                            inBand.size == 1 -> field.label.isEmpty() || lines.any { labelIn(field.label, it) }
                            else -> false
                        }
                    }
                }
            }
        }
    }

    /** 有明确截断标记时可用足够长的标题前缀定位该行，完整标题仍待核实。 */
    private fun anchor(key: String, raw: String): Pair<IntRange, Boolean>? {
        val positions = raw.indices.filter { raw[it].isLetterOrDigit() }
        val compact = normalized(raw)
        val start = compact.indexOf(key)
        if (start >= 0 && (key.length >= 4 || compact == key)) {
            return (positions[start]..positions[start + key.lastIndex]) to true
        }
        val cut = CUT.find(raw) ?: return null
        val prefix = raw.substring(0, cut.range.first).dropWhile { it.isDigit() || !it.isLetterOrDigit() }
        val short = normalized(prefix)
        if (short.length < 7 || !key.startsWith(short)) return null
        return (0 until cut.range.first) to false
    }

    private fun split(answer: String): Pair<String, List<Row>> {
        val prose = mutableListOf<String>()
        val rows = mutableListOf<Row>()
        val lines = answer.lines()
        var headers = emptyList<String>()
        var inCode = false
        var inSources = false
        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (FENCE.containsMatchIn(trimmed)) inCode = !inCode
            if (SOURCES.containsMatchIn(trimmed)) inSources = true
            val cells = cells(line)
            val next = lines.getOrNull(index + 1)?.let(::cells).orEmpty()
            if (!inCode && !inSources && cells.size >= 2 && next.size == cells.size && next.all(ALIGN::matches)) {
                headers = cells
                continue
            }
            if (headers.isNotEmpty() && cells.size == headers.size) {
                if (cells.all(ALIGN::matches)) continue
                val title = cells.indices.firstOrNull { i ->
                    !RANK.matches(normalized(headers[i])) && metric(cells[i]) == null && normalized(cells[i]).length >= 2
                }
                if (title != null) {
                    val fields = cells.indices.filter { it != title && !RANK.matches(normalized(headers[it])) }
                    rows += Row(cells[title], fields.mapNotNull { i ->
                        metric(cells[i])?.let { Field(canon(headers[i]), it) }
                    })
                    // 无法识别成单个数值的复合字段仍交给字面复核，避免直接漏掉回答中的事实。
                    fields.filter { metric(cells[it]) == null && VALUE.containsMatchIn(canon(cells[it])) }.forEach { i ->
                        prose += cells[title] + " " + headers[i] + " " + cells[i]
                    }
                    continue
                }
            }
            headers = emptyList()
            val item = if (inCode || inSources) null else LIST.matchEntire(line)?.groupValues?.get(1)
            val row = item?.let(::listRow)
            if (row == null) prose += line else rows += row
        }
        return prose.joinToString("\n") to rows.distinct()
    }

    private fun listRow(item: String): Row? {
        val bold = BOLD.find(item)
        val separator = SEPARATOR.find(item)
        val end = bold?.range?.last?.plus(1) ?: separator?.range?.first ?: item.length
        val title = bold?.groupValues?.get(1) ?: item.substring(0, end)
        val tail = canon(item.substring(end))
        if (normalized(title).length < 2 || metric(title) != null) return null
        if (bold == null && separator == null && (title.length > 80 || title.any { it in "。；：:" })) return null
        val matches = VALUE.findAll(tail).toList()
        if (matches.size > 1) return null
        val field = matches.singleOrNull()?.let {
            val labelBefore = tail.substring(0, it.range.first).substringAfterLast(')').substringAfterLast('—')
            val labelAfter = tail.substring(it.range.last + 1)
            val label = labelBefore.trim { ch -> ch.isWhitespace() || ch in "—–-:：" }.ifEmpty { labelAfter.trim() }
            Field(label, numberKey(it))
        }
        return Row(title, listOfNotNull(field))
    }

    private fun cells(line: String): List<String> =
        line.trim().takeIf { it.startsWith('|') && it.endsWith('|') }
            ?.removeSurrounding("|")?.split('|')?.map(String::trim).orEmpty()

    private fun metric(cell: String): String? = VALUE.matchEntire(canon(cell).trim())?.let(::numberKey)

    private fun numberKey(m: MatchResult): String {
        val raw = m.groupValues[1].replace(",", "")
        val number = if (raw.startsWith('0') && raw.length > 1 && raw[1].isDigit()) raw
            else raw.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: raw
        val unit = when (val suffix = m.groupValues[2].lowercase()) {
            "min", "分钟" -> "min"
            else -> suffix
        }
        return number + ":" + unit
    }

    private fun unit(key: String) = key.substringAfter(':')

    private fun labelIn(label: String, raw: String): Boolean {
        val name = normalized(label)
        val value = normalized(raw)
        return name.isNotEmpty() && (name in value || when (name) {
            "收益小时" -> "收益h" in value
            "平均时长" -> "平均对局时长" in value
            "热度" -> "热榜" in value
            else -> false
        })
    }

    private fun canon(text: String) = Normalizer.normalize(Grounding.stripRefs(text), Normalizer.Form.NFKC)
        .replace("**", "").replace("__", "").replace("`", "")

    private val TOKEN = Regex("[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*")

    private fun normalized(text: String): String {
        val keyed = TOKEN.replace(canon(text).lowercase()) { m ->
            val token = m.value
            if (token.any(Char::isLetter) && token.any(Char::isDigit)) Grounding.tokenKey(token) else token
        }
        return keyed.filter(Char::isLetterOrDigit)
    }

    private val ALIGN = Regex(":?-{2,}:?")
    private val RANK = Regex("排名|位次|名次|序号|rank|no")
    private val RANKING = Regex("热搜|热榜|榜单|排行榜|排名|(?i:rank|top[ ]+[0-9])")
    private val FENCE = Regex("^(?:```|~~~)")
    private val LIST = Regex("""^\s*(?:\d{1,3}[.)、]|[-*•])\s+(.+)$""")
    private val SEPARATOR = Regex("""\s+[—–-]\s+""")
    private val BOLD = Regex("""^\*\*(.+?)\*\*""")
    private val CUT = Regex("""\.{2,}|…""")
    private val SOURCES = Regex("""(?i)^\**(?:依据|来源|sources?|evidence)\**\s*[:：]""")
    private val VALUE = Regex("""(?<![A-Za-z0-9])(\d+(?:,\d{3})*(?:\.\d+)?)\s*(%|万|亿|分钟|min|元|秒)?(?![A-Za-z0-9.,])""", RegexOption.IGNORE_CASE)
}

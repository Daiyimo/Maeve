package com.daiyimo.maeve.core.verify

import com.daiyimo.maeve.core.observation.InputHints
import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.ReadRegion
import com.daiyimo.maeve.core.record.RunEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredEvidenceTest {
    private fun region(text: String, x: Int, y: Int, width: Int = 100) =
        ReadRegion(text, listOf(x, y, x + width, y + 16))

    private fun read(vararg regions: ReadRegion) = Observation(
        ObsKind.Read, regions.joinToString("\n") { it.text }, regions = regions.toList(),
    )

    @Test fun hotListBindsSeparatedNumbersToTheirOwnRow() {
        val screen = read(
            region("抖音热榜", 40, 100),
            region("周末登山热门路线", 45, 170, 480), region("1212.0万", 830, 172),
            region("城市书店重新开放", 45, 230, 480), region("1197.8万", 830, 232),
        )
        val answer = "1. **周末登山热门路线** — 1212.0万热度\n2. **城市书店重新开放** — 1197.8万热度"
        val result = StructuredEvidence.check(answer, listOf(screen))
        assertEquals(4, result.total)
        assertEquals(4, result.matched)
        assertFalse(result.unbound)

        val swapped = "1. **周末登山热门路线** — 1197.8万热度\n2. **城市书店重新开放** — 1212.0万热度"
        assertEquals(2, StructuredEvidence.check(swapped, listOf(screen)).matched)
        assertTrue(StructuredEvidence.check(swapped, listOf(screen)).unbound)
        val heading = "抖音热榜\n"
        val ordered = StructuredEvidence.check(heading + answer, listOf(screen))
        assertEquals(5, ordered.matched)
        val reversed = StructuredEvidence.check(heading + answer.lines().asReversed().joinToString("\n"), listOf(screen))
        assertEquals(5, reversed.total)
        assertEquals(4, reversed.matched)
    }

    @Test fun doesNotPairAnOcrValueFromAnotherReadAndKeepsTruncatedTitleUnconfirmed() {
        val title = region("城市书店重新开放...", 40, 140, 430)
        val value = region("1197.8万", 835, 140)
        val heading = region("抖音热榜", 40, 96)
        val answer = "1. **城市书店重新开放完整安排** — 1197.8万热度"
        assertEquals(0, StructuredEvidence.check(answer, listOf(read(title, heading), read(value, heading))).matched)
        val sameRead = StructuredEvidence.check(answer, listOf(read(title, value, heading)))
        assertEquals(2, sameRead.total)
        assertEquals(1, sameRead.matched)
    }

    @Test fun mergedPercentagesUseColumnHeadings() {
        val screen = read(
            region("出场率", 626, 280), region("胜率", 800, 280, 80),
            region("奇闻甲战", 202, 399), region("0.37% 70.01% D", 622, 399, 352),
        )
        val table = "| 序号 | 牌组 | 出场率 | 胜率 |\n| --- | --- | --- | --- |\n| 2 | 奇闻甲战 | 0.37% | 70.01% |"
        val good = StructuredEvidence.check(table, listOf(screen))
        assertEquals(3, good.total)
        assertEquals(3, good.matched)
        val swapped = table.replace("0.37% | 70.01%", "70.01% | 0.37%")
        val mismatched = StructuredEvidence.check(swapped, listOf(screen))
        assertEquals(1, mismatched.matched)
        assertEquals(listOf("奇闻甲战 (出场率, 胜率)"), mismatched.missing)
    }

    @Test fun cardValuesUseTheSameColumnAndRequireTheDisplayedField() {
        val screen = read(
            region("战地医疗箱", 550, 620), region("防护头盔", 780, 620),
            region("收益/h", 550, 665), region("收益/h", 780, 665),
            region("5,445", 565, 684), region("22,156", 790, 684),
        )
        val good = StructuredEvidence.check("1. **战地医疗箱** - 收益/小时：5,445", listOf(screen))
        assertEquals(2, good.total)
        assertEquals(2, good.matched)
        assertEquals(1, StructuredEvidence.check("1. **战地医疗箱** - 收益/小时：22,156", listOf(screen)).matched)
        assertEquals(1, StructuredEvidence.check("1. **战地医疗箱** - 可用余额：5,445", listOf(screen)).matched)
    }

    @Test fun ledgerKeepsCoordinatesForActualReadTextButNotInputEcho() {
        val input = region("输入的账号", 40, 100)
        val title = region("搜索结果标题", 40, 150)
        val ledger = EvidenceLedger()
        ledger.noteTyped(input.text)
        ledger.add(read(input, title).copy(inputs = InputHints(listOf(input.text), input.text)))
        val kept = ledger.localReviewReads().single()
        assertEquals(listOf(title), kept.regions)
        assertEquals(listOf(title), (RunEvent.decode(RunEvent.encode(RunEvent.Observed.of(kept))) as RunEvent.Observed)
            .observation().regions)
    }
}

package com.daiyimo.maeve.core.verify

import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.ReadRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 状态句必须和对象、正负结论在同一行实读证据里对上,按钮文案不算结果。 */
class StateEvidenceTest {
    private fun read(vararg lines: String) = Observation(ObsKind.Read, lines.joinToString("\n"))
    private fun row(vararg cells: Pair<String, Int>) = Observation(
        ObsKind.Read,
        cells.joinToString("\n") { it.first },
        regions = cells.map { (text, x) -> ReadRegion(text, listOf(x, 100, x + 120, 120)) },
    )

    @Test fun buttonLabelsDoNotProveTheStateHappened() {
        val buttons = listOf(read("开启 WLAN", "发送", "保存", "付款"))
        assertEquals(0, StateEvidence.check("已开启 WLAN", buttons).matched)
        assertEquals(0, StateEvidence.check("已保存", buttons).matched)
        assertEquals(0, StateEvidence.check("已发送", buttons).matched)
        assertEquals(0, StateEvidence.check("已付款", buttons).matched)

        val result = listOf(read("WLAN 已开启", "更改已保存", "消息已发送", "支付成功"))
        assertEquals(1, StateEvidence.check("已开启 WLAN", result).matched)
        assertEquals(1, StateEvidence.check("已保存", result).matched)
        assertEquals(1, StateEvidence.check("已发送", result).matched)
        assertEquals(1, StateEvidence.check("已付款", result).matched)
    }

    @Test fun stateMustMatchTheObjectAndPolarity() {
        val reads = listOf(read("WLAN 已开启"))
        assertEquals(1, StateEvidence.check("已开启 WLAN", reads).matched)
        val wrongObject = StateEvidence.check("已开启蓝牙", reads)
        assertEquals(0, wrongObject.matched)
        assertTrue(wrongObject.unbound)
        val opposite = StateEvidence.check("已关闭 WLAN", reads)
        assertEquals(0, opposite.matched)
        assertTrue(opposite.unbound)
    }

    @Test fun negativeStateMatchesAnExplicitFailure() {
        val reads = listOf(read("消息发送失败"))
        val negative = StateEvidence.check("没有发送成功", reads)
        assertEquals(1, negative.matched)
        assertEquals(1, negative.total)
        assertEquals(0, StateEvidence.check("已发送", reads).matched)
    }

    @Test fun outwardStateRequiresTheNamedTargetOnTheSameRow() {
        val answer = "已发送给张三"
        val complete = StateEvidence.check(answer, listOf(row("张三" to 20, "消息已发送" to 180)))
        assertEquals(1, complete.matched)
        assertEquals(1, complete.outwardMatched)
        assertEquals(1, complete.outwardTotal)

        val missingTarget = StateEvidence.check(answer, listOf(read("消息已发送")))
        assertEquals(0, missingTarget.matched)
        assertEquals(1, missingTarget.outwardTotal)
        assertTrue(missingTarget.unbound)
    }

    @Test fun severalStatesCountSeparatelyAndSourcesAreIgnored() {
        val reads = listOf(read("WLAN 已开启", "更改已保存"))
        val result = StateEvidence.check("已开启 WLAN，已保存", reads)
        assertEquals(2, result.matched)
        assertEquals(2, result.total)
        assertFalse(result.unbound)
        assertEquals(0, StateEvidence.check("好了\n依据：已发送", reads).total)
    }
}

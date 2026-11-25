package com.daiyimo.maeve.core.verify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本地报告里的数值不仅要出现,还要和回答说的字段在同一行对上。 */
class GroundingValueContextTest {
    private fun check(answer: String, vararg reads: String) =
        Grounding.check("查看页面", answer, reads.toList(), requireValueContext = true)

    @Test fun harmlessLeadInDoesNotBreakFieldBinding() {
        val result = check("当前电量 86%，正在充电", "电量 86%\n正在充电")
        assertTrue(result.unmatched.toString(), result.unmatched.isEmpty())
        assertTrue(result.unboundValues.toString(), result.unboundValues.isEmpty())
    }

    @Test fun matchingNumberForAnotherFieldStaysUnbound() {
        val result = check("余额 128 元", "订单数量 128")
        assertTrue(result.unmatched.isEmpty())
        assertEquals(listOf("128"), result.unboundValues.map { it.text })

        val twoFields = check("余额 256 元", "余额 128 元\n订单数量 256 元")
        assertEquals(listOf("256"), twoFields.unboundValues.map { it.text })
    }

    @Test fun derivedApproximateValueKeepsItsFieldContext() {
        val result = check("售价约 2000 元", "售价 1999 元")
        assertTrue(result.unmatched.toString(), result.unmatched.isEmpty())
        assertTrue(result.unboundValues.toString(), result.unboundValues.isEmpty())
    }

    @Test fun dateAndTimeBindToTheirOwnLabel() {
        val result = check("下单时间：10月23日 14:05", "下单时间 2026-10-23 14:05\n已发货")
        assertTrue(result.unmatched.toString(), result.unmatched.isEmpty())
        assertTrue(result.unboundValues.toString(), result.unboundValues.isEmpty())
    }

    @Test fun buildNumberBindsToTheVersionLabel() {
        val result = check("当前系统版本号 AQ3A.240829.003", "系统版本号 AQ3A.240829.003")
        assertTrue(result.unmatched.toString(), result.unmatched.isEmpty())
        assertTrue(result.unboundValues.toString(), result.unboundValues.isEmpty())
    }

    @Test fun dottedAlphanumericModelIsOneTokenInsteadOfOverlappingPieces() {
        val result = Grounding.check(
            "制造推荐",
            "4.6x30mm AP SX 收益最高，31,899/h",
            listOf("4.6x30mm AP SX\n31,899"),
        )
        assertTrue(result.unmatched.toString(), result.unmatched.isEmpty())
        assertEquals(1, result.claims.count { it.kind == Grounding.Kind.Token && it.text == "4.6x30mm" })
        assertTrue(result.claims.none { it.kind == Grounding.Kind.Token && it.text == "6x30mm" })
    }
}

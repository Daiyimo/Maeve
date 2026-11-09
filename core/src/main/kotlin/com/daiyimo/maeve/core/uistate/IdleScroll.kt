package com.daiyimo.maeve.core.uistate

import kotlin.math.abs
import kotlin.random.Random

/**
 * 停留期间顺手做出的轻量滚动(纯计算,不接触页面)。人在等下一页的时候,往往会把当前页面往下带一小段,
 * 间或又往回拨一点。只有停留时长达到 [Limits.minWaitMs] 才安排;一共做 [Limits.count] 区间内那么多次,
 * 每次滚动的幅度是视口高度的 [Limits.stepFraction] 一小截,方向以往下为主,只有 [Limits.upChance] 的概率往回拨,
 * 且回拨量不超过此前累计往下滚的距离;所有次数加起来不超过 [Limits.maxTotal] 个视口,
 * 净位移不超过 [Limits.maxNet] 个视口。每次滚动的时刻都落在停留区间内,首尾各留出 [Limits.leadMs] 与 [Limits.tailMs]。
 * 只滚页面:不移动指针、不悬停、不点击。
 */
class IdleScroll(private val random: Random, val limits: Limits = Limits()) {
    /** 各项时长以毫秒计,滚动幅度以视口高度为单位。 */
    data class Limits(
        val minWaitMs: Long = 3_000L,
        val count: IntRange = 1..3,
        val stepFraction: ClosedFloatingPointRange<Double> = 0.06..0.16,
        val upChance: Double = 0.25,
        val maxTotal: Double = 0.45,
        val maxNet: Double = 0.3,
        val leadMs: Long = 800L,
        val tailMs: Long = 1_200L,
        /** 相邻两次滚动之间至少隔开这么久。 */
        val gapMs: Long = 700L,
    )

    /** 一次滚动:在停留开始后 [atMs] 毫秒时往下滚 [fraction] 个视口(负数为往回拨)。 */
    data class Step(val atMs: Long, val fraction: Double)

    /** 在一段 [waitMs] 毫秒的停留里安排哪几次滚动;停留太短时一次也不安排。 */
    fun plan(waitMs: Long): List<Step> {
        val bounds = limits
        if (waitMs < bounds.minWaitMs) return emptyList()
        val window = waitMs - bounds.leadMs - bounds.tailMs
        if (window < 0) return emptyList()
        // 按两次之间至少隔 gapMs 计算这段里最多放得下几次
        val capacity = (window / bounds.gapMs + 1).toInt()
        val rounds = random.nextInt(bounds.count.first, bounds.count.last + 1).coerceAtMost(capacity).coerceAtLeast(1)
        var travelled = 0.0
        var net = 0.0
        val fractions = mutableListOf<Double>()
        repeat(rounds) { i ->
            val span = random.nextDouble(bounds.stepFraction.start, bounds.stepFraction.endInclusive)
            val back = i > 0 && net > 0 && random.nextDouble() < bounds.upChance
            val delta = if (back) -minOf(span, net) else minOf(span, bounds.maxNet - net)
            if (abs(delta) < MIN_STEP || travelled + abs(delta) > bounds.maxTotal) return@repeat
            fractions += delta
            travelled += abs(delta)
            net += delta
        }
        // 时刻:在可用区间里随机取点、排序,再保证相邻两次不近于 gapMs
        val moments = MutableList(fractions.size) { bounds.leadMs + random.nextLong(0, window + 1) }.sorted().toMutableList()
        for (i in 1 until moments.size) moments[i] = maxOf(moments[i], moments[i - 1] + bounds.gapMs)
        return fractions.indices.mapNotNull { i -> moments[i].takeIf { it <= waitMs - bounds.tailMs }?.let { Step(it, fractions[i]) } }
    }

    private companion object {
        /** 幅度小于这个数的滚动不做(滚了也看不出来)。 */
        const val MIN_STEP = 0.02
    }
}

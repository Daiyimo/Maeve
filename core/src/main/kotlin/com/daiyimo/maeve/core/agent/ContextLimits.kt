package com.daiyimo.maeve.core.agent

/**
 * 一个模型上下文窗口上的几条线。全部按**整份请求**计(系统提示词 + 工具定义 + 消息,和服务商报的输入 token 同一口径),
 * 固定开销不在这里扣,由量的那一边([UsageMeter])算进去,只算一次。触发点只在 [of] 一处算。
 *
 * - [usable]:窗口扣掉给输出的预留(最多 [MAX_OUTPUT_RESERVE],小窗口按窗口的四分之一)。
 * - [margin]:触发点离 [usable] 的余量(最多 [MARGIN_TOKENS],小窗口按 [usable] 的四分之一);也是压缩后的回差,
 *   压完至少再长这么多才会再压,不会每步都压。
 * - [trigger]:当前请求超过它就压缩一次。不低于窗口的 [MIN_TRIGGER_SHARE],不高于 [usable]。
 * - [target]:一次压缩要把整份请求压到它以下([usable] 的 [AFTER_COMPACTION],且至少比触发点低一个 [margin])。
 */
class ContextLimits private constructor(
    val window: Int, val usable: Int, val trigger: Int, val margin: Int, val target: Int,
    val minTrigger: Int,
) {
    companion object {
        /** 给模型输出预留的 token 上限。 */
        const val MAX_OUTPUT_RESERVE = 20_000
        /** 触发点离可用窗口留的余量上限。 */
        const val MARGIN_TOKENS = 13_000
        /** 一次压缩要压到可用窗口的这个比例以下。 */
        const val AFTER_COMPACTION = 0.5
        /** 触发点的下限(占窗口的比例):给了别的触发点也不会低到一开局就压。 */
        const val MIN_TRIGGER_SHARE = 0.5
        /**
         * 触发点的绝对上限:大窗口(200k+)若按窗口比例算,触发点会高到每步都不压,
         * 请求只增不减,累计 token 很快撞预算。压到这条线以下,每步请求保持小,累计才不爆。
         * 按 30 步、1M 预算反推,每步平均要 <33k,所以触发点定在 30k。
         */
        const val MAX_TRIGGER_TOKENS = 30_000
        /** 触发点下限的绝对上限:窗口比例下限(50%)在大窗口下会高到压不住,必须再压一条绝对线。 */
        const val MAX_FLOOR_TOKENS = 20_000
        /** 压缩目标的绝对上限:大窗口按 usable 的 50% 算目标会高到压不动,每步请求还是大。 */
        const val MAX_TARGET_TOKENS = 20_000

        /**
         * [window]:模型上下文长度。[triggerOverride]:按模型单独给的触发点(整份请求的 token);限在下限和 [usable] 之间。
         */
        fun of(window: Int, triggerOverride: Int? = null): ContextLimits {
            val w = window.coerceAtLeast(0)
            val usable = w - minOf(MAX_OUTPUT_RESERVE, w / 4)
            val margin = minOf(MARGIN_TOKENS, usable / 4)
            // 大窗口:下限、触发点、目标都压到绝对线以下,保证每步请求小、累计不撞预算;小窗口:照旧按窗口比例
            val floor = minOf((w * MIN_TRIGGER_SHARE).toInt(), usable, MAX_FLOOR_TOKENS)
            val trigger = (triggerOverride ?: minOf(usable - margin, MAX_TRIGGER_TOKENS)).coerceIn(floor, usable)
            val target = minOf((usable * AFTER_COMPACTION).toInt(), trigger - margin, MAX_TARGET_TOKENS).coerceAtLeast(0)
            return ContextLimits(w, usable, trigger, margin, target, floor)
        }
    }
}

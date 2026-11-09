package com.daiyimo.maeve.core.record

/**
 * 一个对话累计使用的 token。按每轮运行小结([RunMetrics])相加,口径与「查看
 * 过程」顶部那一行一致([RunMetrics.tokens]:主循环每次回复报的输入加输出)。
 *
 * [missing] 是没能算全的轮次数量:小结还没写入、已经因为保留上限
 * ([RunMetricsStore.DEFAULT_KEEP])被清掉,或其中有的回复服务商没报用量。
 * 此时界面显示「至少」,不做估算。
 */
data class ConversationTokens(val tokens: Long, val turns: Int, val missing: Int) {
    val partial: Boolean get() = missing > 0

    companion object {
        /** 单轮 token;没有小结或全是 0 时返回 null,界面不应显示「0 Token」。 */
        fun turn(metrics: RunMetrics?): Long? = metrics?.tokens?.takeIf { it > 0 }

        /**
         * 汇总 [runIds] 对应的各轮。[runIds] 中重复的编号只算一次;
         * 一个可用的数都没有时返回 null,界面不显示。
         */
        fun of(runIds: List<String>, metrics: Map<String, RunMetrics>): ConversationTokens? {
            val turns = runIds.distinct()
            var total = 0L
            var incomplete = 0
            for (runId in turns) {
                val run = metrics[runId]
                if (run == null) {
                    incomplete++
                    continue
                }
                total += run.tokens
                if (run.callsWithoutUsage > 0) incomplete++
            }
            return if (total > 0) ConversationTokens(total, turns.size, incomplete) else null
        }
    }
}

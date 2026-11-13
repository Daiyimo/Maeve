package com.daiyimo.maeve.ui

import com.daiyimo.maeve.core.record.RunEvent

/** Only server-reported counts; cached input is already part of input. */
data class RunTokens(val input: Long, val output: Long, val cached: Long,
    val calls: Int, val unreported: Int, val reported: Boolean, val scope: String) {
    fun toMap(): Map<String, Any> = mapOf("input" to input, "output" to output, "cached" to cached,
        "calls" to calls, "unreported" to unreported, "reported" to reported, "scope" to scope)
    companion object {
        fun of(events: List<RunEvent>): RunTokens? {
            events.filterIsInstance<RunEvent.ModelCalls>().lastOrNull()?.let { all ->
                return RunTokens(all.inputTokens, all.outputTokens, all.cachedTokens, all.calls,
                    all.unreported, all.calls > all.unreported, "run")
            }
            return replies(events.filterIsInstance<RunEvent.ModelReply>())
        }
        fun replies(replies: List<RunEvent.ModelReply>): RunTokens? = replies.takeIf { it.isNotEmpty() }?.let { rows ->
            RunTokens(rows.sumOf { it.inputTokens?.toLong() ?: 0 }, rows.sumOf { it.outputTokens?.toLong() ?: 0 },
                rows.sumOf { it.cachedTokens?.toLong() ?: 0 }, rows.size,
                rows.count { it.inputTokens == null || it.outputTokens == null },
                rows.any { it.inputTokens != null || it.outputTokens != null }, "loop")
        }
    }
}

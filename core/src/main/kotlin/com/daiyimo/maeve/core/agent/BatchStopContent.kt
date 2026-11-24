package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.access.AppAccessDenied
import com.daiyimo.maeve.core.tool.ActionDelivery
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal data class BatchStop(
    val index: Int,
    val reason: String,
    val entry: JsonObject,
    val executed: Boolean,
    val extra: JsonObject = JsonObject(emptyMap()),
    /** 停下之后没做的那些子步也各给一条结果(新插话时),子步和结果一一对上。 */
    val rest: List<kotlinx.serialization.json.JsonElement> = emptyList(),
)

/** 批处理停止原因的统一文案与证据,和逐步执行循环分开。 */
internal fun JsonObjectBuilder.putBatchStop(stop: BatchStop?, total: Int, confirms: Boolean) {
    when (stop?.reason) {
        null -> Unit
        "confirmation_required" -> {
            put("error", "confirmation_required")
            stop.extra.forEach { (k, v) -> put(k, v) }
            val what = stop.extra["matched"]?.let { (it as JsonPrimitive).content }
            put("message", "Step ${stop.index + 1} of $total spends money ($what). " +
                "It was not run, and the steps after it were not run either. Payment steps never go in do_steps: " +
                if (confirms) "follow the step's message (request_confirmation, or hand_to_user for a payment), then do that step on its own as a single tool call."
                else "do it on its own as a single tool call, not as part of a batch.")
            put("counts_as_failure", false)
        }
        "cancelled" -> {
            put("error", "batch_stopped"); put("reason", "cancelled")
            put("delivery", ActionDelivery.Unknown.wire); put("counts_as_failure", false)
            put("message", "Step ${stop.index + 1} of $total may already have been dispatched when the batch was cancelled. " +
                "Its delivery is unknown; inspect the current screen before deciding whether to repeat it.")
        }
        BatchRunner.NEW_MESSAGE -> {
            put("error", "batch_stopped")
            put("reason", BatchRunner.NEW_MESSAGE)
            put("message", (if (stop.executed) "Step ${stop.index + 1} of $total was run, but waiting for its condition was cut short"
                else "Steps ${stop.index + 1}-$total of $total were skipped") + " to handle a new message from the user. " +
                "Read the message first, then decide what to do next.")
            put("counts_as_failure", false)
        }
        else -> {
            put("error", "batch_stopped")
            put("reason", stop.reason)
            val n = "Step ${stop.index + 1} of $total"
            if (AppAccessDenied.isAppAccessCode(stop.reason)) {
                val detail = (stop.entry["message"] as? JsonPrimitive)?.contentOrNull
                    ?: "Step ${stop.index + 1} of $total was not run (${stop.reason})."
                put("message", "$detail The remaining steps in this batch were not run.")
                put("retryable", false)
            } else {
                put("message", (if (stop.executed) "$n was run but did not pass its check (${stop.reason})" else "$n was not run (${stop.reason})") +
                    "; the steps after it were not run. Check the screen, then continue one step at a time or with a new batch.")
            }
        }
    }
}

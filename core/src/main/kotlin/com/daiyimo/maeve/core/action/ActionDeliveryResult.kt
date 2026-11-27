package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.tool.ActionDelivery
import com.daiyimo.maeve.core.tool.ToolExecution
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 旧工具没有显式投递状态时,按可证明的事实补;派发后超时永远是 unknown,不是 not_sent。 */
internal fun ToolExecution.withActionDelivery(): ToolExecution {
    val wire = (content["delivery"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val reported = delivery ?: ActionDelivery.entries.firstOrNull { it.wire == wire }
    val resolved = reported ?: when {
        content["executed"] == JsonPrimitive(false) -> ActionDelivery.NotSent
        content["submissionUnconfirmed"] == JsonPrimitive(true) || content["error"] == JsonPrimitive("action_timeout") -> ActionDelivery.Unknown
        content["effect"] is JsonObject -> ActionDelivery.Sent
        changed != null -> ActionDelivery.Sent
        content["ok"] == JsonPrimitive(true) -> ActionDelivery.Sent
        content["ok"] == JsonPrimitive(false) -> ActionDelivery.Unknown
        else -> null
    }
    if (resolved == null) return this
    return with(content = JsonObject(content + ("delivery" to JsonPrimitive(resolved.wire))), delivery = resolved)
}

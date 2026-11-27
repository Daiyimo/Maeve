package com.daiyimo.maeve.wechat

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** 流式回复稳定后才把它当作本轮完成。 */
internal const val REPLY_STABLE_MS = 600L

internal suspend fun awaitAmooChatCompletion(now: () -> Long, timeoutMs: Long, readReply: () -> String?): Boolean =
    withTimeoutOrNull(timeoutMs) {
        var previous: String? = null
        var stableSince = now()
        while (isActive) {
            val reply = readReply()
            val at = now()
            if (reply != previous) {
                previous = reply
                stableSince = at
            }
            if (reply != null && at - stableSince >= REPLY_STABLE_MS) return@withTimeoutOrNull true
            delay(250)
        }
        false
    } ?: false

internal fun recoveredSystemAmooReply(id: String, session: String, savedId: String?, savedSession: String?, text: String?): String? =
    text?.takeIf { id == savedId && session == savedSession && it.isNotBlank() }

internal fun wechatPhoneRequestId(id: String): String = "wx-${id.take(56)}"

internal data class WechatPhoneResult(val running: Boolean, val text: String? = null, val waitingForUser: Boolean = false)

internal data class WechatAmooReply(val text: String, val settleChat: Boolean = false)

internal fun selectWechatAmooReply(receipt: WechatPhoneResult?, stableTranscript: String?): WechatAmooReply? {
    receipt?.text?.let { return WechatAmooReply(it, settleChat = stableTranscript == null) }
    return stableTranscript?.takeUnless { receipt?.running == true }?.let { WechatAmooReply(it) }
}

internal fun parseWechatPhoneResult(expectedId: String, raw: String): WechatPhoneResult? {
    val json = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    fun text(key: String) = (json[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    if (text("id") != expectedId) return null
    return when (text("status")) {
        "running" -> WechatPhoneResult(running = true, waitingForUser = text("waiting_for") in setOf("confirmation", "phone"))
        "done" -> WechatPhoneResult(false, text("answer") ?: text("note") ?: "Maeve 已完成任务。")
        "failed" -> WechatPhoneResult(false, text("note") ?: "Maeve 未能完成任务。")
        "cancelled" -> WechatPhoneResult(false, text("note") ?: "Maeve 任务已取消。")
        else -> null
    }
}

/** 只转发本轮完成后的实际答复，不用提示词伪装系统助手身份。 */
internal object SystemAmooTranscript {
    fun reply(current: List<String>, baseline: List<String>, message: String, busy: Boolean): String? {
        if (busy) return null
        val own = current.indexOfLast { it == message }
        val fresh = if (own >= 0) current.drop(own + 1) else {
            val counts = baseline.groupingBy { it }.eachCount().toMutableMap()
            current.filter { value -> val n = counts[value] ?: 0; if (n > 0) { counts[value] = n - 1; false } else true }
        }
        if (fresh.any { it.matches(Regex("正在(思考|生成)[.。…]*")) }) return null
        if (fresh.none { it.startsWith("已完成") || it == "复制" || it == "重新生成" }) return null
        val answer = fresh.filterNot { it == message || it.startsWith("已完成") || it in setOf("复制", "重新生成", "点赞", "点踩") }
            .filterNot { it.isBlank() || it.matches(Regex("\\d+(s|秒|min|分钟)")) }
            .joinToString("\n\n").trim()
        return answer.takeIf { it.isNotBlank() }
    }
}

package com.daiyimo.maeve.ui

import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.chat.SubmissionReceipts
import com.daiyimo.maeve.core.agent.QuoteMessage
import com.daiyimo.maeve.settings.Prerequisites
import com.daiyimo.maeve.task.TaskService
import io.flutter.plugin.common.MethodCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Called under the chat bridge's submission lock. */
internal class ChatSubmission(private val app: MaeveApp) {
    private val receipts get() = app.submissionReceipts
    private fun receiptMap(r: SubmissionReceipts.Receipt) = mapOf("requestId" to r.requestId,
        "conversationId" to r.conversationId, "accepted" to r.accepted,
        "state" to if (r.accepted == null) "receipt_unknown" else if (r.accepted) "accepted" else "rejected")

    fun lookup(id: String): Map<String, Any?> {
        val receipt = receipts.find(id)
        if (receipt?.accepted != null) return receiptMap(receipt)
        val registered = app.runStore.all().firstOrNull { it.submissionId == id }
        if (registered != null) return mapOf(
            "requestId" to id, "conversationId" to registered.threadId,
            "accepted" to true, "state" to "accepted",
        )
        return receipt?.let(::receiptMap) ?: mapOf(
            "requestId" to id, "state" to if (receipts.canIdentifyMissing(id)) "not_received" else "receipt_unknown",
        )
    }

    suspend fun submit(call: MethodCall, check: () -> Prerequisites.Check): Map<String, Any?> {
        val id = requireNotNull(call.argument<String>("requestId"))
        val text = requireNotNull(call.argument<String>("text")?.trim()?.takeIf { it.isNotBlank() })
        val conversation = call.argument<String>("conversationId").orEmpty()
        val quote = call.argument<String>("quote")?.takeIf { it.isNotBlank() }
        val fromUser = call.argument<Boolean>("quoteFromUser") ?: false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Json.encodeToString(listOf(text, quote.orEmpty(), fromUser.toString())).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val previous = withContext(Dispatchers.IO) {
            val old = receipts.find(id)
            receipts.begin(id, digest, conversation)
            old?.let { lookup(id) }
        }
        if (previous != null) return previous
        val current = app.chat.ui.value.conversationId.orEmpty()
        val accepted = current == conversation && app.chat.submit(
            text, quote?.let { QuoteMessage.Quote(if (fromUser) QuoteMessage.From.You else QuoteMessage.From.Maeve, it) },
            check(),
        ) { goal, thread -> TaskService.submitUi(app, goal, thread, id) }
        val settled = withContext(Dispatchers.IO) { receipts.settle(id, accepted) }
        return receiptMap(settled)
    }
}

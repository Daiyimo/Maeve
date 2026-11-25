package com.daiyimo.maeve.chat

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 仅记录提交摘要。started 在调用宿主前落盘；中断后的 started 始终是 unknown，不能重放。 */
class SubmissionReceipts(
    private val file: File,
    private val capacity: Int = 4096,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Serializable data class Receipt(val requestId: String, val digest: String, val conversationId: String,
        val accepted: Boolean? = null)
    private fun read(): List<Receipt> = if (!file.exists()) emptyList() else {
        check(file.length() <= 2 * 1024 * 1024) { "receipt_unavailable" }
        Json.decodeFromString<List<Receipt>>(file.readText())
    }
    @Synchronized fun find(id: String): Receipt? = read().firstOrNull { it.requestId == id }
    /** Expired IDs might already have had their receipt pruned. */
    fun canIdentifyMissing(id: String): Boolean {
        val created = createdAt(id) ?: return false
        val now = clock()
        return created > now - RETENTION_MS && created <= now + CLOCK_SKEW_MS
    }
    @Synchronized fun begin(id: String, digest: String, conversationId: String): Receipt {
        check(createdAt(id) != null) { "bad_request_id" }
        // ID 本身绑定创建时间；清理过期记录后，旧 ID 也不能重新启动动作。
        check(canIdentifyMissing(id)) { "request_expired" }
        val now = clock()
        val rows = read().filter { (createdAt(it.requestId) ?: now) > now - RETENTION_MS }
        rows.firstOrNull { it.requestId == id }?.let {
            require(it.digest == digest && it.conversationId == conversationId) { "request_conflict" }
            return it
        }
        // 有效期内的 unknown 和终态均不驱逐；满额时拒绝新请求，不能悄悄失去去重依据。
        check(rows.size < capacity) { "receipt_capacity" }
        return Receipt(id, digest, conversationId).also { write(rows + it) }
    }
    @Synchronized fun settle(id: String, accepted: Boolean): Receipt {
        val rows = read()
        val receipt = checkNotNull(rows.firstOrNull { it.requestId == id }).copy(accepted = accepted)
        write(rows.map { if (it.requestId == id) receipt else it })
        return receipt
    }
    private fun write(rows: List<Receipt>) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { it.write(Json.encodeToString(rows).toByteArray()); it.fd.sync() }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
        private const val CLOCK_SKEW_MS = 2 * 60 * 1000L
        private val ID = Regex("ui_([0-9]{16,19})_[0-9]{1,10}")
        private fun createdAt(id: String): Long? = ID.matchEntire(id)?.groupValues?.get(1)?.toLongOrNull()?.div(1000)
    }
}

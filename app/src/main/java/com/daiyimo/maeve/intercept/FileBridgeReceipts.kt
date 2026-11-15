package com.daiyimo.maeve.intercept

import com.daiyimo.maeve.core.io.AtomicFiles
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 私有目录的有界提交账本。未知记录不驱逐；满额拒绝新请求，不能清掉保护后重复执行。 */
internal class FileBridgeReceipts(private val dir: File, private val capacity: Int = 4096, private val pendingLimit: Int = 100) {
    init { require(capacity > 0 && pendingLimit > 0) }
    @Serializable data class Receipt(val id: String, val digest: String, val taskId: String, val rejected: Boolean = false,
        val rejectionCode: String? = null, val finished: Boolean = false, val schemaVersion: Int = 1)
    private var cached: MutableMap<String, Receipt>? = null
    private fun read(file: File): Receipt {
        check(file.isFile && file.length() <= 4096) { "receipt_unavailable" }
        return Json.decodeFromString<Receipt>(file.readText()).also {
            check(this.file(it.id) == file && it.schemaVersion == 1 && it.digest.matches(Regex("[a-f0-9]{64}")) &&
                it.taskId == UUID.nameUUIDFromBytes("amoo-file:${it.id}".toByteArray(Charsets.UTF_8)).toString()) { "receipt_unavailable" }
        }
    }
    private fun index(): MutableMap<String, Receipt> = cached ?: run {
        check(dir.isDirectory || dir.mkdirs()) { "receipt_unavailable" }
        val files = java.nio.file.Files.newDirectoryStream(dir.toPath(), "*.json").use { stream -> stream.asSequence().take(capacity + 1).map { it.toFile() }.toList() }
        check(files.size <= capacity) { "receipt_capacity" }
        files.map(::read).associateBy { it.id }.toMutableMap().also { check(it.size == files.size); cached = it }
    }
    private fun file(id: String) = File(dir, digest(id) + ".json")
    @Synchronized fun contains(id: String): Boolean = file(id).isFile
    @Synchronized fun begin(id: String, goal: String, rejected: Boolean): Pair<Receipt, Boolean> {
        val file = file(id)
        if (file.exists()) {
            val saved = read(file)
            check(saved.id == id && saved.digest == digest(goal)) { "request_conflict" }
            return saved to false
        }
        val rows = index()
        check(rows.size < capacity) { "receipt_capacity" }
        val rejection = if (rejected) "bridge_disabled" else if (rows.values.count { !it.rejected && !it.finished } >= pendingLimit) "queue_full" else null
        val receipt = Receipt(id, digest(goal), UUID.nameUUIDFromBytes("amoo-file:$id".toByteArray(Charsets.UTF_8)).toString(),
            rejected = rejection != null, rejectionCode = rejection, finished = rejection != null)
        AtomicFiles.write(file, Json.encodeToString(receipt).toByteArray(Charsets.UTF_8))
        rows[id] = receipt
        return receipt to true
    }
    @Synchronized fun finish(id: String) {
        val rows = index()
        val saved = read(file(id))
        if (saved.finished) return
        val finished = saved.copy(finished = true)
        AtomicFiles.write(file(id), Json.encodeToString(finished).toByteArray(Charsets.UTF_8))
        rows[id] = finished
    }
    companion object {
        private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

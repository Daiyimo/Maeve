package com.daiyimo.maeve.wechat

import com.daiyimo.maeve.core.run.ChannelPeer
import com.daiyimo.maeve.core.run.Delivery
import com.daiyimo.maeve.core.run.SendOutcome
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

/** 双向信箱只交换消息；微信凭据始终保存在应用私有目录。 */
class WechatAmooRelay(
    private val root: () -> File?,
    private val store: WechatStore,
    private val executionEnabled: () -> Boolean,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized fun syncControl() {
        val dir = root() ?: return
        val owner = store.account()?.ownerId
        atomic(File(dir, "control.json"), buildJsonObject {
            put("enabled", store.enabled() && owner != null)
            put("session", owner?.let { store.peer(it).threadId }.orEmpty())
            put("allowMaeve", executionEnabled())
        }.toString())
    }

    @Synchronized fun forward(peer: ChannelPeer, messageId: String, text: String) {
        val dir = checkNotNull(root()) { "共享存储不可用，请解锁手机后重试" }
        val id = requestId(peer, messageId)
        // 留下原请求直到回复确认送达；重放入站不会重建已认领的请求。
        val request = File(dir, "requests/$id.json")
        if (request.exists() || File(dir, "requests/$id.working").exists() || File(dir, "replies/$id.json").exists()) return
        atomic(request, buildJsonObject {
            put("id", id)
            put("session", peer.threadId)
            put("text", text)
            put("createdAt", System.currentTimeMillis())
            put("sequence", store.pending().firstOrNull { it.messageId == messageId }?.sequence ?: 0)
        }.toString())
    }

    suspend fun deliver(send: suspend (ChannelPeer, Delivery) -> SendOutcome) {
        if (!store.enabled()) return
        val dir = root() ?: return
        val owner = store.account()?.ownerId ?: return
        for (pending in store.pending()) {
            if (pending.peer.peerId != owner) continue
            val id = requestId(pending.peer, pending.messageId)
            val reply = File(dir, "replies/$id.json")
            val request = File(dir, "requests/$id.json")
            val working = File(dir, "requests/$id.working")
            val response = if (reply.isFile) {
                val value = runCatching { json.parseToJsonElement(reply.readText()).jsonObject }.getOrNull() ?: continue
                if ((value["id"] as? JsonPrimitive)?.content != id ||
                    (value["session"] as? JsonPrimitive)?.content != pending.peer.threadId) continue
                (value["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            } else {
                val timestamp = listOf(request, working).filter { it.exists() }.minOfOrNull { it.lastModified() } ?: continue
                if (System.currentTimeMillis() - timestamp < REPLY_TIMEOUT_MS) continue
                "Amoo 暂未返回回复。请到 Maeve 的 Amoo 页面检查中转状态；任务可能仍在运行，请核实后再重试。"
            }
            // 再检查一次账号，避免解绑后将旧会话的内容发给新账号。
            if (!store.enabled() || store.account()?.ownerId != owner || store.peer(owner).threadId != pending.peer.threadId) return
            val outcome = send(pending.peer, Delivery.Result("amoo-$id", pending.peer.threadId, response, title = "Amoo 回复"))
            if (outcome == SendOutcome.Delivered) {
                store.acknowledge(pending.messageId)
                request.delete(); working.delete(); reply.delete()
            }
        }
    }

    fun workerReady(now: Long = System.currentTimeMillis()): Boolean {
        val file = root()?.let { File(it, "worker.json") } ?: return false
        val value = runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: return false
        val at = (value["at"] as? JsonPrimitive)?.longOrNull ?: return false
        return (value["ready"] as? JsonPrimitive)?.booleanOrNull == true &&
            ((value["version"] as? JsonPrimitive)?.intOrNull ?: 0) >= 3 && now - at in 0..30_000L
    }

    @Synchronized fun clearSession(session: String) {
        val dir = root() ?: return
        for (folder in listOf("requests", "replies")) {
            File(dir, folder).listFiles()?.filter { it.isFile && it.extension in listOf("json", "working") }?.forEach { file ->
                val value = runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
                if ((value?.get("session") as? JsonPrimitive)?.content == session) file.delete()
            }
        }
    }

    companion object {
        const val REPLY_TIMEOUT_MS = 16 * 60_000L
        internal fun requestId(peer: ChannelPeer, messageId: String): String = MessageDigest.getInstance("SHA-256")
            .digest("${peer.threadId}\n$messageId".toByteArray()).joinToString("") { "%02x".format(it) }

        internal fun atomic(file: File, text: String) {
            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "无法创建中转目录" }
            val temp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

package com.daiyimo.maeve.chat

import com.daiyimo.maeve.core.io.AtomicFiles
import java.io.File
import kotlinx.serialization.json.*

/** UI 私有恢复状态，不进入诊断。损坏时拒绝覆盖，不能丢失未确认发送的 requestId。 */
class ChatDraftStore(private val file: File, private val deletionBlocked: (String) -> Boolean = { false }) {
    @Synchronized fun load(): String? = loadVersioned().second
    @Synchronized fun loadVersioned(): Pair<Long, String?> = if (!file.exists()) 0L to null else {
        check(file.length() <= MAX_BYTES) { "drafts_unavailable" }
        val raw = file.readText()
        val root = Json.parseToJsonElement(raw).jsonObject
        val envelope = "state" in root
        val revision = if (envelope) requireNotNull(root["revision"]?.jsonPrimitive?.longOrNull).also { require(it >= 0 && root.keys == setOf("revision", "state")) } else 0L
        val source = if (envelope) root.getValue("state").toString() else raw
        validate(source)
        revision to pruneDeleted(source)
    }

    @Synchronized fun save(source: String, expectedRevision: Long? = null): Long {
        validate(source)
        // 先确认旧文件仍可读；损坏不能被新的空状态静默覆盖。
        val revision = loadVersioned().first
        check(expectedRevision == null || expectedRevision == revision) { "draft_revision_conflict" }
        val next = Math.addExact(revision, 1L)
        val envelope = buildJsonObject { put("revision", next); put("state", Json.parseToJsonElement(pruneDeleted(source))) }
        val bytes = envelope.toString().toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_BYTES)
        AtomicFiles.write(file, bytes)
        return next
    }

    private fun pruneDeleted(source: String): String {
        val root = Json.parseToJsonElement(source).jsonObject
        val aliases = root.getValue("aliases").jsonObject
        val blocked = aliases.keys.filter(deletionBlocked).toSet() +
            listOf("drafts", "pending").flatMap { root.getValue(it).jsonObject.keys }
                .filter { !it.startsWith("new:") && deletionBlocked(it) }
        if (blocked.isEmpty()) return source
        val keys = blocked + blocked.mapNotNull { aliases[it]?.jsonPrimitive?.content }
        return JsonObject(root.toMutableMap().apply {
            put("aliases", JsonObject(aliases.filterKeys { it !in blocked }))
            for (field in listOf("drafts", "pending")) put(field, JsonObject(root.getValue(field).jsonObject.filterKeys { it !in keys }))
            if (root["activeKey"]?.jsonPrimitive?.content in keys) put("activeKey", JsonPrimitive(""))
        }).toString()
    }

    @Synchronized fun removeConversation(id: String) {
        val root = load()?.let { Json.parseToJsonElement(it).jsonObject } ?: return
        val aliases = root.getValue("aliases").jsonObject
        val key = aliases[id]?.jsonPrimitive?.content ?: id
        val changed = root.toMutableMap().apply {
            put("aliases", JsonObject(aliases.filterKeys { it != id }))
            for (field in listOf("drafts", "pending")) {
                put(field, JsonObject(root.getValue(field).jsonObject.filterKeys { it != key && it != id }))
            }
            if (root["activeKey"]?.jsonPrimitive?.content in setOf(key, id)) put("activeKey", JsonPrimitive(""))
        }
        save(JsonObject(changed).toString())
    }

    private fun validate(source: String) {
        require(source.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "drafts_capacity" }
        val root = Json.parseToJsonElement(source).jsonObject
        require(root.keys == setOf("schemaVersion", "generation", "activeKey", "aliases", "drafts", "pending"))
        require(root["schemaVersion"]?.jsonPrimitive?.intOrNull == 1)
        require((root["generation"]?.jsonPrimitive?.longOrNull ?: -1) >= 0)
        fun text(e: JsonElement?, max: Int): String {
            val p = e as? JsonPrimitive ?: error("drafts_invalid")
            require(p.isString && p.content.length <= max)
            return p.content
        }
        text(root["activeKey"], 160)
        val aliases = root.getValue("aliases").jsonObject
        require(aliases.size <= MAX_ENTRIES)
        aliases.forEach { (key, value) -> require(key.length in 1..160); text(value, 160) }
        val drafts = root.getValue("drafts").jsonObject
        val pending = root.getValue("pending").jsonObject
        require(drafts.size <= MAX_ENTRIES && pending.size <= MAX_ENTRIES)
        fun quote(e: JsonElement?) {
            if (e == JsonNull) return
            val q = e!!.jsonObject
            require(q.keys == setOf("text", "fromUser"))
            text(q["text"], MAX_TEXT)
            require(q["fromUser"]?.jsonPrimitive?.booleanOrNull != null)
        }
        drafts.forEach { (key, value) ->
            require(key.length in 1..160)
            val row = value.jsonObject
            require(row.keys == setOf("text", "quote"))
            text(row["text"], MAX_TEXT); quote(row["quote"])
        }
        pending.forEach { (key, value) ->
            require(key.length in 1..160)
            val row = value.jsonObject
            require(row.keys == setOf("id", "text", "quote", "fromUser"))
            require(text(row["id"], 80).matches(Regex("ui_[0-9]{16,19}_[0-9]{1,10}")))
            text(row["text"], MAX_TEXT)
            if (row["quote"] != JsonNull) text(row["quote"], MAX_TEXT)
            require(row["fromUser"]?.jsonPrimitive?.booleanOrNull != null)
        }
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
        const val MAX_ENTRIES = 128
        const val MAX_TEXT = 16000
    }
}

package com.daiyimo.maeve.core.trace

import com.daiyimo.maeve.core.model.InferenceProtocol
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.Redactor
import com.daiyimo.maeve.core.tool.Verb
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8

internal data class RequestBlock(val path: String, val digest: String, val text: String, val truncation: String?)
internal data class CapturedReply(val text: String, val truncation: String?)

/**
 * Turns one outgoing request or one model reply into the text that is safe to leave on disk.
 *
 * Two things happen side by side. A digest is computed from the original JSON so the next request can be
 * compared with the previous one, before any redaction changes the bytes. The visible text then gets a
 * protocol-independent mask for credentials, opaque reasoning, images and tool arguments/results.
 */
internal object TracePayload {
    const val MAX_BLOCK = 32_768
    private const val LABEL_MAX = 96
    private const val TOOLS_KEY = "tools"

    private val LABEL_SHAPE = Regex("[a-zA-Z0-9_.:/-]{1,96}")

    /** Collect the body fields in wire order; a protocol's instruction and message list stay separate. */
    fun blocks(
        body: JsonObject,
        messages: List<Message>,
        verb: (String?) -> Verb?,
        protocol: InferenceProtocol,
    ): List<RequestBlock> {
        val images = messages.asSequence().flatMap { it.parts.asSequence() }.filterIsInstance<Part.Image>().iterator()
        val capture = Capture(images, verb)
        return buildList {
            fun include(path: String, payload: JsonElement) {
                val digest = payload.toString().encodeUtf8().sha256().hex()
                capture.truncation = null
                val visible = capture.redact(payload).toString()
                add(RequestBlock(path, digest, visible.take(MAX_BLOCK), capture.reasonFor(visible)))
            }
            for ((key, value) in body) {
                val reserved = key == protocol.instruction || key == TOOLS_KEY || key == protocol.messages
                if (!reserved) include(key, value)
            }
            protocol.instruction?.let { name -> body[name]?.let { include(name, it) } }
            (body[TOOLS_KEY] as? JsonArray)?.forEachIndexed { index, value -> include("$TOOLS_KEY[$index]", value) }
            (body[protocol.messages] as? JsonArray)?.forEachIndexed { index, value ->
                include("${protocol.messages}[$index]", value)
            }
        }
    }

    /** Render a reply the same way a request block is rendered, including the truncation marker. */
    fun reply(reply: ModelReply, verb: (String?) -> Verb?): CapturedReply {
        val capture = Capture(emptyList<Part.Image>().iterator(), verb)
        val visible = buildString {
            for (part in reply.parts) {
                when (part) {
                    is Part.Text -> appendLine(capture.redact(JsonPrimitive(part.text)))
                    is Part.ToolCall -> appendLine(capture.redact(buildJsonObject {
                        put("type", "function_call")
                        put("name", part.name)
                        put("call_id", part.id)
                        put("arguments", part.argsJson)
                    }))
                    else -> Unit
                }
                if (length > MAX_BLOCK) break
            }
        }
        val truncation = capture.reasonFor(visible)
        return CapturedReply(
            text = if (truncation == null) visible else visible.take(MAX_BLOCK) + "\n[truncated]",
            truncation = truncation,
        )
    }

    /** An attribute or span name is only kept when it is a short, identifier-shaped token. */
    fun label(value: String): String {
        if (value.length > LABEL_MAX) return "other"
        val sanitized = Scrub.text(value)
        return if (LABEL_SHAPE.matches(sanitized)) sanitized else "other"
    }
}

/** Text-level mask for everything that leaves the process inside a span or an exported body block. */
private object Scrub {
    private const val MASK = "[redacted]"
    private const val ATTACHMENT = "[attachment]"

    private val CREDENTIAL_HEADER =
        Regex("""(?im)\b(?:authorization|proxy-authorization|set-cookie|cookie)\s*:\s*[^\r\n]*""")
    private val HTTP_URL = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val URL_USER_INFO = Regex("""(://)[^/?#\s]*@""")
    private val DATA_URI = Regex("""(?i)data:[^,\s"']{0,100};base64,[a-z0-9+/=_-]+""")
    private val ASSIGNED_SECRET = Regex(
        """(?i)((?:\b(?:password|passwd|token|api[_-]?key|secret|otp|pin|authorization|cookie|session[_-]?id)|密码|口令)\s*[=:：]\s*)[^\s,;&]+""",
    )
    private val QUERY_CUT = charArrayOf('?', '#')

    fun text(value: String): String {
        val detached = DATA_URI.replace(value, ATTACHMENT)
        val withoutUserInfo = HTTP_URL.replace(detached) { match ->
            val bare = URL_USER_INFO.replace(match.value, "$1")
            val cut = bare.indexOfAny(QUERY_CUT)
            if (cut < 0) bare else bare.take(cut) + "?[redacted]"
        }
        val withoutHeaders = CREDENTIAL_HEADER.replace(withoutUserInfo, MASK)
        val withoutAssignments = ASSIGNED_SECRET.replace(withoutHeaders, "$1[redacted]")
        return Redactor.evidence(Redactor.pageData(withoutAssignments))
    }
}

/** Walks one JSON tree and masks fields which belong to a protocol's private surface. */
private class Capture(
    private val images: Iterator<Part.Image>,
    private val verbOf: (String?) -> Verb?,
) {
    private val knownCalls = mutableMapOf<String, Verb?>()
    var truncation: String? = null

    fun reasonFor(text: String): String? =
        truncation ?: "block_size_limit".takeIf { text.length > TracePayload.MAX_BLOCK }

    fun redact(value: JsonElement, depth: Int = 0): JsonElement {
        if (depth > MAX_DEPTH) {
            truncation = "nesting_limit"
            return JsonPrimitive("[truncated]")
        }
        if (value is JsonPrimitive && value.isString && value.content.length > TracePayload.MAX_BLOCK) {
            truncation = "block_size_limit"
            // Stop on a word boundary so a partially visible credential cannot slip past the redactor.
            val head = value.content.take(TracePayload.MAX_BLOCK)
            val boundary = head.lastIndexOfAny(SPLIT_CHARS)
            return JsonPrimitive(Scrub.text(if (boundary < 0) "" else head.take(boundary)) + "[truncated]")
        }
        if (value is JsonArray) return JsonArray(value.map { redact(it, depth + 1) })
        if (value !is JsonObject) {
            return if (value is JsonPrimitive && value.isString) JsonPrimitive(Scrub.text(value.content)) else value
        }
        return redactObject(value, depth)
    }

    private fun redactObject(value: JsonObject, depth: Int): JsonElement {
        val type = value.textOrNull("type")
        val image = imageBytes(value, type)
        if (image != null) {
            val part = if (images.hasNext()) images.next() else null
            return buildJsonObject {
                put("type", "input_image")
                put("width", part?.width ?: 0)
                put("height", part?.height ?: 0)
                put("sha256", image.encodeUtf8().sha256().hex())
            }
        }
        if (isHidden(value, type)) return JsonPrimitive(SCRUB_MASK)
        val callId = value.textOrNull("call_id") ?: value.textOrNull("id")
        if (callId != null) {
            val name = when (type) {
                "function_call", "tool_use" -> value.textOrNull("name")
                "function" -> (value["function"] as? JsonObject)?.textOrNull("name")
                else -> null
            }
            if (name != null) knownCalls[callId] = verbOf(name)
        }
        return JsonObject(value.mapValues { (key, child) -> field(value, type, key, child, depth) })
    }

    private fun field(owner: JsonObject, type: String?, key: String, child: JsonElement, depth: Int): JsonElement = when {
        Redactor.secretName(key) || key in HIDDEN_FIELDS || key.contains("base64", ignoreCase = true) ->
            JsonPrimitive(SCRUB_MASK)
        key == "reasoning" && child !is JsonObject -> JsonPrimitive(SCRUB_MASK)
        key == "arguments" && (type == "function_call" || owner.textOrNull("name") != null) ->
            arguments(owner.textOrNull("name"), child, depth)
        key == "input" && type == "tool_use" -> arguments(owner.textOrNull("name"), child, depth)
        key == "args" && owner.textOrNull("name") != null -> arguments(owner.textOrNull("name"), child, depth)
        key == "output" && type == "function_call_output" ->
            result(knownCalls[owner.textOrNull("call_id")], child, depth)
        key == "content" && type == "tool_result" ->
            result(knownCalls[owner.textOrNull("tool_use_id")], child, depth)
        key == "content" && owner.textOrNull("role") == "tool" ->
            result(knownCalls[owner.textOrNull("tool_call_id")], child, depth)
        key == "response" && owner.textOrNull("name") != null && child is JsonObject && "content" in child ->
            JsonObject(child.mapValues { (name, item) ->
                if (name == "content") result(verbOf(owner.textOrNull("name")), item, depth)
                else redact(item, depth + 1)
            })
        else -> redact(child, depth + 1)
    }

    private fun imageBytes(value: JsonObject, type: String?): String? = when (type) {
        "input_image" -> value.textOrNull("image_url")
        "image_url" -> (value["image_url"] as? JsonObject)?.textOrNull("url")
        "image" -> (value["source"] as? JsonObject)?.textOrNull("data")
        else -> (value["inlineData"] as? JsonObject)?.textOrNull("data")
    }

    private fun isHidden(value: JsonObject, type: String?): Boolean =
        (type != null && type in HIDDEN_TYPES) ||
            value.textOrNull("password") == "true" ||
            value.textOrNull("isPassword") == "true" ||
            type.equals("password", ignoreCase = true) ||
            value.textOrNull("thought") == "true"

    private fun arguments(name: String?, args: JsonElement, depth: Int): JsonElement {
        val raw = (args as? JsonPrimitive)?.takeIf { it.isString }?.content ?: args.toString()
        val safe = redact(parse(Redactor.args(verbOf(name), raw, secretField = true)), depth + 1)
        return if (args is JsonPrimitive && args.isString) encode(raw, safe) else safe
    }

    private fun result(verb: Verb?, item: JsonElement, depth: Int): JsonElement {
        if (verb == Verb.AskUser) return JsonPrimitive(SCRUB_MASK)
        fun plain(element: JsonElement): JsonElement {
            val raw = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            val value = raw?.let(::parse) ?: element
            val safe = redact(if (value is JsonObject) Redactor.result(verb, value) else value, depth + 1)
            return if (raw == null) safe else encode(raw, safe)
        }
        // Explicit-cache chat endpoints can wrap a tool result in a list of text blocks.
        if (item !is JsonArray) return plain(item)
        return JsonArray(item.map { part ->
            if (part is JsonObject && part.textOrNull("type") == "text") {
                JsonObject(part.mapValues { (key, value) -> if (key == "text") plain(value) else redact(value, depth + 1) })
            } else {
                redact(part, depth + 1)
            }
        })
    }

    /** Responses encodes arguments and tool output as strings even when they contain JSON. */
    private fun encode(original: String, safe: JsonElement): JsonPrimitive {
        if (parse(original) == safe) return JsonPrimitive(original)
        return if (safe is JsonPrimitive && safe.isString) safe else JsonPrimitive(safe.toString())
    }

    private fun parse(text: String): JsonElement =
        runCatching { Json.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }

    private fun JsonObject.textOrNull(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val MAX_DEPTH = 32
        val SPLIT_CHARS = charArrayOf(' ', '\n', '\r', '\t')
        val HIDDEN_TYPES = setOf("reasoning", "reasoning_content", "thinking", "redacted_thinking")
        val HIDDEN_FIELDS =
            setOf("encrypted_content", "reasoning_content", "thinking", "signature", "thoughtSignature", "thought_signature")
    }
}

private const val SCRUB_MASK = "[redacted]"

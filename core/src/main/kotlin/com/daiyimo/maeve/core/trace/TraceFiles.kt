package com.daiyimo.maeve.core.trace

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.common.v1.ArrayValue
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.InstrumentationScope
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.resource.v1.Resource
import io.opentelemetry.proto.trace.v1.ResourceSpans
import io.opentelemetry.proto.trace.v1.ScopeSpans
import io.opentelemetry.proto.trace.v1.Span
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString

/** The timeline row the UI reads before the user asks for one request's body. */
data class TraceRow(
    val id: String, val name: String, val step: Long?, val attempt: Long?, val status: String,
    val input: Long?, val cached: Long?, val output: Long?, val durationMs: Long,
    val difference: String?, val contextChange: String?, val captured: Boolean, val toolCallId: String?,
) {
    /** Tokens that actually missed the provider cache; unavailable when either counter is missing. */
    val uncached: Long? get() = input?.let { total -> cached?.let { hit -> total - hit } }
}

@Serializable
internal data class BodyPart(val path: String, val blob: String?, val truncated: Boolean, val reason: String? = null)

/**
 * One run's trace directory: a protobuf root plus child spans, request manifests, content-addressed
 * body blocks and raw replies.
 *
 * Everything the UI shows comes from span attributes; the manifests are only read when a body page is
 * requested or a ZIP export is written. `root.pb` is also the marker that the run exists.
 */
class TraceArchive internal constructor(internal val dir: File, private val disk: TraceDisk) {
    val runId: String get() = dir.name
    internal val root = File(dir, ROOT_FILE)
    private val spanDir = File(dir, "spans")
    private val requestDir = File(dir, "requests")
    private val blockDir = File(dir, "blocks")

    internal fun initialize() {
        if (!spanDir.mkdir() || !requestDir.mkdir() || !blockDir.mkdir()) throw IOException("trace directory")
    }

    internal fun store(message: Span) {
        val target = if (message.parentSpanId.isEmpty) root
        else File(spanDir, message.hexId() + ".pb")
        disk.write(dir, target, message.toByteArray(), "span")
    }

    internal fun hasBlock(key: String) = File(blockDir, key).isFile

    internal fun putBlock(key: String, bytes: ByteArray, path: String): Int {
        val target = File(blockDir, key)
        if (target.exists()) return 0
        disk.write(dir, target, bytes, path)
        return bytes.size
    }

    internal fun writeBody(id: String, parts: List<BodyPart>): Int {
        val manifest = Json.encodeToString(parts).toByteArray()
        disk.write(dir, File(requestDir, id + ".json"), manifest, "request_manifest")
        return manifest.size
    }

    internal fun writeReply(id: String, data: ByteArray) =
        disk.write(dir, File(requestDir, id + ".reply"), data, "response")

    /** Stream the visible body of one request, block by block, followed by its reply. */
    private fun bodySections(id: String): Sequence<String>? {
        if (!SPAN_ID.matches(id)) return null
        val manifest = File(requestDir, id + ".json")
        if (!manifest.isFile) return null
        return sequence {
            for (part in Json.decodeFromString<List<BodyPart>>(manifest.readText())) {
                yield(part.path + "\n")
                val blob = part.blob
                if (blob != null) {
                    require(BLOCK_ID.matches(blob))
                    yield(File(blockDir, blob).readText())
                }
                if (part.truncated) yield("\n[truncated]")
                yield("\n\n")
            }
            val response = File(requestDir, id + ".reply")
            if (response.isFile) {
                yield("response\n")
                yield(response.readText())
            }
        }
    }

    data class Page(val text: String, val hasMore: Boolean)

    /** Materialize at most one page even when a long run references the same block many times. */
    fun bodyPage(id: String, offset: Int = 0, limit: Int = 3000): Page? {
        require(offset >= 0 && limit in 1..65536)
        val sections = bodySections(id) ?: return null
        val window = StringBuilder()
        var skip = offset
        for (section in sections) {
            if (skip >= section.length) {
                skip -= section.length
                continue
            }
            val end = minOf(section.length, skip + limit - window.length)
            window.append(section, skip, end)
            if (end < section.length) return Page(window.toString(), true)
            skip = 0
        }
        return Page(window.toString(), false)
    }

    private fun allSpans(): List<Span> {
        val children = spanDir.listFiles().orEmpty()
            .filter { it.extension == "pb" }
            .map { Span.parseFrom(it.readBytes()) }
            .sortedBy { span -> span.intAttr(SEQUENCE_KEY) }
        return listOf(Span.parseFrom(root.readBytes())) + children
    }

    fun rows(): List<TraceRow> = allSpans().map { span ->
        val attributes = span.attributesList.associate { it.key to it.value }
        fun number(key: String): Long? = attributes[key]?.intValue
        fun text(key: String): String? = attributes[key]?.stringValue
        TraceRow(
            id = span.hexId(),
            name = span.name,
            step = number("maeve.step.index"),
            attempt = number("maeve.attempt"),
            status = text("maeve.status") ?: "partial",
            input = number("gen_ai.usage.input_tokens"),
            cached = number("gen_ai.usage.cache_read.input_tokens"),
            output = number("gen_ai.usage.output_tokens"),
            durationMs = TimeUnit.NANOSECONDS.toMillis(span.endTimeUnixNano - span.startTimeUnixNano),
            difference = text("maeve.context.first_changed_block"),
            contextChange = text("maeve.context.change"),
            captured = attributes["maeve.request.captured"]?.boolValue == true,
            toolCallId = text("gen_ai.tool.call.id"),
        )
    }

    /**
     * Rebuild the spans that will be exported.
     *
     * Diagnostics recorded in the manifests are lifted into `maeve.trace.truncated` events so a receiver
     * learns where a request or reply was clipped. Older archives lack both the format marker and the
     * event attributes; their manifest reasons and reserved reply suffix are translated here.
     */
    private fun exportSpans(): List<Span> {
        val recorded = allSpans()
        val rootSpan = recorded.first()
        val legacy = rootSpan.attributesList.none { it.key == FORMAT_KEY }
        if (!legacy && rootSpan.textAttr(STATUS_KEY) != PARTIAL) return recorded
        val reasons = linkedSetOf<String>()
        rootSpan.textAttr(STOP_REASON_KEY)?.let(reasons::add)
        val children = recorded.drop(1).map { span ->
            if (span.attributesList.none { it.key == ATTEMPT_KEY }) return@map span
            val builder = span.toBuilder()
            fun markCut(path: String, reason: String, at: Long) {
                reasons += reason
                builder.addEvents(
                    Span.Event.newBuilder()
                        .setName("maeve.trace.truncated")
                        .setTimeUnixNano(at)
                        .addAttributes(textAttribute("maeve.block.path", path))
                        .addAttributes(textAttribute("maeve.truncation.reason", reason)),
                )
            }
            val manifest = File(requestDir, span.hexId() + ".json")
            if (manifest.isFile) {
                val parts = try {
                    Json.decodeFromString<List<BodyPart>>(manifest.readText())
                } catch (_: SerializationException) {
                    markCut("request_manifest", "manifest_unreadable", span.startTimeUnixNano)
                    emptyList()
                } catch (_: IOException) {
                    markCut("request_manifest", "manifest_unreadable", span.startTimeUnixNano)
                    emptyList()
                }
                for (part in parts) {
                    if (!part.truncated) continue
                    val reason = part.reason ?: if (part.blob == null) "run_body_limit" else "block_limit"
                    markCut(part.path, reason, span.startTimeUnixNano)
                }
            }
            val replyCut = span.textAttr(REPLY_CUT_KEY)
                ?: if (legacy) legacyReplyCut(span.hexId()) else null
            replyCut?.let { markCut("response", it, span.endTimeUnixNano) }
            builder.build()
        }
        if (rootSpan.textAttr(STATUS_KEY) == PARTIAL) {
            if (rootSpan.startTimeUnixNano == rootSpan.endTimeUnixNano) reasons += "not_finalized"
            if (legacy && rootSpan.textAttr("maeve.run.outcome") == "cancelled") reasons += "run_cancelled"
            if (reasons.isEmpty()) reasons += "unknown"
        }
        if (reasons.isEmpty()) return recorded
        val collected = ArrayValue.newBuilder()
            .addAllValues(reasons.map { AnyValue.newBuilder().setStringValue(it).build() })
        val updated = rootSpan.toBuilder().addAttributes(
            KeyValue.newBuilder().setKey(PARTIAL_REASONS_KEY)
                .setValue(AnyValue.newBuilder().setArrayValue(collected)),
        )
        // Writers older than the format marker did not switch the root to partial for a clipped reply.
        if (rootSpan.textAttr(STATUS_KEY) != PARTIAL) {
            val statusIndex = rootSpan.attributesList.indexOfFirst { it.key == STATUS_KEY }
            updated.setAttributes(statusIndex, textAttribute(STATUS_KEY, PARTIAL))
        }
        return listOf(updated.build()) + children
    }

    private fun legacyReplyCut(id: String): String? {
        val file = File(requestDir, "$id.reply")
        if (!file.isFile) return null
        // The old writer appended this marker outside the JSON-encoded visible reply.
        val marker = "\n[truncated]".toByteArray()
        return RandomAccessFile(file, "r").use { input ->
            val tailSize = minOf(input.length(), marker.size.toLong()).toInt()
            input.seek(input.length() - tailSize)
            val tail = ByteArray(tailSize).also(input::readFully)
            when {
                tail.contentEquals(marker) -> "block_limit"
                input.length() == tailSize.toLong() && tail.decodeToString() == "[truncated]" -> "run_body_limit"
                else -> null
            }
        }
    }

    private fun payload(spans: List<Span>): ByteArray {
        val scope = ScopeSpans.newBuilder()
            .setScope(InstrumentationScope.newBuilder().setName("maeve.harness"))
            .addAllSpans(spans)
        val resource = Resource.newBuilder().addAttributes(
            KeyValue.newBuilder().setKey("service.name")
                .setValue(AnyValue.newBuilder().setStringValue("Maeve")),
        )
        return ExportTraceServiceRequest.newBuilder()
            .addResourceSpans(ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scope))
            .build()
            .toByteArray()
    }

    /** Bodies only leave the device inside a ZIP the user explicitly asked to save. */
    fun save(output: OutputStream, includeBody: Boolean): Unit = disk.read(dir) {
        val exported = exportSpans()
        val encoded = payload(exported)
        if (!includeBody) {
            output.write(encoded)
            return@read
        }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("trace.pb"))
            zip.write(encoded)
            zip.closeEntry()
            for (span in exported) {
                val id = span.hexId()
                val sections = bodySections(id) ?: continue
                zip.putNextEntry(ZipEntry("requests/$id.txt"))
                for (section in sections) zip.write(section.toByteArray())
                zip.closeEntry()
            }
        }
    }

    companion object {
        private const val ROOT_FILE = "root.pb"
        private const val STATUS_KEY = "maeve.status"
        private const val PARTIAL = "partial"
        private const val SEQUENCE_KEY = "maeve.sequence"
        private const val ATTEMPT_KEY = "maeve.attempt"
        private const val FORMAT_KEY = "maeve.trace.format_version"
        private const val STOP_REASON_KEY = "maeve.trace.stop_reason"
        private const val REPLY_CUT_KEY = "maeve.trace.reply_truncation"
        private const val PARTIAL_REASONS_KEY = "maeve.trace.partial_reasons"

        private val SPAN_ID = Regex("[a-f0-9]{16}")
        private val BLOCK_ID = Regex("[a-f0-9]{64}")

        private fun textAttribute(key: String, value: String) = KeyValue.newBuilder().setKey(key)
            .setValue(AnyValue.newBuilder().setStringValue(value)).build()

        private fun Span.textAttr(key: String) = attributesList.firstOrNull { it.key == key }?.value?.stringValue
        private fun Span.intAttr(key: String) = attributesList.firstOrNull { it.key == key }?.value?.intValue
        private fun Span.hexId() = spanId.toByteArray().toByteString().hex()
    }
}

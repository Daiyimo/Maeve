package com.daiyimo.maeve.core.trace

import com.google.protobuf.ByteString
import io.opentelemetry.proto.common.v1.AnyValue
import io.opentelemetry.proto.common.v1.KeyValue
import io.opentelemetry.proto.trace.v1.Span
import io.opentelemetry.proto.trace.v1.Status
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom

/**
 * One span whose operation is still in progress.
 *
 * A checkpoint is a snapshot with `end == start`; the caller seals the same span later with the real end.
 * Each snapshot is rebuilt from the current attribute map, so writing an early checkpoint never freezes
 * attributes that arrive afterwards.
 */
internal class LocalSpan(
    val traceId: ByteString,
    val name: String,
    parent: LocalSpan? = null,
    private val kind: Span.SpanKind = Span.SpanKind.SPAN_KIND_INTERNAL,
    val step: Long? = null,
    private val start: Long,
) {
    private val spanId = randomId(8)
    val id: String = spanId.toByteArray().toByteString().hex()
    private val parentId: ByteString? = parent?.spanId
    private val attributes = LinkedHashMap<String, AnyValue>()
    private var markedError = false

    fun setAttribute(key: String, value: String) = apply {
        attributes[key] = AnyValue.newBuilder().setStringValue(value).build()
    }

    fun setAttribute(key: String, value: Long) = apply {
        attributes[key] = AnyValue.newBuilder().setIntValue(value).build()
    }

    fun setAttribute(key: String, value: Boolean) = apply {
        attributes[key] = AnyValue.newBuilder().setBoolValue(value).build()
    }

    fun error() {
        markedError = true
    }

    fun snapshot(end: Long = start): Span {
        val outgoing = Span.newBuilder()
            .setTraceId(traceId)
            .setSpanId(spanId)
            .setFlags(SAMPLED)
            .setName(name)
            .setKind(kind)
            .setStartTimeUnixNano(start)
            .setEndTimeUnixNano(maxOf(start, end))
        parentId?.let(outgoing::setParentSpanId)
        for ((key, value) in attributes) {
            outgoing.addAttributes(KeyValue.newBuilder().setKey(key).setValue(value))
        }
        if (markedError) {
            outgoing.status = Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_ERROR).build()
        }
        return outgoing.build()
    }

    companion object {
        private const val SAMPLED = 1
        private val entropy = SecureRandom()

        /** Random bytes for a trace/span id; an all-zero id is never valid on the wire. */
        fun randomId(size: Int): ByteString {
            val buffer = ByteArray(size)
            do {
                entropy.nextBytes(buffer)
            } while (buffer.all { it == 0.toByte() })
            return ByteString.copyFrom(buffer)
        }
    }
}

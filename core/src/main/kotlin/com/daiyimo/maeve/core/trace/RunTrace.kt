package com.daiyimo.maeve.core.trace

import com.daiyimo.maeve.core.model.InferenceRequest
import com.daiyimo.maeve.core.model.InferenceProtocol
import com.daiyimo.maeve.core.model.ReportedTokens
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.tool.Verb
import io.opentelemetry.proto.trace.v1.Span.SpanKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okio.ByteString.Companion.toByteString
import java.io.File
import java.io.IOException
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Owns every run that is currently being recorded.
 *
 * Trace directories live outside ordinary run directories because those may be mirrored into an
 * evaluation; a store only keeps the runs for which [start] returned a handle.
 */
class TraceStore(
    private val directory: File,
    private val byteLimit: Int = 8 * 1024 * 1024,
    storageLimitBytes: Long = DEFAULT_LIMIT_MIB * MIB,
) {
    private val activeRuns = mutableMapOf<String, RunTrace>()
    private val disk = TraceDisk(directory, storageLimitBytes)

    fun setStorageLimit(bytes: Long) = disk.setLimit(bytes)

    @Synchronized fun start(id: String): RunTrace? {
        if (!TraceDisk.RUN_ID.matches(id) || activeRuns.containsKey(id)) return null
        val dir = File(directory, id)
        try {
            if (!disk.open(dir)) return null
            try {
                val archive = TraceArchive(dir, disk)
                archive.initialize()
                return RunTrace(archive, byteLimit).also { activeRuns[id] = it }
            } catch (e: Exception) {
                disk.close(dir)
                throw e
            }
        } catch (_: Throwable) {
            return null
        }
    }

    @Synchronized fun finish(trace: RunTrace?) {
        if (trace == null || !activeRuns.remove(trace.archive.runId, trace)) return
        try {
            trace.finish()
        } finally {
            disk.close(trace.archive.dir)
        }
    }

    @Synchronized fun stopAll() {
        for (trace in activeRuns.values) {
            try {
                trace.finish("recording_disabled")
            } finally {
                disk.close(trace.archive.dir)
            }
        }
        activeRuns.clear()
    }

    /** A run that is still recording keeps changing; a viewer opened mid-run has to read it again. */
    @Synchronized fun recording(id: String): Boolean = activeRuns.containsKey(id)

    fun archive(id: String): TraceArchive? {
        if (!TraceDisk.RUN_ID.matches(id)) return null
        return TraceArchive(File(directory, id), disk).takeIf { it.root.isFile }
    }

    companion object {
        const val DEFAULT_LIMIT_MIB = 100
        const val MIB = 1024L * 1024
    }
}

/**
 * Coroutine context element that lets the HTTP layer find the trace call it belongs to.
 *
 * Only a model invocation in the main loop installs it, so token refreshes and catalog requests made
 * from the same coroutine are not mistaken for inference traffic. One call spans every adapter-level
 * retry; each actual request becomes an [Attempt].
 */
internal class TraceCall(
    val run: RunTrace,
    val step: Int,
    val model: ChatModel,
    val verb: (String?) -> Verb?,
    change: String?,
    private val messages: List<Message> = emptyList(),
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TraceCall>

    private var currentAttempt: Attempt? = null
    private var changeNotYetRecorded = change

    fun takeChange(): String? {
        val queued = changeNotYetRecorded
        changeNotYetRecorded = null
        return queued
    }

    internal inner class Attempt(val span: LocalSpan, private val protocol: InferenceProtocol) {
        @Volatile var usage: ReportedTokens? = null
            private set

        fun httpStatus(code: Int) = run.httpStatus(span, code)

        /** A response fragment matters only when it may carry usage; SSE text deltas are skipped early. */
        fun packet(data: String) {
            if (data.firstOrNull { !it.isWhitespace() } != '{') return
            if ("usage" !in data && "\\u" !in data) return
            val envelope = try {
                Json.parseToJsonElement(data) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            }
            val next = envelope?.let(protocol::usageIn) ?: return
            val previous = usage
            usage = ReportedTokens(
                next.input ?: previous?.input,
                next.output ?: previous?.output,
                next.cached ?: previous?.cached,
            )
        }
    }

    fun sending(request: InferenceRequest): Attempt? {
        finish("error", null)
        val span = begin(request.payload, messages, request.provider, request.protocol) ?: return null
        return Attempt(span, request.protocol).also { currentAttempt = it }
    }

    fun finish(status: String, reply: ModelReply?) {
        val attempt = currentAttempt ?: return
        currentAttempt = null
        val usage = attempt.usage
        run.end(
            attempt.span, status, usage?.input, usage?.cached, usage?.output,
            reply?.let { captured -> { TracePayload.reply(captured, verb) } },
        )
    }

    fun begin(
        body: JsonObject,
        messages: List<Message>,
        provider: String = "openai_compatible",
        protocol: InferenceProtocol = InferenceProtocol.Responses,
    ) = run.begin(this, body, messages, provider = provider, protocol = protocol)
}

/**
 * Records one run as OpenTelemetry spans plus the local request bodies.
 *
 * Children are checkpointed as they open and sealed when their operation ends. Every failure while
 * capturing goes through [record], which stops the run with a reason instead of surfacing an exception
 * to the task. After [finish] the trace is final: late callbacks cannot add spans.
 */
class RunTrace internal constructor(internal val archive: TraceArchive, private val byteLimit: Int) : RunSink {
    private var storeFailure: Exception? = null
    private var failedStep: Long? = null
    private val wallClockBase = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
    private val monotonicBase = System.nanoTime()
    private fun nowNanos() = wallClockBase + (System.nanoTime() - monotonicBase)
    private val rootSpan = LocalSpan(LocalSpan.randomId(16), "invoke_agent Maeve", start = nowNanos())
        .setAttribute("gen_ai.operation.name", "invoke_agent")
        .setAttribute("maeve.status", "partial")
        .setAttribute("maeve.trace.format_version", 2L)
    private val pending = mutableSetOf<LocalSpan>()
    private val toolSpans = mutableMapOf<Pair<Int, String>, LocalSpan>()
    private val previousBlocks = IdentityHashMap<ChatModel, List<Pair<String, String>>>()
    private val attemptsPerStep = mutableMapOf<Int, Long>()
    private var contextsJustChanged = false
    private var outcome: String? = null
    private var spanCount = 0
    private var capturedBytes = 0
    private var clipped = false
    @Volatile private var accepting = true

    init {
        checkpoint(rootSpan)
    }

    private fun checkpoint(span: LocalSpan) = archive.store(span.snapshot())

    private fun seal(span: LocalSpan, end: Long = nowNanos()) {
        try {
            archive.store(span.snapshot(end))
        } catch (e: Exception) {
            if (storeFailure == null) {
                storeFailure = e
                failedStep = span.step
            }
        }
    }

    internal suspend fun modelCall(
        step: Int,
        model: ChatModel,
        verb: (String?) -> Verb?,
        change: String?,
        messages: List<Message>,
        block: suspend () -> ModelReply,
    ): ModelReply {
        if (!accepting) return block()
        val scope = TraceCall(this, step, model, verb, change, messages)
        var reply: ModelReply? = null
        var status = "error"
        try {
            return withContext(scope) { block() }.also {
                reply = it
                status = "ok"
            }
        } catch (e: CancellationException) {
            status = "cancelled"
            throw e
        } finally {
            scope.finish(status, reply)
        }
    }

    internal fun begin(
        call: TraceCall,
        body: JsonObject,
        messages: List<Message>,
        start: Long = System.currentTimeMillis(),
        provider: String,
        protocol: InferenceProtocol,
    ): LocalSpan? {
        if (!accepting) return null
        // Redaction can walk a very large prompt or many images; the stop lock must not be held meanwhile.
        val blocks = try {
            TracePayload.blocks(body, messages, call.verb, protocol)
        } catch (_: Exception) {
            finish("capture_error", call.step.toLong(), "request")
            return null
        } catch (_: StackOverflowError) {
            finish("capture_error", call.step.toLong(), "request")
            return null
        }
        return beginPrepared(call, blocks, start, provider)
    }

    @Synchronized private fun beginPrepared(
        call: TraceCall,
        blocks: List<RequestBlock>,
        start: Long,
        provider: String,
    ): LocalSpan? = record(call.step.toLong()) {
        val modelLabel = TracePayload.label(call.model.name)
        val span = child("chat " + modelLabel, call.step, SpanKind.SPAN_KIND_CLIENT, start)
        val attempt = (attemptsPerStep[call.step] ?: 0L) + 1
        attemptsPerStep[call.step] = attempt
        span.setAttribute("gen_ai.operation.name", "chat")
            .setAttribute("gen_ai.request.model", modelLabel)
            .setAttribute("maeve.attempt", attempt)
            .setAttribute("maeve.request.captured", true)
            .setAttribute("gen_ai.provider.name", provider)
        val changes = buildList {
            if (contextsJustChanged) add("tools_opened")
            call.takeChange()?.let { add(it) }
        }.joinToString(",")
        if (changes.isNotEmpty()) span.setAttribute("maeve.context.change", changes)
        contextsJustChanged = false
        val signatures = blocks.map { it.path to it.digest }
        val previous = previousBlocks.put(call.model, signatures)
        span.setAttribute("maeve.context.first_changed_block", describeChange(previous, blocks))
        val parts = ArrayList<BodyPart>()
        for (block in blocks) {
            val content = block.text.toByteArray()
            val key = content.toByteString().sha256().hex()
            val extra = if (archive.hasBlock(key)) 0 else content.size
            if (capturedBytes + extra + (parts.size + 1) * PART_OVERHEAD > byteLimit) {
                span.setAttribute("maeve.status", "truncated")
                parts.add(BodyPart(block.path, null, true, "run_body_limit"))
                capturedBytes += archive.writeBody(span.id, parts)
                checkpoint(span)
                finish("run_body_limit", call.step.toLong(), block.path)
                return@record null
            }
            if (block.truncation != null) clipped = true
            capturedBytes += archive.putBlock(key, content, block.path)
            parts.add(BodyPart(block.path, key, block.truncation != null, block.truncation))
        }
        capturedBytes += archive.writeBody(span.id, parts)
        checkpoint(span)
        span
    }

    /** Label the request body relative to the previous request from the same model. */
    private fun describeChange(previous: List<Pair<String, String>>?, blocks: List<RequestBlock>): String {
        if (previous == null) return "first"
        val current = blocks.map { it.path to it.digest }
        val shared = minOf(previous.size, current.size)
        val firstDiff = (0 until shared).firstOrNull { previous[it] != current[it] } ?: shared
        return when {
            firstDiff == blocks.size && firstDiff == previous.size -> "same"
            firstDiff == previous.size -> "append " + blocks[firstDiff].path
            firstDiff == blocks.size -> "removed " + previous[firstDiff].first
            previous[firstDiff].first != blocks[firstDiff].path &&
                current.none { it.first == previous[firstDiff].first } -> "removed " + previous[firstDiff].first
            else -> blocks[firstDiff].path
        }
    }

    @Synchronized internal fun httpStatus(span: LocalSpan, code: Int) {
        if (span in pending) span.setAttribute("http.response.status_code", code.toLong())
    }

    @Synchronized internal fun end(
        span: LocalSpan?,
        status: String,
        input: Int?,
        cached: Int?,
        output: Int?,
        replyText: (() -> CapturedReply)? = null,
    ) {
        if (span == null || !accepting || span !in pending) return
        record(span.step) {
            input?.let { span.setAttribute("gen_ai.usage.input_tokens", it.toLong()) }
            cached?.let { span.setAttribute("gen_ai.usage.cache_read.input_tokens", it.toLong()) }
            output?.let { span.setAttribute("gen_ai.usage.output_tokens", it.toLong()) }
            replyText?.let { captureReply ->
                val reply = captureReply()
                val data = reply.text.toByteArray()
                val fits = capturedBytes + data.size <= byteLimit
                val reason = if (fits) reply.truncation else "run_body_limit"
                if (reason != null) {
                    clipped = true
                    span.setAttribute("maeve.trace.reply_truncation", reason)
                }
                val saved = if (fits) data else "[truncated]".toByteArray()
                archive.writeReply(span.id, saved)
                capturedBytes += saved.size
            }
            span.setAttribute("maeve.status", status)
            if (status == "error") span.error()
            pending.remove(span)
            seal(span)
        }
    }

    @Synchronized internal fun toolStarted(step: Int, name: String, callId: String, start: Long) {
        record(step.toLong()) {
            val span = openToolSpan(step, name, callId, start)
            toolSpans[step to callId] = span
            checkpoint(span)
        }
    }

    private fun openToolSpan(step: Int, name: String, callId: String?, start: Long): LocalSpan {
        val safeName = TracePayload.label(name)
        return child("execute_tool " + safeName, step, SpanKind.SPAN_KIND_INTERNAL, start).apply {
            setAttribute("gen_ai.operation.name", "execute_tool")
            setAttribute("gen_ai.tool.name", safeName)
            callId?.let { setAttribute("gen_ai.tool.call.id", TracePayload.label(it)) }
        }
    }

    @Synchronized override fun event(e: RunEvent) {
        record((e as? RunEvent.ToolResult)?.step?.toLong()) {
            when (e) {
                is RunEvent.ToolResult -> recordToolResult(e)
                is RunEvent.State -> if (e.name == "tools_opened") contextsJustChanged = true
                is RunEvent.TaskEnded -> outcome = e.outcome
                else -> Unit
            }
        }
    }

    private fun recordToolResult(result: RunEvent.ToolResult) {
        if (result.source != "model") return
        val span = result.callId?.let { toolSpans.remove(result.step to it) }
            ?: openToolSpan(result.step, result.tool, result.callId, result.atMs - result.durationMs)
        span.setAttribute("maeve.tool.duration_ms", result.durationMs)
        val failed = runCatching {
            Json.parseToJsonElement(result.resultJson).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull == false
        }.getOrDefault(false) || INLINE_FAILURE.containsMatchIn(result.resultJson)
        span.setAttribute("maeve.status", if (failed) "error" else "ok")
        if (failed) span.error()
        seal(span, TimeUnit.MILLISECONDS.toNanos(result.atMs))
        pending.remove(span)
    }

    private fun child(name: String, step: Int, kind: SpanKind, start: Long): LocalSpan {
        if (spanCount >= MAX_SPANS) throw SpanLimitReached()
        spanCount++
        return LocalSpan(rootSpan.traceId, name, rootSpan, kind, step.toLong(), TimeUnit.MILLISECONDS.toNanos(start))
            .setAttribute("maeve.step.index", step.toLong())
            .setAttribute("maeve.sequence", spanCount.toLong())
            .setAttribute("maeve.status", "partial")
            .also { pending.add(it) }
    }

    /** Cancellation, quota exhaustion and storage failure all seal the trace and reject late callbacks. */
    @Synchronized internal fun finish(reason: String? = null, step: Long? = null, block: String? = null) {
        if (!accepting) return
        accepting = false
        val interrupted = pending.isNotEmpty()
        pending.forEach { seal(it) }
        pending.clear()
        toolSpans.clear()
        previousBlocks.clear()
        val failure = storeFailure
        val stop = reason ?: when {
            failure != null -> failureReason(failure)
            outcome == "cancelled" -> "run_cancelled"
            interrupted -> "unfinished_spans"
            outcome == null -> "missing_run_outcome"
            else -> null
        }
        val state = if (stop != null || clipped) "partial" else outcome!!
        stop?.let { rootSpan.setAttribute("maeve.trace.stop_reason", it) }
        (step ?: failedStep)?.let { rootSpan.setAttribute("maeve.trace.stopped_step", it) }
        (block ?: (failure as? TraceDiskFull)?.block)?.let { rootSpan.setAttribute("maeve.trace.stopped_block", it) }
        outcome?.let { rootSpan.setAttribute("maeve.run.outcome", it) }
        rootSpan.setAttribute("maeve.status", state)
        if (state == "failed" || state == "error") rootSpan.error()
        seal(rootSpan)
    }

    private class SpanLimitReached : Exception()

    private fun failureReason(e: Exception): String = when (e) {
        is SpanLimitReached -> "span_limit"
        is TraceDiskFull -> "storage_limit"
        is IOException -> "write_error"
        else -> "capture_error"
    }

    private inline fun <T> record(step: Long?, block: () -> T): T? {
        if (!accepting) return null
        if (storeFailure != null) {
            finish()
            return null
        }
        return try {
            block()
        } catch (e: Exception) {
            finish(failureReason(e), step, (e as? TraceDiskFull)?.block)
            null
        } catch (_: StackOverflowError) {
            finish("capture_error", step)
            null
        }
    }

    private companion object {
        const val MAX_SPANS = 1024
        const val PART_OVERHEAD = 192
        val INLINE_FAILURE = Regex("""^\{\s*"ok"\s*:\s*false\b""")
    }
}

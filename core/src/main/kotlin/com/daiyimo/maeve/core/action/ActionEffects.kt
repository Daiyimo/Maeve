package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.io.AtomicFiles
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.ToolExecution
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** 私有副作用账本，与 UI/task 提交回执分开；只存身份和摘要，不存参数/正文或凭据。 */
class ActionEffectStore(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    @Serializable enum class State { Started, Applied, NotApplied, Unknown }
    @Serializable data class Receipt(
        val invocation: String, val intentId: String, val effectId: String, val runId: String,
        val action: String, val parametersDigest: String, val approvalDigest: String?, val backendSession: String,
        val startedSequence: Long, val startedAt: Long, val state: State = State.Started,
        val settledSequence: Long? = null, val settledAt: Long? = null, val evidenceDigest: String? = null,
        val evidence: String? = null,
    )
    @Serializable private data class Journal(val schemaVersion: Int = 1, val sequence: Long = 0, val receipts: List<Receipt> = emptyList())
    /** 同一会话同时只开始一个写动作；其他会话仍可独立操作各自的后端。 */
    private val locks = mutableMapOf<String, Mutex>()
    internal fun lock(scope: String): Mutex = synchronized(locks) { locks.getOrPut(scope) { Mutex() } }
    private fun file(scope: String) = File(directory, digest(scope) + ".json")
    private fun read(scope: String): Journal {
        val file = file(scope)
        if (!file.exists()) return Journal()
        check(file.length() <= MAX_BYTES) { "effect_receipts_unavailable" }
        return Json.decodeFromString<Journal>(file.readText()).also { journal ->
            check(journal.schemaVersion == 1 && journal.receipts.size <= MAX_RECEIPTS && journal.sequence >= 0)
            check(journal.receipts.map { it.invocation }.distinct().size == journal.receipts.size)
            check(journal.receipts.map { it.effectId }.distinct().size == journal.receipts.size)
            check(journal.receipts.all {
                it.startedSequence in 1..journal.sequence && it.runId.length in 1..160 && it.action.length in 1..160 &&
                    it.parametersDigest.matches(DIGEST) &&
                    if (it.state == State.Started) it.settledSequence == null && it.settledAt == null
                    else it.settledSequence != null && it.settledSequence > it.startedSequence && it.settledSequence <= journal.sequence && it.settledAt != null
            })
        }
    }
    private fun write(scope: String, journal: Journal) {
        val bytes = Json.encodeToString(journal).toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_BYTES) { "effect_receipts_capacity" }
        AtomicFiles.write(file(scope), bytes)
    }

    @Synchronized internal fun begin(scope: String, runId: String, invocation: String, call: Part.ToolCall, backend: String, approval: String?): Receipt {
        val journal = read(scope)
        require(runId.length in 1..160 && call.name.length in 1..160)
        val params = digest(canonical(Json.parseToJsonElement(call.argsJson)).toString())
        journal.receipts.firstOrNull { it.invocation == invocation }?.let { old ->
            if (old.action != call.name || old.parametersDigest != params || old.runId != runId) throw ActionEffects.Blocked("effect_conflict")
            throw ActionEffects.Blocked(if (old.state in setOf(State.Started, State.Unknown)) "effect_unknown" else "effect_already_recorded")
        }
        // 换 API/GUI、换调用 ID 或换运行编号都不能清掉同一会话的 unknown。
        if (journal.receipts.any { it.state in setOf(State.Started, State.Unknown) }) throw ActionEffects.Blocked("effect_unknown")
        if (journal.receipts.size >= MAX_RECEIPTS) throw ActionEffects.Blocked("effect_receipts_capacity")
        val receipt = Receipt(invocation, UUID.randomUUID().toString(), UUID.randomUUID().toString(), runId,
            call.name, params, approval, backend, journal.sequence + 1, clock())
        write(scope, Journal(sequence = receipt.startedSequence, receipts = journal.receipts + receipt))
        return receipt
    }

    @Synchronized internal fun settle(scope: String, receipt: Receipt, state: State, evidence: JsonObject?, reason: String) {
        val journal = read(scope)
        check(journal.receipts.any { it.effectId == receipt.effectId && it.state == State.Started })
        val settled = receipt.copy(state = state, settledSequence = journal.sequence + 1, settledAt = clock(),
            evidenceDigest = evidence?.let { digest(canonical(it).toString()) }, evidence = reason)
        write(scope, Journal(sequence = settled.settledSequence!!, receipts = journal.receipts.map { if (it.effectId == receipt.effectId) settled else it }))
    }

    @Synchronized fun receipts(scope: String): List<Receipt> = read(scope).receipts.toList()
    /** 仅供可信本地界面在用户逐条核对后调用；不恢复旧任务或旧授权。 */
    @Synchronized fun reconcile(scope: String, effectId: String, startedSequence: Long, applied: Boolean) {
        val journal = read(scope)
        val old = journal.receipts.single { it.effectId == effectId }
        check(old.startedSequence == startedSequence && old.state in setOf(State.Started, State.Unknown)) { "effect_changed" }
        val settled = old.copy(state = if (applied) State.Applied else State.NotApplied, settledSequence = journal.sequence + 1,
            settledAt = clock(), evidence = "local_user_review", evidenceDigest = digest("$effectId:$startedSequence:$applied"))
        write(scope, Journal(sequence = settled.settledSequence!!, receipts = journal.receipts.map { if (it.effectId == effectId) settled else it }))
    }
    @Synchronized fun delete(scope: String) { val file = file(scope); check(!file.exists() || file.delete()) }

    companion object {
        const val MAX_RECEIPTS = 4096
        const val MAX_BYTES = 4 * 1024 * 1024
        private val DIGEST = Regex("[a-f0-9]{64}")
        internal fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun canonical(value: JsonElement): JsonElement = when (value) {
            is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
            is JsonArray -> JsonArray(value.map(::canonical))
            else -> value
        }
    }
}

/** executionSession 是本次后端装配的随机身份；不代表已完成 helper epoch/屏幕授权绑定。 */
class ActionEffects(
    private val store: ActionEffectStore,
    private val scope: String,
    private val runId: String,
    private val executionSession: String = UUID.randomUUID().toString(),
) {
    class Blocked(val code: String) : IllegalStateException(code)

    suspend fun checkSettled() {
        val unknown = try { withContext(Dispatchers.IO) { store.receipts(scope).any { it.state in setOf(ActionEffectStore.State.Started, ActionEffectStore.State.Unknown) } } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw Blocked("effect_receipts_unavailable").apply { initCause(e) } }
        if (unknown) throw Blocked("effect_unknown")
    }

    suspend fun execute(call: Part.ToolCall, origin: Origin, approvalDigest: String? = null, action: suspend () -> ToolExecution): ToolExecution = store.lock(scope).withLock {
        val invocation = ActionEffectStore.digest(Json.encodeToString(listOf(runId, origin.policy.source, origin.step.toString(), call.id)))
        val receipt = try {
            withContext(Dispatchers.IO) { store.begin(scope, runId, invocation, call, executionSession, approvalDigest) }
        } catch (e: Blocked) { throw e } catch (e: CancellationException) { throw e }
        catch (e: Exception) { throw Blocked("effect_receipts_unavailable").apply { initCause(e) } }
        val result = try { action() } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { store.settle(scope, receipt, ActionEffectStore.State.Unknown, null, "interrupted") }
            }
            if (failure is CancellationException) throw failure
            throw Blocked("effect_unknown").apply { initCause(failure) }
        }
        val state = when {
            result.content["executed"] == JsonPrimitive(false) -> ActionEffectStore.State.NotApplied
            result.content["ok"] == JsonPrimitive(true) -> ActionEffectStore.State.Applied
            else -> ActionEffectStore.State.Unknown
        }
        try {
            withContext(NonCancellable + Dispatchers.IO) { store.settle(scope, receipt, state, result.content,
                when (state) { ActionEffectStore.State.Applied -> "tool_acknowledged"; ActionEffectStore.State.NotApplied -> "tool_declared_not_executed"; else -> "outcome_unconfirmed" }) }
        } catch (_: Exception) { throw Blocked("effect_receipts_unavailable") }
        if (state == ActionEffectStore.State.Unknown) throw Blocked("effect_unknown")
        result
    }
}

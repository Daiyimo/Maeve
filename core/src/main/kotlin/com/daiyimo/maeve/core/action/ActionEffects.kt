package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.ActionDelivery
import com.daiyimo.maeve.core.tool.ToolExecution
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

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

    suspend fun execute(
        call: Part.ToolCall, origin: Origin, approvalDigest: String? = null,
        outward: Boolean = false, finishOnCancellation: Boolean = false, action: suspend () -> ToolExecution,
    ): ToolExecution = store.lock(scope).withLock {
        val caller = currentCoroutineContext()
        if (!finishOnCancellation) return@withLock executeRecorded(call, origin, approvalDigest, outward, action)
        // A bounded input action can finish its readback after cancellation. Honour cancellation before
        // starting it and again after settling its receipt, so the caller cannot start another action.
        val result = withContext(NonCancellable) {
            caller.ensureActive()
            executeRecorded(call, origin, approvalDigest, outward, action)
        }
        caller.ensureActive()
        result
    }

    private suspend fun executeRecorded(
        call: Part.ToolCall, origin: Origin, approvalDigest: String?, outward: Boolean, action: suspend () -> ToolExecution,
    ): ToolExecution {
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
        val content = result.content
        val effect = (content["effect"] as? JsonObject)?.get("kind")
        val uncertain = result.delivery == ActionDelivery.Unknown || content["delivery"] == JsonPrimitive("unknown") ||
            content["submissionUnconfirmed"] == JsonPrimitive(true) || content["error"] == JsonPrimitive("action_timeout") ||
            outward && effect == JsonPrimitive("no_effect")
        val error = (content["error"] as? JsonPrimitive)?.contentOrNull
        val state = when {
            // 只有明确未投递/未执行才能证明 NotApplied;sent + ok=false 仍可能已经产生副作用。
            result.delivery == ActionDelivery.NotSent || content["delivery"] == JsonPrimitive("not_sent") || content["executed"] == JsonPrimitive(false) -> ActionEffectStore.State.NotApplied
            // 旧工具在发送前解析失败时可能只回 unknown;这些错误码本身证明输入没有投递。
            error in PRE_DISPATCH_ERRORS -> ActionEffectStore.State.NotApplied
            uncertain -> ActionEffectStore.State.Unknown
            content["ok"] == JsonPrimitive(true) -> ActionEffectStore.State.Applied
            else -> ActionEffectStore.State.Unknown
        }
        try {
            withContext(NonCancellable + Dispatchers.IO) { store.settle(scope, receipt, state, result.content,
                when (state) {
                    ActionEffectStore.State.Applied -> "tool_acknowledged"
                    // 失败结算成 NotApplied 的,把错误码写进证据,便于核对时看清是哪一步没生效
                    ActionEffectStore.State.NotApplied -> (result.content["error"] as? JsonPrimitive)?.content ?: "tool_declared_not_executed"
                    else -> "outcome_unconfirmed"
                }) }
        } catch (_: Exception) { throw Blocked("effect_receipts_unavailable") }
        if (state == ActionEffectStore.State.Unknown) throw Blocked("effect_unknown")
        return result
    }

    companion object {
        private val PRE_DISPATCH_ERRORS = setOf(
            "invalid_arguments", "screen_unavailable", "element_not_found", "element_ambiguous", "visual_first",
            "vision_timeout", "vision_unavailable", "target_unresolved", "ref_expired",
            "tap_text_mismatch", "viewport_unavailable", "no_app_open", "invalid_gesture",
            "launch_failed", "app_not_found", "no_focus",
        )
    }
}

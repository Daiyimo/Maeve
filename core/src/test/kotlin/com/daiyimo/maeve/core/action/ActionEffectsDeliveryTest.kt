package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.ActionDelivery
import com.daiyimo.maeve.core.tool.ToolExecution
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActionEffectsDeliveryTest {
    @get:Rule val temp = TemporaryFolder()
    private val call = Part.ToolCall("model_call", "phone_back", "{}")
    private fun result(ok: Boolean = true, executed: Boolean? = null) = ToolExecution(buildJsonObject {
        put("ok", ok); executed?.let { put("executed", it) }
    })

    @Test fun outwardActionWithoutObservedEffectRequiresReconciliation() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        val failure = runCatching {
            effects.execute(call, Origin.Model(1), outward = true) {
                ToolExecution(buildJsonObject {
                    put("ok", true)
                    put("effect", buildJsonObject { put("kind", "no_effect") })
                })
            }
        }.exceptionOrNull() as ActionEffects.Blocked
        assertEquals("effect_unknown", failure.code)
        assertEquals(ActionEffectStore.State.Unknown, store.receipts("c").single().state)
    }

    @Test fun unknownDeliveryIsNeverRecordedAsNotApplied() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        val unknown = result(false).with(
            content = buildJsonObject { put("ok", false); put("delivery", "unknown") },
            delivery = ActionDelivery.Unknown,
        )
        val error = runCatching { effects.execute(call, Origin.Model(1)) { unknown } }.exceptionOrNull() as ActionEffects.Blocked
        assertEquals("effect_unknown", error.code)
        assertEquals(ActionEffectStore.State.Unknown, store.receipts("c").single().state)
    }

    @Test fun explicitNotSentIsNotApplied() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        val notSent = result(false).with(
            content = buildJsonObject { put("ok", false); put("delivery", "not_sent") },
            delivery = ActionDelivery.NotSent,
        )
        effects.execute(call, Origin.Model(1)) { notSent }
        assertEquals(ActionEffectStore.State.NotApplied, store.receipts("c").single().state)
    }

    @Test fun sentFailureWithoutNotExecutedProofIsUnknownAndBlocksBlindRetry() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        val error = runCatching { effects.execute(call, Origin.Model(1)) { result(false) } }.exceptionOrNull() as ActionEffects.Blocked
        assertEquals("effect_unknown", error.code)
        val receipt = store.receipts("c").single()
        assertEquals(ActionEffectStore.State.Unknown, receipt.state)
        assertEquals("outcome_unconfirmed", receipt.evidence)
        var nextRan = false
        assertTrue(runCatching { effects.execute(call.copy(id = "next"), Origin.Model(2)) { nextRan = true; result() } }.exceptionOrNull() is ActionEffects.Blocked)
        assertFalse(nextRan)
    }

    @Test fun notExecutedProofWithErrorCodeKeepsItAsEvidence() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        effects.execute(call, Origin.Model(1)) { ToolExecution(buildJsonObject {
            put("ok", false); put("executed", false); put("error", "tap_text_mismatch")
        }) }
        val receipt = store.receipts("c").single()
        assertEquals(ActionEffectStore.State.NotApplied, receipt.state)
        assertEquals("tap_text_mismatch", receipt.evidence)
    }

    @Test fun validJsonInvalidArgumentsAreNotAppliedAndDoNotPoisonNextWrite() = runTest {
        val store = ActionEffectStore(temp.root)
        val screen = com.daiyimo.maeve.core.tool.FakeScreen()
        val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
        val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, "c", "run"))
        val bad = call.copy(name = "phone_long_press", argsJson = """{"x":500,"y":500,"durationMs":1}""")
        val rejected = executor.run(bad, Origin.Model(1)) as ActionOutcome.Ran
        assertEquals(JsonPrimitive("invalid_arguments"), rejected.content["error"])
        assertEquals(JsonPrimitive("not_sent"), rejected.content["delivery"])
        assertTrue(store.receipts("c").isEmpty())
        assertTrue(screen.calls.none { it.startsWith("tap") })

        val good = call.copy(id = "next", name = "phone_tap", argsJson = """{"x":500,"y":100}""")
        assertTrue(executor.run(good, Origin.Model(2)) is ActionOutcome.Ran)
        assertEquals(listOf(ActionEffectStore.State.Applied), store.receipts("c").map { it.state })
    }

    @Test fun concretePreDispatchScreenFailuresAreNotAppliedAndAllowNextWrite() = runTest {
        for (reason in listOf("no_app_open", "invalid_gesture", "launch_failed", "app_not_found", "no_focus")) {
            val store = ActionEffectStore(temp.root)
            val screen = com.daiyimo.maeve.core.tool.FakeScreen().apply { result = com.daiyimo.maeve.core.uistate.ActionResult.Failed(reason) }
            val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
            val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
                recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, reason, "run"))
            val tap = call.copy(name = "phone_tap", argsJson = """{"x":500,"y":100}""")
            val rejected = executor.run(tap, Origin.Model(1)) as ActionOutcome.Ran
            assertEquals(reason, ActionDelivery.NotSent, rejected.exec.delivery)
            assertEquals(JsonPrimitive("not_sent"), rejected.content["delivery"])
            assertEquals(ActionEffectStore.State.NotApplied, store.receipts(reason).single().state)

            screen.result = com.daiyimo.maeve.core.uistate.ActionResult.Ok(changed = true)
            assertTrue(reason, executor.run(tap.copy(id = "next"), Origin.Model(2)) is ActionOutcome.Ran)
            assertEquals(reason, listOf(ActionEffectStore.State.NotApplied, ActionEffectStore.State.Applied), store.receipts(reason).map { it.state })
        }
    }

    @Test fun sentFailureWithoutProofOfNoEffectRemainsUnknown() = runTest {
        val store = ActionEffectStore(temp.root)
        val screen = com.daiyimo.maeve.core.tool.FakeScreen().apply { result = com.daiyimo.maeve.core.uistate.ActionResult.Failed("app_not_in_front") }
        val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
        val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, "c", "run"))
        val tap = call.copy(name = "phone_tap", argsJson = """{"x":500,"y":100}""")
        val error = runCatching { executor.run(tap, Origin.Model(1)) }.exceptionOrNull() as ActionEffects.Blocked
        assertEquals("effect_unknown", error.code)
        assertEquals(ActionEffectStore.State.Unknown, store.receipts("c").single().state)
    }

    @Test fun executedTapThatFailsAfterDeliveryRequiresReconciliationBeforeNextWrite() = runTest {
        val store = ActionEffectStore(temp.root)
        val screen = com.daiyimo.maeve.core.tool.FakeScreen()
        val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
        val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, "c", "run"))
        val tap = call.copy(name = "phone_tap", argsJson = """{"x":500,"y":100}""")
        screen.result = com.daiyimo.maeve.core.uistate.ActionResult.Failed("action_failed")
        val error = runCatching { executor.run(tap, Origin.Model(1)) }.exceptionOrNull() as ActionEffects.Blocked
        assertEquals("effect_unknown", error.code)
        val receipt = store.receipts("c").single()
        assertEquals(ActionEffectStore.State.Unknown, receipt.state)
        assertTrue(runCatching { executor.run(tap.copy(id = "next"), Origin.Model(2)) }.exceptionOrNull() is ActionEffects.Blocked)
        assertEquals(1, screen.calls.count { it.startsWith("tap") })

        store.reconcile("c", receipt.effectId, receipt.startedSequence, applied = false)
        screen.result = com.daiyimo.maeve.core.uistate.ActionResult.Ok()
        val next = executor.run(tap.copy(id = "next"), Origin.Model(2))
        assertTrue(next is ActionOutcome.Ran)
        assertEquals(listOf(ActionEffectStore.State.NotApplied, ActionEffectStore.State.Applied), store.receipts("c").map { it.state })
    }
}

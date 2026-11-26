package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import com.daiyimo.maeve.core.uistate.VisibleTextLine
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

    @Test fun knownPreDispatchErrorsAreNotAppliedEvenWhenLegacyDeliveryIsUnknown() = runTest {
        for (code in listOf("element_not_found", "vision_timeout", "element_ambiguous", "screen_unavailable")) {
            val store = ActionEffectStore(temp.root)
            val effects = ActionEffects(store, code, "run")
            val legacy = ToolExecution(buildJsonObject {
                put("ok", false); put("error", code); put("delivery", "unknown")
            }, delivery = ActionDelivery.Unknown)
            effects.execute(call, Origin.Model(1)) { legacy }
            assertEquals(code, ActionEffectStore.State.NotApplied, store.receipts(code).single().state)

            effects.execute(call.copy(id = "next"), Origin.Model(2)) { result() }
            assertEquals(code, listOf(ActionEffectStore.State.NotApplied, ActionEffectStore.State.Applied), store.receipts(code).map { it.state })
        }
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

    @Test fun ocrMismatchBeforeDispatchIsNotAppliedAndDoesNotPoisonNextWrite() = runTest {
        val surface = UiElement("@e1", "SurfaceView", "Game view", 500, 500, listOf(0, 0, 999, 999), null, true, false, false)
        var lines = listOf(
            VisibleTextLine("K/DA不朽", listOf(30, 240, 100, 290)),
            VisibleTextLine("巨星之路", listOf(30, 420, 100, 470)),
            VisibleTextLine("霓裳天阙", listOf(30, 610, 100, 650)),
            VisibleTextLine("盛世长安", listOf(30, 780, 100, 820)),
            VisibleTextLine("奖池", listOf(170, 30, 220, 70)),
        )
        val base = com.daiyimo.maeve.core.tool.FakeScreen()
        val screen = object : com.daiyimo.maeve.core.uistate.ScreenPort by base {
            override suspend fun snapshot() = ScreenSnapshot("s1", "com.tencent.jkchess", listOf(surface), 2670, 1200)
            override suspend fun peek() = snapshot()
            override suspend fun readVisibleText() = lines
        }
        val store = ActionEffectStore(temp.root)
        val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
        tools.executeRead(Part.ToolCall("observe", "phone_observe", "{}"))
        val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, "c", "run"))
        val tap = call.copy(name = "phone_tap", argsJson = """{"x":65,"y":626,"target":"魔典菜单项"}""")
        val rejected = executor.run(tap, Origin.Model(1)) as ActionOutcome.Ran
        assertEquals(JsonPrimitive("tap_text_mismatch"), rejected.content["error"])
        assertEquals(JsonPrimitive("not_sent"), rejected.content["delivery"])
        assertEquals(JsonPrimitive(false), rejected.content["executed"])
        assertTrue(base.calls.none { it.startsWith("tap") })
        assertEquals(ActionEffectStore.State.NotApplied, store.receipts("c").single().state)

        lines = listOf(VisibleTextLine("@魔典", listOf(20, 550, 100, 610)))
        val next = executor.run(tap.copy(id = "next", argsJson = """{"x":60,"y":580,"target":"魔典菜单项"}"""), Origin.Model(2)) as ActionOutcome.Ran
        assertEquals(JsonPrimitive(true), next.content["ok"])
        assertEquals(1, base.calls.count { it.startsWith("tap") })
        assertEquals(listOf(ActionEffectStore.State.NotApplied, ActionEffectStore.State.Applied), store.receipts("c").map { it.state })
    }

    @Test fun staleLocatedPointIsRefusedBeforeEffectsAndDoesNotPoisonNextWrite() = runTest {
        val store = ActionEffectStore(temp.root)
        val screen = com.daiyimo.maeve.core.tool.FakeScreen()
        val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
        val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, "c", "run"))
        val stale = call.copy(name = "phone_tap", argsJson = """{"ref":"@v1","snapshotId":"loc_missing"}""")
        val rejected = executor.run(stale, Origin.Model(1)) as ActionOutcome.Refused
        assertEquals("target_unresolved", rejected.code)
        assertEquals(ActionDelivery.NotSent, rejected.execution().delivery)
        assertTrue(store.receipts("c").isEmpty())
        assertTrue(screen.calls.none { it.startsWith("tap") })
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

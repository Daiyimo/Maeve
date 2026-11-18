package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.execute
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActionEffectsTest {
    @get:Rule val temp = TemporaryFolder()
    private val call = Part.ToolCall("model_call", "phone_back", "{}")
    private fun result(ok: Boolean = true, executed: Boolean? = null) = ToolExecution(buildJsonObject {
        put("ok", ok); executed?.let { put("executed", it) }
    })
    /** 既没说成功、也没说没执行、连 ok 都没有的畸形结果:真不知道有没有生效。 */
    private fun malformed() = ToolExecution(buildJsonObject { put("note", "no outcome reported") })

    @Test fun savesStartedBeforeExecutionAndNeverReexecutesAcknowledgedInvocation() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "conversation", "run")
        var calls = 0
        effects.execute(call, Origin.Model(1), "approved-digest") {
            assertEquals(ActionEffectStore.State.Started, store.receipts("conversation").single().state)
            calls++; result()
        }
        val receipt = store.receipts("conversation").single()
        assertEquals(ActionEffectStore.State.Applied, receipt.state)
        assertEquals("approved-digest", receipt.approvalDigest)
        assertEquals(1L, receipt.startedSequence); assertEquals(2L, receipt.settledSequence)
        val error = runCatching { effects.execute(call, Origin.Model(1)) { calls++; result() } }.exceptionOrNull()
        assertEquals("effect_already_recorded", (error as ActionEffects.Blocked).code)
        assertEquals(1, calls)
    }

    @Test fun interruptedReceiptBlocksDifferentApiGuiBackendAndRunAfterRestart() = runTest {
        val store = ActionEffectStore(temp.root)
        store.begin("conversation", "old-run", "invocation", call, "old-backend", null)
        val restarted = ActionEffects(ActionEffectStore(temp.root), "conversation", "new-run", "new-backend")
        var executed = false
        val error = runCatching { restarted.execute(call.copy(id = "new-id", name = "web_click"), Origin.Model(9)) { executed = true; result() } }.exceptionOrNull()
        assertEquals("effect_unknown", (error as ActionEffects.Blocked).code)
        assertFalse(executed)
        assertEquals(ActionEffectStore.State.Started, store.receipts("conversation").single().state)
    }

    @Test fun unconfirmedResultStopsAndCannotBeReplayedWithNewCallId() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        val error = runCatching { effects.execute(call, Origin.Model(1)) { malformed() } }.exceptionOrNull()
        assertEquals("effect_unknown", (error as ActionEffects.Blocked).code)
        assertEquals(ActionEffectStore.State.Unknown, store.receipts("c").single().state)
        assertEquals("outcome_unconfirmed", store.receipts("c").single().evidence)
        assertTrue(runCatching { effects.execute(call.copy(id = "retry"), Origin.Model(2)) { error("must not run") } }.exceptionOrNull() is ActionEffects.Blocked)
        assertTrue(runCatching { effects.checkSettled() }.exceptionOrNull() is ActionEffects.Blocked)
    }

    @Test fun cleanFailureIsNotAppliedAndDoesNotBlockNextWrite() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        // 干净地报了失败(ok=false):动作确定没生效,记 NotApplied,不抛、也不卡住后面的写动作
        val ran = effects.execute(call, Origin.Model(1)) { result(false) }
        assertEquals(JsonPrimitive(false), ran.content["ok"])
        val receipt = store.receipts("c").single()
        assertEquals(ActionEffectStore.State.NotApplied, receipt.state)
        assertEquals("tool_declared_not_executed", receipt.evidence)
        var nextRan = false
        effects.execute(call.copy(id = "next"), Origin.Model(2)) { nextRan = true; result() }
        assertTrue(nextRan)
        assertEquals(listOf(ActionEffectStore.State.NotApplied, ActionEffectStore.State.Applied), store.receipts("c").map { it.state })
    }

    @Test fun cleanFailureWithErrorCodeKeepsItAsEvidence() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        effects.execute(call, Origin.Model(1)) { ToolExecution(buildJsonObject { put("ok", false); put("error", "tap_text_mismatch") }) }
        val receipt = store.receipts("c").single()
        assertEquals(ActionEffectStore.State.NotApplied, receipt.state)
        assertEquals("tap_text_mismatch", receipt.evidence)
    }

    @Test fun notExecutedProofAndExplicitNewIntentRemainDistinct() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        effects.execute(call, Origin.Model(1)) { result(false, executed = false) }
        effects.execute(call.copy(id = "new-intent"), Origin.Model(2)) { result() }
        val receipts = store.receipts("c")
        assertEquals(listOf(ActionEffectStore.State.NotApplied, ActionEffectStore.State.Applied), receipts.map { it.state })
        assertEquals(2, receipts.map { it.intentId }.distinct().size)
        assertEquals(2, receipts.map { it.effectId }.distinct().size)
    }

    @Test fun cancellationRetainsUnknownAndReleasesLock() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        val started = CompletableDeferred<Unit>()
        val job = launch { effects.execute(call, Origin.Model(1)) { started.complete(Unit); awaitCancellation() } }
        started.await(); job.cancelAndJoin()
        assertEquals(ActionEffectStore.State.Unknown, store.receipts("c").single().state)
        assertTrue(runCatching { effects.execute(call.copy(id = "retry"), Origin.Model(2)) { error("must not run") } }.exceptionOrNull() is ActionEffects.Blocked)
    }

    @Test fun concurrentIdenticalInvocationRunsOnce() = runTest {
        val store = ActionEffectStore(temp.root)
        var calls = 0
        val work = (1..2).map { async {
            runCatching { ActionEffects(store, "c", "run").execute(call, Origin.Model(1)) { calls++; delay(10); result() } }
        } }.awaitAll()
        assertEquals(1, calls); assertEquals(1, work.count { it.isSuccess })
    }

    @Test fun storageFailurePreventsDispatchAndCorruptionIsPreserved() = runTest {
        val store = ActionEffectStore(temp.root)
        val file = File(temp.root, ActionEffectStore.digest("c") + ".json")
        File(temp.root, file.name + ".tmp").mkdir()
        val failure = runCatching { ActionEffects(store, "c", "run").execute(call, Origin.Model(1)) { error("must not run") } }.exceptionOrNull()
        assertEquals("effect_receipts_unavailable", (failure as ActionEffects.Blocked).code)
        assertFalse(file.exists())
        file.writeText("{")
        assertTrue(runCatching { ActionEffects(store, "c", "run").execute(call, Origin.Model(1)) { error("must not run") } }.exceptionOrNull() is ActionEffects.Blocked)
        assertEquals("{", file.readText())
    }

    @Test fun settlementFailureKeepsStartedReceiptAndSensitiveParametersAreNotStored() = runTest {
        val store = ActionEffectStore(temp.root)
        val file = File(temp.root, ActionEffectStore.digest("c") + ".json")
        val error = runCatching {
            ActionEffects(store, "c", "run").execute(call.copy(argsJson = """{"text":"private-password-and-message"}"""), Origin.Model(1)) {
                File(temp.root, file.name + ".tmp").mkdir()
                ToolExecution(buildJsonObject { put("ok", true); put("text", "private-response") })
            }
        }.exceptionOrNull()
        assertEquals("effect_receipts_unavailable", (error as ActionEffects.Blocked).code)
        assertEquals(ActionEffectStore.State.Started, store.receipts("c").single().state)
        assertFalse(file.readText().contains("private-"))
    }

    @Test fun localReviewSettlesOnlySelectedReceiptAndDoesNotReplayOldInvocation() = runTest {
        val store = ActionEffectStore(temp.root)
        val effects = ActionEffects(store, "c", "run")
        runCatching { effects.execute(call, Origin.Model(1)) { malformed() } }
        val receipt = store.receipts("c").single()
        store.reconcile("c", receipt.effectId, receipt.startedSequence, applied = false)
        assertEquals("local_user_review", store.receipts("c").single().evidence)
        assertTrue(runCatching { effects.execute(call, Origin.Model(1)) { error("must not replay") } }.exceptionOrNull() is ActionEffects.Blocked)
        var nextRan = false
        effects.execute(call.copy(id = "explicit-new-task"), Origin.Model(2)) { nextRan = true; result() }
        assertTrue(nextRan)
        assertTrue(runCatching { store.reconcile("c", receipt.effectId, receipt.startedSequence, applied = true) }.isFailure)
    }

    @Test fun actualExecutorJournalsScreenWritesButNotMalformedCalls() = runTest {
        val store = ActionEffectStore(temp.root)
        val screen = com.daiyimo.maeve.core.tool.FakeScreen()
        val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
        tools.execute(Part.ToolCall("read", "phone_observe", "{}"))
        val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, "c", "run"))
        executor.run(call.copy(name = "phone_tap", argsJson = "{"), Origin.Model(1))
        assertTrue(store.receipts("c").isEmpty())
        val tap = call.copy(name = "phone_tap", argsJson = """{"ref":"@e1","snapshotId":"snap1"}""")
        executor.run(tap, Origin.Model(2))
        assertEquals(ActionEffectStore.State.Applied, store.receipts("c").single().state)
        assertTrue(runCatching { executor.run(tap, Origin.Model(2)) }.exceptionOrNull() is ActionEffects.Blocked)
        assertEquals(1, screen.calls.count { it.startsWith("tap") })
    }

    @Test fun executedTapThatCleanlyFailsIsNotAppliedAndDoesNotBlockNext() = runTest {
        val store = ActionEffectStore(temp.root)
        val screen = com.daiyimo.maeve.core.tool.FakeScreen()
        val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
        val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, "c", "run"))
        val tap = call.copy(name = "phone_tap", argsJson = """{"x":500,"y":100}""")
        // 真机 bug 形态:点击已执行、端口干净地报失败(ok=false)。旧逻辑误判 Unknown 抛 effect_unknown 并卡住整个会话。
        screen.result = com.daiyimo.maeve.core.uistate.ActionResult.Failed("action_failed")
        val failed = executor.run(tap, Origin.Model(1))
        assertTrue(failed is ActionOutcome.Ran)
        val receipt = store.receipts("c").single()
        assertEquals(ActionEffectStore.State.NotApplied, receipt.state)
        assertEquals("action_failed", receipt.evidence)
        // 同一会话的下一个写动作照常执行,不被上一条干净失败卡住
        screen.result = com.daiyimo.maeve.core.uistate.ActionResult.Ok()
        val next = executor.run(tap.copy(id = "next"), Origin.Model(2))
        assertTrue(next is ActionOutcome.Ran)
        assertEquals(listOf(ActionEffectStore.State.NotApplied, ActionEffectStore.State.Applied), store.receipts("c").map { it.state })
    }
}

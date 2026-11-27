package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.model.Part
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhoneTapCancellationTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun cancellingDuringPhoneTapReadbackKeepsItsAcknowledgement() = runTest {
        val store = ActionEffectStore(temp.root)
        val base = com.daiyimo.maeve.core.tool.FakeScreen()
        val dispatched = CompletableDeferred<Unit>()
        val acknowledge = CompletableDeferred<Unit>()
        val screen = object : com.daiyimo.maeve.core.uistate.ScreenPort by base {
            override suspend fun tapXY(x: Int, y: Int): com.daiyimo.maeve.core.uistate.ActionResult {
                val result = base.tapXY(x, y)
                dispatched.complete(Unit)
                acknowledge.await()
                return result
            }
        }
        val tools = com.daiyimo.maeve.core.assembly.toolDispatcher(screen)
        val executor = ActionExecutor(tools, com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), effects = ActionEffects(store, "c", "run"))
        val tap = Part.ToolCall("tap", "phone_tap", """{"x":500,"y":100}""")
        val job = launch { executor.run(tap, Origin.Model(1)) }
        dispatched.await()
        job.cancel()
        acknowledge.complete(Unit)
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, base.calls.count { it.startsWith("tap") })
        assertEquals(ActionEffectStore.State.Applied, store.receipts("c").single().state)
        ActionEffects(store, "c", "next-run").checkSettled()
    }
}

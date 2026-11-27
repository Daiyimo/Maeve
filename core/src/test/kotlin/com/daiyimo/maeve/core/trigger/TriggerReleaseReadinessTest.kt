package com.daiyimo.maeve.core.trigger

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerReleaseReadinessTest {
    @Test fun builtInWaitsForOptInAndStartsAtNextShanghaiMorning() {
        val seededAt = Instant.parse("2026-11-26T00:00:00Z").toEpochMilli()
        val optInAt = Instant.parse("2026-11-27T02:00:00Z").toEpochMilli()
        val store = MemoryTriggerStore()
        val builtIn = TriggerPreset.missingBuiltInSchedules(emptyList(), seededAt).single()
        store.upsert(builtIn)
        val engine = TriggerEngine(store, {}) { _, _ -> error("No check-in should be dispatched yet") }

        assertFalse(builtIn.enabled)
        engine.fire(optInAt)
        assertTrue(store.windows().isEmpty())
        assertTrue(engine.setEnabled(builtIn.id, true, optInAt))
        assertEquals(Instant.parse("2026-11-28T01:00:00Z").toEpochMilli(), store.get(builtIn.id)?.nextAtMs)
        engine.fire(optInAt)
        assertTrue(store.windows().isEmpty())
    }

    @Test fun declinedStartKeepsSameWindowAndRetriesOnWatchdog() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("task", "thread", "goal", nextAtMs = 1_000, intervalMs = 10_000))
        var alarm: Long? = null
        val attempts = mutableListOf<String>()
        val engine = TriggerEngine(store, { alarm = it }, idleRetryMs = 100) { _, window ->
            attempts += window.key
            if (attempts.size == 1) throw TriggerDispatchDeferred(IllegalStateException("background"))
        }

        engine.fire(1_000)
        assertEquals(TriggerWindowState.Deferred, store.windows().single().state)
        assertEquals(TriggerDispatchDeferred.REASON, store.windows().single().reason)
        assertEquals(1_100L, alarm)
        engine.fire(1_100)
        assertEquals(listOf("task:1000", "task:1000"), attempts)
        assertEquals(TriggerWindowState.Dispatched, store.windows().single().state)
        assertEquals(11_000L, store.get("task")?.nextAtMs)
    }

    @Test fun declinedConditionalActionKeepsCompletedCheck() {
        val plan = TriggerDefinition("task", "thread", "goal", nextAtMs = 1_000, conditionGoal = "condition")
        val window = TriggerWindow(plan.id, 900, 900, state = TriggerWindowState.DeferredAction)
        val store = MemoryTriggerStore(TriggerSnapshot(listOf(plan), listOf(window)))
        val engine = TriggerEngine(store, {}) { _, _ -> throw TriggerDispatchDeferred(IllegalStateException("background")) }

        engine.fire(1_000)
        assertEquals(TriggerWindowState.DeferredAction, store.windows().single().state)
        assertEquals(TriggerDispatchDeferred.REASON, store.windows().single().reason)
    }
}

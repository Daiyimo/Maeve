package com.daiyimo.maeve.task

import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.core.run.MemoryRunStore
import com.daiyimo.maeve.core.run.Outbox
import com.daiyimo.maeve.core.run.RunId
import com.daiyimo.maeve.core.run.RunPhase
import com.daiyimo.maeve.core.run.SendOutcome
import com.daiyimo.maeve.core.run.StoredRun
import com.daiyimo.maeve.i18n.ChineseTexts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledRunRecoveryTest {
    @get:Rule val texts = ChineseTexts()
    @get:Rule val tmp = TemporaryFolder()

    @Test fun deliveringRestoredReplyDoesNotConfirmAnUnverifiedScheduledTask() = runTest(UnconfinedTestDispatcher()) {
        val store = MemoryRunStore()
        val deliveries = mutableListOf<String>()
        val results = mutableListOf<Pair<String, Boolean?>>()
        val channel = LocalChannel { _, delivery ->
            deliveries += delivery.id
            SendOutcome.Delivered
        }
        val triggers = object : TriggerOutcomes {
            override fun interrupted(key: String, reason: String) = Unit
            override fun finishTask(key: String, successful: Boolean?, reason: String?) {
                results += key to successful
            }
        }
        val recovery = RunRecovery(store, Outbox(), { channel }, triggers, ConversationStore(tmp.root), this)
        store.save(StoredRun(RunId("a"), "thread", "任务", RunPhase.Aftercare, reply = "只完成部分步骤",
            deliveryId = "d1", triggerWindowKey = "schedule:1", runSucceeded = null))
        recovery.recover()
        assertEquals(listOf("d1"), deliveries)
        assertEquals(listOf<Pair<String, Boolean?>>("schedule:1" to null), results)
    }
}

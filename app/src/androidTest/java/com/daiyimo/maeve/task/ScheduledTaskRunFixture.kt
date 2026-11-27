package com.daiyimo.maeve.task

import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.core.agent.Turn
import com.daiyimo.maeve.core.general.HISTORY_READ
import com.daiyimo.maeve.core.model.ModelSession
import com.daiyimo.maeve.core.trigger.TriggerDefinition
import com.daiyimo.maeve.core.trigger.TriggerWindowState
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal class ScheduledTaskRunFixture(
    private val app: MaeveApp,
    private val plan: TriggerDefinition,
    private val answer: String,
    token: String,
) : AutoCloseable {
    private val model = ScriptedModel()
    private val conversations = ConversationStore.shared(app)
    private val earlierFailure = Turn("旧任务", "旧故障线索 $token", "failed", runId = "old-$token")
    private val sessions = LinkedBlockingQueue<ModelSession>()

    fun install() {
        conversations.append(plan.threadId, earlierFailure)
        testTasks.modelFactory = { _, session ->
            sessions.add(session)
            model
        }
        testTasks.lightModelFactory = { _, _ -> null }
        testTasks.visionModelFactory = { _, _ -> null }
    }

    fun completeRun(round: Int): ModelSession {
        val execution = model.awaitCall()
        assertTrue(execution.messages.toString().contains(plan.goal))
        assertFalse("Previous failure leaked into execution", execution.messages.toString().contains(earlierFailure.outcome))
        assertFalse(execution.tools.any { it.name == HISTORY_READ })
        model.answer(answer)

        val judgement = model.awaitCall()
        assertTrue(judgement.messages.toString().contains("<reply>"))
        assertFalse("Previous failure leaked into judgement", judgement.messages.toString().contains(earlierFailure.outcome))
        model.answer("""{"outcome":"done","reason":"测试回复已核对"}""")
        val finished = DeviceSupport.pollUntil(20_000) {
            val delivered = TriggerRuntime.snapshot(app).windows.count {
                it.triggerId == plan.id && it.state == TriggerWindowState.Delivered
            }
            true.takeIf { delivered == round && activeRun() == null }
        }
        assertEquals(true, finished)
        assertEquals(listOf(earlierFailure.outcome) + List(round) { answer }, conversations.turns(plan.threadId).map { it.outcome })
        return sessions.remove()
    }

    private fun activeRun() = app.taskState.runs.value.firstOrNull { it.conversationId == plan.threadId }

    override fun close() {
        activeRun()?.let { TaskService.cancel(app, it.key) }
        DeviceSupport.pollUntil(10_000) { true.takeIf { activeRun() == null } }
        testTasks.modelFactory = TaskEnvironment.defaultModelFactory
        testTasks.lightModelFactory = TaskEnvironment.defaultLightModelFactory
        testTasks.visionModelFactory = TaskEnvironment.defaultVisionModelFactory
        TriggerRuntime.remove(app, plan.id)
        runBlocking { app.conversationDeleter.delete(plan.threadId) }
    }
}

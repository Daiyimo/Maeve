package com.daiyimo.maeve.core.run

import com.daiyimo.maeve.core.agent.FakeModel
import com.daiyimo.maeve.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RunBudgetTest {
    @Test fun independentExecutorsShareOneActionBudget() = runTest {
        val budget = RunBudget(maxSteps = 2)
        fun executor() = com.daiyimo.maeve.core.action.ActionExecutor(
            com.daiyimo.maeve.core.assembly.toolDispatcher(com.daiyimo.maeve.core.tool.FakeScreen()),
            com.daiyimo.maeve.core.guard.OperationGuards(), null, gate = {},
            recorder = com.daiyimo.maeve.core.action.ActionRecorder(com.daiyimo.maeve.core.record.RunSink.NONE), budget = budget)
        val parent = executor(); val child = executor()
        val call = Part.ToolCall("back", "phone_back", "{}")
        parent.run(call, com.daiyimo.maeve.core.action.Origin.Model(1))
        child.run(call, com.daiyimo.maeve.core.action.Origin.Model(1))
        assertEquals("steps", (runCatching { parent.run(call, com.daiyimo.maeve.core.action.Origin.Model(2)) }.exceptionOrNull() as RunBudget.Exceeded).limit)
    }
    @Test fun waitingUsesMonotonicActiveTimeOnly() {
        var nanos = 0L
        val budget = RunBudget(activeMs = 100) { nanos }
        nanos += 30_000_000
        budget.pause(true)
        nanos += 10_000_000_000
        assertEquals(70, budget.remainingMs())
        budget.pause(false)
        nanos += 70_000_000
        assertEquals("active_time", (runCatching { budget.step() }.exceptionOrNull() as RunBudget.Exceeded).limit)
    }
    @Test fun everyModelAndRetrySharesTheRequestLimit() = runTest {
        val budget = RunBudget(maxRequests = 1)
        val fake = FakeModel(ModelReply(emptyList(), StopReason.EndTurn, null))
        budget.wrap(fake).complete("", emptyList(), emptyList())
        val failure = runCatching { budget.wrap(fake).complete("", emptyList(), emptyList()) }.exceptionOrNull()
        assertEquals("requests", (failure as RunBudget.Exceeded).limit)
        assertEquals(1, fake.requests.size)
    }
    @Test fun stalledRequestIsBoundedAndDoesNotBecomeSuccess() = runTest {
        val budget = RunBudget(activeMs = 20)
        val slow = object : ChatModel {
            override val capabilities = Capabilities(true, false, 4096, false)
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
                delay(1000); return ModelReply(emptyList(), StopReason.EndTurn, null)
            }
        }
        assertTrue(runCatching { budget.wrap(slow).complete("", emptyList(), emptyList()) }.exceptionOrNull() is RunBudget.Exceeded)
    }
    @Test fun reportedTokenLimitStopsNextActionEvenWhenLogicalCallsRemain() = runTest {
        val budget = RunBudget(maxReportedTokens = 5)
        budget.wrap(FakeModel(ModelReply(emptyList(), StopReason.EndTurn, Usage(3, 2))))
            .complete("", emptyList(), emptyList())
        assertEquals(5L, budget.snapshot().reportedTokens)
        assertEquals("reported_tokens", (runCatching { budget.step() }.exceptionOrNull() as RunBudget.Exceeded).limit)
        assertFalse(budget.snapshot().usageIncomplete)
    }
    @Test fun compatibilityRetryCannotEscapeActualHttpAttemptLimit() = runTest {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.start()
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(400).setBody("unknown field stream_options"))
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("must not send second request"))
            val model = OpenAiCompatModel(server.url("/v1").toString().trimEnd('/'), "k", "m", Capabilities(true, false, 32000, true), okhttp3.OkHttpClient())
            val budget = RunBudget(maxHttpAttempts = 1)
            val failure = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { (budget.wrap(model) as StreamingChatModel).completeStreaming("", listOf(Message(Role.User, listOf(Part.Text("hi")), Author.User)), emptyList()) {} }.exceptionOrNull() }
            assertEquals("http_requests", (failure as RunBudget.Exceeded).limit)
            assertEquals(1, server.requestCount)
            assertEquals(1, budget.snapshot().httpAttempts)
            assertTrue(budget.snapshot().usageIncomplete)
        }
    }
}

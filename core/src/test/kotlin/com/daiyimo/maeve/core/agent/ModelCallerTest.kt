package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.Author
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelError
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.Role
import com.daiyimo.maeve.core.model.StreamingChatModel
import com.daiyimo.maeve.core.model.ToolSpec
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.testing.answerReply
import com.daiyimo.maeve.core.testing.fakeModel
import com.daiyimo.maeve.core.tool.FakeScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelCallerTest {
    private fun caller(model: ChatModel, events: MutableList<RunEvent> = mutableListOf()) =
        ModelCaller(model, toolDispatcher(FakeScreen()), null, SpeedFlags.OFF, RetryPolicy.NONE, events::add, { 0 })

    private fun pool(
        primary: ChatModel, backup: ChatModel, health: ModelHealthRegistry = ModelHealthRegistry { 0 },
        session: String = "session",
    ) = GroupPool("main", listOf(
        PoolMember(MemberKey("p", "primary"), primary, 0),
        PoolMember(MemberKey("p", "backup"), backup, 1),
    ), health, StickyTable(), session)

    @Test fun mainFailureSwitchesMemberAndStickyKeepsSuccessfulBackup() = runTest {
        val primary = fakeModel("primary") { error(ModelError.Auth()) }
        val backup = fakeModel("backup") { answer("first"); answer("second") }
        val health = ModelHealthRegistry { 0 }
        val events = mutableListOf<RunEvent>()
        val caller = caller(pool(primary, backup, health), events)
        val first = caller.askRouted(emptyList(), emptyList(), 1)
        assertEquals("first", first.first.parts.filterIsInstance<Part.Text>().single().text)
        assertSame(backup, first.second)
        assertSame(backup, caller.lastMainUsed)
        assertEquals(300_000L, health.snapshot(MemberKey("p", "primary")).cooldownUntilMs)
        assertTrue(events.any { it is RunEvent.State && it.name == "model_route" })

        val second = caller.askRouted(emptyList(), emptyList(), 2)
        assertSame(backup, second.second)
        assertEquals(1, primary.requests.size)
        assertEquals(2, backup.requests.size)
    }

    @Test fun rateLimitedRetryAfterBecomesSharedCooldownWhileBackupAnswers() = runTest {
        val primary = fakeModel("primary") { error(ModelError.RateLimited(90_000)) }
        val backup = fakeModel("backup") { answer("ok") }
        val health = ModelHealthRegistry { 0 }
        val result = caller(pool(primary, backup, health)).askRouted(emptyList(), emptyList(), 1)
        assertSame(backup, result.second)
        assertEquals(90_000L, health.snapshot(MemberKey("p", "primary")).cooldownUntilMs)
        assertFalse(health.available(MemberKey("p", "primary")))
    }

    @Test fun transientMemberGetsOneRetryThenFailover() = runTest {
        val primary = fakeModel("primary") { error(ModelError.Network()); error(ModelError.Network()) }
        val backup = fakeModel("backup") { answer("ok") }
        val model = pool(primary, backup)
        val caller = ModelCaller(model, toolDispatcher(FakeScreen()), null, SpeedFlags.OFF,
            RetryPolicy(maxRetries = 3, jitter = { 1.0 }), {}, { 0 })
        val result = caller.askRouted(emptyList(), emptyList(), 1)
        assertSame(backup, result.second)
        assertEquals(2, primary.requests.size)
        assertEquals(1, backup.requests.size)
    }

    @Test fun longRetryAfterSwitchesWithoutWaiting() = runTest {
        val primary = fakeModel("primary") { error(ModelError.RateLimited(30_000)) }
        val backup = fakeModel("backup") { answer("ok") }
        val caller = ModelCaller(pool(primary, backup), toolDispatcher(FakeScreen()), null, SpeedFlags.OFF,
            RetryPolicy(maxRetries = 3, jitter = { 1.0 }), {}, { 0 })
        val result = caller.askRouted(emptyList(), emptyList(), 1)
        assertSame(backup, result.second)
        assertEquals(1, primary.requests.size)
        assertEquals(0L, currentTime)
    }

    @Test fun contextTooLongIsCompactedOnSameMemberAndNeverSwitches() = runTest {
        val primary = fakeModel("primary") { error(ModelError.ContextTooLong()); error(ModelError.ContextTooLong()) }
        val backup = fakeModel("backup") { answer("should not run") }
        val error = runCatching { caller(pool(primary, backup)).ask(emptyList(), emptyList(), 1) }.exceptionOrNull()
        assertTrue(error is ModelError.ContextTooLong)
        assertEquals(1, primary.requests.size)
        assertTrue(backup.requests.isEmpty())
    }

    @Test fun contentRefusalStaysOnPrimary() = runTest {
        val primary = fakeModel("primary") { error(ModelError.ContentRefused()) }
        val backup = fakeModel("backup") { answer("should not run") }
        val error = runCatching { caller(pool(primary, backup)).ask(emptyList(), emptyList(), 1) }.exceptionOrNull()
        assertTrue(error is ModelError.ContentRefused)
        assertTrue(backup.requests.isEmpty())
    }

    @Test fun knownAndUnknownForeignRepliesLoseThinkingAndOpaaqueOnlyAcrossModels() = runTest {
        val primary = fakeModel("primary") { answer("primary"); answer("primary-again") }
        val backup = fakeModel("backup") { answer("backup"); answer("backup-again"); answer("backup-third") }
        val caller = caller(pool(primary, backup))
        val producedByBackup = Message(Role.Assistant, listOf(
            Part.Thinking("thought", "thinking-opaque"),
            Part.ToolCall("c1", "phone_tap", "{}", "call-opaque"),
        ), Author.Maeve)
        caller.noteReply(producedByBackup, backup, null)
        caller.askRouted(listOf(producedByBackup), emptyList(), 1, using = primary)
        val foreign = primary.requests.single().messages.single().parts
        assertTrue(foreign.none { it is Part.Thinking })
        assertNull(foreign.filterIsInstance<Part.ToolCall>().single().opaque)

        caller.askRouted(listOf(producedByBackup), emptyList(), 2, using = backup)
        val own = backup.requests.single().messages.single().parts
        assertTrue(own.any { it is Part.Thinking })
        assertEquals("call-opaque", own.filterIsInstance<Part.ToolCall>().single().opaque)

        val unknown = producedByBackup.copy(parts = producedByBackup.parts)
        caller.askRouted(listOf(unknown), emptyList(), 3)
        val unknownOnMain = primary.requests.last().messages.single().parts
        assertTrue(unknownOnMain.any { it is Part.Thinking })
        assertEquals("call-opaque", unknownOnMain.filterIsInstance<Part.ToolCall>().single().opaque)

        caller.askRouted(listOf(unknown), emptyList(), 4, using = backup)
        assertNull(backup.requests.last().messages.single().parts.filterIsInstance<Part.ToolCall>().single().opaque)
    }

    @Test fun eachMemberCompressesAgainstItsOwnContextWindow() = runTest {
        val small = fakeModel("small", contextTokens = 4_000) { answer("small") }
        val large = fakeModel("large", contextTokens = 128_000) { answer("large") }
        val caller = caller(pool(small, large))
        val history = mutableListOf(Message(Role.User, listOf(Part.Text("keep the task")), Author.User))
        repeat(40) { i ->
            history += Message(Role.Assistant, listOf(Part.ToolCall("c$i", "phone_tap", "{}")), Author.Maeve)
            history += Message(Role.Tool, listOf(Part.ToolResult("c$i", "x".repeat(1_000), false)), Author.Maeve)
        }
        caller.ask(history, emptyList(), 1, using = small)
        caller.ask(history, emptyList(), 2, using = large)
        assertTrue("small member should fold old rounds", small.requests.single().messages.size < history.size)
        assertEquals("large member keeps the original history", history, large.requests.single().messages)
    }

    @Test fun streamingMemberSwitchClearsPartialOutput() = runTest {
        val partial = object : StreamingChatModel {
            override val name = "partial"
            override val capabilities = fakeModel("caps") { answer("x") }.capabilities
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>) =
                answerReply("unused")
            override suspend fun completeStreaming(
                system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit,
            ): com.daiyimo.maeve.core.model.ModelReply {
                onText("partial")
                throw ModelError.Network()
            }
        }
        val backup = fakeModel("backup") { answer("answer") }
        val seen = mutableListOf<String>()
        caller(pool(partial, backup)).ask(emptyList(), emptyList(), 1) { seen += it }
        assertEquals(listOf("partial", "", "answer"), seen)
    }

    @Test fun singleModelDefaultPathStillReturnsTheOriginalReply() = runTest {
        val model = fakeModel("only") { answer("same") }
        val caller = caller(model)
        val result = caller.askRouted(emptyList(), emptyList(), 1)
        assertSame(model, result.second)
        assertEquals("same", result.first.parts.filterIsInstance<Part.Text>().single().text)
    }
}

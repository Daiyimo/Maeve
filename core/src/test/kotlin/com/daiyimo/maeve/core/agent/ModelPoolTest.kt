package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.model.Author
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelError
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.Role
import com.daiyimo.maeve.core.model.StopReason
import com.daiyimo.maeve.core.model.StreamingChatModel
import com.daiyimo.maeve.core.model.ToolSpec
import com.daiyimo.maeve.core.testing.answerReply
import com.daiyimo.maeve.core.testing.fakeModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPoolTest {
    private val key = MemberKey("provider", "model")

    @Test fun processHealthAndStickyTablesAreSharedAcrossPoolInstances() {
        ModelPools.resetForTests()
        fun members() = listOf("main", "backup").map { name ->
            PoolMember(MemberKey("shared", name), fakeModel(name) { answer(name) }, if (name == "main") 0 else 1)
        }
        val first = GroupPool("main", members(), sessionId = "same-conversation")
        val second = GroupPool("main", members(), sessionId = "same-conversation")
        val backup = first.members[1]
        first.noteResult(backup, 10, null)
        assertEquals("backup", second.ordered(ModelNeed()).first().key.model)
        first.noteResult(backup, 10, ModelError.Auth())
        assertEquals("main", second.ordered(ModelNeed()).first().key.model)
    }

    @Test fun consecutiveTransientFailuresDoubleCooldownAndSuccessResets() {
        var now = 1_000L
        val health = ModelHealthRegistry { now }
        health.noteFailure(key, ModelError.Network())
        assertEquals(1, health.snapshot(key).consecutiveFailures)
        assertEquals(31_000L, health.snapshot(key).cooldownUntilMs)
        now = 31_000L
        assertTrue(health.available(key))
        health.noteFailure(key, ModelError.Server(503))
        assertEquals(2, health.snapshot(key).consecutiveFailures)
        assertEquals(91_000L, health.snapshot(key).cooldownUntilMs)
        health.noteSuccess(key, 40)
        val after = health.snapshot(key)
        assertEquals(0, after.consecutiveFailures)
        assertEquals(40.0, after.ewmaLatencyMs, 0.001)
        assertNull(after.lastError)
        assertTrue(health.available(key))
    }

    @Test fun latencyUsesEwmaAndOnlySuccessfulCallsRefreshIt() {
        val health = ModelHealthRegistry { 0 }
        health.noteSuccess(key, 100)
        health.noteFailure(key, ModelError.ContentRefused())
        health.noteSuccess(key, 400)
        val state = health.snapshot(key)
        assertEquals(190.0, state.ewmaLatencyMs, 0.001)
        assertEquals(2, state.latencySamples)
        assertEquals(2L, state.calls)
        assertEquals(1L, state.failures)
    }

    @Test fun retryAfterControlsRateLimitCooldownAndAuthQuotaUseFiveMinutes() {
        var now = 10L
        val health = ModelHealthRegistry { now }
        health.noteFailure(key, ModelError.RateLimited(120_000))
        assertEquals(120_010L, health.snapshot(key).cooldownUntilMs)
        now = 500_000L
        health.noteFailure(key, ModelError.Auth())
        assertEquals(800_000L, health.snapshot(key).cooldownUntilMs)
        now = 900_000L
        health.noteFailure(key, ModelError.QuotaExceeded())
        assertEquals(1_200_000L, health.snapshot(key).cooldownUntilMs)
    }

    @Test fun requestAndContentErrorsDoNotCoolDownOrBreakConsecutiveFailures() {
        val health = ModelHealthRegistry { 100 }
        val errors = listOf(
            ModelError.ContentRefused(), ModelError.ContextTooLong(), ModelError.BadResponse(),
            ModelError.Server(400), ModelError.Server(404),
        )
        for (error in errors) {
            assertFalse(error::class.simpleName.orEmpty(), FailoverPolicy.canFailover(error))
            health.noteFailure(key, error)
            assertEquals(0, health.snapshot(key).consecutiveFailures)
            assertEquals(0L, health.snapshot(key).cooldownUntilMs)
            assertTrue(health.available(key))
        }
    }

    @Test fun failoverClassifiesTransientAndAccessErrorsButNotBadRequests() {
        assertTrue(FailoverPolicy.canFailover(ModelError.RateLimited(null)))
        assertTrue(FailoverPolicy.canFailover(ModelError.Network()))
        assertTrue(FailoverPolicy.canFailover(ModelError.StreamCut()))
        assertTrue(FailoverPolicy.canFailover(ModelError.Server(429)))
        assertTrue(FailoverPolicy.canFailover(ModelError.Server(503)))
        assertTrue(FailoverPolicy.canFailover(ModelError.ModelNotFound()))
        assertTrue(FailoverPolicy.canFailover(ModelError.ToolsUnsupported()))
        assertTrue(FailoverPolicy.canFailover(ModelError.SignInExpired()))
        assertFalse(FailoverPolicy.canFailover(ModelError.Server(401)))
        assertFalse(FailoverPolicy.canFailover(ModelError.BadResponse()))
    }

    @Test fun orderedKeepsPriorityFiltersCapabilitiesAndUsesGroupPlusSessionSticky() {
        val health = ModelHealthRegistry { 0 }
        val sticky = StickyTable()
        val main = fakeModel("main") { answer("main") }
        val backup = fakeModel("backup", contextTokens = 4_000) { answer("backup") }
        val noTools = fakeModel("no-tools", toolCalls = false) { answer("no") }
        val members = listOf(
            PoolMember(MemberKey("p", "main"), main, 0),
            PoolMember(MemberKey("p", "backup"), backup, 1),
            PoolMember(MemberKey("p", "no-tools"), noTools, 2),
        )
        val firstSession = GroupPool("main", members, health, sticky, "session-a")
        val secondSession = GroupPool("main", members, health, sticky, "session-b")
        assertEquals(listOf("main", "backup"), firstSession.ordered(ModelNeed(toolCalls = true)).map { it.key.model })
        firstSession.noteResult(members[1], 10, null)
        assertEquals("backup", firstSession.ordered(ModelNeed()).first().key.model)
        assertEquals("backup", firstSession.ordered(ModelNeed(estimatedTokens = 10_000)).first().key.model)
        assertEquals("main", secondSession.ordered(ModelNeed()).first().key.model)
        firstSession.noteResult(members[1], 10, ModelError.Auth())
        assertEquals("main", firstSession.ordered(ModelNeed()).first().key.model)
    }

    @Test fun allMembersCoolingMeansNoCandidate() {
        val health = ModelHealthRegistry { 0 }
        val members = listOf("a", "b").map { name ->
            PoolMember(MemberKey("p", name), fakeModel(name) { answer(name) })
        }
        val pool = GroupPool("main", members, health, StickyTable(), "s")
        members.forEach { pool.noteResult(it, 1, ModelError.Network()) }
        assertTrue(pool.ordered(ModelNeed()).isEmpty())
    }

    @Test fun bypassCallFailsOverAndStripsUnknownPrivateFieldsWithoutReplayingActions() = runTest {
        val primary = fakeModel("primary") { error(ModelError.Auth()) }
        val backup = fakeModel("backup") { answer("ok") }
        val pool = GroupPool("main", listOf(
            PoolMember(MemberKey("p", "primary"), primary, 0),
            PoolMember(MemberKey("p", "backup"), backup, 1),
        ), ModelHealthRegistry { 0 }, StickyTable(), "s")
        val history = listOf(Message(Role.Assistant, listOf(
            Part.Thinking("secret", "opaque-thinking"),
            Part.ToolCall("c1", "phone_tap", "{}", "opaque-call"),
        ), Author.Maeve))
        val reply = pool.complete("system", history, emptyList())
        assertEquals("ok", reply.parts.filterIsInstance<Part.Text>().single().text)
        val sent = backup.requests.single().messages.single()
        assertTrue(sent.parts.none { it is Part.Thinking })
        assertEquals(null, sent.parts.filterIsInstance<Part.ToolCall>().single().opaque)
        assertEquals(1, primary.requests.size)
        assertEquals(1, backup.requests.size)
    }

    @Test fun contentRefusalDoesNotTryBackup() = runTest {
        val primary = fakeModel("primary") { error(ModelError.ContentRefused()) }
        val backup = fakeModel("backup") { answer("should not run") }
        val pool = GroupPool("main", listOf(
            PoolMember(MemberKey("p", "primary"), primary, 0),
            PoolMember(MemberKey("p", "backup"), backup, 1),
        ), ModelHealthRegistry { 0 }, StickyTable(), "s")
        val error = runCatching { pool.complete("s", emptyList(), emptyList()) }.exceptionOrNull()
        assertTrue(error is ModelError.ContentRefused)
        assertTrue(backup.requests.isEmpty())
    }

    @Test fun streamingSwitchClearsPartialTextBeforeBackup() = runTest {
        val partial = object : StreamingChatModel {
            override val name = "partial"
            override val capabilities = fakeModel("caps") { answer("x") }.capabilities
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply =
                error("use streaming")
            override suspend fun completeStreaming(
                system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit,
            ): ModelReply {
                onText("partial")
                throw ModelError.Network()
            }
        }
        val backup = fakeModel("backup") { answer("answer") }
        val pool = GroupPool("main", listOf(
            PoolMember(MemberKey("p", "partial"), partial, 0),
            PoolMember(MemberKey("p", "backup"), backup, 1),
        ), ModelHealthRegistry { 0 }, StickyTable(), "s")
        val seen = mutableListOf<String>()
        pool.completeStreaming("s", emptyList(), emptyList()) { seen += it }
        assertEquals(listOf("partial", "", "answer"), seen)
    }

    @Test fun stickyTableEvictsLeastRecentlyUsedEntry() {
        val table = StickyTable(limit = 2)
        table.put("a", MemberKey("p", "a"))
        table.put("b", MemberKey("p", "b"))
        table.get("a")
        table.put("c", MemberKey("p", "c"))
        assertNull(table.get("b"))
        assertEquals(MemberKey("p", "a"), table.get("a"))
    }

    @Test fun poolCapabilitiesIncludeVisionFromAnyMemberAndLargestContext() {
        val primary = fakeModel("primary", contextTokens = 4_000, vision = false) { answer("primary") }
        val backup = fakeModel("backup", contextTokens = 128_000, vision = true) { answer("backup") }
        val pool = GroupPool("main", listOf(
            PoolMember(MemberKey("p", "primary"), primary, 0),
            PoolMember(MemberKey("p", "backup"), backup, 1),
        ), ModelHealthRegistry { 0 }, StickyTable(), "s")
        assertTrue(pool.capabilities.vision)
        assertEquals(128_000, pool.capabilities.contextTokens)
        assertEquals(listOf("backup"), pool.ordered(ModelNeed(vision = true)).map { it.key.model })
    }
}

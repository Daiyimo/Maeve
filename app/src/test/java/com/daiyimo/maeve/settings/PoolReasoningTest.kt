package com.daiyimo.maeve.settings

import com.daiyimo.maeve.core.model.Capabilities
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.ListedModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.ModelRole
import com.daiyimo.maeve.core.model.ModelSession
import com.daiyimo.maeve.core.model.ToolSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class PoolReasoningTest {
    @get:Rule val tmp = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val setups = mutableListOf<ClientSetup>()
    private val tools = Capabilities(toolCalls = true, vision = false, contextTokens = 64_000, reasoning = false)

    private fun store() = ProviderStore(tmp.root, AesGcmKeyCipher { key }, modelFactory = { cfg, _, _, setup ->
        setups += setup
        object : ChatModel {
            override val name = cfg.model
            override val capabilities = cfg.capabilities
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply = error("not called")
        }
    })

    @Test fun fallbackMemberDoesNotReceiveAnUnsupportedReasoningEffort() {
        val s = store()
        val p = s.connect(
            "甲", ProviderConfig.Kind.OpenAiCompat, "https://a.test/v1", "k",
            listOf(
                ListedModel("m1", reasoningEfforts = listOf("high")),
                ListedModel("m2", reasoningEfforts = listOf("low")),
            ),
        )
        s.use(ModelRole.Main, p.id, "m1", tools)
        s.use(ModelRole.Light, p.id, "m2", tools)
        assertTrue(s.setReasoningEffort(ModelRole.Main, "high"))

        s.modelPoolForMain(ModelSession.of("conversation"))

        assertEquals(listOf("high", null), setups.map { it.reasoningEffort })
    }
}

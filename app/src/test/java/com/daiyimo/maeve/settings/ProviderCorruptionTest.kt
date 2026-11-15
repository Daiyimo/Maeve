package com.daiyimo.maeve.settings

import com.daiyimo.maeve.core.model.ModelRole
import java.io.File
import javax.crypto.KeyGenerator
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProviderCorruptionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun store() = ProviderStore(tmp.root, AesGcmKeyCipher { key })
    @Test fun corruptConfigurationCannotOverwriteOrPruneCredentials() {
        val config = File(tmp.root, "providers.json").apply { writeText("{truncated") }
        val credentials = File(tmp.root, "provider-keys.json").apply { writeText("{\"private\":\"keep-this-ciphertext\"}") }
        val store = store()
        assertEquals(ProviderStore.LoadState.Corrupt, store.loadState.value)
        assertNull(store.modelFor(ModelRole.Main))
        assertTrue(runCatching { store.connect("custom", ProviderConfig.Kind.OpenAiCompat, "https://example.test", "new-key", emptyList()) }.isFailure)
        assertEquals("{truncated", config.readText())
        assertEquals("{\"private\":\"keep-this-ciphertext\"}", credentials.readText())
    }
    @Test fun validConfigurationWithBrokenCredentialsDoesNotDropThem() {
        val first = store()
        first.connect("custom", ProviderConfig.Kind.OpenAiCompat, "https://example.test", "key", emptyList())
        val config = File(tmp.root, "providers.json").readText()
        val credentials = File(tmp.root, "provider-keys.json").apply { writeText("{broken") }
        val restarted = store()
        assertEquals(ProviderStore.LoadState.Corrupt, restarted.loadState.value)
        assertEquals(config, File(tmp.root, "providers.json").readText())
        assertEquals("{broken", credentials.readText())
    }
}

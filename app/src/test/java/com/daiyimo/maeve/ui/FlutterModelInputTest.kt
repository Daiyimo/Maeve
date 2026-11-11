package com.daiyimo.maeve.ui

import com.daiyimo.maeve.settings.ProviderConfig
import com.daiyimo.maeve.settings.ProviderStore
import com.daiyimo.maeve.settings.AesGcmKeyCipher
import com.daiyimo.maeve.core.model.ModelRole
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class FlutterModelInputTest {
    @get:Rule val tmp = TemporaryFolder()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun store() = ProviderStore(tmp.root, AesGcmKeyCipher { key })
    private fun values(url: String = "https://models.example/v1/", kind: String = "OpenAiCompat", model: String = "model-id") =
        mapOf("baseUrl" to url, "kind" to kind, "model" to model, "apiKey" to "fixture-key")

    @Test fun normalizesARealEndpointWithoutInventingAProviderOrTier() {
        val input = FlutterModelInput.parse(values())
        assertEquals("https://models.example/v1", input.baseUrl)
        assertEquals("models.example", input.name)
        assertEquals("model-id", input.model)
        assertEquals(ProviderConfig.Kind.OpenAiCompat, input.kind)
    }

    @Test fun rejectsBadEndpointsBeforeChangingStoredModels() {
        for (url in listOf("", "file:///tmp/model", "https://", "https://key@models.example/v1", "https://models.example/?key=secret", "https://models.example/#token")) {
            assertThrows(url, IllegalArgumentException::class.java) { FlutterModelInput.parse(values(url)) }
        }
    }

    @Test fun accountSessionsCannotBeFabricatedAsApiKeyConfigurations() {
        for (kind in listOf("ChatGpt", "Xai", "MaeveHigh", "")) {
            assertThrows(kind, IllegalArgumentException::class.java) { FlutterModelInput.parse(values(kind = kind)) }
        }
        assertThrows(IllegalArgumentException::class.java) { FlutterModelInput.parse(values(model = "  ")) }
    }

    @Test fun savedModelsSurviveRolePruningAndRestartWithoutInventingVerification() {
        val store = store()
        val settings = FlutterModelSettings(store)
        settings.save(FlutterModelInput.parse(values(model = "first")))
        val first = settings.choices().single().id
        settings.save(FlutterModelInput.parse(values(model = "second")))
        assertEquals(setOf("first", "second"), settings.choices().map { it.model }.toSet())
        assertEquals("second", store.configFor(ModelRole.Main)!!.model)
        assertNotNull(store.untestedMainProvider())
        assertTrue(store.providers.value.single().verifiedModels.isEmpty())

        val reloaded = store()
        FlutterModelSettings(reloaded).select(first)
        assertEquals("first", reloaded.configFor(ModelRole.Main)!!.model)
        assertEquals("fixture-key", reloaded.apiKey(reloaded.configFor(ModelRole.Main)!!.id))
        assertTrue(reloaded.configFor(ModelRole.Main)!!.capabilities.toolCalls)
        assertNotNull(reloaded.modelFor(ModelRole.Main))
        assertEquals(2, FlutterModelSettings(reloaded).choices().size)
        assertFalse(FlutterModelSettings(reloaded).choices().any { "fixture-key" in it.toMap().values })
    }

    @Test fun aRemovedModelCannotReplaceTheCurrentSelection() {
        val store = store()
        val settings = FlutterModelSettings(store)
        settings.save(FlutterModelInput.parse(values()))
        val current = store.configFor(ModelRole.Main)
        assertThrows(IllegalArgumentException::class.java) { settings.select("missing") }
        assertEquals(current, store.configFor(ModelRole.Main))
    }
}

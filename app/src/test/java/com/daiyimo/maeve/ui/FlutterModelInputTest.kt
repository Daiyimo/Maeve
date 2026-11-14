package com.daiyimo.maeve.ui

import com.daiyimo.maeve.settings.ProviderConfig
import com.daiyimo.maeve.settings.ProviderStore
import com.daiyimo.maeve.settings.StepModelProfiles
import com.daiyimo.maeve.settings.AesGcmKeyCipher
import com.daiyimo.maeve.core.model.ModelRole
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

class FlutterModelInputTest {
    @Test fun addingAnotherModelKeepsLegacyRoleOnlyConfiguration() {
        val s = store()
        val p = s.connect("models.example", ProviderConfig.Kind.OpenAiCompat, "https://models.example/v1", "fixture-key", emptyList())
        assertTrue(s.use(ModelRole.Main, p.id, "legacy-first", com.daiyimo.maeve.core.model.Capabilities(true, false, 64000, false)))
        val settings = FlutterModelSettings(s)
        settings.save(FlutterModelInput.parse(values(model = "second") + ("apiKey" to "")))
        assertEquals(setOf("legacy-first", "second"), settings.choices().map { it.model }.toSet())
        val reloaded = FlutterModelSettings(store())
        reloaded.select(reloaded.choices().first { it.model == "legacy-first" }.id)
        assertEquals(2, reloaded.choices().size)
    }
    @Test fun legacyStepPlanConfigurationUsesDocumentedImagesUnlessExplicitlyDisabled() {
        val s = store()
        val p = s.connect("阶跃星辰", ProviderConfig.Kind.OpenAiCompat, StepModelProfiles.PLAN,
            "fixture-key", emptyList(), typed = setOf("step-3.7-flash"), untested = true)
        val legacy = com.daiyimo.maeve.core.model.Capabilities(true, false, 64_000, false)
        assertTrue(s.use(ModelRole.Main, p.id, "step-3.7-flash", legacy))
        assertTrue("Old preset defaults must not silently hide screenshots", s.configFor(ModelRole.Main)!!.capabilities.vision)
        assertTrue(FlutterModelSettings(s).choices().single().vision)
        assertTrue(store().configFor(ModelRole.Main)!!.capabilities.vision)
        s.setModelVision(p.id, "step-3.7-flash", false)
        assertFalse(s.configFor(ModelRole.Main)!!.capabilities.vision)
        assertFalse(FlutterModelSettings(s).choices().single().vision)
        assertFalse(store().configFor(ModelRole.Main)!!.capabilities.vision)
    }
    @Test fun flutterShowsOneStepPresetWithBothOfficialEndpoints() {
        val catalog = FlutterModelSettings.presets()
        assertEquals(listOf("阶跃星辰"), catalog.map { it["label"] })
        assertEquals(StepModelProfiles.API, catalog.single()["baseUrl"])
        assertEquals(StepModelProfiles.PLAN, catalog.single()["planUrl"])
        assertEquals("OpenAiCompat", catalog.single()["kind"])
        assertFalse(catalog.any { "apiKey" in it })
    }
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

    @Test fun imageAndReasoningSettingsSurviveModelSwitchesAndRestart() {
        val s = store()
        val settings = FlutterModelSettings(s)
        settings.save(FlutterModelInput.parse(values(url = StepModelProfiles.API, model = "step-3.7-flash") + mapOf("vision" to "true", "reasoningEffort" to "high")))
        val first = settings.choices().single().id
        assertTrue(s.configFor(ModelRole.Main)!!.capabilities.vision)
        assertTrue(s.configFor(ModelRole.Main)!!.capabilities.toolCalls)
        assertEquals("high", s.roles.value.reasoningEffort(ModelRole.Main))
        settings.save(FlutterModelInput.parse(values(url = StepModelProfiles.API, model = "step-3.5-flash")))
        assertNull(s.roles.value.reasoningEffort(ModelRole.Main))
        val reloaded = store()
        FlutterModelSettings(reloaded).select(first)
        assertEquals("high", reloaded.roles.value.reasoningEffort(ModelRole.Main))
        assertTrue(reloaded.configFor(ModelRole.Main)!!.capabilities.vision)
        assertEquals("high", FlutterModelSettings(reloaded).choices().first { it.id == first }.reasoningEffort)
    }

    @Test fun unsupportedReasoningFailsBeforeChangingTheSelection() {
        val s = store()
        val settings = FlutterModelSettings(s)
        settings.save(FlutterModelInput.parse(values()))
        val before = s.configFor(ModelRole.Main)
        assertThrows(IllegalArgumentException::class.java) {
            settings.save(FlutterModelInput.parse(values(url = StepModelProfiles.PLAN, model = "step-3.5-flash-2603") + ("reasoningEffort" to "medium")))
        }
        assertEquals(before, s.configFor(ModelRole.Main))
        assertEquals(listOf("low", "high"), StepModelProfiles.levels("step-3.5-flash-2603"))
    }

    @Test fun draftListingUsesTheChosenPathWithoutPersistingCredentials() = kotlinx.coroutines.runBlocking {
        val requests = mutableListOf<Pair<String, String?>>()
        val s = ProviderStore(tmp.root, AesGcmKeyCipher { key }, modelLister = { _, url, token, _ ->
            requests += url to token
            com.daiyimo.maeve.core.model.ModelListResult.Ok(listOf("step-3.7-flash", "stepaudio-2.5-tts", "stepaudio-2.5-asr", "stepaudio-2.5-realtime").map { com.daiyimo.maeve.core.model.ListedModel(it) })
        })
        val settings = FlutterModelSettings(s)
        val result = settings.list(FlutterModelInput.parse(values(url = StepModelProfiles.PLAN, model = ""), requireModel = false))
        assertEquals(listOf(StepModelProfiles.PLAN to "fixture-key"), requests)
        assertTrue(s.providers.value.isEmpty())
        assertFalse(result.toString().contains("fixture-key"))
        assertEquals(true, ((result["models"] as List<*>).single() as Map<*, *>)["vision"])
        assertThrows(IllegalArgumentException::class.java) {
            settings.save(FlutterModelInput.parse(values(url = StepModelProfiles.PLAN, model = "stepaudio-2.5-tts")))
        }
        assertTrue(s.providers.value.isEmpty())
    }

    @Test fun aRetainedKeyStaysWithTheSameServiceAndSupportsTheOfficialPlanPath() = kotlinx.coroutines.runBlocking {
        var received: Pair<String, String?>? = null
        val s = ProviderStore(tmp.root, AesGcmKeyCipher { key }, modelLister = { _, url, token, _ ->
            received = url to token
            com.daiyimo.maeve.core.model.ModelListResult.Ok(emptyList())
        })
        val settings = FlutterModelSettings(s)
        settings.save(FlutterModelInput.parse(values(url = StepModelProfiles.API, model = "step-3.7-flash")))
        val id = settings.choices().single().id
        settings.list(FlutterModelInput.parse(values(url = StepModelProfiles.PLAN, model = "") + mapOf("id" to id, "apiKey" to ""), requireModel = false))
        assertEquals(StepModelProfiles.PLAN to "fixture-key", received)
        val previous = received
        try {
            settings.list(FlutterModelInput.parse(values(url = "https://different.example/v1", model = "") + mapOf("id" to id, "apiKey" to ""), requireModel = false))
            fail("Stored credentials must not follow a different service")
        } catch (_: IllegalArgumentException) { }
        assertEquals(previous, received)
    }

    @Test fun fetchedModelMetadataIsSavedForOnlyTheChosenModelAndSurvivesRestart() = kotlinx.coroutines.runBlocking {
        val s = ProviderStore(tmp.root, AesGcmKeyCipher { key }, modelLister = { _, _, _, _ ->
            com.daiyimo.maeve.core.model.ModelListResult.Ok(listOf(
                com.daiyimo.maeve.core.model.ListedModel("future-model", vision = true, contextTokens = 200_000, reasoning = true, reasoningEfforts = listOf("low", "high")),
                com.daiyimo.maeve.core.model.ListedModel("unselected"),
            ))
        })
        val settings = FlutterModelSettings(s)
        settings.list(FlutterModelInput.parse(values(model = ""), requireModel = false))
        settings.save(FlutterModelInput.parse(values(model = "future-model") + ("reasoningEffort" to "high")))
        assertEquals(listOf("future-model"), settings.choices().map { it.model })
        val restored = store()
        assertTrue(restored.configFor(ModelRole.Main)!!.capabilities.vision)
        assertEquals(200_000, restored.configFor(ModelRole.Main)!!.capabilities.contextTokens)
        assertEquals("high", restored.roles.value.reasoningEffort(ModelRole.Main))
        assertEquals(listOf("low", "high"), FlutterModelSettings(restored).choices().single().reasoningLevels)
    }

    @Test fun catalogueCapabilitiesCannotFollowAnUnrelatedDraftKey() = kotlinx.coroutines.runBlocking {
        val s = ProviderStore(tmp.root, AesGcmKeyCipher { key }, modelLister = { _, _, _, _ ->
            com.daiyimo.maeve.core.model.ModelListResult.Ok(listOf(com.daiyimo.maeve.core.model.ListedModel("future-model", reasoningEfforts = listOf("high"))))
        })
        val settings = FlutterModelSettings(s)
        settings.list(FlutterModelInput.parse(values(model = ""), requireModel = false))
        assertThrows(IllegalArgumentException::class.java) {
            settings.save(FlutterModelInput.parse(values(model = "future-model") + mapOf("apiKey" to "different-fixture-key", "reasoningEffort" to "high")))
        }
        assertTrue(s.providers.value.isEmpty())
    }
}

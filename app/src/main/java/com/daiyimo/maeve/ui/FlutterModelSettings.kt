package com.daiyimo.maeve.ui

import com.daiyimo.maeve.core.model.ModelRole
import com.daiyimo.maeve.settings.ModelOptions
import com.daiyimo.maeve.settings.ProviderStore
import com.daiyimo.maeve.settings.ProviderPresets
import com.daiyimo.maeve.settings.StepModelProfiles
import com.daiyimo.maeve.settings.ReasoningOptions
import com.daiyimo.maeve.settings.ProviderConfig
import com.daiyimo.maeve.core.model.ModelListResult
import com.daiyimo.maeve.core.model.ListedModel
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID

/** Model choices belong to providers; role configurations are deliberately pruned by ProviderStore. */
internal class FlutterModelSettings(private val store: ProviderStore) {
    private data class DraftCatalog(val kind: ProviderConfig.Kind, val url: String, val keyHash: String, val models: List<ListedModel>)
    @Volatile private var draft: DraftCatalog? = null
    private val listingRevision = AtomicLong()
    private fun keyHash(key: String?) = MessageDigest.getInstance("SHA-256").digest(key.orEmpty().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    data class Choice(val id: String, val providerId: String, val name: String,
        val model: String, val kind: String, val baseUrl: String,
        val vision: Boolean = false, val reasoningEffort: String? = null,
        val reasoningLevels: List<String> = emptyList(), val hasKey: Boolean = false) {
        fun toMap(): Map<String, Any?> = mapOf("id" to id, "name" to name, "model" to model,
            "kind" to kind, "baseUrl" to baseUrl, "vision" to vision, "toolCalls" to true,
            "reasoningEffort" to reasoningEffort, "reasoningLevels" to reasoningLevels, "hasKey" to hasKey)
    }

    fun choices(): List<Choice> = store.providers.value.flatMap { p ->
        ModelOptions.ofProvider(p, store.list()).map { m ->
            val config = store.list().firstOrNull { it.providerId == p.id && it.model == m.id }
                ?: ProviderConfig("", p.kind, p.baseUrl, m.id, m.capabilities)
            Choice(id(p.id, m.id), p.id, p.name, m.id, p.kind.name, p.baseUrl,
                m.capabilities.vision,
                if (m.id in p.modelReasoning) p.modelReasoning[m.id]?.takeUnless { it == "default" }
                else store.roles.value.reasoningEffort(ModelRole.Main).takeIf { store.configFor(ModelRole.Main)?.let { it.providerId == p.id && it.model == m.id } == true },
                ReasoningOptions.of(config, p.models?.firstOrNull { it.id == m.id })?.levels.orEmpty(), store.hasKey(p.id))
        }
    }

    fun select(id: String) {
        val choice = requireNotNull(choices().firstOrNull { it.id == id }) { "Model configuration no longer exists" }
        val provider = requireNotNull(store.provider(choice.providerId))
        val caps = ModelOptions.ofProvider(provider, store.list()).first { it.id == choice.model }.capabilities
        check(store.use(ModelRole.Main, provider.id, choice.model, caps))
        if (choice.model in provider.modelReasoning) {
            check(store.setReasoningEffort(ModelRole.Main, provider.modelReasoning[choice.model]?.takeUnless { it == "default" }))
        }
    }

    fun save(input: FlutterModelInput) {
        val previous = input.id?.let { wanted -> requireNotNull(choices().firstOrNull { it.id == wanted }) { "模型配置已不存在" } }
        val key = credential(input)
        if (StepModelProfiles.isEndpoint(input.baseUrl)) require(!key.isNullOrBlank()) { "请填写此站点的 API Key" }
        require(!StepModelProfiles.isEndpoint(input.baseUrl) || StepModelProfiles.isTaskModel(input.model)) { "语音转写、合成和实时语音模型不能用于手机任务，请选择对话模型" }
        val profile = StepModelProfiles.of(input.model)?.takeIf { StepModelProfiles.isEndpoint(input.baseUrl) }
        val previousProvider = previous?.let { store.provider(it.providerId) }
        val targetProvider = store.providers.value.firstOrNull { it.kind == input.kind && it.baseUrl.trimEnd('/') == input.baseUrl && it.name == input.name }
        val cached = draft?.takeIf { it.kind == input.kind && it.url == input.baseUrl && it.keyHash == keyHash(key) }
        val listed = cached?.models?.firstOrNull { it.id == input.model }
            ?: targetProvider?.models?.firstOrNull { it.id == input.model }
            ?: previousProvider?.takeIf { it.baseUrl == input.baseUrl }?.models?.firstOrNull { it.id == input.model }
        require(listed?.toolCalls != false) { "此模型不支持工具调用，请选择可执行任务的模型" }
        val fallback = ProviderPresets.forBaseUrl(input.baseUrl)?.capabilities ?: ProviderPresets.custom.capabilities
        val caps = fallback.copy(toolCalls = true, vision = input.vision ?: listed?.vision ?: profile?.vision ?: previous?.vision ?: false,
            reasoning = listed?.reasoning ?: profile?.reasoning ?: (input.reasoningEffort != null), contextTokens = listed?.contextTokens ?: profile?.context ?: fallback.contextTokens)
        val proposed = ProviderConfig("", input.kind, input.baseUrl, input.model, caps)
        require(input.reasoningEffort == null || input.reasoningEffort in ReasoningOptions.of(proposed, listed)?.levels.orEmpty()) { "此模型不支持所选的思考强度" }
        val provider = store.connect(input.name, input.kind, input.baseUrl,
            key, if (listed == null) emptyList() else targetProvider?.models.orEmpty().filter { it.id != input.model } + listed,
            typed = setOf(input.model), untested = true)
        check(store.setModelVision(provider.id, input.model, caps.vision))
        store.list().firstOrNull { it.providerId == provider.id && it.model == input.model }?.let { store.save(it.copy(capabilities = caps), null) }
        check(store.use(ModelRole.Main, provider.id, input.model, caps))
        check(store.setReasoningEffort(ModelRole.Main, input.reasoningEffort))
        store.rememberModelReasoning(provider.id, input.model, input.reasoningEffort)
    }

    /** Draft listing never saves a key. A retained key is bound to its original endpoint. */
    private fun credential(input: FlutterModelInput): String? {
        input.apiKey.takeIf { it.isNotBlank() }?.let { return it }
        val previous = input.id?.let { wanted -> choices().firstOrNull { it.id == wanted } } ?: return null
        val sameService = previous.baseUrl.trimEnd('/') == input.baseUrl.trimEnd('/') ||
            StepModelProfiles.isEndpoint(previous.baseUrl) && StepModelProfiles.isEndpoint(input.baseUrl)
        require(sameService && previous.kind == input.kind.name) { "切换服务商后，请填写该站点的 API Key" }
        return store.providerKey(previous.providerId)
    }

    suspend fun list(input: FlutterModelInput): Map<String, Any> {
        val revision = listingRevision.incrementAndGet()
        val key = credential(input)
        require(!key.isNullOrBlank()) { "请先填写此站点的 API Key" }
        return when (val result = store.listModels(input.kind, input.baseUrl, key)) {
            is ModelListResult.Failed -> mapOf("models" to emptyList<Any>(), "error" to result.reason.text)
            is ModelListResult.Ok -> {
                if (listingRevision.get() == revision) draft = DraftCatalog(input.kind, input.baseUrl, keyHash(key), result.models)
                mapOf("models" to result.models.filter { it.usable && it.chat != false && it.toolCalls != false &&
                    (!StepModelProfiles.isEndpoint(input.baseUrl) || StepModelProfiles.isTaskModel(it.id)) }.map { m ->
                val profile = StepModelProfiles.of(m.id)?.takeIf { StepModelProfiles.isEndpoint(input.baseUrl) }
                mapOf("id" to m.id, "name" to m.displayName.ifBlank { m.id },
                    "vision" to (m.vision ?: profile?.vision), "toolCalls" to m.toolCalls,
                    "reasoningLevels" to (m.reasoningEfforts ?: if (profile != null) StepModelProfiles.levels(m.id) else emptyList<String>()))
                })
            }
        }
    }

    companion object {
        fun presets(): List<Map<String, Any>> = ProviderPresets.all.filter { it.baseUrl == StepModelProfiles.API }.map { p ->
            mapOf("id" to p.label, "label" to p.label, "kind" to p.kind.name,
                "baseUrl" to p.baseUrl, "planUrl" to StepModelProfiles.PLAN, "selfHosted" to p.selfHosted,
                "modelProfiles" to listOf("step-5-preview", "step-3.7-flash", "step-3.5-flash", "step-3.5-flash-2603").map { model ->
                    mapOf("id" to model, "vision" to StepModelProfiles.of(model)!!.vision, "reasoningLevels" to StepModelProfiles.levels(model))
                })
        }

        /** Opaque, stable across role changes/restarts; never contains a credential. */
        fun id(providerId: String, model: String): String =
            UUID.nameUUIDFromBytes("$providerId\u0000$model".toByteArray(Charsets.UTF_8)).toString()
    }
}

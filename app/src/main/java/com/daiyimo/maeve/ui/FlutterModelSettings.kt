package com.daiyimo.maeve.ui

import com.daiyimo.maeve.core.model.ModelRole
import com.daiyimo.maeve.settings.ModelOptions
import com.daiyimo.maeve.settings.ProviderStore
import java.util.UUID

/** Model choices belong to providers; role configurations are deliberately pruned by ProviderStore. */
internal class FlutterModelSettings(private val store: ProviderStore) {
    data class Choice(val id: String, val providerId: String, val name: String,
        val model: String, val kind: String, val baseUrl: String) {
        fun toMap(): Map<String, String> = mapOf("id" to id, "name" to name, "model" to model,
            "kind" to kind, "baseUrl" to baseUrl)
    }

    fun choices(): List<Choice> = store.providers.value.flatMap { p ->
        ModelOptions.ofProvider(p, store.list()).map { m ->
            Choice(id(p.id, m.id), p.id, p.name, m.id, p.kind.name, p.baseUrl)
        }
    }

    fun select(id: String) {
        val choice = requireNotNull(choices().firstOrNull { it.id == id }) { "Model configuration no longer exists" }
        val provider = requireNotNull(store.provider(choice.providerId))
        val caps = ModelOptions.ofProvider(provider, store.list()).first { it.id == choice.model }.capabilities
        check(store.use(ModelRole.Main, provider.id, choice.model, caps))
    }

    fun save(input: FlutterModelInput) {
        val provider = store.connect(input.name, input.kind, input.baseUrl,
            input.apiKey.takeIf { it.isNotBlank() }, emptyList(), typed = setOf(input.model), untested = true)
        select(id(provider.id, input.model))
    }

    companion object {
        /** Opaque, stable across role changes/restarts; never contains a credential. */
        fun id(providerId: String, model: String): String =
            UUID.nameUUIDFromBytes("$providerId\u0000$model".toByteArray(Charsets.UTF_8)).toString()
    }
}

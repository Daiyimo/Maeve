package com.daiyimo.maeve.settings

import com.daiyimo.maeve.core.model.ListedModel

/**
 * 思考档位。服务商列表写明的档位适用于四种协议(见 ModelCatalog);没写明时 Responses 给通用手动档位,
 * OpenAI 兼容协议按预设的官方文档给(见 [ProviderPreset.reasoningLevels]),其余协议不猜。实际支持以服务端为准。
 */
/** 成员不支持角色选的档位时传 null,避免把别家的值发给它导致整次备援请求被拒。 */
internal fun memberReasoningEffort(cfg: ProviderConfig, listed: ListedModel?, effort: String?): String? = effort?.takeIf {
    it in ReasoningOptions.of(cfg, listed)?.levels.orEmpty()
}
data class ReasoningOptions(val levels: List<String>, val fromProvider: Boolean) {
    companion object {
        // 列表没说明档位时供手动选择,不声称每个模型都支持;界面会说明,默认始终不指定。
        private val manualLevels = listOf("none", "minimal", "low", "medium", "high", "xhigh", "max")
        fun of(config: ProviderConfig, listed: ListedModel?): ReasoningOptions? {
            listed?.reasoningEfforts?.let { return ReasoningOptions(it, fromProvider = true) }
            // 仍返回空选项,使已保存的旧档位能在界面恢复默认。
            if (listed?.reasoning == false) return ReasoningOptions(emptyList(), fromProvider = true)
            if (StepModelProfiles.isEndpoint(config.baseUrl)) {
                return ReasoningOptions(StepModelProfiles.levels(config.model), fromProvider = false)
            }
            return when (ProviderStore.adapterFor(config, listed?.endpoint).first) {
                ProviderConfig.Kind.OpenAiResponses, ProviderConfig.Kind.ChatGpt, ProviderConfig.Kind.Xai ->
                    ReasoningOptions(manualLevels, fromProvider = false)
                // 预设写了几档就给几档;自定义地址对不上预设,照旧不猜
                ProviderConfig.Kind.OpenAiCompat -> ProviderPresets.forBaseUrl(config.baseUrl)?.reasoningLevels
                    ?.takeIf { it.isNotEmpty() }?.let { ReasoningOptions(it, fromProvider = false) }
                ProviderConfig.Kind.Anthropic, ProviderConfig.Kind.Gemini -> null
            }
        }
    }
}

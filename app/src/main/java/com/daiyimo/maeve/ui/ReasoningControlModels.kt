package com.daiyimo.maeve.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.core.model.ModelRole
import com.daiyimo.maeve.settings.ReasoningOptions
import com.daiyimo.maeve.settings.RoleChoice

internal fun hasReasoningAction(ui: ModelSetupUi, role: ModelRole): Boolean {
    if (ui.roles[role] == RoleChoice.SameAsMain) return false
    val cfg = ui.configFor(role) ?: return false
    val listed = ui.providerOf(cfg)?.models?.firstOrNull { it.id == cfg.model }
    val options = ReasoningOptions.of(cfg, listed) ?: return false
    return options.levels.isNotEmpty() || ui.roles.reasoningEffort(role) != null
}

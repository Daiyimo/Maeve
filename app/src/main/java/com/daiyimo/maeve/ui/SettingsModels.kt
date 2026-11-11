package com.daiyimo.maeve.ui

import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.core.cap.sandbox.SandboxStatus

private enum class SettingsPage { Home, Advanced, AppAccess, PhoneOps, Amoo, Wechat, DuringWork, Capsule, Language, Notifications, Sandbox, Recovery, SelfCheck, ComputerAccess, McpServers, Root, Developer, RunStats }


private fun sandboxValue(s: SandboxStatus): String = when (s) {
    is SandboxStatus.Ready -> tr(R.string.sandbox_ready)
    is SandboxStatus.Installing -> tr(R.string.sandbox_preparing)
    is SandboxStatus.Broken -> tr(R.string.pill_needs_attention)
    else -> tr(R.string.sandbox_not_prepared)
}


private val StatusShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

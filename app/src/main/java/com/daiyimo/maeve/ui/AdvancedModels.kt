package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.core.cap.sandbox.SandboxStatus
import com.daiyimo.maeve.cap.sandbox.SandboxUsage
import com.daiyimo.maeve.cap.sandbox.WorkspaceUsage
import java.util.Locale

object SandboxCopy {
    val NETWORK_TITLE: String get() = tr(R.string.sandbox_network)
    val NETWORK_SUBTITLE: String get() = tr(R.string.sandbox_network_sub)
    val NETWORK_EXPLAIN: String get() = tr(R.string.sandbox_network_explain)
    val USAGE_TITLE: String get() = tr(R.string.sandbox_usage)
    val USAGE_NOTE: String get() = tr(R.string.sandbox_usage_note)
    val CLEAR: String get() = tr(R.string.sandbox_usage_clear)
    val CLEAR_TITLE: String get() = tr(R.string.sandbox_usage_clear_title)
    val CLEAR_ALL: String get() = tr(R.string.sandbox_usage_clear_all)
    val CLEAR_ALL_TITLE: String get() = tr(R.string.sandbox_usage_clear_all_title)
    val CLEAR_ALL_BODY: String get() = tr(R.string.sandbox_usage_clear_all_body)

    fun clearBody(w: WorkspaceUsage): String = tr(R.string.sandbox_usage_clear_body, workspaceTitle(w))

    /** 工作区那一行的名字:对话标题;对话已经删了的写明。 */
    fun workspaceTitle(w: WorkspaceUsage): String = w.title?.let(ChatCopy::plainTitle)?.takeIf(String::isNotBlank) ?: tr(R.string.sandbox_usage_deleted_chat)

    fun usageRows(u: SandboxUsage): List<Pair<String, Long>> = listOf(
        tr(R.string.sandbox_usage_total) to u.total,
        tr(R.string.sandbox_usage_runtime) to u.runtime,
        tr(R.string.sandbox_usage_skills) to u.skills,
        tr(R.string.sandbox_usage_workspaces) to u.workspacesTotal,
    )

    /** 字节数按人看得懂的单位写。 */
    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(Locale.ROOT, bytes.toDouble() / (1L shl 30))
        bytes >= 1L shl 20 -> "%.1f MB".format(Locale.ROOT, bytes.toDouble() / (1L shl 20))
        bytes >= 1L shl 10 -> "%.0f KB".format(Locale.ROOT, bytes.toDouble() / (1L shl 10))
        else -> "$bytes B"
    }
    val ISOLATION_UNAVAILABLE: String get() = tr(R.string.sandbox_isolation_unavailable)
    val RESET_TITLE: String get() = tr(R.string.sandbox_reset_title)
    val RESET_BODY: String get() = tr(R.string.sandbox_reset_body)

    /** 「重置运行环境」下面的一行:平时说明会怎样,准备中或准备失败时如实说。 */
    fun resetSubtitle(s: SandboxStatus): String = when (s) {
        is SandboxStatus.Installing -> tr(R.string.runtime_preparing)
        is SandboxStatus.Broken -> tr(R.string.sandbox_broken, s.reason)
        else -> tr(R.string.sandbox_reset_sub)
    }
}

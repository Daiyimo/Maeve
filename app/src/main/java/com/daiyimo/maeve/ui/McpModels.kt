package com.daiyimo.maeve.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.R
import com.daiyimo.maeve.core.remote.RemoteActivity
import com.daiyimo.maeve.core.remote.RelayState
import com.daiyimo.maeve.mcp.McpServerState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ComputerAccessUi(
    val server: McpServerState,
    val networkEnabled: Boolean,
    val usbEnabled: Boolean,
    val port: Int,
    val activity: RemoteActivity,
    val addresses: List<String>,
    val token: String?,
    val usbCommand: String,
    val relayEnabled: Boolean = false,
    val relayState: RelayState = RelayState.Off,
)


private fun clock(ms: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))


internal fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Maeve", text))
    Toast.makeText(context, context.getString(R.string.mcp_copied), Toast.LENGTH_SHORT).show()
}

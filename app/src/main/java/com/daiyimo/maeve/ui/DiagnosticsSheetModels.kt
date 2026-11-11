package com.daiyimo.maeve.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import com.daiyimo.maeve.about.Diagnostics

private fun copyDiagnostics(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(Diagnostics.HEADER, text))
    Toast.makeText(context, AboutCopy.DIAGNOSTICS_COPIED, Toast.LENGTH_SHORT).show()
}


private fun shareDiagnostics(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, AboutCopy.DIAGNOSTICS_TITLE)) }
}

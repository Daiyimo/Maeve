package com.daiyimo.maeve.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.intercept.AmooBridge
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun newestResultAt(context: Context): Long? {
    val media = context.getExternalMediaDirs().firstOrNull() ?: return null
    val out = File(File(media, AmooBridge.DIR), AmooBridge.OUT)
    return out.listFiles()?.maxOfOrNull { it.lastModified() }
}


private fun clock(ms: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

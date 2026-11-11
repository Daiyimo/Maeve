package com.daiyimo.maeve.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.daiyimo.maeve.R
import com.daiyimo.maeve.core.trace.TraceArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TRACE_REFRESH_MS = 1_000L


private suspend fun saveTrace(archive: TraceArchive, includeBody: Boolean, open: () -> java.io.OutputStream?): Int =
    withContext(Dispatchers.IO) {
        try {
            (open() ?: throw IOException("No destination")).use { archive.save(it, includeBody) }
            R.string.trace_saved
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { R.string.trace_failed }
    }

package com.daiyimo.maeve.ui

import com.daiyimo.maeve.intercept.AmooBridge
import com.daiyimo.maeve.intercept.AmooSetup
import java.io.File

/** Metadata only: file-bridge activity is distinct from completed setup verification. */
internal object FlutterAmooSettings {
    fun snapshot(stage: AmooSetup.Stage, bridgeEnabled: Boolean, media: File?,
                 installed: Boolean, accessibility: Boolean): Map<String, Any?> {
        val out = media?.let { File(File(it, AmooBridge.DIR), AmooBridge.OUT) }
        val last = out?.listFiles()?.asSequence()
            ?.filter { it.isFile && it.extension == "json" }
            ?.maxOfOrNull { it.lastModified() }
        return mapOf("stage" to stage.name.lowercase(java.util.Locale.ROOT),
            "bridgeEnabled" to bridgeEnabled, "installed" to installed,
            "accessibility" to accessibility, "lastResultAt" to last)
    }
}

package com.daiyimo.maeve.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.daiyimo.maeve.core.access.*
import com.daiyimo.maeve.ui.components.*

private class Failure(val message: Int, val detail: String? = null)


private fun classifierLanguage(locale: java.util.Locale): String = when {
    locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO") -> "Traditional Chinese"
    locale.language == "zh" -> "Simplified Chinese"
    else -> locale.getDisplayLanguage(java.util.Locale.ENGLISH)
}

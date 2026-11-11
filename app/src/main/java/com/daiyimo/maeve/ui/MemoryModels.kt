package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.core.memory.MemoryKind

private val SEGMENT_TAGS = Segment.entries.map { "segment-${it.name}" }


private val KIND_GROUPS get() = listOf(MemoryKind.fact to tr(R.string.memory_about_you), MemoryKind.preference to tr(R.string.memory_preferences), MemoryKind.procedure to tr(R.string.memory_procedures))


private val KIND_NAMES get() = listOf(tr(R.string.memory_fact), tr(R.string.memory_preferences), tr(R.string.memory_procedures))


private enum class MemoryDataPage { Home, WebSites, Routes }

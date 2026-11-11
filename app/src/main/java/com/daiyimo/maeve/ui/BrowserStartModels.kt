package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.ui.theme.MaeveType

val BROWSER_SEARCH_HINT: String get() = tr(R.string.browser_search_hint)


val BROWSER_ENGINE_NOTE: String get() = tr(R.string.browser_engine_note)


val BROWSER_TYPING_NOTE: String get() = tr(R.string.browser_typing_note)


val BROWSER_LOGIN_LEGEND: String get() = tr(R.string.browser_login_legend)


val BROWSER_ADD_HINT: String get() = tr(R.string.browser_add_hint)


private const val COLUMNS = 4


private val BigText = MaeveType.Body.copy(fontSize = 16.sp)


private val TileShape = RoundedCornerShape(16.dp)


private val InitialLarge = MaeveType.Title.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)


private const val WIGGLE_DEG = 1.5f


private const val WIGGLE_MS = 250

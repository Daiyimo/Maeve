package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.cap.web.SiteEntry
import java.text.SimpleDateFormat
import java.util.Date
import com.daiyimo.maeve.i18n.uiLocale

class WebSitesState(
    val sites: List<SiteEntry>,
    val onOpenLogin: ((String) -> Unit)?,
    val onLogout: (String) -> Int,
    val onLogoutAll: () -> Unit,
)


val WEB_LOGOUT_PARTIAL: String get() = tr(R.string.web_logout_partial)


val WEB_CHALLENGED_NOTE: String get() = tr(R.string.web_challenged_note)


val WEB_DATA_NOTE: String get() = tr(R.string.web_data_note)


private fun day(ms: Long): String = SimpleDateFormat(tr(R.string.date_pattern_md_compact), uiLocale()).format(Date(ms))

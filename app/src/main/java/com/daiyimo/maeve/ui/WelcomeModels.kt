package com.daiyimo.maeve.ui

import com.daiyimo.maeve.i18n.tr
import androidx.compose.ui.graphics.vector.ImageVector
import com.daiyimo.maeve.R
import com.daiyimo.maeve.ui.theme.MaeveIcons

object WelcomeCopy {
    val TITLE: String get() = tr(R.string.welcome_title)
    val LEAD: String get() = tr(R.string.welcome_lead)
    val NOTE: String get() = tr(R.string.welcome_note)
    val START: String get() = tr(R.string.welcome_start)

    data class Item(val icon: ImageVector, val title: String, val body: String)

    val FEATURES get() = listOf(
        Item(MaeveIcons.Apps, tr(R.string.welcome_apps), tr(R.string.welcome_apps_body)),
        Item(MaeveIcons.Globe, tr(R.string.welcome_web), tr(R.string.welcome_web_body)),
        Item(MaeveIcons.Doc, tr(R.string.welcome_files), tr(R.string.welcome_files_body)),
        Item(MaeveIcons.Clock, tr(R.string.welcome_schedule), tr(R.string.welcome_schedule_body)),
    )
}

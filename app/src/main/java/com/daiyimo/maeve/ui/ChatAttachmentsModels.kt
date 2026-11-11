package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.ui.theme.MaeveColors

object AttachCopy {
    val ADD: String get() = tr(R.string.attach_add)
    val CLOSE: String get() = tr(R.string.attach_close)
    val CAMERA: String get() = tr(R.string.attach_camera)
    val ALBUM: String get() = tr(R.string.attach_album)
    val FILES: String get() = tr(R.string.attach_files)
    val RECENT: String get() = tr(R.string.attach_recent)
    val RECENT_PARTIAL: String get() = tr(R.string.attach_recent_partial)
    val ALL: String get() = tr(R.string.attach_all)
    val CHANGE: String get() = tr(R.string.attach_change)
    val NO_PHOTOS: String get() = tr(R.string.attach_no_photos)
    val NEED_ACCESS: String get() = tr(R.string.attach_need_access)
    val GRANT: String get() = tr(R.string.attach_grant)
    val GRANT_HINT: String get() = tr(R.string.attach_grant_hint)
    val FULL: String get() = tr(R.string.attach_full, AttachTray.LIMIT)
    val NO_CAMERA: String get() = tr(R.string.attach_no_camera)
    val GONE: String get() = tr(R.string.attach_gone)
    val IMPORTING: String get() = tr(R.string.attach_importing)

    fun remove(name: String) = tr(R.string.attach_remove, name)
    fun overLimit(n: Int) = tr(R.string.attach_over_limit, AttachTray.LIMIT, n)
}


private fun badgeColor(kind: FileBadge.Kind, c: MaeveColors): Color = when (kind) {
    FileBadge.Kind.Pdf -> c.bad
    FileBadge.Kind.Sheet -> c.ok
    FileBadge.Kind.Doc -> c.accent
    FileBadge.Kind.Slides -> c.warnSolid
    FileBadge.Kind.Archive, FileBadge.Kind.Other -> NeutralBadge
}


private const val RECENT_PX = 256


private const val TRAY_PX = 180


private const val SINGLE_PX = 640


private val SingleWidth = 200.dp


private val NeutralBadge = Color(0xFF8E8E93)


private val TileShape = RoundedCornerShape(16.dp)


private val GridShape = RoundedCornerShape(12.dp)


private val CellShape = RoundedCornerShape(6.dp)


private val ChipShape = RoundedCornerShape(12.dp)


private val GridPhotoShape = RoundedCornerShape(14.dp)


private val FileCardShape = RoundedCornerShape(14.dp)


private val PanelText = TextStyle(fontSize = 13.5.sp, lineHeight = 21.sp)


private val TileText = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp)


private val LinkText = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp)


private val BadgeText = TextStyle(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)


private val BadgeSmall = TextStyle(fontSize = 8.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)


private val BadgeLarge = TextStyle(fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)


private val ChipName = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)


private val ChipMeta = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp)


private val FileCardName = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)

package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import android.util.LruCache
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BROWSER_DESKTOP_TAG: String get() = tr(R.string.browser_desktop)


val BROWSER_LOGIN_DONE: String get() = tr(R.string.browser_login_done)


internal val MiniShape = RoundedCornerShape(8.dp)


internal val InitialSmall = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)


private object SiteIcons {
    val cache = LruCache<String, ImageBitmap>(64)
}


private val TagText = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)

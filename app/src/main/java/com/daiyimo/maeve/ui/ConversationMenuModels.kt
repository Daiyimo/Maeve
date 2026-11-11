package com.daiyimo.maeve.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.max

internal data class MenuPlacement(val rowTop: Float, val menuTop: Float, val above: Boolean)


internal fun placeMenu(rowTop: Float, rowHeight: Float, menuHeight: Float, gap: Float, top: Float, bottom: Float): MenuPlacement {
    val row = rowTop.coerceIn(top, max(top, bottom - rowHeight))
    if (row + rowHeight + gap + menuHeight <= bottom) return MenuPlacement(row, row + rowHeight + gap, above = false)
    if (row - gap - menuHeight >= top) return MenuPlacement(row, row - gap - menuHeight, above = true)
    val moved = max(top, bottom - menuHeight - gap - rowHeight)
    return MenuPlacement(moved, moved + rowHeight + gap, above = false)
}


internal class MenuEntry(val text: String, val icon: ImageVector, val tag: String, val color: Color? = null, val onClick: () -> Unit)


internal val MenuBlur = 14.dp


private val MenuWidth = 220.dp


private val MenuGap = 8.dp


private const val LiftScale = 0.03f

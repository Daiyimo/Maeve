package com.daiyimo.maeve.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.daiyimo.maeve.ui.theme.MaeveMarkTile

/**
 * Mika 的标志:渐变圆角方块 + 双环 + 光点(见 [MaeveMarkTile])。
 * 圆角默认按边长的 0.28(36dp 约 10,76dp 约 22)。方块跟着当前配色走。
 */
@Composable
fun MikaLogo(size: Dp, modifier: Modifier = Modifier, corner: Dp = size * 0.28f, breathing: Boolean = false) {
    MaeveMarkTile(size, modifier, corner, breathing = breathing)
}

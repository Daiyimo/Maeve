package com.daiyimo.maeve.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import com.daiyimo.maeve.ui.haptic.HapticEngine

/**
 * 旧名兼容层。M2 设计系统落地后,主题的正式名字是 Maeve*(见同包各文件);
 * 全站 80+ 个界面文件仍在用的 Mika* 名字在这里保留为 deprecated 别名,
 * 行为完全转到新系统——不删旧名、不改旧文件,界面自动换新装。
 *
 * 新写的界面请直接用 [MaeveTheme] / [MaeveColors] / [MaeveType]。
 */

/** 旧类名。[MaeveColors] 的别名。 */
typealias MikaColors = MaeveColors

/** 旧 CompositionLocal。 */
val LocalMikaColors: ProvidableCompositionLocal<MaeveColors> get() = LocalMaeveColors

/** 默认配色(雾紫)的浅色 / 深色;界面测试等没有 Theme 包裹时用。 */
val LightColors: MaeveColors get() = MaevePalette.light(deriveAccent(AccentPreset.Reverie))
val DarkColors: MaeveColors get() = MaevePalette.dark(deriveAccent(AccentPreset.Reverie))

/** 旧 token 对象。 */
val MikaType: MaeveType get() = MaeveType
val MikaSpace: MaeveSpace get() = MaeveSpace
val MikaSize: MaeveSize get() = MaeveSize
val MikaRadius: MaeveRadius get() = MaeveRadius
val MikaShapes: MaeveShapes get() = MaeveShapes
val MikaIcons: MaeveIcons get() = MaeveIcons

/** 旧主题入口。 */
object MikaTheme {
    val colors: MaeveColors
        @Composable @ReadOnlyComposable get() = MaeveTheme.colors

    val semantic: MaeveSemantic
        @Composable @ReadOnlyComposable get() = MaeveTheme.semantic

    val surfaces: MaeveSurfaces
        @Composable @ReadOnlyComposable get() = MaeveTheme.surfaces

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = MaeveTheme.reduceMotion

    val haptics: HapticEngine
        @Composable @ReadOnlyComposable get() = MaeveTheme.haptics
}

@Composable
fun MikaTheme(dark: Boolean = appearanceDark(), reduceMotion: Boolean = false, content: @Composable () -> Unit) =
    MaeveTheme(dark = dark, reduceMotion = reduceMotion, content = content)

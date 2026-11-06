package com.daiyimo.maeve.ui.theme

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.daiyimo.maeve.settings.FeatureFlags
import com.daiyimo.maeve.settings.ThemeMode
import com.daiyimo.maeve.ui.haptic.HapticEngine
import com.daiyimo.maeve.ui.haptic.LocalHapticEngine
import com.daiyimo.maeve.ui.haptic.rememberHapticEngine

/** 系统「移除动画」(动画时长缩放为 0)时为真。 */
fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

val LocalReduceMotion = staticCompositionLocalOf { false }

object MaeveTheme {
    val colors: MaeveColors
        @Composable @ReadOnlyComposable get() = LocalMaeveColors.current

    /** 语义色(状态、工具色调、身份色)。 */
    val semantic: MaeveSemantic
        @Composable @ReadOnlyComposable get() = LocalMaeveSemantic.current

    /** 四档玻璃。 */
    val surfaces: MaeveSurfaces
        @Composable @ReadOnlyComposable get() = LocalMaeveSurfaces.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current

    /** 交互触感的唯一入口。所有按压、确认、拦截都从这里发。 */
    val haptics: HapticEngine
        @Composable @ReadOnlyComposable get() = LocalHapticEngine.current
}

/**
 * 设置里的外观:跟随系统时看系统,一直浅色/深色时照设置。设置一改当场换,不重建界面。
 */
@Composable
fun appearanceDark(): Boolean {
    val context = LocalContext.current
    val flags = remember(context) { FeatureFlags.get(context) }
    val mode by remember(flags) { flags.themeModeChanges() }.collectAsState(flags.themeMode())
    return when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
}

/**
 * 当前配色。设置页换了预设/自定义/壁纸都当场生效(存的时候广播,这里收)。
 */
@Composable
fun appearanceAccent(): AccentSpec {
    val context = LocalContext.current
    val store = remember(context) { MaeveAccentStore.get(context) }
    val accent by store.accent.collectAsState()
    return accent
}

/** 状态栏、导航栏的图标深浅跟界面一致。 */
fun ComponentActivity.systemBarsFor(dark: Boolean) {
    val transparent = android.graphics.Color.TRANSPARENT
    val lightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
    val darkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    enableEdgeToEdge(
        statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
        navigationBarStyle = if (dark) SystemBarStyle.dark(darkScrim) else SystemBarStyle.light(lightScrim, darkScrim),
    )
}

/**
 * 深浅色按设置里的外观([appearanceDark]);配色按 [appearanceAccent];
 * 同时把 Material 组件(弹窗、输入框、底部面板)的颜色对上。
 */
@Composable
fun MaeveTheme(
    dark: Boolean = appearanceDark(),
    accent: AccentSpec = appearanceAccent(),
    reduceMotion: Boolean = false,
    blurEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = remember(dark, accent) { if (dark) MaevePalette.dark(accent) else MaevePalette.light(accent) }
    val semantic = remember(dark) { if (dark) MaeveSemantic.dark else MaeveSemantic.light }
    val surfaces = remember(colors, blurEnabled) { buildSurfaces(colors, blurEnabled) }
    val scheme = remember(colors) { scheme(colors) }
    val layoutDirection = if (LocalContext.current.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalMaeveColors provides colors,
        LocalMaeveSemantic provides semantic,
        LocalMaeveSurfaces provides surfaces,
        LocalReduceMotion provides reduceMotion,
        LocalHapticEngine provides rememberHapticEngine(),
    ) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}

private fun scheme(c: MaeveColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentContainer, onPrimaryContainer = c.onAccentContainer,
        secondary = c.accent, onSecondary = c.onAccent,
        background = c.bg, onBackground = c.ink,
        surface = c.surface, onSurface = c.ink, surfaceVariant = c.fill, onSurfaceVariant = c.ink2,
        surfaceContainerLowest = c.surfaceLowest, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceHigh, surfaceContainerHighest = c.surfaceHighest, surfaceTint = c.surface,
        outline = c.ink3, outlineVariant = c.hair,
        error = c.bad, onError = c.onAccent,
    )
}

package com.daiyimo.maeve.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 界面用到的全部颜色。页面一律从 [MaeveTheme.colors] 取,不写死色值。
 *
 * 和旧 MikaColors 的字段一一对应(兼容),新增:
 * - [bgTop]/[bgBottom]:页面竖向渐变的两端(纯平底的时代结束了)
 * - [glowPrimary]/[glowSecondary]: backdrop 上两团光,给玻璃一点可透的颜色
 * - [surfaceLowest]/[surfaceHigh]/[surfaceHighest]:面色阶梯
 * - [accentPressed]/[accentContainer]/[onAccentContainer]/[accentBubble]/[accentGlow]:强调色阶
 * - [scrim]:弹层遮罩
 *
 * 实色只用在强调(主按钮、选中、开关、用户气泡)和状态(成功、需要你、出错);
 * 图标块一律中性。语义色(成功/警告/危险/信息、工具色调、身份色)在 [MaeveSemantic]。
 */
@Immutable
class MaeveColors(
    /** 页面背景(渐变的中值;无法铺渐变时用)。 */
    val bg: Color,
    /** 页面渐变顶端。 */
    val bgTop: Color,
    /** 页面渐变底端。 */
    val bgBottom: Color,
    /** 卡片、分组。 */
    val surface: Color,
    val surfaceLowest: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    /** 浅填充:次按钮、分段控件底、代码块底。 */
    val fill: Color,
    /** 文字三级:正文、次要、辅助。 */
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    /** 分隔线。 */
    val hair: Color,
    /** 卡片描边。 */
    val edge: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val accentBubble: Color,
    val accentGlow: Color,
    val accentSoft: Color,
    /** 强调色上的文字和图标。 */
    val onAccent: Color,
    val ok: Color,
    val okSoft: Color,
    /** 「需要你」:[warn] 用于文字,[warnSolid] 用于圆点、描边。 */
    val warn: Color,
    val warnSolid: Color,
    val warnSoft: Color,
    val bad: Color,
    val badSoft: Color,
    /** 图标块的底。 */
    val neutralSoft: Color,
    /** Maeve 回答的底:比页面背景深一点点的灰,和强调色气泡区分开。 */
    val answer: Color,
    /** 灰色胶囊、关着的开关。 */
    val greySoft: Color,
    /** 悬浮玻璃(输入栏、浮在内容上的按钮)的底和描边。 */
    val glass: Color,
    val glassEdge: Color,
    /** 玻璃顶边的高光(光从上方打在玻璃边上)。 */
    val glassRim: Color,
    /** 卡片阴影;深色下卡片只靠描边,为透明。 */
    val cardShadow: Color,
    /** 悬浮元素的阴影。 */
    val floatShadow: Color,
    /** 开关的圆钮。 */
    val thumb: Color,
    /** 弹层遮罩。 */
    val scrim: Color,
    /** backdrop 的光晕(强调色、暮色)。 */
    val glowPrimary: Color,
    val glowSecondary: Color,
    /**
     * 图表里分类的颜色(例如每家服务商一种),按固定的先后取;超出的归到 [seriesOther]。
     * 只给图形上色,文字一律用 ink 三级。
     */
    val series: List<Color>,
    val seriesOther: Color,
    /** 图表的网格线。 */
    val grid: Color,
    val isDark: Boolean,
)

/**
 * 由 [AccentSpec] 出 [MaeveColors]。中性色的色相转到配色的 [AccentSpec.tintHue],
 * 彩度乘 [AccentSpec.tintChroma];强调色直接用推导好的色位。明度不动,所以换配色时
 * 各级文字的对比度不漂移。
 */
object MaevePalette {

    fun light(spec: AccentSpec): MaeveColors {
        val t = spec.tintHue
        val k = spec.tintChroma
        fun n(L: Float, C: Float) = Oklch(L, C * k, t).toColor()
        val ink = n(0.28f, 0.030f)
        return MaeveColors(
            bg = n(0.968f, 0.006f),
            bgTop = n(0.978f, 0.007f),
            bgBottom = n(0.945f, 0.009f),
            surface = n(0.995f, 0.002f),
            surfaceLowest = n(0.998f, 0.0015f),
            surfaceHigh = n(0.972f, 0.004f),
            surfaceHighest = n(0.952f, 0.005f),
            fill = n(0.955f, 0.005f).copy(alpha = 0.55f),
            ink = ink,
            ink2 = guardText(n(0.48f, 0.025f), n(0.968f, 0.006f), 4.5f),
            ink3 = guardText(n(0.62f, 0.018f), n(0.968f, 0.006f), 3.0f),
            hair = ink.copy(alpha = 0.08f),
            edge = ink.copy(alpha = 0.06f),
            accent = spec.lightSeed,
            accentPressed = spec.lightPressed,
            accentContainer = spec.lightContainer,
            onAccentContainer = guardText(spec.lightOnContainer, spec.lightContainer, 4.5f),
            accentBubble = spec.lightBubble,
            accentGlow = spec.lightGlow,
            accentSoft = spec.lightContainer.copy(alpha = 0.55f),
            onAccent = Color.White,
            ok = MaeveSemantic.light.ok,
            okSoft = MaeveSemantic.light.okContainer,
            warn = MaeveSemantic.light.warn,
            warnSolid = MaeveSemantic.light.warnSolid,
            warnSoft = MaeveSemantic.light.warnContainer,
            bad = MaeveSemantic.light.bad,
            badSoft = MaeveSemantic.light.badContainer,
            neutralSoft = ink.copy(alpha = 0.05f),
            answer = ink.copy(alpha = 0.035f),
            greySoft = ink.copy(alpha = 0.10f),
            glass = Color.White.copy(alpha = 0.70f),
            glassEdge = ink.copy(alpha = 0.07f),
            glassRim = Color.White.copy(alpha = 0.65f),
            cardShadow = Color.Black,
            floatShadow = Color.Black,
            thumb = Color.White,
            scrim = Color.Black.copy(alpha = 0.32f),
            glowPrimary = spec.lightGlow.copy(alpha = 0.20f),
            glowSecondary = spec.hazeLight.copy(alpha = 0.16f),
            series = listOf(
                Oklch(0.55f, 0.13f, 255f).toColor(),
                Oklch(0.58f, 0.13f, 45f).toColor(),
                Oklch(0.55f, 0.11f, 150f).toColor(),
                Oklch(0.55f, 0.12f, 320f).toColor(),
            ),
            seriesOther = n(0.62f, 0.018f),
            grid = ink.copy(alpha = 0.06f),
            isDark = false,
        )
    }

    fun dark(spec: AccentSpec): MaeveColors {
        val t = spec.tintHue
        val k = spec.tintChroma
        fun n(L: Float, C: Float) = Oklch(L, C * k, t).toColor()
        val ink = n(0.945f, 0.010f)
        val bg = n(0.155f, 0.012f)
        return MaeveColors(
            bg = bg,
            bgTop = n(0.175f, 0.013f),
            bgBottom = n(0.135f, 0.011f),
            surface = n(0.185f, 0.013f),
            surfaceLowest = n(0.145f, 0.011f),
            surfaceHigh = n(0.225f, 0.014f),
            surfaceHighest = n(0.265f, 0.015f),
            fill = ink.copy(alpha = 0.06f),
            ink = ink,
            ink2 = guardText(n(0.760f, 0.014f), bg, 4.5f),
            ink3 = guardText(n(0.600f, 0.016f), bg, 3.0f),
            hair = ink.copy(alpha = 0.09f),
            edge = ink.copy(alpha = 0.07f),
            accent = spec.darkSeed,
            accentPressed = spec.darkPressed,
            accentContainer = spec.darkContainer,
            onAccentContainer = guardText(spec.darkOnContainer, spec.darkContainer, 4.5f),
            accentBubble = spec.darkBubble,
            accentGlow = spec.darkGlow,
            accentSoft = spec.darkContainer.copy(alpha = 0.60f),
            onAccent = spec.darkOnSeed,
            ok = MaeveSemantic.dark.ok,
            okSoft = MaeveSemantic.dark.okContainer,
            warn = MaeveSemantic.dark.warn,
            warnSolid = MaeveSemantic.dark.warnSolid,
            warnSoft = MaeveSemantic.dark.warnContainer,
            bad = MaeveSemantic.dark.bad,
            badSoft = MaeveSemantic.dark.badContainer,
            neutralSoft = ink.copy(alpha = 0.07f),
            answer = ink.copy(alpha = 0.045f),
            greySoft = ink.copy(alpha = 0.14f),
            glass = n(0.185f, 0.013f).copy(alpha = 0.70f),
            glassEdge = ink.copy(alpha = 0.09f),
            glassRim = Color.White.copy(alpha = 0.14f),
            cardShadow = Color.Transparent,
            floatShadow = Color.Black,
            thumb = Color.White,
            scrim = Color.Black.copy(alpha = 0.48f),
            glowPrimary = spec.darkGlow.copy(alpha = 0.16f),
            glowSecondary = spec.hazeDark.copy(alpha = 0.14f),
            series = listOf(
                Oklch(0.72f, 0.12f, 255f).toColor(),
                Oklch(0.75f, 0.12f, 45f).toColor(),
                Oklch(0.72f, 0.10f, 150f).toColor(),
                Oklch(0.72f, 0.11f, 320f).toColor(),
            ),
            seriesOther = n(0.600f, 0.016f),
            grid = ink.copy(alpha = 0.08f),
            isDark = true,
        )
    }
}

/** 没有包在 [MaeveTheme] 里时(比如界面测试)按默认配色(雾紫)的浅色。 */
val LocalMaeveColors = staticCompositionLocalOf { MaevePalette.light(deriveAccent(AccentPreset.Reverie)) }

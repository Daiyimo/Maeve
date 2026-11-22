package com.daiyimo.maeve.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.ui.components.GlassBackdrop
import com.daiyimo.maeve.ui.components.rememberGlassBackdrop

/**
 * 表面层级。Card / Pill 长在录制内容里,因此只画填充和边光;Chrome / Sheet
 * 由内容节点单向录制背景,API 33+ 再叠加边缘折射。
 */
enum class GlassTier { Chrome, Sheet, Card, Pill }

/** 液态玻璃浓度。id 与 shared/glass_tokens.json、Flutter 设置保持一致。 */
enum class GlassDensity(val id: String, val label: String) {
    Clear("clear", "清透"),
    Balanced("balanced", "均衡"),
    Dense("dense", "浓郁");

    companion object {
        fun byId(id: String?): GlassDensity = entries.firstOrNull { it.id == id } ?: Balanced
    }
}

/** 迁移旧「减少透明效果」偏好。 */
fun resolveGlassStyle(prefs: android.content.SharedPreferences): String {
    val current = prefs.getString("glassStyle", null)
    val hasLegacy = prefs.contains("reduceTransparency")
    if (current != null && !hasLegacy) return current
    val resolved = current ?: if (prefs.getBoolean("reduceTransparency", false)) "clear" else "balanced"
    prefs.edit().putString("glassStyle", resolved).remove("reduceTransparency").commit()
    return resolved
}

/** 一种表面材质的全部参数。长度单位均为 dp。 */
@Immutable
data class GlassStyle(
    val fill: Color,
    /** 不支持采样时的填充:由 fill composite over 画布得到,alpha 恒为 1。 */
    val fallbackFill: Color,
    val hairline: Color,
    val hairlineWidth: Dp = 0.75.dp,
    val rim: Color = Color.Transparent,
    /** 玻璃内部自上而下的镜面提亮。 */
    val sheen: Color = Color.Transparent,
    val sheenStop: Float = 0.50f,
    /** API 33+ 折射路径的低模糊 σ。 */
    val blurSigma: Float = 0f,
    /** API 31-32 或 shader 不可用时的 blur-only σ。 */
    val fallbackBlurSigma: Float = 0f,
    val saturation: Float = 1f,
    val outerShadow: Color = Color.Transparent,
    /** 兼容字段;实际阴影由 shadowBlur/shadowOffsetY 精确绘制。 */
    val elevation: Dp = 0.dp,
    val shadowBlur: Dp = 0.dp,
    val shadowOffsetY: Dp = 0.dp,
    val rimFraction: Float = 0.30f,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val catchLight: Color = Color.Transparent,
    val bevelTop: Color = Color.Transparent,
    val bevelSide: Color = Color.Transparent,
    val bevelBase: Color = Color.Transparent,
    val band: Dp = 6.dp,
    /** 边缘最大位移。 */
    val refractionStrength: Dp = 0.dp,
    /** 位移从 0 增长到最大值的边缘带宽度。 */
    val refractionWidth: Dp = 0.dp,
    /** RGB 色散的最大偏移。 */
    val chromaticAberration: Dp = 0.dp,
    val specularStrength: Float = 0f,
) {
    val blurs: Boolean get() = blurSigma > 0f || fallbackBlurSigma > 0f
    val refracts: Boolean get() = refractionStrength > 0.dp && refractionWidth > 0.dp
}

/** Inactive surfaces keep card/pill translucent, but chrome/sheet must stay opaque. */
internal fun GlassStyle.inactiveFill(tier: GlassTier): Color = when (tier) {
    GlassTier.Chrome, GlassTier.Sheet -> fallbackFill
    GlassTier.Card, GlassTier.Pill -> fill
}

/** 四档表面,由 [MaeveTheme] 提供。 */
@Immutable
class MaeveSurfaces(
    val chrome: GlassStyle,
    val sheet: GlassStyle,
    val card: GlassStyle,
    val pill: GlassStyle,
    val density: GlassDensity = GlassDensity.Balanced,
) {
    operator fun get(tier: GlassTier): GlassStyle = when (tier) {
        GlassTier.Chrome -> chrome
        GlassTier.Sheet -> sheet
        GlassTier.Card -> card
        GlassTier.Pill -> pill
    }
}

private data class DensityScale(
    val tint: Float,
    val blur: Float,
    val saturation: Float,
    val light: Float,
    val refraction: Float,
)

private data class LightDark(val light: Float, val dark: Float) {
    fun resolve(dark: Boolean): Float = if (dark) this.dark else light
}

private data class ShadowTokens(
    val blur: Float,
    val offsetY: Float,
    val alpha: LightDark,
)

private data class TierTokens(
    val fillAlpha: LightDark,
    val blurSigma: Float,
    val fallbackBlurSigma: Float,
    val saturation: Float,
    val refractionStrength: Float,
    val refractionWidth: Float,
    val chromaticAberration: Float,
    val specularStrength: Float,
    val hairlineAlpha: LightDark,
    val rimAlpha: LightDark,
    val sheenAlpha: LightDark,
    val rimFraction: Float,
    val sheenStop: Float,
    val band: Float,
    val catchLightAlpha: LightDark,
    val bevelTopAlpha: LightDark,
    val bevelSideAlpha: LightDark,
    val bevelBaseAlpha: LightDark,
    val shadow: ShadowTokens,
)

/** shared/glass_tokens.json 的 Kotlin 镜像;修改契约时必须同步修改这里。 */
private object GlassTokenContract {
    val clear = DensityScale(0.45f, 0.70f, 0.90f, 0.60f, 0.85f)
    val balanced = DensityScale(1.00f, 1.00f, 1.00f, 1.00f, 1.00f)
    val dense = DensityScale(1.45f, 1.35f, 1.10f, 1.40f, 1.15f)
    val lightTone = 1.04f to 1.02f
    val darkTone = 0.94f to 1.02f

    val chrome = TierTokens(
        fillAlpha = LightDark(0.10f, 0.14f),
        blurSigma = 3f,
        fallbackBlurSigma = 6f,
        saturation = 1.28f,
        refractionStrength = 14f,
        refractionWidth = 16f,
        chromaticAberration = 0.65f,
        specularStrength = 0.55f,
        hairlineAlpha = LightDark(0.14f, 0.20f),
        rimAlpha = LightDark(0.55f, 0.32f),
        sheenAlpha = LightDark(0.14f, 0.06f),
        rimFraction = 0.46f,
        sheenStop = 0.62f,
        band = 6f,
        catchLightAlpha = LightDark(0.65f, 0.36f),
        bevelTopAlpha = LightDark(0.30f, 0.14f),
        bevelSideAlpha = LightDark(0.14f, 0.06f),
        bevelBaseAlpha = LightDark(0.06f, 0.12f),
        shadow = ShadowTokens(24f, 6f, LightDark(0.08f, 0.18f)),
    )

    val sheet = TierTokens(
        fillAlpha = LightDark(0.14f, 0.18f),
        blurSigma = 4f,
        fallbackBlurSigma = 8f,
        saturation = 1.34f,
        refractionStrength = 18f,
        refractionWidth = 20f,
        chromaticAberration = 0.85f,
        specularStrength = 0.65f,
        hairlineAlpha = LightDark(0.16f, 0.22f),
        rimAlpha = LightDark(0.62f, 0.36f),
        sheenAlpha = LightDark(0.16f, 0.07f),
        rimFraction = 0.22f,
        sheenStop = 0.34f,
        band = 7f,
        catchLightAlpha = LightDark(0.70f, 0.40f),
        bevelTopAlpha = LightDark(0.34f, 0.16f),
        bevelSideAlpha = LightDark(0.16f, 0.07f),
        bevelBaseAlpha = LightDark(0.07f, 0.14f),
        shadow = ShadowTokens(28f, 12f, LightDark(0.14f, 0.26f)),
    )

    val card = TierTokens(
        fillAlpha = LightDark(0.12f, 0.16f),
        blurSigma = 0f,
        fallbackBlurSigma = 0f,
        saturation = 1f,
        refractionStrength = 0f,
        refractionWidth = 0f,
        chromaticAberration = 0f,
        specularStrength = 0f,
        hairlineAlpha = LightDark(0.12f, 0.18f),
        rimAlpha = LightDark(0.38f, 0.24f),
        sheenAlpha = LightDark(0.10f, 0.05f),
        rimFraction = 0.40f,
        sheenStop = 0.50f,
        band = 5f,
        catchLightAlpha = LightDark(0.48f, 0.28f),
        bevelTopAlpha = LightDark(0.22f, 0.12f),
        bevelSideAlpha = LightDark(0.10f, 0.05f),
        bevelBaseAlpha = LightDark(0.05f, 0.10f),
        shadow = ShadowTokens(14f, 3f, LightDark(0.05f, 0.10f)),
    )

    val pill = TierTokens(
        fillAlpha = LightDark(0.16f, 0.20f),
        blurSigma = 0f,
        fallbackBlurSigma = 0f,
        saturation = 1f,
        refractionStrength = 0f,
        refractionWidth = 0f,
        chromaticAberration = 0f,
        specularStrength = 0f,
        hairlineAlpha = LightDark(0.14f, 0.20f),
        rimAlpha = LightDark(0.45f, 0.28f),
        sheenAlpha = LightDark(0.12f, 0.05f),
        rimFraction = 0.50f,
        sheenStop = 0.50f,
        band = 5f,
        catchLightAlpha = LightDark(0.52f, 0.30f),
        bevelTopAlpha = LightDark(0.24f, 0.12f),
        bevelSideAlpha = LightDark(0.11f, 0.05f),
        bevelBaseAlpha = LightDark(0.05f, 0.10f),
        shadow = ShadowTokens(0f, 0f, LightDark(0f, 0f)),
    )
}

/** 由颜色、模糊开关与浓度出四档表面;API 分档由采样运行时负责。 */
fun buildSurfaces(
    c: MaeveColors,
    blurEnabled: Boolean,
    density: GlassDensity = GlassDensity.Balanced,
): MaeveSurfaces {
    val scale = when (density) {
        GlassDensity.Clear -> GlassTokenContract.clear
        GlassDensity.Balanced -> GlassTokenContract.balanced
        GlassDensity.Dense -> GlassTokenContract.dense
    }
    val tone = if (c.isDark) GlassTokenContract.darkTone else GlassTokenContract.lightTone
    val white = Color.White

    fun fill(base: Color, alpha: Float): Color =
        base.copy(alpha = (alpha * scale.tint).coerceIn(0f, 0.92f))

    fun light(alpha: LightDark): Color =
        white.copy(alpha = (alpha.resolve(c.isDark) * scale.light).coerceIn(0f, 1f))

    fun style(base: Color, t: TierTokens): GlassStyle {
        val filled = fill(base, t.fillAlpha.resolve(c.isDark))
        return GlassStyle(
            fill = filled,
            fallbackFill = filled.compositeOver(c.bg),
            hairline = c.glassEdge.copy(alpha = t.hairlineAlpha.resolve(c.isDark).coerceIn(0f, 1f)),
            rim = light(t.rimAlpha),
            sheen = light(t.sheenAlpha),
            sheenStop = t.sheenStop,
            blurSigma = if (blurEnabled) t.blurSigma * scale.blur else 0f,
            fallbackBlurSigma = if (blurEnabled) t.fallbackBlurSigma * scale.blur else 0f,
            saturation = t.saturation * scale.saturation,
            outerShadow = c.floatShadow.copy(alpha = t.shadow.alpha.resolve(c.isDark)),
            elevation = (t.shadow.blur / 2f).dp,
            shadowBlur = t.shadow.blur.dp,
            shadowOffsetY = t.shadow.offsetY.dp,
            rimFraction = t.rimFraction,
            brightness = tone.first,
            contrast = tone.second,
            catchLight = light(t.catchLightAlpha),
            bevelTop = light(t.bevelTopAlpha),
            bevelSide = light(t.bevelSideAlpha),
            bevelBase = c.ink.copy(alpha = (t.bevelBaseAlpha.resolve(c.isDark) * scale.light).coerceIn(0f, 1f)),
            band = t.band.dp,
            refractionStrength = if (blurEnabled) (t.refractionStrength * scale.refraction).dp else 0.dp,
            refractionWidth = if (blurEnabled) (t.refractionWidth * scale.refraction).dp else 0.dp,
            chromaticAberration = if (blurEnabled) (t.chromaticAberration * scale.refraction).dp else 0.dp,
            specularStrength = if (blurEnabled) t.specularStrength * scale.light else 0f,
        )
    }

    return MaeveSurfaces(
        density = density,
        chrome = style(c.glass, GlassTokenContract.chrome),
        sheet = style(if (c.isDark) c.glassSheetDark else c.glassSheet, GlassTokenContract.sheet),
        card = style(c.glassCard, GlassTokenContract.card),
        pill = style(c.glassPill, GlassTokenContract.pill),
    )
}

/** 表面材质的 CompositionLocal;由 [MaeveTheme] 提供。 */
val LocalMaeveSurfaces = staticCompositionLocalOf {
    buildSurfaces(MaevePalette.light(deriveAccent(AccentPreset.Reverie)), blurEnabled = true)
}

/** null = 这一页没有 backdrop(或不支持模糊):玻璃退化为不透明底。 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** 全屏页面背景:中性渐变 + 三团极光。 */
@Composable
fun MaeveBackdrop(
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    Box(
        modifier.fillMaxSize().maeveBackdrop(MaeveTheme.colors),
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            content()
        }
    }
}

/** 背景修饰符(不创建玻璃运行时的轻量版)。 */
fun Modifier.maeveBackdrop(c: MaeveColors, glow: Boolean = true): Modifier = drawBehind {
    drawAurora(c)
}

internal fun DrawScope.drawAurora(c: MaeveColors) {
    drawRect(Brush.verticalGradient(0f to c.bgTop, 0.58f to c.bg, 1f to c.bgBottom))

    val w = size.width
    val h = size.height
    val unit = minOf(w, h)
    drawRect(
        Brush.radialGradient(
            colors = listOf(c.glowPrimary, Color.Transparent),
            center = Offset(w * 0.14f, h * 0.06f),
            radius = unit * 0.92f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(c.glowSecondary, Color.Transparent),
            center = Offset(w * 0.92f, h * 0.26f),
            radius = unit * 0.78f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(c.glowTertiary, Color.Transparent),
            center = Offset(w * 0.44f, h * 1.02f),
            radius = unit * 0.86f,
        ),
    )
}

/** 遮罩:弹层、抽屉后面的那层。 */
fun Modifier.maeveScrim(c: MaeveColors): Modifier = this.background(c.scrim)

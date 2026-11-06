package com.daiyimo.maeve.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OKLCH 色彩科学:整套配色的推导都过这一把尺子。
 *
 * 为什么不用 `ColorScheme.fromSeed`:M3 tonalSpot 会把任何种子推成一套高彩度色阶,
 * 恰好抹掉 Maeve 要的低饱和。所以每个色位都按规则现推,推完再过对比度守卫。
 *
 * 实现是标准 Björn Ottosson OKLab 变换的 Kotlin 直译(公式公开、无版权负担),
 * 没有复制任何第三方源码。
 */

/** OKLCh:感知明度 L(0..1)、彩度 C(通常 0..0.4)、色相 h(角度)。 */
data class Oklch(val L: Float, val C: Float, val h: Float)

// ---------- sRGB ↔ 线性 ----------

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(c: Float): Float =
    if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f

// ---------- Color → OKLCH ----------

/** [Color] 的 sRGB 字节(0..255)转 OKLCH。 */
fun Color.toOklch(): Oklch {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    return oklabToLch(r, g, b)
}

private fun oklabToLch(r: Float, g: Float, b: Float): Oklch {
    val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
    val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
    val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b
    val l_ = cbrt(l)
    val m_ = cbrt(m)
    val s_ = cbrt(s)
    val L = 0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_
    val a = 1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_
    val bb = 0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_
    return Oklch(L.coerceIn(0f, 1f), sqrt(a * a + bb * bb), radiansToDegrees(atan2(bb, a)))
}

private fun radiansToDegrees(r: Float): Float {
    var d = r * 180f / Math.PI.toFloat()
    if (d < 0f) d += 360f
    return d
}

private fun degreesToRadians(d: Float): Float = d * Math.PI.toFloat() / 180f

// ---------- OKLCH → Color ----------

/**
 * OKLCH 转 sRGB 颜色。超出色域时**只压彩度、不动明度**
 * (sRGB 直接截断会让感知亮度跳变,金色截断后会发橙、发脏)。
 */
fun Oklch.toColor(): Color {
    var chroma = C
    var rgb = lchToLinearSrgb(L, chroma, h)
    var guard = 0
    while (!inGamut(rgb) && guard++ < 64) {
        chroma *= 0.96f
        rgb = lchToLinearSrgb(L, chroma, h)
    }
    return Color(
        linearToSrgb(rgb[0].coerceIn(0f, 1f)),
        linearToSrgb(rgb[1].coerceIn(0f, 1f)),
        linearToSrgb(rgb[2].coerceIn(0f, 1f)),
    )
}

private fun inGamut(rgb: FloatArray): Boolean =
    rgb[0] in -0.0001f..1.0001f && rgb[1] in -0.0001f..1.0001f && rgb[2] in -0.0001f..1.0001f

private fun lchToLinearSrgb(L: Float, C: Float, hDeg: Float): FloatArray {
    val h = degreesToRadians(hDeg)
    val a = C * cos(h)
    val b = C * sin(h)
    val l_ = L + 0.3963377774f * a + 0.2158037573f * b
    val m_ = L - 0.1055613458f * a - 0.0638541728f * b
    val s_ = L - 0.0894841775f * a - 1.2914855480f * b
    val l = l_ * l_ * l_
    val m = m_ * m_ * m_
    val s = s_ * s_ * s_
    return floatArrayOf(
        +4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
        -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
        -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
    )
}

/** 按色相旋转:同一感知明度下换色相(中性色随强调色转调用)。 */
fun Oklch.withHue(hue: Float): Oklch = copy(h = hue)

/** 彩度乘一个倍数,明度、色相不动。 */
fun Oklch.scaledChroma(factor: Float): Oklch = copy(C = (C * factor).coerceAtLeast(0f))

/** 明度加减,色相、彩度不动。 */
fun Oklch.withLightness(L: Float): Oklch = copy(L = L.coerceIn(0f, 1f))

// ---------- 对比度(WCAG 2.1)----------

/** 相对亮度。 */
fun Color.luminance(): Float {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/** WCAG 对比度,1..21。文字 ≥ 4.5,图形 ≥ 3。 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** 把 [fg] 调暗/调亮直到对 [bg] 达到 [target];色相不动,只动明度。 */
fun guardText(fg: Color, bg: Color, target: Float): Color {
    var c = fg.toOklch()
    if (contrastRatio(c.toColor(), bg) >= target) return c.toColor()
    // 按 0.02 步长往远离底色的方向走明度,最多 20 步
    val darker = bg.luminance() > 0.35f
    repeat(20) {
        c = if (darker) c.withLightness(c.L - 0.02f) else c.withLightness(c.L + 0.02f)
        if (contrastRatio(c.toColor(), bg) >= target) return c.toColor()
    }
    return c.toColor()
}

/** 十六进制快捷构造(测试与预设用)。 */
fun hex(color: Long): Color = Color(color or 0xFF000000L)

/** 两个颜色按 [t] 插值(主题切换时用)。 */
fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)

/** 角度归一化到 [0,360)。 */
internal fun normDeg(d: Float): Float {
    var x = d % 360f
    if (x < 0f) x += 360f
    return x
}

/** 两个色相之间的角距离(0..180)。 */
internal fun hueDistance(a: Float, b: Float): Float {
    val d = abs(normDeg(a) - normDeg(b)) % 360f
    return if (d > 180f) 360f - d else d
}

/** 正弦快捷(内部用)。 */
internal fun sinDeg(d: Float): Float = sin(degreesToRadians(d))

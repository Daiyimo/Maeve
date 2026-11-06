package com.daiyimo.maeve.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** dsh-Go 的连续曲率半径体系;数值来自 docs/spec/visual-tokens.md §3。 */
object MaeveRadius {
    val Xs = 6.dp
    val Sm = 10.dp
    val Md = 14.dp
    val Lg = 20.dp
    val Xl = 28.dp
}

/**
 * 连续曲率圆角(squircle)。RoundedCornerShape 是「直线 + 四分之一圆弧」,切线连续但曲率
 * 在接缝处跳变,看起来仍是标准 Android 卡片;squircle 用超椭圆让曲率平滑过渡。
 *
 * 用参数方程在 Kotlin/Compose 里重画,只借鉴 dsh-Go 的设计决策(连续曲率、半径分档),
 * 没有复制其 AGPL-3.0 实现或任何素材。
 */
class SquircleShape(
    private val radius: Dp,
    private val exponent: Float = 4.2f,
    private val samplesPerCorner: Int = 14,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }.coerceIn(0f, minOf(w, h) / 2f)
        if (r <= 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))

        val path = Path()
        val n = 2f / exponent
        val halfPi = (Math.PI / 2).toFloat()
        val pi = Math.PI.toFloat()

        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        corner(path, w - r, r, -halfPi, 0f, r, n)
        path.lineTo(w, h - r)
        corner(path, w - r, h - r, 0f, halfPi, r, n)
        path.lineTo(r, h)
        corner(path, r, h - r, halfPi, pi, r, n)
        path.lineTo(0f, r)
        corner(path, r, r, pi, pi + halfPi, r, n)
        path.close()
        return Outline.Generic(path)
    }

    private fun corner(
        path: Path,
        cx: Float,
        cy: Float,
        from: Float,
        to: Float,
        r: Float,
        n: Float,
    ) {
        for (i in 1..samplesPerCorner) {
            val t = from + (to - from) * i / samplesPerCorner
            val c = cos(t)
            val s = sin(t)
            val x = cx + r * sign(c) * abs(c).pow(n)
            val y = cy + r * sign(s) * abs(s).pow(n)
            path.lineTo(x, y)
        }
    }
}

/** 全站共用的一组连续曲率形状。改这里等于改所有卡片和按钮。 */
object MaeveShapes {
    /** 卡片、按钮、图标块统一走连续曲率(squircle),改一处等于改全站。 */
    val Card = SquircleShape(MaeveRadius.Lg)
    /** 用户消息气泡:右下角是尾角。 */
    val UserBubble = RoundedCornerShape(MaeveRadius.Lg, MaeveRadius.Lg, MaeveRadius.Xs, MaeveRadius.Lg)
    /** Maeve 的回答:左下角是尾角,和用户气泡对称。 */
    val AnswerBubble = RoundedCornerShape(MaeveRadius.Lg, MaeveRadius.Lg, MaeveRadius.Lg, MaeveRadius.Xs)
    val Button = SquircleShape(MaeveRadius.Md)
    val Tile = SquircleShape(MaeveRadius.Sm)
    val Sheet = SquircleShape(MaeveRadius.Xl)
    val Pill = CircleShape
}

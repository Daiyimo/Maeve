package com.daiyimo.maeve.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Maeve 的标志:两个错开的环 + 一个偏离中心的光点。
 *
 * - 外环 = 你的主屏;内环 = 她干活的那块隐藏显示;光点 = 此刻正在跑的那一步。
 * - 光点停在外环 45° 的位置上——"这一步踩在你的屏边,但不占你的屏"。
 * - 几何与启动图标(`maeve_launcher_mono` / `maeve_launcher_art`)同一套 24 网格,
 *   标志和图标永远不会各长各的。
 *
 * [breathing] 为真时(且没关动画)光点缓慢明暗:2.4s 一个周期,慢到不打扰。
 */
object MaeveMarkGeo {
    /** 24 网格 viewport 里的几何。 */
    const val VIEWPORT = 24f
    const val OUTER_CX = 11.1f
    const val OUTER_CY = 11.1f
    const val OUTER_R = 5.33f
    const val INNER_CX = 12.9f
    const val INNER_CY = 12.7f
    const val INNER_R = 3.33f
    /** 光点:外环 45° 处的点。 */
    val DOT = Offset(OUTER_CX + OUTER_R * 0.7071f, OUTER_CY - OUTER_R * 0.7071f)
    const val DOT_R = 0.78f
}

@Composable
fun MaeveMark(
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color = MaeveTheme.colors.ink2,
    dotColor: Color = MaeveTheme.colors.accent,
    glowColor: Color = MaeveTheme.colors.accentGlow,
    strokeWidth: Dp = 1.9.dp,
    breathing: Boolean = false,
    /** 光点背后的柔光(任务在跑时打开,静态标志关掉)。 */
    glow: Boolean = false,
) {
    val reduce = MaeveTheme.reduceMotion
    val breathe = if (breathing && !reduce) {
        val transition = rememberInfiniteTransition(label = "mark-breath")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(MaeveMotion.BreathMs, easing = MaeveMotion.Glide), RepeatMode.Reverse),
            label = "mark-breath-t",
        )
        t
    } else {
        if (breathing) 0.5f else 1f
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / MaeveMarkGeo.VIEWPORT
        fun cx(v: Float) = v * s
        val outerR = MaeveMarkGeo.OUTER_R * s
        val innerR = MaeveMarkGeo.INNER_R * s
        val stroke = (strokeWidth.value * s).coerceAtLeast(1f)
        val dotC = Offset(cx(MaeveMarkGeo.DOT.x), cx(MaeveMarkGeo.DOT.y))
        val dotR = MaeveMarkGeo.DOT_R * s * (1f + 0.10f * breathe)
        if (glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor.copy(alpha = 0.5f + 0.3f * breathe), Color.Transparent),
                    center = dotC,
                    radius = dotR * 5f,
                ),
                radius = dotR * 5f,
                center = dotC,
            )
        }
        drawCircle(
            color = ringColor,
            radius = outerR,
            center = Offset(cx(MaeveMarkGeo.OUTER_CX), cx(MaeveMarkGeo.OUTER_CY)),
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = ringColor,
            radius = innerR,
            center = Offset(cx(MaeveMarkGeo.INNER_CX), cx(MaeveMarkGeo.INNER_CY)),
            style = Stroke(width = stroke * 0.9f),
        )
        drawCircle(
            color = dotColor.copy(alpha = 0.65f + 0.35f * breathe),
            radius = dotR,
            center = dotC,
        )
    }
}

/**
 * 标志方块:渐变圆角方底 + 双环标志。启动图、关于页、欢迎页用。
 * 渐变跟着当前配色走,换配色时方块也换色调。
 */
@Composable
fun MaeveMarkTile(
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = size * 0.28f,
    breathing: Boolean = false,
) {
    val c = MaeveTheme.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = corner.toPx()
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(c.accent, c.accentPressed),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        }
        MaeveMark(
            size = size * 0.62f,
            ringColor = Color.White.copy(alpha = 0.92f),
            dotColor = Color.White,
            glowColor = Color.Transparent,
            breathing = breathing,
        )
    }
}

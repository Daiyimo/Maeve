package com.daiyimo.maeve.capsule

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import com.daiyimo.maeve.ui.theme.MaeveTheme
import kotlin.math.*

/** The same hollow eclipse geometry as the Flutter identity, without a colored tile. */
@Composable
internal fun CapsuleEmblem(modifier: Modifier, running: Boolean = false) {
    val phase = if (running && !MaeveTheme.reduceMotion) {
        val p by rememberInfiniteTransition(label = "capsule-eclipse").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "edge")
        p
    } else 0f
    Canvas(modifier) {
        val unit = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        val purple = Color(0xff7654c5)
        fun point(a: Float, r: Float) = center + Offset(cos(a) * r * unit, sin(a) * r * unit)
        fun thickness(a: Float) = .006f + .038f * ((1f + cos(a + PI.toFloat() / 4)) / 2).pow(1.7f)
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            for (i in 0..240) {
                val a = i * 2 * PI.toFloat() / 240
                val p = point(a, .355f + thickness(a))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
            for (i in 0..240) {
                val p = point(i * 2 * PI.toFloat() / 240, .355f)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(path, purple)
        for (i in 0 until 160) {
            val a = i * 2 * PI.toFloat() / 160
            val outer = .355f + thickness(a)
            val grain = ((i * 73 + 17) % 101) / 100f
            val d = atan2(sin(a + PI.toFloat() / 4), cos(a + PI.toFloat() / 4))
            val disturbance = if (running) exp(-d * d / .04f) * .075f * (.35f + .65f * grain) * (.8f + .2f * sin(phase * 2 * PI.toFloat() + a * 13)) else 0f
            drawLine(purple.copy(alpha = .35f + .45f * grain), point(a, outer), point(a, outer + .002f + .009f * grain + disturbance),
                strokeWidth = (unit * .004f).coerceAtLeast(.5f), cap = StrokeCap.Round)
        }
    }
}

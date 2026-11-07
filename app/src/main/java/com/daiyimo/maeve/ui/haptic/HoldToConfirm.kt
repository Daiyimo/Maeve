package com.daiyimo.maeve.ui.haptic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.ui.theme.MaeveMotion
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

/**
 * 危险动作的长按确认:1500 ms 环形进度,按下一次 Tick,完成一次 Heavy,取消不发任何反馈。
 *
 * 只用原始指针事件,不经过 clickable / combinedClickable —— 无障碍服务派发的合成 click
 * 或一次误触都无法完成确认。刻意不跟随 reduceMotion:确认时长归零会让危险动作一碰即发。
 * 数值与语义来自 docs/spec/haptic-matrix.md §3。
 */
@Composable
fun HoldToConfirm(
    label: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    durationMs: Int = MaeveMotion.HoldToConfirmMs,
) {
    val c = MaeveTheme.colors
    val haptics = MaeveTheme.haptics
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (holding) durationMs else MaeveMotion.QuickMs,
            easing = LinearEasing,
        ),
    )

    LaunchedEffect(progress, holding) {
        if (holding && progress >= 0.999f) {
            haptics.heavy()
            onConfirmed()
            holding = false
        }
    }

    Box(
        modifier
            .size(112.dp)
            .pointerInput(enabled, durationMs, haptics) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    haptics.tick()
                    holding = true
                    // 只认同一根指针的原始抬起;期间没有事件也会挂起,进度动画独立跑满即确认
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                    }
                    holding = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(112.dp)) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(c.fill, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(c.accent, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(stroke))
        }
        Text(
            label, Modifier.padding(horizontal = 16.dp),
            style = MaeveType.Hint, color = c.ink2, textAlign = TextAlign.Center,
        )
    }
}

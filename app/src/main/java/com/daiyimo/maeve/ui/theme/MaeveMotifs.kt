package com.daiyimo.maeve.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Maeve 的四个图形母题。它们不是装饰,每一个都对应界面里一件真事:
 *
 * | 母题 | 对应的事 |
 * |---|---|
 * | [MazeRing] | 一个任务从入口走到中心的过程。**替代全站所有转圈** |
 * | [PianoRoll] | 一个循环里已经弹过和正在弹的那一步。**替代「正在思考…」** |
 * | [ScriptRail] | 这是第几轮对话、她说到哪儿了。剧本的边注 |
 * | [CalibrationFrame] | 这块是仪表,不是正文。四角刻度 |
 *
 * 四个都用矢量现画,没有位图、没有第三方素材。
 */

// ============================================================
// 一、迷宫环
// ============================================================

/**
 * 迷宫环:一条从外向内收的阿基米德螺线。光点沿螺线往里走,走到中心 = 这件事办完了。
 *
 * 为什么不用转圈:转圈只表达「在忙」,不表达**进展**——转十圈和转一圈长得一样,
 * 用户没法判断她是快到了还是刚出发。螺线天生有方向、有距离、有终点,
 * 而且和「迷宫」这个意象直接对上。
 *
 * @param progress 0..1;null 表示不确定进度(此时只画一条极淡的底螺线,不动)
 * @param indeterminate 没有明确进度时,光点在外圈缓慢巡游
 */
@Composable
fun MazeRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    track: Color = MaeveTheme.colors.hair,
    head: Color = MaeveTheme.colors.machine,
    strokeWidth: Dp = 1.4.dp,
    indeterminate: Boolean = false,
) {
    val reduce = MaeveTheme.reduceMotion
    val spin = if (indeterminate && !reduce) {
        val t = rememberInfiniteTransition(label = "maze")
        val v by t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "maze-t",
        )
        v
    } else {
        0f
    }
    Canvas(modifier.size(size)) {
        drawMaze(progress ?: if (indeterminate) spin else null, track, head, strokeWidth.toPx())
    }
}

/** 画一次迷宫环。共用给进度条、卡片角标和启动图标。 */
internal fun DrawScope.drawMaze(progress: Float?, track: Color, head: Color, stroke: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = size.minDimension / 2f - stroke
    val path = mazePath(cx, cy, outer)
    // 底螺线:极淡,给光点一条看得见的路
    drawPath(path, track, style = Stroke(width = stroke, cap = StrokeCap.Round))
    if (progress == null) return
    // 走过的那一段:同一条路径按比例截取,所以走的是同一条路,不会跑偏
    val pm = PathMeasure().apply { setPath(path, false) }
    val len = pm.length * progress.coerceIn(0f, 1f)
    if (len <= 0.5f) return
    val walked = Path()
    pm.getSegment(0f, len, walked, true)
    drawPath(walked, head, style = Stroke(width = stroke, cap = StrokeCap.Round))
    // 光点:当前所在的位置
    val pos = pm.getPosition(len)
    drawCircle(head, radius = stroke * 1.55f, center = pos)
}

/**
 * 从外到内的阿基米德螺线路径。
 *
 * **圈数随半径走**:78dp 的标志盘 2.25 圈,好看且有「迷宫」的密度;13dp 的进度环若也盘
 * 2.25 圈,线和线会糊成一个黑点。所以按外半径折算——小尺寸自动退化成 1.2 圈的简笔螺线,
 * 远看仍然是一个「有方向的圈」。
 */
private fun mazePath(cx: Float, cy: Float, outer: Float): Path {
    val path = Path()
    val turns = (outer / 17f).coerceIn(1.2f, 2.25f)
    val steps = 120
    val inner = outer * 0.16f
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val angle = (-90f + t * 360f * turns) * (Math.PI.toFloat() / 180f)
        val r = outer - (outer - inner) * t
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

// ============================================================
// 二、钢琴卷
// ============================================================

/** 钢琴卷里一根音符条的状态。 */
enum class NoteState {
    /** 已经弹过。 */
    Done,
    /** 正在弹这一根。 */
    Active,
    /** 还没到。 */
    Pending,
    /** 弹过,但是出错了。 */
    Failed,
}

/**
 * 钢琴卷:把「已经跑到第几步」画成一条卷带。
 *
 * 每一根音符条的**高度**是它自己的编号推出来的伪随机值——固定不变,
 * 所以同一轮对话里来回滚动时卷带不会跳。看起来像谱子,读起来是进度。
 *
 * @param states 从第一步到当前;超过可视宽度的由调用方自己截断
 */
@Composable
fun PianoRoll(
    states: List<NoteState>,
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
    note: Dp = MaeveSize.Note,
    gap: Dp = MaeveSize.NoteGap,
    done: Color = MaeveTheme.colors.machineDim,
    active: Color = MaeveTheme.colors.signal,
    pending: Color = MaeveTheme.colors.hair,
    failed: Color = MaeveTheme.colors.bad,
) {
    val reduce = MaeveTheme.reduceMotion
    val pulse = if (states.contains(NoteState.Active) && !reduce) {
        val t = rememberInfiniteTransition(label = "roll")
        val v by t.animateFloat(
            initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(760, easing = LinearEasing), RepeatMode.Reverse),
            label = "roll-t",
        )
        v
    } else {
        1f
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (states.isEmpty()) return@Canvas
        // 按可用宽度均分:步数少时每根条不超过 [note],步数多时自动变细,永远铺满一行
        val n = states.size
        val cell = size.width / n
        val bar = (cell - gap.toPx()).coerceIn(1.2f, note.toPx())
        drawPianoRoll(states, bar, cell, height.toPx(), done, active, pending, failed, pulse)
    }
}

internal fun DrawScope.drawPianoRoll(
    states: List<NoteState>,
    note: Float,
    /** 每根条的步距(条宽 + 间隙)。 */
    cell: Float,
    h: Float,
    done: Color,
    active: Color,
    pending: Color,
    failed: Color,
    pulse: Float,
) {
    states.forEachIndexed { i, s ->
        val frac = noteFraction(i)
        val barH = (h * (0.26f + 0.74f * frac)).coerceAtMost(h)
        val x = i * cell
        val y = h - barH
        val color = when (s) {
            NoteState.Done -> done
            NoteState.Active -> active.copy(alpha = pulse)
            NoteState.Pending -> pending
            NoteState.Failed -> failed
        }
        drawRect(color, topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(note, barH))
    }
}

/** 第 [i] 根音符条的高度系数:确定性伪随机,不随重组变化。 */
private fun noteFraction(i: Int): Float {
    val x = sin(i * 12.9898f) * 43758.5453f
    return abs(x - kotlin.math.floor(x)).coerceIn(0.12f, 1f)
}

// ============================================================
// 三、台词轨 / 校准角
// ============================================================

/**
 * 校准角:诊断框四角的细刻度。两条短线成直角,像仪表上的对位标记。
 * 用在「这块是机器读数」的地方——运行过程、用量、自检。
 */
fun Modifier.calibrationCorners(
    color: Color,
    arm: Dp = 5.dp,
    inset: Dp = 0.dp,
    strokeWidth: Dp = 1.dp,
): Modifier = drawBehind {
    val a = arm.toPx()
    val i = inset.toPx()
    val w = strokeWidth.toPx()
    val cw = size.width
    val ch = size.height
    drawLine(color, Offset(i, i + a), Offset(i, i), strokeWidth = w)
    drawLine(color, Offset(i, i), Offset(i + a, i), strokeWidth = w)
    drawLine(color, Offset(cw - i - a, i), Offset(cw - i, i), strokeWidth = w)
    drawLine(color, Offset(cw - i, i), Offset(cw - i, i + a), strokeWidth = w)
    drawLine(color, Offset(i, ch - i - a), Offset(i, ch - i), strokeWidth = w)
    drawLine(color, Offset(i, ch - i), Offset(i + a, ch - i), strokeWidth = w)
    drawLine(color, Offset(cw - i - a, ch - i), Offset(cw - i, ch - i), strokeWidth = w)
    drawLine(color, Offset(cw - i, ch - i), Offset(cw - i, ch - i - a), strokeWidth = w)
}

/**
 * 细线网格:极淡的一层方格。只在「叶子面板」上用(自检、用量、运行过程),
 * 给大片空白一点仪表的质地,但不至于变成草稿纸。
 */
fun Modifier.hairlineLattice(color: Color, step: Dp = 24.dp): Modifier = drawBehind {
    val s = step.toPx()
    var x = s
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += s
    }
    var y = s
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += s
    }
}

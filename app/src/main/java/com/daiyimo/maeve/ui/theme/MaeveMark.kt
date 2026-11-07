package com.daiyimo.maeve.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Maeve 的标志：**日食**。
 *
 * ```
 *        ╭───╮
 *      ╱   ●   ╲        ← 中间是空的：一颗暗星盘挡住了后面的光
 *      ╲       ╱        ← 只在边缘漏出一圈被侵蚀的光
 *        ╰───╯           右上最厚，左下几乎消失
 * ```
 *
 * ## 出处
 *
 * 《西部世界》第三季，罗波安（Rehoboam）的视觉设定：
 * **「日全食影像的黑白反向图像，由微粒组成。」**
 *
 * 所以这个标志不是「一个圆环」——**圆环是容器，日食是事件**。
 * 前者无论怎么加粗、断开、渐变，都只是一个变了形的圆；
 * 后者本身讲述一件事：**有什么东西挡住了光**。
 *
 * ## 三层物理关系
 *
 * 这三层不是三个图层，而是一条因果链：
 *
 * | 层 | 是什么 | 为什么 |
 * |---|---|---|
 * | **暗盘** | 中心不发光的部分 | 被挡住的光源。它是「她看到的东西」 |
 * | **日冕** | 贴着暗盘的一圈光，厚度随角度变化 | 光绕过遮挡物的衍射。厚度差来自观测角度，不是装饰 |
 * | **微粒** | 日冕外缘逸散的砂点 | 设定原文就是「由微粒组成」。它是密度，不是描边 |
 *
 * 因为厚度差有物理理由，所以它在 16dp 时仍然成立——
 * 你看到的是「一边厚一边薄的一圈」，而不是一个普通的圆。
 *
 * ## 三态
 *
 * | 状态 | 日冕 | 微粒 | 用在哪 |
 * |---|---|---|---|
 * | [MarkState.Idle] | 静止 | 静止 | 桌面图标、关于页 |
 * | [MarkState.Armed] | 缓慢呼吸 | 缓慢外逸 | 空状态（她在等你） |
 * | [MarkState.Playing] | 最厚处沿环流动 | 从流动位置喷出 | 正在跑任务 |
 */
enum class MarkState { Idle, Armed, Playing }

/**
 * 几何常量。Compose、启动图、胶囊全部从这里取，标志不会各长各的。
 *
 * 数值与 `res/drawable/maeve_launcher_foreground.xml` 一一对应
 * （那边视口 108、暗盘 r=21.5，这边是同样的比例）。
 */
object MaeveMarkGeo {
    /**
     * 暗盘半径占**可用半径**的比例。
     * 剩下的空间留给日冕最厚处 + 微粒逸散。
     */
    const val DARK = 0.735f

    /** 日冕贴盘处的厚度（相对暗盘半径）。 */
    const val CORONA_INNER = 0.0155f

    /** 日冕最厚处的额外厚度（相对暗盘半径）。最厚 / 最薄 = 约 3.2 : 1。 */
    const val CORONA_SPAN = 0.168f

    /** 日冕最薄处占最厚处的比例。小于它，薄的那一侧就断了。 */
    const val CORONA_FLOOR = 0.26f

    /**
     * 最厚处朝向（度）。
     *
     * ★ 注意符号：Compose 的 Canvas 是 **y 轴向下**，角度 0° 指向 3 点钟、顺时针增加。
     * 所以「右上」是 **-32°**（等价 328°），写成 +32° 会跑到右下。
     * 这个值是整个标志唯一的方向参数——启动图 XML 里的 `PEAK` 必须与这里一致。
     */
    const val PEAK_ANGLE = -32f

    /** 厚度包络的陡峭度。越大，厚的那一段越集中。 */
    const val PEAK_POWER = 1.55f

    /** 微粒数量（相对 100dp）。小尺寸按面积缩。 */
    const val GRAINS = 300

    /** 微粒随机种子。固定值 → 每次启动的砂粒位置一致，不会闪。 */
    const val GRAIN_SEED = 17
}

/**
 * 标志本体。
 *
 * @param size 边长
 * @param coronaColor 日冕与微粒的颜色
 * @param darkColor 暗盘的颜色。**必须与所在背景一致**，否则暗盘会变成一个可见的实心圆
 * @param state 静止 / 等你说 / 正在跑
 */
@Composable
fun MaeveMark(
    size: Dp,
    modifier: Modifier = Modifier,
    coronaColor: Color = MaeveTheme.colors.ink,
    darkColor: Color = Color.Transparent,
    state: MarkState = MarkState.Idle,
    /** 兼容旧调用点：等价于 [MarkState.Armed]。 */
    breathing: Boolean = false,
) {
    val reduce = MaeveTheme.reduceMotion
    val effective = if (breathing && state == MarkState.Idle) MarkState.Armed else state
    val phase = if (effective != MarkState.Idle && !reduce) {
        val t = rememberInfiniteTransition(label = "mark")
        val v by t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(
                    if (effective == MarkState.Playing) 2400 else MaeveMotion.BreathMs,
                    easing = LinearEasing,
                ),
                RepeatMode.Restart,
            ),
            label = "mark-t",
        )
        v
    } else {
        0f
    }
    Canvas(modifier.size(size)) {
        drawMark(
            corona = coronaColor,
            dark = darkColor,
            state = effective,
            phase = phase,
            uid = size.value,
        )
    }
}

/** 画一次标志。启动图预览、胶囊、验收页共用。 */
internal fun DrawScope.drawMark(
    corona: Color,
    dark: Color,
    state: MarkState,
    phase: Float,
    uid: Float = 100f,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    // 外圈最厚处 = DARK * (1 + CORONA_INNER + CORONA_SPAN)，留 2% 余量给抗锯齿
    val half = size.minDimension / 2f * 0.985f
    val rDark = half * MaeveMarkGeo.DARK

    // Playing：最厚处绕环一圈
    val peak = if (state == MarkState.Playing) {
        MaeveMarkGeo.PEAK_ANGLE + phase * 360f
    } else {
        MaeveMarkGeo.PEAK_ANGLE
    }
    // Armed：整体厚度随呼吸轻微起伏（不改变形状，只改变"亮起来"的程度）
    val breathe = if (state == MarkState.Armed) {
        0.86f + 0.14f * (0.5f - 0.5f * cos(phase * 2f * PI).toFloat())
    } else {
        1f
    }

    drawCorona(cx, cy, rDark, corona, peak, breathe)
    drawGrains(cx, cy, rDark, corona, peak, state, phase, uid)

    // 暗盘：用背景色盖住内侧。transparent 时跳过（视为背景已经透明）
    if (dark.alpha > 0f) {
        drawCircle(dark, radius = rDark * (1f + MaeveMarkGeo.CORONA_INNER * 0.9f), center = Offset(cx, cy))
    }
}

/**
 * 日冕：一圈厚度随角度变化的实心环。
 * 内沿贴着暗盘（正圆），外沿按 [thicknessAt] 起伏 —— 这就是"厚度不均"的全部来源。
 */
private fun DrawScope.drawCorona(
    cx: Float,
    cy: Float,
    rDark: Float,
    color: Color,
    peak: Float,
    breathe: Float,
) {
    val inner = rDark * (1f + MaeveMarkGeo.CORONA_INNER * 0.9f)
    val steps = 180
    val outer = Path()
    for (i in 0..steps) {
        val deg = 360f * i / steps
        val rad = Math.toRadians(deg.toDouble())
        val t = thicknessAt(deg, peak) * breathe
        val r = rDark * (1f + MaeveMarkGeo.CORONA_INNER + MaeveMarkGeo.CORONA_SPAN * t)
        val x = cx + (r * cos(rad)).toFloat()
        val y = cy + (r * sin(rad)).toFloat()
        if (i == 0) outer.moveTo(x, y) else outer.lineTo(x, y)
    }
    outer.close()
    // 内沿反向回来做成环带；用 evenOdd 让内侧被挖空，不依赖背景色
    for (i in steps downTo 0) {
        val deg = 360f * i / steps
        val rad = Math.toRadians(deg.toDouble())
        val x = cx + (inner * cos(rad)).toFloat()
        val y = cy + (inner * sin(rad)).toFloat()
        outer.lineTo(x, y)
    }
    outer.close()
    // 外沿顺时针 + 内沿逆时针 ⇒ 绕数相反，默认 NonZero 下内圈自然被挖空，
    // 不依赖背景色。所以这里不需要传 PathFillType.EvenOdd。
    drawPath(outer, color)
}

/** 微粒：只落在日冕外缘，密度随角度（厚的一侧更多）。 */
private fun DrawScope.drawGrains(
    cx: Float,
    cy: Float,
    rDark: Float,
    color: Color,
    peak: Float,
    state: MarkState,
    phase: Float,
    uid: Float,
) {
    // 砂粒数量按面积缩：16dp 时几乎不需要，100dp 时全量
    val density = (uid / 100f).coerceIn(0.34f, 1.15f)
    val count = (MaeveMarkGeo.GRAINS * density).toInt()
    if (count < 12) return
    val dot = maxOf(0.7f, rDark * 0.0115f)
    var i = 0
    while (i < count) {
        val a = rnd(i * 3, 0) * 360f
        val gate = rnd(i * 3, 1)
        val t = thicknessAt(a, peak)
        // 稀的一侧几乎不长砂粒 —— 这是不对称的第二层表达
        if (gate > 0.12f + 0.88f * t) { i++; continue }
        var len = rnd(i * 3, 2).pow(2.2f)
        // Playing：从最厚处向外喷
        if (state == MarkState.Playing) {
            val ang = ((a - peak) % 360f + 540f) % 360f - 180f
            val near = 1f - (kotlin.math.abs(ang) / 70f).coerceIn(0f, 1f)
            len = (len * (1f + 0.9f * near * phase)).coerceIn(0f, 1.4f)
        }
        val rad = Math.toRadians(a.toDouble())
        val rr = rDark * (1f + MaeveMarkGeo.CORONA_INNER + MaeveMarkGeo.CORONA_SPAN * t) +
            rDark * MaeveMarkGeo.CORONA_SPAN * 0.55f * t * len
        val x = cx + (rr * cos(rad)).toFloat()
        val y = cy + (rr * sin(rad)).toFloat()
        // 越靠外越淡，越靠厚侧越亮
        val alpha = ((1f - len * 0.62f).coerceIn(0.18f, 1f)) * (0.42f + 0.58f * t)
        drawCircle(
            color.copy(alpha = color.alpha * alpha * 0.92f),
            radius = dot * (0.55f + 0.85f * rnd(i * 3, 3)),
            center = Offset(x, y),
        )
        i++
    }
}

/**
 * 厚度包络：与角度有关的一个 0..1 值。
 * peak 处 = 1（最厚），对侧 = 0（最薄，但仍保留 [MaeveMarkGeo.CORONA_FLOOR]）。
 *
 * 这是整个标志唯一的"设计参数"——所有不对称都从这里出来。
 */
private fun thicknessAt(deg: Float, peak: Float): Float {
    val d = Math.toRadians((deg - peak).toDouble())
    val k = (0.5 + 0.5 * cos(d)).toFloat().pow(MaeveMarkGeo.PEAK_POWER)
    return MaeveMarkGeo.CORONA_FLOOR + (1f - MaeveMarkGeo.CORONA_FLOOR) * k
}

/**
 * 确定性伪随机。同一个 (i, salt) 永远得到同一个值，
 * 所以砂粒位置在重组之间不会跳动，也不需要存数组。
 */
private fun rnd(i: Int, salt: Int): Float {
    val v = sin((i * 12.9898 + salt * 78.233 + MaeveMarkGeo.GRAIN_SEED) * 1.0) * 43758.5453
    return (v - kotlin.math.floor(v)).toFloat()
}

/**
 * 标志印章：实色底 + 标志。启动图、关于页、欢迎页用。
 * 底跟着当前配色走，换配色时印章也换色调——形状和几何永远不变。
 */
@Composable
fun MaeveMarkTile(
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = size * 0.24f,
    breathing: Boolean = false,
    /** 反白：深底浅光，用在强调色底上。 */
    inverted: Boolean = true,
) {
    val c = MaeveTheme.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
    val bg = if (inverted) c.accent else c.surfaceHigh
    Box(
        modifier.size(size).clip(shape).background(bg).border(0.5.dp, c.accentPressed.copy(alpha = 0.4f), shape),
        contentAlignment = Alignment.Center,
    ) {
        MaeveMark(
            size = size,
            coronaColor = if (inverted) c.onAccent else c.ink,
            darkColor = bg,
            state = if (breathing) MarkState.Armed else MarkState.Idle,
        )
    }
}

/** 只有标志本身、没有底的版本。 */
@Composable
fun MaeveMarkGlyph(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = MaeveTheme.colors.ink,
    breathing: Boolean = false,
) {
    MaeveMark(size, modifier, coronaColor = color, darkColor = Color.Transparent, breathing = breathing)
}

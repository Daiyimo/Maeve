package com.daiyimo.maeve.core.cap.phone

import kotlin.math.hypot
import kotlin.math.roundToLong

/** 手势时间轴的纯计算部分,和节奏参数分开,便于测试与限制文件大小。 */
internal object GestureTimeline {
    fun progress(u: Double, endSpeed: Double): Double {
        val u2 = u * u
        val u3 = u2 * u
        return (-2 * u3 + 3 * u2) + endSpeed * (u3 - u2)
    }

    fun lengthOf(path: List<Pair<Double, Double>>): Double = cumulative(path).last()

    fun cumulative(path: List<Pair<Double, Double>>): DoubleArray {
        val out = DoubleArray(path.size)
        for (i in 1 until path.size) out[i] = out[i - 1] + hypot(path[i].first - path[i - 1].first, path[i].second - path[i - 1].second)
        return out
    }

    /** 沿折线走过 [s] 时的位置。 */
    fun at(path: List<Pair<Double, Double>>, cum: DoubleArray, s: Double): Pair<Double, Double> {
        var i = 1
        while (i < path.size - 1 && cum[i] < s) i++
        val seg = cum[i] - cum[i - 1]
        val f = if (seg <= 0) 1.0 else ((s - cum[i - 1]) / seg).coerceIn(0.0, 1.0)
        val (x0, y0) = path[i - 1]
        val (x1, y1) = path[i]
        return x0 + (x1 - x0) * f to y0 + (y1 - y0) * f
    }

    /** 在移动前后插入原地按住/停稳点,让长按 pickup 与 drop 保持同一笔原生手势。 */
    fun withHoldAndRest(points: List<GesturePacing.Point>, hold: Long, rest: Long, total: Long): List<GesturePacing.Point> {
        if (hold == 0L && rest == 0L) return points
        val start = points.first()
        val end = points.last()
        return buildList {
            add(GesturePacing.Point(start.x, start.y, 0))
            if (hold > 0) add(GesturePacing.Point(start.x, start.y, hold))
            points.drop(1).forEach { add(it.copy(t = it.t + hold)) }
            if (rest > 0) add(GesturePacing.Point(end.x, end.y, total))
        }
    }

    /** 不加节奏时的一笔:就是给定的点,时间按路程匀速分配(和系统按一条路径匀速走的一样)。 */
    fun uniform(path: List<Pair<Double, Double>>, total: Long): List<GesturePacing.Point> {
        val cum = cumulative(path)
        val length = cum.last()
        return path.mapIndexed { i, (x, y) ->
            val t = when {
                i == path.lastIndex -> total
                length <= 0 -> 0L
                else -> (total * cum[i] / length).roundToLong()
            }
            GesturePacing.Point(x, y, t)
        }
    }
}

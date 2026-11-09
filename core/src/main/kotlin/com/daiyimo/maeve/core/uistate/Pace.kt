package com.daiyimo.maeve.core.uistate

import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/**
 * 网页鼠标([com.daiyimo.maeve.core.cap.web.cdp.InputPacing])和手机手势([com.daiyimo.maeve.core.cap.phone.GesturePacing])
 * 都要用的节奏原件:区间里有界地随机取值、略带弧度的二次贝塞尔、两头慢中间快的缓动。
 * 各处时间、距离都由调用方给,这里只负责按给定的区间和幅度取值。
 */
object Pace {
    /** 在 [r] 里随机取一个数(含两端);区间退化成一个值时就是它。 */
    fun pick(random: Random, r: LongRange): Long = if (r.first >= r.last) r.first else random.nextLong(r.first, r.last + 1)

    /** 在 [r] 里随机取一个整数(含两端)。 */
    fun pick(random: Random, r: IntRange): Int = if (r.first >= r.last) r.first else random.nextInt(r.first, r.last + 1)

    /** 弧线偏离直线的幅度:不超过距离的 [fraction],也不超过 [maxPx];偏哪一侧随机(带符号)。 */
    fun bow(random: Random, dist: Double, fraction: Double, maxPx: Double): Double {
        val amplitude = min(dist * fraction, maxPx)
        return amplitude * random.nextDouble(-1.0, 1.0)
    }

    /** 二次贝塞尔的控制点:[from]、[to] 连线的中点沿法线偏 [bow];两点重合时就是中点。 */
    fun control(from: Pair<Double, Double>, to: Pair<Double, Double>, bow: Double): Pair<Double, Double> {
        val (x0, y0) = from
        val (x1, y1) = to
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = hypot(dx, dy)
        val mx = (x0 + x1) / 2
        val my = (y0 + y1) / 2
        if (dist == 0.0) return mx to my
        return mx - dy / dist * bow to my + dx / dist * bow
    }

    /** 二次贝塞尔在 [t](0..1)处的点。 */
    fun bezier(from: Pair<Double, Double>, control: Pair<Double, Double>, to: Pair<Double, Double>, t: Double): Pair<Double, Double> {
        val u = 1 - t
        val w0 = u * u
        val w1 = 2 * u * t
        val w2 = t * t
        return (w0 * from.first + w1 * control.first + w2 * to.first) to
            (w0 * from.second + w1 * control.second + w2 * to.second)
    }

    /** 两头慢中间快(smoothstep 缓动):0→0,1→1,两端速度为 0。 */
    fun ease(t: Double) = t * t * (3 - 2 * t)
}

package com.daiyimo.maeve.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/**
 * 动效分档。数值来自 docs/spec/visual-tokens.md §3,是对 dsh-Go 设计决策的复用,
 * 不含任何 AGPL-3.0 源码。reduceMotion 为真时所有时长归零(直接 snap)。
 */
object MaeveMotion {
    const val PressMs = 90
    const val QuickMs = 160
    const val StandardMs = 240
    const val CollapseMs = 280
    const val EmphasizedMs = 340
    const val SheetInMs = 380
    const val GlassInMs = 120
    const val BreathMs = 2400

    /** 页面切换、开关的时长。 */
    const val PAGE_MS = 220
    const val SWITCH_MS = 200

    /** 危险确认的长按时长。 */
    const val HoldToConfirmMs = 1500

    /** 按下时的缩放,不是整块变暗。 */
    const val PressScale = 0.97f

    /** dsh-Go 的 glide 曲线:抽屉、胶囊位移这类"跟手后落位"的运动。 */
    val Glide = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** 减速曲线:起步快、落地慢。页面推入、开关。 */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    fun <T> spec(ms: Int, reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) snap() else tween(ms, easing = Glide)

    fun <T> page(reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) snap() else tween(PAGE_MS, easing = Decelerate)

    fun <T> switch(reduceMotion: Boolean): AnimationSpec<T> =
        if (reduceMotion) snap() else tween(SWITCH_MS, easing = Decelerate)
}

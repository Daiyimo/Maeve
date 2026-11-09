package com.daiyimo.maeve.core.visual

/**
 * 动作前后两帧画面的粗比较参数。
 *
 * 整屏切成 [GRID_COLS] × [GRID_ROWS] 个格子;一格亮度差超过 [CELL_DELTA] 就算这一格变了。
 * [FRAME_VISIBLE] 是整屏至少多少格变了才算看得出的变化,[REGION_VISIBLE] 是目标区域里的同一门槛,
 * [FRAME_LARGE] 是整屏大面积变化的门槛。比例都用 0–1。
 */
object FrameTuning {
    const val GRID_COLS = 24
    const val GRID_ROWS = 48
    const val CELL_DELTA = 16
    const val FRAME_VISIBLE = 0.01
    const val REGION_VISIBLE = 0.15
    const val FRAME_LARGE = 0.25
}

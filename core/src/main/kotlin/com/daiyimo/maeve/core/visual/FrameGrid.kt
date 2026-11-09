package com.daiyimo.maeve.core.visual

import kotlin.math.abs

/**
 * 一帧画面缩成的亮度格子([cols]×[rows],行优先):动作前后各取一份,作判效果的旁证(主要看界面状态)。
 * 只用像素读取函数取样,不依赖安卓类(可在 JVM 上测);端口拿手里现成的位图取样,不编码图片。
 */
class FrameGrid(val cols: Int, val rows: Int, val luma: IntArray) {
    init {
        require(luma.size == cols * rows) { "luma=${luma.size} does not fit ${cols}x$rows" }
    }

    companion object {
        private const val EDGE_SAMPLES = 2
        private const val SAMPLES_PER_CELL = EDGE_SAMPLES * EDGE_SAMPLES

        /** 把 [width]×[height] 的画面切成格子,每格取 2×2 个点的平均亮度;[pixel] 返回 (x, y) 处的 ARGB。太小的画面为 null。 */
        fun sample(width: Int, height: Int, pixel: (Int, Int) -> Int): FrameGrid? {
            val cellWidth = width / FrameTuning.GRID_COLS
            val cellHeight = height / FrameTuning.GRID_ROWS
            if (cellWidth == 0 || cellHeight == 0) return null

            val cells = IntArray(FrameTuning.GRID_COLS * FrameTuning.GRID_ROWS)
            for (row in 0 until FrameTuning.GRID_ROWS) {
                for (col in 0 until FrameTuning.GRID_COLS) {
                    var total = 0
                    for (dy in 0 until EDGE_SAMPLES) {
                        for (dx in 0 until EDGE_SAMPLES) {
                            val x = col * cellWidth + (2 * dx + 1) * cellWidth / (2 * EDGE_SAMPLES)
                            val y = row * cellHeight + (2 * dy + 1) * cellHeight / (2 * EDGE_SAMPLES)
                            total += brightness(pixel(x, y))
                        }
                    }
                    cells[row * FrameTuning.GRID_COLS + col] = total / SAMPLES_PER_CELL
                }
            }
            return FrameGrid(FrameTuning.GRID_COLS, FrameTuning.GRID_ROWS, cells)
        }

        private fun brightness(argb: Int): Int {
            val red = argb shr 16 and 0xff
            val green = argb shr 8 and 0xff
            val blue = argb and 0xff
            return (red * 3 + green * 6 + blue) / 10
        }
    }
}

/** 两帧之间:整屏变了的格子占比 [whole],目标区域里的占比 [region](没给区域为 null)。 */
data class FrameChange(val whole: Double, val region: Double?) {
    /** 有几格以上明显变了(光标闪烁、一个点的涟漪不算)。 */
    val visible: Boolean get() = whole >= FrameTuning.FRAME_VISIBLE || (region ?: 0.0) >= FrameTuning.REGION_VISIBLE
    /** 整屏大面积变了。 */
    val large: Boolean get() = whole >= FrameTuning.FRAME_LARGE
}

/** 动作前后两帧的粗比较:每格亮度差超过 [FrameTuning.CELL_DELTA] 算这格变了。 */
object FrameDiff {
    /** [roi] 是目标区域(0–999 归一化的 [l,t,r,b]);两帧尺寸不一致、缺一帧为 null。 */
    fun compare(before: FrameGrid?, after: FrameGrid?, roi: List<Int>? = null): FrameChange? {
        if (before == null || after == null) return null
        if (before.cols != after.cols || before.rows != after.rows) return null

        val cols = before.cols
        val rows = before.rows
        val box = regionOf(roi, cols, rows)
        var changed = 0
        var inside = 0
        var insideChanged = 0

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val index = row * cols + col
                val differs = abs(before.luma[index] - after.luma[index]) > FrameTuning.CELL_DELTA
                if (differs) changed++
                if (box != null && col in box.left..box.right && row in box.top..box.bottom) {
                    inside++
                    if (differs) insideChanged++
                }
            }
        }

        val whole = changed.toDouble() / (cols * rows)
        val region = box?.let { insideChanged.toDouble() / inside.coerceAtLeast(1) }
        return FrameChange(whole, region)
    }

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun regionOf(roi: List<Int>?, cols: Int, rows: Int): Box? {
        if (roi == null || roi.size != 4) return null
        // 向外扩半格,免得小控件落在格子缝里
        val left = (roi[0] * cols / 1000.0 - 0.5).toInt().coerceIn(0, cols - 1)
        val top = (roi[1] * rows / 1000.0 - 0.5).toInt().coerceIn(0, rows - 1)
        val right = (roi[2] * cols / 1000.0 + 0.5).toInt().coerceIn(left, cols - 1)
        val bottom = (roi[3] * rows / 1000.0 + 0.5).toInt().coerceIn(top, rows - 1)
        return Box(left, top, right, bottom)
    }
}

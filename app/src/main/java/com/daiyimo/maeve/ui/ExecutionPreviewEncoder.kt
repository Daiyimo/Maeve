package com.daiyimo.maeve.ui

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

internal fun encodeExecutionPreview(frame: Bitmap?): ByteArray? = runCatching {
    if (frame == null || frame.isRecycled) return null
    // 实时执行画面要展示原图:不再缩到 480px 宽(那是缩略图档),只在极高分辨率屏上
    // 把长边收到 1440 以内控制传输量,其余按原分辨率、高画质 JPEG 输出。
    val maxEdge = 1440f
    val scale = minOf(1f, maxEdge / frame.width, maxEdge / frame.height)
    val image = if (scale < 1f) Bitmap.createScaledBitmap(frame,
        (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true) else frame
    try { ByteArrayOutputStream().use { stream ->
        if (image.compress(Bitmap.CompressFormat.JPEG, 90, stream)) stream.toByteArray() else null
    } } finally { if (image !== frame) image.recycle() }
}.getOrNull()

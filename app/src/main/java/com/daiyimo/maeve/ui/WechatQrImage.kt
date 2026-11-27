package com.daiyimo.maeve.ui

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream

internal fun wechatQrPng(content: String): ByteArray {
    val matrix = encodeQr(content)
    val scale = maxOf(1, 320 / matrix.size)
    val size = matrix.size * scale
    val pixels = IntArray(size * size) { index ->
        if (matrix[index % size / scale, index / size / scale]) Color.BLACK else Color.WHITE
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    return try {
        ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
    } finally { bitmap.recycle() }
}

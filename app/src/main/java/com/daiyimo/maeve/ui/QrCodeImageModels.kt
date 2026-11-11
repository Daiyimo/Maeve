package com.daiyimo.maeve.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal data class QrMatrix(val size: Int, private val cells: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = cells[y * size + x]
}


internal fun encodeQr(content: String): QrMatrix {
    require(content.isNotBlank()) { "QR content is empty" }
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, 1, 1,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4,
        ),
    )
    require(matrix.width == matrix.height) { "QR matrix is not square" }
    return QrMatrix(matrix.width, BooleanArray(matrix.width * matrix.height) { i ->
        matrix.get(i % matrix.width, i / matrix.width)
    })
}

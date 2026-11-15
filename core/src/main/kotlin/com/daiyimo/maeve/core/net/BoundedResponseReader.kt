package com.daiyimo.maeve.core.net

import java.io.IOException
import okio.BufferedSource

/** 在积累不可信响应前检查字节数，支持 EOF 前未换行的最后一行。 */
internal class BoundedResponseReader(
    private val source: BufferedSource,
    private val maxLine: Long = 256 * 1024,
    private val maxTotal: Long = 8 * 1024 * 1024,
) {
    private var total = 0L
    fun line(): String? {
        val newline = source.indexOf('\n'.code.toByte(), 0, maxLine + 1)
        val size = if (newline >= 0) newline else source.buffer.size
        if (size > maxLine) throw IOException("response_line_limit")
        if (newline < 0 && size == 0L) return null
        total += size + if (newline >= 0) 1 else 0
        if (total > maxTotal) throw IOException("response_total_limit")
        val line = source.readUtf8(size)
        if (newline >= 0) source.readByte()
        return line.removeSuffix("\r")
    }
}

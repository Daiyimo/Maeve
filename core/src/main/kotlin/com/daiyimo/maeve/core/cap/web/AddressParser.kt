package com.daiyimo.maeve.core.cap.web

import java.net.InetAddress

/** 只解析字面地址,绝不调用 DNS;失败返回 null。 */
internal object AddressParser {
    fun parse(host: String): InetAddress? {
        val h = host.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
        val bytes = if (':' in h) parseIpv6(h) else parseIpv4(h)
        return bytes?.let(InetAddress::getByAddress)
    }

    /** inet_aton 风格的 IPv4:一段整数、两/三段短写,以及十进制、前导零八进制、0x 十六进制。 */
    fun parseIpv4(host: String): ByteArray? {
        val h = host.trim().trimEnd('.')
        if (h.isEmpty()) return null
        val parts = h.split('.')
        if (parts.size !in 1..4 || parts.any { it.isEmpty() }) return null
        val values = parts.map { part ->
            val value = when {
                part.startsWith("0x", true) -> part.drop(2).takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }?.toLongOrNull(16)
                part.length > 1 && part.startsWith('0') -> part.drop(1).takeIf { it.isNotEmpty() && it.all { it in '0'..'7' } }?.toLongOrNull(8)
                else -> part.takeIf { it.all { it.isDigit() } }?.toLongOrNull(10)
            }
            value?.takeIf { it in 0L..0xffffffffL } ?: return null
        }
        var packed = 0L
        for ((index, value) in values.withIndex()) {
            val width = if (index == values.lastIndex) 8 * (5 - values.size) else 8
            packed = (packed shl width) or value
            if (packed !in 0L..0xffffffffL) return null
        }
        return ByteArray(4) { i -> ((packed ushr (24 - i * 8)) and 0xff).toByte() }
    }

    private fun parseIpv6(host: String): ByteArray? {
        var text = host
        val last = text.substringAfterLast(':')
        if ('.' in last) {
            val v4 = parseIpv4(last) ?: return null
            val prefix = text.dropLast(last.length)
            text = prefix + u16(v4[0], v4[1]) + ":" + u16(v4[2], v4[3])
        }
        val compressed = "::" in text
        if (compressed && text.indexOf("::") != text.lastIndexOf("::")) return null
        val sides = if (compressed) text.split("::", limit = 2) else listOf(text, "")
        val left = sides[0].takeIf { it.isNotEmpty() }?.split(':')?.map(::parseGroup) ?: emptyList()
        val right = sides[1].takeIf { it.isNotEmpty() }?.split(':')?.map(::parseGroup) ?: emptyList()
        if (left.any { it == null } || right.any { it == null }) return null
        val l = left.filterNotNull(); val r = right.filterNotNull()
        val present = when {
            compressed -> {
                if (l.size + r.size >= 8) return null
                l + List(8 - l.size - r.size) { 0 } + r
            }
            l.size + r.size == 8 -> l + r
            else -> return null
        }
        return ByteArray(16) { i -> ((present[i / 2] ushr (if (i % 2 == 0) 8 else 0)) and 0xff).toByte() }
    }

    private fun parseGroup(value: String): Int? =
        value.takeIf { it.length in 1..4 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }?.toIntOrNull(16)

    private fun u16(a: Byte, b: Byte): String = (((a.toInt() and 0xff) shl 8) or (b.toInt() and 0xff)).toString(16)
}

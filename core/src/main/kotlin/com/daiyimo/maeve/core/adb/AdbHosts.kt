package com.daiyimo.maeve.core.adb

import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * adb 只允许连本机:127/8、IPv6 回环,或本机某块网卡上的地址。
 *
 * 这里从不把域名交给系统解析。除了精确的 `localhost`(大小写不敏感,允许一个末尾点,
 * 固定映射到 127.0.0.1)之外只接受数字字面量;解析、核对和随后 Socket 使用是同一个
 * [InetAddress],不会出现先校验原字符串、再重新解析的间隙。
 */
object AdbHosts {
    private val IPV4_LOOPBACK = byteArrayOf(127, 0, 0, 1)

    private class LocalNic(
        val name: String,
        val index: Int,
        val addresses: List<InetAddress>,
    )

    /** 核对为本机地址时返回它,否则返回 null(域名、畸形字面量、别人的地址都算)。 */
    fun ownAddress(host: String): InetAddress? = ownAddress(host, localNics())

    /** 返回已经核对并解析好的连接地址;调用方不得再拿原 host 重新构造 [InetSocketAddress]。 */
    fun ownSocketAddress(host: String, port: Int): InetSocketAddress? =
        ownAddress(host)?.let { address -> InetSocketAddress(address, port) }

    private fun localNics(): List<LocalNic> = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching emptyList()
        interfaces.toList().map { nic ->
            LocalNic(nic.name, nic.index, nic.inetAddresses.toList())
        }
    }.getOrDefault(emptyList())

    private fun ownAddress(host: String, nics: List<LocalNic>): InetAddress? {
        val address = literalAddress(host, nics) ?: return null
        if (address.isLoopbackAddress) return address
        if (address.address.size == 4 && address.address[0] == 127.toByte()) return address
        return address.takeIf { candidate ->
            nics.any { nic -> nic.addresses.any { local -> sameAddress(candidate, local) } }
        }
    }

    /** 地址字节相同,且带 zone 的 IPv6 必须落在同一块网卡上。 */
    private fun sameAddress(target: InetAddress, local: InetAddress): Boolean {
        if (!target.address.contentEquals(local.address)) return false
        if (target !is Inet6Address || local !is Inet6Address) return true
        return target.scopeId == 0 || target.scopeId == local.scopeId
    }

    private fun literalAddress(raw: String, nics: List<LocalNic>): InetAddress? {
        val trimmed = raw.trim()
        if (trimmed.equals("localhost", ignoreCase = true) || trimmed.equals("localhost.", ignoreCase = true)) {
            return InetAddress.getByAddress(null, IPV4_LOOPBACK)
        }
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val text: String
        if (trimmed.startsWith('[') || trimmed.endsWith(']')) {
            if (!trimmed.startsWith('[') || !trimmed.endsWith(']') || trimmed.length < 3) return null
            text = trimmed.substring(1, trimmed.length - 1)
            // 方括号只用于 IPv6 字面量,不能拿来包任意字符串。
            if (':' !in text || '[' in text || ']' in text) return null
        } else {
            text = trimmed
        }
        return if (':' in text) ipv6Address(text, nics) else ipv4Bytes(text)?.let { InetAddress.getByAddress(null, it) }
    }

    private val IPV4_PATTERN = Regex("([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})")

    /** 严格点分十进制;不接受缩写、十六进制、八进制或单个整数形式。 */
    private fun ipv4Bytes(text: String): ByteArray? {
        val match = IPV4_PATTERN.matchEntire(text) ?: return null
        val bytes = ByteArray(4)
        for (index in 0 until 4) {
            val value = match.groupValues[index + 1].toInt()
            if (value > 255) return null
            bytes[index] = value.toByte()
        }
        return bytes
    }

    private fun ipv6Address(text: String, nics: List<LocalNic>): InetAddress? {
        val addressAndZone = splitZone(text) ?: return null
        val address = addressAndZone.first
        val zone = addressAndZone.second
        val scope = if (zone == null) 0 else resolveScope(zone, nics) ?: return null
        val bytes = ipv6Bytes(address) ?: return null
        // IPv4-mapped 地址归一成 IPv4,避免用 16 字节表示绕过 127/8 和网卡比较。
        if (isIpv4Mapped(bytes)) {
            if (scope != 0) return null
            return InetAddress.getByAddress(null, bytes.copyOfRange(12, 16))
        }
        return Inet6Address.getByAddress(null, bytes, scope)
    }

    /** 拆出 `%zone`;多个 `%` 或空 zone 都算畸形。 */
    private fun splitZone(text: String): Pair<String, String?>? {
        val first = text.indexOf('%')
        if (first < 0) return text to null
        if (text.indexOf('%', first + 1) >= 0) return null
        val zone = text.substring(first + 1)
        if (zone.isEmpty()) return null
        return text.substring(0, first) to zone
    }

    private fun resolveScope(zone: String, nics: List<LocalNic>): Int? {
        if (zone.all { it in '0'..'9' }) {
            val number = zone.toLongOrNull() ?: return null
            return if (number <= Int.MAX_VALUE) number.toInt() else null
        }
        return nics.firstOrNull { it.name == zone }?.index
    }

    private fun isIpv4Mapped(bytes: ByteArray): Boolean {
        for (index in 0 until 10) {
            if (bytes[index] != 0.toByte()) return false
        }
        return bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte()
    }

    /** 自行展开 IPv6,避免 `InetAddress.getByName` 对畸形数字输入也可能触发名字解析。 */
    private fun ipv6Bytes(text: String): ByteArray? {
        if (text.isEmpty()) return null
        val halves = text.split("::")
        if (halves.size > 2) return null
        val packed: List<Int>
        if (halves.size == 1) {
            packed = ipv6Groups(halves[0], allowTrailingIpv4 = true) ?: return null
            if (packed.size != 8) return null
        } else {
            val head = ipv6Groups(halves[0], allowTrailingIpv4 = false) ?: return null
            val tail = ipv6Groups(halves[1], allowTrailingIpv4 = true) ?: return null
            val explicit = head + tail
            // `::` 至少要代表一组;否则不是压缩写法。
            if (explicit.size >= 8) return null
            packed = head + List(8 - explicit.size) { 0 } + tail
        }
        val bytes = ByteArray(16)
        for (index in packed.indices) {
            bytes[index * 2] = (packed[index] ushr 8).toByte()
            bytes[index * 2 + 1] = packed[index].toByte()
        }
        return bytes
    }

    /** 解析 16 位组;末尾的嵌入式 IPv4 占两组。 */
    private fun ipv6Groups(text: String, allowTrailingIpv4: Boolean): List<Int>? {
        if (text.isEmpty()) return emptyList()
        val parts = text.split(':')
        if (parts.any { it.isEmpty() }) return null
        val groups = ArrayList<Int>(8)
        for ((index, part) in parts.withIndex()) {
            if ('.' in part) {
                if (!allowTrailingIpv4 || index != parts.lastIndex) return null
                val ipv4 = ipv4Bytes(part) ?: return null
                groups += ((ipv4[0].toInt() and 0xff) shl 8) or (ipv4[1].toInt() and 0xff)
                groups += ((ipv4[2].toInt() and 0xff) shl 8) or (ipv4[3].toInt() and 0xff)
            } else {
                if (part.length > 4 || !part.all { it.isHexDigit() }) return null
                groups += part.toInt(16)
            }
        }
        return groups.takeIf { it.size <= 8 }
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}

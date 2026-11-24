package com.daiyimo.maeve.core.cap.web

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 下载能取哪些地址:先过 [WebUrlPolicy.checkNavigable](与 web_navigate 同一条规则),再不许本机、局域网与保留地址。
 * 下载是 App 自己在后台发请求,页面借它就能摸到手机所在局域网里的设备,所以比打开网页更严。
 * 每一次跳转、每一次域名解析都按这条规则查([guardedDns] 挡住解析到内网的域名)。
 */
object DownloadAddressPolicy {
    /** 不能取的原因;能取为 null。只查字面地址,不查 DNS。 */
    fun refusal(url: String): String? {
        WebUrlPolicy.checkNavigable(url)?.let { return it }
        val host = url.toHttpUrlOrNull()?.host ?: return "not a web address"
        return if (blockedHost(host)) "local or private network address" else null
    }

    /** 导航与下载共用的完整检查:格式、字面地址、DNS 返回的每一个地址。 */
    fun resolvedRefusal(url: String, dns: Dns = Dns.SYSTEM, skipDns: (String) -> Boolean = { false }): String? {
        WebUrlPolicy.checkNavigable(url)?.let { return it }
        val host = url.toHttpUrlOrNull()?.host ?: return "not a web address"
        if (skipDns(host)) return null
        return hostRefusal(host, dns)
    }

    /** 普通域名要等解析后检查全部地址;字面地址与本地名字立即拒绝。 */
    fun hostRefusal(host: String, dns: Dns = Dns.SYSTEM): String? = try {
        allowedAddresses(host, dns)
        null
    } catch (_: BlockedAddress) {
        "local or private network address"
    } catch (_: UnknownHostException) {
        "the URL host could not be resolved"
    }

    /**
     * 返回这一次可以直接建连的已验证地址。字面量不查 DNS;域名只解析一次,
     * 调用方必须固定使用这批地址,不能在检查后重新解析(防 DNS 重绑定)。
     */
    fun allowedAddresses(host: String, dns: Dns = Dns.SYSTEM): List<InetAddress> {
        val h = host.trim().trimEnd('.').lowercase().removePrefix("[").removeSuffix("]")
        if (blockedHost(h)) throw BlockedAddress(host)
        val all = parseAddress(h)?.let(::listOf) ?: dns.lookup(h)
        if (all.isEmpty() || all.any(::blockedAddress)) throw BlockedAddress(host)
        return all
    }

    /** 本机或局域网的名字,或字面上就是内网/保留的 IP。普通域名要等解析后由 [guardedDns] 判。 */
    fun blockedHost(host: String): Boolean {
        val h = host.trim().trimEnd('.').lowercase().removePrefix("[").removeSuffix("]")
        if (h.isEmpty() || h == "localhost" || LOCAL_SUFFIXES.any { h.endsWith(it) }) return true
        parseAddress(h)?.let { return blockedAddress(it) }
        // 数字形状但解析失败(如 999.1.1.1、08.0.0.1)不能退回 DNS 绕过规范化。
        if (looksNumeric(h) || ':' in h) return true
        return false
    }

    fun blockedAddress(a: InetAddress): Boolean {
        if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return true
        val b = a.address
        return when (a) {
            is Inet4Address -> blockedV4(b)
            is Inet6Address -> {
                val embeddedV4 = when {
                    b.copyOfRange(0, 10).all { it == 0.toByte() } && b[10] == 0xff.toByte() && b[11] == 0xff.toByte() -> b.copyOfRange(12, 16)
                    b.copyOfRange(0, 12).all { it == 0.toByte() } -> b.copyOfRange(12, 16)
                    b.copyOfRange(0, 12).contentEquals(NAT64) -> b.copyOfRange(12, 16)
                    b[0] == 0x20.toByte() && b[1] == 0x02.toByte() -> b.copyOfRange(2, 6)
                    else -> null
                }
                val u0 = b[0].toInt() and 0xff
                val u1 = b[1].toInt() and 0xff
                (u0 and 0xfe) == 0xfc || b.copyOfRange(0, 6).contentEquals(NAT64_LOCAL) ||
                    (embeddedV4?.let(::blockedV4) == true) ||
                    (u0 == 0x20 && u1 == 0x01 && b[2] == 0x0d.toByte() && b[3] == 0xb8.toByte()) ||
                    (u0 == 0x20 && u1 == 0x01 && b[2] == 0.toByte() && (b[3].toInt() and 0xf0) == 0x10)
            }
            else -> true
        }
    }

    private fun blockedV4(b: ByteArray): Boolean {
        val o0 = b[0].toInt() and 0xff
        val o1 = b[1].toInt() and 0xff
        val o2 = b[2].toInt() and 0xff
        return o0 == 0 || o0 == 10 || o0 == 127 || o0 >= 224 ||
            (o0 == 100 && o1 in 64..127) || (o0 == 169 && o1 == 254) || (o0 == 172 && o1 in 16..31) ||
            (o0 == 192 && o1 == 168) || (o0 == 192 && o1 == 0 && o2 == 0) || (o0 == 192 && o1 == 0 && o2 == 2) ||
            (o0 == 198 && o1 in 18..19) || (o0 == 198 && o1 == 51 && o2 == 100) || (o0 == 203 && o1 == 0 && o2 == 113)
    }

    /** 解析到的地址里有一个是内网/保留地址就整个拒绝([BlockedAddress])。 */
    fun guardedDns(delegate: Dns = Dns.SYSTEM): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = allowedAddresses(hostname, delegate)
    }

    class BlockedAddress(host: String) : UnknownHostException("$host resolves to a local or private network address")

    /** 只解析字面地址,绝不调用 DNS;失败返回 null。 */
    internal fun parseAddress(host: String): InetAddress? = AddressParser.parse(host)

    private fun looksNumeric(host: String): Boolean = host.split('.').all { part ->
        part.startsWith("0x", true) || part.isNotEmpty() && part.all { it.isDigit() }
    }

    private val LOCAL_SUFFIXES = listOf(".localhost", ".local", ".lan", ".home.arpa", ".internal", ".intranet")
    private val NAT64 = byteArrayOf(0, 0x64, 0xff.toByte(), 0x9b.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
    private val NAT64_LOCAL = byteArrayOf(0, 0x64, 0xff.toByte(), 0x9b.toByte(), 0, 1)
}

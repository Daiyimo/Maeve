package com.daiyimo.maeve.core.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 地址是不是在本机或局域网里。只看地址本身,不做域名解析。
 * - [isLocal](严格):本机(localhost、127.0.0.0/8、::1)、私有网段(10/8、172.16/12、192.168/16、IPv6 的 fc00::/7)、
 *   链路本地(169.254/16、fe80::/10)和 `.local` 名字。用来判断明文 http 安不安全这类事。
 * - [bypassesProxy](宽一些):走网络代理时照旧直连的地址——[isLocal] 之外,还有不带点的主机名(局域网里的机器名)、
 *   局域网常用的后缀(`.lan`、`.home.arpa`、`.internal`)和运营商级 NAT 的共享网段 100.64/10(组网工具常给设备分这段)。
 *   本机、局域网里跑的模型服务代理多半到不了,所以直连。
 */
object LocalHosts {
    fun isLocal(host: String): Boolean {
        val name = normalize(host)
        if (name.isEmpty()) return false
        if (isLoopbackName(name)) return true
        parseIpv4(name)?.let { return isPrivateV4(it.first, it.second) }
        if (':' in name) return isLocalV6(name)
        return false
    }

    /** 走网络代理时这个主机照旧直连。 */
    fun bypassesProxy(host: String): Boolean {
        if (isLocal(host)) return true
        val name = normalize(host)
        if (name.isEmpty()) return false
        if (PROXY_SUFFIXES.any(name::endsWith)) return true
        parseIpv4(name)?.let { return it.first == 100 && it.second in 64..127 }
        return ':' !in name && '.' !in name
    }

    /** 明文 http、又不在本机或局域网的地址:Key 和对话会在网上不加密地传。认不出的地址不算。 */
    fun unencryptedRemote(url: String): Boolean {
        val parsed = url.trim().toHttpUrlOrNull() ?: return false
        return !parsed.isHttps && !isLocal(parsed.host)
    }

    private val PROXY_SUFFIXES = listOf(".lan", ".home.arpa", ".internal")

    /**
     * 浏览器的直连规则([bypassesProxy] 的同一套,写成浏览器内核认的格式);本机地址内核本来就直连,
     * 不带点的主机名另由「简单主机名直连」管。
     */
    val BYPASS_RULES: List<String> = listOf(
        "localhost", "*.localhost", "*.local", "*.lan", "*.home.arpa", "*.internal",
        "127.0.0.0/8", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "100.64.0.0/10",
        "fc00::/7", "fe80::/10",
    )

    /** 给命令行工具的 no_proxy:逗号分隔的主机、后缀和网段(不认网段的工具也至少认得前三个)。 */
    const val NO_PROXY = "localhost,127.0.0.1,::1,127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,169.254.0.0/16,100.64.0.0/10," +
        "fc00::/7,fe80::/10,.localhost,.local,.lan,.home.arpa,.internal"

    private fun normalize(host: String): String =
        host.trim().trimStart('[').trimEnd(']').trimEnd('.').lowercase()

    private fun isLoopbackName(name: String): Boolean =
        name == "localhost" || name.endsWith(".localhost") || name.endsWith(".local")

    private fun isPrivateV4(first: Int, second: Int): Boolean = when (first) {
        127, 10 -> true
        172 -> second in 16..31
        192 -> second == 168
        169 -> second == 254
        else -> false
    }

    private fun isLocalV6(name: String): Boolean {
        if (name == "::1") return true
        val first = name.substringBefore(':').toIntOrNull(16) ?: return false
        return first and 0xfe00 == 0xfc00 || first and 0xffc0 == 0xfe80
    }

    /** 点分四段 IPv4 的前两段;不是 IPv4 为 null。 */
    private fun parseIpv4(name: String): Pair<Int, Int>? {
        val parts = name.split('.')
        if (parts.size != 4) return null
        val numbers = ArrayList<Int>(4)
        for (part in parts) {
            if (part.isEmpty() || !part.all(Char::isDigit)) return null
            numbers += part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
        return numbers[0] to numbers[1]
    }
}

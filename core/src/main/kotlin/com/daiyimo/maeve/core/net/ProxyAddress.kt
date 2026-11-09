package com.daiyimo.maeve.core.net

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URLDecoder

/**
 * 一个网络代理地址,形如 `协议://[用户:密码@]主机:端口`:协议是 http、https、socks5、socks5h 之一,后面不带路径。
 * [toString] 不带账号密码,写进日志、显示在界面上都不会泄露。
 */
class ProxyAddress private constructor(
    val scheme: String,
    /** 主机名或 IP;IPv6 不带方括号。 */
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
) {
    val socks: Boolean
        get() = scheme == SOCKS5 || scheme == SOCKS5H

    val hasCredentials: Boolean
        get() = user.isNotEmpty()

    /**
     * Maeve 自己的连接(调模型、下更新、内置浏览器)能不能走它。https 代理要先和代理本身握 TLS,
     * 这些连接用的网络库不支持,只有容器里的命令(curl 等)用得上。
     */
    val forApp: Boolean
        get() = scheme != HTTPS

    fun javaProxy(): Proxy {
        val kind = if (socks) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(kind, InetSocketAddress.createUnresolved(host, port))
    }

    /** 给人看的写法:协议、主机、端口,不带账号密码。 */
    val display: String
        get() = buildString {
            append(scheme).append("://")
            if (host.contains(':')) append('[').append(host).append(']') else append(host)
            append(':').append(port)
        }

    override fun toString(): String = display

    companion object {
        private const val HTTPS = "https"
        private const val SOCKS5 = "socks5"
        private const val SOCKS5H = "socks5h"
        val SCHEMES = setOf("http", "https", SOCKS5, SOCKS5H)

        /** 解析代理地址;空串或写法不对为 null。 */
        fun parse(text: String): ProxyAddress? {
            val candidate = text.trim()
            if (candidate.isEmpty() || candidate.any(Char::isWhitespace)) return null
            val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme !in SCHEMES) return null
            val host = uri.host?.removeSurrounding("[", "]")?.takeIf { it.isNotEmpty() } ?: return null
            val port = uri.port
            if (port !in 1..65535) return null
            if (!isBareTarget(uri)) return null
            val info = uri.rawUserInfo.orEmpty()
            val user = decode(info.substringBefore(':'))
            val password = decode(info.substringAfter(':', ""))
            return ProxyAddress(scheme, host, port, user, password)
        }

        private fun isBareTarget(uri: URI): Boolean {
            val path = uri.rawPath
            return (path.isNullOrEmpty() || path == "/") && uri.rawQuery == null && uri.rawFragment == null
        }

        // URI 中的加号是字面字符,解码时保留它。
        private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")

        /** 空串(不用代理),或能解析的代理地址。 */
        fun valid(text: String): Boolean = text.isBlank() || parse(text) != null
    }
}

package com.daiyimo.maeve.core.net

import java.net.IDN
import java.net.URI
import java.net.URLDecoder

/** 网址里的主机名与站点:「在哪个网站」的说法(读屏、运行状态、知识匹配、技能草稿)都按它算。 */
object UrlHost {
    /** 常见的两段公共后缀。 */
    private val TWO_PART_SUFFIXES = setOf(
        "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn", "ac.cn",
        "com.hk", "com.tw", "com.mo", "com.sg", "com.au", "com.br",
        "co.uk", "org.uk", "ac.uk", "co.jp", "ne.jp", "or.jp", "co.kr", "co.in", "co.nz",
    )

    /** 托管平台:每个用户的子域名是不同的站点,平台域名本身当公共后缀。 */
    private val PRIVATE_SUFFIXES = setOf(
        "github.io", "vercel.app", "pages.dev", "netlify.app", "blogspot.com", "herokuapp.com",
        "amazonaws.com", "workers.dev", "web.app", "appspot.com", "firebaseapp.com",
    )

    /** 国家顶级域名下常作第二段公共后缀的词。 */
    private val GENERIC_SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac", "or", "ne", "go")

    /** 小写 ASCII 域名(国际化域名转成 xn-- 形式),去掉端口和开头的 www.;解析不出为 null。 */
    fun of(url: String): String? {
        val host = raw(url) ?: return null
        val withoutWww = host.removePrefix("www.")
        return withoutWww.ifEmpty { null }
    }

    /**
     * 两个地址是否同一站点:主机名相同(不计 www),或同属一个能确定的可注册域名。
     * 不看协议和端口。拿不准的后缀一律判为不同站点。
     */
    fun sameSite(a: String, b: String): Boolean {
        val left = of(a) ?: return false
        val right = of(b) ?: return false
        if (left == right) return true
        val leftSite = registrable(left) ?: return false
        return leftSite == registrable(right)
    }

    internal fun raw(url: String): String? {
        val parsed = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = parsed.host ?: parsed.rawAuthority?.let(::authorityHost) ?: return null
        val ascii = runCatching { IDN.toASCII(host.trimEnd('.')) }.getOrNull()?.lowercase() ?: return null
        return ascii.ifEmpty { null }
    }

    private fun authorityHost(authority: String): String? {
        val hostAndPort = URLDecoder.decode(authority.substringAfterLast('@'), "UTF-8")
        if (hostAndPort.startsWith("[")) return hostAndPort.substringBefore(']') + "]"
        return hostAndPort.substringBefore(':').ifEmpty { null }
    }

    /** 可注册域名(公共后缀再加一段);后缀拿不准、或主机名就是后缀本身时为 null。 */
    fun registrable(host: String): String? {
        if (host.startsWith("[") || host.all { it.isDigit() || it == '.' }) return null
        val labels = host.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return null
        val suffix = suffixLength(labels)
        if (labels.size <= suffix) return null
        return joinTail(labels, suffix + 1)
    }

    private fun joinTail(labels: List<String>, count: Int): String {
        val first = labels.size - count
        return labels.subList(first, labels.size).joinToString(".")
    }

    private fun suffixLength(labels: List<String>): Int {
        val lastTwo = labels.takeLast(2).joinToString(".")
        if (lastTwo in TWO_PART_SUFFIXES || lastTwo in PRIVATE_SUFFIXES) return 2
        val tld = labels.last()
        val secondLast = labels[labels.size - 2]
        val looksLikeCountrySuffix = tld.length == 2 && (secondLast.length <= 3 || secondLast in GENERIC_SECOND_LEVEL)
        return if (looksLikeCountrySuffix) 2 else 1
    }
}

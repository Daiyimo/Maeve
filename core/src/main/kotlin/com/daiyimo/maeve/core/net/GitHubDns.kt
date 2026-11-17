package com.daiyimo.maeve.core.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * GitHub 加速的方式。「获取更新」只问 GitHub,但国内网络下 api.github.com 时通时不通、
 * github.com 常年被重置——光是 DNS 这一层就常出问题(污染、绕路)。
 *
 * - [System]:跟随系统 DNS(默认,什么都不改);
 * - [AliDoh] / [TencentDoh]:经国内公共 DNS 的 DoH(JSON)解析 GitHub 的域名——
 *   加密通道,不被运营商的污染结果带跑;阿里/腾讯两处可选,哪处顺用哪处;
 * - [CustomIp]:hosts 式定点,把 GitHub 域名都指到用户给的 IP(从测 IP 的网站拿),
 *   适合知道某个边缘 IP 顺的时候。
 *
 * 只作用于 GitHub 的域名(见 [GitHubHosts]);别处(调模型、列模型)照旧走系统 DNS。
 */
enum class GitHubDnsMode { System, AliDoh, TencentDoh, CustomIp }

/** 检查更新与下载安装会碰到的 GitHub 域名。 */
object GitHubHosts {
    private val EXACT = setOf(
        "github.com", "api.github.com", "codeload.github.com", "gist.github.com",
        "raw.githubusercontent.com", "objects.githubusercontent.com", "gist.githubusercontent.com",
    )

    fun matches(host: String): Boolean = host in EXACT || host.endsWith(".githubusercontent.com")
}

/**
 * 给 OkHttp 用的 [Dns]:按 [GitHubDnsMode] 解析 GitHub 域名,别处交给系统。
 * DoH 查询失败、自定义 IP 填得不对,都退回系统 DNS——加速是锦上添花,不拦路。
 * 解析结果按 [TTL_MS] 记住,免得每次建连接都问一遍。
 */
class GitHubDns(
    private val mode: () -> GitHubDnsMode,
    private val customIp: () -> String,
    private val dohClient: OkHttpClient = defaultDohClient(),
    private val now: () -> Long = System::currentTimeMillis,
    /** 两处 DoH 的解析地址(测试里指向本地假服务器)。 */
    private val aliDohUrl: (String) -> String = { host -> "https://dns.alidns.com/resolve?name=$host&type=A" },
    private val tencentDohUrl: (String) -> String = { host -> "https://doh.pub/dns-query?name=$host&type=A" },
    /** 兜底解析:非 GitHub 域名、以及加速没问出结果时都交给它(测试里换假的)。 */
    private val system: Dns = Dns.SYSTEM,
) : Dns {
    private class Cached(val ips: List<InetAddress>, val at: Long)

    private val cache = HashMap<String, Cached>()

    override fun lookup(hostname: String): List<InetAddress> {
        if (!GitHubHosts.matches(hostname)) return system.lookup(hostname)
        return when (mode()) {
            GitHubDnsMode.System -> system.lookup(hostname)
            GitHubDnsMode.CustomIp -> literalIp(customIp())?.let(::listOf)
                ?: system.lookup(hostname)
            GitHubDnsMode.AliDoh, GitHubDnsMode.TencentDoh ->
                remembered(hostname) ?: askDoh(hostname) ?: system.lookup(hostname)
        }
    }

    /** 缓存里还没过期的解析结果。 */
    private fun remembered(host: String): List<InetAddress>? = synchronized(cache) {
        cache[host]?.takeIf { now() - it.at < TTL_MS }?.ips
    }

    private fun remember(host: String, ips: List<InetAddress>) = synchronized(cache) {
        cache[host] = Cached(ips, now())
    }

    /** 经 DoH 问 [host] 的 A 记录;问不到(超时、非 0 状态、没有 A 记录)为 null。 */
    private fun askDoh(host: String): List<InetAddress>? {
        val url = when (mode()) {
            GitHubDnsMode.AliDoh -> aliDohUrl(host)
            GitHubDnsMode.TencentDoh -> tencentDohUrl(host)
            else -> return null
        }
        val ips = runCatching {
            dohClient.newCall(Request.Builder().url(url).header("Accept", "application/dns-json").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = json.decodeFromString<DohResponse>(resp.body?.string().orEmpty())
                    // Status 0(NOERROR)之外(SERVFAIL、被拒等)没有可用的 A 记录
                    if (body.Status != NOERROR) return@use null
                    body.Answer.filter { it.type == A_RECORD }.map { it.data }
                }
        }.getOrNull()?.filter { literalIp(it) != null }?.map { InetAddress.getByName(it) }?.takeIf { it.isNotEmpty() }
            ?: return null
        remember(host, ips)
        return ips
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val A_RECORD = 1
        private const val NOERROR = 0
        private const val TTL_MS = 5 * 60 * 1000L

        /** 字面 IP(IPv4/IPv6)才收;填了域名一律当没填,免得又绕回 DNS。 */
        fun literalIp(text: String): InetAddress? {
            val v = text.trim()
            if (v.isEmpty()) return null
            val addr = runCatching { InetAddress.getByName(v) }.getOrNull() ?: return null
            return addr.takeIf { it.hostAddress == v }
        }

        /** 问 DoH 用的客户端:不挂本 DNS(否则绕回自己),也不走代理——两处 DoH 端点都在国内。 */
        fun defaultDohClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}

/** DoH(JSON)的应答:Status 0 为正常,Answer 里 type=1 的是 A 记录。 */
@Serializable
private data class DohResponse(val Status: Int = -1, val Answer: List<DohAnswer> = emptyList())

@Serializable
private data class DohAnswer(val type: Int = 0, val data: String = "")

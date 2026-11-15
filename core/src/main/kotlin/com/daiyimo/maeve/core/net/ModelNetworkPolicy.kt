package com.daiyimo.maeve.core.net

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class ModelNetworkDenied(val code: String) : IOException(code)

/** 模型接口不跟随重定向；HTTP 例外只限用户批准的源、全 LAN DNS 和可核验直连。 */
class ModelNetworkPolicy(baseUrl: String, private val selfHostedHttp: Boolean = false) {
    private val endpoint = baseUrl.toHttpUrl()
    init {
        require(endpoint.username.isEmpty() && endpoint.password.isEmpty() && endpoint.fragment == null && endpoint.query == null)
        if (!endpoint.isHttps && !selfHostedHttp) throw ModelNetworkDenied("model_https_required")
    }

    fun validateForStorage(resolver: Dns = Dns.SYSTEM) {
        if (!endpoint.isHttps) validateAddresses(resolver.lookup(endpoint.host), cleartext = true)
    }

    fun protect(client: OkHttpClient): OkHttpClient {
        val resolver = client.dns
        val guardedDns = object : Dns {
            override fun lookup(hostname: String) = resolver.lookup(hostname).also { validateAddresses(it, cleartext = !endpoint.isHttps) }
        }
        return client.newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(minOf(client.callTimeoutMillis.takeIf { it > 0 } ?: 120_000, 120_000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .dns(guardedDns)
            .addInterceptor { chain ->
                val url = chain.request().url
                if (url.scheme != endpoint.scheme || url.host != endpoint.host || url.port != endpoint.port) throw ModelNetworkDenied("model_endpoint_changed")
                validateAddresses(resolver.lookup(url.host), cleartext = !url.isHttps)
                if (!url.isHttps) {
                    val proxies = client.proxy?.let(::listOf) ?: client.proxySelector.select(url.toUri())
                    if (proxies.isNullOrEmpty() || proxies.any { it.type() != Proxy.Type.DIRECT }) throw ModelNetworkDenied("model_http_proxy_unverified")
                }
                chain.proceed(chain.request())
            }.addNetworkInterceptor { chain ->
                val route = chain.connection()?.route() ?: throw ModelNetworkDenied("model_route_unverified")
                val address = route.socketAddress.address ?: throw ModelNetworkDenied("model_route_unverified")
                val cleartext = !chain.request().url.isHttps
                if (cleartext && route.proxy.type() != Proxy.Type.DIRECT) throw ModelNetworkDenied("model_http_proxy_unverified")
                validateAddresses(listOf(address), cleartext)
                chain.proceed(chain.request())
            }.build()
    }

    companion object {
        fun validateAddresses(addresses: List<InetAddress>, cleartext: Boolean) {
            if (addresses.isEmpty()) throw ModelNetworkDenied("model_dns_unverified")
            for (address in addresses) {
                val bytes = address.address
                val a = bytes[0].toInt() and 255
                val b = bytes[1].toInt() and 255
                val ipv4Forbidden = bytes.size == 4 && (a == 0 || a >= 224 || bytes[3].toInt() and 255 == 255 || a == 169 && b == 254 || a == 100 && b in 64..127 || a == 198 && b in 18..19)
                val metadataV6 = address.address.contentEquals(InetAddress.getByName("fd00:ec2::254").address)
                val transitionV6 = bytes.size == 16 && a == 0x20 && (b == 2 || b == 1 && bytes[2] == 0.toByte() && bytes[3] == 0.toByte())
                if (address.isAnyLocalAddress || address.isMulticastAddress || address.isLinkLocalAddress || ipv4Forbidden || metadataV6 || transitionV6 ||
                    // IPv6 未指定、链路本地、组播之外的特殊兼容/转换前缀不作为 HTTP LAN 例外。
                    bytes.size == 16 && !address.isLoopbackAddress && a !in 0x20..0x3f && a !in listOf(0xfc, 0xfd)) {
                    throw ModelNetworkDenied("model_destination_forbidden")
                }
                val lan = address.isLoopbackAddress || (bytes.size == 4 && (a == 10 || a == 172 && b in 16..31 || a == 192 && b == 168)) || (bytes.size == 16 && a in listOf(0xfc, 0xfd))
                if (cleartext && !lan) throw ModelNetworkDenied("model_http_public_destination")
            }
        }
    }
}

package com.daiyimo.maeve.core.net

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.Authenticator as JavaAuthenticator
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Authenticator as OkAuthenticator

/** 代理自身的问题，与服务商侧错误区分开。 */
enum class ProxyFailure {
    /** 连不上代理，或代理没有转发该请求。 */
    Unreachable,

    /** 代理要求账号密码，而提供的凭据不被接受。 */
    AuthFailed,
}

/**
 * Maeve 自身连接（模型调用、模型列表、授权换令牌、更新下载、内置浏览器下载）
 * 所使用的网络代理设置。
 *
 * @param source 返回当前保存的代理地址字符串；空串表示不使用代理
 *
 * 行为要点：
 * - 本机与局域网主机（[LocalHosts.bypassesProxy]）始终直连；
 * - 未设置代理或该代理不适用于 App（`ProxyAddress.forApp` 为假）时直连；
 * - HTTP 代理凭据在收到 407 或建立隧道前以 `Proxy-Authorization` 携带，同一请求
 *   已携带过仍被拒则不再重试；
 * - SOCKS 代理凭据只能通过全进程唯一的 [JavaAuthenticator] 提供，
 *   且只回应与当前 SOCKS 代理主机、端口完全匹配的询问。
 */
class NetProxy(private val source: () -> String) {

    /** 代理地址字符串与解析结果的配对缓存，键是原始字符串本身。 */
    private class Parsed(val raw: String, val address: ProxyAddress?)

    @Volatile
    private var parsed: Parsed? = null

    /** 由 [client] 创建的客户端连接池，设置变更时统一清理空闲连接。 */
    private val pools = CopyOnWriteArrayList<WeakReference<okhttp3.ConnectionPool>>()

    /** 当前应使用的代理；未设置、格式非法或不适用时为 null。 */
    fun current(): ProxyAddress? {
        val raw = runCatching(source).getOrDefault("")
        parsed?.takeIf { it.raw == raw }?.let { return it.address }
        val address = ProxyAddress.parse(raw)?.takeIf { it.forApp }
        parsed = Parsed(raw, address)
        return address
    }

    /** 访问 [host] 应使用的代理；本机与局域网地址直连（返回 null）。 */
    fun proxyFor(host: String): ProxyAddress? = current()?.takeUnless { LocalHosts.bypassesProxy(host) }

    /** OkHttp 的代理选择器：按主机决定直连或走代理。 */
    val selector: ProxySelector = Selector()

    /** OkHttp 的代理鉴权器：处理 HTTP 代理的 407 挑战。 */
    val authenticator: OkAuthenticator = OkAuthenticator { route, response -> authenticate(route, response) }

    private fun authenticate(route: Route?, response: Response): Request? {
        // 已经带过凭据仍被拒绝：不再重试，把「代理账号或密码不对」交给上层
        if (response.request.header(PROXY_AUTHORIZATION) != null) return null
        val proxy = current() ?: return null
        if (proxy.socks || !proxy.hasCredentials) return null
        if (route != null && route.proxy.type() != Proxy.Type.HTTP) return null
        return response.request.newBuilder()
            .header(PROXY_AUTHORIZATION, Credentials.basic(proxy.user, proxy.password))
            .build()
    }

    /** 按 [builder] 构建使用本代理设置的客户端；其连接池会在设置变更时被清理。 */
    fun client(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
        builder
            .proxySelector(selector)
            .proxyAuthenticator(authenticator)
            .build()
            .also { pools += WeakReference(it.connectionPool) }

    /** 设置已变更：重新解析并断开所有空闲连接，后续请求按新设置建立连接。 */
    fun changed() {
        parsed = null
        pools.removeAll { it.get() == null }
        pools.forEach { it.get()?.evictAll() }
    }

    /** SOCKS 代理凭据提供者：只回应发给当前 SOCKS 代理的询问。 */
    val socksAuthenticator: JavaAuthenticator = object : JavaAuthenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? =
            socksCredentials(requestingProtocol, requestingHost, requestingPort)
    }

    internal fun socksCredentials(protocol: String?, host: String?, port: Int): PasswordAuthentication? {
        val proxy = current() ?: return null
        if (!proxy.socks || !proxy.hasCredentials) return null
        if (!matchesProxy(protocol, host, port, proxy)) return null
        return PasswordAuthentication(proxy.user, proxy.password.toCharArray())
    }

    /** 这次询问是否发给 [proxy] 这个 SOCKS 代理：协议、主机、端口都要对上。 */
    private fun matchesProxy(protocol: String?, host: String?, port: Int, proxy: ProxyAddress): Boolean {
        if (protocol?.startsWith("SOCKS", ignoreCase = true) != true) return false
        if (host == null) return false
        val normalized = host.removeSurrounding("[", "]")
        return normalized.equals(proxy.host, ignoreCase = true) && port == proxy.port
    }

    /** 安装 [socksAuthenticator]（全进程唯一），App 启动时调用一次。 */
    fun installSocksAuth() {
        JavaAuthenticator.setDefault(socksAuthenticator)
    }

    private inner class Selector : ProxySelector() {
        /** 持有本选择器的 [NetProxy] 实例，供 [NetProxy.of] 反查。 */
        val owner: NetProxy get() = this@NetProxy

        override fun select(uri: URI?): List<Proxy> {
            val host = uri?.host ?: return listOf(Proxy.NO_PROXY)
            return listOf(proxyFor(host)?.javaProxy() ?: Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    companion object {
        private const val PROXY_AUTHORIZATION = "Proxy-Authorization"

        /** 产品内共享实例的代理地址来源，App 启动时接到持久化设置上。 */
        @Volatile
        var sharedSource: () -> String = { "" }

        /** 进程内共享的代理实例。 */
        val shared: NetProxy = NetProxy { sharedSource() }

        /** 反查该客户端是否由 [client] 构建；不是则返回 null。 */
        fun of(http: OkHttpClient): NetProxy? = (http.proxySelector as? Selector)?.owner

        /** [http] 请求 [url] 时的失败是否由代理引起；不走代理时为 null。 */
        fun failure(http: OkHttpClient, url: HttpUrl, e: IOException): ProxyFailure? {
            of(http)?.proxyFor(url.host) ?: return null
            return classify(e)
        }

        /**
         * 对走代理时出现的网络错误归类。
         *
         * 代理拒绝凭据归为 [ProxyFailure.AuthFailed]；连不上代理、建隧道失败、
         * SOCKS 握手失败归为 [ProxyFailure.Unreachable]；读取响应过程中的
         * 中断等不算代理问题，返回 null。
         */
        fun classify(e: IOException): ProxyFailure? {
            val chain = generateSequence(e as Throwable) { it.cause }.take(6).toList()
            val messages = chain.map { it.message.orEmpty() }
            if (messages.any(::rejectedByProxy)) return ProxyFailure.AuthFailed
            if (messages.any(::failedInProxyHop)) return ProxyFailure.Unreachable
            return if (chain.any(::failedToConnect)) ProxyFailure.Unreachable else null
        }

        /** 代理或 SOCKS 服务端明确拒绝了凭据。 */
        private fun rejectedByProxy(message: String): Boolean =
            message.contains("authenticate with proxy", true) ||
                (message.contains("SOCKS", true) && message.contains("auth", true))

        /** 请求已经送到代理，但代理没有把它转发出去。 */
        private fun failedInProxyHop(message: String): Boolean =
            message.contains("for CONNECT", true) || message.contains("SOCKS", true)

        /** 连代理本身就失败（拒绝连接、找不到主机、连接阶段超时）。 */
        private fun failedToConnect(t: Throwable): Boolean = when (t) {
            is ConnectException, is NoRouteToHostException, is UnknownHostException -> true
            is SocketTimeoutException -> t.message.orEmpty().contains("connect", true)
            else -> false
        }
    }
}

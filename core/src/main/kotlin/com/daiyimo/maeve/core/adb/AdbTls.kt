package com.daiyimo.maeve.core.adb

import java.net.InetSocketAddress
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.Provider
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

/** 导出 TLS 密钥材料(RFC 8446 §7.5);JDK 没有公开接口,由平台给出——Android 上是 `SSLSockets.exportKeyingMaterial`。 */
fun interface KeyingMaterialExporter {
    fun export(socket: SSLSocket, label: String, length: Int): ByteArray
}

/**
 * adb 的 TLS 1.3 封装:两端都亮自签名证书,且互不验证对方证书链。
 * 配对时靠 SPAKE2 把身份绑到导出密钥;连接时则由设备核对证书里的公钥。目标仅限本机地址。
 */
class AdbTls(
    val identity: AdbIdentity,
    private val exporter: KeyingMaterialExporter,
    private val provider: Provider? = null,
) {
    val context: SSLContext by lazy {
        val sslContext = if (provider == null) SSLContext.getInstance("TLS") else SSLContext.getInstance("TLS", provider)
        sslContext.init(arrayOf(FixedKeyManager(identity.keyPair.private, identity.certificate)), arrayOf(TrustAll), null)
        sslContext
    }

    /** 按调用方已核对的 [target] 新建 TCP 连接并完成 TLS 握手;不再重新解析主机名。 */
    fun connect(target: InetSocketAddress, timeoutMs: Int): SSLSocket {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(target, timeoutMs)
            socket.soTimeout = timeoutMs
        } catch (error: Exception) {
            socket.close()
            throw error
        }
        return wrap(socket, target, client = true)
    }

    /** 在既有的 [plain] 连接上做 TLS 握手;[client] = false 仅用于测试里的假服务端。 */
    fun wrap(plain: Socket, target: InetSocketAddress, client: Boolean): SSLSocket {
        val hostAddress = target.address?.hostAddress ?: throw IllegalArgumentException("ADB address must be resolved")
        val tls = context.socketFactory.createSocket(plain, hostAddress, target.port, true) as SSLSocket
        try {
            tls.enabledProtocols = arrayOf("TLSv1.3")
            tls.useClientMode = client
            if (!client) tls.needClientAuth = true
            tls.startHandshake()
        } catch (error: Exception) {
            tls.close()
            throw error
        }
        return tls
    }

    fun exportKeyingMaterial(socket: SSLSocket, label: String, length: Int): ByteArray = exporter.export(socket, label, length)

    /** 只认固定的一把钥匙和一张证书,别名一律给 [ALIAS]。 */
    private class FixedKeyManager(private val privateKey: PrivateKey, private val certificate: X509Certificate) : X509ExtendedKeyManager() {
        override fun getClientAliases(keyAlgorithm: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseClientAlias(keyAlgorithms: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyAlgorithms: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getServerAliases(keyAlgorithm: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getCertificateChain(alias: String?) = arrayOf(certificate)
        override fun getPrivateKey(alias: String?) = privateKey
    }

    /** 一律信任:证书链由 SPAKE2 / 设备端公钥比对来背书,这里不再校验。 */
    private object TrustAll : X509ExtendedTrustManager() {
        override fun checkClientTrusted(certChain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkClientTrusted(certChain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
        override fun checkClientTrusted(certChain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
        override fun checkServerTrusted(certChain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(certChain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
        override fun checkServerTrusted(certChain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val ALIAS = "adb"
    }
}

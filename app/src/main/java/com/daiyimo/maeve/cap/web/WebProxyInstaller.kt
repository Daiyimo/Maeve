package com.daiyimo.maeve.cap.web

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor

/** 进程级安装一次回环代理;WebView 的每个新请求都重新校验并固定目标地址。 */
internal object WebProxyInstaller {
    @Volatile private var installed = false
    private var pending: CompletableDeferred<Boolean>? = null
    private var proxy: LocalHttpProxy? = null

    val isInstalled: Boolean get() = installed
    val connectionCount: Long get() = proxy?.connectionCount ?: 0L
    val blockedCount: Long get() = proxy?.blockedCount ?: 0L

    @Synchronized
    fun ensureInstalled(context: Context): CompletableDeferred<Boolean> {
        if (installed) return CompletableDeferred(true)
        pending?.let { return it }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            return CompletableDeferred(false)
        }
        val local = try {
            LocalHttpProxy().also { it.start() }
        } catch (_: Exception) {
            return CompletableDeferred(false)
        }
        val done = CompletableDeferred<Boolean>()
        proxy = local
        pending = done
        val main = Handler(Looper.getMainLooper())
        val executor = Executor { command -> main.post(command) }
        try {
            ProxyController.getInstance().setProxyOverride(
                ProxyConfig.Builder().addProxyRule("127.0.0.1:${local.port}").removeImplicitRules().build(),
                executor,
            ) {
                installed = true
                done.complete(true)
            }
        } catch (_: Exception) {
            local.close()
            proxy = null
            pending = null
            done.complete(false)
        }
        return done
    }

    suspend fun awaitInstalled(context: Context): Boolean =
        withTimeoutOrNull(5_000) { ensureInstalled(context).await() } ?: false
}

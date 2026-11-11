package com.daiyimo.maeve.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.R
import com.daiyimo.maeve.cap.web.WebProxy
import com.daiyimo.maeve.core.net.ProxyAddress
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.settings.NetProxyStore

object NetProxyCopy {
    val TITLE: String get() = tr(R.string.net_proxy)
    val HINT: String get() = tr(R.string.net_proxy_hint)
    val INVALID: String get() = tr(R.string.net_proxy_invalid)

    /** 设置首页那一行右边:没设写「未设置」,设了写地址。 */
    fun value(proxy: String): String = NetProxyStore.display(proxy).ifEmpty { tr(R.string.settings_not_set) }

    /**
     * 那一行唯一的副标题:只在**确实配不上的时候**出现(只给容器用的写法、浏览器用不了)。
     * 平时返回 null —— 「调模型、下更新、内置浏览器都走它」是拿标题换个说法再说一遍，
     * 已经挪进标题旁的 [HINT] 气泡。
     * [browser] 为空表示还没查出浏览器这边的情况。
     */
    fun subtitle(proxy: String, browser: WebProxy.Mode?): String? {
        val address = ProxyAddress.parse(proxy)
        return when {
            address != null && !address.forApp -> tr(R.string.net_proxy_container_only)
            browser == WebProxy.Mode.Unsupported -> tr(R.string.net_proxy_browser_unsupported)
            browser == WebProxy.Mode.SocksWithCredentials -> tr(R.string.net_proxy_browser_socks_auth)
            else -> null
        }
    }
}

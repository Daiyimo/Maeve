package com.daiyimo.maeve.cap.web

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 真机回归:WebView 安装 DNS 固定代理,HTTPS 主文档确实经过它。 */
@RunWith(AndroidJUnit4::class)
class WebProxyDeviceTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private lateinit var tab: WebTab

    @Before
    fun setUp() {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(instr.context))
            .build()
        instr.runOnMainSync { tab = WebTab(instr.targetContext, "proxy", "proxy-task", listOf(loader)) }
    }

    @After
    fun tearDown() {
        instr.runOnMainSync { tab.destroy() }
    }

    @Test
    fun dnsPinningProxyIsInstalledWhenWebViewSupportsIt() = runBlocking {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return@runBlocking
        assertTrue(WebProxyInstaller.awaitInstalled(instr.targetContext))
        assertTrue(WebProxyInstaller.isInstalled)
    }

    @Test
    fun loopbackSubresourceDoesNotBypassPinningProxy() = runBlocking {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return@runBlocking
        assertTrue(WebProxyInstaller.awaitInstalled(instr.targetContext))
        val local = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).apply { soTimeout = 300 }
        try {
            val html = "<script>fetch('http://127.0.0.1:${local.localPort}/')" +
                ".catch(() => {}).finally(() => { document.title = 'fetched'; });</script>"
            instr.runOnMainSync {
                tab.view.loadDataWithBaseURL("http://public.test/", html, "text/html", "UTF-8", null)
            }
            var fetched = false
            repeat(50) {
                instr.runOnMainSync { fetched = tab.view.title == "fetched" }
                if (fetched) return@repeat
                delay(100)
            }
            assertTrue("subresource fetch never ran", fetched)
            val direct = runCatching { local.accept() }.getOrNull()
            assertNull("loopback subresource bypassed the proxy", direct)
            direct?.close()
        } finally {
            local.close()
        }
    }

    @Test
    fun webViewHttpsNavigationActuallyTraversesPinningProxy() = runBlocking {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return@runBlocking
        assertTrue(WebProxyInstaller.awaitInstalled(instr.targetContext))
        val before = WebProxyInstaller.connectionCount
        tab.load("https://example.com:80/", timeoutMs = 5_000)
        assertTrue("proxy connections ${before}->${WebProxyInstaller.connectionCount}, error=${tab.mainFrameError}",
            WebProxyInstaller.connectionCount > before)
    }
}

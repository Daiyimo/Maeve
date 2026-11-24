package com.daiyimo.maeve.cap.web

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 真机回归:主文档与重定向不能借 IPv4 短写绕过 SSRF,资产夹具仍可直接加载。 */
@RunWith(AndroidJUnit4::class)
class WebNavigationSsrfTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private lateinit var tab: WebTab
    private fun fixture(name: String) = "https://appassets.androidplatform.net/assets/web-fixtures/$name"

    @Before
    fun setUp() {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(instr.context))
            .build()
        instr.runOnMainSync { tab = WebTab(instr.targetContext, "ssrf", "ssrf-task", listOf(loader)) }
    }

    @After
    fun tearDown() {
        instr.runOnMainSync { tab.destroy() }
    }

    @Test
    fun shortIntegerOctalAndHexLoopbackAreRefused() = runBlocking {
        for (url in listOf("http://127.1/", "http://2130706433/", "http://0177.0.0.1/", "http://0x7f000001/")) {
            assertFalse(url, tab.load(url, timeoutMs = 1_000))
        }
    }

    @Test
    fun fixtureStillLoadsAndRedirectToShortLoopbackIsRefused() = runBlocking {
        assertTrue(tab.load(fixture("host.html")))
        assertFalse(tab.load(fixture("ssrf-redirect.html")))
        assertEquals("blocked_address", tab.mainFrameError)
    }
}

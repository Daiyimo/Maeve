package com.daiyimo.maeve.cap.web

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

/** WebView 请求头回归：允许列表移除后，请求仍应完成且头部值只能是空或本应用包名。 */
@RunWith(AndroidJUnit4::class)
class RequestedWithHeaderDeviceTest {
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private lateinit var webView: WebView
    private val loaded = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        instr.runOnMainSync {
            webView = WebView(ctx)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    loaded.complete(Unit)
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.path != "/net/headers.json") return null
                    val body = JSONObject()
                        .put("ok", true)
                        .put("xRequestedWith", request.requestHeaders["X-Requested-With"])
                        .toString()
                    val html = "<!doctype html><html><body>$body</body></html>"
                    return WebResourceResponse(
                        "text/html",
                        "utf-8",
                        ByteArrayInputStream(html.toByteArray()),
                    )
                }
            }
            webView.loadUrl("https://appassets.androidplatform.net/net/headers.json")
        }
    }

    @After
    fun tearDown() {
        instr.runOnMainSync { webView.destroy() }
    }

    @Test
    fun requestedWithHeaderDoesNotBreakRequests() = runBlocking<Unit> {
        withTimeout(5_000) { loaded.await() }
        val raw = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { c ->
                webView.evaluateJavascript("document.body.innerText") { c.resume(it ?: "null") }
            }
        }
        val out = when (val parsed = Json.parseToJsonElement(raw)) {
            is JsonPrimitive -> parsed.content
            else -> raw
        }
        Log.i(TAG, "X-Requested-With regression: $out")
        val json = Json.parseToJsonElement(out).jsonObject
        assertEquals(true, json["ok"]!!.jsonPrimitive.boolean)
        val header = (json["xRequestedWith"] as? JsonPrimitive)?.contentOrNull
        assertTrue("unexpected X-Requested-With: $header", header == null || header == ctx.packageName)
    }

    private companion object {
        const val TAG = "RequestedWithHeaderTest"
    }
}

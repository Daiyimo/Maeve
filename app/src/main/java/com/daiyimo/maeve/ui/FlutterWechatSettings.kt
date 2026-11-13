package com.daiyimo.maeve.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.settings.FeatureFlags
import com.daiyimo.maeve.wechat.WechatConnectionState
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/** 登录等待属于当前设置会话；应用后台微信监听属于 WechatRuntime。 */
internal class FlutterWechatSettings(private val app: MaeveApp, private val scope: CoroutineScope) {
    private var login: Job? = null
    private var qr: ByteArray? = null
    private var error: String? = null

    suspend fun action(action: String, enabled: Boolean?): Map<String, Any?> {
        val runtime = app.wechat
        when (action) {
            "snapshot" -> Unit
            "restartWorker" -> {
                check(com.daiyimo.maeve.cap.phone.a11y.ScreenAccessibility.instance.value != null) { "请先开启无障碍服务" }
                app.amooSetup.restartWechat()
                app.startActivity(android.content.Intent().setClassName("com.stepos.ailauncher", "com.stepos.ailauncher.chat.activity.ChatMainActivity")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "login" -> {
                check(runtime.store.account() == null) { "请先解绑当前微信" }
                if (login == null) {
                    error = null
                    val start = runtime.startLogin()
                    val key = checkNotNull(start.qrcode?.takeIf { it.isNotBlank() }) { "二维码响应不完整" }
                    val content = checkNotNull(start.qrContent?.takeIf { it.isNotBlank() }) { "二维码内容为空" }
                    qr = withContext(Dispatchers.Default) { png(content) }
                    login = scope.launch {
                        try {
                            if (runtime.awaitLogin(key) == null) error = "二维码已过期，请重新获取"
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "微信绑定未完成，请重试" }
                        finally { qr = null; login = null }
                    }
                }
            }
            "cancelLogin" -> { login?.cancelAndJoin(); login = null; qr = null; error = null }
            "setEnabled" -> withContext(Dispatchers.IO) { runtime.setEnabled(requireNotNull(enabled)) }
            "setExecution" -> withContext(Dispatchers.IO) {
                val value = requireNotNull(enabled)
                FeatureFlags.get(app).setBridgeAmoo(value)
                if (!value) FeatureFlags.get(app).setInterceptAmoo(false)
                runtime.relay.syncControl()
            }
            "unbind" -> {
                login?.cancelAndJoin(); qr = null; error = null
                withContext(Dispatchers.IO) { runtime.unbind() }
            }
            else -> throw IllegalArgumentException("未知微信操作")
        }
        val loginActive = login != null
        val image = qr
        val loginError = error
        return withContext(Dispatchers.IO) {
            mapOf(
                "bound" to (runtime.store.account() != null),
                "enabled" to runtime.store.enabled(),
                "executionEnabled" to FeatureFlags.get(app).bridgeAmoo(),
                "workerReady" to runtime.relay.workerReady(),
                "loggingIn" to loginActive, "qrPng" to image, "error" to loginError,
                "state" to when (runtime.state.value) {
                    WechatConnectionState.Connected -> "connected"
                    WechatConnectionState.Connecting -> "connecting"
                    WechatConnectionState.NeedsBinding -> "unbound"
                    WechatConnectionState.Off -> "off"
                    is WechatConnectionState.Failed -> "failed"
                },
            )
        }
    }

    private fun png(content: String): ByteArray {
        val matrix = encodeQr(content)
        val scale = maxOf(1, 320 / matrix.size)
        val size = matrix.size * scale
        val pixels = IntArray(size * size) { index ->
            if (matrix[index % size / scale, index / size / scale]) Color.BLACK else Color.WHITE
        }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        } finally { bitmap.recycle() }
    }
}

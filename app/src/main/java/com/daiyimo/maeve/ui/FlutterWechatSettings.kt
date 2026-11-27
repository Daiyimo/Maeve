package com.daiyimo.maeve.ui

import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.settings.FeatureFlags
import com.daiyimo.maeve.wechat.WechatConnectionState
import kotlinx.coroutines.*

/** 登录等待属于当前设置会话；应用后台微信监听属于 WechatRuntime。 */
internal class FlutterWechatSettings(private val app: MaeveApp, private val scope: CoroutineScope) {
    private var login: Job? = null
    private var qr: ByteArray? = null
    private var error: String? = null

    suspend fun action(action: String, enabled: Boolean?): Map<String, Any?> {
        val runtime = app.wechat
        when (action) {
            "snapshot" -> Unit
            "openAmoo" -> {
                check(!runtime.systemAmoo.active) { "请等当前 Amoo 回复完成" }
                runtime.systemAmoo.open()
            }
            "setBackground" -> runtime.systemAmoo.setBackground(requireNotNull(enabled))
            "login" -> {
                check(runtime.store.account() == null) { "请先解绑当前微信" }
                if (login == null) {
                    error = null
                    val start = runtime.startLogin()
                    val key = checkNotNull(start.qrcode?.takeIf { it.isNotBlank() }) { "二维码响应不完整" }
                    val content = checkNotNull(start.qrContent?.takeIf { it.isNotBlank() }) { "二维码内容为空" }
                    qr = withContext(Dispatchers.Default) { wechatQrPng(content) }
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
                runtime.syncControl()
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
                "amooReady" to runtime.systemAmoo.available,
                "transport" to if (runtime.systemAmoo.background) "system_amoo_background" else "system_amoo_foreground",
                "background" to runtime.systemAmoo.background,
                "backgroundReady" to runtime.systemAmoo.backgroundReady,
                "amooActive" to runtime.systemAmoo.active,
                "amooProgress" to runtime.systemAmoo.stage.value.label?.let { tr(it) },
                "relayError" to (runtime.systemAmoo.lastError ?: if (runtime.waitingToDeliver)
                    "Amoo 已生成回复，正在等待微信确认投递。Maeve 会自动重试。" else null),
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
}

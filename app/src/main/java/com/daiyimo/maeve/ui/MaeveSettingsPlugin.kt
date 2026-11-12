package com.daiyimo.maeve.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.app.NotificationManager
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.a11y.MaeveAccessibilityService
import com.daiyimo.maeve.core.model.ModelRole
import com.daiyimo.maeve.core.shell.BuiltinShellStatus
import com.daiyimo.maeve.settings.ProviderConfig
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI

/** Flutter settings operate the existing native stores, never a second engine. */
class MaeveSettingsPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler {
    private lateinit var app: MaeveApp
    private lateinit var channel: MethodChannel
    private var activity: ActivityPluginBinding? = null
    private val scope = MainScope()
    private val settingsMutex = Mutex()
    private val prefs get() = app.getSharedPreferences("flutter_appearance", Context.MODE_PRIVATE)
    private val models get() = FlutterModelSettings(app.providers)

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        app = binding.applicationContext as MaeveApp
        channel = MethodChannel(binding.binaryMessenger, "maeve/settings").also { it.setMethodCallHandler(this) }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scope.cancel()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method == "openAccess") {
            val host = activity?.activity ?: return result.error("no_activity", "A foreground activity is required", null)
            val action = call.argument<String>("target")
            val intent = when (action) {
                "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}"))
                "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                "pairing" -> {
                    app.phone.pairing.begin()
                    Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
                }
                "app" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                else -> return result.error("bad_args", "Unknown system settings target", null)
            }
            try { host.startActivity(intent); result.success(null) }
            catch (_: android.content.ActivityNotFoundException) {
                host.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                result.success(null)
            }
            return
        }
        scope.launch {
            try {
                val value = settingsMutex.withLock { withContext(Dispatchers.IO) {
                    when (call.method) {
                        "snapshot" -> snapshot()
                        "setAppearance" -> {
                            val edit = prefs.edit()
                            call.argument<String>("mode")?.let { require(it in setOf("dark", "light", "system")); edit.putString("mode", it) }
                            call.argument<String>("accent")?.let { require(it in setOf("reverie", "dolores", "maze", "pearl", "sublime", "graphite")); edit.putString("accent", it) }
                            for (key in listOf("haptics", "reduceTransparency")) call.argument<Boolean>(key)?.let { edit.putBoolean(key, it) }
                            check(edit.commit()) { "Unable to save appearance" }
                            snapshot()
                        }
                        "selectModel" -> {
                            val id = requireNotNull(call.argument<String>("id"))
                            models.select(id)
                            snapshot()
                        }
                        "saveModel" -> {
                            val input = FlutterModelInput.parse(call.arguments as? Map<*, *> ?: emptyMap<Any, Any>())
                            models.save(input)
                            snapshot()
                        }
                        else -> null
                    }
                } }
                if (value == null) result.notImplemented() else result.success(value)
            } catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { result.error("bad_args", e.message ?: "Invalid settings", null) }
            catch (_: Exception) { result.error("settings_failed", "Unable to read or save native settings", null) }
        }
    }

    private fun snapshot(): Map<String, Any?> {
        val current = app.providers.configFor(ModelRole.Main)
        val enabled = Settings.Secure.getString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = android.content.ComponentName(app, MaeveAccessibilityService::class.java)
        val a11y = enabled.split(':').any { it.equals(component.flattenToString(), true) || it.equals(component.flattenToShortString(), true) }
        return mapOf(
            "mode" to prefs.getString("mode", "dark"), "accent" to prefs.getString("accent", "reverie"),
            "haptics" to prefs.getBoolean("haptics", true), "reduceTransparency" to prefs.getBoolean("reduceTransparency", false),
            "currentModelId" to current?.let { FlutterModelSettings.id(it.providerId, it.model) }, "modelName" to current?.model,
            "models" to models.choices().map { it.toMap() },
            "presets" to FlutterModelSettings.presets(),
            "accessibility" to a11y, "overlay" to Settings.canDrawOverlays(app),
            "notifications" to app.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
            "helperRunning" to (app.phone.shellControl.status.value is BuiltinShellStatus.Running),
            "phoneSupported" to (android.os.Build.VERSION.SDK_INT >= 31),
        )
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) { activity = binding }
    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding }
    override fun onDetachedFromActivity() { activity = null }
}

/** Validates locally before any native configuration is changed. Secrets never enter a snapshot. */
internal data class FlutterModelInput(val name: String, val kind: ProviderConfig.Kind,
    val baseUrl: String, val model: String, val apiKey: String) {
    companion object {
        fun parse(values: Map<*, *>): FlutterModelInput {
            fun text(key: String) = (values[key] as? String).orEmpty().trim()
            val uri = try { URI(text("baseUrl")) } catch (_: Exception) { throw IllegalArgumentException("请输入有效的 API 地址") }
            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.rawQuery == null) { "请输入不含凭据、查询参数或片段的 HTTP(S) API 基础地址" }
            val kind = ProviderConfig.Kind.entries.firstOrNull { it.name == text("kind") }
            require(kind in setOf(ProviderConfig.Kind.OpenAiCompat, ProviderConfig.Kind.OpenAiResponses, ProviderConfig.Kind.Anthropic, ProviderConfig.Kind.Gemini)) { "请选择支持的接口协议" }
            require(text("model").isNotBlank() && text("model").length <= 256) { "请填写模型名称" }
            return FlutterModelInput(text("name").ifEmpty { uri.host }, kind!!,
                uri.toString().trimEnd('/'), text("model"), text("apiKey"))
        }
    }
}

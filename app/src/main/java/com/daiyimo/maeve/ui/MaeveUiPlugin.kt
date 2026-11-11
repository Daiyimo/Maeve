package com.daiyimo.maeve.ui

import android.content.Intent
import android.content.res.AssetManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.daiyimo.maeve.BuildConfig
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

/**
 * 宿主状态与应用设置入口:MethodChannel `maeve/ui` + EventChannel `maeve/ui/events`。
 *
 * 随 Flutter 引擎常驻(见 [MainActivity.provideFlutterEngine]),不随 Activity 重建:
 * 壳从最近任务划掉再回来、配置变更,状态流的订阅都不用重连。Activity detach 只解绑,
 * 不注销通道;下次 attach 自动接上。
 *
 * open_settings 需要一个前台 Activity 才能跳转;没有就报错,不会静默失败。
 */
class MaeveUiPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var methods: MethodChannel
    private lateinit var events: EventChannel
    private var activity: ActivityPluginBinding? = null
    private var sink: EventChannel.EventSink? = null
    private lateinit var assets: AssetManager
    private val scope = MainScope()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        assets = binding.applicationContext.assets
        methods = MethodChannel(binding.binaryMessenger, CHANNEL).also { it.setMethodCallHandler(this) }
        events = EventChannel(binding.binaryMessenger, EVENTS).also { it.setStreamHandler(this) }
        current = this
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methods.setMethodCallHandler(null)
        events.setStreamHandler(null)
        sink = null
        scope.cancel()
        if (current === this) current = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "open_settings" -> {
                val host = activity?.activity
                if (host == null) {
                    result.error("no_activity", "open_settings needs a foreground activity", null)
                } else {
                    host.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${host.packageName}")),
                    )
                    result.success(null)
                }
            }
            "status" -> result.success(hostStatus())
            "legal_notices" -> scope.launch {
                try {
                    val data = withContext(Dispatchers.IO) {
                        fun read(name: String) = assets.open(name).bufferedReader().use { it.readText() }
                        val entries = Licenses.ENTRIES + Licenses.runtimePackages(read("runtime/packages.json"))
                        mapOf(
                            "entries" to entries.map { mapOf("name" to it.name, "license" to it.license, "note" to it.note) },
                            "sourceOffer" to Licenses.GPL_SOURCE_OFFER,
                            "documents" to listOf("NOTICE.txt", "Apache-2.0.txt", "GPL-2.0.txt", "GPL-3.0.txt", "LGPL-3.0.txt", "Protocol-Buffers.txt", "SOURCE_OFFER.md")
                                .map { mapOf("name" to it, "text" to read("licenses/$it")) },
                        )
                    }
                    result.success(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    result.error("legal_notices", "Unable to read bundled notices", e.message)
                }
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        sink = events
        events.success(hostStatus())
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    /** 回前台刷一次状态(版本、构建、系统版本这类打开期间不会变,但设置里可能改过显示)。 */
    fun pushStatus() {
        sink?.success(hostStatus())
    }

    private fun hostStatus(): Map<String, Any> = mapOf(
        "version" to BuildConfig.VERSION_NAME,
        "build" to BuildConfig.GIT_HASH,
        "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT,
    )

    // ActivityAware:壳换实例(划掉重开、配置变更)时接着用同一个引擎里的这些通道

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding
    }

    override fun onDetachedFromActivityForConfigChanges() = onDetachedFromActivity()

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    companion object {
        const val CHANNEL = "maeve/ui"
        const val EVENTS = "maeve/ui/events"

        /**
         * 进程内单引擎,插件只有一个实例;[MainActivity.onResume] 靠它刷新状态流。
         * 不做事的话可以没有——留着是因为壳回前台时顶栏那行状态应该是最新的。
         */
        @Volatile
        var current: MaeveUiPlugin? = null
            private set
    }
}

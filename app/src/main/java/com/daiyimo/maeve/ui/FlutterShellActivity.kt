package com.daiyimo.maeve.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.daiyimo.maeve.BuildConfig
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * Opt-in Flutter workbench while Compose pages migrate screen by screen.
 * Launch with: adb shell am start -n com.daiyimo.maeve/.ui.FlutterShellActivity
 *
 * The bridge is deliberately small: it exposes host status and the app-settings entry.
 * Chat, model calls, persistence and agent execution are still the Android host's job and
 * are not wired into the Flutter shell yet.
 */
class FlutterShellActivity : FlutterActivity() {
    private var statusSink: EventChannel.EventSink? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        MethodChannel(messenger, "maeve/ui").setMethodCallHandler { call, result ->
            when (call.method) {
                "open_settings" -> {
                    openAppSettings()
                    result.success(null)
                }
                "status" -> result.success(hostStatus())
                else -> result.notImplemented()
            }
        }

        EventChannel(messenger, "maeve/ui/events").setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                    statusSink = events
                    events.success(hostStatus())
                }

                override fun onCancel(arguments: Any?) {
                    statusSink = null
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        statusSink?.success(hostStatus())
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")),
        )
    }

    private fun hostStatus(): Map<String, Any> = mapOf(
        "version" to BuildConfig.VERSION_NAME,
        "build" to BuildConfig.GIT_HASH,
        "android" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT,
    )
}

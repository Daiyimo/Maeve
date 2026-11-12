package com.daiyimo.maeve.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.daiyimo.maeve.MaeveApp
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import com.daiyimo.maeve.settings.FeatureFlags

@RunWith(AndroidJUnit4::class)
class SettingsBridgeDeviceTest {
    @Test fun amooEntryReadsTheNativeBridgeWithoutStartingSetup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        val app = context.applicationContext as MaeveApp
        val before = app.amooSetup.stage.value
        val response = CompletableFuture<Map<*, *>>()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val plugin = activity.provideFlutterEngine(activity).plugins.get(MaeveSettingsPlugin::class.java) as MaeveSettingsPlugin
                plugin.onMethodCall(MethodCall("amoo", mapOf("action" to "snapshot")), object : MethodChannel.Result {
                    override fun success(result: Any?) { response.complete(result as Map<*, *>) }
                    override fun error(code: String, message: String?, details: Any?) { response.completeExceptionally(AssertionError(code)) }
                    override fun notImplemented() { response.completeExceptionally(AssertionError("Amoo bridge is missing")) }
                })
            }
            val status = response.get(5, TimeUnit.SECONDS)
            assertEquals(before.name.lowercase(java.util.Locale.ROOT), status["stage"])
            assertEquals(FeatureFlags.get(context).bridgeAmoo(), status["bridgeEnabled"])
            assertTrue(status["installed"] is Boolean)
            assertEquals(before, app.amooSetup.stage.value)
            assertFalse(status.containsKey("apiKey"))
        }
    }

    @Test @SdkSuppress(minSdkVersion = 31)
    fun wirelessPairingUsesTheExistingDeveloperSettingsRoute() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        val launched = mutableListOf<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                launched += Intent(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
        }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            instrumentation.addMonitor(monitor)
            try {
                scenario.onActivity { activity ->
                    val plugin = activity.provideFlutterEngine(activity).plugins.get(MaeveSettingsPlugin::class.java) as MaeveSettingsPlugin
                    var completed = false
                    plugin.onMethodCall(MethodCall("openAccess", mapOf("target" to "pairing")), object : MethodChannel.Result {
                        override fun success(result: Any?) { completed = true }
                        override fun error(code: String, message: String?, details: Any?) { fail("Pairing navigation failed: $code") }
                        override fun notImplemented() { fail("Pairing navigation was not implemented") }
                    })
                    assertTrue(completed)
                }
                assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, launched.single().action)
                assertEquals("toggle_adb_wireless", launched.single().getStringExtra(":settings:fragment_args_key"))
            } finally {
                instrumentation.removeMonitor(monitor)
                (context.applicationContext as MaeveApp).phone.pairing.end()
            }
        }
    }
}

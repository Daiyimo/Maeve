package com.daiyimo.maeve.ui

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.settings.ApprovalStore
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExecutionBridgeDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as MaeveApp
    private fun call(scenario: ActivityScenario<MainActivity>, method: String, args: Map<String, Any> = emptyMap(), settings: Boolean = false): Any? {
        val result = CompletableFuture<Any?>()
        scenario.onActivity { activity ->
            val callback = object : MethodChannel.Result {
                override fun success(value: Any?) { result.complete(value) }
                override fun error(code: String, message: String?, details: Any?) { result.completeExceptionally(AssertionError(code)) }
                override fun notImplemented() { result.completeExceptionally(AssertionError("Missing method $method")) }
            }
            val plugins = activity.provideFlutterEngine(activity).plugins
            if (settings) (plugins.get(MaeveSettingsPlugin::class.java) as MaeveSettingsPlugin).onMethodCall(MethodCall(method, args), callback)
            else (plugins.get(MaeveChatPlugin::class.java) as MaeveChatPlugin).onMethodCall(MethodCall(method, args), callback)
        }
        return result.get(10, TimeUnit.SECONDS)
    }
    @Test fun overlayPermissionIsDeclaredSoMaeveAppearsInSystemSettings() {
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains(Manifest.permission.SYSTEM_ALERT_WINDOW))
    }
    @Test fun lightDefaultsAndSavedAppearanceAreReadFromTheNativeStore() {
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        val prefs = context.getSharedPreferences("flutter_appearance", android.content.Context.MODE_PRIVATE)
        val oldMode = prefs.getString("mode", null)
        val oldAccent = prefs.getString("accent", null)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            try {
                prefs.edit().remove("mode").remove("accent").commit()
                val defaults = call(scenario, "snapshot", settings = true) as Map<*, *>
                assertEquals("light", defaults["mode"])
                assertEquals("reverie", defaults["accent"])
                val changed = call(scenario, "setAppearance", mapOf("mode" to "dark", "accent" to "dolores"), settings = true) as Map<*, *>
                assertEquals("dark", changed["mode"])
                assertEquals("dolores", changed["accent"])
            } finally {
                prefs.edit().apply {
                    if (oldMode == null) remove("mode") else putString("mode", oldMode)
                    if (oldAccent == null) remove("accent") else putString("accent", oldAccent)
                }.commit()
            }
        }
    }
    @Test fun stepCatalogueUsageAndWelcomeShareNativeStateWithoutExposingKeys() {
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        val prefs = context.getSharedPreferences("flutter_appearance", android.content.Context.MODE_PRIVATE)
        val oldWelcome = if (prefs.contains("welcomeSeen")) prefs.getBoolean("welcomeSeen", false) else null
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            try {
                val snapshot = call(scenario, "snapshot", settings = true) as Map<*, *>
                val preset = (snapshot["presets"] as List<*>).single() as Map<*, *>
                assertEquals("阶跃星辰", preset["label"])
                assertEquals("https://api.stepfun.com/step_plan/v1", preset["planUrl"])
                assertFalse(preset.containsKey("apiKey"))
                val usage = call(scenario, "modelUsage", settings = true) as Map<*, *>
                assertEquals(30, usage["days"])
                assertTrue(((usage["totals"] as Map<*, *>)["input"] as Number).toLong() >= 0)
                assertEquals(true, (call(scenario, "completeOnboarding", settings = true) as Map<*, *>)["welcomeSeen"])
                assertTrue(prefs.getBoolean("welcomeSeen", false))
            } finally {
                prefs.edit().apply { if (oldWelcome == null) remove("welcomeSeen") else putBoolean("welcomeSeen", oldWelcome) }.commit()
            }
        }
    }
    @Test fun capsuleSwitchPersistsToNativeStateInBothDirections() {
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        val flags = com.daiyimo.maeve.settings.FeatureFlags.get(context)
        val original = flags.capsuleMode()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            try {
                val off = call(scenario, "setCapsuleEnabled", mapOf("enabled" to false), settings = true) as Map<*, *>
                assertEquals(false, off["capsuleEnabled"])
                assertEquals(com.daiyimo.maeve.capsule.CapsuleMode.Off, flags.capsuleMode())
                assertEquals(false, (call(scenario, "snapshot", settings = true) as Map<*, *>)["capsuleEnabled"])
                val on = call(scenario, "setCapsuleEnabled", mapOf("enabled" to true), settings = true) as Map<*, *>
                assertEquals(true, on["capsuleEnabled"])
                assertEquals(com.daiyimo.maeve.capsule.CapsuleMode.Always, flags.capsuleMode())
            } finally { flags.setCapsuleMode(original) }
        }
    }
    @Test fun paceChangesPersistToTheExistingNativeStore() {
        assumeTrue("Run without an active user task", app.taskState.runs.value.isEmpty())
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        val flags = com.daiyimo.maeve.settings.FeatureFlags.get(context)
        val original = flags.operatingPace()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            try {
                val saved = call(scenario, "setOperatingPace", mapOf("pace" to "fast"), settings = true) as Map<*, *>
                assertEquals("fast", saved["operatingPace"])
                assertEquals(com.daiyimo.maeve.core.cap.phone.OperatingPace.FAST, flags.operatingPace())
                val loaded = call(scenario, "snapshot", settings = true) as Map<*, *>
                assertEquals("fast", loaded["operatingPace"])
            } finally { flags.setOperatingPace(original) }
        }
    }
    @Test fun nativeApprovalModePersistsAndRemoteTasksKeepTheirOwnRules() {
        assumeTrue("Run without an active user task", app.taskState.runs.value.isEmpty())
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        val original = app.approvals.autonomous.value
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            fun set(enabled: Boolean): Map<*, *> {
                val result = CompletableFuture<Map<*, *>>()
                scenario.onActivity { activity ->
                    val plugin = activity.provideFlutterEngine(activity).plugins.get(MaeveSettingsPlugin::class.java) as MaeveSettingsPlugin
                    plugin.onMethodCall(MethodCall("setAutonomous", mapOf("enabled" to enabled)), object : MethodChannel.Result {
                        override fun success(value: Any?) { result.complete(value as Map<*, *>) }
                        override fun error(code: String, message: String?, details: Any?) { result.completeExceptionally(AssertionError(code)) }
                        override fun notImplemented() { result.completeExceptionally(AssertionError("Missing confirmation mode")) }
                    })
                }
                return result.get(10, TimeUnit.SECONDS)
            }
            set(false)
            val local = app.approvalControl.start()
            val remote = app.approvalControl.start(remote = true)
            try {
                assertEquals(true, set(true)["autonomous"])
                assertTrue(local.autonomous())
                assertFalse(remote.autonomous())
                assertTrue(ApprovalStore(File(context.filesDir, "approvals.json")).autonomous.value)
            } finally {
                app.approvalControl.end(local)
                app.approvalControl.end(remote)
                set(original)
            }
        }
    }
    @Test fun executionBridgeRejectsStaleActionsAndNeverReturnsOtherConversationsFrames() {
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            assertNull(call(scenario, "preview", mapOf("conversationId" to "missing-conversation")))
            assertEquals(false, call(scenario, "taskAction", mapOf("conversationId" to "missing-conversation", "action" to "approve", "key" to 42)))
            val missing = call(scenario, "runDetails", mapOf("runId" to "../settings")) as Map<*, *>
            assertEquals(false, missing["available"])
        }
    }
}

package com.daiyimo.maeve.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.settings.FeatureFlags
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WechatBridgeDeviceTest {
    @Test fun backgroundSystemAmooDisplayWhenExplicitlyRequested() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("backgroundAmoo") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val support = com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
        support.service()
        val shell = support.shell()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            val display = com.daiyimo.maeve.cap.phone.display.AgentDisplay(shell, 720, 1600, 320,
                context.getSystemService(android.hardware.display.DisplayManager::class.java))
            try {
                val before = shell.topTaskId(0)
                val result = shell.runSystemCommand(display.displayId,
                    "am start --display ${display.displayId} -n com.stepos.ailauncher/.chat.activity.ChatMainActivity")
                assertEquals(result.getString("output"), 0, result.getInt("exitCode"))
                val deadline = System.currentTimeMillis() + 12_000
                while (shell.packageOfTask(shell.topTaskId(display.displayId)) != "com.stepos.ailauncher" && System.currentTimeMillis() < deadline) Thread.sleep(100)
                assertEquals("System Amoo must be on its private display", "com.stepos.ailauncher", shell.packageOfTask(shell.topTaskId(display.displayId)))
                assertEquals("The user's main task must stay in front", before, shell.topTaskId(0))
                val service = com.daiyimo.maeve.cap.phone.a11y.ScreenAccessibility.instance.value!!
                val windows = service.windowsOn(display.displayId)
                assertTrue("Amoo must expose its UI on the background display", windows.any { w ->
                    val root = w.root
                    try { root?.packageName?.toString() == "com.stepos.ailauncher" } finally { root?.recycle() }
                })
            } finally { display.release() }
        }
    }
    @Test fun liveAmooChatDoesNotCallMaeveModelWhenExplicitlyRequested() = kotlinx.coroutines.runBlocking<Unit> {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveAmoo") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as MaeveApp
        val support = com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
        support.service()
        val background = InstrumentationRegistry.getArguments().getString("backgroundAmoo") == "true"
        val shell = if (background) support.shell() else null
        val original = app.wechat.systemAmoo.background
        check(app.wechat.store.enabled() && app.wechat.store.account() != null) { "Bind and enable WeChat first" }
        val peer = app.wechat.store.peer(app.wechat.store.account()!!.ownerId)
        val messageId = "live-check-${UUID.randomUUID()}"
        val id = com.daiyimo.maeve.wechat.WechatAmooRelay.requestId(peer, messageId)
        val root = File(context.filesDir, "wechat-system-amoo")
        val beforeCalls = app.usage.snapshot().rows.sumOf { it.counts.calls }
        val beforeTasks = app.remoteTasks.list(100).map { it.id }.toSet()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            try {
                instrumentation.runOnMainSync { app.wechat.systemAmoo.setBackground(background) }
                app.wechat.sync()
                val beforeMain = shell?.topTaskId(0)
                val started = System.currentTimeMillis()
                val text = "请你做一个简短的自我介绍，说明你是什么助手。"
                assertTrue(app.wechat.store.journal(messageId, peer, text))
                app.wechat.relay.forward(peer, messageId, text)
                val deadline = started + 150_000
                while (app.wechat.store.pending().any { p -> p.messageId == messageId } && System.currentTimeMillis() < deadline) {
                    if (shell != null) assertEquals("Background chat must not switch the user's main task", beforeMain, shell.topTaskId(0))
                    Thread.sleep(100)
                }
                assertFalse("The production delivery loop must confirm the actual WeChat send", app.wechat.store.pending().any { p -> p.messageId == messageId })
                assertEquals("The reply must belong to this request; error=${app.wechat.systemAmoo.lastError}", id,
                    app.getSharedPreferences("wechat_system_amoo", 0).getString("lastReplyRequest", null))
                val reply = app.getSharedPreferences("wechat_system_amoo", 0).getString("lastReply", "").orEmpty()
                assertTrue("System Amoo must introduce itself", reply.contains("Amoo", ignoreCase = true))
                assertFalse("Default ClawPhone agent must not answer", reply.contains("ClawPhone", ignoreCase = true))
                assertEquals("No Maeve model request may be made for WeChat chat", beforeCalls, app.usage.snapshot().rows.sumOf { it.counts.calls })
                assertEquals("Chat may not create a Maeve task", beforeTasks, app.remoteTasks.list(100).map { it.id }.toSet())
                android.util.Log.i("WechatLiveCheck", "background=$background deliveredMs=${System.currentTimeMillis() - started}")
            } finally {
                instrumentation.runOnMainSync { app.wechat.systemAmoo.setBackground(original) }
                app.wechat.store.acknowledge(messageId)
                File(root, "requests/$id.json").delete()
                File(root, "requests/$id.working").delete()
                File(root, "replies/$id.json").delete()
            }
        }
    }
    private fun call(scenario: ActivityScenario<MainActivity>, action: String, enabled: Boolean? = null): Map<*, *> {
        val response = CompletableFuture<Map<*, *>>()
        scenario.onActivity { activity ->
            val plugin = activity.provideFlutterEngine(activity).plugins.get(MaeveSettingsPlugin::class.java) as MaeveSettingsPlugin
            val args = mutableMapOf<String, Any>("action" to action)
            if (enabled != null) args["enabled"] = enabled
            plugin.onMethodCall(MethodCall("wechat", args), object : MethodChannel.Result {
                override fun success(result: Any?) { response.complete(result as Map<*, *>) }
                override fun error(code: String, message: String?, details: Any?) { response.completeExceptionally(AssertionError(code)) }
                override fun notImplemented() { response.completeExceptionally(AssertionError("WeChat bridge missing")) }
            })
        }
        return response.get(15, TimeUnit.SECONDS)
    }

    @Test fun snapshotDoesNotStartLoginAndExecutionSwitchPersistsIndependently() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        val app = context.applicationContext as MaeveApp
        val flags = FeatureFlags.get(context)
        val original = flags.bridgeAmoo()
        val intercept = flags.interceptAmoo()
        val receiving = app.wechat.store.enabled()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                val before = call(scenario, "snapshot")
                assertFalse(before["loggingIn"] as Boolean)
                assertNull(before["qrPng"])
                assertFalse(before.containsKey("token"))
                assertEquals(!original, call(scenario, "setExecution", !original)["executionEnabled"])
                assertEquals(!original, FeatureFlags.get(context).bridgeAmoo())
                assertEquals(receiving, app.wechat.store.enabled())
                scenario.recreate()
                assertEquals(!original, call(scenario, "snapshot")["executionEnabled"])
                assertFalse(call(scenario, "cancelLogin")["loggingIn"] as Boolean)
            }
        } finally {
            flags.setBridgeAmoo(original)
            flags.setInterceptAmoo(intercept)
            app.wechat.relay.syncControl()
        }
    }

    @Test fun disabledExecutionReturnsFailureInsteadOfStartingOrQueuingATask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as MaeveApp
        val flags = FeatureFlags.get(context)
        val original = flags.bridgeAmoo()
        val root = File(context.externalMediaDirs.first(), "amoo")
        val id = "device-test-${UUID.randomUUID()}"
        val input = File(root, "in/$id.json")
        val output = File(root, "out/$id.json")
        try {
            flags.setBridgeAmoo(false)
            app.amooBridge
            input.parentFile!!.mkdirs()
            input.writeText("""{"goal":"仅用于验证关闭执行时不会创建任务"}""")
            val deadline = System.currentTimeMillis() + 10_000
            while (!output.exists() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue("Disabled request must receive a rejection", output.exists())
            val result = kotlinx.serialization.json.Json.parseToJsonElement(output.readText()).toString()
            assertTrue(result.contains("\"status\":\"failed\""))
            assertTrue(result.contains("\"task_id\":null"))
            assertFalse(input.exists())
        } finally {
            input.delete(); output.delete(); File(root, "in/$id.claimed").delete()
            flags.setBridgeAmoo(original)
        }
    }
}

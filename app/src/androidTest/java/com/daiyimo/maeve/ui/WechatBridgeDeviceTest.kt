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
    @Test fun liveAmooChatDoesNotCallMaeveModelWhenExplicitlyRequested() = kotlinx.coroutines.runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveAmoo") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as MaeveApp
        check(app.wechat.store.enabled() && app.wechat.store.account() != null) { "Bind and enable WeChat first" }
        val peer = app.wechat.store.peer(app.wechat.store.account()!!.ownerId)
        val messageId = "live-check-${UUID.randomUUID()}"
        val id = com.daiyimo.maeve.wechat.WechatAmooRelay.requestId(peer, messageId)
        val root = File(context.externalMediaDirs.first(), "amoo/wechat")
        val reply = File(root, "replies/$id.json")
        val beforeCalls = app.usage.snapshot().rows.sumOf { it.counts.calls }
        val beforeTasks = app.remoteTasks.list(100).map { it.id }.toSet()
        try {
            app.wechat.relay.forward(peer, messageId, "这是微信聊天链路验收，不是手机操作任务。请由 Amoo 自己直接回答且只回答：微信中转已恢复。不要调用 Maeve，不要调用手机工具。")
            val deadline = System.currentTimeMillis() + 120_000
            while (!reply.isFile && System.currentTimeMillis() < deadline) Thread.sleep(200)
            assertTrue("Amoo must produce a real reply", reply.isFile)
            val value = kotlinx.serialization.json.Json.parseToJsonElement(reply.readText()) as kotlinx.serialization.json.JsonObject
            val text = (value["text"] as kotlinx.serialization.json.JsonPrimitive).content
            assertTrue("Amoo must answer the greeting itself", text.contains("微信中转已恢复"))
            assertEquals("No Maeve model request may be made for WeChat chat", beforeCalls, app.usage.snapshot().rows.sumOf { it.counts.calls })
            assertEquals("Chat may not create a Maeve task", beforeTasks, app.remoteTasks.list(100).map { it.id }.toSet())
            assertEquals(com.daiyimo.maeve.core.run.SendOutcome.Delivered,
                app.wechat.channel.send(peer, com.daiyimo.maeve.core.run.Delivery.Result("probe-$id", peer.threadId, text)))
        } finally {
            File(root, "requests/$id.json").delete()
            File(root, "requests/$id.working").delete()
            reply.delete()
        }
    }
    @Test fun repairWechatWorkerWhenExplicitlyRequested() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("repairAmoo") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val context = instrumentation.targetContext
        val app = context.applicationContext as MaeveApp
        val bound = app.wechat.store.account() != null
        val enabled = app.wechat.store.enabled()
        val heartbeat = File(context.externalMediaDirs.first(), "amoo/wechat/worker.json")
        fun workerPid() = Regex("\"pid\"\\s*:\\s*(\\d+)").find(heartbeat.readText())?.groupValues?.get(1)
        val previous = workerPid()
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        com.daiyimo.maeve.cap.phone.a11y.DeviceSupport.service()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            call(scenario, "restartWorker")
            val deadline = System.currentTimeMillis() + 120_000
            while ((!app.wechat.relay.workerReady() || workerPid() == previous) && System.currentTimeMillis() < deadline) Thread.sleep(500)
            assertTrue("Amoo must start the new worker", app.wechat.relay.workerReady())
            assertNotEquals(previous, workerPid())
            assertEquals(bound, app.wechat.store.account() != null)
            assertEquals(enabled, app.wechat.store.enabled())
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

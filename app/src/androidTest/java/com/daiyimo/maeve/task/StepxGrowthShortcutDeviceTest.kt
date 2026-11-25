package com.daiyimo.maeve.task

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.LinkPolicy
import com.daiyimo.maeve.core.trigger.TriggerPreset
import com.daiyimo.maeve.core.trigger.TriggerWindowState
import com.daiyimo.maeve.ui.FlutterScheduleSettings
import io.flutter.plugin.common.MethodCall
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepxGrowthShortcutDeviceTest {
    @Test fun repeatedRunsOpenGrowthPlanFromAccountUsingOneModelCallEach() {
        val app = ApplicationProvider.getApplicationContext<MaeveApp>()
        val pkg = LinkPolicy.STEPX_ACCOUNT_PACKAGE
        assumeTrue(runCatching { app.packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false))
        DeviceSupport.sh("am start -W -n ${app.packageName}/.ui.MainActivity")
        DeviceSupport.shell()
        DeviceSupport.service()
        assumeTrue(TriggerRuntime.blockedReason(app) == null && app.taskState.runs.value.isEmpty())
        repeat(2) { openGrowthPlan(app, pkg) }
    }

    @Test fun scheduledPresetReadsAnAlreadySignedDayWithoutAVisionModel() {
        val app = ApplicationProvider.getApplicationContext<MaeveApp>()
        val pkg = LinkPolicy.STEPX_ACCOUNT_PACKAGE
        assumeTrue(runCatching { app.packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false))
        DeviceSupport.sh("am start -W -n ${app.packageName}/.ui.MainActivity")
        DeviceSupport.shell()
        DeviceSupport.service()
        assumeTrue(TriggerRuntime.blockedReason(app) == null && app.taskState.runs.value.isEmpty())
        // On a real account, exercise the scheduled workflow only when the read-only pass sees today's receipt.
        assumeTrue(openGrowthPlan(app, pkg).contains("已签到"))
        val preset = TriggerPreset.AmooGrowthCheckIn
        val bridge = FlutterScheduleSettings(app)
        repeat(2) {
            bridge.change(MethodCall("schedules", mapOf("action" to "create", "preset" to preset.key, "time" to "09:00")))
        }
        val plan = TriggerRuntime.list(app).single { it.preset == preset }
        val thread = plan.threadId
        assertEquals("09:00", plan.calendarTime)
        assertEquals("Asia/Shanghai", plan.calendarZone)
        assertTrue(plan.fixedZone)
        try {
            testTasks.modelFactory = { _, _ -> null }
            testTasks.lightModelFactory = { _, _ -> null }
            testTasks.visionModelFactory = { _, _ -> null }
            assertEquals(preset, plan.preset)
            val reply = bridge.change(MethodCall("schedules", mapOf("action" to "runNow", "id" to plan.id)))
            assertEquals(null, reply["deferred"])
            assertTrue(DeviceSupport.pollUntil(30_000) {
                TriggerRuntime.snapshot(app).windows.firstOrNull { it.triggerId == plan.id && it.state == TriggerWindowState.Delivered }
            } != null)
            assertEquals("今天已签到。", ConversationStore.shared(app).turns(thread).last().outcome)
        } finally {
            app.taskState.runs.value.firstOrNull { it.conversationId == thread }?.let { TaskService.cancel(app, it.key) }
            DeviceSupport.pollUntil(10_000) { true.takeIf { app.taskState.runs.value.none { it.conversationId == thread } } }
            TriggerRuntime.remove(app, plan.id)
            testTasks.modelFactory = TaskEnvironment.defaultModelFactory
            testTasks.lightModelFactory = TaskEnvironment.defaultLightModelFactory
            testTasks.visionModelFactory = TaskEnvironment.defaultVisionModelFactory
            runBlocking { app.conversationDeleter.delete(thread) }
        }
    }

    private fun openGrowthPlan(app: MaeveApp, pkg: String): String {
        val thread = ConversationStore.shared(app).newConversation()
        val model = ScriptedModel().also { it.intent = "ACT" }
        try {
            testTasks.modelFactory = { _, _ -> model }
            testTasks.lightModelFactory = { _, _ -> null }
            testTasks.visionModelFactory = { _, _ -> null }
            TaskService.start(app, "打开 Amoo 成长计划，查看今天签到状态", thread)
            assertEquals(1, model.awaitCall().number)
            val start = SystemClock.elapsedRealtime()
            model.feed("phone_run", """{"commands":[
                {"op":"open_link","action":"${LinkPolicy.STEPX_ACCOUNT}","packageName":"$pkg"},
                {"op":"tap","targetText":"Amoo 成长计划"}
            ]}""")
            val next = model.awaitCall()
            val elapsed = SystemClock.elapsedRealtime() - start
            assertEquals(2, next.number)
            val result = next.messages.flatMap { it.parts }.filterIsInstance<Part.ToolResult>().last()
            val navigation = Json.parseToJsonElement(result.content).jsonObject
            val steps = navigation["steps"]?.jsonArray
            assertEquals("${navigation["error"]} / ${navigation["reason"]} / $steps", "2", navigation["completed"]?.jsonPrimitive?.content)
            assertTrue("Growth page lacks a check-in status", navigation["screen"].toString().contains("签到"))
            println("STEPX Growth Plan navigation: ${elapsed}ms")
            model.answer("已打开成长计划。")
            assertTrue(DeviceSupport.pollUntil(15_000) {
                true.takeIf { app.taskState.runs.value.none { it.conversationId == thread } }
            } == true)
            return navigation["screen"].toString()
        } finally {
            app.taskState.runs.value.firstOrNull { it.conversationId == thread }?.let { TaskService.cancel(app, it.key) }
            DeviceSupport.pollUntil(10_000) { true.takeIf { app.taskState.runs.value.none { it.conversationId == thread } } }
            testTasks.modelFactory = TaskEnvironment.defaultModelFactory
            testTasks.lightModelFactory = TaskEnvironment.defaultLightModelFactory
            testTasks.visionModelFactory = TaskEnvironment.defaultVisionModelFactory
            runBlocking { app.conversationDeleter.delete(thread) }
        }
    }
}

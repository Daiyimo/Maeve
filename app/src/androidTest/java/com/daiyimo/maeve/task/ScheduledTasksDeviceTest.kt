package com.daiyimo.maeve.task

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.core.trigger.TriggerWindowState
import com.daiyimo.maeve.ui.FlutterScheduleSettings
import io.flutter.plugin.common.MethodCall
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledTasksDeviceTest {
    @Test fun standaloneDailyPlanCanRunImmediatelyThroughTheNativeBridge() {
        val app = ApplicationProvider.getApplicationContext<MaeveApp>()
        DeviceSupport.sh("am start -W -n ${app.packageName}/.ui.MainActivity")
        assumeTrue(TriggerRuntime.blockedReason(app) == null && app.taskState.runs.value.isEmpty())
        val goal = "定时任务回归测试 ${UUID.randomUUID()}"
        val time = LocalTime.now().plusHours(3).format(DateTimeFormatter.ofPattern("HH:mm"))
        val bridge = FlutterScheduleSettings(app)
        bridge.change(MethodCall("schedules", mapOf("action" to "create", "goal" to goal, "time" to time)))
        val plan = TriggerRuntime.list(app).single { it.goal == goal }
        val model = ScriptedModel()
        try {
            assertTrue(plan.enabled)
            assertEquals(time, plan.calendarTime)
            testTasks.modelFactory = { _, _ -> model }
            testTasks.lightModelFactory = { _, _ -> null }
            testTasks.visionModelFactory = { _, _ -> null }
            val reply = bridge.change(MethodCall("schedules", mapOf("action" to "runNow", "id" to plan.id)))
            assertEquals(null, reply["deferred"])
            model.awaitCall()
            model.answer("定时任务测试已完成")
            val window = DeviceSupport.pollUntil(20_000) {
                TriggerRuntime.snapshot(app).windows.firstOrNull { it.triggerId == plan.id && it.state == TriggerWindowState.Delivered }
            } ?: error("Scheduled run did not return a delivered result")
            assertEquals("定时任务测试已完成", ConversationStore.shared(app).turns(plan.threadId).last().outcome)
            assertEquals(plan.nextAtMs, TriggerRuntime.list(app).single { it.id == plan.id }.nextAtMs)
            assertEquals(1, TriggerRuntime.snapshot(app).windows.count { it.triggerId == window.triggerId })
        } finally {
            app.taskState.runs.value.firstOrNull { it.conversationId == plan.threadId }?.let { TaskService.cancel(app, it.key) }
            DeviceSupport.pollUntil(10_000) { true.takeIf { app.taskState.runs.value.none { it.conversationId == plan.threadId } } }
            testTasks.modelFactory = TaskEnvironment.defaultModelFactory
            testTasks.lightModelFactory = TaskEnvironment.defaultLightModelFactory
            testTasks.visionModelFactory = TaskEnvironment.defaultVisionModelFactory
            TriggerRuntime.remove(app, plan.id)
            runBlocking { app.conversationDeleter.delete(plan.threadId) }
        }
    }
}

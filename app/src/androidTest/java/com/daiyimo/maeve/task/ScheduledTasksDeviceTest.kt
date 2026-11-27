package com.daiyimo.maeve.task

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
import com.daiyimo.maeve.core.model.ModelSession
import com.daiyimo.maeve.ui.FlutterScheduleSettings
import io.flutter.plugin.common.MethodCall
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledTasksDeviceTest {
    @Test fun manualAndAutomaticRunsKeepResultsInTheThreadWithoutReusingItsContext() {
        val app = ApplicationProvider.getApplicationContext<MaeveApp>()
        DeviceSupport.sh("am start -W -n ${app.packageName}/.ui.MainActivity")
        assumeTrue(TriggerRuntime.blockedReason(app) == null && app.taskState.runs.value.isEmpty())
        val token = UUID.randomUUID().toString()
        val answer = "测试已完成 $token"
        val goal = "请直接回复：$answer"
        val time = LocalTime.now().plusHours(3).format(DateTimeFormatter.ofPattern("HH:mm"))
        val bridge = FlutterScheduleSettings(app)
        bridge.change(MethodCall("schedules", mapOf("action" to "create", "goal" to goal, "time" to time)))
        val plan = TriggerRuntime.list(app).single { it.goal == goal }
        ScheduledTaskRunFixture(app, plan, answer, token).use { runs ->
            assertTrue(plan.enabled)
            assertEquals(time, plan.calendarTime)
            runs.install()
            val reply = bridge.change(MethodCall("schedules", mapOf("action" to "runNow", "id" to plan.id)))
            assertEquals(null, reply["deferred"])
            val manualSession = runs.completeRun(1)
            assertEquals(plan.nextAtMs, TriggerRuntime.list(app).single { it.id == plan.id }.nextAtMs)

            TriggerRuntime.schedule(app, plan.copy(nextAtMs = System.currentTimeMillis() - 1))
            TriggerRuntime.fire(app)
            val automaticSession = runs.completeRun(2)
            assertNotEquals(ModelSession.of(plan.threadId), manualSession)
            assertNotEquals(manualSession, automaticSession)
        }
    }
}

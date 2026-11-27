package com.daiyimo.maeve.task

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.core.trigger.TriggerDefinition
import com.daiyimo.maeve.core.trigger.TriggerPreset
import com.daiyimo.maeve.core.trigger.TriggerWindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TriggerReadinessDeviceTest {
    @Test fun dueTaskWaitsForUnlockWhenUnattendedAccessIsOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as MaeveApp
        assumeFalse(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        assumeFalse(app.deviceAccess.settings.enabled)
        assumeTrue(TriggerRuntime.blockedReason(context) == null)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val id = "test-locked-${System.nanoTime()}"
        val dueAt = System.currentTimeMillis() - 1
        val plan = TriggerDefinition(id, id, "锁屏等待测试", dueAt, preset = TriggerPreset.AmooGrowthCheckIn)
        try {
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN))
            val blocked = DeviceSupport.pollUntil(5_000) {
                TriggerRuntime.blockedReason(context, plan)?.takeIf { it == "locked" }
            }
            assertEquals("locked", blocked)
            TriggerRuntime.schedule(context, plan)
            TriggerRuntime.fire(context)
            assertTrue(TriggerRuntime.snapshot(context).windows.none { it.triggerId == id })
            assertEquals(dueAt, TriggerRuntime.list(context).single { it.id == id }.nextAtMs)
        } finally {
            TriggerRuntime.remove(context, id)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_WAKEUP)
        }
    }

    @Test fun openingAppDeliversOverdueReminderWithoutWaitingForAlarm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "test-overdue-${System.nanoTime()}"
        val dueAt = System.currentTimeMillis() - 1
        try {
            TriggerRuntime.schedule(context, TriggerDefinition(id, id, "闹钟测试", dueAt, reminderText = "闹钟测试"))
            TriggerRuntime.onAppVisible(context)
            val turn = DeviceSupport.pollUntil(5_000) {
                ConversationStore.shared(context).turns(id).singleOrNull()
            }
            assertEquals("reminder:$id:$dueAt", turn?.runId)
            assertTrue(TriggerRuntime.snapshot(context).triggers.none { it.id == id })
        } finally {
            TriggerRuntime.remove(context, id)
            ConversationStore.shared(context).delete(id)
        }
    }
}

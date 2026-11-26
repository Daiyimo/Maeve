package com.daiyimo.maeve.task

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.core.trigger.TriggerDefinition
import com.daiyimo.maeve.core.trigger.TriggerWindowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TriggerRuntimeDeviceTest {
    @Test fun lockScreenIsHandledByTaskGateNotTriggerRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            Thread.sleep(400)
            val reason = TriggerRuntime.blockedReason(context)
            assertTrue("lock must be handled by TaskGate, got $reason", reason == null || reason == "offline")
        } finally {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_WAKEUP)
        }
    }

    @Test fun dueTriggerCanBeFiredDirectlyOnReadiness() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "test-readiness-${System.nanoTime()}"
        val threadId = "test-readiness-thread-${System.nanoTime()}"
        try {
            TriggerRuntime.schedule(context, TriggerDefinition(
                id = id,
                threadId = threadId,
                goal = "就绪补跑测试",
                nextAtMs = System.currentTimeMillis() - 1,
                intervalMs = 60_000L,
                reminderText = "就绪补跑测试提醒",
            ))
            TriggerRuntime.fire(context)
            val window = TriggerRuntime.snapshot(context).windows.single { it.triggerId == id }
            assertEquals(TriggerWindowState.Delivered, window.state)
        } finally {
            TriggerRuntime.remove(context, id)
            ConversationStore.shared(context).delete(threadId)
        }
    }
}

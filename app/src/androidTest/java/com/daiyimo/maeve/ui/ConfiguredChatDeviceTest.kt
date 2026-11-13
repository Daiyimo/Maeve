package com.daiyimo.maeve.ui

import android.content.Intent
import android.app.UiAutomation
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.core.model.ModelRole
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in only: uses the device owner's configured model for one small task.
 * No key is read out of native storage, logged or copied to the test process.
 */
@RunWith(AndroidJUnit4::class)
class ConfiguredChatDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as MaeveApp

    private fun nodes(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (node == null) return@sequence
        yield(node)
        for (i in 0 until node.childCount) yieldAll(nodes(node.getChild(i)))
    }

    private fun windowNodes() = automation.rootInActiveWindow?.let { root ->
        if (root.packageName == context.packageName) nodes(root).toList() else emptyList()
    }.orEmpty()

    private fun waitFor(description: String, timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(80)
        }
        if (automation.rootInActiveWindow?.packageName == context.packageName) {
            automation.takeScreenshot()?.let { bitmap ->
                File(app.cacheDir, "task-monitor-failure.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
        fail(description)
    }

    private fun click(label: String) {
        var target: AccessibilityNodeInfo? = null
        waitFor("Missing Flutter control: $label") {
            target = windowNodes().firstOrNull { it.contentDescription?.toString() == label && it.isEnabled }
            target != null
        }
        // Custom Flutter controls can label a parent while its gesture action
        // belongs to a child. Tap the observed control as a user would.
        val bounds = Rect().also { requireNotNull(target).getBoundsInScreen(it) }
        val downAt = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue("Could not tap Flutter control: $label", automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    @Test fun configuredTaskFinishesAndSurvivesNativeSummaryUpdates() {
        assumeTrue("Opt in with -e maeve_verify_configured_chat true",
            InstrumentationRegistry.getArguments().getString("maeve_verify_configured_chat") == "true")
        assumeTrue("A native main model must already be configured", app.providers.configFor(ModelRole.Main) != null)
        assumeTrue("Run without an active user task", app.taskState.runs.value.isEmpty())
        val phoneTask = InstrumentationRegistry.getArguments().getString("maeve_verify_phone_task") == "true"
        val goal = if (phoneTask) "打开系统计算器，清空后计算37+58，告诉我结果。不要进行其他操作。" else "你好"
        val originalPace = com.daiyimo.maeve.settings.FeatureFlags.get(context).operatingPace()
        if (phoneTask) com.daiyimo.maeve.settings.FeatureFlags.get(context).setOperatingPace(com.daiyimo.maeve.core.cap.phone.OperatingPace.FAST)
        FlutterLaunchDeviceTest().prepareInteractiveDisplay()
        automation // Keep the already-authorized accessibility service active during UI checks.
        val lease = if (phoneTask) app.phone.sharedShell.acquire() else null
        var verificationConversationId: String? = null
        try {
            if (lease != null) {
                DeviceSupport.awaitShell(lease)
                DeviceSupport.service()
            }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                click("新对话")
                waitFor("The native chat did not switch to a fresh conversation") {
                    app.chat.ui.value.exchanges.isEmpty() && app.chat.ui.value.composer == ComposerMode.Idle
                }
                verificationConversationId = app.chat.ui.value.conversationId
                waitFor("The fresh conversation input was not available") {
                    val input = windowNodes().firstOrNull { it.className == "android.widget.EditText" }
                    input?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                }
                waitFor("The conversation input did not receive focus") {
                    windowNodes().any { it.className == "android.widget.EditText" && it.isFocused }
                }
                waitFor("The greeting was not entered into the Flutter input") {
                    val input = windowNodes().firstOrNull { it.className == "android.widget.EditText" && it.isFocused }
                    if (input?.text?.toString() == goal) return@waitFor true
                    input?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, goal) })
                    false // Wait for Flutter to acknowledge the edit, rather than Android's action dispatch.
                }
                // A multiline goal changes the composer's height while the IME opens.
                // Wait for geometry to settle before tapping its observed bounds.
                automation.waitForIdle(250, 3_000)
                click("发送消息")
                waitFor("The greeting did not reach the native chat") {
                    assertTrue("Native prerequisites blocked the configured greeting", app.chat.ui.value.blocked.isNullOrEmpty())
                    app.chat.ui.value.exchanges.isNotEmpty()
                }
                var sawPreview = false
                var sawSteps = false
                waitFor("The configured model did not finish its task", if (phoneTask) 180_000 else 60_000) {
                    val exchange = app.chat.ui.value.exchanges.lastOrNull()
                    val status = exchange?.status
                    if (phoneTask) {
                        val labels = windowNodes().mapNotNull { it.contentDescription?.toString() }
                        if (!sawPreview && labels.any { it.contains("实时执行画面") }) {
                            sawPreview = true
                            automation.takeScreenshot()?.let { bitmap ->
                                File(app.cacheDir, "task-monitor-device.png").outputStream().use {
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                                }
                                bitmap.recycle()
                            }
                        }
                        sawSteps = sawSteps || exchange?.steps?.any { step -> labels.any { it.contains(step.title) } } == true
                    }
                    if (status is ExchangeStatus.Failed) fail("The configured provider returned a task failure")
                    app.chat.ui.value.composer == ComposerMode.Idle && status is ExchangeStatus.Done
                }
                if (phoneTask) {
                    assertTrue("The phone task's real steps must be visible while executing", sawSteps)
                    assertTrue("The phone task's actual execution frame must appear in Flutter", sawPreview)
                    val runId = requireNotNull(app.chat.ui.value.exchanges.last().runId)
                    val store = com.daiyimo.maeve.record.RunStore(app)
                    waitFor("Completed phone input must retain its exact pointer evidence") {
                        store.events(runId).any { it is com.daiyimo.maeve.core.record.RunEvent.Pointer && it.screenshotId != null }
                    }
                    val pointers = store.events(runId).filterIsInstance<com.daiyimo.maeve.core.record.RunEvent.Pointer>()
                    assertTrue(pointers.all { it.width > 0 && it.height > 0 && (it.method != "touch" || (it.x != null && it.y != null)) })
                    assertTrue(pointers.filter { it.screenshotId != null }.all { store.screen(runId, it.screenshotId!!) != null })
                    val lastFrame = store.events(runId).filterIsInstance<com.daiyimo.maeve.core.record.RunEvent.Media>().last()
                    val file = requireNotNull(store.screen(runId, lastFrame.mediaId))
                    val bitmap = requireNotNull(android.graphics.BitmapFactory.decodeFile(file.absolutePath))
                    val visible = kotlinx.coroutines.runBlocking { com.daiyimo.maeve.cap.phone.a11y.PhoneOcr.read(bitmap) }
                    // The large result, not an old answer in the smaller history line.
                    val number = visible.filter { it.bounds.size == 4 && Regex("[\\d,.\\s]+" ).matches(it.text) }
                        .maxByOrNull { it.bounds[3] - it.bounds[1] }?.text?.replace(",", "")?.replace(" ", "")?.toBigDecimalOrNull()
                    assertTrue("The actual calculator result must be 95 rather than trusting the model's claim", number?.compareTo(95.toBigDecimal()) == 0)
                }
                val ui = app.chat.ui.value
                val id = requireNotNull(ui.conversationId)
                val answer = (ui.exchanges.last().status as ExchangeStatus.Done).answer
                assertTrue("The model returned an empty greeting", answer.isNotBlank())
                fun renderedReply() = windowNodes().any {
                    it.text?.toString()?.contains(answer) == true || it.contentDescription?.toString()?.contains(answer) == true
                }
                waitFor("Native completion must appear in the Flutter conversation") { renderedReply() }
                try {
                    // A real ConversationStore change emits exactly the metadata event
                    // that previously wiped the displayed turns after completion.
                    app.conversations.setPinned(id, true)
                    automation.waitForIdle(250, 3_000)
                    assertTrue("A summary refresh erased the rendered reply", renderedReply())
                    assertFalse("A completed native live turn still displays Stop", windowNodes().any {
                        it.contentDescription?.toString() == "停止生成"
                    })
                } finally {
                    app.conversations.setPinned(id, false)
                }
            }
        } finally {
            if (verificationConversationId != null && app.chat.ui.value.conversationId == verificationConversationId &&
                app.chat.ui.value.composer != ComposerMode.Idle) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { app.chat.cancel() }
            }
            if (phoneTask) com.daiyimo.maeve.settings.FeatureFlags.get(context).setOperatingPace(originalPace)
            if (lease != null) app.phone.sharedShell.release()
        }
    }
}

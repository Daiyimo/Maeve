package com.daiyimo.maeve.task

import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
import com.daiyimo.maeve.confirm.ConfirmationCopy
import com.daiyimo.maeve.confirm.ConfirmationNotifier
import com.daiyimo.maeve.core.agent.TaskState
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.Role
import com.daiyimo.maeve.core.remote.RemoteStatus
import com.daiyimo.maeve.core.run.RunOrigin
import com.daiyimo.maeve.settings.FeatureFlags
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AmooFileApprovalDeviceTest {
    private enum class Reply { Automatic, Approve, Decline }

    @Test fun fileRequestWaitsForPhoneConfirmationAndReturnsTheDeclinedResult() = verifyReply(Reply.Decline)

    @Test fun fileRequestResumesOnPhoneApprovalAndReturnsTheAnswer() = verifyReply(Reply.Approve)

    @Test fun fileRequestUsesAutonomousModeForWeChatNavigation() = verifyReply(Reply.Automatic)

    private fun verifyReply(choice: Reply) {
        val app = ApplicationProvider.getApplicationContext<MaeveApp>()
        assumeTrue(FeatureFlags.get(app).bridgeAmoo())
        assumeTrue(app.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        assumeTrue(app.taskState.runs.value.isEmpty())
        DeviceSupport.sh("am start -W -n ${app.packageName}/.ui.MainActivity")
        DeviceSupport.shell()
        DeviceSupport.service()

        val mailbox = File(app.externalMediaDirs.first(), "amoo")
        val inbox = File(mailbox, "in").apply { mkdirs() }
        val id = "approval-test-" + UUID.randomUUID()
        val request = File(inbox, "$id.json")
        val pending = File(inbox, "$id.tmp")
        val output = File(mailbox, "out/$id.json")
        fun receipt() = runCatching { Json.parseToJsonElement(output.readText()).jsonObject }.getOrNull()

        val model = ScriptedModel()
        val goal = "文件桥本机确认测试 $id"
        val originalMode = app.approvals.autonomous.value
        val modeFile = File(app.filesDir, "approvals.json")
        val hadModeFile = modeFile.exists()
        var run: TaskRun? = null
        try {
            if (originalMode != (choice == Reply.Automatic)) app.approvalControl.setAutonomous(choice == Reply.Automatic)
            testTasks.modelFactory = { _, _ -> model }
            testTasks.lightModelFactory = { _, _ -> null }
            testTasks.visionModelFactory = { _, _ -> null }
            pending.writeText("""{"goal":"$goal"}""")
            check(pending.renameTo(request))

            model.awaitCall()
            model.feed("phone_launch_app", """{"package":"com.tencent.mm"}""")
            model.awaitCall()
            model.feed("phone_observe", "{}")
            model.awaitCall()
            // WeChat reapplies its previous page on launch. Back is safe from any of those pages.
            model.feed("phone_back")
            run = app.taskState.runs.value.first { it.goal == goal }
            assertEquals(RunOrigin.Amoo, run.provenance.origin)
            if (choice != Reply.Automatic) {
                val confirmation = DeviceSupport.pollUntil(20_000) { run.state.value as? TaskState.WaitingConfirmation }
                    ?: error("Amoo did not enter on-phone confirmation: ${run.state.value}")
                assertEquals("com.tencent.mm", confirmation.request.app)
                val reply = DeviceSupport.pollUntil(5_000) {
                    receipt()?.takeIf { it["waiting_for"]?.jsonPrimitive?.content == "confirmation" }
                } ?: error("Amoo mailbox did not show confirmation")
                assertEquals(RemoteStatus.RUNNING, reply.getValue("status").jsonPrimitive.content)
                val notificationText = ConfirmationCopy.body(confirmation.request, ConfirmationNotifier.appLabel(app, confirmation.request.app))
                val notification = DeviceSupport.pollUntil(5_000) {
                    app.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull {
                        it.notification.channelId == TaskService.CHANNEL_ATTENTION && it.notification.actions?.size == 2 &&
                            it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == notificationText
                    }
                }?.notification ?: error("Maeve did not post an approval notification")
                // Only the test-generated request is answered. The approval path uses Android Back.
                assertTrue(run.state.value === confirmation)
                notification.actions[if (choice == Reply.Approve) 0 else 1].actionIntent.send()
            }
            val toolAnswer = model.awaitCall().messages.last { it.role == Role.Tool }
                .parts.filterIsInstance<Part.ToolResult>().single()
            val expected: String
            val status: String
            if (choice != Reply.Decline) {
                assertFalse(toolAnswer.content, toolAnswer.isError)
                expected = if (choice == Reply.Automatic) "已自动执行微信返回操作。" else "本机已同意，已执行返回操作。"
                status = RemoteStatus.DONE
                model.answer(expected)
            } else {
                assertTrue(toolAnswer.content, "confirmation_declined" in toolAnswer.content)
                expected = "本机已拒绝这一步。"
                status = RemoteStatus.FAILED
                model.feed("end_not_done", """{"message":"$expected"}""")
            }
            val result = DeviceSupport.pollUntil(20_000) {
                receipt()?.takeIf { it["status"]?.jsonPrimitive?.content == status }
            } ?: error("Amoo did not receive the final result")
            assertEquals(expected, result.getValue(if (choice == Reply.Decline) "note" else "answer").jsonPrimitive.content)
        } finally {
            val started = run ?: app.taskState.runs.value.firstOrNull { it.goal == goal }
            started?.let {
                if (it.state.value !is TaskState.Terminal) TaskService.cancel(app, it.key)
                DeviceSupport.pollUntil(10_000) { true.takeIf { _ -> it !in app.taskState.runs.value } }
                DeviceSupport.pollUntil(5_000) { true.takeUnless { File(inbox, "$id.claimed").exists() } }
            }
            testTasks.modelFactory = TaskEnvironment.defaultModelFactory
            testTasks.lightModelFactory = TaskEnvironment.defaultLightModelFactory
            testTasks.visionModelFactory = TaskEnvironment.defaultVisionModelFactory
            if (app.approvals.autonomous.value != originalMode) app.approvalControl.setAutonomous(originalMode)
            if (!hadModeFile) modeFile.delete()
            if (started != null && started.state.value is TaskState.Terminal) {
                runBlocking { app.conversationDeleter.delete(started.conversationId) }
            }
            request.delete()
            pending.delete()
            output.delete()
        }
    }
}

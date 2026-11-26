package com.daiyimo.maeve.task

import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daiyimo.maeve.cap.phone.a11y.AndroidScreen
import com.daiyimo.maeve.cap.phone.a11y.DeviceSupport
import com.daiyimo.maeve.cap.phone.display.AgentDisplay
import com.daiyimo.maeve.cap.phone.display.FrameMath
import com.daiyimo.maeve.core.tool.LinkPolicy
import com.daiyimo.maeve.core.uistate.ActionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StepxGrowthDisplayDeviceTest {
    @Test fun growthPlanStaysOnItsScreenWhenNoOtherScreenHasAmoo() = openGrowthPlan(withChat = false)

    @Test fun growthPlanReturnsToItsScreenWhenAmooChatIsOnAnotherScreen() = openGrowthPlan(withChat = true)

    private fun openGrowthPlan(withChat: Boolean) = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packages = setOf(LinkPolicy.STEPX_ACCOUNT_PACKAGE, LinkPolicy.AMOO_GROWTH_PACKAGE)
        assumeTrue(packages.all { pkg ->
            runCatching { context.packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)
        })
        val shell = DeviceSupport.shell()
        val service = DeviceSupport.service()
        val main = Display.DEFAULT_DISPLAY
        val mainTask = shell.topTaskId(main)
        assumeTrue("Amoo or the account page is already in use",
            shell.packageOfTask(mainTask) !in packages && packages.none { shell.displayOfPackage(it) >= 0 })
        val dm = context.getSystemService(DisplayManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        dm.getDisplay(main).getRealMetrics(metrics)
        val chat = if (withChat) AgentDisplay(shell, 720, 1600, 320, dm) else null
        try {
            val chatTask = chat?.let { display ->
                DeviceSupport.sh("am start -W --display ${display.displayId} -n ${LinkPolicy.AMOO_GROWTH_PACKAGE}/.chat.activity.ChatMainActivity")
                DeviceSupport.pollUntil(5_000) {
                    shell.topTaskId(display.displayId).takeIf { shell.topActivity(display.displayId) == CHAT }
                } ?: error("Amoo chat did not open on the other virtual display")
            }
            val growth = AgentDisplay(shell, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, dm)
            try {
                val screen = AndroidScreen(service, shell, DeviceSupport.ready(growth))
                val opened = screen.openLink(LinkPolicy.STEPX_ACCOUNT, null, LinkPolicy.STEPX_ACCOUNT_PACKAGE)
                assertTrue("account page did not open: $opened", opened is ActionResult.Ok)
                val account = screen.snapshot()
                val button = account.elements.firstOrNull { it.name.contains("成长计划") }
                assertNotNull("account page did not expose the growth button", button)
                val task = shell.topTaskId(growth.displayId)
                assertTrue(task >= 0)
                val tapped = screen.tapRef(button!!.ref, account.snapshotId)
                assertTrue("growth button action failed: $tapped", tapped is ActionResult.Ok)
                assertEquals("growth plan was reported as a main-screen launch", null, (tapped as ActionResult.Ok).opened)

                val rendered = DeviceSupport.pollUntil(8_000) {
                    val page = runBlocking { screen.snapshot() }
                    if (page.packageName != LinkPolicy.AMOO_GROWTH_PACKAGE || page.protected ||
                        page.elements.none { it.name.contains("签到") }) return@pollUntil null
                    growth.latestFrameBitmap()?.let { frame ->
                        try {
                            true.takeUnless { FrameMath.isBlack(frame.width, frame.height, frame::getPixel) }
                        } finally {
                            frame.recycle()
                        }
                    }
                }
                assertNotNull("growth plan did not render on its original display; task $task is on display ${shell.displayOfTask(task)}", rendered)
                assertEquals(task, shell.topTaskId(growth.displayId))
                assertEquals(GROWTH, shell.topActivity(growth.displayId))
                if (chat != null) {
                    assertEquals(chatTask, shell.topTaskId(chat.displayId))
                    assertEquals(CHAT, shell.topActivity(chat.displayId))
                }
                assertEquals(mainTask, shell.topTaskId(main))
            } finally {
                growth.release()
            }
        } finally {
            chat?.release()
        }
    }

    private companion object {
        const val CHAT = "${LinkPolicy.AMOO_GROWTH_PACKAGE}.chat.activity.ChatMainActivity"
        const val GROWTH = "${LinkPolicy.AMOO_GROWTH_PACKAGE}.task.space.RepairWebActivity"
    }
}

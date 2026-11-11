package com.daiyimo.maeve.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.flutter.embedding.android.FlutterView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlutterLaunchDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun views(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) yieldAll(views(root.getChildAt(i)))
    }

    private fun assertFlutterFrame(scenario: ActivityScenario<MainActivity>) {
        // Keep only this test Activity awake; never change the device's timeout.
        scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val deadline = SystemClock.uptimeMillis() + 15_000
        var rendered = false
        do {
            scenario.onActivity { activity ->
                val tree = views(activity.window.decorView).toList()
                val flutter = tree.filterIsInstance<FlutterView>().single()
                assertFalse(tree.any { it.javaClass.name.startsWith("androidx.compose.ui.platform.") })
                rendered = flutter.hasRenderedFirstFrame()
            }
            if (!rendered) SystemClock.sleep(50)
        } while (!rendered && SystemClock.uptimeMillis() < deadline)
        assertTrue("The app must render a Flutter frame, not just create a FlutterView", rendered)
    }

    @Test fun directMainLaunchContainsFlutterAndNoComposeRoot() {
        // Historical visual-spec flags must not switch an app launch back to Compose.
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_VISUAL_SPEC, true)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            assertFlutterFrame(scenario)
            scenario.recreate()
            assertFlutterFrame(scenario)
        }
    }

    @Test fun installedLauncherAndCompatibilityComponentsResolveToMain() {
        val manager = context.packageManager
        val launch = manager.getLaunchIntentForPackage(context.packageName)!!
        val launcher = manager.getActivityInfo(launch.component!!, 0)
        assertEquals(MainActivity::class.java.name, launcher.targetActivity ?: launcher.name)
        val compatibility = manager.getActivityInfo(
            ComponentName(context.packageName, FlutterShellActivity::class.java.name),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertEquals(MainActivity::class.java.name, compatibility.targetActivity)
    }
}

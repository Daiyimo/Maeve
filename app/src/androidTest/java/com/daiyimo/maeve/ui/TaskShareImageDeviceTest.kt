package com.daiyimo.maeve.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.provider.MediaStore
import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskShareImageDeviceTest {
    @Test fun downloadBridgeSavesThePngToDownloads() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val png = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            val result = CompletableFuture<Any?>()
            scenario.onActivity { activity ->
                val plugin = requireNotNull(activity.provideFlutterEngine(activity).plugins.get(MaeveUiPlugin::class.java))
                    as MaeveUiPlugin
                plugin.onMethodCall(MethodCall("downloadTaskImage", png), object : MethodChannel.Result {
                    override fun success(value: Any?) { result.complete(value) }
                    override fun error(code: String, message: String?, details: Any?) {
                        result.completeExceptionally(AssertionError(code))
                    }
                    override fun notImplemented() { result.completeExceptionally(AssertionError("Missing download bridge")) }
                })
            }
            assertEquals(true, result.get(15, TimeUnit.SECONDS))
            val saved = context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("Maeve-%.png"),
                "${MediaStore.Downloads.DATE_ADDED} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0),
                    )
                    try {
                        assertArrayEquals(png, context.contentResolver.openInputStream(uri)?.use { it.readBytes() })
                        true
                    } finally {
                        context.contentResolver.delete(uri, null, null)
                    }
                } else false
            } ?: false
            assertTrue(saved)
        }
    }

    @Test fun shareBridgeOpensAnImageChooserWithAReadablePng() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val png = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=",
            Base64.DEFAULT,
        )
        val outgoing = CompletableFuture<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                @Suppress("DEPRECATION")
                outgoing.complete(requireNotNull(intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)))
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                val result = CompletableFuture<Any?>()
                scenario.onActivity { activity ->
                    val plugin = requireNotNull(activity.provideFlutterEngine(activity).plugins.get(MaeveUiPlugin::class.java))
                        as MaeveUiPlugin
                    plugin.onMethodCall(MethodCall("shareTaskImage", png), object : MethodChannel.Result {
                        override fun success(value: Any?) { result.complete(value) }
                        override fun error(code: String, message: String?, details: Any?) {
                            result.completeExceptionally(AssertionError(code))
                        }
                        override fun notImplemented() { result.completeExceptionally(AssertionError("Missing share bridge")) }
                    })
                }
                assertEquals(true, result.get(15, TimeUnit.SECONDS))
                val send = outgoing.get(15, TimeUnit.SECONDS)
                assertEquals(Intent.ACTION_SEND, send.action)
                assertEquals("image/png", send.type)
                assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                val uri = requireNotNull(send.clipData?.getItemAt(0)?.uri)
                assertEquals("content", uri.scheme)
                assertArrayEquals(png, context.contentResolver.openInputStream(uri)?.use { it.readBytes() })
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}

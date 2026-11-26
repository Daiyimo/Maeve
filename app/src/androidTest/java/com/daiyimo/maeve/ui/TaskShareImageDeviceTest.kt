package com.daiyimo.maeve.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val png = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=",
        Base64.DEFAULT,
    )

    @Test fun downloadBridgePublishesOnePngInThePhotoAlbum() {
        val resolver = context.contentResolver
        val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val imagesBefore = exportedPngs(resolver, images, png.size)
        val downloadsBefore = exportedPngs(resolver, downloads, png.size)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
            .use { assertEquals(true, export(it, "downloadTaskImage")) }
        val newImages = exportedPngs(resolver, images, png.size) - imagesBefore
        val newDownloads = exportedPngs(resolver, downloads, png.size) - downloadsBefore
        try {
            assertTrue("Found an export in Downloads", newDownloads.isEmpty())
            assertEquals("One tap should publish one image", 1, newImages.size)
            val uri = newImages.single()
            val columns = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.IS_PENDING)
            requireNotNull(resolver.query(uri, columns, null, null, null)).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("${Environment.DIRECTORY_PICTURES}/Maeve/", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
            assertArrayEquals(png, resolver.openInputStream(uri)?.use { it.readBytes() })
        } finally {
            for (uri in (newImages + newDownloads).distinctBy { it.lastPathSegment }) {
                val isFixture = resolver.openInputStream(uri)?.use { it.readBytes().contentEquals(png) } == true
                if (isFixture) resolver.delete(uri, null, null)
            }
        }
    }

    private fun exportedPngs(resolver: ContentResolver, collection: Uri, size: Int): Set<Uri> =
        requireNotNull(resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.SIZE} = ?",
            arrayOf("Maeve-%.png", size.toString()), null,
        )).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(ContentUris.withAppendedId(collection, cursor.getLong(0))) }
        }

    @Test fun shareBridgeOpensAnImageChooserWithAReadablePng() {
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
                assertEquals(true, export(scenario, "shareTaskImage"))
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

    private fun export(scenario: ActivityScenario<MainActivity>, method: String): Any? {
        val result = CompletableFuture<Any?>()
        scenario.onActivity { activity ->
            val plugin = activity.provideFlutterEngine(activity).plugins.get(MaeveUiPlugin::class.java) as MaeveUiPlugin
            plugin.onMethodCall(MethodCall(method, png), object : MethodChannel.Result {
                override fun success(value: Any?) { result.complete(value) }
                override fun error(code: String, message: String?, details: Any?) {
                    result.completeExceptionally(AssertionError(code))
                }
                override fun notImplemented() { result.completeExceptionally(AssertionError("Missing $method bridge")) }
            })
        }
        return result.get(15, TimeUnit.SECONDS)
    }
}

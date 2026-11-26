package com.daiyimo.maeve.ui

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.daiyimo.maeve.chat.AttachFilesProvider
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object TaskShareImage {
    private val pngHeader = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private const val maxBytes = 16 * 1024 * 1024
    private const val keepMillis = 24 * 60 * 60 * 1000L

    private fun validPng(png: ByteArray?): Boolean =
        png != null && png.size in pngHeader.size + 1..maxBytes &&
            pngHeader.indices.all { png[it] == pngHeader[it] }

    suspend fun download(activity: Activity?, png: ByteArray?): Boolean {
        if (activity == null || !validPng(png)) return false
        val image = requireNotNull(png)
        return try {
            withContext(Dispatchers.IO) {
                val resolver = activity.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Maeve-${System.currentTimeMillis()}.png")
                    put(MediaStore.Downloads.MIME_TYPE, "image/png")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Maeve/")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext false
                var completed = false
                try {
                    val output = resolver.openOutputStream(uri) ?: return@withContext false
                    output.use { it.write(image) }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    completed = true
                    true
                } finally {
                    if (!completed) resolver.delete(uri, null, null)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun open(activity: Activity?, png: ByteArray?): Boolean {
        if (activity == null || !validPng(png)) return false
        val image = requireNotNull(png)

        var file: File? = null
        var shared = false
        try {
            val image = withContext(Dispatchers.IO) {
                val dir = File(activity.cacheDir, "attach/task-share")
                dir.mkdirs()
                val expired = System.currentTimeMillis() - keepMillis
                dir.listFiles()?.filter { it.isFile && it.lastModified() < expired }?.forEach { it.delete() }
                File.createTempFile("Maeve-", ".png", dir).also {
                    file = it
                    it.writeBytes(png)
                }
            }
            if (activity.isFinishing || activity.isDestroyed) return false
            val uri = FileProvider.getUriForFile(activity, AttachFilesProvider.authority(activity.packageName), image)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(image.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(send, null))
            shared = true
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        } finally {
            if (!shared) file?.delete()
        }
    }
}

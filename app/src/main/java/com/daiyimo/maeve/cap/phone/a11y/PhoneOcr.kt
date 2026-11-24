package com.daiyimo.maeve.cap.phone.a11y

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.daiyimo.maeve.core.risk.NumberMask
import com.daiyimo.maeve.core.uistate.VisibleTextLine
import com.daiyimo.maeve.core.uistate.normalize
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.ensureActive
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 两套内置模型均在本机运行；同一帧并行识别，完成后才释放位图。两套都出错时抛异常(不当成没字)。 */
internal object PhoneOcr {
    private const val TAG = "PhoneOcr"

    // OCR can outlive its waiting coroutine. Keep the ML Kit lease until the callback finishes.
    // A concurrent read is unavailable because cached coordinates can belong to another frame or tile.
    private val busy = AtomicBoolean(false)

    private val chinese: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val latin: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun read(bitmap: Bitmap): List<VisibleTextLine> = readFresh(bitmap).orEmpty()

    suspend fun readFresh(bitmap: Bitmap): List<VisibleTextLine>? {
        try {
            val full = recognize(bitmap.copy(Bitmap.Config.ARGB_8888, false)) ?: return null
            // Small navigation labels often disappear at full-screen scale. Only dense/small-text
            // frames get bounded overlapping Chinese tiles; never enlarge every frame.
            val small = full.filter { (it.bounds[3] - it.bounds[1]) * bitmap.height / 999 < 32 }
            if (bitmap.width < 600 || small.size < 3) return full
            val detailed = mutableListOf<VisibleTextLine>()
            // Magnify the least certain lines individually. Tight crops keep adjacent labels
            // and icons from influencing the second pass on dense, tall screens.
            val candidates = small.sortedWith(compareBy<VisibleTextLine> { it.confidence ?: 1f }
                .thenBy { it.bounds[1] }.thenBy { it.bounds[0] }).take(4)
            for (candidate in candidates) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val bounds = candidate.bounds
                val top = (bounds[1] * bitmap.height / 999 - 8).coerceAtLeast(0)
                val bottom = (bounds[3] * bitmap.height / 999 + 8).coerceAtMost(bitmap.height)
                val left = (bounds[0] * bitmap.width / 999 - 10).coerceAtLeast(0)
                val right = (bounds[2] * bitmap.width / 999 + 10).coerceAtMost(bitmap.width)
                val scale = minOf(4.5, 1280.0 / maxOf(right - left, bottom - top))
                if (scale < 1.5) continue
                val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val enlarged = Bitmap.createScaledBitmap(crop, (crop.width * scale).toInt(), (crop.height * scale).toInt(), true)
                if (crop !== bitmap && crop !== enlarged) crop.recycle()
                val lines = try { recognize(enlarged, chineseOnly = true) ?: continue }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { continue }
                detailed += lines.map { line -> line.copy(bounds = listOf(
                    normalize(left + line.bounds[0] * (right - left - 1) / 999, bitmap.width),
                    normalize(top + line.bounds[1] * (bottom - top - 1) / 999, bitmap.height),
                    normalize(left + line.bounds[2] * (right - left - 1) / 999, bitmap.width),
                    normalize(top + line.bounds[3] * (bottom - top - 1) / 999, bitmap.height))) }
            }
            // Keep a better region reading instead of two contradictory labels at the same spot.
            return detailed + full.filterNot { old -> detailed.any { fresh ->
                overlaps(old.bounds, fresh.bounds) && (fresh.confidence ?: 0f) >= (old.confidence ?: 0f)
            } }
        } finally { bitmap.recycle() }
    }

    private fun overlaps(a: List<Int>, b: List<Int>): Boolean {
        val intersection = (minOf(a[2], b[2]) - maxOf(a[0], b[0])).coerceAtLeast(0).toLong() *
            (minOf(a[3], b[3]) - maxOf(a[1], b[1])).coerceAtLeast(0)
        val area = (a[2] - a[0]).toLong() * (a[3] - a[1])
        return area > 0 && intersection.toDouble() / area >= .7
    }

    private suspend fun recognize(bitmap: Bitmap, chineseOnly: Boolean = false): List<VisibleTextLine>? {
        if (!busy.compareAndSet(false, true)) {
            bitmap.recycle()
            return null
        }
        val recognizers = try { if (chineseOnly) listOf(chinese) else listOf(chinese, latin) } catch (e: Exception) {
            bitmap.recycle(); busy.set(false)
            throw e
        }
        val image = try { InputImage.fromBitmap(bitmap, 0) } catch (e: Exception) {
            bitmap.recycle(); busy.set(false)
            throw e
        }
        return suspendCancellableCoroutine { continuation ->
            val remaining = AtomicInteger(recognizers.size)
            val succeeded = AtomicInteger(0)
            val lines = Collections.synchronizedList(mutableListOf<VisibleTextLine>())

            fun finish(result: Text?) {
                if (result != null) succeeded.incrementAndGet()
                result?.textBlocks?.forEach { block -> block.lines.forEach { line ->
                    val box = line.boundingBox ?: return@forEach
                    // 银行卡号、身份证号打码后再交出去(认屏文字和用户屏幕两条路都经这里)
                    val value = NumberMask.mask(line.text.trim())
                    // 有的识别模型不给置信度(为 0),当作没有
                    if (value.isNotEmpty()) lines += VisibleTextLine(value, listOf(
                        normalize(box.left, bitmap.width), normalize(box.top, bitmap.height),
                        normalize(box.right, bitmap.width), normalize(box.bottom, bitmap.height),
                    ), line.confidence.takeIf { it > 0f })
                } }
                if (remaining.decrementAndGet() == 0) {
                    val complete = lines.toList()
                    bitmap.recycle()
                    busy.set(false)
                    // 两套都出错不是「屏上没字」:照实报错,读屏结果才写得出 ocr: failed
                    if (continuation.isActive) {
                        if (succeeded.get() == 0) continuation.resumeWithException(IllegalStateException("text recognition failed"))
                        else continuation.resume(complete)
                    }
                }
            }

            for (recognizer in recognizers) try {
                val task: Task<Text> = recognizer.process(image)
                task.addOnCompleteListener { done ->
                    if (!done.isSuccessful) Log.w(TAG, "recognizer failed", done.exception)
                    finish(if (done.isSuccessful) done.result else null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "recognizer failed", e)
                finish(null)
            }
        }
    }
}

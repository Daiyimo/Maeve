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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 两套内置模型均在本机运行；同一帧并行识别，完成后才释放位图。两套都出错时抛异常(不当成没字)。 */
internal object PhoneOcr {
    private const val TAG = "PhoneOcr"

    private val chinese: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val latin: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun read(bitmap: Bitmap): List<VisibleTextLine> {
        try {
            val full = recognize(bitmap.copy(Bitmap.Config.ARGB_8888, false))
            // Small navigation labels often disappear at full-screen scale. Only dense/small-text
            // frames get bounded overlapping Chinese tiles; never enlarge every frame.
            val small = full.count { (it.bounds[3] - it.bounds[1]) * bitmap.height / 999 < 32 }
            if (bitmap.width < 600 || small < 3) return full
            val detailed = mutableListOf<VisibleTextLine>()
            // Some decorated labels survive better at overview scale than at native scale.
            if (bitmap.width > 900) try {
                detailed += recognize(Bitmap.createScaledBitmap(bitmap, 720, bitmap.height * 720 / bitmap.width, true), chineseOnly = true)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Keep the successful native read. */ }
            for (band in 0..3) for (column in 0..1) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val top = (band * bitmap.height / 4 - 48).coerceAtLeast(0)
                val bottom = ((band + 1) * bitmap.height / 4 + 48).coerceAtMost(bitmap.height)
                val left = (column * bitmap.width / 2 - 32).coerceAtLeast(0)
                val right = ((column + 1) * bitmap.width / 2 + 32).coerceAtMost(bitmap.width)
                val scale = minOf(3.0, 1280.0 / maxOf(right - left, bottom - top))
                if (scale <= 1.0) continue
                val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                val enlarged = Bitmap.createScaledBitmap(crop, (crop.width * scale).toInt(), (crop.height * scale).toInt(), true)
                if (crop !== bitmap && crop !== enlarged) crop.recycle()
                val lines = try { recognize(enlarged, chineseOnly = true) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { continue } // A refinement failure must not erase a valid full-frame read.
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

    private suspend fun recognize(bitmap: Bitmap, chineseOnly: Boolean = false): List<VisibleTextLine> {
        val recognizers = try { if (chineseOnly) listOf(chinese) else listOf(chinese, latin) } catch (e: Exception) {
            bitmap.recycle()
            throw e
        }
        val image = try { InputImage.fromBitmap(bitmap, 0) } catch (e: Exception) {
            bitmap.recycle()
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

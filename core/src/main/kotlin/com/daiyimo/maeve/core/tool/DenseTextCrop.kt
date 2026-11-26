package com.daiyimo.maeve.core.tool

import com.daiyimo.maeve.core.visual.Recall
import com.daiyimo.maeve.core.visual.Shots
import com.daiyimo.maeve.core.vision.ImageOps
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Formula-like OCR is often the least reliable text on a phone screenshot. When a screen result
 * contains a cluster of such lines, attach a source-pixel crop automatically so the model does not
 * have to notice the ambiguity and ask for one.
 */
internal object DenseTextCrop {
    private val KEYWORDS = Regex(
        "(?i)(ascii|checksum|formula|cipher|array|体验码|验证码|校验|数组|密文|数列|公式|规则|解码|偏移)",
    )
    private const val MIN_LINES = 3
    private const val PADDING = 18

    fun attach(result: ToolExecution, ops: ImageOps?, shots: Shots, step: Int): ToolExecution {
        if (ops == null || result.images.isEmpty()) return result
        val screen = result.content["screen"] as? JsonObject
        val lines = (screen?.get("visibleText") as? JsonArray) ?: (result.content["visibleText"] as? JsonArray)
            ?: (screen?.get("elements") as? JsonArray) ?: return result
        val matches = lines.mapNotNull { it as? JsonObject }.mapNotNull { line ->
            val text = (line["text"] as? JsonPrimitive)?.contentOrNull
                ?: (line["name"] as? JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            if (!KEYWORDS.containsMatchIn(text)) return@mapNotNull null
            val box = line["bounds"] as? JsonArray ?: return@mapNotNull null
            if (box.size != 4) return@mapNotNull null
            val values = box.map { (it as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null }
            if (values[0] !in 0..999 || values[1] !in 0..999 || values[2] !in 0..999 || values[3] !in 0..999 ||
                values[2] <= values[0] || values[3] <= values[1]) return@mapNotNull null
            text to values
        }
        if (matches.size < MIN_LINES) return result

        val left = (matches.minOf { it.second[0] } - PADDING).coerceAtLeast(0)
        val top = (matches.minOf { it.second[1] } - PADDING).coerceAtLeast(0)
        val right = (matches.maxOf { it.second[2] } + PADDING).coerceAtMost(999)
        val bottom = (matches.maxOf { it.second[3] } + PADDING).coerceAtMost(999)
        if (right <= left || bottom <= top) return result
        val crop = try { ops.crop(result.images.first(), intArrayOf(left, top, right, bottom)) } catch (e: Exception) { return result }
        val id = shots.next()
        val app = (screen?.get("currentApp") as? JsonPrimitive)?.contentOrNull
        shots.remember(Recall(id, step, app, crop, matches.joinToString("\n") { it.first }))
        val ids = result.imageIds.toMutableList().apply {
            while (size < result.images.size) add(null)
            add(id)
        }
        val note = buildJsonObject {
            put("screenshotId", id)
            put("reason", "dense_text")
            put("region", JsonArray(listOf(left, top, right, bottom).map(::JsonPrimitive)))
        }
        return result.with(
            images = result.images + crop,
            imageIds = ids,
            content = JsonObject(result.content + ("evidenceCrop" to note)),
        )
    }
}

package com.daiyimo.maeve.core.guard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 工具结果的指纹(不含每次读取都会变化的编号、截图和状态元数据)。 */
internal fun resultFingerprint(content: JsonObject): Long {
    val text = JsonObject(content.filterKeys { it !in VOLATILE_FIELDS }).toString()
    var h = -0x340d631b7bdddcdbL
    for (b in text.toByteArray(Charsets.UTF_8)) h = (h xor (b.toLong() and 0xff)) * 0x100000001b3L
    return h
}

/** 动作结果能证明的屏幕指纹;盲树合并语义 OCR,视觉优先页面没有元素时用 OCR/正文,不能把空数组当成所有屏幕。 */
internal fun screenFingerprint(content: JsonObject, elements: JsonArray?): Long? {
    val screen = content["screen"] as? JsonObject
    val visibleText = screen?.get("visibleText") as? JsonArray ?: content["visibleText"] as? JsonArray
    val treeQuality = (screen?.get("treeQuality") as? JsonPrimitive)?.contentOrNull
        ?: (content["treeQuality"] as? JsonPrimitive)?.contentOrNull
    val textHash = if (elements.isNullOrEmpty() || treeQuality in setOf("partial", "unusable")) visibleTextHash(visibleText) else null
    val base = when {
        !elements.isNullOrEmpty() -> elementsHash(elements)
        screen != null -> resultFingerprint(JsonObject(screen.filterKeys { it != "visibleText" }))
        "visibleText" in content -> textHash
        "elements" in content -> resultFingerprint(content)
        else -> null
    }
    return when {
        base != null && textHash != null -> mix(base, textHash)
        base != null -> base
        else -> null
    }
}

/** OCR 只取文字内容:坐标抖动、行序和截图编号不代表界面状态变化。 */
private fun visibleTextHash(lines: JsonArray?): Long? {
    val texts = lines.orEmpty().mapNotNull { line ->
        val value = (line as? JsonObject)?.get("text") ?: line as? JsonPrimitive
        (value as? JsonPrimitive)?.contentOrNull?.let(::semanticOcrText)
    }.distinct().sorted()
    if (texts.isEmpty()) return null
    var h = -0x340d631b7bdddcdbL
    for (b in texts.joinToString("\u0000").toByteArray(Charsets.UTF_8)) h = (h xor (b.toLong() and 0xff)) * 0x100000001b3L
    return h
}

private val NETWORK_TEXT = Regex("""(?i)\d+(?:\.\d+)?\s*[kmgt]?b?/s|\d+(?:\.\d+)?\s*ms\b""")
private val CLOCK_TEXT = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")

/** 网速/时钟是瞬态 token;含业务标签的时间必须保留,不能把整行业务文字删掉。 */
private fun semanticOcrText(raw: String): String? {
    val text = raw.trim().replace(Regex("\\s+"), " ").lowercase()
    val withoutNetwork = NETWORK_TEXT.replace(text, " ")
    if (withoutNetwork != text) {
        return withoutNetwork.trim().takeIf { it.any(Char::isLetter) }
    }
    if (CLOCK_TEXT.containsMatchIn(text) && CLOCK_TEXT.replace(text, "").none(Char::isLetter)) return null
    return text.takeIf { it.isNotEmpty() }
}

private fun mix(a: Long, b: Long): Long {
    var h = -0x340d631b7bdddcdbL
    for (v in listOf(a, b)) for (shift in 0..56 step 8) {
        val byte = (v ushr shift).toInt() and 0xff
        h = (h xor byte.toLong()) * 0x100000001b3L
    }
    return h
}

/** 页面读取的指纹;没有元素表时整份可读内容也算进展。 */
internal fun readFingerprint(content: JsonObject, elements: JsonArray?): Long =
    screenFingerprint(content, elements) ?: resultFingerprint(content)

private val VOLATILE_FIELDS = setOf(
    "snapshotId", "base", "screenshotId", "shotId", "mediaId", "guard", "advice", "moreTools",
    "imageUnchanged", "ocr", "readMode", "treeQuality", "hint",
)

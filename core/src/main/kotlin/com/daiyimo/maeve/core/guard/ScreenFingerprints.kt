package com.daiyimo.maeve.core.guard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** 工具结果的指纹(不含每次读取都会变化的编号、截图和状态元数据)。 */
internal fun resultFingerprint(content: JsonObject): Long {
    val text = JsonObject(content.filterKeys { it !in VOLATILE_FIELDS }).toString()
    var h = -0x340d631b7bdddcdbL
    for (b in text.toByteArray(Charsets.UTF_8)) h = (h xor (b.toLong() and 0xff)) * 0x100000001b3L
    return h
}

/** 动作结果能证明的屏幕指纹;视觉优先页面没有元素时用 OCR/正文,不能把空数组当成所有屏幕。 */
internal fun screenFingerprint(content: JsonObject, elements: JsonArray?): Long? {
    if (!elements.isNullOrEmpty()) return elementsHash(elements)
    val screen = content["screen"] as? JsonObject
    if (screen != null) return resultFingerprint(screen)
    if ("visibleText" in content || "elements" in content) return resultFingerprint(content)
    return null
}

/** 页面读取的指纹;没有元素表时整份可读内容也算进展。 */
internal fun readFingerprint(content: JsonObject, elements: JsonArray?): Long =
    screenFingerprint(content, elements) ?: resultFingerprint(content)

private val VOLATILE_FIELDS = setOf(
    "snapshotId", "screenshotId", "shotId", "mediaId", "guard", "advice", "moreTools",
    "imageUnchanged", "ocr", "readMode", "treeQuality", "hint",
)

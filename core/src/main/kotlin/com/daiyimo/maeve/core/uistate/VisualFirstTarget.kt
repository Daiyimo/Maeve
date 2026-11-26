package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.run.SKIPPED_FOR_NEW_MESSAGE
import com.daiyimo.maeve.core.run.waitOrSteer
import com.daiyimo.maeve.core.tool.failCode
import com.daiyimo.maeve.core.tool.notSent
import com.daiyimo.maeve.core.vision.LocateResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 视觉优先页没有树:element 只按 OCR 原文定位,未命中立即失败,不再调用第二遍视觉模型。 */
internal suspend fun resolveVisualFirstTarget(
    session: ScreenSession, args: JsonObject, shown: String, role: String?, field: Boolean,
): Described {
    val message = "Visual-first mode has no accessibility element list. Use x+y with target from the latest screenshot, " +
        "or phone_find_visually for icons."
    if (role != null || field) return Described.Failed(notSent("visual_first", message))
    val result = waitOrSteer { session.locateVisualText(shown) ?: LocateResult.NotFound("visual_first", message) }
        ?: return Described.Failed(failCode(SKIPPED_FOR_NEW_MESSAGE))
    if (result is LocateResult.NotFound) return Described.Failed(notSent("visual_first", message))
    val hit = result as LocateResult.Found
    session.locatedInfo[hit.ref] = Located(shown, null, hit.seen, hit.unverified)
    return Described.Resolved(
        JsonObject(args.filterKeys { it != DESCRIBED_ELEMENT && it != DESCRIBED_ROLE } +
            mapOf("ref" to JsonPrimitive(hit.ref), "snapshotId" to JsonPrimitive(hit.snapshotId))),
        null,
        JsonObject(listOfNotNull("resolvedFrom" to JsonPrimitive(shown), "foundBySight" to JsonPrimitive(true),
            hit.seen?.let { "seen" to JsonPrimitive(it) }).toMap()),
    )
}

package com.daiyimo.maeve.core.record

import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.PlaceKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 旧运行记录里的两个事件种类已经改名。这里只在解码时做一层兼容转换,磁盘上的
 * 行保持原样:
 *
 * - `screen_text`(过去用来记读到的界面文字)换成一条 `observed` 观察。地点取
 *   当时的站点或 App,观察编号补 0;
 * - `screen_saved`(过去用来记存下的画面)换成 `media`。
 */
internal object LegacyEvents {
    fun upgrade(e: JsonElement): JsonElement {
        val original = e as? JsonObject ?: return e
        return when (original.string("type")) {
            "screen_text" -> convertScreenText(original)
            "screen_saved" -> convertScreenSaved(original)
            else -> original
        }
    }

    private fun convertScreenText(old: JsonObject): JsonObject = buildJsonObject {
        put("type", "observed")
        old.copyTo(this, "atMs")
        old.copyTo(this, "step")
        put("kind", ObsKind.Read.wire)

        val siteName = old.string("site")
        val appName = old.string("app")
        val located = siteName?.let { PlaceKind.Site to it }
            ?: appName?.let { PlaceKind.App to it }
        if (located != null) {
            val (kind, name) = located
            put("place", buildJsonObject {
                put("kind", kind.wire)
                put("name", name)
            })
            // 网页一侧过去只报站点,手机一侧只报 App
            put("source", if (kind == PlaceKind.Site) "web" else "phone")
        }

        put("text", old.stringItems("names").joinToString("\n"))
        old.copyTo(this, "inputs")
        old.copyTo(this, "focused")
    }

    private fun convertScreenSaved(old: JsonObject): JsonObject = buildJsonObject {
        put("type", "media")
        old.copyTo(this, "atMs")
        old.copyTo(this, "screenshotId", asName = "mediaId")
        old.copyTo(this, "step")
        old.copyTo(this, "app", asName = "place")
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.stringItems(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private fun JsonObject.copyTo(
        target: JsonObjectBuilder,
        key: String,
        asName: String = key,
    ) {
        this[key]?.let { target.put(asName, it) }
    }
}

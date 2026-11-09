package com.daiyimo.maeve.core.uistate

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 差异文字超过全量的这个比例就发全量:差异省不下多少,还更难读。 */
private const val MAX_DIFF_RATIO = 0.5
private const val DEFAULT_MAX_ELEMENTS = 200

/**
 * 两屏之间的差异(按稳定编号对齐),写进写操作结果的 `screen` 字段:
 * `{"snapshotId","base","currentApp","full":false,"added":[元素],"removed":[{ref,name,role}],"changed":[元素+"was"],"unchanged":N}`。
 * 没有 base、[baseVisible] 为假(模型已看不到差异要依赖的那一屏)、前台 App 变了、或差异文字超过全量一半时,发全量:
 * `{"snapshotId","currentApp","full":true,"elements":[…],"truncated"}`。
 * 差异按两屏的完整元素列表算;输出时 added/changed 合计不超过 [maxElements] 个,超出的标 truncated。
 * 手机快照有窗口树时,全量结果带 tree,差异结果带 treeChanges;调用方给手机传的是完整元素数。
 */
object InterfaceUpdate {
    fun between(base: ModelSnapshot?, current: ModelSnapshot, baseVisible: Boolean, maxElements: Int = DEFAULT_MAX_ELEMENTS): JsonObject {
        val now = ScreenJson.elements(current.screen, Int.MAX_VALUE).first
        fun whole() = buildJsonObject {
            put("snapshotId", current.snapshotId)
            put("currentApp", current.port.packageName)
            put("full", true)
            flags(current)
            put("elements", JsonArray(now.take(maxElements)))
            put("truncated", now.size > maxElements)
            if (current.screen.windows.isNotEmpty()) put("tree", UiTreeJson.windows(current.screen))
        }
        if (base == null || !baseVisible || base.port.packageName != current.port.packageName) return whole()
        if (current.screen.windows.isNotEmpty() && !UiTreeJson.sameWindows(base.screen, current.screen)) return whole()
        val prior = ScreenJson.elements(base.screen, Int.MAX_VALUE).first.associateBy(::refOf)
        val present = now.associateBy(::refOf)
        val added = now.filter { refOf(it) !in prior }
        val removed = prior.filter { it.key !in present }.values.map(::removedEntry)
        val changed = now.mapNotNull { e ->
            val old = prior[refOf(e)] ?: return@mapNotNull null
            if (old == e) return@mapNotNull null
            val was = (old as JsonObject)["name"]
            if (was != (e as JsonObject)["name"]) JsonObject(e + ("was" to (was ?: JsonPrimitive("")))) else e
        }
        val diffChars = JsonArray(added).toString().length + JsonArray(removed).toString().length + JsonArray(changed).toString().length
        if (diffChars > now.toString().length * MAX_DIFF_RATIO) return whole()
        val treeDiff = if (current.screen.windows.isEmpty()) null else UiTreeJson.changes(base.screen, current.screen)
        if (treeDiff != null && treeDiff.toString().length > UiTreeJson.windows(current.screen).toString().length * MAX_DIFF_RATIO) return whole()
        val listedAdded = added.take(maxElements)
        val listedChanged = changed.take((maxElements - listedAdded.size).coerceAtLeast(0))
        return buildJsonObject {
            put("snapshotId", current.snapshotId)
            put("base", base.snapshotId)
            put("currentApp", current.port.packageName)
            put("full", false)
            flags(current)
            put("added", JsonArray(listedAdded))
            put("removed", JsonArray(removed))
            put("changed", JsonArray(listedChanged))
            put("unchanged", now.size - added.size - changed.size)
            if (listedAdded.size + listedChanged.size < added.size + changed.size) put("truncated", true)
            treeDiff?.let { put("treeChanges", it) }
        }
    }

    private fun JsonObjectBuilder.flags(s: ModelSnapshot) {
        if (s.port.changedDuringRead) put("screenChangedDuringRead", true)
    }

    /** 已消失元素压成 {ref,name?,role?} 摘要,只保留定位所需字段。 */
    private fun removedEntry(e: JsonElement) = buildJsonObject {
        val o = e as JsonObject
        put("ref", refOf(e))
        o["name"]?.let { put("name", it) }
        o["role"]?.let { put("role", it) }
    }

    /** 元素的稳定编号,差异对齐就按它配对。 */
    private fun refOf(e: JsonElement) = ((e as JsonObject)["ref"] as JsonPrimitive).content
}

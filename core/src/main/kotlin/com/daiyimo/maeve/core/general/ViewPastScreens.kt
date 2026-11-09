package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Concurrency
import com.daiyimo.maeve.core.tool.RECALL_KEEP
import com.daiyimo.maeve.core.tool.RECALL_MAX_IDS
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.visual.Recall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 回看之前几次读屏(哪块屏的都行,共用同一个画面编号空间)。能看图的主模型拿到当时的截图,
 * 看不到图的只给当时的元素文字;编号找不到时如实说缺了哪些,不当成手机操作失败。
 */
internal object ViewPastScreens : AgentTool {
    override val verb = Verb.ViewPastScreens
    override val risk = Risk.Read
    override val withScreens = true
    override val concurrency = Concurrency.ParallelSafe

    override val def = ToolDef("view_past_screens",
        Prompts.tool("view_past_screens", "keep" to RECALL_KEEP),
        mapOf("ids" to buildJsonObject {
            put("type", "array"); put("description", "1-$RECALL_MAX_IDS screenshotIds from earlier screen reads.")
            put("minItems", 1); put("maxItems", RECALL_MAX_IDS)
            put("items", buildJsonObject { put("type", "integer"); put("minimum", 1) })
        }), listOf("ids"))

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val requested = a.intList("ids", 1, RECALL_MAX_IDS)!!.distinct()
        val recalls = ctx.shots.recalls
        // 按请求顺序找:找到的留着发图或发文字,没找到的单独报
        val found = requested.mapNotNull { id -> recalls.firstOrNull { it.id == id } }
        val missing = requested.filter { id -> found.none { it.id == id } }
        val images = mutableListOf<ByteArray>()
        val imageIds = mutableListOf<Int?>()
        val content = buildJsonObject {
            put("ok", missing.isEmpty())
            put("screenshots", screenshotEntries(found, images, imageIds))
            if (missing.isNotEmpty()) reportMissing(missing)
        }
        return ToolExecution(content, images = images, imageIds = imageIds)
    }

    /** 逐条建回看条目:有截图的随结果发图并记下它的编号,只有元素文字的发给文字。 */
    private fun screenshotEntries(found: List<Recall>, images: MutableList<ByteArray>, imageIds: MutableList<Int?>): JsonArray =
        JsonArray(found.map { r ->
            buildJsonObject {
                put("id", r.id); put("step", r.step); put("app", r.app)
                if (r.png != null) { images += r.png; imageIds += r.id; put("imageAttached", true) } else put("elementsText", r.elementsText)
            }
        })

    /** 有编号没找到:列出缺的编号,说明这是旧画面、可恢复,不是操作失败。 */
    private fun JsonObjectBuilder.reportMissing(missing: List<Int>) {
        put("missing", JsonArray(missing.map(::JsonPrimitive)))
        put("error", "screenshot_not_found")
        put("message", Feedback.format("recall_not_found", "keep" to RECALL_KEEP))
        put("recoverable", true)
        // 找不到的是旧画面,不是手机操作失败
        put("counts_as_failure", false)
    }
}

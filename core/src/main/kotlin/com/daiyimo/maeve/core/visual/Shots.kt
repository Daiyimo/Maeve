package com.daiyimo.maeve.core.visual

import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.tool.RECALL_KEEP
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.refExpired
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 一张历史画面，供 view_past_screens 回看。
 *
 * @property id 画面编号（与 snapshotId 空间无关）
 * @property step 产生该画面的循环轮次
 * @property app 当时的前台应用，未知为 null
 * @property png 画面数据；受保护页面与付款页不存图，为 null
 * @property elementsText 该画面的可访问性树文本
 */
internal class Recall(
    val id: Int,
    val step: Int,
    val app: String?,
    val png: ByteArray?,
    val elementsText: String,
)

/**
 * 画面编号与回看队列。
 *
 * 编号在一个任务内单调递增，跨屏幕类型共用：不管是手机屏还是网页屏，
 * view_past_screens 都按同一个编号空间查找。
 */
internal class Shots {
    /** 当前循环轮次，写入新回看记录。 */
    var step = 0

    /** 已发出的最大画面编号。 */
    var lastId = 0
        private set

    /** 回看队列，只保留最近 [RECALL_KEEP] 条。 */
    val recalls = ArrayDeque<Recall>()

    /** 取下一个画面编号。 */
    fun next(): Int = ++lastId

    /** 追加一条回看记录。 */
    fun remember(entry: Recall) {
        recalls.addLast(entry)
        while (recalls.size > RECALL_KEEP) recalls.removeFirst()
    }

    /**
     * 把画面编号误当成 snapshotId 传入时给出纠正提示；不是这类误用返回 null。
     *
     * 判据：snapshotId 是纯数字且在已发出的画面编号范围内。真正的 snapshotId
     * 从不写成纯数字，因此满足这两个条件即可认定为误用。
     */
    fun idAsSnapshot(o: JsonObject): ToolExecution? {
        val primitive = o["snapshotId"] as? JsonPrimitive ?: return null
        val value = primitive.content.trim().toIntOrNull() ?: return null
        if (value < 1 || value > lastId) return null
        return refExpired(
            Feedback.format("screenshot_id_as_snapshot", "id" to value),
            "screenshot_id",
        )
    }
}

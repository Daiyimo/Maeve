package com.daiyimo.maeve.core.trigger

/**
 * 本机用户的计划管理。
 *
 * 核心只管「有哪些计划、开不开、什么时候跑」;真正把计划写进存储、向系统重新布置闹钟的是宿主。
 * 每个方法都带 [threadId](对话),计划只在它所属的对话里可见。
 */
interface ScheduleAdmin {
    fun list(threadId: String): List<TriggerDefinition>

    fun setEnabled(threadId: String, id: String, enabled: Boolean): Boolean

    fun remove(threadId: String, id: String): Boolean

    fun reschedule(threadId: String, id: String, atMs: Long): Boolean

    fun runNow(threadId: String, id: String): Boolean
}

/**
 * 存下一个定时任务之后的情况:[onTime] 为 false 表示系统没准许准时闹钟(到点可能不准时或起不来);
 * 这时 [userNotified] 说这次有没有真的给用户发出去开启的提示。
 */
data class Scheduled(
    val onTime: Boolean,
    val userNotified: Boolean = false,
)

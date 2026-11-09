package com.daiyimo.maeve.core.record

/**
 * 只读访问已经落盘的运行记录。
 *
 * 写入侧已经完成脱敏与截短,这里读出的内容可以原样交给界面。记录被清理、
 * 编号不认识或内容损坏时返回 null / 空表,不向调用方抛异常。具体存储由
 * app 侧实现。
 */
interface RunRecords {
    /** 读取一次运行的概要;没有时返回 null。 */
    suspend fun summary(runId: String): RunSummary?

    /** 读取一次运行的全部事件;没有时返回空表。 */
    suspend fun events(runId: String): List<RunEvent>
}

package com.daiyimo.maeve.core.record

/**
 * 只读地看已经落盘的运行记录:概要(`run.json`)和事件(`events.jsonl`)。
 * 写入时已经脱敏、截短,读出来原样交给界面。记录被清掉、编号不认识或读不出时返回 null / 空表,不抛异常。
 * 由 app 一侧按实际存储实现。
 */
interface RunRecords {
    suspend fun summary(runId: String): RunSummary?

    suspend fun events(runId: String): List<RunEvent>
}

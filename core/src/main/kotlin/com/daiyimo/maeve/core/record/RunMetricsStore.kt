package com.daiyimo.maeve.core.record

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 运行小结的持久化:每次运行一份 [RunMetrics],以 JSON Lines 存在 [file] 中,
 * 只留最近 [keep] 份。运行记录本身只留 30 次,但统计需要更长的历史,所以
 * 小结独立保留、不随记录清理。
 *
 * 收尾时 [append] 一份,交付回答并判定结局后再用 [update] 补上。读不出的行
 * 直接跳过,整份文件损坏按空处理;写入先落到同目录的临时文件再改名,写不成功
 * 只损失这一次。同一个文件可能被多个实例打开,读写共用进程内的一把锁。
 */
class RunMetricsStore(private val file: File, private val keep: Int = DEFAULT_KEEP) {
    init {
        require(keep > 0) { "keep must be positive" }
    }

    /** 全部小结,旧的在前。 */
    fun all(): List<RunMetrics> = withDataLock { load() }

    /**
     * 指定运行的小结,以运行编号为键;不存在的不出现在结果里。先按文本粗筛
     * 可能含目标编号的行,解码后再核对编号,避免每次都解析整份文件
     * (最多 [keep] 份)。
     */
    fun byIds(runIds: Set<String>): Map<String, RunMetrics> = withDataLock {
        val wanted = runIds.filter { it.isNotEmpty() }
        if (wanted.isEmpty() || !file.isFile) return@withDataLock emptyMap()
        readLinesOrEmpty()
            .asSequence()
            .filter { row -> wanted.any { row.contains(it) } }
            .mapNotNull(::decodeRow)
            .filter { it.runId in runIds }
            .associateBy { it.runId }
    }

    /** 最近 [n] 份,旧的在前;[n] 为空时返回全部。 */
    fun recent(n: Int?): List<RunMetrics> = all().let { rows ->
        if (n == null) rows else rows.takeLast(n)
    }

    /** 追加一份;同一运行已有记录则替换。运行编号为空时总是新增。 */
    fun append(m: RunMetrics) = withDataLock {
        val existing = load()
        val kept = if (m.runId.isEmpty()) {
            existing
        } else {
            existing.filter { it.runId != m.runId }
        }
        persist(kept + m)
    }

    /** 改写指定运行的那一份;不存在时什么也不做。 */
    fun update(runId: String, change: (RunMetrics) -> RunMetrics) = withDataLock {
        val before = load()
        if (before.none { it.runId == runId }) return@withDataLock
        persist(before.map { row -> if (row.runId == runId) change(row) else row })
    }

    private fun load(): List<RunMetrics> {
        if (!file.isFile) return emptyList()
        return readLinesOrEmpty()
            .filter { it.isNotBlank() }
            .mapNotNull(::decodeRow)
    }

    private fun readLinesOrEmpty(): List<String> =
        runCatching { file.readLines() }.getOrDefault(emptyList())

    private fun decodeRow(row: String): RunMetrics? =
        runCatching { JSON.decodeFromString(RunMetrics.serializer(), row) }.getOrNull()

    private fun persist(rows: List<RunMetrics>) {
        runCatching {
            file.parentFile?.mkdirs()
            val staging = File(file.path + ".tmp")
            val content = rows.takeLast(keep).joinToString("") { row ->
                JSON.encodeToString(RunMetrics.serializer(), row) + "\n"
            }
            staging.writeText(content)
            check(staging.renameTo(file)) { "rename failed" }
        }
    }

    private inline fun <T> withDataLock(block: () -> T): T = synchronized(LOCK, block)

    companion object {
        const val DEFAULT_KEEP = 500
        const val FILE_NAME = "run-metrics.jsonl"
        private val LOCK = Any()
        val JSON = Json { ignoreUnknownKeys = true }

        /** 导出成一个 JSON 数组,方便人工分析;旧的在前。 */
        fun export(list: List<RunMetrics>): String =
            EXPORT.encodeToString(ListSerializer(RunMetrics.serializer()), list)

        private val EXPORT = Json { encodeDefaults = true }
    }
}

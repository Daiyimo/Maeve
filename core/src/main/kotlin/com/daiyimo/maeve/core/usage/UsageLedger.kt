package com.daiyimo.maeve.core.usage

import com.daiyimo.maeve.core.model.Usage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** 一格地址里的静态部分:哪个服务商、哪个模型、哪个用途。 */
data class UsageKey(val providerId: String, val model: String, val purpose: UsagePurpose)

/**
 * 一格累计下来的量:输入(缓存命中已经算在里面)、输出、缓存命中的 token,
 * 实际发出的 HTTP 请求次数,以及其中没拿到最终用量的那几次。
 */
data class UsageCounts(
    val input: Long = 0,
    val output: Long = 0,
    val cached: Long = 0,
    val calls: Long = 0,
    val callsWithoutUsage: Long = 0,
) {
    operator fun plus(other: UsageCounts): UsageCounts = UsageCounts(
        input = input + other.input,
        output = output + other.output,
        cached = cached + other.cached,
        calls = calls + other.calls,
        callsWithoutUsage = callsWithoutUsage + other.callsWithoutUsage,
    )

    companion object {
        /** 把一次调用的结果折成增量:[httpAttempts] 是发出去的请求数,[usage] 是最终回复报的用量。 */
        fun of(usage: Usage?, httpAttempts: Int = 1): UsageCounts {
            val attempted = httpAttempts.coerceAtLeast(0).toLong()
            val answered = if (usage == null) 0L else 1L
            return UsageCounts(
                input = usage?.inputTokens?.toLong() ?: 0L,
                output = usage?.outputTokens?.toLong() ?: 0L,
                cached = usage?.cachedInputTokens?.toLong() ?: 0L,
                calls = attempted,
                callsWithoutUsage = (attempted - answered).coerceAtLeast(0L),
            )
        }
    }
}

/** 账本里的一行:[hour] 是 UTC 整点桶,从 1970-01-01T00:00Z 起数第几个小时。 */
data class UsageRow(val hour: Long, val key: UsageKey, val counts: UsageCounts)

/**
 * 某一刻账本的全部内容。
 * [providerNames] 记的是每个服务商最后一次记账时的名字;服务商后来被删了,这个名字仍然保留。
 */
data class UsageSnapshot(val rows: List<UsageRow>, val providerNames: Map<String, String>) {
    companion object {
        val EMPTY = UsageSnapshot(emptyList(), emptyMap())
    }
}

/** 一格的完整坐标;磁盘上按 (hour, key) 唯一。 */
private data class UsageCell(val hour: Long, val key: UsageKey)

/** [LedgerFile.decode] 的产物,还没并进账本。 */
private class RestoredLedger(
    val cells: Map<UsageCell, UsageCounts>,
    val names: Map<String, String>,
)

/**
 * 磁盘格式。字段名属于持久化契约,改了就再也读不出老版本写的文件。
 *
 * 布局是刻意压扁的:keys 只出现一次,rows 用下标引用它,避免每行重复服务商/模型/用途三个字符串。
 */
private object LedgerFile {
    private const val FORMAT = 1
    private const val VERSION = "version"
    private const val NAMES = "names"
    private const val KEYS = "keys"
    private const val ROWS = "rows"
    private const val PROVIDER = "provider"
    private const val MODEL = "model"
    private const val PURPOSE = "purpose"

    fun encode(cells: Map<UsageCell, UsageCounts>, names: Map<String, String>): String {
        val distinctKeys = cells.keys.mapTo(linkedSetOf<UsageKey>()) { cell -> cell.key }.toList()
        val keyIndex = distinctKeys.withIndex().associate { (position, key) -> key to position }
        return buildJsonObject {
            put(VERSION, FORMAT)
            putJsonObject(NAMES) {
                names.forEach { (providerId, providerName) -> put(providerId, providerName) }
            }
            putJsonArray(KEYS) {
                distinctKeys.forEach { key ->
                    addJsonObject {
                        put(PROVIDER, key.providerId)
                        put(MODEL, key.model)
                        put(PURPOSE, key.purpose.key)
                    }
                }
            }
            putJsonArray(ROWS) {
                cells.entries.sortedBy { entry -> entry.key.hour }.forEach { (cell, counts) ->
                    addJsonArray {
                        add(JsonPrimitive(cell.hour))
                        add(JsonPrimitive(keyIndex.getValue(cell.key)))
                        add(JsonPrimitive(counts.input))
                        add(JsonPrimitive(counts.output))
                        add(JsonPrimitive(counts.cached))
                        add(JsonPrimitive(counts.calls))
                        add(JsonPrimitive(counts.callsWithoutUsage))
                    }
                }
            }
        }.toString()
    }

    /** 解析失败一律让异常冒出去,由调用方决定怎么收拾;早于 [oldestHour] 的行直接跳过。 */
    fun decode(text: String, oldestHour: Long): RestoredLedger {
        val root = Json.parseToJsonElement(text).jsonObject
        val names = LinkedHashMap<String, String>()
        root[NAMES]?.jsonObject?.forEach { (providerId, providerName) ->
            names[providerId] = providerName.jsonPrimitive.content
        }
        val keys = root[KEYS]?.jsonArray.orEmpty().map { element ->
            val fields = element.jsonObject
            UsageKey(
                providerId = fields.getValue(PROVIDER).jsonPrimitive.content,
                model = fields.getValue(MODEL).jsonPrimitive.content,
                purpose = UsagePurpose.of(fields.getValue(PURPOSE).jsonPrimitive.content),
            )
        }
        val cells = HashMap<UsageCell, UsageCounts>()
        root[ROWS]?.jsonArray.orEmpty().forEach { element ->
            val numbers = element.jsonArray.map { number -> number.jsonPrimitive }
            val hour = numbers[0].long
            if (hour < oldestHour) return@forEach
            val cell = UsageCell(hour, keys[numbers[1].int])
            val counts = UsageCounts(
                input = numbers[2].long,
                output = numbers[3].long,
                cached = numbers[4].long,
                calls = numbers[5].long,
                callsWithoutUsage = numbers[6].long,
            )
            val previous = cells[cell]
            cells[cell] = previous?.plus(counts) ?: counts
        }
        return RestoredLedger(cells, names)
    }
}

/**
 * 本机的 token 用量账本:按 UTC 整点 × 服务商 × 模型 × 用途累计,整本只存在 [file] 这一个 JSON 文件里。
 *
 * [record] 只改内存,多线程并发记也不会丢;改过之后由一条后台线程在 [flushDelayMs] 毫秒后整份落盘。
 * 落盘先写同目录的临时文件再改名覆盖,写到一半被打断也毁不掉上一份好数据;进程被杀时最多丢掉最后这一小段。
 * 只保留最近 [keepDays] 天的桶,读入和写出两个时机都会清掉更早的。文件读不出来(损坏)就当空账本重来。
 */
class UsageLedger(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val flushDelayMs: Long = 2_000,
    private val keepDays: Int = KEEP_DAYS,
) : UsageSink {
    private val accumulated = HashMap<UsageCell, UsageCounts>()
    private val lastKnownNames = HashMap<String, String>()
    private val dataLock = Any()
    private val ioLock = Any()
    private var unwritten = false
    private var flushPlanned = false

    private val flushTimer: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "usage-ledger").apply { isDaemon = true }
        }
    }

    private val _version = MutableStateFlow(0L)

    /** 每记下一笔就加一;界面按它触发重新统计。 */
    val version: StateFlow<Long> = _version.asStateFlow()

    init {
        restore()
    }

    override fun record(source: UsageSource, usage: Usage?) = record(source, usage, httpAttempts = 1)

    override fun record(source: UsageSource, usage: Usage?, httpAttempts: Int) {
        // 请求没发出去、也没有用量:这次不构成任何可记的用量
        if (httpAttempts <= 0 && usage == null) return
        val cell = UsageCell(hourNow(), UsageKey(source.providerId, source.model, source.purpose))
        val increment = UsageCounts.of(usage, httpAttempts)
        val firstOfThisBatch = synchronized(dataLock) {
            val previous = accumulated[cell]
            accumulated[cell] = previous?.plus(increment) ?: increment
            if (source.providerName.isNotEmpty()) lastKnownNames[source.providerId] = source.providerName
            unwritten = true
            if (flushPlanned) {
                false
            } else {
                flushPlanned = true
                true
            }
        }
        _version.update { it + 1 }
        if (firstOfThisBatch) {
            flushTimer.schedule({ runCatching { flush() } }, flushDelayMs, TimeUnit.MILLISECONDS)
        }
    }

    fun snapshot(): UsageSnapshot = synchronized(dataLock) {
        val rows = accumulated.entries.map { (cell, counts) -> UsageRow(cell.hour, cell.key, counts) }
        UsageSnapshot(rows, HashMap(lastKnownNames))
    }

    /**
     * 有没落盘的改动就立刻写出。后台线程会定时调,测试和退出前也可以直接调。
     * 写失败时把改动重新标脏,留给下一次,同时把异常抛给调用方。
     */
    fun flush() {
        synchronized(ioLock) {
            val payload = synchronized(dataLock) {
                flushPlanned = false
                if (!unwritten) return
                unwritten = false
                discardExpired()
                LedgerFile.encode(accumulated, lastKnownNames)
            }
            try {
                replaceFile(payload)
            } catch (failure: Exception) {
                synchronized(dataLock) { unwritten = true }
                throw failure
            }
        }
    }

    private fun hourNow(): Long = Math.floorDiv(now(), HOUR_MS)

    private fun expiryHour(): Long = hourNow() - keepDays * HOURS_PER_DAY

    private fun discardExpired() {
        val oldestKept = expiryHour()
        accumulated.keys.retainAll { cell -> cell.hour >= oldestKept }
    }

    private fun restore() {
        if (!file.isFile) return
        val restored = runCatching { LedgerFile.decode(file.readText(), expiryHour()) }.getOrNull()
        synchronized(dataLock) {
            if (restored == null) {
                accumulated.clear()
                lastKnownNames.clear()
            } else {
                accumulated.putAll(restored.cells)
                lastKnownNames.putAll(restored.names)
            }
        }
    }

    private fun replaceFile(payload: String) {
        val directory = file.parentFile
        directory?.mkdirs()
        val staging = File(directory, file.name + ".tmp")
        staging.writeText(payload)
        if (staging.renameTo(file)) return
        file.delete()
        check(staging.renameTo(file)) { "cannot write ${file.name}" }
    }

    companion object {
        /** 账本保留的天数。 */
        const val KEEP_DAYS = 400

        /** 一个整点桶有多少毫秒;也是 [UsageRow.hour] 的进制。 */
        const val HOUR_MS = 3_600_000L

        private const val HOURS_PER_DAY = 24L
    }
}

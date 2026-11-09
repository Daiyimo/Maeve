package com.daiyimo.maeve.core.usage

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 统计要看的时段。
 *
 * - [Last24Hours] 是滚动窗口:到此刻所在的小时为止的 24 个小时,按小时分。
 * - [LastDays] 是含今天在内的最近 [LastDays.days] 个本地日历日,按天分。
 * - [Custom] 是本地日期 [Custom.from] 与 [Custom.to],两头都算。
 *
 * 后两种折成天数后如果超过 [UsageQuery.MAX_DAILY_BARS] 根柱子,就改从第一天起每七天一根。
 */
sealed interface UsageRange {
    data object Last24Hours : UsageRange
    data class LastDays(val days: Int) : UsageRange
    data class Custom(val from: LocalDate, val to: LocalDate) : UsageRange
}

/** 趋势图一根柱子代表的时长。 */
enum class UsageStep { Hour, Day, Week }

/**
 * 一段时间内的合计。缓存命中的 token 已经算进 [input],所以 [tokens] 只把输入和输出相加。
 */
data class UsageTotals(
    val input: Long = 0,
    val output: Long = 0,
    val cached: Long = 0,
    val calls: Long = 0,
    val callsWithoutUsage: Long = 0,
) {
    val tokens: Long get() = input + output

    operator fun plus(counts: UsageCounts): UsageTotals = UsageTotals(
        input = input + counts.input,
        output = output + counts.output,
        cached = cached + counts.cached,
        calls = calls + counts.calls,
        callsWithoutUsage = callsWithoutUsage + counts.callsWithoutUsage,
    )
}

/**
 * 趋势图里的一根柱子。
 *
 * [start] 是这一格的起点(按小时给整点,按天或按周给首日零点);[lastDay] 只在按周分时有值,
 * 表示这一格覆盖到的最后一天,最后一格可能不满七天;[byProvider] 只列本格内有 token 的服务商。
 */
data class UsageBucket(
    val start: LocalDateTime,
    val tokens: Long,
    val byProvider: Map<String, Long> = emptyMap(),
    val lastDay: LocalDate? = null,
)

/** 一个模型下某个用途的用量;[share] 是它占该模型的比例(0..1)。用途只是统计维度,界面不单列。 */
data class PurposeUsage(val purpose: UsagePurpose, val totals: UsageTotals, val share: Double)

/** 一个服务商下某个模型的用量;[share] 是它占该服务商的比例。 */
data class ModelUsage(val model: String, val totals: UsageTotals, val share: Double, val purposes: List<PurposeUsage>)

/**
 * 一个服务商的用量;[share] 是它占总量的比例。
 *
 * [name] 优先用当前名字;服务商已经删掉([deleted])时退回账本记下的旧名。[order] 是配色用的稳定序号:
 * 仍连着的按连接先后排,删掉的接在后面、内部按最早出现的时间排,换时间范围也不会变。
 */
data class ProviderUsage(
    val id: String,
    val name: String,
    val deleted: Boolean,
    val totals: UsageTotals,
    val share: Double,
    val models: List<ModelUsage>,
    val order: Int = 0,
)

/** 一个时段的完整统计:合计、趋势图([step] 定一根多长)、以及服务商 › 模型 › 用途三层分项,每层按 token 降序。 */
data class UsageReport(
    val range: UsageRange,
    val step: UsageStep,
    val totals: UsageTotals,
    val series: List<UsageBucket>,
    val providers: List<ProviderUsage>,
) {
    val hourly: Boolean get() = step == UsageStep.Hour

    fun provider(id: String): ProviderUsage? = providers.firstOrNull { usage -> usage.id == id }
}

/** 一根柱子的位置表:每行属于哪一根,以及这一根的起点和(按周时)末日。 */
private class UsageGrid(
    val step: UsageStep,
    val starts: List<LocalDateTime>,
    val lastDays: List<LocalDate?>,
    private val locate: (Long) -> Int?,
) {
    fun barFor(hour: Long): Int? = locate(hour)
}

/** 逐行折叠账本:柱状合计与三级分项一次扫描完成;范围外的行只参与“首次出现时间”的统计。 */
private class Rollup(val grid: UsageGrid) {
    val barTokens: LongArray = LongArray(grid.starts.size)
    val barProviders: List<HashMap<String, Long>> = List(grid.starts.size) { hashMapOf<String, Long>() }
    val byKey: HashMap<UsageKey, UsageTotals> = HashMap()
    val firstHour: HashMap<String, Long> = HashMap()
    var grandTotal: UsageTotals = UsageTotals()
        private set

    fun include(row: UsageRow) {
        val providerId = row.key.providerId
        val seenAt = firstHour[providerId]
        if (seenAt == null || row.hour < seenAt) firstHour[providerId] = row.hour

        val bar = grid.barFor(row.hour) ?: return
        val tokens = row.counts.input + row.counts.output
        barTokens[bar] += tokens
        if (tokens > 0) {
            val perProvider = barProviders[bar]
            perProvider[providerId] = (perProvider[providerId] ?: 0L) + tokens
        }
        val keyTotals = byKey[row.key] ?: UsageTotals()
        byKey[row.key] = keyTotals + row.counts
        grandTotal += row.counts
    }
}

/** 服务商的稳定序号:仍连着的取连接顺序,已删除的排在它们后面、按首次出现时间。 */
private class ProviderOrder(live: Map<String, String>, firstHour: Map<String, Long>) {
    private val connected: Map<String, Int> = live.keys.withIndex().associate { (index, id) -> id to index }

    private val removed: Map<String, Int> = firstHour.keys
        .asSequence()
        .filter { providerId -> providerId !in live }
        .sortedWith(compareBy<String> { providerId -> firstHour.getValue(providerId) }.thenBy { providerId -> providerId })
        .withIndex()
        .associate { (index, providerId) -> providerId to (live.size + index) }

    operator fun get(providerId: String): Int = connected[providerId] ?: removed.getValue(providerId)
}

/**
 * 把账本内容算成统计。账本按 UTC 整点存,换成本地日历由 [report] 的 `zone` 决定:
 * 一个整点桶归到它开始那一刻所在的本地日期,所以夏令时那天 23 或 25 个小时也不会错,
 * 与 UTC 差半小时的时区里跨本地零点的那一小时整份算到它开始的那天。
 */
object UsageQuery {
    private const val HOUR_MS = UsageLedger.HOUR_MS
    private const val HOURS_PER_DAY = 24
    private const val DAYS_PER_WEEK = 7

    /** 按天分时最多几根柱子;超过就按周分。 */
    const val MAX_DAILY_BARS = 45

    /**
     * [live] 是当前仍连着的服务商(id → 名字),迭代顺序就是连接先后;不在其中的一律算已删除,
     * 名字退回 [UsageSnapshot.providerNames] 里记下的旧名。
     */
    fun report(
        snapshot: UsageSnapshot,
        range: UsageRange,
        now: Instant,
        zone: ZoneId,
        live: Map<String, String>,
    ): UsageReport {
        val currentHour = Math.floorDiv(now.toEpochMilli(), HOUR_MS)
        val today = now.atZone(zone).toLocalDate()
        val grid = gridFor(range, currentHour, today, zone)
        val rollup = Rollup(grid)
        snapshot.rows.forEach { row -> rollup.include(row) }

        val order = ProviderOrder(live, rollup.firstHour)
        val providers = rollup.byKey.entries
            .groupBy { entry -> entry.key.providerId }
            .map { (providerId, entries) ->
                providerUsage(providerId, entries, live, snapshot.providerNames, rollup.grandTotal, order)
            }
            .sortedWith(
                compareByDescending<ProviderUsage> { usage -> usage.totals.tokens }
                    .thenByDescending { usage -> usage.totals.calls }
                    .thenBy { usage -> usage.order },
            )

        val series = grid.starts.indices.map { index ->
            UsageBucket(
                start = grid.starts[index],
                tokens = rollup.barTokens[index],
                byProvider = rollup.barProviders[index],
                lastDay = grid.lastDays[index],
            )
        }
        return UsageReport(
            range = range,
            step = grid.step,
            totals = rollup.grandTotal,
            series = series,
            providers = providers,
        )
    }

    /** 按天的时段落在哪两天(含两头);[UsageRange.Custom] 两头写反了就换回来。 */
    fun days(range: UsageRange, today: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
        is UsageRange.LastDays -> today.minusDays(range.days.coerceAtLeast(1).toLong() - 1) to today
        is UsageRange.Custom -> if (range.from <= range.to) range.from to range.to else range.to to range.from
        UsageRange.Last24Hours -> today.minusDays(1) to today
    }

    private fun gridFor(range: UsageRange, currentHour: Long, today: LocalDate, zone: ZoneId): UsageGrid =
        when (range) {
            UsageRange.Last24Hours -> hourlyGrid(currentHour, zone)
            is UsageRange.LastDays, is UsageRange.Custom -> {
                val (firstDay, lastDay) = days(range, today)
                dailyGrid(firstDay, lastDay, zone)
            }
        }

    private fun hourlyGrid(currentHour: Long, zone: ZoneId): UsageGrid {
        val opening = currentHour - (HOURS_PER_DAY - 1)
        val starts = (0 until HOURS_PER_DAY).map { offset ->
            LocalDateTime.ofInstant(Instant.ofEpochMilli((opening + offset) * HOUR_MS), zone)
        }
        return UsageGrid(
            step = UsageStep.Hour,
            starts = starts,
            lastDays = List(starts.size) { null },
            locate = { hour ->
                val offset = hour - opening
                when {
                    offset < 0 || offset >= HOURS_PER_DAY -> null
                    else -> offset.toInt()
                }
            },
        )
    }

    private fun dailyGrid(firstDay: LocalDate, lastDay: LocalDate, zone: ZoneId): UsageGrid {
        val dayCount = ChronoUnit.DAYS.between(firstDay, lastDay).toInt() + 1
        val stride = if (dayCount > MAX_DAILY_BARS) DAYS_PER_WEEK else 1
        val barCount = (dayCount + stride - 1) / stride
        val starts = List(barCount) { bar -> firstDay.plusDays(bar.toLong() * stride).atStartOfDay() }
        val lastDays: List<LocalDate?> = List(barCount) { bar ->
            if (stride == DAYS_PER_WEEK) {
                minOf(firstDay.plusDays(bar.toLong() * DAYS_PER_WEEK + 6), lastDay)
            } else {
                null
            }
        }
        return UsageGrid(
            step = if (stride == DAYS_PER_WEEK) UsageStep.Week else UsageStep.Day,
            starts = starts,
            lastDays = lastDays,
            locate = { hour ->
                val day = Instant.ofEpochMilli(hour * HOUR_MS).atZone(zone).toLocalDate()
                when {
                    day < firstDay || day > lastDay -> null
                    else -> ChronoUnit.DAYS.between(firstDay, day).toInt() / stride
                }
            },
        )
    }

    private fun providerUsage(
        providerId: String,
        entries: List<Map.Entry<UsageKey, UsageTotals>>,
        live: Map<String, String>,
        rememberedNames: Map<String, String>,
        overall: UsageTotals,
        order: ProviderOrder,
    ): ProviderUsage {
        val byModel = entries.groupBy { entry -> entry.key.model }
        val providerTotals = byModel.values
            .fold(UsageTotals()) { sum, modelEntries -> sum.merge(modelEntries.foldTotals()) }

        val models = byModel.map { (modelName, modelEntries) ->
            val modelTotals = modelEntries.foldTotals()
            val purposes = modelEntries
                .groupBy { entry -> entry.key.purpose }
                .map { (purpose, purposeEntries) ->
                    val purposeTotals = purposeEntries.foldTotals()
                    PurposeUsage(purpose, purposeTotals, shareOf(purposeTotals, modelTotals))
                }
                .sortedWith(
                    compareByDescending<PurposeUsage> { usage -> usage.totals.tokens }
                        .thenByDescending { usage -> usage.totals.calls }
                        .thenBy { usage -> usage.purpose.ordinal },
                )
            ModelUsage(modelName, modelTotals, shareOf(modelTotals, providerTotals), purposes)
        }.sortedWith(
            compareByDescending<ModelUsage> { usage -> usage.totals.tokens }
                .thenByDescending { usage -> usage.totals.calls }
                .thenBy { usage -> usage.model },
        )

        val liveName = live[providerId]
        return ProviderUsage(
            id = providerId,
            name = liveName ?: rememberedNames[providerId] ?: providerId,
            deleted = liveName == null,
            totals = providerTotals,
            share = shareOf(providerTotals, overall),
            models = models,
            order = order[providerId],
        )
    }

    private fun List<Map.Entry<UsageKey, UsageTotals>>.foldTotals(): UsageTotals =
        fold(UsageTotals()) { sum, entry -> sum.merge(entry.value) }

    private fun UsageTotals.merge(other: UsageTotals): UsageTotals = UsageTotals(
        input = input + other.input,
        output = output + other.output,
        cached = cached + other.cached,
        calls = calls + other.calls,
        callsWithoutUsage = callsWithoutUsage + other.callsWithoutUsage,
    )

    private fun shareOf(part: UsageTotals, whole: UsageTotals): Double =
        if (whole.tokens > 0) part.tokens.toDouble() / whole.tokens else 0.0
}

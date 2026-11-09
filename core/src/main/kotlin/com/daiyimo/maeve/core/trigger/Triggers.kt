package com.daiyimo.maeve.core.trigger

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 一条定时/周期计划的定义。
 *
 * @property id 计划唯一标识
 * @property threadId 触发时所属的会话
 * @property goal 到点后交给模型的目标
 * @property nextAtMs 下一次触发的绝对时间（毫秒）
 * @property intervalMs 固定毫秒间隔；null 表示一次性
 * @property conditionGoal 先执行的检查目标，条件成立才运行 [goal]
 * @property enabled 是否启用
 * @property idleOnly 仅在无前台任务时执行；繁忙时由看门狗稍后重试
 * @property lastConditionMet 上一次条件检查的结果，用于只在状态翻转时触发
 * @property reminderText 固定提醒文案；非空时到点直接送达，不启动模型
 * @property calendarTime 按当地钟点重复（`HH:mm`），配合 [calendarZone] 使用
 * @property calendarZone 当地钟点所属时区
 * @property calendarDays 允许触发的星期（1=周一 … 7=周日），空表示每天
 */
@Serializable
data class TriggerDefinition(
    val id: String,
    val threadId: String,
    val goal: String,
    val nextAtMs: Long,
    val intervalMs: Long? = null,
    val conditionGoal: String? = null,
    val enabled: Boolean = true,
    val idleOnly: Boolean = false,
    val lastConditionMet: Boolean? = null,
    val reminderText: String? = null,
    val calendarTime: String? = null,
    val calendarZone: String? = null,
    val calendarDays: List<Int> = emptyList(),
) {
    init {
        require(id.isNotBlank() && threadId.isNotBlank() && goal.isNotBlank())
        require(nextAtMs >= 0 && (intervalMs == null || intervalMs > 0))
        require(calendarTime == null || (intervalMs == null && calendarZone != null && calendarDays.all { it in 1..7 }))
    }

    /** 是否为按当地钟点重复的计划。 */
    val isCalendar: Boolean get() = calendarTime != null
}

/** 一个触发时段的处理状态。 */
@Serializable
enum class TriggerWindowState {
    /** 已认领，等待派发。 */
    Claimed,

    /** 已派发给执行方。 */
    Dispatched,

    /** 动作已派发（条件检查或提醒投递进行中）。 */
    ActionDispatched,

    /** 已成功送达。 */
    Delivered,

    /** 已跳过（条件不成立或用户未空闲）。 */
    Skipped,

    /** 执行失败。 */
    Failed,

    /** 结果不明（进程在确认前退出）。 */
    Unknown,
}

/**
 * 一个触发时段的账本条目。
 *
 * @property triggerId 所属计划
 * @property dueAtMs 计划中的应触发时间
 * @property claimedAtMs 实际认领时间
 * @property missedIntervals 因积压而合并跳过的间隔数
 * @property state 当前处理状态
 * @property reason 状态变更原因，供排障使用
 */
@Serializable
data class TriggerWindow(
    val triggerId: String,
    val dueAtMs: Long,
    val claimedAtMs: Long,
    val missedIntervals: Long = 0,
    val state: TriggerWindowState = TriggerWindowState.Claimed,
    val reason: String? = null,
) {
    /** 账本主键：同一计划同一到期时间只有一个条目。 */
    val key: String get() = "$triggerId:$dueAtMs"

    /** 尚未尘埃落定的中间状态。 */
    val inFlight: Boolean
        get() = state == TriggerWindowState.Claimed ||
            state == TriggerWindowState.Dispatched ||
            state == TriggerWindowState.ActionDispatched

    /** 已经终结，不再接受状态流转。 */
    val settled: Boolean
        get() = state == TriggerWindowState.Delivered ||
            state == TriggerWindowState.Skipped ||
            state == TriggerWindowState.Failed
}

/**
 * 计划的持久化与账本。
 *
 * 实现方必须保证 [claimDue] 原子地写入「下次时间 + 待处理账本」，
 * 只有这样调用方才可以在返回后安全派发，进程异常重启也不会重复执行。
 */
interface TriggerStore {
    fun upsert(trigger: TriggerDefinition)

    fun get(id: String): TriggerDefinition?

    fun all(): List<TriggerDefinition>

    fun windows(): List<TriggerWindow>

    fun claimDue(nowMs: Long, eligible: (TriggerDefinition) -> Boolean = { true }): List<TriggerWindow>

    /** 手动触发一次，不改变原计划的下次时间。 */
    fun claimNow(id: String, nowMs: Long): TriggerWindow?

    /** 推进账本条目状态；条目不存在或已终结时返回 false。 */
    fun mark(key: String, state: TriggerWindowState, reason: String? = null): Boolean

    /** 清理已确认送达的一次性计划。 */
    fun retireDeliveredOneShots()

    fun remove(id: String)
}

@Serializable
data class TriggerSnapshot(
    val triggers: List<TriggerDefinition>,
    val windows: List<TriggerWindow>,
)

/** 触发窗口的三种进行中状态，用于判断计划是否已有未完成时段。 */
private val IN_FLIGHT_STATES = setOf(
    TriggerWindowState.Claimed,
    TriggerWindowState.Dispatched,
    TriggerWindowState.ActionDispatched,
)

/** 触发窗口的三种终结状态，用于账本裁剪与状态锁。 */
private val SETTLED_STATES = setOf(
    TriggerWindowState.Delivered,
    TriggerWindowState.Skipped,
    TriggerWindowState.Failed,
)

/** 每个触发最多保留的已终结账本条目数。 */
private const val SETTLED_KEEP = 100

/**
 * 纯内存实现；文件持久化实现复用同一套状态转换规则。
 */
class MemoryTriggerStore(initial: TriggerSnapshot = TriggerSnapshot(emptyList(), emptyList())) : TriggerStore {
    private val triggers = linkedMapOf<String, TriggerDefinition>()
    private val ledger = linkedMapOf<String, TriggerWindow>()

    init {
        initial.triggers.forEach { triggers[it.id] = it }
        initial.windows.forEach { ledger[it.key] = it }
    }

    @Synchronized
    fun snapshot(): TriggerSnapshot = TriggerSnapshot(triggers.values.toList(), ledger.values.toList())

    @Synchronized
    override fun upsert(trigger: TriggerDefinition) {
        triggers[trigger.id] = trigger
    }

    @Synchronized
    override fun remove(id: String) {
        triggers.remove(id)
        ledger.entries.removeAll { it.value.triggerId == id }
    }

    @Synchronized
    override fun get(id: String): TriggerDefinition? = triggers[id]

    @Synchronized
    override fun all(): List<TriggerDefinition> = triggers.values.toList()

    @Synchronized
    override fun windows(): List<TriggerWindow> = ledger.values.toList()

    @Synchronized
    override fun claimDue(nowMs: Long, eligible: (TriggerDefinition) -> Boolean): List<TriggerWindow> {
        val inFlight = ledger.values.filter { it.inFlight }.map { it.triggerId }.toSet()
        val due = triggers.values.filter { t ->
            t.enabled && t.id !in inFlight && t.nextAtMs <= nowMs &&
                ledger["${t.id}:${t.nextAtMs}"] == null && eligible(t)
        }
        return due.map { t -> claimLocked(t, nowMs) }
    }

    /** 认领单条计划：写账本并推进下次时间。条件与 [TriggerDefinition.slotTaken] 保持一致。 */
    private fun claimLocked(t: TriggerDefinition, nowMs: Long): TriggerWindow {
        val interval = t.intervalMs
        val missed = if (interval == null) 0 else (nowMs - t.nextAtMs) / interval
        val window = TriggerWindow(t.id, t.nextAtMs, nowMs, missed)
        ledger[window.key] = window
        triggers[t.id] = when {
            t.isCalendar -> t.copy(
                nextAtMs = nextCalendarAt(t.calendarTime!!, t.calendarZone!!, t.calendarDays, nowMs),
            )

            interval != null -> t.copy(nextAtMs = t.nextAtMs + (missed + 1) * interval)
            else -> t.copy(enabled = false)
        }
        return window
    }

    @Synchronized
    override fun claimNow(id: String, nowMs: Long): TriggerWindow? {
        if (triggers[id] == null) return null
        if (ledger.values.any { it.triggerId == id && it.inFlight }) return null
        val at = generateSequence(nowMs) { it + 1 }.first { "$id:$it" !in ledger }
        return TriggerWindow(id, at, nowMs).also { ledger[it.key] = it }
    }

    @Synchronized
    override fun mark(key: String, state: TriggerWindowState, reason: String?): Boolean {
        val old = ledger[key] ?: return false
        if (old.settled) return false
        ledger[key] = old.copy(state = state, reason = reason)
        if (state == TriggerWindowState.Delivered || state == TriggerWindowState.Skipped ||
            state == TriggerWindowState.Failed || state == TriggerWindowState.Unknown) {
            pruneCompleted(old.triggerId)
        }
        return true
    }

    /** 只保留每个计划最近 [SETTLED_KEEP] 条已终结账本。 */
    private fun pruneCompleted(id: String) {
        ledger.values
            .filter { it.triggerId == id && it.settled }
            .sortedByDescending { it.dueAtMs }
            .drop(SETTLED_KEEP)
            .forEach { ledger.remove(it.key) }
    }

    @Synchronized
    override fun retireDeliveredOneShots() {
        val retired = triggers.values.filter { t ->
            t.intervalMs == null && t.calendarTime == null &&
                ledger.values.any { it.triggerId == t.id && it.dueAtMs == t.nextAtMs && it.state == TriggerWindowState.Delivered }
        }.map { it.id }
        retired.forEach { id ->
            triggers.remove(id)
            ledger.entries.removeAll { it.value.triggerId == id }
        }
    }
}

/**
 * 该计划当前这一次时段是否已经入账（认领过、跑过或正在跑）。
 *
 * [TriggerStore.claimDue] 因此不会再认领它，两处判据必须保持一致。
 * 一次性计划认领后下次时间不再前进，循环计划认领时已切到下一时段。
 */
fun TriggerDefinition.slotTaken(windows: List<TriggerWindow>): Boolean =
    windows.any { it.triggerId == id && it.dueAtMs == nextAtMs }

/** 该时段是否仍处于未完成的中间状态。 */
fun TriggerWindow.isInFlight(): Boolean = state in IN_FLIGHT_STATES

private val CALENDAR_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 计算下一个匹配的当地钟点时刻（毫秒）。
 *
 * 按 [zone] 的当地日历逐日递进，跨夏令时仍保持同一钟点；
 * 遇到的非法钟点交由系统的向前调整规则处理。[days] 为空表示不限星期。
 */
fun nextCalendarAt(time: String, zone: String, days: List<Int>, afterMs: Long): Long {
    val localTime = LocalTime.parse(time)
    val zoneId = ZoneId.of(zone)
    var date = Instant.ofEpochMilli(afterMs).atZone(zoneId).toLocalDate()
    repeat(9) {
        val candidate = date.atTime(localTime).atZone(zoneId).toInstant().toEpochMilli()
        if (candidate > afterMs && (days.isEmpty() || date.dayOfWeek.value in days)) return candidate
        date = date.plusDays(1)
    }
    error("calendar schedule has no matching day")
}

/** 修改下次时间时，日历计划同步改用新钟点；保存与预览共用这一转换。 */
fun TriggerDefinition.rescheduledTo(nextAtMs: Long): TriggerDefinition {
    val local = calendarZone?.let { zone ->
        CALENDAR_CLOCK.format(Instant.ofEpochMilli(nextAtMs).atZone(ZoneId.of(zone)))
    }
    return copy(nextAtMs = nextAtMs, enabled = true, calendarTime = if (calendarTime != null) local else null)
}

/** 编辑或创建时的预览：列出接下来 [count] 次触发时间。 */
fun upcoming(def: TriggerDefinition, afterMs: Long, count: Int): List<Long> {
    if (count <= 0) return emptyList()
    val out = ArrayList<Long>(count)
    when {
        def.isCalendar -> {
            var at = if (def.nextAtMs >= afterMs) def.nextAtMs
            else nextCalendarAt(def.calendarTime!!, def.calendarZone!!, def.calendarDays, afterMs)
            repeat(count) {
                out += at
                at = nextCalendarAt(def.calendarTime!!, def.calendarZone!!, def.calendarDays, at)
            }
        }

        def.intervalMs != null && def.intervalMs > 0 -> {
            var t = def.nextAtMs
            if (t < afterMs) t += (afterMs - t) / def.intervalMs * def.intervalMs + def.intervalMs
            repeat(count) {
                out += t
                t += def.intervalMs
            }
        }

        else -> out += def.nextAtMs
    }
    return out
}

/**
 * 触发器引擎：负责闹钟排布、时段认领前的去重与派发。
 *
 * 派发顺序固定为「先记账、后派发」，因此进程异常退出不会重复执行已认领的时段。
 * 具体的持久化与闹钟能力由宿主通过 [store] 与 [scheduleAlarm] 提供。
 */
class TriggerEngine(
    private val store: TriggerStore,
    private val scheduleAlarm: (Long?) -> Unit,
    private val isIdle: () -> Boolean = { true },
    private val idleRetryMs: Long = 60_000L,
    private val dispatch: (TriggerDefinition, TriggerWindow) -> Unit,
) {
    /** 重排闹钟，并把停留在 Claimed 的时段标记为结果不明。 */
    fun rearm(nowMs: Long) {
        store.windows()
            .filter { it.state == TriggerWindowState.Claimed }
            .forEach { store.mark(it.key, TriggerWindowState.Unknown, "process restarted before dispatch confirmation") }
        val idle = isIdle()
        val windows = store.windows()
        val inFlight = windows.filter { it.inFlight }.map { it.triggerId }.toSet()
        // 已有未完成时段的计划不会再次认领，为它排「到期即响」只会空转，改为按重试间隔排
        scheduleAlarm(
            store.all()
                .filter { it.enabled && (it.triggerIdIn(inFlight) || !it.slotTaken(windows)) }
                .minOfOrNull {
                    if (it.triggerIdIn(inFlight)) nowMs + idleRetryMs
                    else maxOf(if (it.idleOnly && !idle) nowMs + idleRetryMs else nowMs, it.nextAtMs)
                }
        )
    }

    /**
     * 暂停或恢复计划 [id]。
     *
     * 计划不存在，或要恢复的计划当前时段已入账（认领过的一次性计划）时不做修改并返回 false。
     */
    fun setEnabled(id: String, enabled: Boolean, nowMs: Long): Boolean {
        val trigger = store.get(id) ?: return false
        if (enabled && trigger.slotTaken(store.windows())) return false
        store.upsert(trigger.copy(enabled = enabled))
        rearm(nowMs)
        return true
    }

    /** 时区变化后，跟随旧时区的当地钟点计划重算到新时区；显式指定其他时区的不动。 */
    fun rezone(nowMs: Long, fromZone: String, toZone: ZoneId) {
        if (fromZone == toZone.id) return
        store.all()
            .filter { it.enabled && it.calendarTime != null && it.calendarZone == fromZone }
            .forEach { t ->
                store.upsert(
                    t.copy(
                        calendarZone = toZone.id,
                        nextAtMs = nextCalendarAt(t.calendarTime!!, toZone.id, t.calendarDays, nowMs),
                    )
                )
            }
        rearm(nowMs)
    }

    /** 闹钟到点：认领到期时段并派发，随后重排下一次闹钟。 */
    fun fire(nowMs: Long) {
        val idle = isIdle()
        store.claimDue(nowMs) { !it.idleOnly || idle }.forEach { window ->
            val trigger = store.get(window.triggerId) ?: return@forEach
            try {
                dispatch(trigger, window)
                store.mark(window.key, TriggerWindowState.Dispatched)
            } catch (e: Exception) {
                store.mark(window.key, TriggerWindowState.Unknown, e::class.simpleName)
            }
        }
        rearm(nowMs)
    }
}

private fun TriggerDefinition.triggerIdIn(ids: Set<String>): Boolean = id in ids

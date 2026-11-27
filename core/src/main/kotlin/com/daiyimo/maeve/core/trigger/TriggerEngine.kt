package com.daiyimo.maeve.core.trigger

import java.time.LocalTime
import java.time.ZoneId

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
    private val blocked: (TriggerDefinition) -> String? = { null },
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
        val deferred = windows.filter { it.state == TriggerWindowState.Deferred || it.state == TriggerWindowState.DeferredAction }
        val due = store.all()
                .filter { it.enabled && (it.triggerIdIn(inFlight) || !it.slotTaken(windows)) }
                .minOfOrNull {
                    if (it.triggerIdIn(inFlight)) nowMs + idleRetryMs
                    else if (it.nextAtMs <= nowMs && ((it.idleOnly && !idle) || blocked(it) != null)) nowMs + idleRetryMs
                    else maxOf(nowMs, it.nextAtMs)
                }
        val pending = deferred.mapNotNull { window ->
            val trigger = store.get(window.triggerId) ?: return@mapNotNull null
            val shouldWait = window.reason == TriggerDispatchDeferred.REASON || (trigger.idleOnly && !idle) || blocked(trigger) != null
            if (shouldWait) nowMs + idleRetryMs else nowMs
        }.minOrNull()
        scheduleAlarm(listOfNotNull(due, pending).minOrNull())
    }

    /**
     * 暂停或恢复计划 [id]。
     *
     * 计划不存在，或要恢复的计划当前时段已入账（认领过的一次性计划）时不做修改并返回 false。
     */
    fun setEnabled(id: String, enabled: Boolean, nowMs: Long): Boolean {
        val trigger = store.get(id) ?: return false
        if (enabled && trigger.slotTaken(store.windows())) return false
        val nextAt = if (enabled && !trigger.enabled && trigger.preset != null && trigger.nextAtMs <= nowMs && trigger.isCalendar)
            nextCalendarAt(trigger.calendarTime!!, trigger.calendarZone!!, trigger.calendarDays, nowMs) else trigger.nextAtMs
        store.upsert(trigger.copy(enabled = enabled, nextAtMs = nextAt))
        if (!enabled) store.windows().filter {
            it.triggerId == id && it.state in setOf(TriggerWindowState.Deferred, TriggerWindowState.DeferredAction)
        }.forEach { store.mark(it.key, TriggerWindowState.Skipped, "paused") }
        rearm(nowMs)
        return true
    }

    /** 时区变化后，跟随旧时区的当地钟点计划重算到新时区；显式指定其他时区的不动。 */
    fun rezone(nowMs: Long, fromZone: String, toZone: ZoneId) {
        if (fromZone == toZone.id) return
        store.all()
            .filter { it.enabled && !it.fixedZone && it.calendarTime != null && it.calendarZone == fromZone }
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

    /** Close the execution window once its outcome is known, independently of result notification. */
    fun finish(key: String, state: TriggerWindowState, nowMs: Long, reason: String? = null) {
        require(state in SETTLED_STATES || state == TriggerWindowState.Unknown)
        if (store.mark(key, state, reason) && state == TriggerWindowState.Delivered) {
            store.retireDeliveredOneShots()
        }
        rearm(nowMs)
    }

    /** Change the clock time or next occurrence while preserving the plan's enabled state. */
    fun editTime(id: String, nowMs: Long, localTime: LocalTime?, nextAtMs: Long?): Boolean {
        val trigger = store.get(id) ?: return false
        if (trigger.preset != null || store.windows().any { it.triggerId == id && it.inFlight }) return false
        val time = if (trigger.isCalendar) {
            CALENDAR_CLOCK.format(localTime ?: return false)
        } else null
        val next = if (time != null) nextCalendarAt(time, trigger.calendarZone!!, trigger.calendarDays, nowMs)
            else nextAtMs ?: return false
        if (next <= nowMs) return false
        store.upsert(trigger.copy(calendarTime = time, nextAtMs = next))
        rearm(nowMs)
        return true
    }

    /** 闹钟到点：认领到期时段并派发，随后重排下一次闹钟。 */
    fun fire(nowMs: Long) {
        val idle = isIdle()
        val previouslyDeferred = store.windows().filter { it.state == TriggerWindowState.Deferred || it.state == TriggerWindowState.DeferredAction }
        val due = store.claimDue(nowMs) { (!it.idleOnly || idle) && blocked(it) == null }
        for (window in previouslyDeferred + due) {
            val trigger = store.get(window.triggerId) ?: continue
            val reason = blocked(trigger)
            if (reason != null || (window.state != TriggerWindowState.Claimed && trigger.idleOnly && !idle)) {
                val waiting = if (window.state == TriggerWindowState.DeferredAction) window.state else TriggerWindowState.Deferred
                store.mark(window.key, waiting, reason ?: "busy")
                continue
            }
            val checked = window.state == TriggerWindowState.DeferredAction
            if (window.state != TriggerWindowState.Claimed) {
                store.mark(window.key, if (checked) TriggerWindowState.ActionDispatched else TriggerWindowState.Claimed)
            }
            try {
                dispatch(trigger, window)
                if (!checked) store.mark(window.key, TriggerWindowState.Dispatched)
            } catch (_: TriggerDispatchDeferred) {
                store.mark(window.key, if (checked) TriggerWindowState.DeferredAction else TriggerWindowState.Deferred,
                    TriggerDispatchDeferred.REASON)
            } catch (e: Exception) {
                store.mark(window.key, TriggerWindowState.Unknown, e::class.simpleName)
            }
        }
        rearm(nowMs)
    }
}

private fun TriggerDefinition.triggerIdIn(ids: Set<String>): Boolean = id in ids

/** The platform declined to start the executor, so this window can be dispatched again. */
class TriggerDispatchDeferred(cause: Throwable) : Exception(REASON, cause) {
    companion object { const val REASON = "foreground_start_restricted" }
}

private val SETTLED_STATES = setOf(
    TriggerWindowState.Delivered,
    TriggerWindowState.Skipped,
    TriggerWindowState.Failed,
)

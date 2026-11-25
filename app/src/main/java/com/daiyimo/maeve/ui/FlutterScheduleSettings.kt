package com.daiyimo.maeve.ui

import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.core.trigger.TriggerDefinition
import com.daiyimo.maeve.core.trigger.TriggerPreset
import com.daiyimo.maeve.core.trigger.TriggerWindowState
import com.daiyimo.maeve.core.trigger.nextCalendarAt
import com.daiyimo.maeve.task.TriggerRuntime
import io.flutter.plugin.common.MethodCall
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

internal class FlutterScheduleSettings(private val app: MaeveApp) {
    fun change(call: MethodCall): Map<String, Any?> {
        var deferred: String? = null
        when (call.argument<String>("action") ?: "snapshot") {
            "snapshot" -> Unit
            "create" -> {
                val preset = call.argument<String>("preset")?.let { key ->
                    requireNotNull(TriggerPreset.entries.firstOrNull { it.key == key }) { "定时任务示例不存在" }
                }
                val goal = preset?.goal ?: call.argument<String>("goal").orEmpty().trim()
                require(goal.isNotEmpty() && goal.length <= 4000) { "请输入 1 到 4000 字的任务说明" }
                val time = runCatching { LocalTime.parse(call.argument<String>("time")) }
                    .getOrNull()?.format(DateTimeFormatter.ofPattern("HH:mm"))
                requireNotNull(time) { "请选择执行时间" }
                val now = System.currentTimeMillis()
                val builtIn = preset?.builtInSchedule(now)
                if (builtIn != null) {
                    val exists = TriggerRuntime.list(app).any { it.id == builtIn.id || it.preset == preset }
                    if (!exists) TriggerRuntime.schedule(app, builtIn)
                } else {
                    val thread = call.argument<String>("conversationId")?.takeIf { it.isNotBlank() }
                        ?: UUID.randomUUID().toString()
                    val zone = ZoneId.systemDefault().id
                    val next = nextCalendarAt(time, zone, emptyList(), now)
                    TriggerRuntime.schedule(app, TriggerDefinition(UUID.randomUUID().toString(), thread, goal, next,
                        idleOnly = true, calendarTime = time, calendarZone = zone, preset = preset))
                }
            }
            "setEnabled" -> require(TriggerRuntime.setEnabled(app, id(call), requireNotNull(call.argument<Boolean>("enabled")))) {
                "这个计划无法更改启用状态"
            }
            "runNow" -> deferred = TriggerRuntime.runNow(app, id(call))
            "remove" -> TriggerRuntime.remove(app, id(call))
            else -> throw IllegalArgumentException("未知定时任务操作")
        }
        val blocked = TriggerRuntime.blockedReason(app)
        val now = System.currentTimeMillis()
        val snapshot = TriggerRuntime.snapshot(app)
        val windows = snapshot.windows.groupBy { it.triggerId }
        return mapOf(
            "exactAlarm" to TriggerRuntime.canScheduleExactly(app),
            "deferred" to deferred,
            "examples" to TriggerPreset.entries.map { mapOf("id" to it.key, "title" to it.title, "goal" to it.goal) },
            "plans" to snapshot.triggers.map { plan ->
                val latest = windows[plan.id]?.maxByOrNull { it.claimedAtMs }
                val waiting = latest?.state in setOf(TriggerWindowState.Deferred, TriggerWindowState.DeferredAction)
                val due = plan.enabled && plan.nextAtMs <= now && latest?.inFlight != true
                mapOf(
                    "id" to plan.id, "threadId" to plan.threadId, "goal" to plan.goal, "enabled" to plan.enabled,
                    "title" to plan.preset?.title,
                    "preset" to plan.preset?.key,
                    "time" to plan.calendarTime, "nextAt" to plan.nextAtMs,
                    "inFlight" to (latest?.inFlight == true),
                    "state" to (if (waiting || (due && blocked != null && plan.reminderText == null)) "waiting" else latest?.state?.name),
                    "blocked" to blocked.takeIf { waiting || (due && plan.reminderText == null) },
                )
            },
        )
    }

    private fun id(call: MethodCall): String = requireNotNull(call.argument<String>("id")) { "缺少计划编号" }
}

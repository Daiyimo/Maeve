package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.core.trigger.TriggerWindowState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PLAN_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")


private fun formatPlanTime(ms: Long): String = PLAN_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))


private fun windowLabel(state: TriggerWindowState): String = when (state) {
    TriggerWindowState.Delivered -> tr(R.string.plans_delivered)
    TriggerWindowState.Skipped -> tr(R.string.plans_unchanged)
    TriggerWindowState.Failed -> tr(R.string.plans_check_failed)
    TriggerWindowState.Unknown -> tr(R.string.plans_result_pending)
    TriggerWindowState.Claimed, TriggerWindowState.Dispatched, TriggerWindowState.ActionDispatched -> tr(R.string.plans_running_now)
}

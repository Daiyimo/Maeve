package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.chat.ConversationSummary
import com.daiyimo.maeve.settings.Prerequisites
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DateTextStyle
import com.daiyimo.maeve.i18n.uiLocale

data class DrawerConversation(
    val id: String?,
    val title: String,
    val subtitle: String,
    val running: Boolean,
    val current: Boolean,
    val enabled: Boolean,
    /** 最后改动的时间(写在标题右边);还没存进对话列表的为空。 */
    val time: String = "",
    /** 置顶了:标题前一个小图钉,长按菜单里是「取消置顶」。 */
    val pinned: Boolean = false,
    /** 在浏览器页上对着一页开始的对话:那个网站(主机名),标题后面一个「网页」小标签;不是为 null。 */
    val site: String? = null,
    /** 从另一个对话分支出来的:标题前一个小分支图标。 */
    val branch: Boolean = false,
)


data class DrawerSections(
    val running: List<DrawerConversation>,
    val recent: List<DrawerConversation>,
    val pinned: List<DrawerConversation> = emptyList(),
)


data class CurrentConversation(val id: String?, val running: Boolean, val title: String, val subtitle: String)


fun drawerSections(
    all: List<ConversationSummary>,
    current: CurrentConversation,
    others: List<String>,
    otherTitle: (String) -> String?,
    canSwitch: Boolean,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): DrawerSections {
    val byId = all.associateBy { it.id }
    fun timeOf(id: String?) = byId[id]?.let { conversationTime(it.updatedAt, now, zone) }.orEmpty()
    fun pinned(id: String?) = byId[id]?.pinnedAt != null
    val running = buildList {
        if (current.running) add(DrawerConversation(current.id, current.title.ifBlank { tr(R.string.chat_new) }, current.subtitle, true, true, true, time = timeOf(current.id), pinned = pinned(current.id), site = byId[current.id]?.page?.host, branch = byId[current.id]?.branchOf != null))
        others.filter { it != current.id }.distinct().forEach { id ->
            val title = (byId[id]?.title ?: otherTitle(id))?.let(ChatCopy::plainTitle)?.ifBlank { null } ?: tr(R.string.drawer_running_task)
            add(DrawerConversation(id, title, tr(R.string.drawer_running), running = true, current = false, enabled = canSwitch, time = timeOf(id), pinned = pinned(id), site = byId[id]?.page?.host, branch = byId[id]?.branchOf != null))
        }
    }
    val runningIds = running.mapNotNull { it.id }.toSet()
    fun entry(s: ConversationSummary): DrawerConversation {
        val isCurrent = s.id == current.id
        return DrawerConversation(s.id, ChatCopy.plainTitle(s.title), "", false, isCurrent, canSwitch || isCurrent, conversationTime(s.updatedAt, now, zone), pinned = s.pinnedAt != null,
            site = s.page?.host, branch = s.branchOf != null)
    }
    val rest = all.filter { it.id !in runningIds }
    val pinnedRows = rest.filter { it.pinnedAt != null }.sortedByDescending { it.pinnedAt }.map(::entry)
    val recent = rest.asSequence().filter { it.pinnedAt == null }.take(MAX_RECENT).map(::entry).toList()
    return DrawerSections(running, recent, pinnedRows)
}


fun conversationTime(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day: LocalDate = t.toLocalDate()
    val days = today.toEpochDay() - day.toEpochDay()
    return when {
        days <= 0L -> "%d:%02d".format(t.hour, t.minute)
        days == 1L -> tr(R.string.drawer_yesterday)
        days < 7L -> t.dayOfWeek.getDisplayName(DateTextStyle.SHORT, uiLocale())
        day.year == today.year -> tr(R.string.date_month_day, day.monthValue, day.month.getDisplayName(DateTextStyle.SHORT, uiLocale()), day.dayOfMonth)
        else -> tr(R.string.date_year_month_day, day.year, day.monthValue, day.month.getDisplayName(DateTextStyle.SHORT, uiLocale()), day.dayOfMonth)
    }
}


fun readiness(check: Prerequisites.Check): Pair<String, Boolean> = when {
    !check.canStartTask -> tr(R.string.chat_cannot_start) to false
    !check.phoneReady -> tr(R.string.drawer_phone_not_ready) to false
    else -> tr(R.string.all_ready) to true
}


private const val MAX_RECENT = 50


internal const val MENU_IN_MS = 200


internal const val MENU_OUT_MS = 140


private val DrawerWidth = 244.dp


private const val DRAWER_MAX_FRACTION = 0.62f


private val DrawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)


internal val NavShape = RoundedCornerShape(12.dp)


private val ConversationTitle = TextStyle(fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)


private val ConversationTime = TextStyle(fontSize = 10.5.sp, lineHeight = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontFeatureSettings = "tnum")


private val ConversationTag = TextStyle(fontSize = 10.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)

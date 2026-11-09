package com.daiyimo.maeve.core.uistate

/**
 * 经安卓标准接口(intent)执行的系统级动作:设闹钟、设定时器、打开闹钟列表、打开预填好的新日程。
 * 实际由手机上装着的时钟、日历类 App 完成;没有 App 接手时端口报 [SystemActionResult.NOT_SUPPORTED],模型退回操作界面。
 * 动作只描述意图,具体怎么做由端口和接手的 App 决定。
 */
sealed interface SystemAction {
    data class SetAlarm(
        /** 几点(0–23)。 */
        val hour: Int,
        /** 几分(0–59)。 */
        val minute: Int,
        /** 闹钟名;没有为 null。 */
        val label: String? = null,
        /** 每周重复的星期,按 java.util.Calendar 的写法(1 = 周日 … 7 = 周六);空列表表示只响一次。 */
        val days: List<Int> = emptyList(),
        /** 要不要震动;没有为 null。 */
        val vibrate: Boolean? = null,
    ) : SystemAction
    /** 定时 [seconds] 秒;[label] 是给这个计时器起的名。 */
    data class SetTimer(val seconds: Int, val label: String? = null) : SystemAction
    /** 打开时钟 App 的闹钟列表。 */
    data object ShowAlarms : SystemAction
    /** 起止时间是毫秒时间戳;[allDay] 为真时只看日期。 */
    data class AddCalendarEvent(
        val title: String, val beginMs: Long, val endMs: Long? = null, val allDay: Boolean = false,
        val location: String? = null, val description: String? = null,
    ) : SystemAction

    /** 闹钟和计时器交给 App 后不留在界面上;其余动作会在 Maeve 的屏上打开对应 App 的页面。 */
    val silent: Boolean get() = this is SetAlarm || this is SetTimer
}

/** 一个系统动作交出去之后的结果:成没成、谁接的、闹钟有没有变化、界面上开没开。 */
data class SystemActionResult(
    /** 动作的结果;失败时原因是 [NOT_SUPPORTED](没有 App 接这个动作)、app_in_use_by_user、launch_failed 等。 */
    val result: ActionResult,
    /** 接手的 App 的包名。 */
    val handledBy: String? = null,
    /** 动作之前系统报的下一个闹钟(毫秒时间戳;没有为 null)。 */
    val nextAlarmBefore: Long? = null,
    /** 动作之后系统报的下一个闹钟(毫秒时间戳;没有为 null)。 */
    val nextAlarmAfter: Long? = null,
    /** 为 false 表示下一个闹钟读不到。 */
    val alarmReadable: Boolean = false,
    /** 动作之后 Maeve 的屏上开着 [handledBy] 的页面(本该悄悄设好的,App 却开了界面)。 */
    val showing: Boolean = false,
) {
    companion object {
        /** 没有 App 接这个动作时 [result] 的取值。 */
        const val NOT_SUPPORTED = "not_supported"
    }
}

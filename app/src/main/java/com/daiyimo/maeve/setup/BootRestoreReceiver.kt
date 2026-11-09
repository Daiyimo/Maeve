package com.daiyimo.maeve.setup

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.setup.BootRestoreHandler
import com.daiyimo.maeve.task.TriggerRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 开机广播的入口:清单里登记的是这个全名;实现在手机能力里。
 *
 * 这里的额外动作不再自己 goAsync——父类开机恢复已经取走了那份 PendingResult,再取一次是 null,
 * 挂 [restoreInBackground] 钩子上由父类一起跑完再 finish,广播的 10 秒预算才同时覆盖两边。
 */
class BootRestoreReceiver : BootRestoreHandler() {
    override fun accepts(intent: Intent): Boolean = intent.action in ACTIONS

    override suspend fun restoreInBackground(context: Context, intent: Intent) {
        try {
            // 重新布置闹钟要读改写 triggers 账本、同步时区要重算计划、ensureRunning 要走 binder,都是磁盘加 IPC 的活,放 IO
            withContext(Dispatchers.IO) {
                when (intent.action) {
                    // 开机、时间被改、时区变了、精确闹钟权限变了:都按当前时间把闹钟重新布置一遍
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                        TriggerRuntime.rearm(context)
                    // 换了时区:跟随设备时区的当地时间计划立刻按新时区重算(内部会重新布置闹钟),显式钉了其他时区的不动
                    Intent.ACTION_TIMEZONE_CHANGED -> TriggerRuntime.syncTimeZone(context)
                    // 开机时局域网那种电脑连接开着就重新监听(USB 那种不跨重启:换了开机编号,这里已按关着算,上次的连接信息也清掉)
                    Intent.ACTION_BOOT_COMPLETED ->
                        (context.applicationContext as MaeveApp).mcp.ensureRunning()
                }
            }
        } catch (e: Exception) {
            Log.e("MaeveTrigger", "boot restore failed", e)
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

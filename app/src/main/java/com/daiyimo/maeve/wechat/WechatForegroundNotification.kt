package com.daiyimo.maeve.wechat

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.task.TaskService
import android.content.Context

internal fun Context.wechatForegroundNotification(stage: AmooRelayStage): Notification {
    val off = PendingIntent.getService(this, 1, Intent(this, WechatService::class.java).setAction(WechatService.ACTION_TURN_OFF),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return Notification.Builder(this, TaskService.CHANNEL_PROGRESS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(tr(R.string.wechat_notif_title))
        .setContentText(tr(stage.label ?: R.string.wechat_notif_body))
        .setOngoing(true).setOnlyAlertOnce(true)
        .setContentIntent(TaskService.openApp(this))
        .addAction(Notification.Action.Builder(null, tr(R.string.wechat_turn_off), off).build())
        .build()
}

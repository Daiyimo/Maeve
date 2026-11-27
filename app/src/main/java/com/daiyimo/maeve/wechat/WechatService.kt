package com.daiyimo.maeve.wechat

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.settings.FeatureFlags
import com.daiyimo.maeve.task.TaskService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 只保活微信长轮询；任务执行仍由 TaskService 管理。 */
class WechatService : Service() {
    private val runtime get() = (application as MaeveApp).wechat
    private var progress: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TaskService.ensureChannels(this)
        if (intent?.action == ACTION_TURN_OFF && !FeatureFlags.get(this).quitRequested()) runtime.setEnabled(false)
        val foreground = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, wechatForegroundNotification(runtime.systemAmoo.stage.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTIFICATION_ID, wechatForegroundNotification(runtime.systemAmoo.stage.value))
        }.onFailure { Log.w("MaeveWechat", "WeChat service could not enter the foreground", it) }.isSuccess
        if (!foreground || FeatureFlags.get(this).quitRequested() || !runtime.sync()) {
            runtime.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (progress == null) progress = CoroutineScope(Dispatchers.Main.immediate).launch {
            runtime.systemAmoo.stage.collect { stage ->
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, wechatForegroundNotification(stage))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        progress?.cancel()
        runtime.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TURN_OFF = "com.daiyimo.maeve.wechat.TURN_OFF"
        private const val NOTIFICATION_ID = 30_002
    }
}

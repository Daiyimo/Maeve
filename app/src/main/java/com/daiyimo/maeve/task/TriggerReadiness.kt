package com.daiyimo.maeve.task

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object TriggerReadiness {
    private var watching = false
    private var lastUserPresentFireAt = 0L
    private var lastNetworkFireAt = 0L
    private val restoreScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    /** 亮屏或网络恢复时直接补跑到期任务，再重排下一次闹钟。 */
    @Synchronized fun watchReadiness(context: Context) {
        if (watching) return
        val app = context.applicationContext
        fun changed(userPresent: Boolean) {
            val now = SystemClock.elapsedRealtime()
            val last = if (userPresent) lastUserPresentFireAt else lastNetworkFireAt
            if (now - last < 5_000L) return
            if (userPresent) lastUserPresentFireAt = now else lastNetworkFireAt = now
            dispatchReady(app)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_PRESENT || !app.getSystemService(KeyguardManager::class.java).isDeviceLocked) {
                    changed(userPresent = true)
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_SCREEN_ON) }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(receiver, filter)
        val network = app.getSystemService(ConnectivityManager::class.java)
        runCatching { network.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) changed(userPresent = false)
            }
        }) }.onFailure { Log.w("MaeveTrigger", "network readiness observer unavailable", it) }
        watching = true
    }

    /** The app becoming visible also covers delayed alarms and missed readiness broadcasts. */
    fun onAppVisible(context: Context) = dispatchReady(context.applicationContext)

    private fun dispatchReady(context: Context) {
        restoreScope.launch {
            runCatching { TriggerRuntime.fire(context) }.onFailure { Log.w("MaeveTrigger", "readiness dispatch failed", it) }
        }
    }
}

package com.daiyimo.maeve.task

import com.daiyimo.maeve.i18n.tr
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.core.agent.Turn
import com.daiyimo.maeve.ui.MainActivity
import com.daiyimo.maeve.core.trigger.Scheduled
import com.daiyimo.maeve.core.trigger.TriggerDefinition
import com.daiyimo.maeve.core.trigger.TriggerDispatchDeferred
import com.daiyimo.maeve.core.trigger.TriggerEngine
import com.daiyimo.maeve.core.trigger.ConditionOutcome
import com.daiyimo.maeve.core.trigger.TriggerSnapshot
import com.daiyimo.maeve.core.trigger.TriggerPreset
import com.daiyimo.maeve.core.trigger.TriggerWindowState
import com.daiyimo.maeve.core.trigger.rescheduledTo
import com.daiyimo.maeve.settings.FeatureFlags
import java.io.File
import java.time.ZoneId
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 安卓闹钟的薄壳；时间与去重由持久化 TriggerStore 决定，闹钟广播可以重复来。 */
object TriggerRuntime {
    private var recovered = false
    private var watching = false
    private var lastUserPresentFireAt = 0L
    private var lastNetworkFireAt = 0L
    @Volatile private var armedAtMs: Long? = null
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
            restoreScope.launch {
                // 系统可能在 Doze 中推迟精确闹钟；亮屏/网络恢复时直接把到期任务跑掉，不再等第二次闹钟。
                runCatching { fire(app) }.onFailure { Log.w("MaeveTrigger", "readiness dispatch failed", it) }
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = changed(userPresent = true)
        }
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
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

    fun blockedReason(context: Context): String? {
        // 锁屏由 TaskGate 处理（可无人值守解锁，否则等待解锁）；这里只拦截模型确实无法工作的离线状态。
        val network = context.getSystemService(ConnectivityManager::class.java)
        val online = network.getNetworkCapabilities(network.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return if (online) null else "offline"
    }

    private fun blockedReason(context: Context, trigger: TriggerDefinition): String? =
        if (trigger.reminderText != null) null else blockedReason(context)
    /** 存下并布置闹钟;系统没准许准时闹钟(到点可能不准时或起不来)时引导用户去开一次,结果里照实说有没有提示到。 */
    @Synchronized fun schedule(context: Context, definition: TriggerDefinition): Scheduled {
        val store = store(context)
        store.upsert(definition)
        engine(context, store).rearm(System.currentTimeMillis())
        val granted = canScheduleExactly(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 只有通知真的发出去了才算提示过;没发出去下次再试
        val notified = ExactAlarmGuide.shouldPrompt(granted, prefs.getBoolean(PROMPTED, false)) && promptExactAlarm(context)
        if (notified) prefs.edit().putBoolean(PROMPTED, true).apply()
        return Scheduled(granted, notified)
    }

    /** 系统的「闹钟和提醒」授权页(安卓 12 起)。 */
    fun exactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 第一次没有权限就建了定时任务:发一条通知,点开就是授权页(任务在后台跑,不直接跳页面)。返回通知有没有发出去。 */
    private fun promptExactAlarm(context: Context): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (!notifications.areNotificationsEnabled()) return false
        TaskService.ensureChannels(context)
        val open = PendingIntent.getActivity(context, PROMPTED.hashCode(), exactAlarmSettings(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return runCatching { notifications.notify(PROMPTED.hashCode(), Notification.Builder(context, TaskService.CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(ExactAlarmGuide.TITLE)
            .setContentText(ExactAlarmGuide.BODY).setStyle(Notification.BigTextStyle().bigText(ExactAlarmGuide.BODY))
            .setContentIntent(open).setAutoCancel(true).build()) }
            .onFailure { Log.w("MaeveTrigger", "exact alarm prompt failed", it) }.isSuccess
    }

    @Synchronized fun rearm(context: Context) {
        if (FeatureFlags.get(context).quitRequested()) arm(context, null)
        else engine(context, store(context)).rearm(System.currentTimeMillis())
    }

    /** 开机、改时间、精确闹钟权限变化后，系统可能已撤掉旧闹钟；强制重新登记。 */
    @Synchronized fun rearmAfterSystemChange(context: Context) {
        armedAtMs = null
        rearm(context)
    }

    /** 完全退出时只撤系统闹钟,不改计划与待处理账本;下次打开 App 由 [rearm] 接着排。 */
    @Synchronized fun suspendAlarms(context: Context) = arm(context, null)

    /** 设备时区变化后,跟随旧设备时区的当地时间计划重算到新时区;显式钉了其他时区的不动。没有基线(首次使用)就只记下当前时区。 */
    @Synchronized fun syncTimeZone(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = ZoneId.systemDefault().id
        val previous = prefs.getString(KEY_DEVICE_ZONE, null)
        prefs.edit().putString(KEY_DEVICE_ZONE, current).apply()
        if (previous == null || previous == current) return
        engine(context, store(context)).rezone(System.currentTimeMillis(), previous, ZoneId.of(current))
    }

    @Synchronized fun fire(context: Context) {
        // 已发出的广播可能晚于退出到达:不要认领时段,也不要发提醒,重新打开后仍可按原账本恢复
        if (FeatureFlags.get(context).quitRequested()) {
            arm(context, null)
            return
        }
        engine(context, store(context)).fire(System.currentTimeMillis())
    }

    @Synchronized fun finishTask(context: Context, key: String, successful: Boolean?, reason: String? = null) {
        val state = when (successful) {
            true -> TriggerWindowState.Delivered
            false -> TriggerWindowState.Failed
            null -> TriggerWindowState.Unknown
        }
        engine(context, store(context)).finish(key, state, System.currentTimeMillis(), reason?.take(160))
    }

    @Synchronized fun cancelQueued(context: Context, key: String) {
        engine(context, store(context)).finish(key, TriggerWindowState.Skipped, System.currentTimeMillis(), "cancelled")
    }

    /** A dispatched run may wait in TaskService's queue before it can touch the device. */
    @Synchronized fun deferBeforeStart(context: Context, key: String): Boolean {
        val blocked = blockedReason(context) ?: return false
        val store = store(context)
        val window = store.windows().firstOrNull { it.key == key } ?: return false
        val waiting = if (window.state == TriggerWindowState.ActionDispatched) TriggerWindowState.DeferredAction
            else TriggerWindowState.Deferred
        if (!store.mark(key, waiting, blocked)) return false
        engine(context, store).rearm(System.currentTimeMillis())
        return true
    }

    @Synchronized fun interrupted(context: Context, key: String, reason: String) {
        val store = store(context)
        store.mark(key, TriggerWindowState.Unknown, reason)
        store.windows().firstOrNull { it.key == key }?.let { window ->
            store.get(window.triggerId)?.let { trigger ->
                if (!hasSavedResult(context, key)) runCatching { reportUncertain(context, trigger, key) }
                    .onFailure { Log.w("MaeveTrigger", "could not report interrupted task", it) }
            }
        }
        engine(context, store).rearm(System.currentTimeMillis())
    }

    @Synchronized fun checked(context: Context, key: String, goal: String, threadId: String, outcome: ConditionOutcome) {
        val store = store(context)
        val window = store.windows().firstOrNull { it.key == key && it.state == TriggerWindowState.Dispatched } ?: return
        if (outcome is ConditionOutcome.Error) {
            store.mark(window.key, TriggerWindowState.Failed, outcome.reason)
            engine(context, store).rearm(System.currentTimeMillis())
            return
        }
        outcome as ConditionOutcome.Checked
        val trigger = store.get(window.triggerId) ?: return
        val wasMet = trigger.lastConditionMet == true
        store.upsert(trigger.copy(lastConditionMet = outcome.met))
        if (!outcome.met || wasMet) {
            store.mark(window.key, TriggerWindowState.Skipped, if (wasMet) "condition_unchanged" else "condition_not_met")
            engine(context, store).rearm(System.currentTimeMillis())
            return
        }
        val blocked = blockedReason(context, trigger)
        if (blocked != null) {
            store.mark(window.key, TriggerWindowState.DeferredAction, blocked)
            engine(context, store).rearm(System.currentTimeMillis())
            return
        }
        store.mark(window.key, TriggerWindowState.ActionDispatched)
        try { TaskService.startTrigger(context, goal, threadId, key, null) }
        catch (e: Exception) {
            if (startRestricted(e)) store.mark(key, TriggerWindowState.DeferredAction, TriggerDispatchDeferred.REASON)
            else reportStartFailure(context, store, trigger, window.key, e, resetCondition = true)
            engine(context, store).rearm(System.currentTimeMillis())
        }
    }

    /** 升级或首次启动只播种一次；之后用户删除、暂停或改时间都不会被恢复。 */
    @Synchronized fun ensureBuiltInSchedules(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(BUILT_INS_SEEDED, false)) return
        val store = store(context)
        val now = System.currentTimeMillis()
        TriggerPreset.missingBuiltInSchedules(store.all(), now).forEach(store::upsert)
        prefs.edit().putBoolean(BUILT_INS_SEEDED, true).apply()
    }

    @Synchronized fun list(context: Context): List<TriggerDefinition> = store(context).all()

    @Synchronized fun presetFor(context: Context, windowKey: String) = store(context).let { store ->
        store.windows().firstOrNull { it.key == windowKey }?.let { store.get(it.triggerId)?.preset }
    }

    fun canScheduleExactly(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @Synchronized fun snapshot(context: Context): TriggerSnapshot = store(context).let {
        TriggerSnapshot(it.all(), it.windows())
    }

    /** 暂停或恢复;改不了(没有这个计划、恢复跑过的一次性计划)时返回 false,见 [TriggerEngine.setEnabled]。 */
    @Synchronized fun setEnabled(context: Context, id: String, enabled: Boolean): Boolean =
        store(context).let { engine(context, it).setEnabled(id, enabled, System.currentTimeMillis()) }

    @Synchronized fun remove(context: Context, id: String) {
        val store = store(context)
        store.remove(id)
        engine(context, store).rearm(System.currentTimeMillis())
    }

    @Synchronized fun reschedule(context: Context, id: String, nextAtMs: Long) {
        val store = store(context)
        store.get(id)?.let { trigger ->
            store.upsert(trigger.rescheduledTo(nextAtMs))
        }
        engine(context, store).rearm(System.currentTimeMillis())
    }

    @Synchronized fun editTime(context: Context, id: String, time: LocalTime?, nextAtMs: Long?): Boolean =
        engine(context, store(context)).editTime(id, System.currentTimeMillis(), time, nextAtMs)

    @Synchronized fun runNow(context: Context, id: String): String? {
        val store = store(context)
        val trigger = store.get(id) ?: error(tr(R.string.trigger_deleted))
        val window = store.claimNow(id, System.currentTimeMillis()) ?: error(tr(R.string.trigger_busy))
        try {
            val blocked = blockedReason(context, trigger)
            if (blocked != null) {
                store.mark(window.key, TriggerWindowState.Deferred, blocked)
                return blocked
            }
            store.mark(window.key, TriggerWindowState.Dispatched)
            if (trigger.reminderText != null) deliverReminder(context, store, trigger, window.key)
            else TaskService.startTrigger(context, trigger.goal, trigger.threadId, window.key, trigger.conditionGoal)
            return null
        } catch (e: Exception) {
            if (startRestricted(e)) {
                store.mark(window.key, TriggerWindowState.Deferred, TriggerDispatchDeferred.REASON)
                return TriggerDispatchDeferred.REASON
            }
            reportStartFailure(context, store, trigger, window.key, e)
            throw e
        } finally {
            engine(context, store).rearm(System.currentTimeMillis())
        }
    }

    private fun store(context: Context): FileTriggerStore {
        val store = FileTriggerStore(File(context.filesDir, "triggers-v3.json"))
        if (!recovered) {
            val results = (context.applicationContext as MaeveApp).runStore.all()
                .filter { it.triggerWindowKey != null && it.reply != null }
                .associateBy { it.triggerWindowKey }
            var recoveredCompletion = false
            store.windows().filter { it.state in setOf(TriggerWindowState.Claimed,
                TriggerWindowState.Dispatched, TriggerWindowState.ActionDispatched) }.forEach {
                val trigger = store.get(it.triggerId)
                if (trigger?.reminderText != null) {
                    runCatching { deliverReminder(context, store, trigger, it.key) }
                        .onFailure { error -> store.mark(it.key, TriggerWindowState.Unknown, error::class.simpleName) }
                } else {
                    val result = results[it.key]
                    val state = when (result?.runSucceeded) {
                        true -> TriggerWindowState.Delivered
                        false -> TriggerWindowState.Failed
                        null -> TriggerWindowState.Unknown
                    }
                    val marked = store.mark(it.key, state, result?.reply?.take(160) ?: "process restarted before result was confirmed")
                    if (marked && state == TriggerWindowState.Delivered) recoveredCompletion = true
                    if (trigger != null && result == null)
                        runCatching { reportUncertain(context, trigger, it.key) }
                            .onFailure { error -> Log.w("MaeveTrigger", "could not report recovered task", error) }
                }
            }
            if (recoveredCompletion) store.retireDeliveredOneShots()
            recovered = true
        }
        return store
    }

    private fun engine(context: Context, store: FileTriggerStore) = TriggerEngine(store,
        { at -> arm(context, at) }, isIdle = { !((context.applicationContext as MaeveApp).taskState.busy.value) },
        blocked = { blockedReason(context, it) }) { trigger, window ->
            if (trigger.reminderText != null) deliverReminder(context, store, trigger, window.key)
            else {
                val checked = window.state == TriggerWindowState.DeferredAction
                try { TaskService.startTrigger(context, trigger.goal, trigger.threadId, window.key, trigger.conditionGoal.takeUnless { checked }) }
                catch (e: Exception) {
                    if (startRestricted(e)) throw TriggerDispatchDeferred(e)
                    reportStartFailure(context, store, trigger, window.key, e, resetCondition = checked)
                    throw e
                }
            }
        }

    private fun deliverReminder(context: Context, store: FileTriggerStore, trigger: TriggerDefinition, key: String) {
        val text = trigger.reminderText ?: return
        val conversations = ConversationStore.shared(context)
        val runId = "reminder:$key"
        if (conversations.turns(trigger.threadId).none { it.runId == runId }) {
            conversations.append(trigger.threadId, Turn(trigger.goal, text, "succeeded", runId = runId))
        }
        notifyLocal(context, trigger.threadId, key, tr(R.string.trigger_reminder), text)
        store.mark(key, TriggerWindowState.Delivered)
        store.retireDeliveredOneShots()
    }

    private fun reportStartFailure(context: Context, store: FileTriggerStore, trigger: TriggerDefinition,
        key: String, error: Exception, resetCondition: Boolean = false) {
        val message = tr(R.string.trigger_not_started_body)
        Log.w("MaeveTrigger", "scheduled task could not start", error)
        store.mark(key, TriggerWindowState.Failed, tr(R.string.trigger_start_failed))
        if (resetCondition) store.get(trigger.id)?.let { store.upsert(it.copy(lastConditionMet = false)) }
        val conversations = ConversationStore.shared(context)
        val runId = "schedule-failed:$key"
        if (conversations.turns(trigger.threadId).none { it.runId == runId }) {
            conversations.append(trigger.threadId, Turn(trigger.goal, message, "failed", runId = runId))
        }
        notifyLocal(context, trigger.threadId, key, tr(R.string.trigger_not_started), message)
    }

    private fun hasSavedResult(context: Context, key: String): Boolean =
        (context.applicationContext as MaeveApp).runStore.all().any {
            it.triggerWindowKey == key && it.reply != null
        }

    private fun reportUncertain(context: Context, trigger: TriggerDefinition, key: String) {
        val message = tr(R.string.trigger_interrupted_body)
        val conversations = ConversationStore.shared(context)
        val runId = "schedule-uncertain:$key"
        if (conversations.turns(trigger.threadId).none { it.runId == runId }) {
            conversations.append(trigger.threadId, Turn(trigger.goal, message, "unverified", runId = runId))
        }
        notifyLocal(context, trigger.threadId, key, tr(R.string.trigger_unconfirmed), message)
    }

    private fun notifyLocal(context: Context, threadId: String, key: String, title: String, text: String) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        TaskService.ensureChannels(context)
        val open = PendingIntent.getActivity(context, key.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_CONVERSATION, threadId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (notifications.areNotificationsEnabled()) {
            runCatching { notifications.notify(key.hashCode(), Notification.Builder(context, TaskService.CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
                .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build()) }
                .onFailure { Log.w("MaeveTrigger", "reminder notification failed", it) }
        }
    }

    private fun arm(context: Context, atMs: Long?) {
        val at = atMs?.takeUnless { FeatureFlags.get(context).quitRequested() }
        // 网络/亮屏回调可能连续来多次；同一个到期时间不再取消重设，避免把系统即将投递的闹钟反复推后。
        if (armedAtMs == at) return
        val previous = armedAtMs
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, TriggerAlarmReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            alarms.cancel(pending)
            if (at != null) {
                if (canScheduleExactly(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
            armedAtMs = at
        } catch (e: Exception) {
            armedAtMs = previous
            throw e
        }
    }

    private const val ACTION = "com.daiyimo.maeve.task.TRIGGER_ALARM"
    private const val PREFS = "triggers"
    private const val PROMPTED = "exact_alarm_prompted"
    private const val KEY_DEVICE_ZONE = "device_timezone"
    private const val BUILT_INS_SEEDED = "builtin_schedules_seeded_v1"

    private fun startRestricted(error: Exception): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && error is ForegroundServiceStartNotAllowedException
}

/** 准时闹钟权限的引导:没开时只主动提醒一次,之后在定时任务页一直写明。 */
object ExactAlarmGuide {
    val TITLE: String get() = tr(R.string.trigger_alarm_title)
    val BODY: String get() = tr(R.string.trigger_alarm_body)

    fun shouldPrompt(granted: Boolean, prompted: Boolean): Boolean = !granted && !prompted
}

class TriggerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.daiyimo.maeve.task.TRIGGER_ALARM") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { TriggerRuntime.fire(context.applicationContext) }
            catch (e: Exception) { Log.e("MaeveTrigger", "alarm dispatch failed", e) }
            finally { pending.finish() }
        }
    }
}

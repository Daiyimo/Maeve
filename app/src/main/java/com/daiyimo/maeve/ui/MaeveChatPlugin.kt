package com.daiyimo.maeve.ui

import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.PhoneServices
import com.daiyimo.maeve.core.agent.QuoteMessage
import com.daiyimo.maeve.settings.Prerequisites
import com.daiyimo.maeve.settings.ProviderStore
import com.daiyimo.maeve.settings.Problem
import com.daiyimo.maeve.settings.SystemStatusMonitor
import com.daiyimo.maeve.task.TaskService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 对话桥:Flutter 壳经 `maeve/chat`(方法)和 `maeve/chat/events`(事件)驱动真实的对话栈——
 * 会话列表、轮次、发送/插话/停止、新建/置顶/删除,全部走进程内那份单例([MaeveApp.chat] 与
 * [MaeveApp.conversations]),和 Compose 界面共用同一个状态源。
 *
 * 事件两种:kind=conversations 推会话摘要列表;kind=ui 推当前对话的轮次快照
 * (goal/status/answer/steps/line/elapsed)。Dart 未 listen 时不推,listen 时补发全量快照。
 *
 * 发送的前置检查(模型配没配、无障碍开没开)由宿主这边按 Compose 同样的算法算,
 * 见 [check]:壳不自己判断,免得两套规则漂移。
 */
class MaeveChatPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var app: MaeveApp
    private lateinit var methods: MethodChannel
    private lateinit var events: EventChannel
    private var scope: CoroutineScope? = null
    private var sink: EventChannel.EventSink? = null
    private var monitor: SystemStatusMonitor? = null
    private var uiJob: Job? = null
    private var changesJob: Job? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        app = binding.applicationContext as MaeveApp
        methods = MethodChannel(binding.binaryMessenger, CHANNEL).also { it.setMethodCallHandler(this) }
        events = EventChannel(binding.binaryMessenger, EVENTS).also { it.setStreamHandler(this) }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methods.setMethodCallHandler(null)
        events.setStreamHandler(null)
        sink = null
        stopWatching()
        monitor?.stop()
        monitor = null
        scope?.cancel()
        scope = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "conversations" -> result.success(summaries())
            "open" -> {
                val id = call.argument<String>("id")?.takeIf { it.isNotBlank() }
                if (id == null) {
                    result.error("bad_args", "open needs a non-empty id", null)
                } else engineScope().launch {
                    app.chat.openConversation(id)
                    pushUi()
                    result.success(null)
                }
            }
            "send" -> {
                val text = call.argument<String>("text")?.trim()
                if (text.isNullOrEmpty()) {
                    result.error("bad_args", "send needs non-empty text", null)
                } else {
                    // 引用由宿主按 QuoteMessage 的格式拼(壳不重复这套格式)
                    val quote = call.argument<String>("quote")?.takeIf { it.isNotBlank() }
                    if (quote != null) {
                        val fromUser = call.argument<Boolean>("quoteFromUser") ?: false
                        app.chat.quote(
                            if (fromUser) QuoteMessage.From.You else QuoteMessage.From.Maeve,
                            quote,
                        )
                    }
                    result.success(app.chat.send(text, check()))
                }
            }
            "cancel" -> {
                TaskService.cancel(app)
                result.success(null)
            }
            "new" -> {
                app.chat.newConversation()
                pushUi()
                result.success(null)
            }
            "setPinned" -> {
                val id = call.argument<String>("id")
                val pinned = call.argument<Boolean>("pinned") ?: false
                if (id.isNullOrBlank()) {
                    result.error("bad_args", "setPinned needs a non-empty id", null)
                } else {
                    app.conversations.setPinned(id, pinned)
                    pushConversations()
                    result.success(null)
                }
            }
            "delete" -> {
                val id = call.argument<String>("id")
                if (id.isNullOrBlank()) {
                    result.error("bad_args", "delete needs a non-empty id", null)
                } else {
                    app.conversations.delete(id)
                    pushConversations()
                    result.success(null)
                }
            }
            "refresh" -> engineScope().launch {
                app.chat.refresh()
                pushConversations()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        sink = events
        startWatching()
        pushConversations()
        pushUi()
    }

    override fun onCancel(arguments: Any?) {
        sink = null
        stopWatching()
    }

    // —— 事件推送 ——

    private fun startWatching() {
        if (uiJob != null) return
        val s = engineScope()
        // StateFlow:订阅即推当前值,之后每次 _ui 变更都推
        uiJob = s.launch { app.chat.ui.collect { pushUi() } }
        changesJob = s.launch { app.conversations.changes.collect { pushConversations() } }
    }

    private fun stopWatching() {
        uiJob?.cancel()
        changesJob?.cancel()
        uiJob = null
        changesJob = null
    }

    private fun pushUi() {
        val events = sink ?: return
        val ui = app.chat.ui.value
        events.success(
            mapOf(
                "kind" to "ui",
                "conversationId" to ui.conversationId,
                "composer" to when (ui.composer) {
                    ComposerMode.Idle -> "idle"
                    ComposerMode.Busy -> "busy"
                    ComposerMode.Answer -> "answer"
                },
                "blocked" to ui.blocked?.map { problemCode(it) },
                "exchanges" to ui.exchanges.map { ex ->
                    mapOf(
                        "goal" to ex.goal,
                        "status" to statusCode(ex.status),
                        "answer" to answerOf(ex),
                        "detail" to detailOf(ex.status),
                        "live" to ex.live,
                        "steps" to ex.steps.size,
                        "line" to ex.line,
                        "elapsedMs" to ex.elapsedMs,
                        "runId" to ex.runId,
                    )
                },
            ),
        )
    }

    private fun pushConversations() {
        val events = sink ?: return
        events.success(mapOf("kind" to "conversations", "items" to summaries()))
    }

    // —— 数据映射(宿主 → 壳的稳定字符串码,壳不做业务判断)——

    private fun summaries(): List<Map<String, Any?>> = app.conversations.list().map { c ->
        mapOf(
            "id" to c.id,
            "title" to c.title,
            "updatedAt" to c.updatedAt,
            "pinned" to (c.pinnedAt != null),
        )
    }

    /** Compose 界面同一套检查:系统状态 + 有没有主模型。发送前现读一次,和回前台刷新一个来源。 */
    private fun check(): Prerequisites.Check {
        val monitor = monitor ?: SystemStatusMonitor(app, PhoneServices.of(app).shellControl).also {
            monitor = it
            it.start()
        }
        monitor.refresh()
        val hasMainModel = ProviderStore.get(app).roles.value.main != null
        return Prerequisites.check(monitor.status.value, hasMainModel)
    }

    private fun statusCode(status: ExchangeStatus): String = when (status) {
        is ExchangeStatus.Working -> "working"
        is ExchangeStatus.WaitingUnlock -> "waiting_unlock"
        is ExchangeStatus.NeedsTakeover -> "needs_takeover"
        is ExchangeStatus.NeedsInput -> "needs_input"
        is ExchangeStatus.NeedsConfirmation -> "needs_confirmation"
        is ExchangeStatus.Paused -> "paused"
        is ExchangeStatus.Queued -> "queued"
        is ExchangeStatus.Done -> "done"
        is ExchangeStatus.Failed -> "failed"
        is ExchangeStatus.Cancelled -> "cancelled"
    }

    /** 轮次里能当"回答"显示的文字:完成了用答复,在跑用模型已给出的草稿,停了用交代。 */
    private fun answerOf(ex: Exchange): String? = when (val s = ex.status) {
        is ExchangeStatus.Done -> s.answer
        is ExchangeStatus.Cancelled -> s.summary
        else -> ex.draft
    }

    /** 非完成状态的补充说明(提问的提示、失败的原因、等你处理的消息)。 */
    private fun detailOf(status: ExchangeStatus): String? = when (status) {
        is ExchangeStatus.Failed -> status.reason
        is ExchangeStatus.NeedsInput -> status.prompt
        is ExchangeStatus.NeedsTakeover -> status.message
        is ExchangeStatus.NeedsConfirmation -> status.body
        is ExchangeStatus.Paused -> status.why
        is ExchangeStatus.Queued -> status.why
        is ExchangeStatus.WaitingUnlock, is ExchangeStatus.Working,
        is ExchangeStatus.Done, is ExchangeStatus.Cancelled -> null
    }

    private fun problemCode(problem: Problem): String = when (problem) {
        is Problem.NoModel -> "no_model"
        is Problem.SystemTooOld -> "system_too_old"
        is Problem.PhoneHelper -> "phone_helper"
        is Problem.Accessibility -> "accessibility"
        is Problem.NotificationsOff -> "notifications_off"
        is Problem.AttentionLowered -> "attention_lowered"
        is Problem.FocusMayLeak -> "focus_may_leak"
    }

    private fun engineScope(): CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }

    companion object {
        const val CHANNEL = "maeve/chat"
        const val EVENTS = "maeve/chat/events"
    }
}

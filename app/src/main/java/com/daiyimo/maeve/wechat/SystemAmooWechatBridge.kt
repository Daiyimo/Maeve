package com.daiyimo.maeve.wechat

import android.content.Intent
import android.app.ActivityOptions
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.cap.phone.a11y.ScreenAccessibility
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.io.File

internal enum class AmooRelayStage(val label: Int?) {
    Idle(null),
    Opening(R.string.wechat_amoo_opening),
    WaitingForAmoo(R.string.wechat_amoo_waiting),
    RunningOnMaeve(R.string.wechat_amoo_running),
    WaitingForPhone(R.string.wechat_amoo_needs_phone),
    ReturningReply(R.string.wechat_amoo_returning),
    SettlingAmoo(R.string.wechat_amoo_settling),
}

/** 使用系统 Amoo 的真实聊天界面；不调用 OpenClaw 默认 agent 或 Maeve 模型。 */
class SystemAmooWechatBridge(private val app: MaeveApp, private val store: WechatStore, val directory: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var worker: Job? = null
    private var previousWorker: Job? = null
    private val requestReady = Channel<Unit>(Channel.CONFLATED)
    fun notifyRequestReady() { requestReady.trySend(Unit) }
    private val _stage = MutableStateFlow(AmooRelayStage.Idle)
    internal val stage = _stage.asStateFlow()
    val active: Boolean get() = stage.value != AmooRelayStage.Idle
    @Volatile var lastError: String? = null
        private set
    private val prefs = app.getSharedPreferences("wechat_system_amoo", 0)
    private var display: com.daiyimo.maeve.cap.phone.display.AgentDisplay? = null
    private var binding: com.daiyimo.maeve.cap.phone.shell.ShellBinding? = null
    private var cleanup: Job? = null
    private var targetDisplay = 0
    val background: Boolean get() = prefs.getBoolean("background", false)
    val backgroundReady: Boolean get() = app.phone.shellControl.status.value is com.daiyimo.maeve.core.shell.BuiltinShellStatus.Running
    val available: Boolean get() = ScreenAccessibility.instance.value != null && runCatching {
        app.packageManager.getPackageInfo(PACKAGE, 0)
    }.isSuccess && (!background || backgroundReady)

    fun setBackground(enabled: Boolean) {
        check(!active) { "请等当前 Amoo 回复结束后再切换接入方式" }
        check(!enabled || backgroundReady) { "后台接入需要手机助手，请先到系统授权连接手机助手" }
        prefs.edit().putBoolean("background", enabled).commit()
        if (!enabled) closeBackground()
    }

    suspend fun open() {
        check(!active) { "请等当前 Amoo 回复完成" }
        closeBackground()?.join()
        launchForeground()
    }

    private fun launchForeground() {
        check(ScreenAccessibility.instance.value != null) { "请开启 Maeve 无障碍服务" }
        app.startActivity(Intent().setClassName(PACKAGE, "$PACKAGE.chat.activity.ChatMainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(0).toBundle())
    }

    private suspend fun openForRequest() {
        cleanup?.join()
        if (!background) { targetDisplay = 0; launchForeground(); return }
        val connection = binding ?: app.phone.sharedShell.acquire().also { binding = it }
        val shell = withTimeoutOrNull(10_000) { connection.shell.filterNotNull().first { it.asBinder().pingBinder() } }
            ?: throw AmooUiException("手机助手尚未连接，后台 Amoo 未启动。请先连接手机助手；不会自动切到前台。")
        if (shell.packageOfTask(shell.topTaskId(0)) == PACKAGE) {
            throw AmooUiException("你正在前台使用 Amoo，后台微信不会抢占当前对话。请退出 Amoo 后重试，或切换为前台接入。")
        }
        val screen = display ?: com.daiyimo.maeve.cap.phone.display.AgentDisplay(shell, 720, 1600, 320,
            app.getSystemService(android.hardware.display.DisplayManager::class.java)).also { display = it }
        targetDisplay = screen.displayId
        if (shell.packageOfTask(shell.topTaskId(targetDisplay)) != PACKAGE) {
            val before = shell.topTaskId(0)
            val result = withContext(Dispatchers.IO) { shell.runSystemCommand(targetDisplay,
                "am start --display $targetDisplay -n $PACKAGE/.chat.activity.ChatMainActivity") }
            val exit = result.getInt("exitCode", -1)
            // am start 对已存在的实例只投递 intent:用户刚在主机用过 Amoo 时,它会被唤醒到主屏。
            // 那不是失败——把它挪到后台屏就是了;主屏归用户,后台屏归 Maeve。
            var landed = false
            val landBy = SystemClock.uptimeMillis() + LAND_WAIT_MS
            while (SystemClock.uptimeMillis() < landBy && !landed) {
                landed = shell.packageOfTask(shell.topTaskId(targetDisplay)) == PACKAGE
                if (!landed && shell.packageOfTask(shell.topTaskId(0)) == PACKAGE) {
                    shell.moveTask(shell.topTaskId(0), targetDisplay)
                }
                if (!landed) delay(200)
            }
            val mainNow = shell.topTaskId(0)
            if (exit != 0 || !landed || mainNow != before) {
                // 现场留足:屏号、退出码与输出、前后主屏顶任务及属主,下次直接从日志定位
                android.util.Log.w("SystemAmooWechat",
                    "background Amoo start: display=$targetDisplay exit=$exit output=${result.getString("output")} " +
                    "landed=$landed mainBefore=$before/${shell.packageOfTask(before)} mainNow=$mainNow/${shell.packageOfTask(mainNow)}")
            }
            // 只有 Amoo 最终不在后台屏上才算失败;主屏前台怎么变是用户自己的事
            if (!landed) {
                throw AmooUiException("这台手机未能保持 Amoo 在后台屏幕中，已停止此次处理。请改用前台接入。")
            }
        }
    }

    private fun closeBackground(keepChat: Boolean = true): Job? {
        val screen = display
        val connection = binding
        display = null; binding = null; targetDisplay = 0
        if (connection == null) return cleanup
        cleanup = scope.launch(Dispatchers.IO) {
            if (screen != null) {
                if (keepChat) runCatching {
                    connection.shell.value?.finishDisplay(screen.displayId, arrayOf(PACKAGE), emptyArray())?.let { result ->
                        android.util.Log.i("SystemAmooWechat", "后台屏幕收尾 kept=${result.getStringArray("kept")?.size ?: 0} note=${result.getString("note")}")
                    }
                }
                // 未确认收尾的聊天随虚拟屏释放，避免把仍在生成的任务迁到主屏。
                screen.release()
            }
            app.phone.sharedShell.release()
        }
        return cleanup
    }

    @Synchronized fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            previousWorker?.join()
            // 只在新进程/新监听启动时恢复已认领消息，不重放可能已经发出的内容。
            withContext(Dispatchers.IO) {
                File(directory, "requests").listFiles()?.filter { it.extension == "working" }?.forEach { file ->
                    read(file)?.let { request ->
                        if (!File(directory, "replies/${request.id}.json").exists()) {
                            val saved = recoveredSystemAmooReply(request.id, request.session,
                                prefs.getString("lastReplyRequest", null), prefs.getString("session", null), prefs.getString("lastReply", null))
                            reply(request, saved ?: recoverPhoneResult(request)
                                ?: "上次系统 Amoo 对话在中途停止，结果尚未确认。请先在 Amoo 检查，再决定是否重试。")
                        }
                    }
                }
            }
            while (isActive && store.enabled()) {
                val request = withContext(Dispatchers.IO) {
                    File(directory, "requests").listFiles()?.filter { it.extension == "json" }
                        ?.mapNotNull(::read)?.sortedBy { it.sequence }?.firstOrNull { !File(directory, "replies/${it.id}.json").exists() }
                }
                if (request == null || app.amooSetup.active) { withTimeoutOrNull(500) { requestReady.receive() }; continue }
                val owner = store.account()?.ownerId
                if (owner == null || store.peer(owner).threadId != request.session) { delay(150); continue }
                val claimed = withContext(Dispatchers.IO) {
                    File(directory, "requests/${request.id}.json").renameTo(File(directory, "requests/${request.id}.working"))
                }
                if (!claimed) continue
                _stage.value = AmooRelayStage.Opening
                var keepChat = true
                try {
                    val response = try {
                        chat(request)
                    } catch (e: CancellationException) { throw e }
                    catch (e: AmooUiException) { lastError = e.message; ChatResult(e.message.orEmpty()) }
                    catch (_: Exception) {
                        val error = "系统 Amoo 对话未能完成。请检查无障碍服务，并在 Amoo 页面确认当前对话。"
                        lastError = error
                        ChatResult(error)
                    }
                    val awaitCompletion = response.awaitCompletion
                    keepChat = awaitCompletion == null
                    _stage.value = AmooRelayStage.ReturningReply
                    withContext(Dispatchers.IO) { reply(request, response.text) }
                    app.wechat.notifyReplyReady()
                    if (awaitCompletion != null) {
                        _stage.value = AmooRelayStage.SettlingAmoo
                        keepChat = awaitCompletion()
                        if (!keepChat) android.util.Log.w(TAG, "Amoo chat completion was not confirmed before releasing its display")
                    }
                } finally {
                    withContext(NonCancellable) { closeBackground(keepChat)?.join() }
                    _stage.value = AmooRelayStage.Idle
                }
            }
        }
    }

    @Synchronized fun stop() {
        worker?.let { it.cancel(); previousWorker = it }
        worker = null
        val keepChat = _stage.value != AmooRelayStage.SettlingAmoo
        _stage.value = AmooRelayStage.Idle
        closeBackground(keepChat)
    }

    /**
     * 微信侧的 /new:在 Amoo 里开一个新会话,并忘掉本机记住的会话标记——下一条消息起都进新会话。
     * 聊天由系统 Amoo 处理,这里只负责等聊天就绪、点「新会话」并等它切换。
     * 标记无论如何都会清掉:这次没点成,下一条消息会按新会话再试(见 [chat] 里的同款分支)。
     */
    private suspend fun startFreshSession(): String {
        // 等 Amoo 聊天就绪:输入框出现了才谈得上点「新会话」(Activity 刚起来时无障碍树还没长全)
        val readyBy = SystemClock.uptimeMillis() + 12_000
        var before = emptyList<String>()
        while (SystemClock.uptimeMillis() < readyBy && before.isEmpty()) {
            withRoot { root ->
                if (find(root, "et_input_bar") != null) before = chatTexts(root)
            }
            if (before.isEmpty()) delay(200)
        }
        if (before.isEmpty()) {
            resetSessionMarkers()
            return "Amoo 聊天还没就绪。会话标记已重置，下一条消息会重试开新会话；若持续失败请到 Amoo 手动新建。"
        }
        // 点「新会话」
        val clickBy = SystemClock.uptimeMillis() + 6_000
        var clicked = false
        while (SystemClock.uptimeMillis() < clickBy && !clicked) {
            withRoot { root ->
                find(root, "btn_new_session")?.let { fresh ->
                    try { clicked = fresh.performAction(AccessibilityNodeInfo.ACTION_CLICK) } finally { fresh.recycle() }
                }
            }
            if (!clicked) delay(200)
        }
        if (!clicked) {
            resetSessionMarkers()
            return "没能开始新会话：Amoo 聊天里没有「新会话」按钮。请到 Amoo 手动新建一个会话，再发消息。"
        }
        // 等聊天换成新内容(通常直接清空)
        val switchBy = SystemClock.uptimeMillis() + 6_000
        var switched = false
        while (SystemClock.uptimeMillis() < switchBy && !switched) {
            val now = withRoot { root -> chatTexts(root) } ?: emptyList()
            switched = now.toString() != before.toString()
            if (!switched) delay(200)
        }
        resetSessionMarkers()
        return if (switched) "已开始新的 Amoo 会话。下一条消息起都是新对话。"
        else "已点「新会话」但聊天还没切换成功。下一条消息会重试；若仍不行，请到 Amoo 手动新建会话。"
    }

    /**
     * 忘掉本机记住的 Amoo 会话(会话编号、上一条消息和回复)。下一条消息到来时按新会话处理:
     * 聊天里有旧内容就点「新会话」,没有就直接用。clawbot 新绑定(重连)时也调它——
     * 连上之后从新会话开始,不接着旧的聊。
     */
    fun resetSessionMarkers() {
        prefs.edit().remove("session").remove("lastMessage").remove("lastReply").remove("lastReplyRequest").apply()
    }

    private data class ChatResult(val text: String, val awaitCompletion: (suspend () -> Boolean)? = null)

    private suspend fun chat(request: Request): ChatResult {
        if (background && !backgroundReady) throw AmooUiException("手机助手未连接，后台接入不会自动切到前台。请先连接手机助手。")
        if (!available) throw AmooUiException("请先开启 Maeve 无障碍服务，再通过微信与系统 Amoo 对话。")
        lastError = null
        openForRequest()
        if (request.text.trim() == NEW_SESSION_COMMAND) return ChatResult(startFreshSession())
        val allow = withContext(Dispatchers.IO) { app.wechat.syncControl() }
        val readyAt = SystemClock.uptimeMillis() + 12_000
        var prepared = false
        val message = systemAmooMessage(request.text, app.packageName, allow, wechatPhoneRequestId(request.id))
        var baseline = emptyList<String>()
        while (SystemClock.uptimeMillis() < readyAt && !prepared) {
            withRoot { root ->
                val input = find(root, "et_input_bar") ?: return@withRoot
                try {
                    val draft = input.text?.toString().orEmpty()
                    if (draft.isNotBlank() && draft != input.hintText?.toString()) throw AmooUiException("Amoo 输入框中已有草稿，Maeve 未覆盖它。请先处理草稿后重试。")
                    if (isBusy(root)) return@withRoot
                    baseline = chatTexts(root)
                    val previousSession = prefs.getString("session", null)
                    val previousMessage = prefs.getString("lastMessage", null)
                    val previousReply = prefs.getString("lastReply", null)
                    if (previousSession != request.session && baseline.isNotEmpty()) {
                        val fresh = find(root, "btn_new_session")
                            ?: throw AmooUiException("请先在 Amoo 新建一个微信专用对话，Maeve 不会覆盖现有对话。")
                        try { fresh.performAction(AccessibilityNodeInfo.ACTION_CLICK) } finally { fresh.recycle() }
                        return@withRoot
                    }
                    if (previousSession == request.session && baseline.isNotEmpty() &&
                        previousMessage !in baseline && previousReply !in baseline) {
                        throw AmooUiException("Amoo 当前显示的是其他对话。请先回到微信专用的 Amoo 对话后重试，避免混入别的会话。")
                    }
                    val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message) }
                    if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return@withRoot
                    prepared = true
                } finally { input.recycle() }
            }
            if (!prepared) delay(150)
        }
        if (!prepared) throw AmooUiException("未能打开系统 Amoo 聊天输入框。请解锁手机并打开 Amoo 后重试。")
        var sent = false
        val sendDeadline = SystemClock.uptimeMillis() + 5_000
        while (!sent && SystemClock.uptimeMillis() < sendDeadline) {
            withRoot { root ->
                val send = find(root, "btn_text_send") ?: return@withRoot
                try { sent = send.performAction(AccessibilityNodeInfo.ACTION_CLICK) } finally { send.recycle() }
            }
            if (!sent) delay(150)
        }
        if (!sent) throw AmooUiException("未能发送至系统 Amoo。输入内容仍保留在 Amoo，请先确认是否已发送，避免重复。")
        _stage.value = AmooRelayStage.WaitingForAmoo
        val sentAt = SystemClock.uptimeMillis()
        android.util.Log.i(TAG, "Amoo message sent after ${(System.currentTimeMillis() - request.createdAt).coerceAtLeast(0)} ms")
        prefs.edit().putString("session", request.session).putString("lastMessage", message).apply()
        val deadline = SystemClock.uptimeMillis() + if (allow) 15 * 60_000 else 120_000
        // 流式回复:提取到的文本要连续两拍不变才算数,否则会把半截(按钮已出现、文字还在长)当成答案
        var lastSeen: String? = null
        var stableSince = 0L
        var nextReceipt = 0L
        var receipt: WechatPhoneResult? = null
        suspend fun finish(answer: WechatAmooReply): ChatResult {
            withContext(Dispatchers.IO) {
                prefs.edit().putString("lastReply", answer.text).putString("lastReplyRequest", request.id).commit()
            }
            val chatDisplay = targetDisplay
            val awaitCompletion: (suspend () -> Boolean)? = if (answer.settleChat && chatDisplay > 0) {
                {
                    awaitAmooChatCompletion(SystemClock::uptimeMillis, CHAT_SETTLE_TIMEOUT_MS) {
                        withRoot(chatDisplay) { root ->
                            SystemAmooTranscript.reply(chatTexts(root), baseline, message, isBusy(root))
                        }
                    }
                }
            } else null
            return ChatResult(answer.text, awaitCompletion)
        }
        while (SystemClock.uptimeMillis() < deadline) {
            val now = SystemClock.uptimeMillis()
            if (allow && now >= nextReceipt) {
                nextReceipt = now + RECEIPT_POLL_MS
                withContext(Dispatchers.IO) { phoneResult(request) }?.let {
                    if (receipt == null) android.util.Log.i(TAG, "Maeve receipt appeared ${now - sentAt} ms after sending to Amoo")
                    receipt = it
                    if (it.running) _stage.value = if (it.waitingForUser) AmooRelayStage.WaitingForPhone else AmooRelayStage.RunningOnMaeve
                }
            }
            // 接单后只跟踪对应的本机回执，避免和执行任务争用无障碍读取。
            val answer = if (receipt == null) withRoot { root ->
                SystemAmooTranscript.reply(chatTexts(root), baseline, message, isBusy(root))
            } else null
            if (answer != lastSeen) {
                lastSeen = answer
                stableSince = now
            }
            val stable = answer?.takeIf { now - stableSince >= REPLY_STABLE_MS }
            selectWechatAmooReply(receipt, stable)?.let { return finish(it) }
            delay(if (receipt?.running == true) RECEIPT_POLL_MS else 150L)
        }
        throw AmooUiException("系统 Amoo 暂未完成回复。请打开 Amoo 对话查看；任务可能仍在运行，请核实后再重试。")
    }

    private fun <T> withRoot(displayId: Int = targetDisplay, block: (AccessibilityNodeInfo) -> T): T? {
        val service = ScreenAccessibility.instance.value ?: return null
        val root = if (displayId == 0) service.rootInActiveWindow else {
            var selected: AccessibilityNodeInfo? = null
            for (window in service.windowsOn(displayId)) {
                val candidate = window.root ?: continue
                if (candidate.packageName?.toString() == PACKAGE && selected == null) selected = candidate else candidate.recycle()
            }
            selected
        } ?: return null
        return try { if (root.packageName?.toString() == PACKAGE) block(root) else null } finally { root.recycle() }
    }

    private fun find(root: AccessibilityNodeInfo, name: String): AccessibilityNodeInfo? {
        val matches = root.findAccessibilityNodeInfosByViewId("$PACKAGE:id/$name")
        val first = matches.firstOrNull()
        matches.drop(1).forEach { it.recycle() }
        return first
    }

    private fun isBusy(root: AccessibilityNodeInfo): Boolean = listOf("btn_stop", "btn_stop_send", "btn_stop_generate").any { name ->
        find(root, name)?.let { it.recycle(); true } == true
    }

    private fun chatTexts(root: AccessibilityNodeInfo): List<String> {
        val list = find(root, "chat_list") ?: return emptyList()
        return try {
            val texts = mutableListOf<String>()
            fun walk(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 40) return
                node.text?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
                if (node.viewIdResourceName == "$PACKAGE:id/iv_copy" && node.isVisibleToUser) texts += "复制"
                for (index in 0 until node.childCount) node.getChild(index)?.let { child ->
                    try { walk(child, depth + 1) } finally { child.recycle() }
                }
            }
            walk(list, 0)
            texts
        } finally { list.recycle() }
    }

    private data class Request(val id: String, val session: String, val text: String, val sequence: Long, val createdAt: Long)
    private fun read(file: File): Request? = runCatching {
        val value = Json.parseToJsonElement(file.readText()).jsonObject
        Request(value.getValue("id").jsonPrimitive.content, value.getValue("session").jsonPrimitive.content,
            value.getValue("text").jsonPrimitive.content, value["sequence"]?.jsonPrimitive?.longOrNull ?: 0,
            value["createdAt"]?.jsonPrimitive?.longOrNull ?: file.lastModified())
    }.getOrNull()

    private fun phoneResult(request: Request): WechatPhoneResult? {
        val id = wechatPhoneRequestId(request.id)
        val root = app.externalMediaDirs.firstOrNull() ?: return null
        val result = File(root, "amoo/out/$id.json")
        if (!result.isFile || result.length() > 64 * 1024) return null
        return runCatching { parseWechatPhoneResult(id, result.readText()) }.getOrNull()
    }

    private suspend fun recoverPhoneResult(request: Request): String? {
        var result = phoneResult(request) ?: return null
        val deadline = request.createdAt + WechatAmooRelay.REPLY_TIMEOUT_MS
        while (result.running && System.currentTimeMillis() < deadline) {
            delay(500)
            result = phoneResult(request) ?: result
        }
        return result.text
    }

    private fun reply(request: Request, text: String) {
        WechatAmooRelay.atomic(File(directory, "replies/${request.id}.json"), buildJsonObject {
            put("id", request.id); put("session", request.session); put("text", text)
        }.toString())
    }

    private class AmooUiException(message: String) : Exception(message)
    companion object {
        const val PACKAGE = "com.stepos.ailauncher"
        private const val TAG = "SystemAmooWechat"

        /** 微信侧开新 Amoo 会话的指令(clawbot 的 /new)。 */
        private const val NEW_SESSION_COMMAND = "/new"

        /** am start 之后等 Amoo 在后台屏上落地这么久;任务建起来要一点时间。 */
        private const val LAND_WAIT_MS = 3_000L

        private const val RECEIPT_POLL_MS = 500L
        private const val CHAT_SETTLE_TIMEOUT_MS = 30_000L
    }
}

/** 流式回复稳定后才把它当作本轮完成。 */
private const val REPLY_STABLE_MS = 600L

internal suspend fun awaitAmooChatCompletion(now: () -> Long, timeoutMs: Long, readReply: () -> String?): Boolean =
    withTimeoutOrNull(timeoutMs) {
        var previous: String? = null
        var stableSince = now()
        while (isActive) {
            val reply = readReply()
            val at = now()
            if (reply != previous) {
                previous = reply
                stableSince = at
            }
            if (reply != null && at - stableSince >= REPLY_STABLE_MS) return@withTimeoutOrNull true
            delay(250)
        }
        false
    } ?: false

internal fun recoveredSystemAmooReply(id: String, session: String, savedId: String?, savedSession: String?, text: String?): String? =
    text?.takeIf { id == savedId && session == savedSession && it.isNotBlank() }

internal fun wechatPhoneRequestId(id: String): String = "wx-${id.take(56)}"

internal data class WechatPhoneResult(val running: Boolean, val text: String? = null, val waitingForUser: Boolean = false)

internal data class WechatAmooReply(val text: String, val settleChat: Boolean = false)

internal fun selectWechatAmooReply(receipt: WechatPhoneResult?, stableTranscript: String?): WechatAmooReply? {
    receipt?.text?.let { return WechatAmooReply(it, settleChat = stableTranscript == null) }
    return stableTranscript?.takeUnless { receipt?.running == true }?.let { WechatAmooReply(it) }
}

internal fun parseWechatPhoneResult(expectedId: String, raw: String): WechatPhoneResult? {
    val json = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    fun text(key: String) = (json[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    if (text("id") != expectedId) return null
    return when (text("status")) {
        "running" -> WechatPhoneResult(running = true, waitingForUser = text("waiting_for") in setOf("confirmation", "phone"))
        "done" -> WechatPhoneResult(false, text("answer") ?: text("note") ?: "Maeve 已完成任务。")
        "failed" -> WechatPhoneResult(false, text("note") ?: "Maeve 未能完成任务。")
        "cancelled" -> WechatPhoneResult(false, text("note") ?: "Maeve 任务已取消。")
        else -> null
    }
}

/** 只转发本轮完成后的实际答复，不用提示词伪装系统助手身份。 */
internal object SystemAmooTranscript {
    fun reply(current: List<String>, baseline: List<String>, message: String, busy: Boolean): String? {
        if (busy) return null
        val own = current.indexOfLast { it == message }
        val fresh = if (own >= 0) current.drop(own + 1) else {
            val counts = baseline.groupingBy { it }.eachCount().toMutableMap()
            current.filter { value -> val n = counts[value] ?: 0; if (n > 0) { counts[value] = n - 1; false } else true }
        }
        if (fresh.any { it.matches(Regex("正在(思考|生成)[.。…]*")) }) return null
        if (fresh.none { it.startsWith("已完成") || it == "复制" || it == "重新生成" }) return null
        val answer = fresh.filterNot { it == message || it.startsWith("已完成") || it in setOf("复制", "重新生成", "点赞", "点踩") }
            .filterNot { it.isBlank() || it.matches(Regex("\\d+(s|秒|min|分钟)")) }
            .joinToString("\n\n").trim()
        return answer.takeIf { it.isNotBlank() }
    }
}

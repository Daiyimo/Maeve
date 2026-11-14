package com.daiyimo.maeve.wechat

import android.content.Intent
import android.app.ActivityOptions
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.cap.phone.a11y.ScreenAccessibility
import com.daiyimo.maeve.settings.FeatureFlags
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.io.File

/** 使用系统 Amoo 的真实聊天界面；不调用 OpenClaw 默认 agent 或 Maeve 模型。 */
class SystemAmooWechatBridge(private val app: MaeveApp, private val store: WechatStore, val directory: File) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var worker: Job? = null
    private val requestReady = Channel<Unit>(Channel.CONFLATED)
    fun notifyRequestReady() { requestReady.trySend(Unit) }
    @Volatile var active = false
        private set
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
            if (result.getInt("exitCode", -1) != 0 || shell.topTaskId(0) != before) {
                throw AmooUiException("这台手机未能保持 Amoo 在后台屏幕中，已停止此次处理。请改用前台接入。")
            }
        }
    }

    private fun closeBackground(): Job? {
        val screen = display
        val connection = binding
        display = null; binding = null; targetDisplay = 0
        if (connection == null) return cleanup
        cleanup = scope.launch(Dispatchers.IO) {
            if (screen != null) {
                runCatching {
                    connection.shell.value?.finishDisplay(screen.displayId, arrayOf(PACKAGE), emptyArray())?.let { result ->
                        android.util.Log.i("SystemAmooWechat", "后台屏幕收尾 kept=${result.getStringArray("kept")?.size ?: 0} note=${result.getString("note")}")
                    }
                }
                screen.release()
            }
            app.phone.sharedShell.release()
        }
        return cleanup
    }

    @Synchronized fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            // 只在新进程/新监听启动时恢复已认领消息，不重放可能已经发出的内容。
            withContext(Dispatchers.IO) {
                File(directory, "requests").listFiles()?.filter { it.extension == "working" }?.forEach { file ->
                    read(file)?.let { request ->
                        if (!File(directory, "replies/${request.id}.json").exists()) {
                            reply(request, "上次系统 Amoo 对话在中途停止，结果尚未确认。请先在 Amoo 检查，再决定是否重试。")
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
                active = true
                try {
                    val response = try {
                        chat(request)
                    } catch (e: CancellationException) { throw e }
                    catch (e: AmooUiException) { lastError = e.message; e.message.orEmpty() }
                    catch (_: Exception) {
                        "系统 Amoo 对话未能完成。请检查无障碍服务，并在 Amoo 页面确认当前对话。".also { lastError = it }
                    }
                    // 最终答案先投递，后台屏幕收尾不再阻塞微信回传。
                    withContext(Dispatchers.IO) { reply(request, response) }
                    app.wechat.notifyReplyReady()
                } finally {
                    withContext(NonCancellable) { closeBackground()?.join() }
                    active = false
                }
            }
        }
    }

    @Synchronized fun stop() { worker?.cancel(); worker = null; active = false; closeBackground() }

    private suspend fun chat(request: Request): String {
        if (background && !backgroundReady) throw AmooUiException("手机助手未连接，后台接入不会自动切到前台。请先连接手机助手。")
        if (!available) throw AmooUiException("请先开启 Maeve 无障碍服务，再通过微信与系统 Amoo 对话。")
        lastError = null
        openForRequest()
        val readyAt = SystemClock.uptimeMillis() + 12_000
        var prepared = false
        val allow = FeatureFlags.get(app).bridgeAmoo()
        app.wechat.syncControl()
        val message = systemAmooMessage(request.text, app.packageName, allow)
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
        prefs.edit().putString("session", request.session).putString("lastMessage", message).apply()
        val deadline = SystemClock.uptimeMillis() + if (allow) 15 * 60_000 else 120_000
        while (SystemClock.uptimeMillis() < deadline) {
            var response: String? = null
            withRoot { root ->
                response = SystemAmooTranscript.reply(chatTexts(root), baseline, message, isBusy(root))
            }
            response?.let {
                prefs.edit().putString("lastReply", it).putString("lastReplyRequest", request.id).apply()
                return it
            }
            delay(150)
        }
        throw AmooUiException("系统 Amoo 暂未完成回复。请打开 Amoo 对话查看；任务可能仍在运行，请核实后再重试。")
    }

    private fun <T> withRoot(block: (AccessibilityNodeInfo) -> T): T? {
        val service = ScreenAccessibility.instance.value ?: return null
        val root = if (targetDisplay == 0) service.rootInActiveWindow else {
            var selected: AccessibilityNodeInfo? = null
            for (window in service.windowsOn(targetDisplay)) {
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

    private data class Request(val id: String, val session: String, val text: String, val sequence: Long)
    private fun read(file: File): Request? = runCatching {
        val value = Json.parseToJsonElement(file.readText()).jsonObject
        Request(value.getValue("id").jsonPrimitive.content, value.getValue("session").jsonPrimitive.content,
            value.getValue("text").jsonPrimitive.content, value["sequence"]?.jsonPrimitive?.longOrNull ?: 0)
    }.getOrNull()
    private fun reply(request: Request, text: String) {
        WechatAmooRelay.atomic(File(directory, "replies/${request.id}.json"), buildJsonObject {
            put("id", request.id); put("session", request.session); put("text", text)
        }.toString())
    }

    private class AmooUiException(message: String) : Exception(message)
    companion object { const val PACKAGE = "com.stepos.ailauncher" }
}

/** 只转发本轮完成后的实际答复，不用提示词伪装系统助手身份。 */
internal object SystemAmooTranscript {
    fun reply(current: List<String>, baseline: List<String>, message: String, busy: Boolean): String? {
        if (busy || current.any { it.matches(Regex("正在(思考|生成)[.。…]*")) }) return null
        val own = current.indexOfLast { it == message }
        val fresh = if (own >= 0) current.drop(own + 1) else {
            val counts = baseline.groupingBy { it }.eachCount().toMutableMap()
            current.filter { value -> val n = counts[value] ?: 0; if (n > 0) { counts[value] = n - 1; false } else true }
        }
        if (fresh.none { it.startsWith("已完成") || it == "复制" || it == "重新生成" }) return null
        val answer = fresh.filterNot { it == message || it.startsWith("已完成") || it in setOf("复制", "重新生成", "点赞", "点踩") }
            .filterNot { it.isBlank() || it.matches(Regex("\\d+(s|秒|min|分钟)")) }
            .joinToString("\n\n").trim()
        return answer.takeIf { it.isNotBlank() }
    }
}

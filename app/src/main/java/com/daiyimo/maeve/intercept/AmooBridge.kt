package com.daiyimo.maeve.intercept

import android.util.Log
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.core.remote.RemoteStatus
import com.daiyimo.maeve.core.remote.RemoteTaskService
import com.daiyimo.maeve.settings.FeatureFlags
import com.daiyimo.maeve.core.agent.TaskState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * amoo 文件桥。
 *
 * amoo(跑在 stepclaw 的沙箱里)与 Maeve 的网络命名空间不同:它能连外网,但连不到手机的 127.0.0.1,
 * 手机的 18770 它也看不见。两边唯一都读得到的地方是 /sdcard/Android/media/<包名>/ ——
 * 沙箱挂载了 /sdcard,Maeve 又是这个目录的属主,于是用它当信箱:
 *
 *   <root>/in/<id>.json   {"goal": "要做的事"}        amoo 写,Maeve 读到就开工
 *   <root>/out/<id>.json  {"status": "done", ...}     Maeve 写,amoo 读结果
 *
 * 开关见 [FeatureFlags.bridgeAmoo]。协议文本见 docs/dev/amoo-bridge.md。
 */
class AmooBridge(private val app: MaeveApp) {

    private val tasks = RemoteTaskService(app.remoteTasks)
    private val flags = FeatureFlags.get(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = AtomicInteger(0)

    @Volatile
    private var poller: Job? = null

    init {
        scope.launch {
            // 关闭执行时仍回绝新请求，避免它们积压后在开关打开时突然执行。
            start()
        }
    }

    /** 信箱根目录:外部存储没挂上就返回 null(不报错,下次轮询再试)。 */
    private fun root(): File? {
        val media = app.getExternalMediaDirs().firstOrNull() ?: return null
        return File(media, DIR)
    }

    @Synchronized
    private fun start() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            Log.i(TAG, "file bridge on")
            while (isActive) {
                runCatching { sweep() }.onFailure { Log.w(TAG, "sweep failed: ${it.message}") }
                delay(POLL_MS)
            }
        }
    }

    private suspend fun sweep() {
        if (inFlight.get() >= MAX_IN_FLIGHT) return
        val root = root() ?: return
        val inbox = File(root, IN)
        if (!inbox.isDirectory && !inbox.mkdirs()) return
        val requests = inbox.listFiles()?.filter { it.isFile && it.name.endsWith(".json") } ?: return
        for (file in requests.sortedBy { it.name }.take(MAX_IN_FLIGHT - inFlight.get())) {
            val id = file.name.removeSuffix(".json")
            if (!ID.matches(id)) continue
            // 先改名占位:同一个请求不会被两轮扫地重复认领。
            val claimed = File(inbox, "$id.claimed")
            if (!file.renameTo(claimed)) continue
            val body = runCatching { claimed.readText() }.getOrNull() ?: continue
            val req = parseAmooRequest(body)
            if (req?.goal.isNullOrBlank()) {
                writeResult(root, id, null, "failed", null, "request is not a {\"goal\":\"...\"} object")
                claimed.delete()
                continue
            }
            claimed.delete()
            inFlight.incrementAndGet()
            scope.launch {
                try {
                    handle(root, id, req!!.goal)
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }
    }

    private suspend fun handle(root: File, id: String, goal: String) {
        if (!flags.bridgeAmoo()) {
            writeResult(root, id, null, "failed", null, "用户已关闭通过 Maeve 执行任务；Amoo 聊天仍可使用。")
            return
        }
        val requestId = UUID.randomUUID().toString()
        val started = runCatching { tasks.start(goal, null, requestId) }
        val taskId = started.getOrNull()?.id
        if (taskId == null) {
            val why = started.exceptionOrNull()?.message ?: "Maeve refused to start the task"
            writeResult(root, id, null, "failed", null, why)
            Log.w(TAG, "start refused: $why")
            return
        }
        Log.i(TAG, "request $id -> task $taskId (${goal.length} chars)")
        writeResult(root, id, taskId, "running", null, null)
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_MS)
            val state = runCatching { tasks.get(taskId)?.state }.getOrNull() ?: continue
            val status = RemoteStatus.of(state)
            if (!RemoteStatus.finished(status)) continue
            val answer = (state as? TaskState.Succeeded)?.answer
            val reason = (state as? TaskState.Failed)?.reason
            writeResult(root, id, taskId, status, answer, reason)
            Log.i(TAG, "request $id finished: $status")
            return
        }
        writeResult(
            root, id, taskId, "running", null,
            "still running after ${WAIT_MS / 1000}s; ask again or check the task list"
        )
    }

    private suspend fun writeResult(root: File, id: String, taskId: String?, status: String, answer: String?, note: String?) {
        withContext(Dispatchers.IO) {
            runCatching {
                val out = File(root, OUT).apply { mkdirs() }
                val tmp = File(out, "$id.tmp")
                tmp.writeText(amooResultJson(id, taskId, status, answer, note))
                if (!tmp.renameTo(File(out, "$id.json"))) {
                    File(out, "$id.json").writeText(amooResultJson(id, taskId, status, answer, note))
                    tmp.delete()
                }
            }.onFailure { Log.w(TAG, "write result $id failed: ${it.message}") }
        }
    }

    companion object {
        const val TAG = "AmooBridge"
        const val DIR = "amoo"
        const val IN = "in"
        const val OUT = "out"
        const val POLL_MS = 1_500L
        const val WAIT_MS = 15 * 60_000L
        const val MAX_IN_FLIGHT = 4
        val ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}

/** amoo 写进来的请求:{ "goal": "..." }。格式不认识就返回 null,不让它建空任务。 */
internal data class AmooRequest(val goal: String)

internal fun parseAmooRequest(body: String): AmooRequest? {
    val goal = jsonString(body, "goal")?.trim()
    if (goal.isNullOrEmpty()) return null
    return AmooRequest(goal.take(4000))
}

/** 写回给 amoo 的结果。字段名叫 status/answer,和 Maeve 的 /api/v1 任务接口一致,便于它复用同一套理解。 */
internal fun amooResultJson(id: String, taskId: String?, status: String, answer: String?, note: String?): String =
    buildString {
        append('{')
        append("\"id\":").append(quote(id))
        append(",\"task_id\":").append(taskId?.let(::quote) ?: "null")
        append(",\"status\":").append(quote(status))
        append(",\"answer\":").append(answer?.let(::quote) ?: "null")
        append(",\"note\":").append(note?.let(::quote) ?: "null")
        append('}')
    }

/**
 * 只认我们自己约定的平铺 JSON;够用就好,不引 JSON 库(单测里也不想碰 android 的 org.json)。
 * 手写扫描而不是正则:goal 可能几千字,带分支的正则会在长串上把栈打爆。
 */
internal fun jsonString(body: String, name: String): String? {
    val key = "\"" + name + "\""
    var i = body.indexOf(key)
    while (i >= 0) {
        var c = i + key.length
        while (c < body.length && body[c].isWhitespace()) c++
        if (c < body.length && body[c] == ':') {
            c++
            while (c < body.length && body[c].isWhitespace()) c++
            if (c < body.length && body[c] == '"') {
                val sb = StringBuilder()
                c++
                while (c < body.length) {
                    val ch = body[c]
                    if (ch == '\\' && c + 1 < body.length) {
                        sb.append(ch).append(body[c + 1]); c += 2; continue
                    }
                    if (ch == '"') return unescape(sb.toString())
                    sb.append(ch); c++
                }
                return null
            }
        }
        i = body.indexOf(key, i + key.length)
    }
    return null
}

private fun quote(s: String): String = buildString {
    append('"')
    for (c in s) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    append('"')
}

private fun unescape(s: String): String = buildString {
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c != '\\' || i == s.length - 1) {
            append(c); i++; continue
        }
        when (val n = s[i + 1]) {
            '"' -> { append('"'); i += 2 }
            '\\' -> { append('\\'); i += 2 }
            '/' -> { append('/'); i += 2 }
            'n' -> { append('\n'); i += 2 }
            'r' -> { append('\r'); i += 2 }
            't' -> { append('\t'); i += 2 }
            'b' -> { append('\b'); i += 2 }
            'f' -> { append('\u000C'); i += 2 }
            'u' -> {
                val hex = s.substring(i + 2, minOf(i + 6, s.length))
                val v = hex.toIntOrNull(16)
                if (v == null) { append(n); i += 2 } else { append(v.toChar()); i += 6 }
            }
            else -> { append(n); i += 2 }
        }
    }
}

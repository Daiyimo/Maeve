package com.daiyimo.maeve.intercept

import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.settings.FeatureFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * 一键接入 amoo。
 *
 * 技能只能由 amoo 自己写进它自己的沙箱(/root/.openclaw/workspace/skills/),Maeve 没有那条路;
 * 但 Maeve 有无障碍服务,可以替用户把那段说明「打字 + 发送」到 amoo 聊天框里。
 * 于是:用户点一次按钮 → 打开 amoo 聊天 → 我们检测到 amoo 聊天页就把技能说明填进输入框并点发送 →
 * amoo 装好技能并按说明跑一次试任务 → 信箱里出现请求文件 ⇒ 接入成功。
 *
 * 这条链路只对同一套 system app(com.stepos.ailauncher 的 amoo 聊天)成立,
 * 输入框/发送按钮的 view id 写死在下面,换机型要重新核对。
 */
class AmooSetup(private val app: MaeveApp) {

    enum class Stage { Idle, Waiting, Sending, Verifying, Done, Failed }

    private val _stage = MutableStateFlow(Stage.Idle)
    val stage: StateFlow<Stage> = _stage

    /** 接入流程进行中:截获关着也要收无障碍事件,否则没人替我们填输入框。 */
    val active: Boolean
        get() = _stage.value == Stage.Waiting || _stage.value == Stage.Sending || _stage.value == Stage.Verifying


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var injectedAt = 0L
    private var repairingWechat = false
    private var workerBeforePid: String? = null

    /** 用户点了「一键接入」。等他自己切到 amoo 聊天,剩下的我们来做。 */
    fun start() = begin(false)

    fun restartWechat() = begin(true)

    private fun begin(repair: Boolean) {
        repairingWechat = repair
        workerBeforePid = workerPid()
        // 接入成功后 amoo 要靠文件桥派活,顺手把桥打开(截获那类有隐私含义的开关不代开)
        if (!repair) FeatureFlags.get(app).setBridgeAmoo(true)
        val relayDir = app.externalMediaDirs.firstOrNull()?.let { File(it, "amoo/wechat") }
        if (relayDir != null) {
            relayDir.mkdirs()
            com.daiyimo.maeve.wechat.WechatAmooRelay.atomic(
                File(relayDir, "wechat_worker.py"), app.assets.open("amoo/wechat_worker.py").bufferedReader().use { it.readText() })
            app.wechat.relay.syncControl()
        }
        inbox().mkdirs()
        injectedAt = 0L
        _stage.value = Stage.Waiting
        Log.i(TAG, "setup started")
        watch?.cancel()
        watch = scope.launch {
            delay(WAIT_CHAT_MS)
            if (_stage.value == Stage.Waiting) fail("amoo chat not opened")
        }
    }

    fun cancel() {
        watch?.cancel()
        _stage.value = Stage.Idle
    }

    /**
     * amoo 聊天页每次扫描都会调进来。返回 true 表示这一轮归接入流程,截获别再插手
     * (否则我们自己填进去的那段说明会被当成用户消息再建一个任务)。
     */
    fun onAmooChat(root: AccessibilityNodeInfo): Boolean = when (_stage.value) {
        Stage.Waiting -> fill(root)
        Stage.Sending -> send(root)
        else -> false
    }

    private fun fill(root: AccessibilityNodeInfo): Boolean {
        val input = find(root, ID_INPUT) ?: return false
        val ok = runCatching {
            input.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, instruction())
                },
            )
        }.getOrDefault(false)
        recycle(input)
        if (!ok) return false
        injectedAt = System.currentTimeMillis()
        _stage.value = Stage.Sending
        Log.i(TAG, "setup: instruction filled (${instruction().length} chars)")
        return true
    }

    private fun send(root: AccessibilityNodeInfo): Boolean {
        // 刚 setText 的那一下 amoo 可能还没把发送按钮换出来,下一次内容变化再试
        if (System.currentTimeMillis() - injectedAt < 300) return true
        val button = find(root, ID_SEND)
        if (button == null) {
            val input = find(root, ID_INPUT)
            val left = input?.text?.toString().orEmpty()
            recycle(input)
            // 输入框已经空了 = 消息发出去了
            if (left.isBlank()) startWatching()
            return true
        }
        val clicked = runCatching { button.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        recycle(button)
        Log.i(TAG, "setup: send clicked=$clicked")
        startWatching()
        return true
    }

    /** 盯着 out 目录:三分钟内出现新的结果文件,就说明 amoo 真的会用桥了。
     *  in/ 里的请求文件会被桥领走后删掉,所以只能看 out/。 */
    private fun startWatching() {
        if (_stage.value == Stage.Verifying) return
        _stage.value = Stage.Verifying
        val before = outbox().list()?.toSet() ?: emptySet()
        watch?.cancel()
        watch = scope.launch {
            val deadline = System.currentTimeMillis() + VERIFY_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(POLL_MS)
                if (app.wechat.relay.workerReady() && workerPid() != workerBeforePid &&
                    (repairingWechat || skillVersion() == SKILL_VERSION)) {
                    _stage.value = Stage.Done
                    return@launch
                }
                val now = outbox().list()?.toSet() ?: continue
                val fresh = now - before
                if (fresh.any { it.endsWith(".json") }) {
                    Log.i(TAG, "setup: amoo finished ${fresh.joinToString()}")
                    // 版本对不上说明跑的是旧技能,明确报错,别假装接好了
                    val installed = skillVersion()
                    if (!repairingWechat && installed != null && installed != SKILL_VERSION) {
                        fail("skill version $installed != $SKILL_VERSION")
                        return@launch
                    }
                    Log.i(TAG, "setup: skill version ${installed ?: "unknown"}")
                    // 任务结果不能代替微信中转心跳；继续等真正的新版本中转启动。
                }
            }
            fail("amoo did not use the bridge")
        }
    }

    private fun fail(why: String) {
        Log.w(TAG, "setup failed: $why")
        _stage.value = Stage.Failed
    }

    private fun inbox(): File {
        val media = app.getExternalMediaDirs().firstOrNull()
        return if (media == null) File(app.filesDir, AmooBridge.DIR) else File(File(media, AmooBridge.DIR), AmooBridge.IN)
    }

    private fun outbox(): File = File(inbox().parentFile, AmooBridge.OUT)

    private fun instruction(): String = if (repairingWechat) wechatRepairInstruction(app.packageName)
        else setupInstruction(app.packageName, SKILL_VERSION)

    private fun workerPid(): String? = runCatching {
        val media = app.externalMediaDirs.firstOrNull() ?: return null
        Regex("\"pid\"\\s*:\\s*(\\d+)").find(File(media, "amoo/wechat/worker.json").readText())?.groupValues?.get(1)
    }.getOrNull()

    private fun find(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? =
        runCatching { root.findAccessibilityNodeInfosByViewId("$AMOO_PKG:id/$id")?.firstOrNull() }.getOrNull()

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo?) {
        runCatching { node?.recycle() }
    }
    /** amoo 装完技能会写这个标记(技能名 + 版本号),用来确认是覆盖升级,不是并存了一个旧版。 */
    private fun marker(): File = File(inbox().parentFile, MARKER)

    /** 标记里的版本号;文件不存在或格式不认识就是 null(老版本没有这一步)。 */
    internal fun skillVersion(): Int? = runCatching { parseSkillVersion(marker().readText()) }.getOrNull()

    private companion object {
        const val TAG = "AmooSetup"
        const val AMOO_PKG = "com.stepos.ailauncher"
        const val ID_INPUT = "et_input_bar"
        const val ID_SEND = "btn_text_send"
        const val WAIT_CHAT_MS = 3 * 60_000L
        const val VERIFY_MS = 3 * 60_000L
        const val POLL_MS = 2_000L

        /** 技能契约版本:改动说明口径/桥协议就 +1,旧版本跑着一看便知。 */
        const val SKILL_VERSION = 5
    }
}

/** 从 amoo 写的 skill.json 里取版本号。 */
internal fun parseSkillVersion(body: String): Int? =
    Regex("\"version\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()

/** 版本标记文件名,放在信箱根目录(与 in/、out/ 同级)。 */
const val MARKER = "skill.json"

internal fun wechatRepairInstruction(pkg: String): String {
    val root = "/sdcard/Android/media/$pkg/amoo/wechat/"
    return """
        请修复 Maeve 微信中转，不解绑微信、不重跑历史消息、不改动模型或网关配置。
        执行 nohup python3 ${root}wechat_worker.py --restart --probe >${root}worker.log 2>&1 </dev/null &
        只启动这个后台命令，脚本会在后台读取 CLI 帮助并通过 Amoo 自身回复一句诊断问候，禁止调用 Maeve。
        不要同步运行 --diagnose，也不要用 10 秒或 20 秒超时终止后台诊断；诊断结果会自行写入 diagnostics.json。
        脚本会只停止同一信箱的旧中转进程，并用 mkdir 独占锁启动新进程。
        禁止修改脚本、绕过锁、替换锁函数、或者另外实现中转。
        读取 ${root}worker.json，确认 version 至少为 3、ready 为 true、at 在最近 30 秒内。
        若失败读取 worker.log 如实报告；不要宣称已成功，不要再次运行历史请求。
        除脚本内固定的 Amoo 诊断问候外，只做启动维护，不要额外发起对话或执行手机任务。
    """.trimIndent()
}

/**
 * 发给 amoo 的那段说明。中文写死:amoo 的手机端界面只有中文,这段文字是给它的模型读的,
 * 不走 Maeve 的界面语言。**每次都整份覆盖**同一个技能目录,不在沙箱里留第二个版本。
 */
internal fun setupInstruction(pkg: String, version: Int): String {
    val root = "/sdcard/Android/media/$pkg/amoo/"
    return """
        Maeve 一键接入(可重复执行,每次整份覆盖,不要并存多个版本)。
        1) 技能固定为 ~/.openclaw/workspace/skills/maeve-bridge/SKILL.md,frontmatter 写 name: maeve-bridge、version: $version。
        已经存在就直接覆盖这一个文件(以及它的正文/参考文件),不要新建 maeve-bridge-2、maeve-bridge-v2 之类;
        目录里如果有旧版本残留(别的名字的同款技能、旧备份目录),用 rm -rf 清掉,最后只留 maeve-bridge 一个。
        2) description 与正文统一按这个口径:用户请求只要涉及手机上任何屏幕内操作
        (微信、小程序、游戏、跨应用流程、点按、滑动、输入文字、读屏),一律交给手机端 Maeve 执行,
        不要自己尝试,也不要回复「界面功能不支持」;纯云侧查询(天气、提醒、打车、机票酒店)可以自己来。
        微信只是消息来源，不代表用户要求操作微信界面；问候、测试、聊天、写作、纯文本生成和云端查询必须由 Amoo 自己回复，禁止将回复微信用户的工作转成 Maeve 任务。
        3) 执行方式:把 {"goal":"<用中文写清要做的事>"} 写入 ${root}in/<时间戳>.json;
        然后每 2 秒读 ${root}out/<时间戳>.json,直到 status 不是 running;
        把 answer 原样告诉用户;最多等 15 分钟,中途不要放弃;failed 时把 note 说清楚。
        4) 装好后先覆盖写 ${root}skill.json,内容是 {"skill":"maeve-bridge","version":$version};
        本次仅安装接入，不要启动 Maeve 任务做问候测试，也不要求用户在 Maeve 配置模型；微信聊天必须由 Amoo 自己回复。
        5) 微信双向中转:先确认沙箱内 python3 和 openclaw agent 命令可用。
        运行 nohup python3 ${root}wechat/wechat_worker.py --restart >${root}wechat/worker.log 2>&1 </dev/null &，
        脚本已使用共享目录支持的 mkdir 独占锁；禁止绕过锁、替换锁函数或自行并行启动别的中转器。
        这个进程只监听共享信箱，由它调用 openclaw agent --session-id 保持微信专属会话，不能把微信消息直接当成 Maeve 任务。
        检查 ${root}wechat/worker.json 的 ready 为 true、version 至少为 3、at 为最近 30 秒内的毫秒时间戳；
        若失败请明确报告，不要声称微信中转已连接。不要复制或索取微信 token，也不要额外安装另一份微信渠道。
        每次手机操作前必须读取 ${root}wechat/control.json，allowMaeve 为 false 时只与用户聊天，不得调用 Maeve。
    """.trimIndent()
}

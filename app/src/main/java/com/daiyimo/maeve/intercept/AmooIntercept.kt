package com.daiyimo.maeve.intercept

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.daiyimo.maeve.MikaApp
import com.daiyimo.maeve.cap.phone.a11y.ScreenAccessibility
import com.daiyimo.maeve.core.remote.RemoteTaskService
import com.daiyimo.maeve.settings.FeatureFlags
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 截获 amoo 聊天里发送的任务,在 Mika 里新建任务执行。
 *
 * 背景:amoo(com.stepos.ailauncher)发出的消息走 AgentBus 云端优先,Mika 这台「本机操作手」
 * 会被绕过。没有 root 改不了 amoo/AgentBus 的路由,那就从 Mika 侧接:无障碍服务看着 amoo
 * 聊天页,用户每发一条消息,就按 MCP mika_run_task 的同一条底层路径
 * ([RemoteTaskService.start])在 Mika 里建一个任务,胶囊照常执行,任务页可见、可取消。
 * amoo 发往云端的消息不受影响,照常发送——Mika 是在本机并行做一遍,不是拦路。
 *
 * 判定见 [AmooSendDetector] 的三层闸门;这里的职责是 Android glue:读无障碍树、
 * 找输入框文本和气泡、命中后起任务。
 *
 * 线程:detector 的状态只在主线程读写(无障碍回调本来就在主线程),起任务才切到 IO。
 */
class AmooIntercept(private val app: MikaApp) {

    private val detector = AmooSendDetector()

    /**
     * detector 与 [lastScanMs] 都只在主线程动:无障碍回调在主线程,开关 Flow、重连补记也回到这里。
     * (早先用 Dispatchers.Default 收开关,和主线程的 scan 并发改 ArrayDeque,是数据竞争。)
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tasks = RemoteTaskService(app.remoteTasks)
    private val flags = FeatureFlags.get(app)

    @Volatile
    private var enabled = false
    private var lastScanMs = 0L

    /** 上一次记进日志的输入框内容/草稿,只在变化时打一行,免得 3 行每秒把日志刷满。 */
    private var loggedInput: String? = null
    private var loggedDraft: String? = null

    /** 已经算过基线的那段输入,以及「开始打这段字之前,聊天记录里就已经有同样的文本」。 */
    private var baselineInput: String? = null
    private var bubbleAlreadyPresent = false

    init {
        // 开关跟着设置页走:改设置立刻生效,不用重启无障碍服务
        scope.launch {
            flags.interceptAmooChanges().collect { on ->
                Log.i(TAG, "interceptAmoo flag -> $on")
                enabled = on
                loggedInput = null
                loggedDraft = null
                baselineInput = null
                bubbleAlreadyPresent = false
                if (!on) detector.reset()
            }
        }
    }

    /**
     * 无障碍服务连上时同步一次前台:服务被 freeze 打断重连是常事(~20s 一次),
     * 断开期间的窗口切换事件全部丢失,不主动补一笔,amoo 前台就永远发现不了。
     */
    fun onServiceConnected(service: ScreenAccessibility) {
        scope.launch {
            delay(300) // 刚连上时 rootInActiveWindow 可能还没就绪
            val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return@launch
            try {
                if (root.packageName?.toString() == AmooSendDetector.AMOO_PACKAGE) {
                    detector.noteAmooForeground()
                    Log.i(TAG, "foreground synced: amoo, inAmooChat=${detector.inAmooChat}")
                }
            } finally {
                recycleRoot(root)
            }
        }
    }

    /** 由 MikaAccessibilityService 转进来;[service] 就是当前无障碍服务实例。 */
    fun onAccessibilityEvent(event: AccessibilityEvent, service: ScreenAccessibility) {
        // 一键接入期间即使截获关着也要跑:填输入框要靠这些事件
        if (!enabled && !app.amooSetup.active) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 输入法弹窗也会发窗口状态事件,不管它:一记就把前台冲成非 amoo,打字时全程漏截。
                // 各家输入法窗口名不一(InputMethod*/SoftInputWindow*),包名也认一下
                val cls = event.className?.toString()
                val pkg = event.packageName?.toString()
                if (cls != null && (cls.contains("InputMethod", true) || cls.contains("SoftInput", true))) return
                if (pkg != null && pkg.contains("inputmethod", true)) return
                // Mika 自己的浮层/胶囊窗口变化不是「前台换了 App」,不记(scan 里 root 包名兜底防误判)
                if (pkg == app.packageName) return
                detector.onWindowStateChanged(pkg, cls)
                Log.i(TAG, "win-state pkg=$pkg cls=$cls inAmooChat=${detector.inAmooChat}")
                // 刚切进 amoo 聊天页时先扫一遍,不必等下一次内容变化
                scan(service)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            -> scan(service)
        }
    }

    private fun scan(service: ScreenAccessibility) {
        val now = System.currentTimeMillis()
        if (now - lastScanMs < SCAN_INTERVAL_MS) return
        lastScanMs = now
        val root = runCatching { service.rootInActiveWindow }.getOrNull()
        if (root == null) {
            Log.w(TAG, "scan: rootInActiveWindow is null")
            return
        }
        try {
            handle(root)
        } finally {
            recycleRoot(root) // root 是我们取出来的新实例,谁取谁回收
        }
    }

    /** [root] 由 [scan] 回收;这里自己 walk 出来的节点自己回收,整棵树只走一遍。 */
    private fun handle(root: AccessibilityNodeInfo) {
        // 当前窗口不是 amoo 就不看(窗口状态可能是旧的,root 的包名才是此刻的真前台)
        if (root.packageName?.toString() != AmooSendDetector.AMOO_PACKAGE) {
            Log.d(TAG, "scan: root pkg=${root.packageName} skipped")
            return
        }
        // root 就是 amoo:前台不知道时补记(重连会漏掉窗口事件);
        // 已经知道是 amoo 的具体页面就不覆盖,闸门 1 才拦得住设置页/搜索页。
        detector.noteAmooForeground()
        if (!detector.inAmooChat) {
            Log.d(TAG, "scan: amoo non-chat page, skipped")
            return
        }
        // 一键接入:正在把技能说明填进 amoo 输入框时,这一轮归它,别把说明当用户消息截获
        if (app.amooSetup.onAmooChat(root)) return
        val nodes = descendants(root)
        try {
            val input = inputText(root, nodes)
            detector.noteInputText(input)
            if (input != null && input != baselineInput) {
                // 每换一段输入就重算基线:这段文字在「开始打之前」是不是已经在聊天记录里了。
                // 是的话这一轮不认领,否则重打一句历史里出现过的话(「继续」「好」之类)会立刻误建任务。
                baselineInput = input
                bubbleAlreadyPresent = hasBubble(nodes, input)
                Log.d(TAG, "scan: baseline ${input.length} chars, bubbleAlreadyPresent=$bubbleAlreadyPresent")
            }
            if (input != loggedInput) {
                loggedInput = input
                Log.d(TAG, "scan: input now ${input?.length ?: 0} chars") // 不记原文
            }
            val draft = detector.pendingDraft()
            if (draft != loggedDraft) {
                loggedDraft = draft
                Log.d(TAG, "scan: draft ${draft?.length ?: 0} chars") // 不记原文
            }
            if (draft == null) return // 常态:没在打字,或草稿超过 10s
            if (bubbleAlreadyPresent) return // 这段话发过,历史里那条气泡不是这次发的
            if (!hasBubble(nodes, draft)) return // 草稿还在输入框里,没发出去
            Log.i(TAG, "scan: bubble matched, intercepting ${draft.length} chars")
            detector.markIntercepted(draft)
            baselineInput = null // 下一条输入重新算基线:这条气泡已进聊天记录,不该再被认领
            startTask(draft)
        } finally {
            recycle(nodes)
        }
    }

    /**
     * 聚焦的输入框文本;没有聚焦输入框就取第一个非空的可编辑节点。
     *
     * 不苛求 isEditable:实测 amoo 的输入框在无障碍树里 class 是 EditText 但 editable 属性缺失,
     * 只认 isEditable 会永远取不到草稿。类名兜底,再不行才退回可编辑节点。
     */
    private fun inputText(root: AccessibilityNodeInfo, nodes: List<AccessibilityNodeInfo>): String? {
        val focused = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        if (focused != null) {
            try {
                if (looksLikeInput(focused)) {
                    val t = focused.text?.toString()?.trim()
                    val hint = focused.hintText?.toString()?.trim()
                    // 空输入框的 text 属性会返回 hint,别把它当草稿
                    if (!t.isNullOrEmpty() && t != hint) return t
                }
            } finally {
                recycle(listOf(focused))
            }
        }
        return editableTexts(nodes).firstOrNull { it.isNotEmpty() }
    }

    /** 像输入框:可编辑,或类名带 EditText(见 [inputText] 的说明)。 */
    private fun looksLikeInput(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        return node.className?.toString()?.contains("EditText", ignoreCase = true) == true
    }

    /**
     * 树里有没有和草稿同文本、且不是输入框的节点(= 聊天列表里的气泡)。
     * 输入框本身文字和草稿一样,必须先排除;聚焦节点也排掉,双保险。
     */
    private fun hasBubble(nodes: List<AccessibilityNodeInfo>, draft: String): Boolean =
        nodes.any {
            !looksLikeInput(it) && !it.isFocused && it.text?.toString()?.trim() == draft
        }

    private fun editableTexts(nodes: List<AccessibilityNodeInfo>): List<String> =
        nodes.filter { looksLikeInput(it) }
            .mapNotNull { n ->
                val t = n.text?.toString()?.trim()
                // 空输入框的 text 属性会返回 hint,别把它当草稿
                if (t.isNullOrEmpty() || t == n.hintText?.toString()?.trim()) null else t
            }

    /** 深度优先收集 [root] 的所有子孙节点(不含 root 本身);调用方负责 [recycle]。 */
    private fun descendants(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) runCatching { root.getChild(i) }.getOrNull()?.let { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            out.add(n)
            for (i in 0 until n.childCount) {
                runCatching { n.getChild(i) }.getOrNull()?.let { stack.addLast(it) }
            }
        }
        return out
    }

    private fun startTask(goal: String) {
        Log.i(TAG, "amoo task intercepted: ${goal.length} chars")
        // 起任务要写账本、可能拉服务,别占主线程
        scope.launch(Dispatchers.IO) {
            runCatching { tasks.start(goal, null, UUID.randomUUID().toString()) }
                .onSuccess { Log.i(TAG, "intercepted task started: ${it.id}") }
                .onFailure { Log.w(TAG, "intercepted task failed to start", it) }
        }
    }

    @Suppress("DEPRECATION")
    private fun recycleRoot(root: AccessibilityNodeInfo) {
        // API 33 起 recycle 已废弃(节点随窗口自动回收),但 minSdk 30 上不回收会漏原生资源
        runCatching { root.recycle() }
    }

    @Suppress("DEPRECATION")
    private fun recycle(nodes: List<AccessibilityNodeInfo>) {
        nodes.forEach { runCatching { it.recycle() } }
    }

    companion object {
        private const val TAG = "AmooIntercept"

        /** 两次树扫描的最小间隔;聊天滚动事件很密,300ms 足够跟上「发送」这种动作。 */
        private const val SCAN_INTERVAL_MS = 300L
    }
}

package com.daiyimo.maeve.core.guard

import com.daiyimo.maeve.core.prompt.Feedback
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class Severity(val wire: String) { Warn("warn"), Stop("stop") }

/**
 * 守卫的一条结论。提醒([Severity.Warn])带 [nudge]:说清卡在哪、给几条出路的一段话(发给模型);叫停的没有。
 */
data class GuardVerdict(val code: String, val severity: Severity, val evidence: JsonObject, val nudge: String? = null) {
    fun toJson(): JsonObject = buildJsonObject {
        put("code", code); put("severity", severity.wire); put("evidence", evidence)
        nudge?.let { put("nudge", it) }
    }
}



/**
 * 每次任务一份的操作守卫。动作没有效果、重复读取没有新内容、回到旧状态或工具连续失败时提醒并最终叫停。
 * 相同的点击/滚动只有在同一屏幕上连续重复(或 ABAB)才算停滞；新的页面或读取内容会清掉对应窗口。
 */
class OperationGuards(private val stepBudget: Int? = null) {

    private var frameId: String? = null
    private var frameHash: Long? = null
    private var acted = false
    /** 上一个动作已按它报的实际效果记过「有没有效果」:接着的界面不再按指纹重复记。 */
    private var effectNoted = false

    /** 连续没有效果的动作数。 */
    private var unchanged = 0
    private var unchangedWarnedAt = 0
    /** 同一轮无效果里,树读不出落点的次数;不借用无关的普通无效果提前叫停。 */
    private var treeBlindActions = 0

    /** 到过的每个界面状态(元素表指纹)。 */
    private val seenStates = HashSet<Long>()
    /** 自上次到过新状态起,回到各状态的次数与已提醒到的门槛。 */
    private val visits = HashMap<Long, Int>()
    private val visitsWarnedAt = HashMap<Long, Int>()
    /** 先后看到的各状态(相邻的不同),最近几个。 */
    private val trail = ArrayDeque<Long>()
    private var cycleWarnedAt = 0
    /** 自上次到过新状态起做了几个动作。 */
    private var sinceNewState = 0
    private var sinceNewWarnedAt = 0

    private data class CallObservation(val signature: String, val tool: String, val screen: Long? = null)
    /** poki 的最近调用窗口:调用在 beforeAction 记下,frame 回填它之后的屏幕。 */
    private val recentCalls = ArrayDeque<CallObservation>()
    private var pendingCall: CallObservation? = null

    /** 任务的计数;画面每变一次 [epoch] 加一,各段读的计数据此作废。 */
    private val reads = GuardScope(canStop = true)
    /** 跨页面记住读过的内容；返回同一页时不把旧内容当作任务新进展。 */
    private val taskReads = HashSet<Long>()
    private var epoch = 0

    private var consecutiveFailures = 0
    private var unrecoverableFailures = 0
    private val recentFailures = ArrayDeque<JsonObject>()

    var stop: GuardVerdict? = null
        private set
    val unchangedFrames: Int get() = unchanged
    var progressVersion: Long = 0
        private set

    /** 提醒附上卡在哪(guard.<code> 那条,填 [count])和出路;叫停的不附。 */
    private fun verdict(code: String, severity: Severity, evidence: JsonObject, count: Int, waysOut: Boolean = true, period: Int? = null): List<GuardVerdict> {
        val nudge = if (severity == Severity.Warn) listOfNotNull(
            if (period != null) Feedback.format(LEADS.getValue(code), "count" to count, "states" to period) else Feedback.format(LEADS.getValue(code), "count" to count),
            Feedback["guard.ways_out"].takeIf { waysOut },
        ).joinToString(" ") else null
        val v = GuardVerdict(code, severity, evidence, nudge)
        if (severity == Severity.Stop) stop = v
        return listOf(v)
    }

    private fun <T> ArrayDeque<T>.pushCapped(v: T, cap: Int = 10) { addLast(v); if (size > cap) removeFirst() }

    /**
     * 记下这次调用,等它之后的 [frame] 回填屏幕再判断 poki 的同调用/ABAB 窗口。
     * [trackCall] 为 false 用于普通页面读取:不观察这次调用,但不清掉已有窗口;用户交回后调 [noteOwnerWait]。
     */
    fun beforeAction(
        tool: String,
        args: JsonObject,
        diagnostic: Boolean = false,
        scope: GuardScope = reads,
        trackCall: Boolean = true,
    ): List<GuardVerdict> {
        pendingCall = if (trackCall) CallObservation(exactCallSignature(tool, args), tool) else null
        return emptyList()
    }

    /** 一个会改变界面的动作要做了。 */
    fun noteAction() {
        acted = true
        effectNoted = false
        sinceNewState += 1
    }

    /**
     * 动作报回的实际效果(effect 的 kind,见 core.uistate.EffectKind):no_effect 记一次「没有效果」,
     * 明显变了、换了 App 或换了页就从头计;小的局部变化两样都不算。
     * [treeBlind] 时连画面也没有可见变化,既不能用控件树查隐藏状态,也不该换着坐标一直试:两次提醒、三次叫停。
     */
    fun effect(kind: String, treeBlind: Boolean = false): List<GuardVerdict> {
        effectNoted = true
        when (kind) {
            NO_EFFECT -> {
                unchanged += 1
                if (treeBlind) treeBlindActions += 1
            }
            else -> { resetNoEffect(); return emptyList() }
        }
        return noProgress()
    }

    private fun resetNoEffect() {
        unchanged = 0
        unchangedWarnedAt = 0
        treeBlindActions = 0
    }

    private fun noProgress(): List<GuardVerdict> {
        if (treeBlindActions >= 2) {
            return verdict("no_progress", if (unchanged >= 3) Severity.Stop else Severity.Warn, buildJsonObject {
                put("unchanged", unchanged); put("treeBlind", true); put("treeBlindActions", treeBlindActions)
            }, unchanged)
        }
        val rung = GuardRules.climb(unchanged, GuardRules.NO_EFFECT_LADDER, unchangedWarnedAt, canStop = true) ?: return emptyList()
        unchangedWarnedAt = rung.threshold
        return verdict("no_progress", rung.severity, buildJsonObject { put("unchanged", unchanged) }, unchanged)
    }

    /**
     * 模型拿到了一份界面(读界面,或动作结果里带的),[hash] 是它的指纹。只有「动过之后界面仍一样」才算没有效果
     * (动作已报过效果的不再重复记);单纯反复看不算。动作之后到了哪个状态还用来看回到同一状态、兜圈、一直没到新状态。
     */
    fun frame(id: String, hash: Long?): List<GuardVerdict> {
        if (frameId == id) {
            pendingCall = null
            return emptyList()
        }
        val call = pendingCall
        pendingCall = null
        // 等待只读屏幕、不附带元素表时,用最近一次实际读到的屏幕判断同屏;从未读过屏时用固定未知桶。
        val observedHash = hash ?: frameHash ?: NO_SCREEN_HASH
        val same = frameHash == observedHash
        val didAct = acted
        val noted = effectNoted
        acted = false
        effectNoted = false
        frameId = id
        frameHash = observedHash
        val out = mutableListOf<GuardVerdict>()
        if (!same) {
            pageChanged(resetCalls = true)
            if (trail.lastOrNull() != observedHash) {
                trail.addLast(observedHash)
                while (trail.size > TRAIL_KEEP) trail.removeFirst()
            }
            if (seenStates.add(observedHash)) progress() else out += arrived(observedHash)
            out += cycle()
        }
        if (didAct && !noted) {
            if (same) { unchanged += 1; out += noProgress() } else resetNoEffect()
        }
        // 旧界面本身不能证明失败，只提醒模型评估是否有新内容或换路线。
        if (didAct) out += noNewState()
        if (call != null) out += callStall(call, observedHash)
        return out
    }

    /** poki:最近 6 条同调用同屏,或最近 8 条同屏且调用按 ABAB 重复。 */
    private fun callStall(call: CallObservation, hash: Long): List<GuardVerdict> {
        recentCalls.addLast(call.copy(screen = hash))
        while (recentCalls.size > CALL_WINDOW) recentCalls.removeFirst()
        val repeated = recentCalls.takeLast(SAME_CALL_STEPS)
        if (repeated.size == SAME_CALL_STEPS && repeated.all { it.signature == call.signature && it.screen == hash }) {
            return verdict("repeating_action", Severity.Stop, buildJsonObject {
                put("tool", call.tool); put("repeats", SAME_CALL_STEPS); put("sameScreen", true)
            }, SAME_CALL_STEPS)
        }
        val alternating = recentCalls.takeLast(ABAB_STEPS)
        if (alternating.size == ABAB_STEPS && alternating.all { it.screen == hash } &&
            (0 until (ABAB_STEPS - 2)).all { alternating[it].signature == alternating[it + 2].signature }) {
            return verdict("repeating_action", Severity.Stop, buildJsonObject {
                put("pattern", "abab"); put("steps", ABAB_STEPS); put("sameScreen", true)
            }, ABAB_STEPS)
        }
        return emptyList()
    }

    /** 动作之后回到一个已到过的状态。正在兜圈时由 [cycle] 说,这里不重复。 */
    private fun arrived(hash: Long): List<GuardVerdict> {
        val n = (visits[hash] ?: 1) + 1
        visits[hash] = n
        if (GuardRules.cycle(trail.toList()) != null) return emptyList()
        val rung = GuardRules.climb(n, GuardRules.sameStateLadder(stepBudget), visitsWarnedAt[hash] ?: 0, canStop = true) ?: return emptyList()
        visitsWarnedAt[hash] = rung.threshold
        return verdict("same_state", rung.severity, buildJsonObject { put("visits", n) }, n)
    }

    private fun cycle(): List<GuardVerdict> {
        val c = GuardRules.cycle(trail.toList())
        if (c == null) { cycleWarnedAt = 0; return emptyList() }
        val rung = GuardRules.climb(c.rounds, GuardRules.cycleLadder(stepBudget), cycleWarnedAt, canStop = true) ?: return emptyList()
        cycleWarnedAt = rung.threshold
        return verdict("state_cycle", rung.severity, buildJsonObject { put("states", c.period); put("rounds", c.rounds) }, c.rounds, period = c.period)
    }

    private fun noNewState(): List<GuardVerdict> {
        val rung = GuardRules.climb(sinceNewState, GuardRules.noNewStateLadder(stepBudget), sinceNewWarnedAt, canStop = false) ?: return emptyList()
        sinceNewWarnedAt = rung.threshold
        return verdict("no_new_state", rung.severity, buildJsonObject { put("actions", sinceNewState) }, sinceNewState)
    }

    /**
     * 一次读屏/读网页/网页脚本:画面没变、也没做有效操作时反复读,读不出新东西(要找的东西不在这一页上)。
     * [fresh] 为 true 表示这次读到了之前没读到过的内容(如读整页换了起点),不算重读。
     * 换着工具、换着脚本读也算:页面没变,答案就是页面上现有的这些。
     * [window] 给了就在它里面单独计数(页面脚本一次调用里的读),不动任务的计数。
     */
    fun read(
        tool: String,
        fresh: Boolean,
        window: GuardScope = reads,
        fingerprint: Long? = null,
        noteProgress: Boolean = true,
    ): List<GuardVerdict> {
        if (fresh) {
            if (fingerprint == null || taskReads.add(fingerprint)) {
                if (noteProgress) progress()
            }
            return emptyList()
        }
        window.sync(epoch)
        window.rereads += 1
        val n = window.rereads
        val rung = GuardRules.climb(n, GuardRules.REREAD_LADDER, window.warnedAt, window.canStop) ?: return emptyList()
        window.warnedAt = rung.threshold
        return verdict("read_loop", rung.severity, buildJsonObject {
            put("tool", tool); put("rereadsOfUnchangedPage", n)
        }, n)
    }

    /** 这份读到的结果是不是画面上次变化以来头一回读到。 */
    fun firstSeen(fingerprint: Long, window: GuardScope = reads): Boolean {
        window.sync(epoch)
        return window.seen.add(fingerprint)
    }

    /**
     * 画面变了(或做了有效的操作):之前读到的不再作数(所有读的计数一起作废)。
     * [resetCalls] 只在指纹实际变化或工具明确报告 changed=true 时开启;旧工具的 changed=null 不能当作 poki 的进展。
     */
    fun pageChanged(resetCalls: Boolean = false) {
        epoch += 1
        if (resetCalls) {
            resetCallHistory()
            if (stop?.code == "repeating_action") stop = null
        }
    }

    /** 只清 poki 的同调用/ABAB 观察历史,不动状态轨迹、无进展和读循环计数。 */
    fun resetCallHistory() {
        recentCalls.clear()
        pendingCall = null
    }

    /** 用户回答、接管或确认交回后调:等待不算卡死,之前的调用窗口也不再作数。 */
    fun noteOwnerWait() {
        resetCallHistory()
    }

    private fun progress() {
        progressVersion += 1
        resetCallHistory()
        resetNoEffect()
        sinceNewState = 0
        sinceNewWarnedAt = 0
        if (stop?.code == "no_progress" || stop?.code == "read_loop" || stop?.code == "repeating_action") stop = null
    }

    fun result(tool: String, content: JsonObject, neutral: Boolean = false): List<GuardVerdict> {
        val ok = (content["ok"] as? JsonPrimitive)?.booleanOrNull
        val countsAsFailure = (content["counts_as_failure"] as? JsonPrimitive)?.booleanOrNull
        if (neutral || countsAsFailure == false || ok != false) {
            consecutiveFailures = 0
            unrecoverableFailures = 0
            return emptyList()
        }
        val recoverable = (content["recoverable"] as? JsonPrimitive)?.booleanOrNull != false
        consecutiveFailures += 1
        if (!recoverable) unrecoverableFailures += 1
        recentFailures.pushCapped(buildJsonObject {
            put("tool", tool); put("recoverable", recoverable)
            (content["error"] as? JsonPrimitive)?.takeIf { it.isString }?.let { put("error", it.content) }
        })
        val severity = GuardRules.failures(consecutiveFailures, unrecoverableFailures) ?: return emptyList()
        return verdict("too_many_failures", severity, buildJsonObject {
            put("consecutive", consecutiveFailures)
            put("unrecoverable", unrecoverableFailures)
            put("recent", JsonArray(recentFailures.toList().takeLast(5)))
        }, consecutiveFailures, waysOut = consecutiveFailures >= GuardRules.FAILURE_WAYS_OUT || unrecoverableFailures > 0)
    }

    private companion object {
        const val NO_EFFECT = "no_effect"
        /** 记最近多少个状态(够看出最长的兜圈安全网)。 */
        const val TRAIL_KEEP = 64
        const val CALL_WINDOW = 8
        const val SAME_CALL_STEPS = 6
        const val ABAB_STEPS = 8
        const val NO_SCREEN_HASH = Long.MIN_VALUE

        /** 各条提醒开头说卡在哪的那句(prompts/feedback/tools.md)。 */
        val LEADS = mapOf(
            "repeating_action" to "guard.repeating_action", "no_progress" to "guard.no_progress", "same_state" to "guard.same_state",
            "state_cycle" to "guard.state_cycle", "no_new_state" to "guard.no_new_state", "read_loop" to "guard.read_loop",
            "too_many_failures" to "guard.too_many_failures",
        )
    }
}

/**
 * 一段计数:最近的动作签名(重复操作),读到过的结果指纹、又读了一遍没变的页面的次数。
 * [canStop] 为 false 时最多提醒,不叫停任务。读的计数在画面变了([OperationGuards.pageChanged])时作废重计。
 */
class GuardScope(val canStop: Boolean) {
    internal val loop = ArrayDeque<String>()
    internal var loopWarnedAt = 0
    internal val seen = HashSet<Long>()
    internal var rereads = 0
    internal var warnedAt = 0
    private var epoch = 0

    internal fun sync(now: Int) {
        if (now == epoch) return
        epoch = now
        seen.clear(); rereads = 0; warnedAt = 0
    }
}

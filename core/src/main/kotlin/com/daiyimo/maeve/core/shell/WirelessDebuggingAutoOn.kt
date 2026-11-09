package com.daiyimo.maeve.core.shell

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 替用户打开无线调试后停手的原因；停手后要等换网络、用户亲手打开、点重试或助手起来才恢复。 */
enum class AutoOnBlock {
    /** 写入打开后系统立刻又拨回关：这个 Wi-Fi 还没被允许无线调试（系统同时会弹框问用户）。 */
    NotAllowedHere,
    /** 系统不接受写入（抛异常或返回失败）。 */
    Refused,
    /** 连着替用户写了 [WirelessDebuggingAutoOn.MAX_WRITES] 次，助手仍未起来。 */
    GaveUp,
}

/** 现在该不该替用户写无线调试开关。 */
sealed interface AutoOnGate {
    data object Act : AutoOnGate
    /** 距上次写入太近，等 [ms] 再试。 */
    data class Wait(val ms: Long) : AutoOnGate
    data class Skip(val reason: AutoOnSkip) : AutoOnGate
}

enum class AutoOnSkip {
    /** 用户在 Maeve 里关掉了「自动打开无线调试」。 */
    Declined,
    /** Maeve 拿不到修改系统设置的授权。 */
    NoPermission,
    /** 当前没有 Wi-Fi：系统不会在无 Wi-Fi 时保持无线调试打开（写了也会被拨回）。 */
    NoWifi,
    /** 已经停手，原因见 [AutoOnBlock]。 */
    Blocked,
}

/**
 * 内置助手要起、无线调试却关着时，替用户把开关打开（只负责开，不负责关）。助手一旦跑起来就不再依赖无线调试，
 * 但重启手机、Maeve 更新、助手丢失后要再起它仍然要靠它；而安卓原版会在 Wi-Fi 断开或重启后把开关关掉。
 *
 * 只有三个条件同时满足才写：用户没关掉「自动打开无线调试」、Maeve 手里有修改系统设置的授权、当前连着 Wi-Fi。
 * 要起助手时助手必然不在，而经已配对连接跑命令又得先有无线调试，所以只剩 App 直接写这一条路；做不到就不动。
 *
 * 依据的原版行为（AdbDebuggingManager 处理 `adb_wifi_enabled`）：
 * - 写成 1 后系统会核对当前 Wi-Fi：没连 Wi-Fi 就立刻写回 0；这个网络（按 BSSID）不在「始终允许」名单里，
 *   也会立刻写回 0，同时弹框问「允许在此网络上使用无线调试吗」。用户允许后系统自己写回 1；拒绝就一直关着。
 * - 网络已在名单里就保持 1，并随后启动无线调试服务（端口仍由连接那一步去找）。
 * 因此写完要看 [settleMs]：这段时间内读到关着，就认为这个 Wi-Fi 尚未被允许，停手（[AutoOnBlock.NotAllowedHere]），
 * 不再反复写（每写一次系统就弹一次框），直到换网络、用户亲手打开、点重试或助手起来（[reset]）。
 * 有的系统另有处理（拒绝写入、写了不生效），同样停手，不去适配每一家。
 * 两次写入之间按 [gapAfter] 拉开；连续写满 [MAX_WRITES] 次助手仍没起也停手。
 */
class WirelessDebuggingAutoOn(
    /** 用户是否还要这一项（没在 Maeve 里关掉「自动打开无线调试」）。 */
    private val wants: () -> Boolean,
    /** Maeve 是否持有修改系统设置的授权。 */
    private val canWrite: () -> Boolean,
    /** 读系统的无线调试开关；读不到为 null。 */
    private val read: () -> Boolean?,
    /** 把开关写成打开；系统拒绝时返回 false 或抛异常。 */
    private val write: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val settleMs: Long = SETTLE_MS,
    private val pollMs: Long = POLL_MS,
) {
    private val _blocked = MutableStateFlow<AutoOnBlock?>(null)

    /** 停手的原因；没停手为 null。设置页和状态提示会引用它。 */
    val blocked: StateFlow<AutoOnBlock?> = _blocked.asStateFlow()

    /** 正在写入并等系统处理。这段时间里开关的变化交给这里判断，别的组件不要跟着动。 */
    @Volatile var writing = false
        private set

    private var writeCount = 0
    private var lastWriteAt = 0L

    @Synchronized
    fun gate(wifi: Boolean): AutoOnGate {
        if (_blocked.value == null && writeCount >= MAX_WRITES) _blocked.value = AutoOnBlock.GaveUp
        return decide(wants(), canWrite(), wifi, _blocked.value, writeCount, now() - lastWriteAt)
    }

    /** 写打开并观察系统反应。开关保持打开返回 true；被拒或被拨回返回 false，同时记下停手原因。 */
    suspend fun turnOn(): Boolean {
        markWriteStarted()
        try {
            return attemptWrite()
        } finally {
            writing = false
        }
    }

    /** 换 Wi-Fi、用户亲手打开或点重试、助手起来了：计数和停手原因全部归零。 */
    @Synchronized
    fun reset() {
        writeCount = 0
        lastWriteAt = 0L
        _blocked.value = null
    }

    private fun markWriteStarted() {
        synchronized(this) {
            writing = true
            writeCount++
            lastWriteAt = now()
        }
    }

    private suspend fun attemptWrite(): Boolean {
        val accepted = runCatching(write).getOrDefault(false)
        if (!accepted) return stopWith(AutoOnBlock.Refused)
        if (read() == false) return stopWith(AutoOnBlock.NotAllowedHere)
        var waited = 0L
        while (waited < settleMs) {
            delay(pollMs)
            waited += pollMs
            if (read() == false) return stopWith(AutoOnBlock.NotAllowedHere)
        }
        return true
    }

    private fun stopWith(reason: AutoOnBlock): Boolean {
        _blocked.value = reason
        return false
    }

    companion object {
        /** 写完后观察多久是否被系统拨回；原版收到设置变化就处理，几百毫秒内就会拨回。 */
        const val SETTLE_MS = 3_000L
        const val POLL_MS = 250L
        const val MAX_WRITES = 3
        const val BASE_GAP_MS = 10_000L
        const val MAX_GAP_MS = 5 * 60_000L

        /** 已经写过 [writes] 次时，下一次至少隔多久：第一次不等，之后 10 秒、20 秒……封顶 5 分钟。 */
        fun gapAfter(writes: Int): Long =
            if (writes <= 0) 0L else (BASE_GAP_MS shl (writes - 1).coerceAtMost(20)).coerceAtMost(MAX_GAP_MS)

        fun decide(wants: Boolean, canWrite: Boolean, wifi: Boolean, blocked: AutoOnBlock?, writes: Int, sinceLastMs: Long): AutoOnGate {
            if (!wants) return AutoOnGate.Skip(AutoOnSkip.Declined)
            if (!canWrite) return AutoOnGate.Skip(AutoOnSkip.NoPermission)
            if (blocked != null || writes >= MAX_WRITES) return AutoOnGate.Skip(AutoOnSkip.Blocked)
            if (!wifi) return AutoOnGate.Skip(AutoOnSkip.NoWifi)
            val gap = gapAfter(writes)
            return if (sinceLastMs < gap) AutoOnGate.Wait(gap - sinceLastMs) else AutoOnGate.Act
        }
    }
}

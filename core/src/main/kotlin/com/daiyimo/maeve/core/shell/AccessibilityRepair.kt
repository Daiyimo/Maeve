package com.daiyimo.maeve.core.shell

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** App 看到的 Maeve 无障碍服务：系统列表里有没有它（[enabled]），服务实例是否连着（[connected]）。 */
data class A11yObservation(val enabled: Boolean, val connected: Boolean) {
    /**
     * 服务没在运行就要修：不管是系统里开着却没绑定（进程被杀后系统当它崩溃了），
     * 还是被从系统列表里拿掉了（强行停止时系统会这样做）。用户不想修就关掉「替我打开无障碍」。
     */
    val stuck: Boolean get() = !connected
}

/** 自动重启无障碍服务进行到哪一步：要用服务的一方据此决定多等一会儿还是马上放弃。 */
enum class RepairPhase {
    /** 没有要修的（服务连着、用户在 Maeve 里关掉了替我打开、助手不在、试过两次放弃了）。 */
    Idle,
    /** 卡住了，先等一会儿看系统自己会不会连上。 */
    Pending,
    /** 正在去掉再加回、等服务连上。 */
    Repairing,
}

/** 上一次自动重启无障碍服务的结果（只记在内存里，给设置页说一句）。 */
data class RepairOutcome(val restarted: Boolean, val atMillis: Long, val attempts: Int)

/**
 * Maeve 的无障碍服务没在运行时自动把它打开：内置助手在跑时，用它把 Maeve 的服务写回系统列表
 * （[RecoveryPermissions.restartAccessibility]：列表里有就去掉再加回让系统重新绑定，没有就直接加上）。
 * App 自己有修改系统设置的授权时（[direct]）直接写，不用等助手（无线调试关着也能修）。
 * 只看三路状态：服务（[A11yObservation]）、内置助手、用户是否已解锁；任一变化都重新判断，不轮询。
 *
 * - 只要没在运行就修；只有用户在 Maeve 里关掉了「替我打开无障碍」才不管。
 * - 开机后用户还没解锁过时（凭据加密的存储尚未解开），不支持直接启动的服务本来也绑不上：这时不修，
 *   等用户解锁（[watch] 的 unlocked 变为 true）后再判断一次，该修照样修。
 * - 刚卡住时先等 [GRACE_MS]：进程刚起来时系统通常马上会连上，不必动手。
 * - 每次卡住最多试 [MAX_ATTEMPTS] 次，间隔逐次拉长；仍没连上就停下，把手动处理的提示留给界面。
 *   服务连上才算这一次过去，之后再断开重新计数。
 */
class AccessibilityRepair(
    private val restart: suspend (gapMs: Long) -> PermissionResult,
    private val consent: () -> RecoveryConsent,
    /** App 自己就能写系统设置（已拿到修改系统设置的授权）时为 true：这时不用等内置助手。 */
    private val direct: () -> Boolean = { false },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _last = MutableStateFlow<RepairOutcome?>(null)
    val last: StateFlow<RepairOutcome?> = _last.asStateFlow()

    private val _phase = MutableStateFlow(RepairPhase.Idle)
    val phase: StateFlow<RepairPhase> = _phase.asStateFlow()

    private enum class Result { Restored, GaveUp, Interrupted }

    /**
     * 一直看着，直到被取消。修的过程中状态变化（包括自己去掉又加回）都按最新的一份再判断一次。
     * [unlocked] 是开机后用户解锁过没有；没解锁时不修，解锁时它的变化让这里再判断一次。
     */
    suspend fun watch(
        service: StateFlow<A11yObservation>,
        helper: StateFlow<BuiltinShellStatus>,
        unlocked: StateFlow<Boolean> = MutableStateFlow(true),
    ) {
        var exhausted = false
        combine(service, helper, unlocked, ::Triple).conflate().collect { (observation, helperStatus, userUnlocked) ->
            when {
                observation.connected -> exhausted = false
                exhausted -> Unit
                !shouldRepair(observation, helperStatus, userUnlocked) -> Unit
                else -> exhausted = handleStuck(service, helper, unlocked)
            }
        }
    }

    /** 卡住了：先等宽限期看系统自己连不连得上，再进去重试；返回 true 表示放弃了，要等下次连上重新计数。 */
    private suspend fun handleStuck(
        service: StateFlow<A11yObservation>,
        helper: StateFlow<BuiltinShellStatus>,
        unlocked: StateFlow<Boolean>,
    ): Boolean {
        return try {
            _phase.value = RepairPhase.Pending
            delay(GRACE_MS)
            if (!shouldRepair(service.value, helper.value, unlocked.value)) return false
            _phase.value = RepairPhase.Repairing
            repair(service, helper, unlocked) == Result.GaveUp
        } finally {
            _phase.value = RepairPhase.Idle
        }
    }

    private fun shouldRepair(observation: A11yObservation, helperStatus: BuiltinShellStatus, userUnlocked: Boolean): Boolean =
        observation.stuck && userUnlocked && (direct() || helperStatus == BuiltinShellStatus.Running) &&
            consent().wants(RecoveryCapability.Accessibility)

    private suspend fun repair(
        service: StateFlow<A11yObservation>,
        helper: StateFlow<BuiltinShellStatus>,
        unlocked: StateFlow<Boolean>,
    ): Result {
        for (attempt in 1..MAX_ATTEMPTS) {
            backOff(attempt, service, helper, unlocked)?.let { return it }
            if (restart(GAP_MS * attempt) == PermissionResult.Done && awaitConnected(service)) {
                return succeeded(attempt)
            }
        }
        record(false, MAX_ATTEMPTS)
        return Result.GaveUp
    }

    /**
     * 第二次起先退避，退避完情况可能变了：连上了算成功，不该修了算被打断；都没变才返回 null 接着试。
     * 退避的间隔逐次拉长：两次写入挨得太近，系统可能只看到最后的列表。
     */
    private suspend fun backOff(
        attempt: Int,
        service: StateFlow<A11yObservation>,
        helper: StateFlow<BuiltinShellStatus>,
        unlocked: StateFlow<Boolean>,
    ): Result? {
        if (attempt == 1) return null
        delay(BACKOFF_MS * (attempt - 1))
        val observation = service.value
        if (observation.connected) return succeeded(attempt - 1)
        if (!shouldRepair(observation, helper.value, unlocked.value)) return Result.Interrupted
        return null
    }

    private suspend fun awaitConnected(service: StateFlow<A11yObservation>): Boolean =
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { service.first { it.connected } } != null

    private fun succeeded(attempts: Int): Result {
        record(restarted = true, attempts = attempts)
        return Result.Restored
    }

    private fun record(restarted: Boolean, attempts: Int) {
        _last.value = RepairOutcome(restarted, now(), attempts)
    }

    companion object {
        const val GRACE_MS = 3_000L
        const val MAX_ATTEMPTS = 2

        /** 去掉与加回之间的停顿，逐次拉长（1 秒、2 秒）：两次写入挨得太近，系统可能只看到最后的列表。 */
        const val GAP_MS = 1_000L
        const val BACKOFF_MS = 5_000L
        const val CONNECT_TIMEOUT_MS = 8_000L

        /** 从卡住到放弃最长要多久：先等一会儿，两次去掉再加回、各等连上，中间退避一次，再留些跑命令的余量。 */
        const val WORST_CASE_MS = GRACE_MS + MAX_ATTEMPTS * CONNECT_TIMEOUT_MS + GAP_MS * 3 + BACKOFF_MS + 2_000L

        /**
         * 要用无障碍服务时等它：先照常等 [baseMs]；还没连上、但正要修或正在修（[phase] 不是 Idle），
         * 就继续等到连上或修的那一边停下（修好了服务已连着，放弃了返回 null），总共不超过 [capMs]。
         * 没有要修的就和以前一样，[baseMs] 到了返回 null。
         */
        suspend fun <T : Any> awaitService(
            service: StateFlow<T?>,
            phase: StateFlow<RepairPhase>,
            baseMs: Long,
            capMs: Long = WORST_CASE_MS,
        ): T? = firstWithin(service, baseMs) ?: waitOutRepair(service, phase, capMs - baseMs)

        /** 照常等 [baseMs]：等到了就给；没等到就看那一刻有没有（可能刚刚连上）。 */
        private suspend fun <T : Any> firstWithin(service: StateFlow<T?>, baseMs: Long): T? =
            withTimeoutOrNull(baseMs) { service.filterNotNull().first() } ?: service.value

        /** 正在修就接着等：连上了、或修的那一边停下了（[phase] 回 Idle），二者其一就收；没在修就当时的值。 */
        private suspend fun <T : Any> waitOutRepair(service: StateFlow<T?>, phase: StateFlow<RepairPhase>, budgetMs: Long): T? {
            if (phase.value == RepairPhase.Idle) return service.value
            return withTimeoutOrNull(budgetMs.coerceAtLeast(0)) {
                combine(service, phase, ::Pair).first { (instance, currentPhase) ->
                    instance != null || currentPhase == RepairPhase.Idle
                }.first
            }
        }
    }
}

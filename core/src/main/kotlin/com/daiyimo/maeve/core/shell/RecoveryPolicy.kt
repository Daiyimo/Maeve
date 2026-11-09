package com.daiyimo.maeve.core.shell

import kotlinx.coroutines.delay

/** 判断要不要重起助手时看的几样输入。 */
data class RecoveryInputs(
    /** 本机存着配对钥匙。 */
    val paired: Boolean,
    /** 系统的无线调试开关；有的手机读不到，为 null。 */
    val wirelessDebugging: Boolean?,
    val status: BuiltinShellStatus,
)

enum class StopReason {
    NotPaired,
    /** 无线调试关着：等它打开时再来（由监听开关的一方重新触发）。 */
    WirelessDebuggingOff,
    /** 这类失败需要用户出手（重新配对、连 Wi-Fi），重试没有用。 */
    NeedsUser,
    OutOfAttempts,
}

sealed interface RecoveryDecision {
    data object Healthy : RecoveryDecision
    /** 等 [delayMs] 后用已配对的钥匙重连一次。 */
    data class Try(val delayMs: Long) : RecoveryDecision
    /** 别人正在连：等 [delayMs] 再看，不叠一次重连。 */
    data class Wait(val delayMs: Long) : RecoveryDecision
    data class Stop(val reason: StopReason) : RecoveryDecision
}

data class RecoveryOutcome(val recovered: Boolean, val attempts: Int, val stoppedBecause: StopReason?)

/**
 * 助手不在时什么时候重起、隔多久、试几次。拔 USB、adbd 重启会带走所有 shell 身份进程，所以不区分「怎么没的」：
 * 只要配对还在、无线调试没被确认关掉、失败不需要用户出手，就按退避重连。
 * 信任网络与否读不到，一律先试，由连接结果说话。
 */
object RecoveryPolicy {
    const val MAX_ATTEMPTS = 5
    const val BASE_DELAY_MS = 2_000L
    const val MAX_DELAY_MS = 30_000L

    /** 位移上限：再高只会把数字挪废，并不改变封顶后的结果。 */
    private const val MAX_SHIFT = 20

    /** 第一次马上试，之后 2、4、8、16 秒……封顶 30 秒。 */
    fun backoffMs(attempt: Int): Long {
        if (attempt <= 0) return 0L
        return (BASE_DELAY_MS shl (attempt - 1).coerceAtMost(MAX_SHIFT)).coerceAtMost(MAX_DELAY_MS)
    }

    fun decide(inputs: RecoveryInputs, attempts: Int): RecoveryDecision = when (inputs.status) {
        BuiltinShellStatus.Running -> RecoveryDecision.Healthy
        else -> stopReason(inputs, attempts)?.let { RecoveryDecision.Stop(it) } ?: nextStep(inputs, attempts)
    }

    /** 该停下的原因；没有（还能再试）时返回 null。分支的顺序就是优先级。 */
    private fun stopReason(inputs: RecoveryInputs, attempts: Int): StopReason? {
        val status = inputs.status
        return when {
            !inputs.paired || status == BuiltinShellStatus.NotPaired -> StopReason.NotPaired
            inputs.wirelessDebugging == false -> StopReason.WirelessDebuggingOff
            status is BuiltinShellStatus.Failed && userMustAct(status.reason) -> StopReason.NeedsUser
            attempts >= MAX_ATTEMPTS -> StopReason.OutOfAttempts
            else -> null
        }
    }

    /** 还能再试：别人正在连就只等着看，否则按退避重连一次。 */
    private fun nextStep(inputs: RecoveryInputs, attempts: Int): RecoveryDecision {
        val delayMs = backoffMs(attempts)
        return if (inputs.status == BuiltinShellStatus.Connecting) {
            RecoveryDecision.Wait(delayMs.coerceAtLeast(BASE_DELAY_MS))
        } else {
            RecoveryDecision.Try(delayMs)
        }
    }

    private fun userMustAct(failure: BuiltinShellFailure) = failure.defaultFix != BuiltinShellFix.Retry

    /**
     * 按 [decide] 一直试到恢复或该停。[attempt] 是一次重连（真实实现是 `BuiltinShellControl.retry()`，
     * 它不抛异常，结果写进状态）；[inputs] 每轮重新读。
     */
    suspend fun recover(
        inputs: suspend () -> RecoveryInputs,
        attempt: suspend () -> Unit,
        sleep: suspend (Long) -> Unit = { delay(it) },
    ): RecoveryOutcome {
        var attempts = 0
        while (true) {
            when (val decision = decide(inputs(), attempts)) {
                RecoveryDecision.Healthy ->
                    return RecoveryOutcome(recovered = true, attempts = attempts, stoppedBecause = null)
                is RecoveryDecision.Stop ->
                    return RecoveryOutcome(recovered = false, attempts = attempts, stoppedBecause = decision.reason)
                is RecoveryDecision.Wait -> sleep(decision.delayMs)
                is RecoveryDecision.Try -> {
                    sleep(decision.delayMs)
                    attempt()
                }
            }
            attempts++
        }
    }
}

/** 开机时「重启后自动恢复」要不要动手：看它是否已经生效（默认打开、后来补上的也算），不看用户有没有拨过开关。 */
object BootRestore {
    const val MIN_SDK = 31

    enum class Gate { Act, Skip, PermissionMissing }

    fun gate(sdkInt: Int, consent: RecoveryConsent, holdsPermission: Boolean): Gate = when {
        sdkInt < MIN_SDK || !consent.isApplied(RecoveryCapability.RestoreAfterReboot) -> Gate.Skip
        !holdsPermission -> Gate.PermissionMissing
        else -> Gate.Act
    }
}

/** 上一次开机自动恢复的结果，设置页照实显示。 */
enum class RestoreOutcome {
    Restored,
    /** 这一项打开过，但修改系统设置的授权已经不在。 */
    PermissionMissing,
    /** 写无线调试开关被系统拒绝或写了不生效：这台手机不支持开机自动打开。 */
    SettingUnavailable,
    /** 开关打开了，但没能连上（比如不在信任的 Wi-Fi 上）。 */
    NotRecovered,
}

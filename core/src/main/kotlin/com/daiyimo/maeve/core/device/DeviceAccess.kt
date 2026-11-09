package com.daiyimo.maeve.core.device

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 主屏状态，和 Agent 的虚拟屏无关。读不到状态时由端口报错，不推定已经解锁。 */
data class DeviceState(
    val interactive: Boolean,
    val keyguardShowing: Boolean,
    val deviceLocked: Boolean,
    val secure: Boolean,
    val helperAvailable: Boolean,
    val userUnlocked: Boolean,
) {
    /** 亮屏、无锁屏、且用户存储已解密，三者缺一不可。 */
    val ready: Boolean
        get() = interactive && !keyguardShowing && !deviceLocked && userUnlocked
}

enum class CredentialKind { Pin, Password }

/**
 * 一次性的屏幕凭据。只在本机确定性流程里短暂存在：
 * [close] 擦除字符，[toString] 不带原文，调用方也不应把它留在别处。
 */
class DeviceCredential(
    val kind: CredentialKind,
    val value: CharArray,
    val revision: String = "",
) : AutoCloseable {
    override fun close() {
        value.fill(WIPE)
    }

    override fun toString() = "DeviceCredential($kind, redacted)"

    companion object {
        private const val WIPE = '\u0000'
        private val PIN_LENGTH = 4..16
        private val PIN_CHARS = '0'..'9'
        private val PASSWORD_LENGTH = 4..64
        private val PASSWORD_CHARS = ' '..'~'

        fun valid(kind: CredentialKind, value: CharArray): Boolean = when (kind) {
            CredentialKind.Pin -> value.size in PIN_LENGTH && value.all { it in PIN_CHARS }
            CredentialKind.Password -> value.size in PASSWORD_LENGTH && value.all { it in PASSWORD_CHARS }
        }
    }
}

/**
 * 平台适配层：唤醒、显示系统凭据页、确认输入目标、输入。
 * 绝不改变系统锁屏凭据本身。
 */
interface DeviceAccessPort {
    suspend fun state(): DeviceState

    suspend fun wake(): Boolean

    suspend fun dismiss(): Boolean

    /**
     * 安全锁屏还停在通知/指纹页时，尝试一次通用系统导航露出凭据页；
     * 不识别文字，也不输入凭据。默认实现表示平台没有这个能力。
     */
    suspend fun revealCredential(): Boolean = false

    suspend fun credentialReady(kind: CredentialKind): Boolean

    /** 只读识别当前唯一的系统凭据框；未知、歧义或平台不支持时返回 null，不聚焦也不输入。 */
    suspend fun detectedCredentialKind(): CredentialKind? = null

    suspend fun enter(credential: DeviceCredential): Boolean
}

/**
 * 许可和凭据来自手机设置，电脑不能修改。输入前先持久化占位；
 * 失败、取消或崩溃后保持占位，不再自动重试。
 */
interface DeviceAccessStore {
    val enabled: Boolean

    val credentialConfigured: Boolean

    val attemptBlocked: Boolean

    fun credential(): DeviceCredential?

    fun beginAttempt(credential: DeviceCredential): Boolean

    fun completeAttempt(credential: DeviceCredential)
}

data class DeviceAccessResult(
    val status: String,
    val state: DeviceState?,
    val enabled: Boolean,
    val credentialConfigured: Boolean,
    val attemptBlocked: Boolean,
    val storedKind: CredentialKind? = null,
    val detectedKind: CredentialKind? = null,
)

/**
 * 主屏解锁状态机。整个进程共用一份实例：串行化所有会读写系统状态的调用，
 * 保证并发请求最多只提交一次凭据。
 *
 * 成败只按系统状态判定，不按键事件或 shell 命令的返回码。
 */
class DeviceAccess(
    private val port: DeviceAccessPort,
    private val store: DeviceAccessStore,
    private val pollMs: Long = 150,
    private val waitMs: Long = 5_000,
) {
    /** 在凭据框出现之前，流程要么继续，要么已经带着结果结束。 */
    private sealed interface FieldWatch {
        object Ready : FieldWatch

        class Stopped(val result: DeviceAccessResult) : FieldWatch
    }

    private val gate = Mutex()

    suspend fun state(): DeviceAccessResult {
        val snapshot = readState()
        return reply(if (snapshot == null) "device_unavailable" else "state", snapshot)
    }

    suspend fun wake(): DeviceAccessResult = gate.withLock {
        preflight()?.let { return@withLock it }

        val before = readState() ?: return@withLock reply("device_unavailable")
        if (before.interactive) return@withLock reply("awake", before)

        if (!port.wake()) return@withLock reply("wake_failed", readState())
        val after = pollState { it.interactive }
        reply(if (after?.interactive == true) "awake" else "wake_failed", after)
    }

    suspend fun unlock(): DeviceAccessResult = gate.withLock {
        preflight()?.let { return@withLock it }

        var screen = readState() ?: return@withLock reply("device_unavailable")
        if (screen.ready) return@withLock reply("unlocked", screen)

        if (!screen.interactive) {
            if (!port.wake()) return@withLock reply("wake_failed", readState())
            screen = pollState { it.interactive } ?: return@withLock reply("device_unavailable")
            if (!screen.interactive) return@withLock reply("wake_failed", screen)
        }

        if (!store.enabled) return@withLock reply("disabled", screen)
        if (!port.dismiss()) return@withLock reply("dismiss_failed", readState())

        screen = pollState { it.ready || (it.secure && it.keyguardShowing) }
            ?: return@withLock reply("device_unavailable")
        if (screen.ready) return@withLock reply("unlocked", screen)
        if (!screen.secure) return@withLock reply("unlock_failed", screen)
        if (store.attemptBlocked) return@withLock reply("attempt_blocked", screen)

        val pending = store.credential() ?: return@withLock reply("credential_required", screen)
        pending.use { credential ->
            if (!DeviceCredential.valid(credential.kind, credential.value)) {
                return@withLock reply("credential_unavailable", screen)
            }
            when (val watch = watchCredentialField(credential.kind)) {
                FieldWatch.Ready -> submit(credential)
                is FieldWatch.Stopped -> return@withLock watch.result
            }
        }
    }

    /**
     * 凭据框出现前的等待循环。每个轮询周期先重读系统状态；
     * 已解锁、被禁用或 helper 掉线都立即结束，绝不在这些状态下继续输入。
     */
    private suspend fun watchCredentialField(kind: CredentialKind): FieldWatch {
        var navigated = false
        var waited = 0L

        while (waited < waitMs) {
            val current = readState() ?: return FieldWatch.Stopped(reply("device_unavailable"))
            if (current.ready) return FieldWatch.Stopped(reply("unlocked", current))
            if (!store.enabled) return FieldWatch.Stopped(reply("disabled", current))
            if (!current.helperAvailable) return FieldWatch.Stopped(reply("helper_unavailable", current))
            if (port.credentialReady(kind)) return FieldWatch.Ready

            // dismiss 之后先给系统一个轮询周期；仍认不出时才导航一次，
            // 避免在已经可用的 PIN 页上做多余的滑动。
            if (!navigated && waited >= pollMs) {
                navigated = true
                port.revealCredential()
            }
            delay(pollMs)
            waited += pollMs
        }

        val current = readState() ?: return FieldWatch.Stopped(reply("device_unavailable"))
        if (current.ready) return FieldWatch.Stopped(reply("unlocked", current))
        if (!store.enabled) return FieldWatch.Stopped(reply("disabled", current))
        if (!current.helperAvailable) return FieldWatch.Stopped(reply("helper_unavailable", current))

        val detected = port.detectedCredentialKind()
        return when {
            detected != null && detected != kind -> FieldWatch.Stopped(
                reply("credential_kind_mismatch", current, storedKind = kind, detectedKind = detected),
            )
            else -> FieldWatch.Stopped(reply("unsupported_keyguard", current))
        }
    }

    /**
     * 真正按键之前的所有检查都在这里。一旦 [DeviceAccessStore.beginAttempt] 成功，
     * 后续即使失败或中断也会留下占位，只有系统确认解锁才会清除。
     */
    private suspend fun submit(credential: DeviceCredential): DeviceAccessResult {
        if (!store.enabled) return reply("disabled", readState())
        if (!store.beginAttempt(credential)) return reply("attempt_blocked", readState())

        // 占位已落盘，这里再确认一次许可，避免设置刚好在这一刻被关掉。
        if (!store.enabled) return reply("disabled", readState())

        if (!port.enter(credential)) {
            val after = readState()
            if (after?.ready == true) {
                store.completeAttempt(credential)
                return reply("unlocked", after)
            }
            return reply("input_failed", after)
        }

        val settled = pollState { it.ready } ?: return reply("device_unavailable")
        return if (settled.ready) {
            store.completeAttempt(credential)
            reply("unlocked", settled)
        } else {
            reply("unlock_failed", settled)
        }
    }

    /** 解锁前的公共门槛：设置许可、用户已解锁、helper 可用。任一不满足都不碰系统界面。 */
    private suspend fun preflight(): DeviceAccessResult? {
        if (!store.enabled) return reply("disabled", readState())

        val snapshot = readState() ?: return reply("device_unavailable")
        return when {
            !snapshot.userUnlocked -> reply("first_unlock_required", snapshot)
            !snapshot.helperAvailable -> reply("helper_unavailable", snapshot)
            else -> null
        }
    }

    /** 读一次状态；平台异常统一折算成“读不到”，但取消必须原样向上传播。 */
    private suspend fun readState(): DeviceState? = try {
        port.state()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    /**
     * 先读一次，条件不满足就按 [pollMs] 继续读，最多花 [waitMs]。
     * 返回最后一次读到的状态（可能仍不满足条件），读不到时返回 null。
     */
    private suspend fun pollState(until: (DeviceState) -> Boolean): DeviceState? {
        var snapshot = readState()
        var waited = 0L
        while (snapshot != null && !until(snapshot) && waited < waitMs) {
            delay(pollMs)
            waited += pollMs
            snapshot = readState()
        }
        return snapshot
    }

    private fun reply(
        status: String,
        state: DeviceState? = null,
        storedKind: CredentialKind? = null,
        detectedKind: CredentialKind? = null,
    ) = DeviceAccessResult(
        status = status,
        state = state,
        enabled = store.enabled,
        credentialConfigured = store.credentialConfigured,
        attemptBlocked = store.attemptBlocked,
        storedKind = storedKind,
        detectedKind = detectedKind,
    )
}

package com.daiyimo.maeve.core.shell

import com.daiyimo.maeve.core.adb.AdbConnectionException
import com.daiyimo.maeve.core.adb.AdbKeyUnreadableException
import com.daiyimo.maeve.core.adb.AdbPairingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 引擎赖以工作的平台能力。凡是可能阻塞的调用，都由引擎切到 IO 调度器上再执行。 */
interface BuiltinShellPlatform {
    fun paired(): Boolean
    fun setPaired(paired: Boolean)

    /** 无线调试开关的状态；读不到时返回 null（不拦着，继续往下走）。 */
    fun wirelessDebugging(): Boolean?
    fun wifiConnected(): Boolean
    suspend fun findPairingPort(timeoutMs: Long): Int?

    /** 拿配对码去配对；没配成抛 [AdbPairingException]。 */
    fun pair(port: Int, code: String)

    /** 连本机 adbd 并起助手（不等交接）；连不上抛 [AdbConnectionException]，存的钥匙读不出抛 [AdbKeyUnreadableException]。 */
    suspend fun launchHelper()

    /** 让还在跑的助手撤掉它做过的改动并退出；没有助手就什么都不做。 */
    fun stopHelper()

    /** 删掉配对钥匙和交接口令；「用 root 启动」也一并关掉（解除配对就是停止手机操作，看门狗不再经 su 把助手起回来）。 */
    fun forget()

    /** 没配对时收下过助手（电脑经 USB 起的）：它停了要告诉用户怎么再起，而不是当作从没设置过。持久化。 */
    fun usbStarted(): Boolean
    fun setUsbStarted(on: Boolean)

    /** 「用 root 启动」此刻能不能用（用户的开关、有没有 su、su 上次的答复）；默认没有这条路。 */
    fun rootStart(): RootStart = RootStart.Off

    /**
     * 经 su 以 shell 身份起助手（不等交接），返回 su 的答复：[SuAnswer.Granted] 时助手已在起，交接由引擎等；
     * 手机上找不到 su 了为 null。失败抛异常（当作原因不明）。
     */
    suspend fun launchHelperAsRoot(): SuAnswer? = null

    /** 记下 su 的答复，持久化；null 是忘掉上一次的拒绝（用户点了重试）。 */
    fun setRootAnswer(answer: SuAnswer?) {}

    /** 用户明确点了 Root 失败后的重试。返回 true 表示平台已发起异步授权，完成后会通知引擎。 */
    fun requestRootStart(): Boolean = false
}

/** 看门狗重起助手的间隔：第 n 次失败后等 base·2^(n-1)，封顶 [maxMs]；连续失败 [maxAttempts] 次后停下等人或等开关变化。 */
data class Backoff(val baseMs: Long = 2_000, val maxMs: Long = 60_000, val maxAttempts: Int = 5) {
    fun delayAfter(failures: Int): Long? {
        if (failures >= maxAttempts) return null
        return (baseMs shl (failures - 1).coerceIn(0, 20)).coerceAtMost(maxMs)
    }
}

object BuiltinShellFailures {
    fun ofPairing(reason: AdbPairingException.Reason) = when (reason) {
        AdbPairingException.Reason.WRONG_CODE -> BuiltinShellFailure.WrongCode
        // 配对端口连不上：配对窗口已经关了（端口随窗口一起消失）
        AdbPairingException.Reason.CONNECT_FAILED -> BuiltinShellFailure.PairingWindowClosed
        AdbPairingException.Reason.TLS_FAILED, AdbPairingException.Reason.PROTOCOL -> BuiltinShellFailure.Unknown
        AdbPairingException.Reason.NOT_LOCAL -> BuiltinShellFailure.NotLocal
    }

    fun ofConnection(reason: AdbConnectionException.Reason) = when (reason) {
        AdbConnectionException.Reason.UNAUTHORIZED -> BuiltinShellFailure.AuthorizationRevoked
        AdbConnectionException.Reason.NOT_LOCAL -> BuiltinShellFailure.NotLocal
        AdbConnectionException.Reason.CONNECT_FAILED,
        AdbConnectionException.Reason.TLS_FAILED,
        AdbConnectionException.Reason.PROTOCOL -> BuiltinShellFailure.ConnectFailed
    }

    /** 看门狗自己再试有没有用：钥匙被撤销、地址不合法、没 Wi-Fi 时要等用户或开关变化。 */
    fun retryable(f: BuiltinShellFailure) = f == BuiltinShellFailure.ConnectFailed ||
        f == BuiltinShellFailure.HelperStartFailed || f == BuiltinShellFailure.Unknown
}

/**
 * 内置助手的状态与看门狗。[helper] 有值表示助手的 Binder 已在手（Running）；
 * 没有时：Maeve 自己起得来（用户打开了「用 root 启动」且能用，或配过对）就先等 [graceMs]
 * （还活着的助手会在 Maeve 进程起来后自己交接），再起一个：先经 su（见 [BuiltinShellPlatform.rootStart]），
 * 不行或没打开再经 adbd；失败按 [backoff] 重试。su 拒绝或不回话时记下来，不再自己去问，等用户点重试。
 * 要起助手时无线调试关着、又有 [autoOn]，就先替用户打开（条件和停下的规矩见 [WirelessDebuggingAutoOn]）。
 */
class BuiltinShellEngine(
    private val platform: BuiltinShellPlatform,
    private val helper: StateFlow<Any?>,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val backoff: Backoff = Backoff(),
    private val graceMs: Long = 3_000,
    private val handoffTimeoutMs: Long = 15_000,
    private val discoveryTimeoutMs: Long = 10 * 60_000L,
    private val pairingPortTimeoutMs: Long = 5_000,
    private val autoOn: WirelessDebuggingAutoOn? = null,
) : BuiltinShellControl {
    private val _status = MutableStateFlow(startupStatus())
    override val status: StateFlow<BuiltinShellStatus> = _status.asStateFlow()

    private val _discovery = MutableStateFlow<PairingDiscovery>(PairingDiscovery.Idle)
    override val pairingDiscovery: StateFlow<PairingDiscovery> = _discovery.asStateFlow()

    /** 最近一次配对或连接失败的异常类别与说明（诊断用，未脱敏）；连上后清空。 */
    @Volatile var lastError: String? = null
        private set

    private val launchLock = Mutex()
    private var consecutiveFailures = 0
    private var retryTask: Job? = null
    private var discoveryTask: Job? = null
    private var watching = false

    override val paired: Boolean get() = platform.paired()

    override val restart: HelperRestart get() = when {
        platform.paired() -> HelperRestart.Wireless
        platform.usbStarted() -> HelperRestart.UsbCommand
        else -> HelperRestart.None
    }

    override val rootStart: Boolean get() = platform.rootStart() == RootStart.Ready

    /** 把新状态发布到对外的 [status]：引擎里每一处状态变化都收口到这里。 */
    private fun publish(next: BuiltinShellStatus) {
        _status.value = next
    }

    /** Maeve 自己起得来助手：经 su（打开了且能用）或经已配对的无线调试。 */
    private fun canStartAlone(): Boolean = rootStart || platform.paired()

    private fun startupStatus(): BuiltinShellStatus = when {
        helper.value != null -> BuiltinShellStatus.Running
        canStartAlone() -> BuiltinShellStatus.Connecting
        // 电脑起过的助手可能还活着，等它来交接
        platform.usbStarted() -> BuiltinShellStatus.Connecting
        else -> restingStatus()
    }

    /**
     * Maeve 自己起不来、手里也没有助手时的状态：打开了「用 root 启动」却用不了就说原因；
     * 电脑起过的助手停了就照实说；否则就是还没设置。
     */
    private fun restingStatus(): BuiltinShellStatus {
        platform.rootStart().failure?.let { return BuiltinShellStatus.Failed(it) }
        return if (platform.usbStarted()) BuiltinShellStatus.Failed(BuiltinShellFailure.UsbHelperStopped) else BuiltinShellStatus.NotPaired
    }

    /** 开始盯着助手：掉线就重起。只在功能打开时调。 */
    @Synchronized
    fun start() {
        if (watching) return
        watching = true
        var firstEmission = true
        scope.launch {
            helper.collect { binder ->
                if (binder != null) {
                    synchronized(this@BuiltinShellEngine) {
                        consecutiveFailures = 0
                        retryTask?.cancel()
                    }
                    autoOn?.reset()
                    // 没配对、也没用 root 就来了：是电脑经 USB 起的。记下来，它停了才知道该怎么提示
                    if (!platform.paired() && platform.rootStart() == RootStart.Off) platform.setUsbStarted(true)
                    publish(BuiltinShellStatus.Running)
                } else if (!canStartAlone()) {
                    // App 刚起时还活着的助手会自己来交接；用着用着断了也可能是正在换一个新的来交接：
                    // 都先等一会儿再说它停了，免得界面一闪「已停止」
                    if (platform.usbStarted()) {
                        publish(BuiltinShellStatus.Connecting)
                        scheduleRetry(graceMs)
                    } else {
                        publish(restingStatus())
                    }
                } else {
                    if (_status.value == BuiltinShellStatus.Running) publish(BuiltinShellStatus.Connecting)
                    scheduleRetry(if (firstEmission) graceMs else backoff.baseMs)
                }
                firstEmission = false
            }
        }
    }

    /** 有人要用助手却没有：没在起、也没排着重试就立刻起一次。 */
    fun need() {
        if (helper.value != null || !canStartAlone()) return
        synchronized(this) { if (retryTask?.isActive == true) return }
        if (launchLock.isLocked) return
        scheduleRetry(0)
    }

    /**
     * 「用 root 启动」的开关或 su 的答复变了（用户在设置、引导页里打开、关掉、重新授权）：
     * 助手不在就按新情况从头再试；Maeve 自己起不来了就照实改状态。
     */
    fun rootChanged() {
        if (helper.value != null) return
        synchronized(this) {
            consecutiveFailures = 0
            retryTask?.cancel()
        }
        if (canStartAlone()) scheduleRetry(0) else publish(restingStatus())
    }

    /**
     * 无线调试开关变了：刚打开就从头再试。关掉时只改状态，不马上替用户打开（可能是用户亲手关的），等要用助手时再说。
     * Maeve 自己正在写这个开关时不跟着动：系统可能马上拨回去，由写的那一边判断。
     */
    fun wirelessDebuggingChanged() {
        // 经 su 起助手不看无线调试
        if (helper.value != null || !platform.paired() || rootStart) return
        if (autoOn?.writing == true) return
        if (platform.wirelessDebugging() == false) {
            publish(wirelessOffStatus())
            return
        }
        autoOn?.reset()
        synchronized(this) { consecutiveFailures = 0 }
        scheduleRetry(0)
    }

    /**
     * 连上了 Wi-Fi：之前卡在「没有 Wi-Fi」就马上再试（能上网与否不限）；能替用户打开无线调试时，
     * 卡在无线调试关着（包括上个网络不允许）也马上再试。换了网络，上个网络上的拒绝不再算数。
     */
    fun wifiAvailable() {
        autoOn?.reset()
        val currentStatus = _status.value
        val wirelessBlocked = currentStatus == BuiltinShellStatus.WirelessDebuggingOff ||
            currentStatus == BuiltinShellStatus.Failed(BuiltinShellFailure.WifiNotAllowed)
        val waitingForWifi = currentStatus == BuiltinShellStatus.Failed(BuiltinShellFailure.NoWifi)
        if (!waitingForWifi && !(autoOn != null && wirelessBlocked)) return
        synchronized(this) { consecutiveFailures = 0 }
        scheduleRetry(0)
    }

    /** 无线调试关着时的状态：这个 Wi-Fi 上刚被系统拨回过就照实说，否则就是关着。 */
    private fun wirelessOffStatus(): BuiltinShellStatus =
        if (autoOn?.blocked?.value == AutoOnBlock.NotAllowedHere) {
            BuiltinShellStatus.Failed(BuiltinShellFailure.WifiNotAllowed)
        } else {
            BuiltinShellStatus.WirelessDebuggingOff
        }

    /** 无线调试关着：能替用户打开就打开，开着且没被拨回返回 true 继续连；否则写好状态返回 false。 */
    private suspend fun tryTurnOnWirelessDebugging(): Boolean {
        val auto = autoOn ?: return false.also { publish(wirelessOffStatus()) }
        when (val gate = auto.gate(platform.wifiConnected())) {
            AutoOnGate.Act -> {
                publish(BuiltinShellStatus.Connecting)
                if (withContext(io) { auto.turnOn() }) return true
            }
            // 离上一次写太近：到时候再来一次
            is AutoOnGate.Wait -> synchronized(this) {
                retryTask = scope.launch {
                    delay(gate.ms)
                    startHelperOnce()
                }
            }
            is AutoOnGate.Skip -> Unit
        }
        publish(wirelessOffStatus())
        return false
    }

    @Synchronized
    private fun scheduleRetry(delayMs: Long) {
        retryTask?.cancel()
        retryTask = scope.launch {
            delay(delayMs)
            startHelperOnce()
        }
    }

    private suspend fun startHelperOnce(): Unit = launchLock.withLock {
        if (helper.value != null) return@withLock
        // 先走 root：不用配对、不用 Wi-Fi；不行时配过对就接着走无线调试
        if (rootStart) {
            publish(BuiltinShellStatus.Connecting)
            val rootFailure = launchViaRoot()
            if (rootFailure == null) {
                lastError = null
                publish(BuiltinShellStatus.Running)
                return@withLock
            }
            if (!platform.paired()) {
                reportFailure(rootFailure)
                return@withLock
            }
        }
        if (!platform.paired()) {
            publish(restingStatus())
            return@withLock
        }
        if (platform.wirelessDebugging() == false && !tryTurnOnWirelessDebugging()) return@withLock
        publish(BuiltinShellStatus.Connecting)
        val adbFailure = launchViaAdb()
        if (adbFailure == null) {
            lastError = null
            publish(BuiltinShellStatus.Running)
            return@withLock
        }
        reportFailure(adbFailure)
    }

    /** 报失败；看门狗自己再试有用的按 [backoff] 排下一次。 */
    private fun reportFailure(failure: BuiltinShellFailure) {
        publish(BuiltinShellStatus.Failed(failure))
        val next = synchronized(this) { ++consecutiveFailures }
        if (!BuiltinShellFailures.retryable(failure)) return
        val wait = backoff.delayAfter(next) ?: return
        synchronized(this) {
            retryTask = scope.launch {
                delay(wait)
                startHelperOnce()
            }
        }
    }

    /** 经 su 起一次助手并等交接；su 的答复记下来（拒绝、不回话之后就不再自己去问）。 */
    private suspend fun launchViaRoot(): BuiltinShellFailure? {
        val answer = try {
            withContext(io) { platform.launchHelperAsRoot() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = formatError(e)
            return BuiltinShellFailure.Unknown
        } ?: return BuiltinShellFailure.RootUnavailable.also { lastError = "su not found" }
        platform.setRootAnswer(answer)
        if (answer == SuAnswer.Granted) {
            return if (waitForHandoff()) null
            else BuiltinShellFailure.HelperStartFailed.also { lastError = "helper handoff timed out (root)" }
        }
        return when (answer) {
            SuAnswer.Granted -> null
            SuAnswer.Denied -> BuiltinShellFailure.RootDenied.also { lastError = "su denied" }
            SuAnswer.NoAnswer -> BuiltinShellFailure.RootNoAnswer.also { lastError = "su did not answer" }
            SuAnswer.WrongUid -> BuiltinShellFailure.RootNoShellUid.also { lastError = "su cannot switch to the shell uid" }
        }
    }

    private suspend fun launchViaAdb(): BuiltinShellFailure? = try {
        withContext(io) { platform.launchHelper() }
        if (waitForHandoff()) null
        else BuiltinShellFailure.HelperStartFailed.also { lastError = "helper handoff timed out" }
    } catch (e: CancellationException) {
        throw e
    } catch (e: AdbConnectionException) {
        lastError = formatError(e)
        val mapped = BuiltinShellFailures.ofConnection(e.reason)
        if (mapped == BuiltinShellFailure.ConnectFailed && !platform.wifiConnected()) BuiltinShellFailure.NoWifi else mapped
    } catch (e: AdbKeyUnreadableException) {
        lastError = formatError(e)
        BuiltinShellFailure.KeyUnreadable
    } catch (e: Exception) {
        lastError = formatError(e)
        BuiltinShellFailure.Unknown
    }

    private suspend fun waitForHandoff(): Boolean =
        withTimeoutOrNull(handoffTimeoutMs) { helper.first { it != null } } != null

    private fun formatError(e: Exception): String {
        val reason = when (e) {
            is AdbConnectionException -> e.reason
            is AdbPairingException -> e.reason
            else -> null
        }
        val head = e.javaClass.simpleName + (reason?.let { "($it)" } ?: "")
        val detail = e.message?.let { ": " + it.take(200) } ?: ""
        return head + detail
    }

    override fun startDiscovery() {
        synchronized(this) {
            // 还在找也重新开始：用户又回到配对这件事上，找的时限从现在算
            discoveryTask?.cancel()
            _discovery.value = PairingDiscovery.Searching
            discoveryTask = scope.launch {
                val port = withContext(io) { platform.findPairingPort(discoveryTimeoutMs) }
                _discovery.value = port?.let { PairingDiscovery.Found(it) } ?: PairingDiscovery.Idle
            }
        }
    }

    override fun stopDiscovery() {
        synchronized(this) {
            discoveryTask?.cancel()
            discoveryTask = null
        }
        _discovery.value = PairingDiscovery.Idle
    }

    override suspend fun startPairing(code: String, port: Int?) {
        publish(BuiltinShellStatus.Connecting)
        val resolvedPort = port ?: (_discovery.value as? PairingDiscovery.Found)?.port
            ?: withContext(io) { platform.findPairingPort(pairingPortTimeoutMs) }
        if (resolvedPort == null) {
            lastError = "pairing port not found"
            publish(BuiltinShellStatus.Failed(BuiltinShellFailure.PairingWindowClosed))
            return
        }
        val pairingFailure = try {
            withContext(io) { platform.pair(resolvedPort, code) }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: AdbPairingException) {
            lastError = formatError(e)
            BuiltinShellFailures.ofPairing(e.reason)
        } catch (e: Exception) {
            lastError = formatError(e)
            BuiltinShellFailure.Unknown
        }
        if (pairingFailure != null) {
            publish(BuiltinShellStatus.Failed(pairingFailure))
            return
        }
        platform.setPaired(true)
        stopDiscovery()
        retry()
    }

    override suspend fun retry() {
        synchronized(this) {
            consecutiveFailures = 0
            retryTask?.cancel()
        }
        // 用户看着 su 拒绝、不回话的提示点了重试：由平台明确再请求一次，未授权状态不会自动问 su
        val current = _status.value
        if (current is BuiltinShellStatus.Failed && current.reason in ROOT_ASKABLE) {
            if (platform.requestRootStart()) {
                publish(BuiltinShellStatus.Connecting)
                return
            }
            platform.setRootAnswer(null)
        }
        // 用户点了重试：之前替用户打开无线调试时的拒绝不再算数（不允许的网络上系统会再问一次）
        autoOn?.reset()
        if (helper.value != null) {
            publish(BuiltinShellStatus.Running)
            return
        }
        startHelperOnce()
    }

    override suspend fun unpair() {
        synchronized(this) { retryTask?.cancel() }
        launchLock.withLock {
            withContext(io) {
                // 先忘掉「电脑起过」：助手停下时状态就是没设置，不提示再跑命令
                platform.setUsbStarted(false)
                runCatching { platform.stopHelper() }
                platform.forget()
            }
            platform.setPaired(false)
            publish(BuiltinShellStatus.NotPaired)
        }
    }

    private companion object {
        /** 点重试会再问 su 的几种失败。 */
        val ROOT_ASKABLE = setOf(BuiltinShellFailure.RootDenied, BuiltinShellFailure.RootNoAnswer, BuiltinShellFailure.RootNoShellUid)
    }
}

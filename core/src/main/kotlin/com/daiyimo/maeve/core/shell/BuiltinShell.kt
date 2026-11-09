package com.daiyimo.maeve.core.shell

import kotlinx.coroutines.flow.StateFlow

/**
 * 内置助手（经本机「无线调试」、电脑或 su 以 shell 身份拉起的特权进程）此刻的状态。
 * 界面、设置页与任务入口都只看它；配对、连接、看门狗各自把结果写成下面几种之一。
 */
sealed interface BuiltinShellStatus {
    /** 没配对过，或是已经解除了配对：先走一遍引导。 */
    data object NotPaired : BuiltinShellStatus

    /** 配对过，无线调试却关着：把开关打开就会自己连上。 */
    data object WirelessDebuggingOff : BuiltinShellStatus

    /** 正在配对、连接，或是正在把助手拉起来。 */
    data object Connecting : BuiltinShellStatus

    /** 助手的 Binder 已经在手，能用了。 */
    data object Running : BuiltinShellStatus

    /** 出错：[reason] 是类别，[fix] 是一键修复走哪条路。 */
    data class Failed(val reason: BuiltinShellFailure, val fix: BuiltinShellFix = reason.defaultFix) : BuiltinShellStatus
}

/** 失败类别。文案留在界面层；这里只区分「用户接下来该做什么」不同的情况。 */
enum class BuiltinShellFailure(val defaultFix: BuiltinShellFix, val duringPairing: Boolean) {
    /** 6 位配对码不对，或者已经过期。 */
    WrongCode(BuiltinShellFix.RePair, duringPairing = true),

    /** 填码之前配对窗口就关了（找不到配对端口）。 */
    PairingWindowClosed(BuiltinShellFix.RePair, duringPairing = true),

    /** 没有 Wi-Fi：无线调试打不开或连不上。 */
    NoWifi(BuiltinShellFix.OpenWifi, duringPairing = false),

    /** Maeve 替用户打开无线调试，系统立刻又关掉：这个 Wi-Fi 还没被允许无线调试，需要用户亲手打开一次并允许。 */
    WifiNotAllowed(BuiltinShellFix.OpenWirelessDebugging, duringPairing = false),

    /** 连不上本机的无线调试。 */
    ConnectFailed(BuiltinShellFix.Retry, duringPairing = false),

    /** ADB 目标不是本机自己的地址：这是调用方错误，不是会自行恢复的网络故障。 */
    NotLocal(BuiltinShellFix.Retry, duringPairing = false),

    /** 已配对的授权不再被承认（太久没用被系统撤销，或用户在系统里删了）。 */
    AuthorizationRevoked(BuiltinShellFix.RePair, duringPairing = false),

    /** 配对钥匙存着却读不出来：设备只认那一把，不能悄悄换，必须重新配对（配对时才换新钥匙）。 */
    KeyUnreadable(BuiltinShellFix.RePair, duringPairing = false),

    /** 连上了，但助手没能启动或交接。 */
    HelperStartFailed(BuiltinShellFix.Retry, duringPairing = false),

    /**
     * 经 USB 启动命令起的助手停了（多半是手机重启过），又没配对过无线调试，Maeve 自己起不来：
     * 需要再运行一次启动命令。不往无线调试引，因为有的手机根本没有可用的无线调试。
     */
    UsbHelperStopped(BuiltinShellFix.RunUsbCommand, duringPairing = false),

    /** 打开了「用 root 启动」，su 却拒绝：不再自己去问，等用户点重试（重试时 su 会再弹一次授权）。 */
    RootDenied(BuiltinShellFix.Retry, duringPairing = false),

    /** su 没回话（授权弹框没人点）：同样等用户点重试。 */
    RootNoAnswer(BuiltinShellFix.Retry, duringPairing = false),

    /** 打开了「用 root 启动」，手机上却找不到 su 了（root 被去掉）：关掉这一项，或去配对无线调试。 */
    RootUnavailable(BuiltinShellFix.RePair, duringPairing = false),

    /** su 答应了，却切不到 shell 身份：这种 su 起不了助手，改用无线调试或电脑。 */
    RootNoShellUid(BuiltinShellFix.RePair, duringPairing = false),

    /** 归不到上面任何一类：当作还会自行恢复的故障，让用户重试。 */
    Unknown(BuiltinShellFix.Retry, duringPairing = false),
}

enum class BuiltinShellFix {
    /** 还用已经配对上的那把钥匙再连一次。 */
    Retry,

    /** 重新走一遍配对那一步。 */
    RePair,

    /** 去系统的 Wi-Fi 设置页。 */
    OpenWifi,

    /** 去开发者选项里亲手打开无线调试。 */
    OpenWirelessDebugging,

    /** 再运行一次 USB 启动命令（界面给出命令和复制按钮）。 */
    RunUsbCommand,
}

/**
 * 助手停了以后靠什么再起来，取决于它上次是怎么起来的。停了时往哪儿引导用户、成功页与设置页怎么说，都看它。
 */
enum class HelperRestart {
    /** 还没设置过：助手一次都没起来过。 */
    None,

    /** 配对过无线调试：Maeve 自己经它再起；手机重启后打开无线调试即可（能替用户打开时连这一步也不用）。 */
    Wireless,

    /**
     * 只用 USB 启动命令起过、没配对过无线调试：Maeve 自己起不来，停了（如手机重启后）要再运行一次命令。
     * 有的手机没有可用的无线调试，只能走这条路，所以这时不提示去打开无线调试。
     */
    UsbCommand,
}

/** 找「使用配对码配对设备」窗口公布的配对端口（本机 mDNS）的结果。 */
sealed interface PairingDiscovery {
    /** 没在找。 */
    data object Idle : PairingDiscovery

    /** 正在等 mDNS 应答。 */
    data object Searching : PairingDiscovery

    /** 端口找到了：[port] 就是它。 */
    data class Found(val port: Int) : PairingDiscovery
}

/** 助手只经 USB 启动命令起过：Maeve 自己起不来，停了只能让用户再运行一次命令。 */
private val BuiltinShellStatus.isUsbOnly: Boolean
    get() = this == BuiltinShellStatus.Failed(BuiltinShellFailure.UsbHelperStopped)

/**
 * 内置助手对界面的接口。实现负责配对（mDNS 找端口、6 位码）、连接本机 adbd、拉起并看住助手；
 * 所有结果都通过 [status] 反映，动作本身不抛异常。
 */
interface BuiltinShellControl {
    val status: StateFlow<BuiltinShellStatus>
    val pairingDiscovery: StateFlow<PairingDiscovery>

    /** 配对过无线调试（Maeve 能自己经它起助手、跑命令）。助手也可能是电脑经 USB 起的，所以运行中不等于配对过。 */
    val paired: Boolean get() = status.value != BuiltinShellStatus.NotPaired

    /** 助手停了以后靠什么再起来（见 [HelperRestart]）。 */
    val restart: HelperRestart get() = when {
        status.value.isUsbOnly -> HelperRestart.UsbCommand
        paired -> HelperRestart.Wireless
        else -> HelperRestart.None
    }

    /** 用户打开了「用 root 启动」且此刻能用：看门狗先经 su 起助手（状态是正在连接时，界面说正在用 root 启动）。 */
    val rootStart: Boolean get() = false

    /** 开始找配对端口（用户正去打开配对窗口）。重复调用无害：还在找就从这时起重新计时。 */
    fun startDiscovery()
    fun stopDiscovery()

    /** 用 6 位配对码配对，成功后接着连接并启动助手。[port] 为空时用找到的配对端口。 */
    suspend fun startPairing(code: String, port: Int? = null)

    /** 拿已经配好的钥匙重新连一次，并把助手拉起来。 */
    suspend fun retry()

    /** 删除配对钥匙、停掉助手、撤销助手给 Maeve 的授权；之后状态为 [BuiltinShellStatus.NotPaired]。 */
    suspend fun unpair()
}

/** 配对码：6 位数字。用户可能带空格、横线或全角数字，一并规范掉；不是 6 位数字返回 null。 */
object PairingCode {
    const val LENGTH = 6

    /** 输入法可能打出的各种横线：半角、全角、连字号、短横、长横。 */
    private const val DASHES = "-－‐‑–—"

    fun normalize(input: String): String? = digitsIn(input).takeIf { it.isPairingCode() }

    /** 只留数字：空白和横线一律去掉，全角数字折成半角。 */
    private fun digitsIn(input: String): String = buildString {
        for (ch in input) {
            if (ch.isWhitespace() || ch in DASHES) continue
            append(if (ch in '０'..'９') '0' + (ch - '０') else ch)
        }
    }

    private fun String.isPairingCode(): Boolean = length == LENGTH && all { it in '0'..'9' }

    /** 手动输入的端口：1–65535。 */
    fun port(input: String): Int? = input.trim().toIntOrNull()?.takeIf { it in 1..65535 }
}

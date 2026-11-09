package com.daiyimo.maeve.core.shell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 助手把 Binder 交给 Maeve 时双方共用的约定：provider 方法与 Bundle 键、助手进程名、启动命令，
 * 以及 Maeve 收下之前要做的检查。
 */
object HelperHandoff {
    const val SHELL_UID = 2000
    const val METHOD_ATTACH = "attach"
    const val HELPER_CLASS = "com.daiyimo.maeve.shell.HelperMain"

    // 助手 → Maeve
    const val KEY_NONCE = "nonce"
    const val KEY_BINDER = "binder"
    const val KEY_UID = "uid"
    const val KEY_PID = "pid"
    const val KEY_APK = "apk"

    /** 助手拉起 Maeve 的记录（见 [HelperRestoreReport]）；没拉过时不带。 */
    const val KEY_RESTORED_AT = "restoredAt"
    const val KEY_RESTORES = "restores"
    const val KEY_GAVE_UP_AT = "restoreGaveUpAt"

    // Maeve → 助手
    const val KEY_ACCEPTED = "accepted"
    const val KEY_NEXT = "next"
    const val KEY_TOKEN = "token"
    const val KEY_REASON = "reason"

    /** 「用电脑启动」的脚本文件名；放在 App 外部文件目录里，电脑经 USB 调试以 shell 身份去读。 */
    const val USB_SCRIPT = "usb-start.sh"

    /** 主用户的外部存储在 shell 里也叫 /sdcard；给用户看的命令用这个短写法，别的用户按实际路径。 */
    private const val PRIMARY_STORAGE = "/storage/emulated/0/"

    fun authority(packageName: String) = "$packageName.helper"

    /** 助手的进程名（app_process 的 --nice-name）；也用来认出旧的助手实例。 */
    fun processName(packageName: String) = "$packageName:helper"

    /**
     * 经 adbd 的 exec 让 shell 执行的启动命令（adbd 用 `sh -c` 跑它）。流一关，adbd 会杀掉 sh 这一组进程，
     * 所以助手用 setsid 另开会话；sh 再 `read` 等 App 收下交接后关流，避免 setsid 还没生效 sh 就退出、把助手一起带走。
     *
     * 口令不进命令行（同为 shell 的进程能读 /proc/<pid>/cmdline）：App 开流后写一行 [stdinLine]，
     * sh 读进变量再用内建 echo 经管道交给助手；助手的标准输入是这条管道，不是 adb 的流。
     * [certPin] 是 Maeve 签名证书的指纹，不是秘密，可以放在参数里。
     */
    fun command(apkPath: String, packageName: String, appUid: Int, certPin: String): String {
        val helperClasspath = quote(apkPath)
        val niceName = quote(processName(packageName))
        val launch = "CLASSPATH=$helperClasspath setsid app_process /system/bin " +
            "--nice-name=$niceName $HELPER_CLASS ${quote(packageName)} $appUid ${quote(certPin)} " +
            ">/dev/null 2>&1"
        return "read n; (echo \"\$n\" | $launch &); read _"
    }

    fun stdinLine(nonce: String): ByteArray = "$nonce\n".toByteArray(Charsets.UTF_8)

    /**
     * 经 su 起助手时交给 `su 2000 -c` 的命令：先打一行 `id -u`，App 读到 2000（su 答应了且切到了 shell 身份）才写口令；
     * 之后与 [command] 相同（口令走标准输入，App 收下交接后关流）。su 没答应时一行也没有，口令就不会写出去。
     */
    fun rootCommand(apkPath: String, packageName: String, appUid: Int, certPin: String): String =
        "id -u; " + command(apkPath, packageName, appUid, certPin)

    /** 电脑上要跑的命令。[scriptPath] 是脚本在手机上的实际路径。 */
    fun usbCommand(scriptPath: String): String {
        val shownPath = if (scriptPath.startsWith(PRIMARY_STORAGE)) {
            "/sdcard/" + scriptPath.removePrefix(PRIMARY_STORAGE)
        } else {
            scriptPath
        }
        return "adb shell sh $shownPath"
    }

    /**
     * 电脑经 USB 调试执行的启动脚本：起与 [command] 同一个助手、同样的参数，只是口令换成 [nonce]（写在脚本里，见 [HandoffNonces.usbNonce]）。
     * adb shell 会话一结束（命令跑完、拔线），adbd 就会杀掉它那一组进程：助手用 nohup 忽略挂断信号，再用 setsid 另开会话，
     * 放进后台子 shell 里起（子 shell 退出后归 init 管），标准输出与错误丢掉，不再连着 adb 的流。脚本再等一会儿让 setsid 生效，
     * 看到助手进程就说一句「正在启动」，不在（多半口令过期或 Maeve 不认）就请用户打开 Maeve 再跑一次。
     * 多数手机这样起完拔线也能继续用；部分系统会在拔线时停掉助手，插回数据线再跑一次本命令即可，脚本不保证拔线后一定可用。
     * 只用安卓原版 sh 与 toybox 里有的命令。不是 shell 身份（没经 adb 跑）时不起：Maeve 只收 shell 交来的助手。
     */
    fun usbScript(apkPath: String, packageName: String, appUid: Int, certPin: String, nonce: String, scriptPath: String): String {
        val helperName = quote(processName(packageName))
        val rerun = usbCommand(scriptPath)
        val launch = "CLASSPATH=${quote(apkPath)} nohup setsid app_process /system/bin --nice-name=$helperName " +
            "$HELPER_CLASS ${quote(packageName)} $appUid ${quote(certPin)} >/dev/null 2>&1"
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# Written by Maeve. Starts Maeve's phone helper from a computer: $rerun")
            appendLine("if [ \"\$(id -u)\" != \"$SHELL_UID\" ]; then")
            appendLine("  echo \"Run this from a computer over USB: $rerun\"")
            appendLine("  exit 1")
            appendLine("fi")
            appendLine("if [ ! -f ${quote(apkPath)} ]; then")
            appendLine("  echo \"Maeve was updated or reinstalled. Open Maeve once, then run this command again.\"")
            appendLine("  exit 1")
            appendLine("fi")
            appendLine("n=${quote(nonce)}")
            appendLine("(echo \"\$n\" | $launch &)")
            appendLine("sleep 2")
            appendLine("if pidof $helperName >/dev/null 2>&1; then")
            appendLine("  echo \"Maeve helper starting... Open Maeve to confirm phone control is ready.\"")
            appendLine("  echo \"On most phones it keeps running after you unplug the cable. After a phone restart, or if phone control stopped after unplugging, plug in and run this command again.\"")
            appendLine("else")
            appendLine("  echo \"Maeve helper did not start. Open Maeve once, then run this command again.\"")
            appendLine("  exit 1")
            appendLine("fi")
        }
    }

    /**
     * 什么时候重写「用电脑启动」脚本：助手来或走，以及口令整体作废（[HandoffNonces.resets]，例如解除配对）。
     * 发出的值是此刻助手在不在。后一种情况助手可能一直不在，不跟着重写的话脚本里留的就是作废的口令。
     */
    fun usbScriptWrites(helper: Flow<Any?>, resets: Flow<Int>): Flow<Boolean> =
        combine(helper, resets) { connection, _ -> connection != null }

    /**
     * 此刻该写的脚本：助手连着（[connected]）就写不带口令的 [usbStub]，也不去取口令；否则写带新口令的 [usbScript]。
     * [certPin]、[nonce] 只在确实要写带口令的脚本时才取。
     */
    fun usbFile(
        connected: Boolean, apkPath: String, packageName: String, appUid: Int, certPin: () -> String, nonce: () -> String, scriptPath: String,
    ): String = if (connected) {
        usbStub(packageName, scriptPath)
    } else {
        usbScript(apkPath, packageName, appUid, certPin(), nonce(), scriptPath)
    }

    /** 助手已连着时换上的脚本：不带口令，只说一句。进程不在（如手机重启过、Maeve 还没打开）就请用户先打开 Maeve。 */
    fun usbStub(packageName: String, scriptPath: String): String {
        val helperName = quote(processName(packageName))
        return buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# Written by Maeve. The phone helper is connected, so this file holds no start code.")
            appendLine("if pidof $helperName >/dev/null 2>&1; then")
            appendLine("  echo \"Maeve helper is already running. Nothing to do.\"")
            appendLine("else")
            appendLine("  echo \"Maeve helper is not running. Open Maeve once, then run this command again: ${usbCommand(scriptPath)}\"")
            appendLine("  exit 1")
            appendLine("fi")
        }
    }

    /** 签名证书（DER）的 SHA-256 指纹；多个签名者排序后以逗号相连。没有证书为 null。 */
    fun certPin(certs: List<ByteArray>?): String? {
        if (certs.isNullOrEmpty()) return null
        return certs.map { sha256Hex(it) }.sorted().joinToString(",")
    }

    enum class Reject {
        /** 调用方不是 shell（uid 2000）。 */
        NotShell,
        /** 助手自报的 uid/pid 与 Binder 看到的调用方不一致。 */
        IdMismatch,
        /** 助手跑的不是当前安装的 APK（Maeve 更新过）：让它退出，再起一个新的。 */
        StaleApk,
        NoBinder,
        BadNonce,
        /** Binder 所在的进程不是来交接的那个。 */
        BinderElsewhere,
    }

    /** 不碰 Binder 就能做的检查；通过返回 null。口令另由 [HandoffNonces] 核对。 */
    fun check(
        callingUid: Int, callingPid: Int, reportedUid: Int, reportedPid: Int,
        helperApk: String?, appApk: String, hasBinder: Boolean,
    ): Reject? = when {
        callingUid != SHELL_UID -> Reject.NotShell
        reportedUid != callingUid || reportedPid != callingPid -> Reject.IdMismatch
        helperApk != appApk -> Reject.StaleApk
        !hasBinder -> Reject.NoBinder
        else -> null
    }

    private fun sha256Hex(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { byte -> "%02x".format(byte) }

    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}

/**
 * 持久化的几枚口令：[resume] 给正在跑的助手下次交接用，[launch] 给刚从 adbd 起的新助手用，
 * [launchAt] 是 [launch] 的签发时间（开机以来的毫秒数）；[usb] 写在「用电脑启动」脚本里，
 * [usbAt] 是它的签发时间（墙上时钟毫秒：脚本要跨过重启还能用）。
 */
data class NonceSlots(
    val resume: String? = null, val launch: String? = null, val launchAt: Long = 0,
    val usb: String? = null, val usbAt: Long = 0,
)

interface NonceStore {
    fun load(): NonceSlots
    fun save(slots: NonceSlots)
}

/**
 * Maeve 生成、用一次就作废的交接口令。任何一枚被用掉，几枚一起作废，并换发一枚新的 [NonceSlots.resume] 给收下的助手；
 * 存下来是为了 Maeve 进程被杀后，还活着的助手拿它重新交接时新进程能认出。
 * [clock] 量启动口令的时效（开机以来），[wallClock] 量「用电脑启动」口令的时效。
 */
class HandoffNonces(
    private val store: NonceStore,
    private val clock: () -> Long,
    private val wallClock: () -> Long = clock,
    private val random: () -> String = ::randomNonce,
) {
    private val _resets = MutableStateFlow(0)

    /** 口令被整体作废（[clear]）的次数：变了就说明写出去的口令（如脚本里那枚）都不能用了。 */
    val resets: StateFlow<Int> = _resets.asStateFlow()

    @Synchronized
    fun issueLaunch(): String {
        val fresh = random()
        store.save(store.load().copy(launch = fresh, launchAt = clock()))
        return fresh
    }

    /**
     * 写进「用电脑启动」脚本的口令。手上那枚还新（剩余时效超过一半）就沿用，免得电脑正在跑脚本时被换掉；
     * 否则换发一枚。与别的口令一样，用掉一次就作废（见 [accept]）。
     */
    @Synchronized
    fun usbNonce(): String {
        val slots = store.load()
        val age = wallClock() - slots.usbAt
        if (slots.usb != null && age in 0..USB_TTL_MS / 2) return slots.usb
        val fresh = random()
        store.save(slots.copy(usb = fresh, usbAt = wallClock()))
        return fresh
    }

    @Synchronized
    fun matches(nonce: String?): Boolean {
        if (nonce == null) return false
        val slots = store.load()
        // 启动口令只在交接等待期内有效；没用上的过期作废
        val launchAge = clock() - slots.launchAt
        val launch = slots.launch?.takeIf { launchAge in 0..LAUNCH_TTL_MS }
        // 脚本里的口令等的是用户插线跑命令，给得长一些；过期或时钟倒退都不认
        val usb = slots.usb?.takeIf { wallClock() - slots.usbAt in 0..USB_TTL_MS }
        return same(nonce, slots.resume) or same(nonce, launch) or same(nonce, usb)
    }

    /** 口令对就用掉它并返回换发的新口令；不对返回 null。 */
    @Synchronized
    fun accept(nonce: String?): String? {
        if (!matches(nonce)) return null
        val next = random()
        store.save(NonceSlots(resume = next))
        return next
    }

    @Synchronized
    fun clear() {
        store.save(NonceSlots())
        _resets.value++
    }

    private fun same(a: String, b: String?) =
        b != null && MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    companion object {
        /** 覆盖 App 开着流等交接、引擎再等一轮交接的总时长。 */
        const val LAUNCH_TTL_MS = 30_000L

        /**
         * 「用电脑启动」口令的时效：脚本写好后用户可能过一阵才插线跑命令（或重启后再跑一次）。
         * Maeve 每次打开、看不到助手、打开引导页时都会重写脚本，用了一半就换新。
         */
        const val USB_TTL_MS = 24 * 60 * 60_000L

        private val rng = SecureRandom()
        fun randomNonce(): String = ByteArray(32).also(rng::nextBytes).joinToString("") { "%02x".format(it) }
    }
}

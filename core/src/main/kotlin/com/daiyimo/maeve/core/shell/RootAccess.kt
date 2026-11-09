package com.daiyimo.maeve.core.shell

/**
 * 已 root 手机上 su 给出的四种答复。[Granted] 是答应了、并且确实切换到了要求的身份；
 * [Denied] 是被拒（或 su 没跑命令就退出）；[NoAnswer] 是等到超时仍无回音（授权框没人点，或 root 管理器卡住）；
 * [WrongUid] 是答应了却切不到要求的身份（这类 su 不支持指定用户）。
 */
enum class SuAnswer { Granted, Denied, NoAnswer, WrongUid }

/** 找 su 的位置、解读它的答复：只看能力（文件在不在、跑出来的 uid 是多少），不关心来自哪家 root 方案。 */
object SuProbe {
    /** su 常出现的目录：系统 PATH 里的几处，加上各家 root 方案惯放的几处。PATH 里额外出现的也会一并查找。 */
    val DIRS = listOf("/system/bin", "/system/xbin", "/sbin", "/su/bin", "/system/sbin", "/vendor/bin", "/odm/bin", "/debug_ramdisk")

    private val UID_PATTERN = Regex("""uid=(\d+)""")

    /** 第一个真实存在的 su 完整路径；一个都没有时为 null。[path] 是 PATH，[exists] 判断文件在不在（测试可替换）。 */
    fun find(path: String?, exists: (String) -> Boolean): String? {
        val pathDirs = path.orEmpty().split(':')
            .map { it.trim().trimEnd('/') }
            .filter { it.startsWith("/") }
        return (pathDirs + DIRS).distinct()
            .map { "$it/su" }
            .firstOrNull(exists)
    }

    /** 从 `id` / `id -u` 的输出里读出 uid；读不出为 null。su 有时会先打印几行提示，所以逐行去看。 */
    fun uid(output: String): Int? {
        UID_PATTERN.find(output)?.let { return it.groupValues[1].toIntOrNull() }
        return output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toIntOrNull()
    }

    /**
     * 跑过 `su … -c id` 之类命令后的结论。[output] 是 su 的输出（没输出就退出时是空串）；
     * [timedOut] 表示到时限仍没回音；[expectUid] 是期望的身份。uid 不符算 [SuAnswer.WrongUid]，读不到 uid 算拒绝。
     */
    fun answer(output: String, timedOut: Boolean, expectUid: Int): SuAnswer {
        val actualUid = uid(output)
        return when {
            actualUid == expectUid -> SuAnswer.Granted
            actualUid != null -> SuAnswer.WrongUid
            timedOut -> SuAnswer.NoAnswer
            else -> SuAnswer.Denied
        }
    }

    /** 以 root 身份跑一条命令：`su -c <命令>`。命令作为单独一个参数交给 sh，不会被各家 su 再拆开。 */
    fun rootArgs(su: String, command: String): List<String> = listOf(su, "-c", command)

    /**
     * 以 shell 身份（uid 2000）跑一条命令：`su 2000 -c <命令>`。用户参数放在选项之前，
     * 因为有的 su 会把 -c 后面的参数全当作命令。切不过去的 su 由跑出来的 uid 判断（见 [answer]）。
     */
    fun shellArgs(su: String, command: String): List<String> = listOf(su, HelperHandoff.SHELL_UID.toString(), "-c", command)
}

/**
 * 「自动用 root 启动手机助手」此刻能不能用。[Off] 是用户没请求、或还没有明确的授权结果；
 * [Ready] 是请求过、su 在、且 su 已确认能切到 shell 身份。其余几种是授权后用不了，各自对应用户看到的一句话。
 * [Gone] 是 su 没了；[Denied] 被拒；[NoAnswer] 没回音；[NoShellUid] 切不过去。
 * 后三种都要用户点重试才会再问 su，避免反复弹授权框。
 */
enum class RootStart {
    Off, Ready, Gone, Denied, NoAnswer, NoShellUid;

    /** 打开却用不了时状态里报的失败；[Off]、[Ready] 为 null。 */
    val failure: BuiltinShellFailure?
        get() = when (this) {
            Off, Ready -> null
            Gone -> BuiltinShellFailure.RootUnavailable
            Denied -> BuiltinShellFailure.RootDenied
            NoAnswer -> BuiltinShellFailure.RootNoAnswer
            NoShellUid -> BuiltinShellFailure.RootNoShellUid
        }

    companion object {
        /** [enabled] 是用户是否请求过；[suPresent] 是找不找得到 su；[last] 是 su 明确给过的答复。 */
        fun of(enabled: Boolean, suPresent: Boolean, last: SuAnswer?): RootStart = when {
            !enabled || last == null -> Off
            !suPresent -> Gone
            else -> when (last) {
                SuAnswer.Denied -> Denied
                SuAnswer.NoAnswer -> NoAnswer
                SuAnswer.WrongUid -> NoShellUid
                else -> Ready
            }
        }
    }
}

/** 设置页 root 那一项的样子（纯状态机）：没有 su 就整项不显示；问 su 时 [Pending]，问完落到答复对应的状态。 */
enum class RootGrant {
    Unknown, Pending, Granted, Denied, NoAnswer, NoShellUid;

    companion object {
        fun of(answer: SuAnswer?): RootGrant = when (answer) {
            null -> Unknown
            SuAnswer.Granted -> Granted
            SuAnswer.Denied -> Denied
            SuAnswer.NoAnswer -> NoAnswer
            SuAnswer.WrongUid -> NoShellUid
        }
    }
}

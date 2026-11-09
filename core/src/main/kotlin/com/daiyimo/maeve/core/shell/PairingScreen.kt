package com.daiyimo.maeve.core.shell

/** 从系统配对窗口上读到的码与端口；同一种出现两个不同值、或有歧义时为 null。 */
data class PairingScreenReading(val code: String?, val port: Int?)

/** 拿读到的码去配对；[port] 为空时用已找到的配对端口。 */
data class PairingAttempt(val code: String, val port: Int?)

/**
 * 只从窗口里的一堆文字认配对码和「地址:端口」，不依赖控件 id、界面语言或具体布局：
 * 配对码是整段文字去掉空白后恰好 6 位数字；端口取 IPv4 或方括号 IPv6 地址里冒号后的数字。
 * 同一窗口里若出现两个不同的码（或两个不同的端口），宁可当作没读到，让用户手动填。
 */
object PairingScreen {
    private val CODE = Regex("""\d{6}""")
    private val IPV4_PORT = Regex("""(?<![\w.:])(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3}):(\d{1,5})(?![\w.:])""")
    private val IPV6_PORT = Regex("""\[[0-9A-Fa-f]*:[0-9A-Fa-f:.]*(?:%[\w.]+)?]:(\d{1,5})(?![\w.:])""")
    private val OCTETS = 1..4
    private val VALID_PORT = 1..65535

    fun read(texts: List<String>): PairingScreenReading {
        val codes = texts.mapNotNull(::codeIn).toSet()
        val ports = texts.flatMap(::portsIn).toSet()
        return PairingScreenReading(code = codes.singleOrNull(), port = ports.singleOrNull())
    }

    private fun codeIn(text: String): String? =
        text.filterNot(Char::isWhitespace).takeIf(CODE::matches)

    private fun portsIn(text: String): List<Int> {
        val fromV4 = IPV4_PORT.findAll(text)
            .filter { match -> OCTETS.all { match.groupValues[it].toInt() <= 255 } }
            .map { it.groupValues[5] }
        val fromV6 = IPV6_PORT.findAll(text).map { it.groupValues[1] }
        return (fromV4 + fromV6).mapNotNull { it.toIntOrNull()?.takeIf { value -> value in VALID_PORT } }.toList()
    }

    /**
     * 此刻该不该拿读到的码去试：要有码、有端口（屏上读到或已找到配对窗口），这个码没试过，
     * 并且状态还停在等配对（不在配对中、也没有配对完成）。同一个错码不会重复试，等窗口换出新码。
     */
    fun attempt(reading: PairingScreenReading, portFound: Boolean, status: BuiltinShellStatus, tried: Set<String>): PairingAttempt? {
        val code = reading.code ?: return null
        if (code in tried) return null
        if (reading.port == null && !portFound) return null
        if (!awaitingPairing(status)) return null
        return PairingAttempt(code, reading.port)
    }

    private fun awaitingPairing(status: BuiltinShellStatus): Boolean = when (status) {
        BuiltinShellStatus.NotPaired, BuiltinShellStatus.WirelessDebuggingOff -> true
        is BuiltinShellStatus.Failed -> status.reason.duringPairing || status.fix == BuiltinShellFix.RePair
        BuiltinShellStatus.Connecting, BuiltinShellStatus.Running -> false
    }
}

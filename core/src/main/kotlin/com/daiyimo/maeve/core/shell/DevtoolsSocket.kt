package com.daiyimo.maeve.core.shell

/**
 * 内置助手代连 WebView 调试接口时认的本地套接字名：每个开着调试的 WebView 进程各有一个，
 * 形如 `webview_devtools_remote_<pid>`。只有这个名字形状、且 pid 确实属于 Maeve 的 uid，才允许连。
 */
object DevtoolsSocket {
    private const val NAME_PREFIX = "webview_devtools_remote_"
    private val PID_FORM = Regex("""webview_devtools_remote_([1-9][0-9]{0,9})""")
    private val SPACES = Regex("\\s+")

    fun name(pid: Int): String = NAME_PREFIX + pid

    /** 从套接字名里取 pid；前后多字、有前导 0、数字越界都当作不认识。 */
    fun pidOf(name: String): Int? {
        val digits = PID_FORM.matchEntire(name)?.groupValues?.getOrNull(1) ?: return null
        return digits.toIntOrNull()?.takeIf { it > 0 }
    }

    /** 解析 `/proc/<pid>/status` 的 Uid 行第一列（进程真实 uid）；没有这一行就是 null。 */
    fun procUid(status: String): Int? {
        val uidRow = status.lineSequence().firstOrNull { it.startsWith("Uid:") } ?: return null
        return uidRow.removePrefix("Uid:").trim().split(SPACES).firstOrNull()?.toIntOrNull()
    }
}

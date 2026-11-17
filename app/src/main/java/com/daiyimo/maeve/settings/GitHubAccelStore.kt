package com.daiyimo.maeve.settings

import android.content.Context
import com.daiyimo.maeve.core.net.GitHubDns
import com.daiyimo.maeve.core.net.GitHubDnsMode

/**
 * 「GitHub 加速」(获取更新页里配):检查更新与下载安装问 GitHub 时,域名怎么解析。
 *
 * 不是秘密,存普通偏好;默认 [GitHubDnsMode.System](什么都不改)。
 * 改了只影响之后新建的连接——正在下的那个包不受影响,下次下载才走新设置。
 */
class GitHubAccelStore(context: Context) {
    private val prefs = context.getSharedPreferences("github-accel", Context.MODE_PRIVATE)

    fun mode(): GitHubDnsMode =
        GitHubDnsMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: GitHubDnsMode.System

    /** 自定义 IP(hosts 式);只有 [GitHubDnsMode.CustomIp] 用得上。 */
    fun customIp(): String = prefs.getString(KEY_IP, "").orEmpty().trim()

    fun set(mode: GitHubDnsMode, ip: String) {
        prefs.edit().putString(KEY_MODE, mode.name).putString(KEY_IP, ip.trim()).commit()
    }

    /** 给 OkHttp 的 [com.daiyimo.maeve.core.net.GitHubDns];模式与 IP 都是现读,改了下次建连接就生效。 */
    fun dns(): GitHubDns = GitHubDns({ mode() }, { customIp() })

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_IP = "ip"
    }
}

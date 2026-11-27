package com.daiyimo.maeve.cap.phone.shell

import android.os.Build

// DisplayManager.VIRTUAL_DISPLAY_FLAG_*(部分为隐藏常量)
private const val FLAG_PUBLIC = 1 shl 0
private const val FLAG_OWN_CONTENT_ONLY = 1 shl 3
private const val FLAG_SUPPORTS_TOUCH = 1 shl 6
private const val FLAG_ROTATES_WITH_CONTENT = 1 shl 7
private const val FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
private const val FLAG_TRUSTED = 1 shl 10
internal const val FLAG_OWN_DISPLAY_GROUP = 1 shl 11
private const val FLAG_ALWAYS_UNLOCKED = 1 shl 12
private const val FLAG_TOUCH_FEEDBACK_DISABLED = 1 shl 13
private const val FLAG_OWN_FOCUS = 1 shl 14
private const val FLAG_STEAL_TOP_FOCUS_DISABLED = 1 shl 16

/**
 * 建虚拟屏用的标志。[trusted] 为 false 时去掉依赖可信屏的那几项:有的系统里 shell 没有
 * ADD_TRUSTED_DISPLAY,带上 TRUSTED 会被系统以 SecurityException 拒绝;OWN_DISPLAY_GROUP、ALWAYS_UNLOCKED
 * 和 OWN_FOCUS/STEAL_TOP_FOCUS_DISABLED 同样只对可信屏可用。其余几项与可信与否无关,照旧。
 */
internal fun shellDisplayFlags(sdk: Int, trusted: Boolean): Int {
    var flags = FLAG_PUBLIC or FLAG_OWN_CONTENT_ONLY or FLAG_SUPPORTS_TOUCH or
        FLAG_ROTATES_WITH_CONTENT or FLAG_DESTROY_CONTENT_ON_REMOVAL
    if (sdk >= Build.VERSION_CODES.TIRAMISU) {
        flags = flags or FLAG_TOUCH_FEEDBACK_DISABLED
        if (trusted) flags = flags or FLAG_TRUSTED or FLAG_OWN_DISPLAY_GROUP or FLAG_ALWAYS_UNLOCKED
    }
    // 只有 OWN_FOCUS 时,虚拟屏上启动 App 或 App 自己开新页面都会让它成为「最上层焦点屏」,
    // 主屏的焦点窗口随之清空;STEAL_TOP_FOCUS_DISABLED(需同时有 OWN_FOCUS、TRUSTED)让它不抢主屏的焦点
    if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && trusted) {
        flags = flags or FLAG_OWN_FOCUS or FLAG_STEAL_TOP_FOCUS_DISABLED
    }
    return flags
}

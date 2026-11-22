package com.daiyimo.maeve.core.uistate

/**
 * 只按前台包名决定读屏方式:命中时不再读取无障碍树,只用截图、本机 OCR 与视觉模型。
 * 这里放包名而不放页面判断,避免为了识别页面再触发一次树读取。
 */
object VisualFirstPolicy {
    private val packages: Set<String> = setOf(
        "com.xingin.xhs",
        "com.tencent.mm",
    )

    fun applies(packageName: String?): Boolean = packages.contains(packageName?.trim()?.lowercase())
}

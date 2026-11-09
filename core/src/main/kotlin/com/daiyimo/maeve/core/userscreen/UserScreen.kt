package com.daiyimo.maeve.core.userscreen

/** 屏上的一行字：[x]/[y] 是它的中心，与点按共用同一套 0–999 坐标（左上为 0,0）。 */
data class ScreenLine(val text: String, val x: Int, val y: Int)

/**
 * 用户发来的「此刻的屏幕」：双击胶囊或点卡片里的截屏按钮时，用户主屏上正看着的那一屏。
 *
 * 它与从相册、文件里挑的图片不是一回事——它表达的是「我现在在看这个」，
 * 因此带着前台 App、截下的时刻和屏上的字。
 *
 * @property packageName 前台 App 包名，读不到为 null
 * @property appLabel 前台 App 的显示名，读不到为 null
 * @property blocked 页面不允许截图（防截屏）时为真：没有画面，[lines] 也为空
 * @property lines 屏上的字，取自无障碍树，树不够用时补上 OCR 认出的字
 * @property fromOcr [lines] 里含有 OCR 认出的字
 */
data class UserScreen(
    val id: String,
    val takenAt: Long,
    val packageName: String?,
    val appLabel: String?,
    val blocked: Boolean = false,
    val lines: List<ScreenLine> = emptyList(),
    val fromOcr: Boolean = false,
) {
    /** 怎么称呼那个 App：有显示名用显示名，否则用包名，都没有时用占位说法。 */
    val app: String
        get() {
            val label = appLabel
            if (!label.isNullOrBlank()) return label
            return packageName ?: UNKNOWN_APP
        }

    companion object {
        const val UNKNOWN_APP = "某个 App"
    }
}

/** 按编号取这个对话里用户发来的屏幕；说明和截图都可能已经不在，读不到返回 null。 */
interface UserScreenSource {
    suspend fun screen(id: String): UserScreen?

    /** 截下的画面（JPEG 或 PNG）；页面不允许截图或读不到时为 null。 */
    suspend fun image(id: String): ByteArray?
}

/**
 * 截下来、还没发出去的一屏。
 *
 * @property jpeg 画面数据，页面不允许截图时为 null
 * @property screen 屏幕说明；编号在存进对话时才确定
 * @property sourcePackages 这一屏涉及的前台包名
 */
class CapturedScreen(
    val jpeg: ByteArray?,
    val screen: UserScreen,
    val sourcePackages: Set<String> = setOfNotNull(screen.packageName),
)

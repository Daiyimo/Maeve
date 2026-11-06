package com.daiyimo.maeve.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import com.daiyimo.maeve.R

/**
 * 桌面图标的四套变体:雾紫(默认)、珠白、夜色、单色。
 * 名字和启动图标的渐变一一对应(见 res/mipmap-anydpi-v26/)。
 *
 * 实现是 manifest 里的四个 activity-alias,同一时刻只有一个 enabled——
 * 桌面因此永远只有一个入口,图标却可以整套换。
 */
enum class LauncherIcon(val id: String, @StringRes val labelRes: Int, val bgTop: Long, val bgBottom: Long) {
    Reverie("reverie", R.string.icon_reverie, 0xFF6E6296, 0xFF332B4A),
    Pearl("pearl", R.string.icon_pearl, 0xFF54687A, 0xFF232D36),
    Night("night", R.string.icon_night, 0xFF221E2E, 0xFF0B0A10),
    Mono("mono", R.string.icon_mono, 0xFF15151B, 0xFF0E0E12),
    ;

    /** alias 的类名后缀(manifest 里 .ui.MainActivityAlias* 的相对名)。 */
    val aliasSuffix: String get() = ".ui.MainActivityAlias$name"

    companion object {
        fun byId(id: String?): LauncherIcon = entries.firstOrNull { it.id == id } ?: Reverie
    }
}

/**
 * 桌面图标的切换:存偏好 + 翻组件的启用状态。
 *
 * 顺序铁律:**先启新的,再关旧的**——反过来中间会有一瞬桌面没有入口,
 * 部分桌面会在这时候把图标从桌面布局里摘掉。
 */
object LauncherIconController {

    private const val PREFS = "maeve_launcher_icon"
    private const val KEY = "icon"

    /** 当前选的变体(没选过就是雾紫)。 */
    fun current(context: Context): LauncherIcon =
        LauncherIcon.byId(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    /** 换变体并立刻生效;桌面图标下一秒(或重启桌面后)换成新的。 */
    fun apply(context: Context, icon: LauncherIcon) {
        val pm = context.packageManager
        val pkg = context.packageName
        // 1. 先打开新的这一个
        pm.setComponentEnabledSetting(
            ComponentName(pkg, pkg + icon.aliasSuffix),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        // 2. 再关掉其余的
        LauncherIcon.entries.asSequence().filter { it != icon }.forEach { other ->
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + other.aliasSuffix),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, icon.id).apply()
    }

    /**
     * 开机/升级后对齐一次:manifest 里默认开着雾紫,偏好里存的却是别的
     * (覆盖安装不重跑 alias 的默认值时就会这样)。不一致就按偏好翻过来。
     */
    fun sync(context: Context) {
        val want = current(context)
        val pm = context.packageManager
        val pkg = context.packageName
        val enabledNow = LauncherIcon.entries.filter {
            pm.getComponentEnabledSetting(ComponentName(pkg, pkg + it.aliasSuffix)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        if (enabledNow.singleOrNull() != want) apply(context, want)
    }
}

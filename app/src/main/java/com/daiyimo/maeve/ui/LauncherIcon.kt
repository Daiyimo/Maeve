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
    Reverie("reverie", R.string.icon_reverie, 0xFF6B3D57, 0xFF38202F),
    Pearl("pearl", R.string.icon_pearl, 0xFF55646E, 0xFF25303A),
    Night("night", R.string.icon_night, 0xFF221C1E, 0xFF0C0908),
    Mono("mono", R.string.icon_mono, 0xFF16120F, 0xFF0E0B09),
    ;

    companion object {
        fun byId(id: String?): LauncherIcon = entries.firstOrNull { it.id == id } ?: Reverie
    }
}

/**
 * alias 的类名前缀。
 *
 * manifest 里写的是相对名 `.ui.MainActivityAlias*`,合并时按**原始 manifest package**
 * (com.daiyimo.maeve)展开成绝对名——所以组件类名不随 applicationId 变:
 * -PdevInstall 装上的是 com.daiyimo.maeve.dev,但 alias 仍叫
 * com.daiyimo.maeve.ui.MainActivityAliasReverie。拿 packageName 去拼会拼出
 * 不存在的组件(debug 构建在 Application.onCreate 就崩),必须按这里的前缀解析。
 */
private const val ALIAS_CLASS_PREFIX = "com.daiyimo.maeve.ui.MainActivityAlias"

/**
 * 四套变体的真实组件名。
 *
 * 首选从 PackageManager 查(带 MATCH_DISABLED_COMPONENTS,被关掉的 alias 也要能查到),
 * 查询异常时退回常量拼接——合并后的 manifest 就是这个名字。
 */
private fun resolveAliases(context: Context): Map<LauncherIcon, ComponentName> {
    val pkg = context.packageName
    val found = mutableMapOf<LauncherIcon, ComponentName>()
    try {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        context.packageManager.getPackageInfo(pkg, flags).activities?.forEach { ai ->
            val icon = LauncherIcon.entries.firstOrNull { ai.name == "$ALIAS_CLASS_PREFIX${it.name}" }
            if (icon != null) found[icon] = ComponentName(pkg, ai.name)
        }
    } catch (_: Exception) {
        // 查询失败:下面用常量兜底
    }
    return LauncherIcon.entries.associateWith { found[it] ?: ComponentName(pkg, "$ALIAS_CLASS_PREFIX${it.name}") }
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
        val aliases = resolveAliases(context)
        // 1. 先打开新的这一个
        aliases[icon]?.let { cn ->
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        // 2. 再关掉其余的
        LauncherIcon.entries.asSequence().filter { it != icon }.forEach { other ->
            aliases[other]?.let { cn ->
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, icon.id).apply()
    }

    /**
     * 开机/升级后对齐一次:manifest 里默认开着雾紫,偏好里存的却是别的
     * (覆盖安装不重跑 alias 的默认值时就会这样)。不一致就按偏好翻过来。
     */
    fun sync(context: Context) {
        val want = current(context)
        val aliases = resolveAliases(context)
        val pm = context.packageManager
        val enabledNow = LauncherIcon.entries.filter {
            aliases[it]?.let { cn -> pm.getComponentEnabledSetting(cn) } == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        if (enabledNow.singleOrNull() != want) apply(context, want)
    }
}

package com.daiyimo.maeve.ui

import android.content.Intent
import com.daiyimo.maeve.MaeveApp

/** 只管理已有 AppAccessStore;分类、权限与读屏模式互不混用。 */
internal class FlutterAppAccessSettings(private val app: MaeveApp) {
    fun snapshot(): Map<String, Any> {
        val state = app.appAccess.current()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val labels = runCatching {
            app.packageManager.queryIntentActivities(launch, 0).associate {
                it.activityInfo.packageName to it.loadLabel(app.packageManager).toString()
            }
        }.getOrElse { emptyMap() }
        val packages = (labels.keys + state.blocked + state.shareAllowed + state.visualFirstOverrides.keys).distinctBy { it.trim().lowercase() }
        return mapOf("available" to state.available, "revision" to state.revision,
            "visualFirstDefault" to state.visualFirstDefault,
            "apps" to packages.sortedWith(compareBy({ labels[it] ?: it }, { it })).map { pkg ->
                mapOf("packageName" to pkg, "label" to (labels[pkg] ?: pkg),
                    "blocked" to (pkg in state.blocked), "shareAllowed" to (pkg in state.shareAllowed),
                    "protected" to (pkg == app.packageName),
                    "policySource" to if (pkg in state.blocked || pkg in state.shareAllowed) "用户设置" else "默认允许",
                    "visualFirstOverride" to state.visualFirstOverrides[pkg.trim().lowercase()],
                    "visualFirst" to state.visualFirst(pkg))
            })
    }

    fun change(action: String, pkg: String?, enabled: Boolean?): Map<String, Any> {
        if (action == "reload") { app.appAccess.reload(); return snapshot() }
        if (action == "setVisualFirstDefault") {
            require(pkg == null)
            app.appAccess.setVisualFirstDefault(requireNotNull(enabled))
            return snapshot()
        }
        if (action in setOf("setVisualFirstOverride", "clearVisualFirstOverride")) {
            val target = requirePackage(pkg)
            app.appAccess.setVisualFirstOverride(target, if (action == "setVisualFirstOverride") requireNotNull(enabled) else null)
            return snapshot()
        }
        if (action != "snapshot") {
            val target = requirePackage(pkg)
            val on = requireNotNull(enabled)
            when (action) {
                "setBlocked" -> app.appAccess.setBlocked(target, on)
                "setShareAllowed" -> app.appAccess.setShareAllowed(target, on)
                else -> error("unsupported")
            }
        }
        return snapshot()
    }

    private fun requirePackage(pkg: String?): String = requireAppAccessPackage(pkg, app.packageName)
}

/** 包名先归一化再校验，避免大小写变体绕过 Maeve 自身保护。 */
internal fun requireAppAccessPackage(pkg: String?, ownPackage: String): String {
    val target = requireNotNull(pkg).trim().lowercase()
    require(target.matches(Regex("[a-z0-9_]+(?:\\.[a-z0-9_]+)+")) && target != ownPackage.trim().lowercase())
    return target
}

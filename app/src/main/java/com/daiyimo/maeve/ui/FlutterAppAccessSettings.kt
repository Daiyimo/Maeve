package com.daiyimo.maeve.ui

import android.content.Intent
import com.daiyimo.maeve.MaeveApp

/** 只管理已有 AppAccessStore；分类不授予权限，不创建第二份策略。 */
internal class FlutterAppAccessSettings(private val app: MaeveApp) {
    fun snapshot(): Map<String, Any> {
        val state = app.appAccess.current()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val labels = app.packageManager.queryIntentActivities(launch, 0).associate {
            it.activityInfo.packageName to it.loadLabel(app.packageManager).toString()
        }
        val packages = labels.keys + state.blocked + state.shareAllowed
        return mapOf("available" to state.available, "revision" to state.revision,
            "apps" to packages.sortedWith(compareBy({ labels[it] ?: it }, { it })).map { pkg ->
                mapOf("packageName" to pkg, "label" to (labels[pkg] ?: pkg),
                    "blocked" to (pkg in state.blocked), "shareAllowed" to (pkg in state.shareAllowed),
                    "protected" to (pkg == app.packageName),
                    "policySource" to if (pkg in state.blocked || pkg in state.shareAllowed) "用户设置" else "默认允许")
            })
    }

    fun change(action: String, pkg: String?, enabled: Boolean?): Map<String, Any> {
        if (action == "reload") { app.appAccess.reload(); return snapshot() }
        if (action != "snapshot") {
            val target = requireNotNull(pkg)
            require(target.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) && target != app.packageName)
            val on = requireNotNull(enabled)
            when (action) {
                "setBlocked" -> app.appAccess.setBlocked(target, on)
                "setShareAllowed" -> app.appAccess.setShareAllowed(target, on)
                else -> error("unsupported")
            }
        }
        return snapshot()
    }
}

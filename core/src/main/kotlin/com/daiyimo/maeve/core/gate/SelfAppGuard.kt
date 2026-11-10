package com.daiyimo.maeve.core.gate

import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 「不对 Maeve 自己动手」这道门:写操作作用在 Maeve 自己身上时一律不做
 * (不自己给自己点同意、改设置)。先于一切放行之前查——自主模式的提前返回也拦不住它
 * (见 com.daiyimo.maeve.core.action.ConfirmDecision.judge)。
 */
object SelfAppGuard {
    /**
     * 这一步是不是作用在 Maeve 自己身上:屏上是 Maeve,或要打开 Maeve(含它的应用详情页)。
     * [self] 为 null(不知道自己的包名)时这道门不生效;[writesScreen] 为 false(只读、界面间移动)时不判。
     */
    fun targetsSelf(self: String?, writesScreen: Boolean, args: JsonObject, screen: ScreenSnapshot?): Boolean {
        if (self == null || !writesScreen) return false
        if (screen?.packageName == self) return true
        return listOf("package", "uri").any { k -> (args[k] as? JsonPrimitive)?.contentOrNull?.let { self in it } == true }
    }
}

package com.daiyimo.maeve.core.gate

import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「不对 Maeve 自己动手」这道门:任何模式下(含自主)写操作都不作用在 Maeve 自己身上。 */
class SelfAppGuardTest {
    private fun screen(pkg: String?) = ScreenSnapshot("s1", pkg, emptyList(), 1080, 2400)
    private fun args(vararg kv: Pair<String, String>) = buildJsonObject { kv.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }

    @Test fun selfScreenIsGuardedEvenForPlainWrites() {
        assertTrue(SelfAppGuard.targetsSelf("com.maeve", writesScreen = true, args(), screen("com.maeve")))
    }

    @Test fun openingSelfOrItsDetailsPageIsGuarded() {
        assertTrue(SelfAppGuard.targetsSelf("com.maeve", writesScreen = true,
            args("package" to "com.maeve"), screen("com.chat")))
        assertTrue(SelfAppGuard.targetsSelf("com.maeve", writesScreen = true,
            args("uri" to "package:com.maeve"), screen("com.chat")))
    }

    @Test fun otherAppsAndReadsAreNotGuarded() {
        assertFalse(SelfAppGuard.targetsSelf("com.maeve", writesScreen = true, args(), screen("com.chat")))
        assertFalse(SelfAppGuard.targetsSelf("com.maeve", writesScreen = true,
            args("package" to "com.other"), screen("com.chat")))
        // 只读、界面间移动不判
        assertFalse(SelfAppGuard.targetsSelf("com.maeve", writesScreen = false, args(), screen("com.maeve")))
        // 不知道自己的包名时这道门不生效
        assertFalse(SelfAppGuard.targetsSelf(null, writesScreen = true, args(), screen("com.maeve")))
        // 子串匹配是故意的(宁可多拦):名字里带上自己包名的也拦
        assertTrue(SelfAppGuard.targetsSelf("com.maeve", writesScreen = true,
            args("package" to "com.maeve.theme"), screen("com.chat")))
    }
}

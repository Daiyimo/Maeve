package com.daiyimo.maeve.wechat

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AmooExecutionPolicyTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun switchingBothWaysPublishesPermissionWithoutRestartingTheLegacyWorker() {
        for (enabled in listOf(false, true, false, true)) {
            AmooExecutionPolicy.write(tmp.root, enabled)
            fun read(path: String) = Json.parseToJsonElement(File(tmp.root, path).readText()).jsonObject
            assertEquals(enabled, read("execution-policy.json")["allowMaeve"]!!.jsonPrimitive.boolean)
            val legacy = read("wechat/control.json")
            assertFalse(legacy["enabled"]!!.jsonPrimitive.boolean)
            assertEquals(enabled, legacy["allowMaeve"]!!.jsonPrimitive.boolean)
        }
    }

    @Test fun eachTurnIncludesLiteralPermissionAndTheCurrentPackageContract() {
        val enabled = systemAmooMessage("打开设置", "com.example.maeve", true)
        assertTrue(enabled.contains("\"allowMaeve\":true"))
        assertTrue(enabled.contains("/sdcard/Android/media/com.example.maeve/amoo/in/"))
        assertTrue(enabled.contains("execution-policy.json"))
        assertTrue(enabled.contains("普通聊天、问候、写作和云端查询由 Amoo 自行回答"))
        val disabled = systemAmooMessage("你好", "com.example.maeve", false)
        assertTrue(disabled.contains("\"allowMaeve\":false"))
        assertTrue(disabled.contains("当前禁止调用 Maeve 文件桥"))
        assertFalse(disabled.contains("in/<唯一编号>"))
    }
}

package com.daiyimo.maeve.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daiyimo.maeve.chat.ChatDraftStore
import com.daiyimo.maeve.core.action.ActionEffectStore
import com.daiyimo.maeve.core.action.ActionEffects
import com.daiyimo.maeve.core.action.Origin
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.ToolExecution
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只在独立缓存目录验证 Android 的原子替换与重启恢复，不触碰用户会话、模型或系统设置。 */
@RunWith(AndroidJUnit4::class)
class ReceiptStorageDeviceTest {
    @Test fun privateReceiptsPersistAndStaleDraftWritersAreRejected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "receipt-storage-test-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val draftsFile = File(root, "drafts.json")
            val source = """{"schemaVersion":1,"generation":1,"activeKey":"new:1","aliases":{},"drafts":{"new:1":{"text":"设备测试草稿","quote":null}},"pending":{}}"""
            val drafts = ChatDraftStore(draftsFile)
            assertEquals(1L, drafts.save(source, expectedRevision = 0))
            assertEquals(source, ChatDraftStore(draftsFile).load())
            assertEquals(2L, drafts.save(source, expectedRevision = 1))
            assertTrue(runCatching { drafts.save(source, expectedRevision = 1) }.isFailure)

            val effectsDir = File(root, "effects")
            val store = ActionEffectStore(effectsDir)
            val call = Part.ToolCall("test-only-call", "test_only_action", "{}")
            var calls = 0
            val first = runCatching {
                ActionEffects(store, "test-scope", "test-run").execute(call, Origin.Model(1)) {
                    calls++; ToolExecution(buildJsonObject { put("ok", false) })
                }
            }.exceptionOrNull()
            assertEquals("effect_unknown", (first as ActionEffects.Blocked).code)
            val restarted = ActionEffects(ActionEffectStore(effectsDir), "test-scope", "new-test-run")
            assertTrue(runCatching { restarted.execute(call.copy(id = "different"), Origin.Model(2)) { calls++; error("must not run") } }.exceptionOrNull() is ActionEffects.Blocked)
            assertEquals(1, calls)
            assertEquals(ActionEffectStore.State.Unknown, store.receipts("test-scope").single().state)
        } finally { root.deleteRecursively() }
    }
}

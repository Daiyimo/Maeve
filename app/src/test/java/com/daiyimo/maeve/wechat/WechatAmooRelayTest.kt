package com.daiyimo.maeve.wechat

import com.daiyimo.maeve.core.run.Delivery
import com.daiyimo.maeve.core.run.SendOutcome
import com.daiyimo.maeve.core.wechat.WechatAccount
import com.daiyimo.maeve.settings.KeyCipher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WechatAmooRelayTest {
    @get:Rule val tmp = TemporaryFolder()
    private val cipher = object : KeyCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(data: ByteArray) = data
    }
    private fun store() = WechatStore(tmp.newFolder(), cipher).apply {
        saveAccount(WechatAccount("private-token", "https://example.test", "bot", "owner"))
        setEnabled(true)
    }

    @Test fun replayKeepsOneRequestWithoutLeakingCredentials() {
        val dir = tmp.newFolder()
        val store = store()
        val relay = WechatAmooRelay({ dir }, store) { false }
        val peer = store.peer("owner")
        relay.syncControl()
        relay.syncControl()
        relay.forward(peer, "1", "你好 Amoo")
        val file = File(dir, "requests/${WechatAmooRelay.requestId(peer, "1")}.json")
        val original = file.readText()
        relay.forward(peer, "1", "不应覆盖原文")
        assertEquals(original, file.readText())
        assertFalse(dir.walkTopDown().filter { it.isFile }.any { "private-token" in it.readText() })
        val control = Json.parseToJsonElement(File(dir, "control.json").readText()).jsonObject
        assertEquals(false, control["allowMaeve"]?.jsonPrimitive?.boolean)
        assertEquals(peer.threadId, control["session"]?.jsonPrimitive?.content)
    }

    @Test fun onlyDeliveredReplyAcknowledgesTheMessage() = runTest {
        val dir = tmp.newFolder()
        val store = store()
        val relay = WechatAmooRelay({ dir }, store) { true }
        val peer = store.peer("owner")
        store.journal("1", peer, "问题")
        relay.forward(peer, "1", "问题")
        val id = WechatAmooRelay.requestId(peer, "1")
        val reply = File(dir, "replies/$id.json")
        WechatAmooRelay.atomic(reply, buildJsonObject {
            put("id", id); put("session", peer.threadId); put("text", "Amoo 的回答")
        }.toString())
        assertTrue(relay.deliver { _, _ -> SendOutcome.Uncertain })
        assertEquals(1, store.pending().size)
        relay.deliver { _, delivery ->
            assertEquals("Amoo 的回答", (delivery as Delivery.Result).text)
            SendOutcome.Delivered
        }
        assertTrue(store.pending().isEmpty())
        assertFalse(reply.exists())
        assertFalse(store.journal("1", peer, "重复推送"))
        // 收件长轮询可能持有确认前的 pending 快照，不能在回发后重新建立请求。
        relay.forward(peer, "1", "长轮询补交旧快照")
        assertFalse(File(dir, "requests/$id.json").exists())
    }

    @Test fun wrongSessionAndDisabledReceptionCannotDeliver() = runTest {
        val dir = tmp.newFolder()
        val store = store()
        val relay = WechatAmooRelay({ dir }, store) { false }
        val peer = store.peer("owner")
        store.journal("1", peer, "问题")
        val id = WechatAmooRelay.requestId(peer, "1")
        relay.forward(peer, "1", "问题")
        WechatAmooRelay.atomic(File(dir, "replies/$id.json"), buildJsonObject {
            put("id", id); put("session", "old-session"); put("text", "旧回复")
        }.toString())
        relay.deliver { _, _ -> fail("Wrong session delivered"); SendOutcome.Delivered }
        store.setEnabled(false)
        relay.deliver { _, _ -> fail("Disabled channel delivered"); SendOutcome.Delivered }
        store.clear()
        relay.syncControl()
        assertFalse(Json.parseToJsonElement(File(dir, "control.json").readText()).jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertTrue(store.pending().isEmpty())
        relay.clearSession(peer.threadId)
        assertFalse(File(dir, "requests/$id.json").exists())
    }

}

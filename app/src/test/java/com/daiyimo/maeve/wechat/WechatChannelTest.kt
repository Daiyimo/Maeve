package com.daiyimo.maeve.wechat

import com.daiyimo.maeve.core.run.ChannelInput
import com.daiyimo.maeve.core.run.ChannelInputSink
import com.daiyimo.maeve.core.run.Delivery
import com.daiyimo.maeve.core.run.SendOutcome
import com.daiyimo.maeve.core.wechat.WechatAccount
import com.daiyimo.maeve.core.wechat.WechatApi
import com.daiyimo.maeve.settings.KeyCipher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress

class WechatChannelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val cipher = object : KeyCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(data: ByteArray) = data
    }

    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun wechatTextTravelsThroughAmooMailboxAndReturnsToWechatOnce() = runTest {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val store = WechatStore(tmp.newFolder(), cipher)
            store.saveAccount(WechatAccount("private-token", server.url("/").toString(), "bot", "owner"))
            store.setEnabled(true)
            val dir = tmp.newFolder()
            val relay = WechatAmooRelay({ dir }, store) { false }
            val channel = WechatChannel(store, WechatApi(OkHttpClient(), "test"), log = {})
            channel.start(ChannelInputSink { input ->
                if (input is ChannelInput.Text) relay.forward(input.peer, input.messageId, input.text)
            })
            val updates = """{"get_updates_buf":"next","msgs":[{"message_id":77,"from_user_id":"owner","message_type":1,"context_token":"ctx","item_list":[{"type":1,"text_item":{"text":"你好 Amoo"}}]}]}"""
            server.enqueue(response(updates))
            channel.pollOnce()
            server.takeRequest()
            val peer = store.pending().single().peer
            val id = WechatAmooRelay.requestId(peer, "77")
            val request = java.io.File(dir, "requests/$id.json")
            assertTrue(request.readText().contains("你好 Amoo"))
            WechatAmooRelay.atomic(java.io.File(dir, "replies/$id.json"),
                """{"id":"$id","session":"${peer.threadId}","text":"我是 Amoo"}""")
            server.enqueue(response("""{"ret":0}"""))
            relay.deliver(channel::send)
            val outbound = server.takeRequest().body.readUtf8()
            assertTrue(outbound.contains("我是 Amoo"))
            assertTrue(outbound.contains("ctx"))
            assertTrue(store.pending().isEmpty())
            server.enqueue(response(updates))
            channel.pollOnce()
            assertTrue(store.pending().isEmpty())
            assertTrue(!request.exists())
        }
    }

    @Test fun pollingJournalsOnlyOwnerTextAndAcknowledgesAfterTaskRegistration() = runTest {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val store = WechatStore(tmp.newFolder(), cipher)
            store.saveAccount(WechatAccount("token", server.url("/").toString(), "bot", "owner"))
            store.setEnabled(true)
            val channel = WechatChannel(store, WechatApi(OkHttpClient(), "0.1.35"), log = {})
            val received = mutableListOf<ChannelInput>()
            channel.start(ChannelInputSink { received += it })
            server.enqueue(response("""{"get_updates_buf":"next","msgs":[
                {"message_id":1,"from_user_id":"owner","message_type":1,"context_token":"ctx","item_list":[{"type":1,"text_item":{"text":"任务"}}]},
                {"message_id":2,"from_user_id":"stranger","message_type":1,"item_list":[{"type":1,"text_item":{"text":"不收"}}]}
            ]}"""))

            channel.pollOnce()

            val input = received.single() as ChannelInput.Text
            assertEquals("1", input.messageId)
            assertEquals("任务", input.text)
            assertEquals("wechat", input.peer.channelId)
            assertEquals("next", store.cursor())
            assertEquals("ctx", store.contextToken("owner"))
            assertEquals(1, store.pending().size)
            channel.acknowledge("1")
            assertEquals(emptyList<PendingWechatMessage>(), store.pending())
        }
    }

    @Test fun acceptanceNoticeIsSentAsARealMessage() = runTest {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val store = WechatStore(tmp.newFolder(), cipher)
            store.saveAccount(WechatAccount("token", server.url("/").toString(), "bot", "owner"))
            store.setEnabled(true)
            val channel = WechatChannel(store, WechatApi(OkHttpClient(), "0.1.35"), log = {})
            val peer = store.peer("owner")
            server.enqueue(response("""{"ret":0}"""))

            assertEquals(SendOutcome.Delivered,
                channel.send(peer, Delivery.Notice("accepted:1", peer.threadId, "收到，任务已登记。")))
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("accepted:1"))
            assertTrue(body.contains("收到，任务已登记。"))
        }
    }

    @Test fun sendDistinguishesRejectionFromUncertainNetworkResult() = runTest {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val store = WechatStore(tmp.newFolder(), cipher)
            store.saveAccount(WechatAccount("token", server.url("/").toString(), "bot", "owner"))
            store.setEnabled(true)
            val channel = WechatChannel(store, WechatApi(OkHttpClient(), "0.1.35"), log = {})
            val peer = store.peer("owner")
            val delivery = Delivery.Result("result:1", peer.threadId, "完成")
            server.enqueue(response("""{"ret":-1,"errmsg":"rejected"}"""))
            assertEquals(SendOutcome.Failed, channel.send(peer, delivery))
            server.enqueue(response("""{"ret":0}""").setResponseCode(500))
            assertEquals(SendOutcome.Uncertain, channel.send(peer, delivery))
        }
    }
}

package com.daiyimo.maeve.task

import com.daiyimo.maeve.core.run.ChannelPeer
import com.daiyimo.maeve.core.run.Delivery
import com.daiyimo.maeve.core.run.DeliveryRecord
import com.daiyimo.maeve.core.run.DeliveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class FileOutboxStoreTest {
    @Test fun attemptingDeliverySurvivesRestartWithoutLosingItsIdentity() {
        val dir = Files.createTempDirectory("maeve-outbox").toFile()
        try {
            val file = dir.resolve("outbox.json")
            val peer = ChannelPeer("local", "owner", "thread")
            val delivery = Delivery.Result("result:1", "thread", "回答", title = "任务完成")
            FileOutboxStore(file).save(DeliveryRecord(peer, delivery, DeliveryState.Attempting))
            val restored = FileOutboxStore(file).get("result:1")
            assertEquals(DeliveryRecord(peer, delivery, DeliveryState.Attempting), restored)
        } finally { dir.deleteRecursively() }
    }

    @Test fun deliveredEntriesArePrunedButUncertainEntriesRemain() {
        val dir = Files.createTempDirectory("maeve-outbox").toFile()
        try {
            val store = FileOutboxStore(dir.resolve("outbox.json"), maxDelivered = 2)
            val peer = ChannelPeer("local", "owner", "thread")
            for (i in 1..3) store.save(DeliveryRecord(peer, Delivery.Result("result:$i", "thread", "ok"), DeliveryState.Delivered))
            store.save(DeliveryRecord(peer, Delivery.Result("result:4", "thread", "maybe"), DeliveryState.Uncertain))
            assertNull(store.get("result:1"))
            assertEquals(DeliveryState.Delivered, store.get("result:2")?.state)
            assertEquals(DeliveryState.Delivered, store.get("result:3")?.state)
            assertEquals(DeliveryState.Uncertain, store.get("result:4")?.state)
        } finally { dir.deleteRecursively() }
    }
}

package com.daiyimo.maeve.core.tool

import com.daiyimo.maeve.core.visual.Shots
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DenseTextCropTest {
    private val frame = JvmTestImageOps.encode(1000, 2000) { x, _ -> if (x < 500) 40 else 220 }

    private fun result(vararg lines: Pair<String, List<Int>>) = ToolExecution(
        content = buildJsonObject {
            put("screen", buildJsonObject {
                put("currentApp", "com.x")
                put("visibleText", JsonArray(lines.map { (text, box) -> buildJsonObject {
                    put("text", text)
                    put("bounds", JsonArray(box.map(::JsonPrimitive)))
                } }))
            })
        },
        image = frame,
        imageIds = listOf(1),
    )

    @Test fun formulaClusterAddsSourcePixelCropAndRecallId() {
        val input = result(
            "体验码#01" to listOf(20, 100, 400, 130),
            "数组 A=[166,220]" to listOf(20, 150, 450, 180),
            "规则 ASCII(c)=(A-i^2)/3" to listOf(20, 200, 480, 230),
            "校验 sum=411" to listOf(20, 250, 300, 280),
        )
        val shots = Shots().apply { next() }
        val out = DenseTextCrop.attach(input, JvmTestImageOps, shots, 7)
        assertEquals(2, out.images.size)
        assertEquals(listOf(1, 2), out.imageIds)
        val crop = JvmTestImageOps.decode(out.images.last())
        assertTrue("crop keeps the left-hand source pixels", crop.px.all { (it.toInt() and 0xff) == 40 })
        val note = out.content["evidenceCrop"] as JsonObject
        assertEquals("dense_text", note.s("reason"))
        assertEquals("2", note.s("screenshotId"))
    }

    @Test fun ordinaryScreenDoesNotAddAnImage() {
        val input = result("搜索" to listOf(10, 10, 100, 40), "笔记" to listOf(10, 60, 100, 90))
        assertSame(input, DenseTextCrop.attach(input, JvmTestImageOps, Shots(), 1))
    }

    private fun JsonObject.s(key: String) = this[key]!!.jsonPrimitive.content
}

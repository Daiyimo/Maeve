package com.daiyimo.maeve.core.tool

import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.uistate.ActionResult
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.ScreenSnapshot
import com.daiyimo.maeve.core.uistate.UiElement
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import com.daiyimo.maeve.core.vision.ModelImage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import com.daiyimo.maeve.core.assembly.toolDispatcher

class ToolDispatcherScreenshotTest {
    private fun call(name: String, args: String = "{}") = Part.ToolCall("c1", name, args)
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun screenshotCropPreservesDenseTextPixelsAndRecall() = runTest {
        val d = toolDispatcher(FrameScreen(), visionEnabled = true, imageOps = JvmTestImageOps)
        val r = d.execute(call("phone_screenshot", """{"x":0,"y":0,"w":480,"h":500}"""))
        val image = JvmTestImageOps.decode(r.images.single())
        assertEquals(589, image.w)
        assertEquals(1352, image.h)
        assertTrue("crop must keep the source pixels, not resample them", image.px.all { (it.toInt() and 0xff) == 40 })
        assertEquals("589", r.content.s("width"))
        assertEquals("1352", r.content.s("height"))
        assertEquals("480", (r.content["crop"] as JsonObject).s("w"))
        val id = r.content.s("screenshotId").toInt()
        assertArrayEquals(r.images.single(), d.execute(call("view_past_screens", """{"ids":[$id]}""")).images.single())
    }

    @Test fun formulaScreenAutomaticallyAttachesEvidenceCrop() = runTest {
        val s = FrameScreen(listOf("体验码#01", "数组 A=[166,220]", "规则 ASCII=(A-i^2)/3", "校验 sum=411"))
        val d = toolDispatcher(s, visionEnabled = true, imageOps = JvmTestImageOps)
        val r = d.execute(call("phone_back"))
        assertEquals(2, r.images.size)
        assertEquals("dense_text", (r.content["evidenceCrop"] as JsonObject).s("reason"))
    }

    @Test fun screenshotCropRejectsPartialAndOutOfBoundsRegions() = runTest {
        val d = toolDispatcher(FrameScreen(), visionEnabled = true, imageOps = JvmTestImageOps)
        val partial = d.execute(call("phone_screenshot", """{"x":0}""")).content
        assertEquals("invalid_arguments", partial.s("error"))
        val outside = d.execute(call("phone_screenshot", """{"x":900,"y":0,"w":200,"h":100}""")).content
        assertEquals("invalid_region", outside.s("error"))
    }

    @Test fun screenshotToolIsRecallable() = runTest {
        val d = toolDispatcher(FrameScreen(), visionEnabled = true)
        d.execute(call("phone_observe"))
        val r = d.execute(call("phone_screenshot"))
        val id = r.content.s("screenshotId").toInt()
        assertEquals(listOf<Int?>(id), r.imageIds)
        assertEquals(1, d.execute(call("view_past_screens", """{"ids":[$id]}""")).images.size)
    }

    @Test fun noOpsSendsOriginalWithSniffedType() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13,
            'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(), 0, 0, 4, 200.toByte(), 0, 0, 10, 140.toByte())
        val p = ModelImage.part(png, null, 3)
        assertEquals("image/png", p.mime)
        assertEquals(1224 to 2700, p.width to p.height)
        assertEquals(3, p.screenshotId)
    }
}

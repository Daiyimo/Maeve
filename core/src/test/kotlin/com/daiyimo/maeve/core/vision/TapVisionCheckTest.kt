package com.daiyimo.maeve.core.vision

import com.daiyimo.maeve.core.model.ModelError
import com.daiyimo.maeve.core.model.Part
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** 点前查落点、点后查界面变化:按视觉模型的回答下结论,看不了时不拦路。 */
class TapVisionCheckTest {
    private val ops = JvmTestImageOps
    /** 没有天然 255 像素的图:出现的 255 只可能是画上去的红标记。 */
    private val shot = ops.encode(100, 200) { x, _ -> if (x < 50) 30 else 200 }
    private val other = ops.encode(100, 200) { _, y -> if (y < 100) 30 else 200 }

    private fun check(vararg replies: Any) = TapVisionCheck(VisionService(says(*replies), ops))

    // ---- 点前核查 ----

    @Test fun textAtThePointMatchingWhatWeWantPasses() = runTest {
        assertEquals(PointAudit.Ok, check("""{"at": "更多热搜"}""").auditPoint(shot, 500, 300, "更多热搜"))
    }

    @Test fun blankPointIsRejected() = runTest {
        // 提示词要求空白处回 null:读成没查到(不拦路)就拦不住点空白
        assertEquals(PointAudit.Blank, check("""{"at": null}""").auditPoint(shot, 500, 300, "更多热搜"))
        assertEquals(PointAudit.Blank, check("""{"at": ""}""").auditPoint(shot, 500, 300, null))
        // 代码块、前后带话都照常读
        assertEquals(PointAudit.Blank, check("那一点什么都没有\n```json\n{\"at\": null}\n```").auditPoint(shot, 500, 300, null))
    }

    @Test fun textOfSomethingElseIsRejected() = runTest {
        val r = check("""{"at": "视频"}""").auditPoint(shot, 500, 300, "更多热搜")
        assertEquals(PointAudit.OffTarget("视频"), r)
    }

    @Test fun containedTextCountsAsTheSameThing() = runTest {
        // 落点是「热搜」、要找的是「更多热搜」:互相包含算对上了(和 Locator 核对名字同一口径)
        assertEquals(PointAudit.Ok, check("""{"at": "热搜"}""").auditPoint(shot, 500, 300, "更多热搜"))
        assertEquals(PointAudit.Ok, check("""{"at": "更多热搜 榜"}""").auditPoint(shot, 500, 300, "更多热搜"))
        // 不是一个东西:拒
        assertEquals(PointAudit.OffTarget("视频"), check("""{"at": "视频"}""").auditPoint(shot, 500, 300, "WLAN"))
    }

    @Test fun noWantOnlyChecksBlank() = runTest {
        // 名字为空的大容器:只查那一点是不是空白处,不查是什么
        assertEquals(PointAudit.Ok, check("""{"at": " whatever "}""").auditPoint(shot, 500, 300, null))
        assertEquals(PointAudit.Blank, check("""{"at": null}""").auditPoint(shot, 500, 300, null))
    }

    @Test fun cannotSeeMeansLetItThrough() = runTest {
        // 视觉核查是兜底:看不了的时候不拦路
        assertEquals(PointAudit.Unavailable("audit_unparsed"), check("我在那一点看到了按钮").auditPoint(shot, 500, 300, "更多热搜"))
        assertEquals(PointAudit.Unavailable("vision_unavailable"), check(ModelError.Network()).auditPoint(shot, 500, 300, "更多热搜"))
        assertEquals(PointAudit.Unavailable("audit_unparsed"), check("""{"other": "x"}""").auditPoint(shot, 500, 300, "更多热搜"))
    }

    @Test fun markIsDrawnAtThePoint() = runTest {
        val m = says("""{"at": "更多热搜"}""")
        TapVisionCheck(VisionService(m, ops)).auditPoint(shot, 500, 300, "更多热搜")
        val sent = Base64.getDecoder().decode((m.requests.single().single().parts[1] as Part.Image).pngBase64)
        val px = JvmTestImageOps.decode(sent)
        // 0–999 的 (500,300) 在 100×200 上是 (50,60):标记环画在它周围,那一点本身不盖住
        assertTrue(px.px.any { (it.toInt() and 0xFF) == 255 })
        assertEquals("落点上盖了标记就看不到那儿的字了", 200, px[50, 60])
        assertEquals(255, px[45, 60])
        // 问的就是标记下的内容,不是整屏
        assertEquals("What is at the marked point?", (m.requests.single().single().parts[0] as Part.Text).text)
        assertTrue(m.system!!, "marked point" in m.system!!)
    }

    // ---- 点后核查 ----

    @Test fun screenChangeReadsOneWord() = runTest {
        assertEquals(ScreenChange.Page, check("page").screenChange(shot, other))
        assertEquals(ScreenChange.Minor, check("minor").screenChange(shot, other))
        assertEquals(ScreenChange.None, check("none").screenChange(shot, other))
    }

    @Test fun screenChangeIgnoresDecorations() = runTest {
        assertEquals(ScreenChange.Page, check("Page.").screenChange(shot, other))
        assertEquals(ScreenChange.Minor, check("Minor — still the same page").screenChange(shot, other))
        assertEquals(ScreenChange.None, check("None\n").screenChange(shot, other))
        assertEquals(ScreenChange.None, check("\"none\"").screenChange(shot, other))
    }

    @Test fun screenChangeCannotReadMeansKeepTheTreeVerdict() = runTest {
        assertEquals(ScreenChange.Unavailable, check("界面发生了很多变化").screenChange(shot, other))
        assertEquals(ScreenChange.Unavailable, check(ModelError.Server(500)).screenChange(shot, other))
    }

    @Test fun screenChangeSendsBothShotsInOrder() = runTest {
        val m = says("none")
        TapVisionCheck(VisionService(m, ops)).screenChange(shot, other)
        val msg = m.requests.single().single()
        assertEquals("Before the tap, then after the tap. How did the screen change?", (msg.parts[0] as Part.Text).text)
        val first = Base64.getDecoder().decode((msg.parts[1] as Part.Image).pngBase64)
        val second = Base64.getDecoder().decode((msg.parts[2] as Part.Image).pngBase64)
        assertArrayEquals(shot, first)
        assertArrayEquals(other, second)
        assertTrue(m.system!!, "before a tap" in m.system!!)
        assertFalse(m.system!!, "marked point" in m.system!!)
    }
}

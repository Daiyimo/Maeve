package com.daiyimo.maeve.intercept

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件桥的报文只有两种,但都要能被 amoo 原样理解:读进来的 {"goal":"…"} 不能把转义当成正文,
 * 写回去的 answer 里带引号/换行时也必须是合法 JSON。
 */
class AmooBridgeProtocolTest {

    @Test fun readsGoal() {
        val req = parseAmooRequest("""{"id":"20261105-1620","goal":"打开微信,看看特勤处今天的密码"}""")
        assertEquals("打开微信,看看特勤处今天的密码", req?.goal)
    }

    @Test fun rejectsMissingOrBlankGoal() {
        assertNull(parseAmooRequest("""{"id":"x"}"""))
        assertNull(parseAmooRequest("""{"goal":"   "}"""))
        assertNull(parseAmooRequest("not json at all"))
    }

    @Test fun unescapesQuotesAndNewlines() {
        val req = parseAmooRequest("""{"goal":"say \"hi\"\nsecond line"}""")
        assertEquals("say \"hi\"\nsecond line", req?.goal)
    }

    @Test fun keepsGoalUnderLimit() {
        val long = "a".repeat(5000)
        assertEquals(4000, parseAmooRequest("""{"goal":"$long"}""")?.goal?.length)
    }

    @Test fun writesResultWithEscapes() {
        val json = amooResultJson("id-1", "task-1", "done", "他说:\"好\"\n完成", null)
        assertEquals(
            """{"id":"id-1","task_id":"task-1","status":"done","answer":"他说:\"好\"\n完成","note":null}""",
            json
        )
        assertEquals("task-1", jsonString(json, "task_id"))
    }

    @Test fun writesNullsWhenMissing() {
        val json = amooResultJson("id-2", null, "failed", null, "refused")
        assertEquals("""{"id":"id-2","task_id":null,"status":"failed","answer":null,"note":"refused"}""", json)
        assertTrue(jsonString(json, "goal") == null)
    }
}

package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.tool.ActionPass
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.tool.ToolDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPlanTest {
    private fun call(tools: ToolDispatcher, args: String): JsonObject = runBlocking {
        tools.execute(Part.ToolCall("plan", "report_plan", args), ActionPass()).content
    }

    @Test fun titleIsRequiredOnlyForTheFirstPlan() {
        assertEquals(listOf("steps", "currentStep"), ReportPlan.def.required)
        val tools = toolDispatcher(FakeScreen())
        val first = call(tools, """{"title":"整理资料","steps":["打开文件","读取内容","汇总答案"],"currentStep":1}""")
        assertEquals(JsonPrimitive(true), first["ok"])
        assertEquals("整理资料", tools.taskPlan?.title)

        val update = call(tools, """{"steps":["打开文件","读取内容","汇总答案"],"currentStep":2}""")
        assertEquals(JsonPrimitive(true), update["ok"])
        assertEquals("整理资料", update["title"]!!.jsonPrimitive.content)
        assertEquals("整理资料", tools.taskPlan?.title)
        assertEquals(2, tools.taskPlan?.currentStep)
    }

    @Test fun firstPlanWithoutTitleIsRejected() {
        val tools = toolDispatcher(FakeScreen())
        val result = call(tools, """{"steps":["打开文件"],"currentStep":1}""")
        assertEquals(JsonPrimitive(false), result["ok"])
        assertEquals("invalid_arguments", result["error"]!!.jsonPrimitive.content)
        assertNull(tools.taskPlan)
    }

    @Test fun anExplicitLaterTitleStillReplacesTheOldOne() {
        val tools = toolDispatcher(FakeScreen())
        assertTrue(call(tools, """{"title":"旧标题","steps":["打开文件"],"currentStep":1}""")["ok"] == JsonPrimitive(true))
        val result = call(tools, """{"title":"新标题","steps":["读取内容"],"currentStep":1}""")
        assertEquals(JsonPrimitive(true), result["ok"])
        assertEquals("新标题", tools.taskPlan?.title)
        assertFalse(tools.taskPlan?.title == "旧标题")
    }
}

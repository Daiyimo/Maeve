package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.Capabilities
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.StopReason
import com.daiyimo.maeve.core.model.ToolSpec
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.testing.answerReply
import com.daiyimo.maeve.core.tool.FakeScreen
import com.daiyimo.maeve.core.uistate.ActionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopExplicitLaunchTest {
    private val goal = "去微信搜三角洲小程序 主页特勤处看看制造推荐和今日密码"
    private val apps = listOf("com.tencent.mm" to "微信")

    @Test fun explicitMiniProgramOpensWeChatAndRecordsAFrameBeforeTheFirstModelWait() = runTest {
        val screen = FakeScreen().apply { result = ActionResult.Ok(changed = true, appSwitched = "com.tencent.mm") }
        val events = mutableListOf<RunEvent>()
        val requests = mutableListOf<List<Message>>()
        val judge = FakeModel(answerReply("READ"))
        val main = object : ChatModel {
            override val capabilities = Capabilities(vision = true, toolCalls = true, contextTokens = 128_000, reasoning = false)
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
                requests += messages
                delay(30_000)
                return answerReply("已查看")
            }
        }
        val end = AgentLoop(main, toolDispatcher(screen, true), "BASE",
            installedApps = { apps }, intentJudge = judge, clock = { currentTime },
            sink = object : RunSink { override fun event(e: RunEvent) { events += e } }).run(goal)
        assertTrue(end is TaskState.Succeeded)
        assertEquals(listOf("open com.tencent.mm false"), screen.calls)
        assertTrue(judge.requests.isEmpty())
        val media = events.filterIsInstance<RunEvent.Media>().first()
        assertEquals(1, media.step)
        assertTrue("The first frame must precede the 30-second model wait", media.atMs < 30_000)
        val result = requests.first().flatMap { it.parts }.filterIsInstance<Part.ToolResult>().single()
        assertTrue(result.content.contains("com.tencent.mm"))
        assertEquals(2, events.filterIsInstance<RunEvent.ModelReply>().first().step)
    }

    @Test fun examplesQuestionsAndNegativeInstructionsStillGoThroughTheIntentJudge() = runTest {
        for (text in listOf("例如：$goal", "去微信搜三角洲小程序要怎么操作？", "$goal，先别执行")) {
            val screen = FakeScreen()
            val call = Part.ToolCall("launch", "phone_launch_app", """{"package":"com.tencent.mm"}""")
            val main = FakeModel(ModelReply(listOf(call), StopReason.ToolUse, null), answerReply("可以说明做法"))
            val judge = FakeModel(answerReply("DISCUSS"))
            AgentLoop(main, toolDispatcher(screen), "BASE", installedApps = { apps }, intentJudge = judge).run(text)
            assertTrue(text, screen.calls.isEmpty())
            assertEquals(text, 1, judge.requests.size)
        }
    }

    @Test fun intentJudgeTimeIsReportedSeparatelyFromModelLatency() = runTest {
        val call = Part.ToolCall("launch", "phone_launch_app", """{"package":"com.tencent.mm"}""")
        val scripted = FakeModel(ModelReply(listOf(call), StopReason.ToolUse, null), answerReply("已完成"))
        val main = object : ChatModel by scripted {
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
                delay(200)
                return scripted.complete(system, messages, tools)
            }
        }
        val judge = object : ChatModel by scripted {
            override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
                delay(1200)
                return answerReply("ACT")
            }
        }
        val events = mutableListOf<RunEvent>()
        AgentLoop(main, toolDispatcher(FakeScreen()), "BASE", installedApps = { apps },
            intentJudge = judge, clock = { currentTime },
            sink = object : RunSink { override fun event(e: RunEvent) { events += e } }).run("现在打开微信")
        assertEquals(200L, events.filterIsInstance<RunEvent.ModelReply>().first().latencyMs)
        assertEquals("1200", events.filterIsInstance<RunEvent.State>().single { it.name == "intent_gate_ms" }.detail)
    }

    @Test fun unavailableWeChatFallsBackToTheNormalModelRoute() = runTest {
        val screen = FakeScreen()
        val main = FakeModel(answerReply("未找到微信"))
        AgentLoop(main, toolDispatcher(screen), "BASE", installedApps = { emptyList() }).run(goal)
        assertTrue(screen.calls.isEmpty())
        assertEquals(1, main.requests.size)
    }
}

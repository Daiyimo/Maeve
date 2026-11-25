package com.daiyimo.maeve.core.cap.phone

import com.daiyimo.maeve.core.agent.AgentLoop
import com.daiyimo.maeve.core.agent.FakeModel
import com.daiyimo.maeve.core.agent.TaskState
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.model.ModelError
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.StopReason
import com.daiyimo.maeve.core.run.RunId
import com.daiyimo.maeve.core.run.RunInbox
import com.daiyimo.maeve.core.run.RunSpec
import com.daiyimo.maeve.core.record.LocalReview
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.testing.Screen
import com.daiyimo.maeve.core.testing.ScriptedScreen
import com.daiyimo.maeve.core.testing.answerReply
import com.daiyimo.maeve.core.testing.button
import com.daiyimo.maeve.core.testing.label
import com.daiyimo.maeve.core.trigger.TriggerPreset
import com.daiyimo.maeve.core.tool.LinkPolicy
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.vision.FRAME_A
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import com.daiyimo.maeve.core.vision.VisionService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmooCheckInTest {
    private val preset = TriggerPreset.AmooGrowthCheckIn
    private val growth = "com.stepos.ailauncher"
    private val pending = Screen(growth, listOf(button("@e1", "签到")))
    private val signed = Screen(growth, listOf(label("@e1", "已签到")))

    @Test fun visionWinsWhenTheTreeAlsoHasTheButton() = runTest {
        val screen = ScriptedScreen(listOf(pending, signed))
        val vision = FakeModel(answerReply("""{"bbox_2d":[470,460,530,540],"seen":"签到"}"""), answerReply("yes"))
        val output = run(screen, vision)
        assertEquals("vision", output.single()["method"]!!.jsonPrimitive.content)
        assertEquals(2, vision.requests.size)
        assertTrue(screen.calls.single().startsWith("tapXY "))
    }

    @Test fun visionWinsEvenWhenTheTreeHasMultipleCheckInButtons() = runTest {
        val screen = ScriptedScreen(listOf(pending.copy(elements = pending.elements + button("@e2", "签到")), signed))
        val vision = FakeModel(answerReply("""{"bbox_2d":[470,460,530,540],"seen":"签到"}"""), answerReply("yes"))

        val output = run(screen, vision)

        assertEquals("vision", output.single()["method"]!!.jsonPrimitive.content)
        assertTrue(screen.calls.single().startsWith("tapXY "))
    }

    @Test fun visionOnlyPointIsUsedWhenTheTreeHasNoTarget() = runTest {
        val screen = ScriptedScreen(listOf(Screen(growth, emptyList()), signed))
        val vision = FakeModel(
            answerReply("""{"bbox_2d":[470,460,530,540],"seen":"签到"}"""),
            answerReply("yes"),
            groundingVerified = false,
        )

        val output = run(screen, vision)

        assertEquals("signed", output.single()["status"]!!.jsonPrimitive.content)
        assertEquals("vision", output.single()["method"]!!.jsonPrimitive.content)
        assertTrue(screen.calls.single().startsWith("tapXY "))
    }

    @Test fun signedStateCanBeReadByVisionWhenTheTreeIsEmpty() = runTest {
        val screen = ScriptedScreen(listOf(Screen(growth, emptyList())))
        val vision = FakeModel(
            answerReply("""{"bbox_2d":[470,460,530,540],"seen":"已签到"}"""), answerReply("yes"),
            answerReply("""{"bbox_2d":[470,460,530,540],"seen":"已签到"}"""), answerReply("yes"),
        )

        val output = run(screen, vision)

        assertEquals("already_signed", output.single()["status"]!!.jsonPrimitive.content)
        assertEquals("vision", output.single()["method"]!!.jsonPrimitive.content)
        assertTrue(screen.calls.isEmpty())
    }

    @Test fun accountNavigationAlsoPrefersVisionOverDuplicateTreeLabels() = runTest {
        val account = Screen(LinkPolicy.STEPX_ACCOUNT_PACKAGE, listOf(
            button("@e1", "Amoo 成长计划"), button("@e2", "Amoo 成长计划"),
        ))
        val screen = ScriptedScreen(listOf(account, pending, signed))
        val vision = FakeModel(
            answerReply("""{"bbox_2d":[100,460,900,540],"seen":"Amoo 成长计划"}"""), answerReply("yes"),
            answerReply("""{"bbox_2d":[470,460,530,540],"seen":"签到"}"""), answerReply("yes"),
        )

        val output = run(screen, vision)

        val result = output.single()
        assertEquals(output.toString(), "signed", result["status"]?.jsonPrimitive?.content)
        assertEquals(2, screen.calls.size)
        assertTrue(screen.calls.all { it.startsWith("tapXY ") })
    }

    @Test fun brokenVisionApiUsesTheExactTreeTarget() = runTest {
        val screen = ScriptedScreen(listOf(pending, signed))
        val vision = FakeModel(ModelError.Auth())
        val output = run(screen, vision)
        assertEquals("signed", output.single()["status"]!!.jsonPrimitive.content)
        assertEquals("ui_tree", output.single()["method"]!!.jsonPrimitive.content)
        assertEquals(1, vision.requests.size)
        assertTrue(screen.calls.single().startsWith("tap @e1 "))
    }

    @Test fun signedStateNeverTapsEvenWhenAButtonIsAlsoPresent() = runTest {
        val screen = ScriptedScreen(listOf(signed.copy(elements = signed.elements + pending.elements)))
        val output = run(screen)
        assertEquals("already_signed", output.single()["status"]!!.jsonPrimitive.content)
        assertTrue(screen.calls.isEmpty())
    }

    @Test fun unverifiedResultNeverTriggersAnotherTap() = runTest {
        val screen = ScriptedScreen(listOf(pending))
        val output = run(screen, attempts = 2)
        assertEquals(listOf("check_in_unverified", "check_in_already_attempted"), output.map { it["error"]!!.jsonPrimitive.content })
        assertEquals(1, screen.calls.size)
    }

    @Test fun scheduledPresetRecordsFramesAfterEnteringGrowthPlan() = runTest {
        val account = Screen(LinkPolicy.STEPX_ACCOUNT_PACKAGE, listOf(button("@e1", "Amoo 成长计划")))
        val screen = ScriptedScreen(listOf(account, pending, signed))
        val shots = mutableListOf<Int>()
        val events = mutableListOf<RunEvent>()
        val sink = object : RunSink {
            override fun event(e: RunEvent) { events += e }
            override fun media(mediaId: Int, png: ByteArray) { shots += mediaId }
        }
        val port = object : ScreenPort by screen {
            override suspend fun currentPackage() = screen.current.app
            override suspend fun screenshotPng() = FRAME_A
        }
        val spec = RunSpec(RunId("growth-frames"), preset.goal, "test", triggerPreset = preset)
        val tools = toolDispatcher(port, deepLinks = true, recordFrames = true, runSpec = spec)

        val result = AgentLoop(FakeModel(), tools, "BASE", sink = sink).run(spec, RunInbox())

        assertTrue(result is TaskState.Succeeded)
        val navigation = events.filterIsInstance<RunEvent.ToolResult>()
            .firstOrNull { it.tool == "phone_tap" && it.screenshotId != null }
        assertNotNull("entering Growth Plan must record an after frame", navigation)
        val shotId = navigation!!.screenshotId!!
        assertTrue(shots.contains(shotId))
        assertTrue(events.filterIsInstance<RunEvent.Media>().any { it.mediaId == shotId })
    }

    @Test fun scheduledPresetEmitsVerifiedReviewAfterTreeReadback() = runTest {
        val screen = ScriptedScreen(listOf(signed))
        val events = mutableListOf<RunEvent>()
        val sink = object : RunSink {
            override fun event(e: RunEvent) { events += e }
        }
        val port = object : ScreenPort by screen {
            override suspend fun currentPackage() = growth
            override suspend fun screenshotPng() = FRAME_A
        }
        val spec = RunSpec(RunId("growth-review"), preset.goal, "test", triggerPreset = preset)
        val tools = toolDispatcher(port, recordFrames = true, runSpec = spec)

        val result = AgentLoop(FakeModel(), tools, "BASE", sink = sink, localAnswerReview = true).run(spec, RunInbox())

        assertTrue(result is TaskState.Succeeded)
        val review = events.filterIsInstance<RunEvent.LocalReviewCompleted>().single().review
        assertEquals(LocalReview.Status.Verified, review.status)
    }

    @Test fun scheduledPresetRunsWithoutCallingThePlanningModel() = runTest {
        val screen = ScriptedScreen(listOf(pending, signed))
        val port = object : ScreenPort by screen {
            override suspend fun currentPackage() = growth
        }
        val model = FakeModel()
        val spec = RunSpec(RunId("growth-schedule"), preset.goal, "test", triggerPreset = preset)
        val result = AgentLoop(model, toolDispatcher(port, runSpec = spec), "BASE").run(spec, RunInbox())
        assertTrue(result is TaskState.Succeeded)
        assertTrue(model.requests.isEmpty())
        assertEquals(1, screen.calls.size)
    }

    private suspend fun run(screen: ScriptedScreen, vision: FakeModel? = null, attempts: Int = 1) =
        with(object : ScreenPort by screen {
            override suspend fun currentPackage() = screen.current.app
            override suspend fun screenshotPng() = FRAME_A
        }) {
            val model = FakeModel(*Array(attempts) { i ->
                ModelReply(listOf(Part.ToolCall("case-$i", "phone_run", """{"commands":[{"op":"amoo_check_in"}]}""")), StopReason.ToolUse, null)
            }, answerReply("完成核对"))
            val spec = RunSpec(RunId("growth-test"), preset.goal, "test", triggerPreset = preset)
            val tools = toolDispatcher(this, deepLinks = true, runSpec = spec,
                vision = vision?.let { VisionService(it, JvmTestImageOps) })
            AgentLoop(model, tools, "BASE").run(preset.goal)
            model.requests.last().flatMap { it.parts }.filterIsInstance<Part.ToolResult>()
                .map { Json.parseToJsonElement(it.content).jsonObject }
        }
}

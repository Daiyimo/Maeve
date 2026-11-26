package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.access.AppAccessPolicy
import com.daiyimo.maeve.core.access.AppAccessState
import com.daiyimo.maeve.core.agent.FakeModel
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.StopReason
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.VisualFrame
import com.daiyimo.maeve.core.vision.FRAME_A
import com.daiyimo.maeve.core.vision.JvmTestImageOps
import com.daiyimo.maeve.core.vision.LocScreen
import com.daiyimo.maeve.core.vision.VisionService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualFirstDescribedTargetTest {
    private val sink = object : RunSink { override fun event(e: RunEvent) {} }

    private fun model(vararg replies: String) = FakeModel(
        *replies.map { ModelReply(listOf(Part.Text(it)), StopReason.EndTurn, null) }.toTypedArray(),
        groundingVerified = false,
    )

    private fun executor(screen: ScreenPort, vision: FakeModel) = ActionExecutor(
        toolDispatcher(screen, vision = VisionService(vision, JvmTestImageOps)),
        OperationGuards(), ConfirmationGate(), gate = {}, recorder = ActionRecorder(sink),
    )

    @Test fun visualFirstElementWithoutOcrFailsFastWithoutVisionModel() = runTest {
        val inner = LocScreen()
        val screen = object : ScreenPort by inner {
            override val appAccess = AppAccessPolicy { AppAccessState() }
            override suspend fun currentPackage() = "com.tencent.jkchess"
            override suspend fun viewport() = 2400 to 1080
            override suspend fun visualFrame(withImage: Boolean, timeoutMs: Long) =
                VisualFrame(if (withImage) FRAME_A else null, emptyList(), 2400, 1080)
        }
        val vision = model("""{"bbox_2d":[0,0,100,100],"seen":"X"}""", "是")
        val outcome = executor(screen, vision).run(
            Part.ToolCall("c", "phone_tap", """{"element":"X"}"""), Origin.Model(1),
        ) as ActionOutcome.Ran
        val content = outcome.content

        assertEquals("visual_first", (content["error"] as JsonPrimitive).content)
        assertTrue((content["message"] as JsonPrimitive).content.contains("x+y"))
        assertTrue(vision.requests.isEmpty())
        assertTrue(inner.taps.isEmpty())
    }
}

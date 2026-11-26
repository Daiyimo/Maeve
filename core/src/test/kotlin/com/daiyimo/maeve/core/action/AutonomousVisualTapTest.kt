package com.daiyimo.maeve.core.action

import com.daiyimo.maeve.core.access.AppAccessPolicy
import com.daiyimo.maeve.core.access.AppAccessState
import com.daiyimo.maeve.core.assembly.toolDispatcher
import com.daiyimo.maeve.core.gate.ConfirmationGate
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.RunSink
import com.daiyimo.maeve.core.uistate.ScreenPort
import com.daiyimo.maeve.core.uistate.VisualFrame
import com.daiyimo.maeve.core.uistate.VisibleTextLine
import com.daiyimo.maeve.core.uistate.session
import com.daiyimo.maeve.core.vision.FRAME_A
import com.daiyimo.maeve.core.vision.LocScreen
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousVisualTapTest {
    @Test fun autonomousCoordinateTapWorksWhenOcrIsUnavailable() = runTest {
        val inner = LocScreen()
        val screen = object : ScreenPort by inner {
            override val appAccess = AppAccessPolicy { AppAccessState() }
            override suspend fun currentPackage() = "com.tencent.jkchess"
            override suspend fun viewport() = 2400 to 1080
            override suspend fun visualFrame(withImage: Boolean, timeoutMs: Long) =
                VisualFrame(if (withImage) FRAME_A else null, null, 2400, 1080)
        }
        val executor = ActionExecutor(
            toolDispatcher(screen, visionEnabled = true),
            OperationGuards(), ConfirmationGate(), gate = {}, recorder = ActionRecorder(RunSink.NONE), autonomous = true,
        )
        val outcome = executor.run(
            Part.ToolCall("c", "phone_tap", """{"x":500,"y":500,"target":"按钮"}"""), Origin.Model(1),
        )

        assertTrue("$outcome", outcome is ActionOutcome.Ran)
        assertEquals(1, inner.taps.size)
    }

    @Test fun coordinateGuardReusesFreshOcrInsteadOfLosingItToASecondRead() = runTest {
        var ocrReads = 0
        val screen = object : ScreenPort by LocScreen() {
            override val appAccess = AppAccessPolicy { AppAccessState() }
            override suspend fun currentPackage() = "com.tencent.jkchess"
            override suspend fun viewport() = 2400 to 1080
            override suspend fun readVisibleText(): List<VisibleTextLine>? {
                ocrReads++
                return if (ocrReads == 1) listOf(VisibleTextLine("开始游戏", listOf(459, 813, 540, 852))) else null
            }
            override suspend fun visualFrame(withImage: Boolean, timeoutMs: Long) =
                VisualFrame(if (withImage) FRAME_A else null, readVisibleText(), 2400, 1080)
        }
        val dispatcher = toolDispatcher(screen, visionEnabled = true)
        dispatcher.executeRead(Part.ToolCall("o", "phone_observe", "{}"))
        val mismatch = dispatcher.ctx.session.checkTextPoint("开始游戏", 500, 500)

        assertEquals("tap_text_mismatch", mismatch!!.content["error"]!!.jsonPrimitive.content)
        assertEquals(1, ocrReads)
    }
}

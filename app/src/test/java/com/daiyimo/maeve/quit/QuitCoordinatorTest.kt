package com.daiyimo.maeve.quit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 完全退出的编排不碰安卓对象,可以直接核对「先记标记、再停后台、最后杀进程」的顺序。 */
class QuitCoordinatorTest {
    private class State(initial: Boolean, private val events: MutableList<String>) : QuitState {
        var quit = initial
            private set

        override fun quitRequested(): Boolean = quit
        override fun setQuitRequested(on: Boolean) {
            quit = on
            events += "state:$on"
        }
    }

    private class Effects(private val events: MutableList<String>) : QuitEffects {
        override fun stopCapsule() { events += "stopCapsule" }
        override fun stopBackgroundWork() { events += "stopBackgroundWork" }
        override fun stopServices() { events += "stopServices" }
        override fun finishActivity() { events += "finishActivity" }
        override fun clearNotifications() { events += "clearNotifications" }
        override fun resumeCapsule() { events += "resumeCapsule" }
        override fun resumeServices() { events += "resumeServices" }
    }

    private class Exit(private val events: MutableList<String>) : QuitExit {
        override fun cancel() { events += "cancelExit" }
        override fun schedule() { events += "scheduleExit" }
    }

    @Test fun quitPersistsFirstAndSchedulesProcessExitLast() {
        val events = mutableListOf<String>()
        val state = State(initial = false, events)
        QuitCoordinator(state, Effects(events), Exit(events)).quit()

        assertEquals(
            listOf(
                "state:true",
                "stopCapsule",
                "stopBackgroundWork",
                "stopServices",
                "finishActivity",
                "clearNotifications",
                "scheduleExit",
            ),
            events,
        )
        assertTrue(state.quit)
    }

    @Test fun resumeCancelsPendingExitBeforeClearingTheFlagAndRestoring() {
        val events = mutableListOf<String>()
        val state = State(initial = true, events)
        val restored = QuitCoordinator(state, Effects(events), Exit(events)).resume()

        assertTrue(restored)
        assertFalse(state.quit)
        assertEquals(
            listOf("cancelExit", "state:false", "resumeCapsule", "resumeServices"),
            events,
        )
    }

    @Test fun openingMaeveWithoutQuitOnlyCancelsAPendingExit() {
        val events = mutableListOf<String>()
        val state = State(initial = false, events)
        val restored = QuitCoordinator(state, Effects(events), Exit(events)).resume()

        assertFalse(restored)
        assertEquals(listOf("cancelExit"), events)
    }
}

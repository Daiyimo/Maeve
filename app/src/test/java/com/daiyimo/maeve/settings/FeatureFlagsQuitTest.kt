package com.daiyimo.maeve.settings

import com.daiyimo.maeve.capsule.CapsuleMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「完全退出」的标记:默认没有,记下后跨进程还在,重新打开时发一次变化。 */
class FeatureFlagsQuitTest {
    @Test fun quitDefaultsOffAndIsKept() {
        val prefs = FeatureFlagsCapsuleTest.MemoryPrefs()
        assertEquals(false, FeatureFlags(prefs).quitRequested())
        FeatureFlags(prefs).setQuitRequested(true)
        assertEquals(true, FeatureFlags(prefs).quitRequested())
        FeatureFlags(prefs).setQuitRequested(false)
        assertEquals(false, FeatureFlags(prefs).quitRequested())
    }

    @Test fun quitDoesNotOverwriteTheSavedCapsuleMode() {
        val flags = FeatureFlags(FeatureFlagsCapsuleTest.MemoryPrefs())
        flags.setCapsuleMode(CapsuleMode.DuringTasks)
        flags.setQuitRequested(true)
        assertEquals(CapsuleMode.Off, flags.capsuleMode().effective(quit = true))
        assertEquals(CapsuleMode.DuringTasks, flags.capsuleMode())

        flags.setQuitRequested(false)
        assertEquals(CapsuleMode.DuringTasks, flags.capsuleMode().effective(quit = false))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun changesEmitCurrentThenEachChange() = runTest {
        val flags = FeatureFlags(FeatureFlagsCapsuleTest.MemoryPrefs())
        assertEquals(false, flags.quitChanges().first())
        val seen = mutableListOf<Boolean>()
        val job = launch { flags.quitChanges().take(2).toList(seen) }
        runCurrent()
        flags.setQuitRequested(true)
        job.join()
        assertEquals(listOf(false, true), seen)
    }
}

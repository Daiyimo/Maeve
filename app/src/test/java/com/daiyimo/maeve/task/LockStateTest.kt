package com.daiyimo.maeve.task

import com.daiyimo.maeve.core.agent.TaskState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockStateTest {
    private var interactive = false
    private var keyguardLocked = true
    private var reads = 0

    private fun TestScope.lockState() = LockState({ reads++; LockState.Reading(interactive, keyguardLocked) }, backgroundScope)

    private fun TestScope.tick() {
        advanceTimeBy(DeviceStateWatcher.POLL_MS)
        runCurrent()
    }

    // 解锁的广播来得比锁屏界面退下早:读到的还是锁着,之后没有广播,也要自己看到解锁
    @Test fun unlockSeenWithoutAnotherBroadcast() = runTest {
        val lock = lockState()
        val gate = TaskGate(lock.locked, MutableStateFlow(true)) { true }
        val reported = mutableListOf<TaskState?>()
        var passed = false
        launch { gate.pass { reported += it }; passed = true }
        runCurrent()
        assertEquals(TaskState.Held(TaskGate.LOCKED), reported.last())

        // 熄屏期间也继续重读:部分 ROM 会漏 SCREEN_ON / USER_PRESENT,不能永久等广播
        val dark = reads
        repeat(5) { tick() }
        assertTrue("熄屏时仍应轮询系统状态", reads > dark)

        interactive = true
        lock.refresh() // SCREEN_ON 或 USER_PRESENT:亮了,锁屏界面还在
        tick()
        assertFalse(passed)
        keyguardLocked = false // 锁屏界面退下,没有广播
        tick()
        assertTrue(passed)
        assertEquals(null, reported.last())

        // 读到解锁后不再读
        val unlocked = reads
        repeat(10) { tick() }
        assertEquals(unlocked, reads)
    }

    @Test fun generationChangesOnEveryObservedLockCycle() = runTest {
        val lock = lockState()
        assertEquals(0L, lock.generation)
        interactive = true
        keyguardLocked = false
        lock.refresh()
        assertEquals(1L, lock.generation)
        keyguardLocked = true
        lock.refresh()
        assertEquals(2L, lock.generation)
    }

    // 没人看这份状态时不读
    @Test fun noReadsWithoutSubscribers() = runTest {
        interactive = true
        val lock = lockState()
        runCurrent()
        val start = reads
        repeat(10) { tick() }
        assertEquals(start, reads)
        assertTrue(lock.locked.value)
    }
}

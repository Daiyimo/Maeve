package com.daiyimo.maeve.ui.haptic

import android.os.VibrationEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HapticEngine 的门控、五级映射、降级链和 Reject 语义;见 docs/spec/haptic-matrix.md §5。 */
class HapticEngineTest {

    private class FakeActuator(var supported: Set<Int> = emptySet()) : HapticActuator {
        val predefined = mutableListOf<Int>()
        val waveforms = mutableListOf<Pair<LongArray, IntArray>>()
        override fun supports(effect: Int) = effect in supported
        override fun predefined(effect: Int) { predefined += effect }
        override fun waveform(timings: LongArray, amplitudes: IntArray) {
            waveforms += timings to amplitudes
        }
    }

    private val allEffects = setOf(
        VibrationEffect.EFFECT_TICK,
        VibrationEffect.EFFECT_CLICK,
        VibrationEffect.EFFECT_HEAVY_CLICK,
        VibrationEffect.EFFECT_DOUBLE_CLICK,
    )

    @Test
    fun disabledEngineEmitsNothing() {
        val actuator = FakeActuator(allEffects)
        val engine = DefaultHapticEngine(actuator) { false }
        HapticLevel.entries.forEach { engine.emit(it) }
        assertTrue(actuator.predefined.isEmpty())
        assertTrue(actuator.waveforms.isEmpty())
    }

    @Test
    fun levelsMapToDistinctEffects() {
        val actuator = FakeActuator(allEffects)
        val engine = DefaultHapticEngine(actuator) { true }
        engine.tick()
        engine.light()
        engine.medium()
        engine.heavy()
        assertEquals(
            listOf(
                VibrationEffect.EFFECT_TICK,
                VibrationEffect.EFFECT_CLICK,
                VibrationEffect.EFFECT_HEAVY_CLICK,
                VibrationEffect.EFFECT_DOUBLE_CLICK,
            ),
            actuator.predefined,
        )
    }

    @Test
    fun rejectIsNeverAPositiveClick() {
        val actuator = FakeActuator(allEffects)
        DefaultHapticEngine(actuator) { true }.reject()
        assertTrue("Reject must not reuse a confirmation click", actuator.predefined.isEmpty())
        assertEquals(1, actuator.waveforms.size)
        assertTrue(actuator.waveforms.single().second.size > 2)
    }

    @Test
    fun unsupportedEffectsDegradeSilentlyWithoutCrashing() {
        val actuator = FakeActuator(emptySet())
        val engine = DefaultHapticEngine(actuator) { true }
        engine.tick()
        engine.heavy()
        assertTrue(actuator.predefined.isEmpty())
        assertEquals(
            "Heavy must never degrade to a light click — that would fake an approval",
            1,
            actuator.waveforms.size,
        )
    }

    @Test
    fun refreshPicksUpHapticSettingChanges() {
        val actuator = FakeActuator(allEffects)
        var on = false
        val engine = DefaultHapticEngine(actuator) { on }
        engine.tick()
        assertTrue(actuator.predefined.isEmpty())

        on = true
        engine.refresh()
        engine.tick()
        assertEquals(1, actuator.predefined.size)
    }
}

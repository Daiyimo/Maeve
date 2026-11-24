package com.daiyimo.maeve.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureFlagsStepLimitTest {
    private fun flags() = FeatureFlags(FeatureFlagsCapsuleTest.MemoryPrefs())

    @Test fun defaultsToFiniteLimit() {
        assertEquals(500, flags().taskStepLimit())
    }

    @Test fun keepsAChosenLimit() {
        val f = flags()
        f.setTaskStepLimit(60)
        assertEquals(60, f.taskStepLimit())
        f.setTaskStepLimit(null)
        assertEquals(500, f.taskStepLimit())
    }

    @Test fun clampsIntoRange() {
        val f = flags()
        f.setTaskStepLimit(0)
        assertEquals(500, f.taskStepLimit())
        f.setTaskStepLimit(-5)
        assertEquals(500, f.taskStepLimit())
        f.setTaskStepLimit(FeatureFlags.MAX_TASK_STEP_LIMIT + 100)
        assertEquals(FeatureFlags.MAX_TASK_STEP_LIMIT, f.taskStepLimit())
    }
}

package com.daiyimo.maeve.core.cap.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GesturePacingDragTest {

    @Test fun instantDragInsertsHoldAndRestWithoutMoving() {
        val s = GesturePacing.Instant.stroke(
            listOf(0.0 to 0.0, 0.0 to 100.0), 1_000, holdMs = 200, restMs = 300,
        )
        assertEquals(listOf(
            GesturePacing.Point(0.0, 0.0, 0),
            GesturePacing.Point(0.0, 0.0, 200),
            GesturePacing.Point(0.0, 100.0, 700),
            GesturePacing.Point(0.0, 100.0, 1_000),
        ), s.points)
    }

    @Test fun pacedDragWithExplicitRestNeverMovesPastItsTotalDuration() {
        for (seed in 1..40) {
            val stroke = GesturePacing.human(Random(seed)).stroke(
                listOf(0.0 to 0.0, 0.0 to 100.0), 1_000, holdMs = 100, restMs = 10,
            )
            assertEquals("seed $seed duration", 1_000L, stroke.durationMs)
            assertTrue("seed $seed timeline ${stroke.points.map { it.t }}",
                stroke.points.zipWithNext().all { (a, b) -> b.t >= a.t })
        }
    }
}

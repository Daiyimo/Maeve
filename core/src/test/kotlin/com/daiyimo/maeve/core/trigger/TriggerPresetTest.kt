package com.daiyimo.maeve.core.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TriggerPresetTest {
    @Test fun amooBuiltInScheduleRunsAtNineInShanghai() {
        val beforeNine = Instant.parse("2026-11-25T00:30:00Z").toEpochMilli()
        val plan = TriggerPreset.AmooGrowthCheckIn.builtInSchedule(beforeNine)!!

        assertEquals("builtin-amoo_growth_check_in", plan.id)
        assertEquals("scheduled-amoo_growth_check_in", plan.threadId)
        assertEquals(TriggerPreset.AmooGrowthCheckIn, plan.preset)
        assertEquals("09:00", plan.calendarTime)
        assertEquals("Asia/Shanghai", plan.calendarZone)
        assertEquals(emptyList<Int>(), plan.calendarDays)
        assertTrue(plan.fixedZone)
        assertTrue(plan.idleOnly)
        assertEquals(
            Instant.parse("2026-11-25T01:00:00Z").toEpochMilli(),
            plan.nextAtMs,
        )
    }

    @Test fun amooBuiltInScheduleAtNineAdvancesToTomorrow() {
        val now = Instant.parse("2026-11-25T01:00:00Z").toEpochMilli()
        val plan = TriggerPreset.AmooGrowthCheckIn.builtInSchedule(now)!!

        assertEquals(Instant.parse("2026-11-26T01:00:00Z").toEpochMilli(), plan.nextAtMs)
        assertEquals(plan.id, TriggerPreset.AmooGrowthCheckIn.builtInSchedule(now)!!.id)
    }

    @Test fun builtInZoneDoesNotFollowTheDevice() {
        val now = Instant.parse("2026-11-25T01:00:00Z").toEpochMilli()
        val plan = TriggerPreset.AmooGrowthCheckIn.builtInSchedule(now)!!
        val store = MemoryTriggerStore()
        store.upsert(plan)

        TriggerEngine(store, {}) { _, _ -> }.rezone(now, "Asia/Shanghai", ZoneId.of("America/New_York"))

        assertEquals(plan, store.get(plan.id))
    }

    @Test fun missingBuiltInsPreserveExistingPausedAndManualPlans() {
        val now = Instant.parse("2026-11-25T00:30:00Z").toEpochMilli()
        val builtIn = TriggerPreset.missingBuiltInSchedules(emptyList(), now).single()
        assertTrue(TriggerPreset.missingBuiltInSchedules(listOf(builtIn.copy(enabled = false)), now).isEmpty())
        assertTrue(TriggerPreset.missingBuiltInSchedules(listOf(builtIn), now).isEmpty())

        val manual = builtIn.copy(id = "manual", threadId = "manual", calendarZone = "America/New_York")
        assertTrue(TriggerPreset.missingBuiltInSchedules(listOf(manual), now).isEmpty())
    }
}

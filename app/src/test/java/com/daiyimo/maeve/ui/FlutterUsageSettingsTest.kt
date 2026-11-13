package com.daiyimo.maeve.ui

import com.daiyimo.maeve.core.usage.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class FlutterUsageSettingsTest {
    @Test fun usesThirtyLocalDaysAndKeepsMissingUsageSeparateFromMeasuredCounts() {
        val now = Instant.parse("2026-11-13T12:00:00Z")
        val hour = now.toEpochMilli() / UsageLedger.HOUR_MS
        val snapshot = UsageSnapshot(listOf(
            UsageRow(hour, UsageKey("p", "m", UsagePurpose.Main), UsageCounts(100, 20, 40, 2, 1)),
            UsageRow(hour, UsageKey("p", "m", UsagePurpose.Light), UsageCounts(50, 10, 10, 1, 0)),
            UsageRow(hour - 40 * 24, UsageKey("p", "old", UsagePurpose.Main), UsageCounts(1000, 0)),
        ), mapOf("p" to "Previous name"))
        val data = FlutterUsageSettings.snapshot(snapshot, mapOf("p" to "Step"), now, ZoneOffset.UTC)
        val total = data["totals"] as Map<*, *>
        assertEquals(150L, total["input"])
        assertEquals(30L, total["output"])
        assertEquals(50L, total["cached"])
        assertEquals(1L, total["unreported"])
        assertEquals(3L, total["calls"])
        val model = (data["models"] as List<*>).single() as Map<*, *>
        assertEquals("m", model["model"])
        assertEquals("Step", model["provider"])
    }
}

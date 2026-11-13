package com.daiyimo.maeve.ui

import com.daiyimo.maeve.core.usage.UsageQuery
import com.daiyimo.maeve.core.usage.UsageRange
import com.daiyimo.maeve.core.usage.UsageSnapshot
import com.daiyimo.maeve.core.usage.UsageTotals
import java.time.Instant
import java.time.ZoneId

/** The existing ledger includes retries and auxiliary calls; no billing estimate. */
internal object FlutterUsageSettings {
    fun snapshot(snapshot: UsageSnapshot, live: Map<String, String>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Map<String, Any> {
        val report = UsageQuery.report(snapshot, UsageRange.LastDays(30), now, zone, live)
        return mapOf("days" to 30, "totals" to counts(report.totals), "models" to report.providers.flatMap { p ->
            p.models.map { m -> mapOf("provider" to p.name, "providerId" to p.id, "model" to m.model, "totals" to counts(m.totals)) }
        })
    }
    private fun counts(t: UsageTotals) = mapOf("input" to t.input, "output" to t.output,
        "cached" to t.cached, "calls" to t.calls, "unreported" to t.callsWithoutUsage)
}

package com.daiyimo.maeve.core.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportExportOrdinaryTextTest {

    @Test fun ordinaryReportTextIsNotChanged() {
        val raw = ReportExportInput("查询 <车票> & 时间", body = "G6042 ¥302 https://example.com/?a=1&b=2")
        val safe = ReportExportSanitizer.sanitize(raw, ReportExportRedaction.SecretsAndPii)
        assertEquals(raw.title, safe.title)
        assertEquals(raw.body, safe.body)
        assertEquals(raw.urls, safe.urls)
    }
}

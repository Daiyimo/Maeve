package com.daiyimo.maeve.core.model

import com.daiyimo.maeve.core.net.BoundedResponseReader
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class HttpResponseLimitsTest {
    @Test fun boundsUtf8BytesAndAllowsFinalUnterminatedLine() {
        val reader = BoundedResponseReader(Buffer().writeUtf8("你好\r\nx"), maxLine = 7, maxTotal = 9)
        assertEquals("你好", reader.line()); assertEquals("x", reader.line()); assertNull(reader.line())
        assertThrows(IOException::class.java) { BoundedResponseReader(Buffer().writeUtf8("你好啊"), maxLine = 7).line() }
        val total = BoundedResponseReader(Buffer().writeUtf8("abc\ndef\n"), maxLine = 3, maxTotal = 7)
        assertEquals("abc", total.line()); assertThrows(IOException::class.java) { total.line() }
    }

    @Test fun boundedSseAndNonStreamResponsesFailWithoutSuccess() = runTest {
        MockWebServer().use { server ->
            server.start()
            val request = Request.Builder().url(server.url("/")).build()
            val http = OkHttpClient()
            for (body in listOf("data: ${"x".repeat(256 * 1024)}\n\n", ("data: ${"x".repeat(64 * 1024)}\n").repeat(17) + "\n")) {
                server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
                assertTrue(runCatching { HttpSupport.stream(http, request) { false } }.exceptionOrNull() is ModelError.Network)
            }
            server.enqueue(MockResponse().setChunkedBody("x".repeat(8 * 1024 * 1024 + 1), 8192))
            assertTrue(runCatching { HttpSupport.execute(http, request) }.exceptionOrNull() is ModelError.Network)
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: [DONE]\n\n"))
            var called = 0
            assertEquals(200, HttpSupport.stream(http, request) { called++; true }.status)
            assertEquals(1, called)
        }
    }

    @Test fun retryAfterAcceptsSecondsAndHttpDateAndRejectsInvalidValues() {
        val now = 1_794_700_000_000L
        assertEquals(2500L, HttpSupport.retryAfterMs("2.5", now))
        assertEquals(120_000L, HttpSupport.retryAfterMs("999999999999999999999", now))
        for (value in listOf("-1", "NaN", "Infinity", "-Infinity", "invalid")) assertNull(value, HttpSupport.retryAfterMs(value, now))
        fun date(at: Long) = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneOffset.UTC))
        assertEquals(30_000L, HttpSupport.retryAfterMs(date(now + 30_000), now))
        assertEquals(120_000L, HttpSupport.retryAfterMs(date(now + 3600_000), now))
        assertNull(HttpSupport.retryAfterMs(date(now - 1_000), now))
    }
}

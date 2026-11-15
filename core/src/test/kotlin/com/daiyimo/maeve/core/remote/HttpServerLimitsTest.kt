package com.daiyimo.maeve.core.remote

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class HttpServerLimitsTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private fun parse(headers: String, body: String = "") = MiniHttpServer.readRequest(
        "POST /api/v1/tasks HTTP/1.1\r\n$headers\r\n\r\n$body".byteInputStream(), loopback)

    @Test fun rejectsAmbiguousFramingAndDuplicateSecurityHeaders() {
        listOf(
            "Transfer-Encoding: chunked\r\nContent-Length: 0",
            "Transfer-Encoding: gzip, chunked", "Transfer-Encoding: xchunked",
            "Transfer-Encoding: chunked\r\nTransfer-Encoding: chunked",
            "Content-Length: 0\r\nContent-Length: 0", "Content-Length: +1", "Content-Length: 0, 0",
            "Content-Length : 0", "Authorization: Bearer a\r\nAuthorization: Bearer b",
            "Host: a\r\nHost: b", "X-A: value\r\n folded: value",
        ).forEach { headers -> assertEquals(headers, 400, assertThrows(HttpProtocolException::class.java) { parse(headers) }.status) }
    }

    @Test fun rejectsOversizeBeforeSendingContinue() {
        val output = ByteArrayOutputStream()
        assertEquals(413, assertThrows(HttpProtocolException::class.java) {
            MiniHttpServer.readRequest("POST / HTTP/1.1\r\nContent-Length: 9999999999999999999999\r\nExpect: 100-continue\r\n\r\n".byteInputStream(), loopback, output)
        }.status)
        assertEquals(0, output.size())
    }

    @Test fun enforcesChunkTerminatorsAndBoundedTrailers() {
        listOf("1\nx\n0\n\n", "1\r\nxgarbage\r\n0\r\n\r\n", "+1\r\nx\r\n0\r\n\r\n", "0\r\nContent-Length: 0\r\n\r\n").forEach {
            assertEquals(400, assertThrows(HttpProtocolException::class.java) { parse("Transfer-Encoding: chunked", it) }.status)
        }
        val trailers = (1..60).joinToString("") { "X-$it: ${"a".repeat(100)}\r\n" }
        assertEquals(431, assertThrows(HttpProtocolException::class.java) { parse("Transfer-Encoding: chunked", "0\r\n$trailers\r\n") }.status)
        assertEquals("x", parse("Transfer-Encoding: chunked", "1\r\nx\r\n0\r\nX-Checksum: a\r\n\r\n")!!.body.toString(Charsets.UTF_8))
    }

    @Test fun limitsHeaderCountAndRequestSyntax() {
        assertEquals(431, assertThrows(HttpProtocolException::class.java) { parse((1..65).joinToString("\r\n") { "X-$it: a" }) }.status)
        for (line in listOf("GET / HTTP/1.9", "GET / HTTP/2.0", "GET /bad#fragment HTTP/1.1", "GET http://localhost/ HTTP/1.1")) {
            assertEquals(400, assertThrows(HttpProtocolException::class.java) { MiniHttpServer.readRequest("$line\r\n\r\n".byteInputStream(), loopback) }.status)
        }
    }

    @Test fun trickledBytesCannotExtendTotalRequestDeadline() {
        val handled = AtomicInteger()
        val server = MiniHttpServer(InetSocketAddress(loopback, 0), handler = { handled.incrementAndGet(); HttpResponse.empty(200) }, readTimeoutMs = 300)
        server.start()
        try {
            Socket(loopback, server.port).use { socket ->
                socket.soTimeout = 2_000
                val start = System.nanoTime()
                val writer = Thread {
                    runCatching { repeat(30) { socket.getOutputStream().write('G'.code); socket.getOutputStream().flush(); Thread.sleep(40) } }
                }.apply { isDaemon = true; start() }
                assertEquals(-1, socket.getInputStream().read())
                assertTrue((System.nanoTime() - start) / 1_000_000 < 1500)
                socket.close(); writer.join(500)
                assertEquals(0, handled.get())
            }
        } finally { server.stop() }
    }

    @Test fun perSourceConnectionLimitDoesNotConsumeAllGlobalSlots() {
        val server = MiniHttpServer(InetSocketAddress(loopback, 0), handler = { HttpResponse.empty(200) }, maxConnections = 4, maxPerSource = 1)
        server.start()
        try {
            Socket(loopback, server.port).use { first ->
                first.getOutputStream().write('G'.code)
                // accept loop admits sockets in order, so the second source slot is deterministically occupied.
                Socket(loopback, server.port).use { second ->
                    second.soTimeout = 2000
                    assertTrue(second.getInputStream().bufferedReader().readText().startsWith("HTTP/1.1 429 "))
                }
            }
        } finally { server.stop() }
    }
}

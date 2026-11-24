package com.daiyimo.maeve.cap.web

import com.daiyimo.maeve.core.cap.web.DownloadAddressPolicy
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LocalHttpProxyTest {
    @Test fun plainHttpRewritesRequestAndUsesOneResolvedAddress() {
        val upstream = MockWebServer().apply { enqueue(MockResponse().setBody("ok")); start() }
        val lookups = AtomicInteger()
        val proxy = LocalHttpProxy {
            assertEquals("public.test", it)
            lookups.incrementAndGet()
            listOf(InetAddress.getLoopbackAddress())
        }
        val port = proxy.start()
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
            .build()
        try {
            val response = client.newCall(
                Request.Builder()
                    .url("http://public.test:${upstream.port}/a?b=1")
                    .header("X-Test", "yes")
                    .post("payload".toRequestBody())
                    .build(),
            ).execute()
            assertEquals(200, response.code)
            assertEquals("ok", response.body?.string())
            val recorded = upstream.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/a?b=1", recorded.path)
            assertEquals("public.test:${upstream.port}", recorded.getHeader("Host"))
            assertEquals("yes", recorded.getHeader("X-Test"))
            assertEquals("payload", recorded.body.readUtf8())
            assertEquals(1, lookups.get())
        } finally {
            client.dispatcher.executorService.shutdown()
            proxy.close()
            upstream.shutdown()
        }
    }

    @Test fun blockedAddressGets403WithoutContactingUpstream() {
        val proxy = LocalHttpProxy { throw DownloadAddressPolicy.BlockedAddress(it) }
        val port = proxy.start()
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)))
            .build()
        try {
            val response = client.newCall(Request.Builder().url("http://blocked.test/secret").build()).execute()
            assertEquals(403, response.code)
            response.close()
            assertEquals(1L, proxy.blockedCount)
        } finally {
            client.dispatcher.executorService.shutdown()
            proxy.close()
        }
    }

    @Test fun connectTunnelPinsAddressAndRelaysBytes() {
        val echo = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val echoThread = Thread {
            echo.accept().use { socket ->
                val bytes = ByteArray(5)
                var read = 0
                while (read < bytes.size) {
                    val n = socket.getInputStream().read(bytes, read, bytes.size - read)
                    if (n < 0) break
                    read += n
                }
                socket.getOutputStream().write(bytes, 0, read)
                socket.getOutputStream().flush()
            }
        }.apply { start() }
        val lookups = AtomicInteger()
        val proxy = LocalHttpProxy {
            assertEquals("public.test", it)
            assertTrue(lookups.incrementAndGet() == 1)
            listOf(InetAddress.getLoopbackAddress())
        }
        val port = proxy.start()
        try {
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 5_000
                val out = socket.getOutputStream()
                val request = listOf(
                    "CONNECT public.test:${echo.localPort} HTTP/1.1",
                    "Host: public.test:${echo.localPort}",
                    "",
                ).joinToString("") { it + 13.toChar() + 10.toChar() }
                out.write(request.toByteArray())
                out.flush()
                val reader = BufferedReader(socket.getInputStream().reader(Charsets.ISO_8859_1))
                val status = reader.readLine()
                assertTrue(status, status.contains("200"))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                out.write("hello".toByteArray())
                out.flush()
                val reply = ByteArray(5)
                var read = 0
                while (read < reply.size) {
                    val n = socket.getInputStream().read(reply, read, reply.size - read)
                    if (n < 0) break
                    read += n
                }
                assertEquals("hello", String(reply, 0, read, Charsets.UTF_8))
            }
            echoThread.join(5_000)
            assertEquals(1, lookups.get())
        } finally {
            proxy.close()
            echo.close()
        }
    }

    @Test fun establishedTunnelHasNoHeaderIdleTimeout() {
        val echo = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val echoThread = Thread {
            echo.accept().use { socket ->
                val bytes = ByteArray(5)
                var read = 0
                while (read < bytes.size) {
                    val n = socket.getInputStream().read(bytes, read, bytes.size - read)
                    if (n < 0) break
                    read += n
                }
                socket.getOutputStream().write(bytes, 0, read)
                socket.getOutputStream().flush()
            }
        }.apply { start() }
        val proxy = LocalHttpProxy(headerTimeoutMs = 200, resolve = { listOf(InetAddress.getLoopbackAddress()) })
        proxy.start()
        try {
            Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 3_000
                val out = socket.getOutputStream()
                out.write(listOf("CONNECT public.test:${echo.localPort} HTTP/1.1", "")
                    .joinToString("") { it + 13.toChar() + 10.toChar() }.toByteArray())
                out.flush()
                val reader = BufferedReader(socket.getInputStream().reader(Charsets.ISO_8859_1))
                assertTrue(reader.readLine()!!.contains("200"))
                while (reader.readLine()!!.isNotEmpty()) { }
                Thread.sleep(600)
                out.write("hello".toByteArray())
                out.flush()
                val reply = ByteArray(5)
                var read = 0
                while (read < reply.size) {
                    val n = socket.getInputStream().read(reply, read, reply.size - read)
                    if (n < 0) break
                    read += n
                }
                assertEquals("hello", String(reply, 0, read, Charsets.UTF_8))
            }
            echoThread.join(5_000)
        } finally {
            proxy.close()
            echo.close()
        }
    }

    @Test fun plainWebSocketUpgradeKeepsConnectionAndUpgradeHeaders() {
        val upstream = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val seen = AtomicReference<String>()
        val serverThread = Thread {
            upstream.accept().use { socket ->
                val reader = BufferedReader(socket.getInputStream().reader(Charsets.ISO_8859_1))
                val headers = buildString {
                    while (true) {
                        val line = reader.readLine() ?: break
                        append(line).append(10.toChar())
                        if (line.isEmpty()) break
                    }
                }
                seen.set(headers)
                val response = listOf(
                    "HTTP/1.1 101 Switching Protocols", "Upgrade: websocket", "Connection: Upgrade", "",
                ).joinToString("") { it + 13.toChar() + 10.toChar() }
                socket.getOutputStream().write(response.toByteArray())
                socket.getOutputStream().flush()
                val bytes = ByteArray(5)
                var read = 0
                while (read < bytes.size) {
                    val n = socket.getInputStream().read(bytes, read, bytes.size - read)
                    if (n < 0) break
                    read += n
                }
                socket.getOutputStream().write(bytes, 0, read)
                socket.getOutputStream().flush()
            }
        }.apply { start() }
        val proxy = LocalHttpProxy(resolve = { listOf(InetAddress.getLoopbackAddress()) })
        proxy.start()
        try {
            Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 3_000
                val out = socket.getOutputStream()
                out.write(listOf(
                    "GET http://public.test:${upstream.localPort}/socket HTTP/1.1",
                    "Host: public.test:${upstream.localPort}", "Upgrade: websocket", "Connection: Upgrade", "",
                ).joinToString("") { it + 13.toChar() + 10.toChar() }.toByteArray())
                out.flush()
                val reader = BufferedReader(socket.getInputStream().reader(Charsets.ISO_8859_1))
                assertTrue(reader.readLine()!!.contains("101"))
                while (reader.readLine()!!.isNotEmpty()) { }
                out.write("hello".toByteArray())
                out.flush()
                val reply = ByteArray(5)
                var read = 0
                while (read < reply.size) {
                    val n = socket.getInputStream().read(reply, read, reply.size - read)
                    if (n < 0) break
                    read += n
                }
                assertEquals("hello", String(reply, 0, read, Charsets.UTF_8))
            }
            serverThread.join(5_000)
            val headers = seen.get()!!.lowercase()
            assertTrue(headers.contains("connection: upgrade"))
            assertTrue(headers.contains("upgrade: websocket"))
            assertTrue(!headers.contains("connection: close"))
        } finally {
            proxy.close()
            upstream.close()
        }
    }
}

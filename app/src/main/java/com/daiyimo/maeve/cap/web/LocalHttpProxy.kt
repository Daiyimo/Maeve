package com.daiyimo.maeve.cap.web

import android.util.Log
import com.daiyimo.maeve.core.cap.web.DownloadAddressPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 只监听回环的 HTTP 代理。WebView 通过 HTTP CONNECT 走 HTTPS 时,这里先校验并固定 DNS 答案,
 * 再盲转发 TLS;明文 HTTP 则重写请求行后转发。检查与建连使用同一批地址,不给 DNS 重绑定留窗口。
 */
internal class LocalHttpProxy(
    private val headerTimeoutMs: Long = HEAD_TIMEOUT_MS,
    private val resolve: (String) -> List<InetAddress> = DownloadAddressPolicy::allowedAddresses,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sockets = ConcurrentHashMap<Socket, Unit>()
    private val lock = Any()
    private lateinit var server: ServerSocket
    private val stopped = AtomicBoolean(false)
    private val accepted = AtomicLong()
    private val blocked = AtomicLong()
    private val active = AtomicInteger()

    val port: Int get() = server.localPort
    val connectionCount: Long get() = accepted.get()
    val blockedCount: Long get() = blocked.get()
    val activeCount: Int get() = active.get()

    @Synchronized
    fun start(): Int {
        check(!stopped.get()) { "proxy already closed" }
        if (::server.isInitialized) return server.localPort
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        scope.launch {
            while (isActive && !stopped.get()) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    if (stopped.get() || server.isClosed) break
                    Log.w(TAG, "proxy accept failed", e)
                    delay(100)
                    continue
                }
                synchronized(lock) {
                    if (stopped.get()) {
                        runCatching { client.close() }
                    } else {
                        accepted.incrementAndGet()
                        active.incrementAndGet()
                        sockets[client] = Unit
                        scope.launch { handle(client) }
                    }
                }
            }
        }
        return server.localPort
    }

    @Synchronized
    fun close() {
        synchronized(lock) {
            if (!stopped.compareAndSet(false, true)) return
            runCatching { server.close() }
            sockets.keys.forEach { runCatching { it.close() } }
            sockets.clear()
        }
    }

    private suspend fun handle(client: Socket) {
        try {
            client.soTimeout = headerTimeoutMs.toInt()
            val head = readHead(client.getInputStream()) ?: throw IOException("missing proxy request")
            client.soTimeout = 0
            if (head.method == "CONNECT") handleConnect(client, head) else handleHttp(client, head)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is IOException && e !is UnknownHostException) Log.w(TAG, "proxy request failed", e)
        } finally {
            sockets.remove(client)
            runCatching { client.close() }
            active.decrementAndGet()
        }
    }

    private suspend fun handleConnect(client: Socket, head: RequestHead) {
        val target = parseAuthority(head.target)
        val upstream = try {
            connectAllowed(target.host, target.port)
        } catch (_: DownloadAddressPolicy.BlockedAddress) {
            blocked.incrementAndGet(); sendError(client, 403, "Blocked address"); return
        } catch (_: UnknownHostException) {
            blocked.incrementAndGet(); sendError(client, 403, "Blocked address"); return
        } catch (_: IOException) {
            sendError(client, 502, "Upstream connection failed"); return
        }
        sockets[upstream] = Unit
        try {
            client.getOutputStream().write(wire("HTTP/1.1 200 Connection Established", "").toByteArray(Charsets.ISO_8859_1))
            client.getOutputStream().flush()
            relay(client, upstream)
        } finally {
            sockets.remove(upstream)
            runCatching { upstream.close() }
        }
    }

    private suspend fun handleHttp(client: Socket, head: RequestHead) {
        val uri = try {
            URI(head.target)
        } catch (_: Exception) {
            sendError(client, 400, "Bad request"); return
        }
        if (!head.version.startsWith("HTTP/1.") || !uri.scheme.equals("http", true) ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null) {
            sendError(client, 400, "Only absolute HTTP requests are supported"); return
        }
        val host = uri.host
        val port = uri.port.takeIf { it > 0 } ?: 80
        if (port !in 1..65535) {
            sendError(client, 400, "Bad port"); return
        }
        val authority = if (uri.port in 1..65535) "$host:${uri.port}" else host
        val path = buildString {
            append(uri.rawPath?.takeIf(String::isNotEmpty) ?: "/")
            uri.rawQuery?.let { append('?').append(it) }
        }
        val upstream = try {
            connectAllowed(host, port)
        } catch (_: DownloadAddressPolicy.BlockedAddress) {
            blocked.incrementAndGet(); sendError(client, 403, "Blocked address"); return
        } catch (_: UnknownHostException) {
            blocked.incrementAndGet(); sendError(client, 403, "Blocked address"); return
        } catch (_: IOException) {
            sendError(client, 502, "Upstream connection failed"); return
        }
        sockets[upstream] = Unit
        try {
            val out = upstream.getOutputStream()
            out.write(wire("${head.method} $path ${head.version}").toByteArray(Charsets.ISO_8859_1))
            val upgrade = head.headers.any { (name, value) -> name.equals("upgrade", true) && value.isNotBlank() } &&
                head.headers.any { (name, value) -> name.equals("connection", true) &&
                    value.split(',').any { it.trim().equals("upgrade", true) } }
            head.headers.filterNot { (name, _) ->
                name.equals("host", true) || (!upgrade && name.equals("connection", true)) ||
                    name.equals("proxy-connection", true) || name.equals("proxy-authorization", true)
            }.forEach { (name, value) ->
                out.write(wire("$name: $value").toByteArray(Charsets.ISO_8859_1))
            }
            out.write(wire("Host: $authority").toByteArray(Charsets.ISO_8859_1))
            if (!upgrade) out.write(wire("Connection: close").toByteArray(Charsets.ISO_8859_1))
            out.write(wire("").toByteArray(Charsets.ISO_8859_1))
            out.flush()
            relay(client, upstream)
        } finally {
            sockets.remove(upstream)
            runCatching { upstream.close() }
        }
    }

    private fun connectAllowed(host: String, port: Int): Socket {
        val addresses = resolve(host)
        if (addresses.isEmpty()) throw UnknownHostException(host)
        var last: IOException? = null
        for (address in addresses) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = 0
                return socket
            } catch (e: IOException) {
                last = e
                runCatching { socket.close() }
            }
        }
        throw last ?: IOException("no validated address for $host")
    }

    private suspend fun relay(client: Socket, upstream: Socket) = coroutineScope {
        val upload = launch(Dispatchers.IO) { pump(client.getInputStream(), upstream.getOutputStream()) }
        val download = launch(Dispatchers.IO) { pump(upstream.getInputStream(), client.getOutputStream()) }
        select<Unit> {
            upload.onJoin {}
            download.onJoin {}
        }
        runCatching { client.close() }
        runCatching { upstream.close() }
        coroutineContext.cancelChildren()
    }

    private fun pump(input: InputStream, output: OutputStream) {
        try {
            input.copyTo(output)
            output.flush()
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // The peer closed one direction; the other direction and socket cleanup still finish.
        }
    }

    private fun sendError(client: Socket, status: Int, reason: String) {
        runCatching {
            val body = wire(reason).toByteArray()
            val out = client.getOutputStream()
            out.write(wire(
                "HTTP/1.1 $status $reason",
                "Content-Type: text/plain; charset=utf-8",
                "Content-Length: ${body.size}",
                "Connection: close",
                "",
            ).toByteArray())
            out.write(body)
            out.flush()
        }
    }

    private data class RequestHead(
        val method: String,
        val target: String,
        val version: String,
        val headers: List<Pair<String, String>>,
    )

    private data class Authority(val host: String, val port: Int)

    private fun parseAuthority(raw: String): Authority {
        val authority = raw.trim()
        val host: String
        val portText: String
        if (authority.startsWith('[')) {
            val end = authority.indexOf(']')
            if (end <= 1 || end + 1 >= authority.length || authority[end + 1] != ':') throw IOException("bad CONNECT authority")
            host = authority.substring(1, end)
            portText = authority.substring(end + 2)
        } else {
            val colon = authority.lastIndexOf(':')
            if (colon <= 0 || authority.substring(0, colon).contains(':')) throw IOException("bad CONNECT authority")
            host = authority.substring(0, colon)
            portText = authority.substring(colon + 1)
        }
        if (host.contains('@') || host.isBlank()) throw IOException("bad CONNECT host")
        val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw IOException("bad CONNECT port")
        return Authority(host, port)
    }

    private fun readHead(input: InputStream): RequestHead? {
        val requestLine = readLine(input, MAX_LINE) ?: return null
        val parts = requestLine.split(Regex(" +"))
        if (parts.size != 3 || parts[0].isEmpty() || parts[1].isEmpty() ||
            !parts[0].all { it.code in 33..126 } || !parts[2].startsWith("HTTP/1.")) {
            throw IOException("bad proxy request line")
        }
        val headers = mutableListOf<Pair<String, String>>()
        var total = requestLine.length
        while (true) {
            val line = readLine(input, MAX_LINE) ?: throw EOFException("truncated proxy headers")
            if (line.isEmpty()) break
            total += line.length
            if (total > MAX_HEADERS || headers.size >= MAX_HEADER_COUNT) throw IOException("proxy headers too large")
            val colon = line.indexOf(':')
            if (colon <= 0) throw IOException("bad proxy header")
            val name = line.substring(0, colon).trim()
            if (!name.all { it.code in 33..126 }) throw IOException("bad proxy header name")
            headers += name to line.substring(colon + 1).trim()
        }
        return RequestHead(parts[0].uppercase(), parts[1], parts[2], headers)
    }

    private fun readLine(input: InputStream, limit: Int): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) {
                if (bytes.size() == 0) return null
                throw EOFException("truncated proxy line")
            }
            if (value == 10) {
                if (bytes.size() > 0 && bytes.toByteArray().last() == 13.toByte()) {
                    return String(bytes.toByteArray().dropLast(1).toByteArray(), Charsets.ISO_8859_1)
                }
                return String(bytes.toByteArray(), Charsets.ISO_8859_1)
            }
            if (bytes.size() >= limit) throw IOException("proxy line too large")
            bytes.write(value)
        }
    }

    private fun wire(vararg lines: String): String = lines.joinToString("") { it + 13.toChar() + 10.toChar() }

    companion object {
        private const val TAG = "MaeveWebProxy"
        private const val HEAD_TIMEOUT_MS = 15_000L
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val MAX_LINE = 8 * 1024
        private const val MAX_HEADERS = 64 * 1024
        private const val MAX_HEADER_COUNT = 100
    }
}

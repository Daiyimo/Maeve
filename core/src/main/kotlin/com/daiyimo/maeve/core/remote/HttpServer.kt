package com.daiyimo.maeve.core.remote

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.FilterInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** 一次 HTTP 请求。头名一律小写;[remote] 是对方的地址(限流、近期连接按它算)。 */
class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: ByteArray,
    val remote: InetAddress,
    val query: String = "",
    /** 失败冷却按它记。本地监听就是对方地址;经网关转发的是网关看到的调用方地址,与本地地址分开记。 */
    val source: String = remote.hostAddress ?: "?",
) {
    fun header(name: String): String? = headers[name.lowercase()]
}

class HttpResponse(val status: Int, val body: ByteArray = ByteArray(0), val headers: Map<String, String> = emptyMap()) {
    companion object {
        fun json(status: Int, text: String, extra: Map<String, String> = emptyMap()) =
            HttpResponse(status, text.toByteArray(Charsets.UTF_8), mapOf("Content-Type" to "application/json") + extra)

        fun empty(status: Int, extra: Map<String, String> = emptyMap()) = HttpResponse(status, ByteArray(0), extra)
    }
}

/** 请求本身不合 HTTP 规矩(太大、格式不对):直接回 [status] 并断开。 */
class HttpProtocolException(val status: Int, message: String) : IOException(message)

/**
 * 只为电脑连接用的极简 HTTP/1.1 服务:一个端口、每个连接一个线程、连接数有上限,请求头和正文都有大小上限。
 * 只认 Content-Length 和 chunked 两种正文;不做 TLS(只在局域网和私有网络里用,靠访问令牌把关)。
 * [accept] 按两端地址决定收不收这个连接(不收就当场关掉,不回任何字节)。
 *
 * 风险(P1):全程明文 HTTP,Bearer 令牌与请求体同网段可被嗅探后完整接管(含 shell 命令)。
 * relay 出路是 TLS-only,局域网这条不是。缓解:令牌 32 字节 SecureRandom + 失败冷却 + 私有地址过滤;
 * 文档已明示「只在可信局域网/私有网络里用」。彻底修复需首次 pairing 走 TLS 或短期令牌,留待后续。
 */
class MiniHttpServer(
    private val bind: InetSocketAddress,
    private val handler: (HttpRequest) -> HttpResponse,
    private val accept: (local: InetAddress, remote: InetAddress) -> Boolean = { _, _ -> true },
    private val maxConnections: Int = 16,
    private val readTimeoutMs: Int = 30_000,
    private val onError: (Throwable) -> Unit = {},
    private val maxPerSource: Int = 4,
) {
    init { require(maxConnections > 0 && maxPerSource > 0 && readTimeoutMs > 0) }
    private val running = AtomicBoolean(false)
    private val slots = Semaphore(maxConnections)
    private val open = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Socket, Boolean>())
    private val sources = mutableMapOf<InetAddress, Int>()
    private fun sourceSlot(address: InetAddress): Boolean = synchronized(sources) {
        val count = sources[address] ?: 0
        if (count >= maxPerSource) false else { sources[address] = count + 1; true }
    }
    private fun releaseSource(address: InetAddress) = synchronized(sources) {
        val count = (sources[address] ?: 1) - 1
        if (count <= 0) sources.remove(address) else sources[address] = count
    }
    @Volatile private var server: ServerSocket? = null

    /** 实际监听的端口(给 0 时由系统挑)。 */
    val port: Int get() = server?.localPort ?: -1

    /** 绑定端口并开始收连接;绑定失败抛出异常(端口被占等)。 */
    @Synchronized fun start() {
        check(!running.get()) { "already running" }
        val s = ServerSocket()
        s.reuseAddress = true
        try { s.bind(bind, 32) } catch (e: IOException) { s.close(); throw e }
        server = s
        running.set(true)
        Thread({ acceptLoop(s) }, "remote-accept").apply { isDaemon = true }.start()
    }

    @Synchronized fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        open.forEach { runCatching { it.close() } }
        open.clear()
    }

    private fun acceptLoop(s: ServerSocket) {
        while (running.get()) {
            val socket = try { s.accept() } catch (e: IOException) { if (running.get()) onError(e); break }
            if (!accept(socket.localAddress, socket.inetAddress)) { runCatching { socket.close() }; continue }
            if (!slots.tryAcquire()) {
                runCatching { socket.soTimeout = 2_000; write(socket.getOutputStream(), HttpResponse.empty(503), close = true); socket.close() }
                continue
            }
            if (!sourceSlot(socket.inetAddress)) {
                slots.release()
                runCatching { write(socket.getOutputStream(), HttpResponse.empty(429), close = true) }
                runCatching { socket.close() }
                continue
            }
            open += socket
            Thread({
                try { serve(socket) } finally {
                    open -= socket
                    runCatching { socket.close() }
                    slots.release()
                    releaseSource(socket.inetAddress)
                }
            }, "remote-conn").apply { isDaemon = true }.start()
        }
    }

    private fun serve(socket: Socket) {
        try {
            socket.soTimeout = readTimeoutMs
            socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            var served = 0
            while (running.get()) {
                val request = try { readRequest(RequestDeadline(input, socket, readTimeoutMs), socket.inetAddress, output) ?: return } catch (e: HttpProtocolException) {
                    write(output, HttpResponse.empty(e.status), close = true)
                    return
                }
                val response = try { handler(request) } catch (e: Exception) { onError(e); HttpResponse.empty(500) }
                val close = ++served >= 100 || request.header("connection")?.split(',')
                    ?.any { it.trim().equals("close", ignoreCase = true) } == true
                write(output, response, close)
                if (close) return
            }
        } catch (_: SocketException) {
        } catch (_: java.net.SocketTimeoutException) {
        } catch (e: IOException) {
            onError(e)
        }
    }

    /** 每次请求的总读取期限；持续滴入字节不能无限占住连接。 */
    private class RequestDeadline(input: InputStream, private val socket: Socket, timeoutMs: Int) : FilterInputStream(input) {
        private val until = System.nanoTime() + timeoutMs.toLong() * 1_000_000
        private fun checkDeadline() {
            val nanos = until - System.nanoTime()
            if (nanos <= 0) throw java.net.SocketTimeoutException("request deadline")
            socket.soTimeout = ((nanos + 999_999) / 1_000_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        override fun read(): Int { checkDeadline(); return `in`.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int { checkDeadline(); return `in`.read(b, off, len) }
    }

    companion object {
        const val MAX_HEAD = 16 * 1024
        const val MAX_BODY = 256 * 1024
        const val MAX_HEADERS = 64
        const val MAX_TRAILERS = 4096
        private val TOKEN = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")

        /**
         * 读一个请求;对方在请求开始前关了连接时返回 null。对方先问能不能发正文(`Expect: 100-continue`,
         * 命令行工具发稍大的正文时会这样)而给了 [output] 时,先答一句「继续」,免得它干等。
         */
        fun readRequest(input: InputStream, remote: InetAddress, output: OutputStream? = null): HttpRequest? {
            val head = readHead(input) ?: return null
            val lines = head.split("\r\n")
            val parts = lines.first().split(' ')
            if (parts.size != 3 || parts[2] !in setOf("HTTP/1.0", "HTTP/1.1") || !TOKEN.matches(parts[0]) ||
                !parts[1].startsWith('/') || parts[1].any { it.code <= 32 || it.code >= 127 || it == '#' }) throw HttpProtocolException(400, "bad request line")
            val headers = HashMap<String, String>()
            if (lines.size - 1 > MAX_HEADERS) throw HttpProtocolException(431, "too many headers")
            for (line in lines.drop(1)) {
                if (line.isEmpty()) continue
                val colon = line.indexOf(':')
                if (colon <= 0) throw HttpProtocolException(400, "bad header")
                val rawName = line.substring(0, colon)
                if (!TOKEN.matches(rawName) || line.any { it.code < 32 && it != '\t' || it.code == 127 }) throw HttpProtocolException(400, "bad header")
                val name = rawName.lowercase()
                val value = line.substring(colon + 1).trim()
                // 同名头合并;Content-Length 重复且不同时按坏请求
                headers[name] = headers[name]?.let { old ->
                    if (name in setOf("content-length", "transfer-encoding", "host", "authorization")) throw HttpProtocolException(400, "duplicate header")
                    "$old, $value"
                } ?: value
            }
            val transfer = headers["transfer-encoding"]
            if (transfer != null && (headers.containsKey("content-length") || !transfer.equals("chunked", true))) throw HttpProtocolException(400, "ambiguous transfer encoding")
            val length = headers["content-length"]?.let { value ->
                if (!value.matches(Regex("[0-9]+"))) throw HttpProtocolException(400, "bad length")
                val n = value.toLongOrNull() ?: throw HttpProtocolException(413, "body too large")
                if (n > MAX_BODY) throw HttpProtocolException(413, "body too large")
                n.toInt()
            }
            if (headers["expect"] != null && !headers["expect"].equals("100-continue", true)) throw HttpProtocolException(417, "unsupported expectation")
            if (output != null && headers["expect"].equals("100-continue", ignoreCase = true)) {
                output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                output.flush()
            }
            val body = when {
                transfer != null -> readChunked(input)
                length != null -> readExactly(input, length)
                else -> ByteArray(0)
            }
            val target = parts[1]
            return HttpRequest(parts[0], target.substringBefore('?'), headers, body, remote, target.substringAfter('?', ""))
        }

        private fun readHead(input: InputStream): String? {
            val buf = ByteArrayOutputStream()
            var matched = 0
            val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
            while (matched < 4) {
                val b = input.read()
                if (b < 0) {
                    if (buf.size() == 0) return null
                    throw HttpProtocolException(400, "truncated head")
                }
                // 两个请求之间多出来的空行忽略
                buf.write(b)
                if (buf.size() > MAX_HEAD) throw HttpProtocolException(431, "head too large")
                matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
            }
            return String(buf.toByteArray(), 0, buf.size() - 4, Charsets.ISO_8859_1)
        }

        private fun readExactly(input: InputStream, n: Int): ByteArray {
            val out = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = input.read(out, off, n - off)
                if (r < 0) throw HttpProtocolException(400, "truncated body")
                off += r
            }
            return out
        }

        private fun readLine(input: InputStream): String {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) throw HttpProtocolException(400, "truncated chunk")
                if (b == '\r'.code) {
                    if (input.read() != '\n'.code) throw HttpProtocolException(400, "bad chunk line ending")
                    return sb.toString()
                }
                if (b == '\n'.code) throw HttpProtocolException(400, "bad chunk line ending")
                sb.append(b.toChar())
                if (sb.length > 1024) throw HttpProtocolException(400, "chunk line too long")
            }
        }

        private fun readChunked(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            while (true) {
                val token = readLine(input).substringBefore(';')
                if (!token.matches(Regex("[0-9a-fA-F]+"))) throw HttpProtocolException(400, "bad chunk")
                val size = token.toLongOrNull(16) ?: throw HttpProtocolException(413, "body too large")
                // 按剩余额度检查,避免相加溢出后放行超大分块并尝试分配内存。
                if (size < 0 || size > MAX_BODY - out.size()) throw HttpProtocolException(413, "body too large")
                if (size == 0L) {
                    var bytes = 0
                    var count = 0
                    while (true) {
                        val trailer = readLine(input)
                        bytes += trailer.length + 2
                        if (bytes > MAX_TRAILERS || ++count > MAX_HEADERS) throw HttpProtocolException(431, "trailers too large")
                        if (trailer.isEmpty()) break
                        val name = trailer.substringBefore(':')
                        if (':' !in trailer || !TOKEN.matches(name) || name.lowercase() in setOf("content-length", "transfer-encoding", "host", "authorization") ||
                            trailer.any { it.code < 32 && it != '\t' || it.code == 127 }) throw HttpProtocolException(400, "bad trailer")
                    }
                    return out.toByteArray()
                }
                out.write(readExactly(input, size.toInt()))
                if (readLine(input).isNotEmpty()) throw HttpProtocolException(400, "bad chunk delimiter")
            }
        }

        private val REASONS = mapOf(
            200 to "OK", 201 to "Created", 202 to "Accepted", 400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden",
            404 to "Not Found", 405 to "Method Not Allowed", 406 to "Not Acceptable", 409 to "Conflict", 413 to "Payload Too Large",
            415 to "Unsupported Media Type", 429 to "Too Many Requests", 431 to "Request Header Fields Too Large",
            500 to "Internal Server Error", 503 to "Service Unavailable",
        )

        fun write(output: OutputStream, r: HttpResponse, close: Boolean) {
            val head = StringBuilder("HTTP/1.1 ${r.status} ${REASONS[r.status] ?: "Status"}\r\n")
            r.headers.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
            head.append("Content-Length: ").append(r.body.size).append("\r\n")
            head.append("Cache-Control: no-store\r\n")
            if (close) head.append("Connection: close\r\n")
            head.append("\r\n")
            output.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            output.write(r.body)
            output.flush()
        }
    }
}

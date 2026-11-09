package com.daiyimo.maeve.core.adb

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import kotlin.concurrent.thread

class AdbConnectionException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason {
        /** 目标不是本机自己的地址:建 Socket 前拒绝,不能按网络故障重试。 */
        NOT_LOCAL,
        CONNECT_FAILED,
        TLS_FAILED,
        /** 设备不认这把钥匙(没配对、被撤销或授权过期),要重新配对。 */
        UNAUTHORIZED,
        PROTOCOL,
    }
}

/**
 * 到 adbd 的一条连接。握手:发 CNXN → 设备回 STLS → 回 STLS 并以已配对的证书做 TLS 1.3 →
 * 设备在 TLS 上发 CNXN(横幅)。之后在其上多路复用流:OPEN → OKAY,WRTE 每包都要等对方 OKAY。
 * 不声明任何 feature(不用 delayed ack),OPEN 的 arg1 因此为 0。
 * 一条流的对端流号要等设备回第一个 OKAY 才知道;读循环按 arg1 把包派发给对应的流。
 */
class AdbConnection private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: OutputStream,
    /** 设备横幅,如 `device::ro.product.name=…;features=…`。 */
    val banner: String,
    private val peerVersion: Int,
    private val peerMaxData: Int,
    private val timeoutMs: Int,
) : Closeable {
    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val nextId = AtomicInteger(1)
    @Volatile private var closed = false

    private val reader = thread(isDaemon = true, name = "adb-reader") { pump() }

    val isClosed: Boolean get() = closed

    /** 运行 `exec:` 服务:命令的原始标准输出(不经 pty),写入的数据进它的标准输入。 */
    fun exec(command: String): AdbStream = open("exec:$command")

    fun open(destination: String): AdbStream {
        if (closed) throw IOException("adb connection closed")
        val stream = register(destination)
        if (!awaitGranted(stream, destination)) {
            streams.remove(stream.localId)
            throw IOException("device refused '$destination'")
        }
        return stream
    }

    /** 等设备答复 OPEN:超时(或等的过程出错)撤掉这个流并抛;答复为 false 是设备拒绝。 */
    private fun awaitGranted(stream: AdbStream, destination: String): Boolean = try {
        stream.opened.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
    } catch (e: Exception) {
        streams.remove(stream.localId)
        throw IOException("open '$destination' timed out", e)
    }

    /** 登记一条新流并把 OPEN 发出去;设备那头的结果由读循环补全([AdbStream.opened])。 */
    private fun register(destination: String): AdbStream {
        val stream = AdbStream(nextId.getAndIncrement())
        streams[stream.localId] = stream
        send(AdbMessage(AdbMessage.OPEN, stream.localId, 0, (destination + "\u0000").toByteArray(Charsets.UTF_8)))
        return stream
    }

    override fun close() {
        closed = true
        runCatching { socket.close() }
        streams.values.forEach { it.endOfStream() }
        streams.clear()
    }

    private fun send(m: AdbMessage) {
        val bytes = m.encode()
        synchronized(output) {
            output.write(bytes)
            output.flush()
        }
    }

    /** 读循环:一个包一个包地读,读完或出错就把整条连接关掉。 */
    private fun pump() {
        try {
            while (!closed) {
                val m = nextPacket() ?: break
                deliver(m)
            }
        } catch (_: IOException) {
        } finally {
            close()
        }
    }

    /** 读下一个包;对端关了返回 null。老协议没有校验和字段,按对端版本决定跳过不跳过。 */
    private fun nextPacket(): AdbMessage? =
        AdbMessage.read(input, AdbMessage.MAX_PAYLOAD, peerVersion < AdbMessage.VERSION_SKIP_CHECKSUM)

    /** 按命令把包交给对应的流;连接建立后的 CNXN/AUTH 等不处理。 */
    private fun deliver(m: AdbMessage) {
        val stream = streams[m.arg1] ?: return
        when (m.command) {
            AdbMessage.OKAY -> stream.onOkay(m.arg0)
            AdbMessage.WRTE -> stream.onWrite(m.data)
            AdbMessage.CLSE -> drop(stream)
            else -> Unit
        }
    }

    /** 设备关了流:从表里撤掉,等开的 future 落空,让读写双方都醒来。 */
    private fun drop(stream: AdbStream) {
        streams.remove(stream.localId)
        stream.opened.complete(false)
        stream.endOfStream()
    }

    /** 一条流。[read] 阻塞到有数据,流结束返回 null。 */
    inner class AdbStream internal constructor(val localId: Int) : Closeable {
        @Volatile internal var remoteId = 0
        internal val opened = CompletableFuture<Boolean>()
        internal val acks = Semaphore(0)
        internal val incoming = LinkedBlockingQueue<ByteArray>()
        @Volatile private var remoteGone = false
        @Volatile private var localClosed = false

        fun read(): ByteArray? {
            val b = incoming.take()
            if (b === EOF) {
                incoming.put(EOF)
                return null
            }
            return b
        }

        /** 读到对方关闭为止。 */
        fun readAll(): ByteArray {
            val out = ByteArrayOutputStream()
            while (true) out.write(read() ?: return out.toByteArray())
        }

        fun readText(): String = readAll().toString(Charsets.UTF_8)

        fun write(data: ByteArray) {
            var sent = 0
            while (sent < data.size) {
                ensureOpen()
                val n = minOf(peerMaxData, data.size - sent)
                sendChunk(data, sent, n)
                sent += n
            }
        }

        /** 发一段(不超过对端 maxdata)并等它的 OKAY;等不到就是这个流断了。 */
        private fun sendChunk(data: ByteArray, from: Int, n: Int) {
            send(AdbMessage(AdbMessage.WRTE, localId, remoteId, data.copyOfRange(from, from + n)))
            if (!acks.tryAcquire(timeoutMs.toLong(), TimeUnit.MILLISECONDS)) throw IOException("write not acknowledged")
            ensureOpen()
        }

        private fun ensureOpen() {
            if (remoteGone || localClosed) throw IOException("stream closed")
        }

        val inputStream: InputStream get() = Feed()

        /** 把这条流包成标准 InputStream:按块取,流结束给 -1。 */
        private inner class Feed : InputStream() {
            private var buf = ByteArray(0)
            private var off = 0
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
            }
            override fun read(b: ByteArray, o: Int, len: Int): Int {
                if (len == 0) return 0
                while (off >= buf.size) {
                    buf = this@AdbStream.read() ?: return -1
                    off = 0
                }
                val n = minOf(len, buf.size - off)
                System.arraycopy(buf, off, b, o, n)
                off += n
                return n
            }
        }

        /** 设备答复了 OPEN:记下它那侧的流号,开流才算成。 */
        internal fun confirmOpen(remote: Int) {
            remoteId = remote
            opened.complete(true)
        }

        /** 设备回了 OKAY:刚开流时是答复 OPEN,之后每回放行一个等着的写。 */
        internal fun onOkay(remote: Int) {
            if (remoteId == 0) confirmOpen(remote) else acks.release()
        }

        /** 设备来了数据:排进 incoming,并回一个 OKAY(每个 WRTE 都要回)。 */
        internal fun onWrite(data: ByteArray) {
            incoming.put(data)
            send(AdbMessage(AdbMessage.OKAY, localId, remoteId))
        }

        /** 流到头:设备那头关了、整条连接没了、或本地主动关,都走这里——读给 EOF、等开的落空、等写的放行。 */
        internal fun endOfStream() {
            remoteGone = true
            opened.complete(false)
            acks.release()
            incoming.put(EOF)
        }

        override fun close() {
            if (localClosed) return
            localClosed = true
            if (!remoteGone && !closed) runCatching { send(AdbMessage(AdbMessage.CLSE, localId, remoteId)) }
            streams.remove(localId)
            endOfStream()
        }
    }

    companion object {
        private val EOF = ByteArray(0)
        private val HOST_BANNER = "host::\u0000".toByteArray(Charsets.US_ASCII)

        /** 连上本机 [host]:[port] 的 adbd。校验结果同时就是实际连接地址,不再解析原 host。 */
        fun open(host: String, port: Int, tls: AdbTls, timeoutMs: Int = 10_000): AdbConnection {
            val target = AdbHosts.ownSocketAddress(host, port) ?: throw AdbConnectionException(
                AdbConnectionException.Reason.NOT_LOCAL,
                "refusing to connect to $host: adb is only reachable on this device",
            )
            val plain = dial(target, host, port, timeoutMs)
            return try {
                handshake(plain, target, tls, timeoutMs)
            } catch (e: Throwable) {
                runCatching { plain.close() }
                throw e
            }
        }

        private fun dial(target: InetSocketAddress, host: String, port: Int, timeoutMs: Int): Socket {
            val plain = Socket()
            return try {
                plain.tcpNoDelay = true
                plain.connect(target, timeoutMs)
                plain.soTimeout = timeoutMs
                plain
            } catch (e: IOException) {
                runCatching { plain.close() }
                throw AdbConnectionException(AdbConnectionException.Reason.CONNECT_FAILED, "cannot connect to $host:$port", e)
            }
        }

        private fun handshake(plain: Socket, target: InetSocketAddress, tls: AdbTls, timeoutMs: Int): AdbConnection {
            val out = plain.getOutputStream()
            out.write(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, HOST_BANNER).encode())
            out.flush()
            val first = AdbMessage.read(DataInputStream(plain.getInputStream()))
                ?: throw AdbConnectionException(AdbConnectionException.Reason.PROTOCOL, "device closed the connection")
            return when (first.command) {
                AdbMessage.CNXN -> finish(plain, DataInputStream(plain.getInputStream()), out, first, timeoutMs)
                AdbMessage.STLS -> upgrade(plain, target, tls, timeoutMs)
                AdbMessage.AUTH -> throw AdbConnectionException(AdbConnectionException.Reason.UNAUTHORIZED, "device asks for legacy RSA auth (not a wireless-debugging port?)")
                else -> throw AdbConnectionException(AdbConnectionException.Reason.PROTOCOL, "unexpected $first")
            }
        }

        /** 设备要求 TLS:回 STLS,把连接升级成 TLS 1.3,再等它在 TLS 上发横幅。 */
        private fun upgrade(plain: Socket, target: InetSocketAddress, tls: AdbTls, timeoutMs: Int): AdbConnection {
            val out = plain.getOutputStream()
            out.write(AdbMessage(AdbMessage.STLS, AdbMessage.STLS_VERSION, 0).encode())
            out.flush()
            val ssl = try {
                tls.wrap(plain, target, client = true)
            } catch (e: IOException) {
                throw AdbConnectionException(AdbConnectionException.Reason.TLS_FAILED, "TLS handshake failed", e)
            }
            val input = DataInputStream(ssl.inputStream)
            // TLS 1.3 里设备拒绝客户端证书发生在握手之后,表现为读第一个包时出错或断开。
            // 客户端分不清是 adbd 不认这把钥匙,还是另一个服务接住了这次握手,由调用方再试(见 AdbConnectPort)
            val cnxn = try {
                AdbMessage.read(input)
            } catch (e: SSLException) {
                throw AdbConnectionException(AdbConnectionException.Reason.UNAUTHORIZED, "TLS rejected our certificate (key not paired, or another service took the handshake)", e)
            } catch (e: IOException) {
                if (e is SocketTimeoutException) throw AdbConnectionException(AdbConnectionException.Reason.PROTOCOL, "no CNXN from device", e)
                null
            } ?: throw AdbConnectionException(AdbConnectionException.Reason.UNAUTHORIZED, "device closed the connection after TLS (key not paired?)")
            if (cnxn.command != AdbMessage.CNXN) throw AdbConnectionException(AdbConnectionException.Reason.PROTOCOL, "expected CNXN, got $cnxn")
            return finish(ssl, input, ssl.outputStream, cnxn, timeoutMs)
        }

        private fun finish(socket: Socket, input: DataInputStream, out: OutputStream, cnxn: AdbMessage, timeoutMs: Int): AdbConnection {
            socket.soTimeout = 0
            val banner = cnxn.data.toString(Charsets.UTF_8).trimEnd('\u0000')
            val maxData = if (cnxn.arg1 in 1..AdbMessage.MAX_PAYLOAD) cnxn.arg1 else 4096
            return AdbConnection(socket, input, out, banner, cnxn.arg0, maxData, timeoutMs)
        }
    }
}

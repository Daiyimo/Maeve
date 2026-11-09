package com.daiyimo.maeve.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap

/** 连接端口的一个候选:本机的哪个地址、哪个端口。 */
data class AdbEndpoint(val host: String, val port: Int)

/** Maeve 自己在本机开着的监听端口(如登录回调):扫端口时跳过,免得连到自己。 */
object OwnListeningPorts {
    private val ports = ConcurrentHashMap.newKeySet<Int>()
    fun add(port: Int) { ports += port }
    fun remove(port: Int) { ports -= port }
    fun snapshot(): Set<Int> = ports.toSet()
}

/** 扫本机端口的底层动作;测试里换成假的。 */
interface LocalPortScanner {
    /** [host] 上 [ports] 里哪些能建立 TCP 连接(升序),跳过 [skip];[budgetMs] 用完就停,已找到的照给。 */
    suspend fun openPorts(host: String, ports: IntRange, skip: Set<Int>, budgetMs: Long): List<Int>

    /** 发 CNXN、对方回 STLS 才算无线调试的 adbd(别的服务、自己连自己都不会回 STLS)。 */
    fun speaksAdbTls(host: String, port: Int, timeoutMs: Int): Boolean
}

/** 一次本机扫描的结果:几个端口开着、几个像 adbd(诊断信息只记个数)。 */
data class ScanResult(val endpoints: List<AdbEndpoint>, val open: Int, val adb: Int)

/**
 * 找连接端口的最后一招:前面几个来源都没给出能连上的端口时,扫本机自己的地址。
 * 无线调试的 adbd 监听一个系统随手分配的端口,落在临时端口范围里([range],读不到就用 [FALLBACK_RANGE])。
 * 按 [hosts] 的顺序一个地址一个地址扫(先 127.0.0.1,再本机 Wi-Fi 地址;adbd 可能只绑其中一个),
 * 每个开着的端口发 CNXN 看回不回 STLS;一个地址上有像 adbd 的就不再扫下一个。只扫本机,不碰别的设备。
 * 扫描和核对合计不超过 [budgetMs]。
 */
class LocalPortScan(
    private val scanner: LocalPortScanner,
    private val hosts: () -> List<String>,
    private val range: () -> IntRange = { FALLBACK_RANGE },
    private val ownPorts: () -> Set<Int> = OwnListeningPorts::snapshot,
    private val budgetMs: Long = 10_000,
    private val probeTimeoutMs: Int = 1_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** 扫一遍;[skip] 是前面来源已经试过的端口。 */
    suspend fun scan(skip: Set<Int>): ScanResult {
        val deadline = now() + budgetMs
        val ports = range()
        val excluded = skip + ownPorts()
        var open = 0
        for (host in hosts().distinct()) {
            val left = deadline - now()
            if (left <= 0) break
            val (openHere, adbHere) = probeHost(host, ports, excluded, left, deadline)
            open += openHere
            if (adbHere.isNotEmpty()) return ScanResult(adbHere, open, adbHere.size)
        }
        return ScanResult(emptyList(), open, 0)
    }

    /** 一个地址上的两步:要开着的端口,再逐个发 CNXN 核对是不是 adbd;预算用完就停。 */
    private suspend fun probeHost(
        host: String, ports: IntRange, excluded: Set<Int>, budget: Long, deadline: Long,
    ): Pair<Int, List<AdbEndpoint>> {
        val openPorts = scanner.openPorts(host, ports, excluded, budget)
        val adb = ArrayList<AdbEndpoint>()
        for (port in openPorts) {
            if (now() >= deadline) break
            if (scanner.speaksAdbTls(host, port, probeTimeoutMs)) adb += AdbEndpoint(host, port)
        }
        return openPorts.size to adb
    }

    companion object {
        /** 读不到系统的临时端口范围时扫这一段(覆盖各版本常见的设置)。 */
        val FALLBACK_RANGE = 30_000..65_535

        /**
         * 解析 `/proc/sys/net/ipv4/ip_local_port_range`(两个数,空白分隔)。读不到、格式不对或范围不合理时用 [FALLBACK_RANGE]。
         */
        fun parseRange(text: String?): IntRange {
            val nums = text?.trim()?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() } ?: return FALLBACK_RANGE
            if (nums.size != 2) return FALLBACK_RANGE
            val (lo, hi) = nums
            return if (lo in 1024..65_535 && hi in lo..65_535) lo..hi else FALLBACK_RANGE
        }
    }
}

/**
 * 真实的扫描:java.nio 非阻塞连接,同时最多 [concurrency] 个在连,每个最多等 [connectTimeoutMs]。
 * 本机地址连不上的端口会马上被拒,整段扫下来几秒钟。在 IO 线程上跑,协程取消时停下。
 */
class NioPortScanner(
    private val concurrency: Int = 200,
    private val connectTimeoutMs: Long = 150,
) : LocalPortScanner {
    override suspend fun openPorts(host: String, ports: IntRange, skip: Set<Int>, budgetMs: Long): List<Int> = withContext(Dispatchers.IO) {
        val address = AdbHosts.ownAddress(host) ?: return@withContext emptyList()
        val ctx = currentCoroutineContext()
        sweep(address, ports, skip, System.currentTimeMillis() + budgetMs) { ctx.isActive }
    }

    private fun sweep(addr: InetAddress, ports: IntRange, skip: Set<Int>, deadline: Long, active: () -> Boolean): List<Int> {
        val open = TreeSet<Int>()
        val pending = ports.iterator()
        Selector.open().use { selector ->
            val inFlight = HashMap<SelectionKey, Long>()
            try {
                while (moreToDo(pending, inFlight, deadline, active)) {
                    fill(selector, inFlight, pending, addr, skip, open)
                    selector.select(SELECT_MS)
                    reap(selector, inFlight, open)
                    dropStale(inFlight)
                }
            } finally {
                inFlight.keys.forEach { runCatching { it.channel().close() } }
            }
        }
        return open.toList()
    }

    /** 还有端口要连、或在连的没完,且没超预算、协程还没被取消,就接着扫。 */
    private fun moreToDo(pending: IntIterator, inFlight: HashMap<SelectionKey, Long>, deadline: Long, active: () -> Boolean) =
        (pending.hasNext() || inFlight.isNotEmpty()) && System.currentTimeMillis() < deadline && active()

    /** 把在连数补到 [concurrency]:握手立即成功的当场记账,其余挂到 selector 上等结果。 */
    private fun fill(
        selector: Selector, inFlight: HashMap<SelectionKey, Long>, pending: IntIterator,
        addr: InetAddress, skip: Set<Int>, open: TreeSet<Int>,
    ) {
        while (pending.hasNext() && inFlight.size < concurrency) {
            val port = pending.nextInt()
            if (port in skip) continue
            val channel = SocketChannel.open()
            try {
                channel.configureBlocking(false)
                if (channel.connect(InetSocketAddress(addr, port))) {
                    if (!selfConnected(channel, port)) open += port
                    channel.close()
                } else {
                    inFlight[channel.register(selector, SelectionKey.OP_CONNECT, port)] = System.currentTimeMillis()
                }
            } catch (e: IOException) {
                channel.close()
            }
        }
    }

    /** 收这一轮连完的:连上、且不是自己连自己的,才记进结果。 */
    private fun reap(selector: Selector, inFlight: HashMap<SelectionKey, Long>, open: TreeSet<Int>) {
        for (key in selector.selectedKeys()) {
            val channel = key.channel() as SocketChannel
            val port = key.attachment() as Int
            val connected = try { channel.finishConnect() } catch (e: IOException) { false }
            if (connected && !selfConnected(channel, port)) open += port
            inFlight.remove(key)
            key.cancel()
            channel.close()
        }
        selector.selectedKeys().clear()
    }

    /** 等太久的放弃:本机连不上的端口本该立刻被拒,一直拖着的是过滤了包的对端。 */
    private fun dropStale(inFlight: HashMap<SelectionKey, Long>) {
        val now = System.currentTimeMillis()
        val stale = inFlight.filterValues { now - it > connectTimeoutMs }.keys
        for (key in stale) {
            inFlight.remove(key)
            key.cancel()
            key.channel().close()
        }
    }

    /** 扫临时端口范围时,系统可能恰好给这条连接分到同一个本地端口,于是自己连上了自己。 */
    private fun selfConnected(channel: SocketChannel, port: Int): Boolean = runCatching {
        selfConnected(channel.socket(), port)
    }.getOrDefault(false)

    /** 连接的本地端口正是目标端口、且本地地址就是远端地址:连上的是自己。 */
    private fun selfConnected(socket: Socket, port: Int) =
        socket.localPort == port && socket.localAddress == socket.inetAddress

    override fun speaksAdbTls(host: String, port: Int, timeoutMs: Int): Boolean = try {
        probeAdb(host, port, timeoutMs)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** 连上后发一句 CNXN:回 STLS 的才是无线调试的 adbd,别的服务不会回。 */
    private fun probeAdb(host: String, port: Int, timeoutMs: Int): Boolean {
        val target = AdbHosts.ownSocketAddress(host, port) ?: return false
        Socket().use { s ->
            s.connect(target, timeoutMs)
            if (selfConnected(s, port)) return false
            s.soTimeout = timeoutMs
            s.getOutputStream().apply {
                write(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_PAYLOAD, HOST_BANNER).encode())
                flush()
            }
            return AdbMessage.read(DataInputStream(s.getInputStream()), maxData = 4096)?.command == AdbMessage.STLS
        }
    }

    private companion object {
        const val SELECT_MS = 20L
        val HOST_BANNER = "host::\u0000".toByteArray(Charsets.US_ASCII)
    }
}

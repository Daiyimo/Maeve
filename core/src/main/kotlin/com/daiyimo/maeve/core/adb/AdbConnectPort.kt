package com.daiyimo.maeve.core.adb

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 上次连上的无线调试端口(App 私有存储)。 */
interface PortCache {
    fun load(): Int?
    fun save(port: Int)
}

/**
 * 一次找连接端口各来源给出了几个候选(不含端口本身,诊断信息用)。
 * [scanned] 为真时扫过本机:[scanOpen] 个端口开着,其中 [scanAdb] 个像 adbd;[manual] 是这次带着用户填的端口。
 */
data class ConnectCandidates(
    val property: Int,
    val mdns: Int,
    val cache: Int,
    val connected: Boolean,
    val scanned: Boolean = false,
    val scanOpen: Int = 0,
    val scanAdb: Int = 0,
    val manual: Boolean = false,
)

/** 候选端口的来源,按尝试顺序排列;下标同时是诊断计数里的序号。 */
private enum class PortSource { CACHE, PROPERTY, MDNS, SCAN, MANUAL }

/**
 * 找本机无线调试的连接端口并连上。按顺序试,哪个连上就停:上次连上的端口、系统属性(读得到才用)、mDNS 查到的每一个、
 * 扫本机端口([scan],前面都不行时才扫)、用户在引导页填的端口([manual])。
 * 只靠 mDNS 不行:adbd 只在打开无线调试时登记一次服务,mDNS 守护进程重启后登记就没了,有的网络还拦着组播;
 * mDNS 也可能同时查到已经失效的旧端口,所以查到几个试几个。缓存的端口失效时被拒得很快,所以排第一。
 * 用户填了端口就不再扫(之前就是扫不到才让填的),直接试它。
 * TLS 上被拒(UNAUTHORIZED、TLS_FAILED)不一定是钥匙不对:有的系统让另一个服务先接住同一端口的第一次 TLS 握手,
 * 对谁的证书都拒,紧接着再连才落到 adbd。所以同一端口隔 [tlsRetryDelayMs] 再试一次,还不行就接着试其余候选;
 * 只有试遍都连不上、且有端口这样拒了我们,才报 UNAUTHORIZED(要重新配对)。已经有端口拒了我们就不再扫本机端口:
 * adbd 找到了,扫出来的也只会是它。
 */
class AdbConnectPort(
    private val property: () -> Int?,
    private val finder: AdbServiceFinder,
    private val cache: PortCache,
    private val mdnsTimeoutMs: Long = 3_000,
    /** 每次连接结束(连上或失败)报一次各来源的候选个数。 */
    private val report: (ConnectCandidates) -> Unit = {},
    private val scan: LocalPortScan? = null,
    /** 用户这次填的端口;没填为 null。 */
    private val manual: () -> Int? = { null },
    /** 开始、结束扫本机端口时各报一次(界面显示「正在本机查找…」)。 */
    private val scanning: (Boolean) -> Unit = {},
    private val localHost: String = LOCALHOST,
    private val tlsRetryDelayMs: Long = 300,
) {
    /** 用 [open] 依次试候选,返回第一个连上的结果;一个都连不上时抛有端口拒了我们的 UNAUTHORIZED,否则抛最后一个错。 */
    suspend fun <T> connect(open: (host: String, port: Int) -> T): T {
        val tried = HashSet<AdbEndpoint>()
        val triedPorts = HashSet<Int>()
        var last: AdbConnectionException? = null
        var rejected: AdbConnectionException? = null
        val manualPort = manual()?.takeIf { it in 1..65535 }
        var scanResult: ScanResult? = null
        val counts = IntArray(PortSource.entries.size)
        var connected = false
        try {
            for (source in PortSource.entries) {
                // 读不到的来源(属性被拦、mDNS 起不来)当作没有,不算失败
                val endpoints = try {
                    fetch(source, manualPort, triedPorts, rejected) { scanResult = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyList()
                }
                counts[source.ordinal] = endpoints.size
                for (ep in endpoints) {
                    if (ep.port !in 1..65535 || !tried.add(ep)) continue
                    triedPorts += ep.port
                    try {
                        return attempt(ep, open).also { cache.save(ep.port); connected = true }
                    } catch (e: AdbConnectionException) {
                        if (e.reason == AdbConnectionException.Reason.NOT_LOCAL) throw e
                        if (e.reason == AdbConnectionException.Reason.UNAUTHORIZED) rejected = e
                        last = e
                    }
                }
            }
            throw rejected ?: last ?: AdbConnectionException(AdbConnectionException.Reason.CONNECT_FAILED, "no wireless debugging port found")
        } finally {
            val s = scanResult
            report(
                ConnectCandidates(
                    property = counts[PortSource.PROPERTY.ordinal],
                    mdns = counts[PortSource.MDNS.ordinal],
                    cache = counts[PortSource.CACHE.ordinal],
                    connected = connected,
                    scanned = s != null, scanOpen = s?.open ?: 0, scanAdb = s?.adb ?: 0, manual = manualPort != null,
                ),
            )
        }
    }

    /**
     * 连一个端点。TLS 上被拒(UNAUTHORIZED、TLS_FAILED)不一定是钥匙不对:有的系统让另一个服务先接住
     * 同一端口的第一次 TLS 握手,对谁的证书都拒,紧接着再连才落到 adbd——所以隔 [tlsRetryDelayMs] 再试一次。
     * 其余失败原样抛给调用方记录;地址策略失败(NOT_LOCAL)必须直接穿透,不能换端口继续。
     */
    private suspend fun <T> attempt(ep: AdbEndpoint, open: (host: String, port: Int) -> T): T {
        var retried = false
        while (true) {
            try {
                return open(ep.host, ep.port)
            } catch (e: AdbConnectionException) {
                if (e.reason == AdbConnectionException.Reason.NOT_LOCAL) throw e
                val tls = e.reason == AdbConnectionException.Reason.UNAUTHORIZED ||
                    e.reason == AdbConnectionException.Reason.TLS_FAILED
                if (!tls || retried) throw e
                retried = true
                delay(tlsRetryDelayMs)
            }
        }
    }

    /** 取一个来源给出的候选端口;[rejected] 是到此刻为止被设备拒过的记录,扫本机时要看它。 */
    private suspend fun fetch(
        source: PortSource, manualPort: Int?, triedPorts: Set<Int>, rejected: AdbConnectionException?, onScan: (ScanResult) -> Unit,
    ): List<AdbEndpoint> = when (source) {
        PortSource.CACHE -> local(cache.load())
        PortSource.PROPERTY -> local(property())
        PortSource.MDNS -> finder.findPorts(AdbServiceTypes.CONNECT, mdnsTimeoutMs).map { AdbEndpoint(localHost, it) }
        PortSource.SCAN -> scanLocal(manualPort, rejected, triedPorts, onScan)
        PortSource.MANUAL -> local(manualPort)
    }

    /** 扫本机是最后一招:没配扫描器、用户填了端口、或已经有端口拒了我们,都不扫。 */
    private suspend fun scanLocal(
        manualPort: Int?, rejected: AdbConnectionException?, triedPorts: Set<Int>, onScan: (ScanResult) -> Unit,
    ): List<AdbEndpoint> {
        if (scan == null || manualPort != null || rejected != null) return emptyList()
        scanning(true)
        try {
            return scan.scan(triedPorts).also(onScan).endpoints
        } finally {
            scanning(false)
        }
    }

    private fun local(vararg ports: Int?) = ports.filterNotNull().map { AdbEndpoint(localHost, it) }

    companion object {
        const val LOCALHOST = "127.0.0.1"
    }
}

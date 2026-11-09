package com.daiyimo.maeve.core.adb

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/** adbd 通过 mDNS 广播出来的服务类型。 */
object AdbServiceTypes {
    /** 仅当「使用配对码配对」对话框打开时才广播。 */
    const val PAIRING = "_adb-tls-pairing._tcp"
    /** 无线调试开启时广播;端口每次开关无线调试都会变。 */
    const val CONNECT = "_adb-tls-connect._tcp"
}

/**
 * 找本机 adbd 公布的服务端口。同一服务可能查到好几条(关了又开无线调试后,旧的那条可能还留在 mDNS 里,新的改名带上序号),
 * 全部交出,先查到的在前,由调用方挨个试;一条都找不到(超时)时返回空表。
 */
fun interface AdbServiceFinder {
    suspend fun findPorts(serviceType: String, timeoutMs: Long): List<Int>
}

class AdbPairingException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason {
        /** 目标不是本机自己的地址:建 Socket 之前就拒绝,不能当网络故障重试。 */
        NOT_LOCAL,
        CONNECT_FAILED, TLS_FAILED, WRONG_CODE, PROTOCOL,
    }
}

/** 配对成功后设备回报的身份,通常是 type=1 的设备 GUID。 */
class AdbPeerInfo(val type: Int, val data: String)

/**
 * 无线调试的配对码配对,这里是客户端一侧。步骤:
 * 先 TLS 1.3,导出 64 字节密钥("adb-label\0"),口令 = 配对码拼上导出密钥;再交换 SPAKE2 消息;
 * 最后用协商好的 AES-GCM 互发 8192 字节的 PeerInfo——我们交出 adb 公钥,设备回它的 GUID。
 * 每个包都带 6 字节头:版本(1)、类型(0=SPAKE2,1=PEER_INFO)、负载长度(大端 uint32)。
 */
class AdbPairing(private val tls: AdbTls) {
    /** 和本机上的设备配对;拿到的校验地址就是真正连接的地址,不再回头解析原 host。 */
    fun pair(host: String, port: Int, code: String, timeoutMs: Int = 15_000): AdbPeerInfo {
        val address = AdbHosts.ownSocketAddress(host, port) ?: throw AdbPairingException(
            AdbPairingException.Reason.NOT_LOCAL,
            "refusing to pair with $host: adb is only reachable on this device",
        )
        val connection = try {
            tls.connect(address, timeoutMs)
        } catch (error: SSLException) {
            throw AdbPairingException(AdbPairingException.Reason.TLS_FAILED, "TLS handshake failed", error)
        } catch (error: IOException) {
            throw AdbPairingException(AdbPairingException.Reason.CONNECT_FAILED, "cannot connect to $host:$port", error)
        }
        connection.use { return exchange(it, code) }
    }

    private fun exchange(socket: SSLSocket, code: String): AdbPeerInfo {
        val exportedKey = tls.exportKeyingMaterial(socket, EXPORT_LABEL, EXPORT_LENGTH)
        val auth = PairingAuth.client(code.toByteArray(Charsets.UTF_8) + exportedKey)
        val reader = DataInputStream(socket.inputStream)
        val writer = socket.outputStream

        writePacket(writer, TYPE_SPAKE2, auth.message)
        val peerSpake = readPacket(reader, TYPE_SPAKE2) ?: throw protocol("connection closed before key exchange")
        try {
            auth.initCipher(peerSpake)
        } catch (cause: IllegalArgumentException) {
            throw protocol("bad key exchange message: ${cause.message}")
        }

        val peerInfo = ByteArray(PEER_INFO_SIZE)
        peerInfo[0] = PEER_RSA_PUB_KEY
        val adbKey = tls.identity.adbPublicKey.toByteArray(Charsets.UTF_8)
        check(adbKey.size < PEER_INFO_SIZE - 1)
        System.arraycopy(adbKey, 0, peerInfo, 1, adbKey.size)
        writePacket(writer, TYPE_PEER_INFO, auth.encrypt(peerInfo))

        // 配对码不对时,设备解不开我们的 PeerInfo,直接断开
        val sealedInfo = try {
            readPacket(reader, TYPE_PEER_INFO)
        } catch (error: IOException) {
            if (error is AdbPairingException) throw error
            null
        } ?: throw AdbPairingException(AdbPairingException.Reason.WRONG_CODE, "device closed the connection (wrong pairing code?)")
        val deviceInfo = try {
            auth.decrypt(sealedInfo)
        } catch (error: AEADBadTagException) {
            throw AdbPairingException(AdbPairingException.Reason.WRONG_CODE, "cannot decrypt device info (wrong pairing code?)", error)
        }
        if (deviceInfo.size != PEER_INFO_SIZE) throw protocol("peer info has ${deviceInfo.size} bytes")
        var dataEnd = 1
        while (dataEnd < deviceInfo.size && deviceInfo[dataEnd] != 0.toByte()) dataEnd++
        return AdbPeerInfo(deviceInfo[0].toInt() and 0xff, String(deviceInfo, 1, dataEnd - 1, Charsets.UTF_8))
    }

    private fun protocol(msg: String) = AdbPairingException(AdbPairingException.Reason.PROTOCOL, msg)

    companion object {
        const val EXPORT_LABEL = "adb-label\u0000"
        const val EXPORT_LENGTH = 64
        const val PEER_INFO_SIZE = 8192
        const val MAX_PAYLOAD = PEER_INFO_SIZE * 2
        const val VERSION: Byte = 1
        const val TYPE_SPAKE2: Byte = 0
        const val TYPE_PEER_INFO: Byte = 1
        const val PEER_RSA_PUB_KEY: Byte = 0
        const val PEER_DEVICE_GUID: Byte = 1

        fun writePacket(out: OutputStream, type: Byte, payload: ByteArray) {
            val frame = ByteBuffer.allocate(6).put(VERSION).put(type).putInt(payload.size).array()
            out.write(frame + payload)
            out.flush()
        }

        /** 读入一个包并校验类型;对方在读包头前就断开时返回 null。 */
        fun readPacket(input: DataInputStream, expectedType: Byte): ByteArray? {
            val header = ByteArray(6)
            try {
                input.readFully(header)
            } catch (error: EOFException) {
                return null
            }
            val buffer = ByteBuffer.wrap(header)
            val packetVersion = buffer.get()
            val packetType = buffer.get()
            val payloadSize = buffer.getInt()
            if (packetVersion != VERSION) throw AdbPairingException(AdbPairingException.Reason.PROTOCOL, "unsupported pairing version $packetVersion")
            if (packetType != expectedType) throw AdbPairingException(AdbPairingException.Reason.PROTOCOL, "unexpected packet type $packetType")
            if (payloadSize <= 0 || payloadSize > MAX_PAYLOAD) throw AdbPairingException(AdbPairingException.Reason.PROTOCOL, "bad payload size $payloadSize")
            return ByteArray(payloadSize).also { input.readFully(it) }
        }
    }
}

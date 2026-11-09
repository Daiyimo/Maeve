package com.daiyimo.maeve.core.adb

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException

/**
 * adb 传输层的一个数据包:24 字节小端头,依次是命令、arg0、arg1、负载长度、
 * 负载各字节(按无符号)之和、命令按位取反,随后是负载本身。
 *
 * 从协议版本 [VERSION_SKIP_CHECKSUM] 起接收方不再核对校验和;发送方仍旧照常填写。
 */
class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray = EMPTY) {
    /** 生成可直接写到 adbd 的完整帧。 */
    fun encode(): ByteArray {
        val frame = ByteArray(HEADER_SIZE + data.size)
        frame.writeIntLe(0, command)
        frame.writeIntLe(4, arg0)
        frame.writeIntLe(8, arg1)
        frame.writeIntLe(12, data.size)
        frame.writeIntLe(16, checksum(data))
        frame.writeIntLe(20, command.inv())
        data.copyInto(frame, HEADER_SIZE)
        return frame
    }

    override fun toString() = "${name(command)}($arg0, $arg1, ${data.size} bytes)"

    companion object {
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257
        const val STLS = 0x534c5453

        const val VERSION = 0x01000001
        const val VERSION_SKIP_CHECKSUM = 0x01000001
        const val STLS_VERSION = 0x01000000
        const val MAX_PAYLOAD = 1024 * 1024
        const val HEADER_SIZE = 24
        private val EMPTY = ByteArray(0)

        /** 负载各字节按无符号相加的结果。 */
        fun checksum(data: ByteArray): Int {
            var total = 0
            for (byte in data) total += byte.toInt() and 0xff
            return total
        }

        /** 可读的命令名;四个字节不全是 A–Z 时退回十六进制表示。 */
        fun name(cmd: Int): String {
            val chars = CharArray(4) { index -> ((cmd ushr (8 * index)) and 0xff).toChar() }
            return String(chars).takeIf { text -> text.all { it in 'A'..'Z' } }
                ?: "0x%08x".format(cmd)
        }

        /**
         * 读一个包。数据流在包头之前就结束时返回 null;包头残缺、魔数不符、长度越界
         * 或校验和不对都抛 [IOException]。
         */
        fun read(input: DataInputStream, maxData: Int = MAX_PAYLOAD, verifyChecksum: Boolean = false): AdbMessage? {
            val header = ByteArray(HEADER_SIZE)
            try {
                input.readFully(header)
            } catch (_: EOFException) {
                return null
            }
            val command = header.intLe(0)
            val arg0 = header.intLe(4)
            val arg1 = header.intLe(8)
            val payloadSize = header.intLe(12)
            val expectedChecksum = header.intLe(16)
            val magic = header.intLe(20)
            if (magic != command.inv()) throw IOException("bad adb packet magic for ${name(command)}")
            if (payloadSize < 0 || payloadSize > maxData) throw IOException("adb packet too large: $payloadSize")
            val payload = if (payloadSize == 0) EMPTY else ByteArray(payloadSize).also { input.readFully(it) }
            if (verifyChecksum && checksum(payload) != expectedChecksum) throw IOException("bad adb packet checksum")
            return AdbMessage(command, arg0, arg1, payload)
        }
    }
}

private fun ByteArray.writeIntLe(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
    this[offset + 2] = (value ushr 16).toByte()
    this[offset + 3] = (value ushr 24).toByte()
}

private fun ByteArray.intLe(offset: Int): Int =
    (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8) or
        ((this[offset + 2].toInt() and 0xff) shl 16) or
        ((this[offset + 3].toInt() and 0xff) shl 24)

package com.daiyimo.maeve.core.adb

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * adb 无线配对用的 SPAKE2 变体(edwards25519 + SHA-512),必须和设备端逐字节一致:
 * - 私钥 = (64 字节随机数 mod L) × 8,乘上余因子;
 * - 口令标量 = SHA-512(口令) mod L,再按低三位依次加 L、2L、4L,补成 8 的倍数(对齐设备端的历史修正);
 * - 发出去的消息 P* = 私钥·B + 口令标量·(甲方取 M,乙方取 N);
 * - 共享密钥 = SHA-512,每段都带 8 字节小端长度前缀,依次是:甲名、乙名、甲消息、乙消息、
 *   私钥·(Q* − 口令标量·对方常量)、SHA-512(口令)。
 * M、N 由公开种子串推导,见 [SEED_M]/[SEED_N];测试里重新推导并核对。
 */
class Spake2(
    private val role: Role,
    private val myName: ByteArray,
    private val theirName: ByteArray,
) {
    enum class Role { ALICE, BOB }

    private var privateScalar: BigInteger? = null
    private var pwScalar: BigInteger? = null
    private var pwHash: ByteArray? = null
    private var myMessage: ByteArray? = null
    private var finished = false

    fun generateMessage(password: ByteArray, random: SecureRandom = SecureRandom()): ByteArray =
        generateMessage(password, ByteArray(64).also { random.nextBytes(it) })

    /** [seed] 是 64 字节随机数;只有测试传入固定值。 */
    internal fun generateMessage(password: ByteArray, seed: ByteArray): ByteArray {
        check(myMessage == null) { "message already generated" }
        require(seed.size == 64)
        val keyScalar = EdPoint.fromLe(seed).mod(EdPoint.L).shiftLeft(3)
        val digest = MessageDigest.getInstance("SHA-512").digest(password)
        var scalar = EdPoint.fromLe(digest).mod(EdPoint.L)
        if (scalar.testBit(0)) scalar += EdPoint.L
        if (scalar.testBit(1)) scalar += EdPoint.L.shiftLeft(1)
        if (scalar.testBit(2)) scalar += EdPoint.L.shiftLeft(2)
        val maskPoint = (if (role == Role.ALICE) M else N).times(scalar)
        val outgoing = (EdPoint.BASE.times(keyScalar) + maskPoint).encode()
        privateScalar = keyScalar
        pwScalar = scalar
        pwHash = digest
        myMessage = outgoing
        return outgoing.copyOf()
    }

    /** 返回 64 字节共享密钥;对方的点不在曲线上时抛 [IllegalArgumentException]。 */
    fun processMessage(theirMsg: ByteArray): ByteArray {
        val mine = checkNotNull(myMessage) { "generateMessage first" }
        check(!finished) { "already processed" }
        require(theirMsg.size == 32) { "peer message must be 32 bytes" }
        val peerPoint = requireNotNull(EdPoint.decode(theirMsg)) { "peer point not on curve" }
        val peerMaskPoint = (if (role == Role.ALICE) N else M).times(pwScalar!!)
        val sharedPoint = (peerPoint - peerMaskPoint).times(privateScalar!!).encode()
        val digest = MessageDigest.getInstance("SHA-512")
        fun put(chunk: ByteArray) {
            val prefix = ByteArray(8)
            var remaining = chunk.size.toLong()
            for (i in 0 until 8) { prefix[i] = (remaining and 0xff).toByte(); remaining = remaining ushr 8 }
            digest.update(prefix)
            digest.update(chunk)
        }
        if (role == Role.ALICE) {
            put(myName); put(theirName); put(mine); put(theirMsg)
        } else {
            put(theirName); put(myName); put(theirMsg); put(mine)
        }
        put(sharedPoint)
        put(pwHash!!)
        finished = true
        return digest.digest()
    }

    companion object {
        const val SEED_M = "edwards25519 point generation seed (M)"
        const val SEED_N = "edwards25519 point generation seed (N)"
        internal val M = requireNotNull(EdPoint.decode(EdPoint.hex("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e")))
        internal val N = requireNotNull(EdPoint.decode(EdPoint.hex("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778")))

        /** 由种子串推导常量点:SHA-256(种子),解不出点就对摘要再取 SHA-256,直到落在曲线上。 */
        internal fun derivePoint(seed: String): EdPoint {
            var candidate = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.US_ASCII))
            while (true) {
                EdPoint.decode(candidate)?.let { return it }
                candidate = MessageDigest.getInstance("SHA-256").digest(candidate)
            }
        }
    }
}

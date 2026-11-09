package com.daiyimo.maeve.core.adb

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** HKDF-SHA256(RFC 5869);salt 为空时按 RFC 约定改用 32 个零字节。 */
object Hkdf {
    fun sha256(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32)
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(if (salt == null || salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = hmac.doFinal(ikm)
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        val output = ByteArray(length)
        var block = ByteArray(0)
        var filled = 0
        var counter = 1
        while (filled < length) {
            hmac.update(block)
            hmac.update(info)
            hmac.update(counter.toByte())
            block = hmac.doFinal()
            val copied = minOf(block.size, length - filled)
            System.arraycopy(block, 0, output, filled, copied)
            filled += copied
            counter++
        }
        return output
    }
}

/**
 * 配对通道上的 AES-128-GCM。密钥 = HKDF(SPAKE2 密钥, 无 salt, "adb pairing_auth aes-128-gcm key", 16);
 * nonce 12 字节:前 8 字节是本方向的序号(小端,每条消息自增,从 0 起),后 4 字节为零;不带附加数据,16 字节认证标签缀在密文末尾。
 */
class PairingCipher(keyMaterial: ByteArray) {
    private val key = SecretKeySpec(Hkdf.sha256(keyMaterial, null, INFO, 16), "AES")
    private var encryptSeq = 0L
    private var decryptSeq = 0L

    fun encrypt(plain: ByteArray): ByteArray = transform(Cipher.ENCRYPT_MODE, encryptSeq++, plain)

    /** 解密并校验;认证不过时抛 [javax.crypto.AEADBadTagException]。 */
    fun decrypt(sealed: ByteArray): ByteArray = transform(Cipher.DECRYPT_MODE, decryptSeq, sealed).also { decryptSeq++ }

    private fun transform(mode: Int, sequence: Long, payload: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        for (byteIndex in 0 until 8) nonce[byteIndex] = (sequence ushr (8 * byteIndex)).toByte()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(128, nonce))
        return cipher.doFinal(payload)
    }

    companion object {
        private val INFO = "adb pairing_auth aes-128-gcm key".toByteArray(Charsets.US_ASCII)
    }
}

/** 配对认证:先交换 SPAKE2 消息,再用协商出的密钥建 [PairingCipher];两端的名字都以 NUL 结尾,与设备端一致。 */
class PairingAuth private constructor(role: Spake2.Role, password: ByteArray, seed: ByteArray?) {
    private val spake = if (role == Spake2.Role.ALICE) Spake2(role, CLIENT_NAME, SERVER_NAME) else Spake2(role, SERVER_NAME, CLIENT_NAME)
    val message: ByteArray = if (seed == null) spake.generateMessage(password, SecureRandom()) else spake.generateMessage(password, seed)
    private var sessionCipher: PairingCipher? = null

    fun initCipher(theirMessage: ByteArray) {
        check(sessionCipher == null)
        sessionCipher = PairingCipher(spake.processMessage(theirMessage))
    }

    fun encrypt(data: ByteArray) = checkNotNull(sessionCipher).encrypt(data)
    fun decrypt(data: ByteArray) = checkNotNull(sessionCipher).decrypt(data)

    companion object {
        val CLIENT_NAME = "adb pair client\u0000".toByteArray(Charsets.US_ASCII)
        val SERVER_NAME = "adb pair server\u0000".toByteArray(Charsets.US_ASCII)
        fun client(password: ByteArray) = PairingAuth(Spake2.Role.ALICE, password, null)
        fun server(password: ByteArray) = PairingAuth(Spake2.Role.BOB, password, null)
        internal fun client(password: ByteArray, seed: ByteArray) = PairingAuth(Spake2.Role.ALICE, password, seed)
        internal fun server(password: ByteArray, seed: ByteArray) = PairingAuth(Spake2.Role.BOB, password, seed)
    }
}

package com.daiyimo.maeve.core.adb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Maeve 自己的 adb 身份:一把 RSA-2048 钥匙 + 自签名证书。配对时把 [adbPublicKey] 交给设备,
 * 之后每次 TLS 连接都出示 [certificate](设备只比对证书里的公钥)。
 */
class AdbIdentity(val keyPair: KeyPair, val name: String) {
    val publicKey: RSAPublicKey get() = keyPair.public as RSAPublicKey

    val certificate: X509Certificate by lazy { SelfSignedCertificate.create(keyPair) }

    /**
     * 设备 keystore 里的一行:base64(524 字节公钥结构) + 空格 + 名字。
     * 名字里的空白必须换成下划线——系统设置按空白切分这一行,带空格的名字在「已配对的设备」里删不掉。
     */
    val adbPublicKey: String by lazy {
        val body = Base64.getEncoder().encodeToString(AdbPublicKey.encode(publicKey))
        "$body ${name.replace(BLANKS, "_")}"
    }

    companion object {
        /** 新钥匙的公开指数:F4(65537)。读回钥匙时拿不到指数就按它补,并验证补得对。 */
        val PUBLIC_EXPONENT: BigInteger = RSAKeyGenParameterSpec.F4

        private val BLANKS = Regex("\\s")

        fun generate(name: String): AdbIdentity {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(RSAKeyGenParameterSpec(2048, PUBLIC_EXPONENT))
            return AdbIdentity(generator.generateKeyPair(), name)
        }

        /**
         * 从存下的 PKCS#8 读回身份。不要求平台给的是带 CRT 参数的私钥:只要是 RSA 私钥就取模数,
         * 公开指数按 [publicExponent] 找。读不出抛 [AdbKeyUnreadableException]。
         */
        fun fromPkcs8(pkcs8: ByteArray, name: String): AdbIdentity = try {
            pairUp(privateKeyOf(pkcs8), pkcs8, name)
        } catch (e: AdbKeyUnreadableException) {
            throw e
        } catch (e: Exception) {
            throw AdbKeyUnreadableException("stored key is not valid PKCS#8", e)
        }

        private fun privateKeyOf(pkcs8: ByteArray): RSAPrivateKey {
            val parsed = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
            return parsed as? RSAPrivateKey
                ?: throw AdbKeyUnreadableException("stored key is not RSA (${parsed.javaClass.simpleName})")
        }

        /** 私钥只能给出模数,公钥要的公开指数单独找([publicExponent]),两者凑成一对。 */
        private fun pairUp(private: RSAPrivateKey, pkcs8: ByteArray, name: String): AdbIdentity {
            val factory = KeyFactory.getInstance("RSA")
            val public = factory.generatePublic(RSAPublicKeySpec(private.modulus, publicExponent(private, pkcs8))) as RSAPublicKey
            return AdbIdentity(KeyPair(public, private), name)
        }

        /**
         * 私钥对应的公开指数:平台给了 CRT 形式就直接用;否则从 PKCS#8 里的 RSAPrivateKey 结构读;
         * 结构里没有(写的是 0)才按 [PUBLIC_EXPONENT] 补,补之前签一次名验一次,对不上就抛,不拿错的公钥去连。
         */
        internal fun publicExponent(key: RSAPrivateKey, pkcs8: ByteArray): BigInteger {
            (key as? RSAPrivateCrtKey)?.publicExponent?.takeIf { it.signum() > 0 }?.let { return it }
            Pkcs8Rsa.publicExponent(pkcs8)?.takeIf { it.signum() > 0 }?.let { return it }
            if (!signsAndVerifiesWith(key, PUBLIC_EXPONENT)) {
                throw AdbKeyUnreadableException("cannot recover the public exponent of the stored key")
            }
            return PUBLIC_EXPONENT
        }

        /** 拿 [candidate] 当公开指数现场签一次名、再用对应的公钥验一次:对得上才敢用它补公钥。 */
        private fun signsAndVerifiesWith(key: RSAPrivateKey, candidate: BigInteger): Boolean = runCatching {
            val public = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(key.modulus, candidate))
            val probe = "maeve-adb-key-check".toByteArray()
            val signature = Signature.getInstance("SHA256withRSA").run { initSign(key); update(probe); sign() }
            Signature.getInstance("SHA256withRSA").run { initVerify(public); update(probe); verify(signature) }
        }.getOrDefault(false)
    }
}

/** 已存的钥匙在,却读不出来(解不开或解析不了)。不能悄悄换新的:设备只认配对时那一把。 */
class AdbKeyUnreadableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 钥匙此刻的状况(诊断信息用)。 */
enum class AdbKeyState {
    /** 这个进程里还没用过钥匙。 */
    NotLoaded,
    /** 读回了已存的那把。 */
    Loaded,
    /** 之前没有(或配对时换掉了读不出的那把),新建了一把并存下。 */
    Created,
    /** 存着一把却读不出来:要重新配对。 */
    Unreadable,
}

/**
 * 一个进程里的 adb 身份只有一份:配对和连接拿到的一定是同一把钥匙,不会因为某次读失败各用各的。
 * 只有配对([forPairing])会在存着的钥匙读不出时换一把新的(配对本来就会把新钥匙交给设备);
 * 连接([forConnect])遇到读不出就抛 [AdbKeyUnreadableException],界面据此请用户重新配对。
 */
class AdbIdentitySource(
    private val store: AdbKeyStore,
    private val name: () -> String,
    private val log: (String, Throwable?) -> Unit = { _, _ -> },
) {
    private var cached: AdbIdentity? = null

    @Volatile var state: AdbKeyState = AdbKeyState.NotLoaded
        private set

    /** 最近一次读不出的原因(诊断信息用);读得出时为 null。 */
    @Volatile var problem: String? = null
        private set

    /** 连接用:已存的那把;一把都没有就新建并存下;存着却读不出就抛,不覆盖。 */
    @Synchronized
    fun forConnect(): AdbIdentity = cached ?: acquire(replaceWhenBroken = false)

    /** 配对用:和 [forConnect] 同一把;只有存着的读不出时,在这里换一把新的存下(随后的配对会把它交给设备)。 */
    @Synchronized
    fun forPairing(): AdbIdentity = cached ?: acquire(replaceWhenBroken = true)

    /** 解除配对:钥匙和缓存一起删掉,下次配对用新钥匙。 */
    @Synchronized
    fun forget() {
        cached = null
        store.clear()
        state = AdbKeyState.NotLoaded
        problem = null
    }

    /** 取身份的唯一入口:读存储 →(没有就新建 / 读不出就按 [replaceWhenBroken] 处置)→ 缓存下来。 */
    private fun acquire(replaceWhenBroken: Boolean): AdbIdentity {
        val stored = try {
            store.read()
        } catch (e: AdbKeyUnreadableException) {
            return recover(e, replaceWhenBroken)
        }
        if (stored == null) return mint()
        return try {
            keep(AdbIdentity.fromPkcs8(stored, name()), AdbKeyState.Loaded)
        } catch (e: AdbKeyUnreadableException) {
            recover(e, replaceWhenBroken)
        }
    }

    /** 新建一把并存下;写存储的异常直接往上抛(存不下就不算有身份)。 */
    private fun mint(): AdbIdentity {
        val fresh = AdbIdentity.generate(name())
        store.write(fresh.keyPair.private.encoded)
        return keep(fresh, AdbKeyState.Created)
    }

    /** 连接撞上读不出:只记录并抛出;配对撞上:清掉坏的那把,换新的交给设备。 */
    private fun recover(broken: AdbKeyUnreadableException, replaceWhenBroken: Boolean): AdbIdentity {
        if (!replaceWhenBroken) throw report(broken)
        log("replacing the unreadable adb key for a new pairing", broken)
        store.clear()
        return mint()
    }

    private fun report(broken: AdbKeyUnreadableException): AdbKeyUnreadableException {
        state = AdbKeyState.Unreadable
        problem = generateSequence<Throwable>(broken) { it.cause }
            .joinToString(" <- ") { it.message ?: it.javaClass.simpleName }
            .take(200)
        log("stored adb key is unreadable; pair again to replace it", broken)
        return broken
    }

    private fun keep(identity: AdbIdentity, loaded: AdbKeyState): AdbIdentity {
        cached = identity
        state = loaded
        problem = null
        return identity
    }
}

/**
 * 钥匙(PKCS#8)的存取。App 里用 Android Keystore 包一层加密存放。
 * [read] 没存过返回 null;存着却读不出抛 [AdbKeyUnreadableException](两者要分开,后者不能当作没有)。
 */
interface AdbKeyStore {
    fun read(): ByteArray?
    fun write(pkcs8: ByteArray)
    fun clear()
}

/**
 * 加密存放在文件里的钥匙:文件内容 = 1 字节 IV 长度 + IV + AES-GCM 密文。
 * IV 由 Cipher 自己生成(Android Keystore 的密钥不许调用方指定 IV)。
 */
class WrappedKeyFile(private val file: File, private val wrappingKey: () -> SecretKey) : AdbKeyStore {
    override fun read(): ByteArray? {
        if (!file.isFile) return null
        return try {
            unwrap(file.readBytes())
        } catch (e: Exception) {
            throw AdbKeyUnreadableException("cannot unwrap ${file.name}", e)
        }
    }

    /** 拆信封并按 GCM 解:标签对不上(包装密钥换过)由 Cipher 抛异常,外层翻译成读不出。 */
    private fun unwrap(envelope: ByteArray): ByteArray {
        require(envelope.isNotEmpty()) { "key file is empty" }
        val ivLength = envelope[0].toInt() and 0xff
        require(envelope.size > 1 + ivLength) { "key file is truncated" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, envelope.copyOfRange(1, 1 + ivLength)))
        return cipher.doFinal(envelope, 1 + ivLength, envelope.size - 1 - ivLength)
    }

    override fun write(pkcs8: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        val iv = cipher.iv
        val envelope = byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(pkcs8)
        swapIn(envelope)
    }

    /** 先写同目录临时文件再改名:半路断电也不会留下写了一半的钥匙文件。 */
    private fun swapIn(envelope: ByteArray) {
        file.parentFile?.mkdirs()
        val staged = File(file.path + ".tmp")
        staged.writeBytes(envelope)
        check(staged.renameTo(file)) { "cannot write ${file.name}" }
    }

    override fun clear() {
        file.delete()
    }
}

/**
 * 从 PKCS#8 里读 RSA 公开指数:PrivateKeyInfo ::= SEQUENCE { version, algorithm, privateKey OCTET STRING },
 * privateKey 里是 RSAPrivateKey ::= SEQUENCE { version, modulus, publicExponent, ... }。结构不对返回 null。
 */
internal object Pkcs8Rsa {
    private const val SEQUENCE = 0x30
    private const val INTEGER = 0x02
    private const val OCTET_STRING = 0x04

    fun publicExponent(pkcs8: ByteArray): BigInteger? = runCatching {
        val info = Cursor(Cursor(pkcs8).enter(SEQUENCE))
        info.enter(INTEGER)
        info.enter(SEQUENCE)
        val body = info.enter(OCTET_STRING)
        val rsa = Cursor(Cursor(body).enter(SEQUENCE))
        rsa.enter(INTEGER)
        rsa.enter(INTEGER)
        BigInteger(rsa.enter(INTEGER))
    }.getOrNull()

    /** 只够读上面那几层的 DER:按顺序取指定标签的内容,长度支持长 form(最多 3 字节)。 */
    private class Cursor(private val bytes: ByteArray) {
        private var at = 0

        fun enter(tag: Int): ByteArray {
            require((bytes[at++].toInt() and 0xff) == tag) { "unexpected tag" }
            val length = readLength()
            require(at + length <= bytes.size) { "truncated" }
            return bytes.copyOfRange(at, at + length).also { at += length }
        }

        private fun readLength(): Int {
            val first = bytes[at++].toInt() and 0xff
            if (first < 0x80) return first
            val count = first and 0x7f
            require(count in 1..3) { "unsupported length" }
            var length = 0
            repeat(count) { length = (length shl 8) or (bytes[at++].toInt() and 0xff) }
            return length
        }
    }
}

/**
 * adb 的 RSA 公钥二进制格式(全部小端,共 524 字节):
 * 模数字数(64)、n0inv = −1/n mod 2^32、模数 n、rr = (2^2048)^2 mod n、公开指数。
 */
object AdbPublicKey {
    private const val WORDS = 64
    private const val WORD_BYTES = 4
    private const val WIDE_BYTES = 256

    const val SIZE = 2 * WORD_BYTES + WORDS * WORD_BYTES * 2 + WORD_BYTES

    fun encode(key: RSAPublicKey): ByteArray {
        val modulus = key.modulus
        require(modulus.bitLength() == 2048) { "adb keys are RSA-2048" }
        val radix = BigInteger.ONE.shiftLeft(32)
        val n0inv = radix - modulus.mod(radix).modInverse(radix)
        val rr = BigInteger.ONE.shiftLeft(2048 * 2).mod(modulus)
        return LeWriter(SIZE).apply {
            putInt(WORDS)
            putInt(n0inv.toInt())
            putBytes(EdPoint.toLe(modulus, WIDE_BYTES))
            putBytes(EdPoint.toLe(rr, WIDE_BYTES))
            putInt(key.publicExponent.toInt())
        }.array()
    }

    private class LeWriter(size: Int) {
        private val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        fun putInt(value: Int) = buffer.putInt(value)
        fun putBytes(value: ByteArray) = buffer.put(value)
        fun array(): ByteArray = buffer.array()
    }
}

/** 最小的自签名 X.509 v3 证书(无扩展):CN=Maeve,1970-01-01 到 2099-12-31,SHA256withRSA。 */
internal object SelfSignedCertificate {
    private const val INTEGER = 0x02
    private const val OCTET_STRING = 0x04
    private const val NULL = 0x05
    private const val OID = 0x06
    private const val UTF8_STRING = 0x0c
    private const val UTC_TIME = 0x17
    private const val GENERALIZED_TIME = 0x18
    private const val SEQUENCE = 0x30
    private const val SET = 0x31
    private const val BIT_STRING = 0x03
    private const val TAGGED_0 = 0xa0

    private val SHA256_WITH_RSA = byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b)
    private val COMMON_NAME = byteArrayOf(0x55, 0x04, 0x03)
    private val NOT_BEFORE = "700101000000Z".toByteArray()
    private val NOT_AFTER = "20991231235959Z".toByteArray()
    private val SERIAL = byteArrayOf(1)
    private val VERSION = tlv(INTEGER, byteArrayOf(2))

    fun create(keyPair: KeyPair): X509Certificate {
        val algorithm = sequence(element(OID, SHA256_WITH_RSA), element(NULL, ByteArray(0)))
        val issuer = sequence(element(SET, sequence(element(OID, COMMON_NAME), element(UTF8_STRING, "Maeve".toByteArray()))))
        val validity = sequence(element(UTC_TIME, NOT_BEFORE), element(GENERALIZED_TIME, NOT_AFTER))
        val tbs = sequence(
            element(TAGGED_0, VERSION),
            element(INTEGER, SERIAL),
            algorithm, issuer, validity, issuer,
            keyPair.public.encoded,
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }
        val der = sequence(tbs, algorithm, element(BIT_STRING, byteArrayOf(0) + signature))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    private fun sequence(vararg parts: ByteArray) = element(SEQUENCE, parts.fold(ByteArray(0)) { a, b -> a + b })

    private fun element(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val n = content.size
        when {
            n < 0x80 -> out.write(n)
            n < 0x100 -> { out.write(0x81); out.write(n) }
            n < 0x10000 -> { out.write(0x82); out.write(n ushr 8); out.write(n and 0xff) }
            else -> { out.write(0x83); out.write(n ushr 16); out.write((n ushr 8) and 0xff); out.write(n and 0xff) }
        }
        out.write(content)
        return out.toByteArray()
    }

    internal fun tlv(tag: Int, content: ByteArray): ByteArray = element(tag, content)
}

package com.daiyimo.maeve.core.adb

import java.math.BigInteger

/**
 * edwards25519 上的点,用扩展齐次坐标 (x:y:z:t) 表示,满足 x=X/Z、y=Y/Z、xy=T/Z。
 * 点加、取负、标量乘的公式与字节编解码都按 RFC 8032 §5.1。只给配对时的 SPAKE2 用:
 * 每次配对只做几次标量乘,BigInteger 足够快;也不追求常数时间——私钥是一次性的,
 * 配对码在局域网对话框里本来就可见。
 */
internal class EdPoint private constructor(
    private val x: BigInteger,
    private val y: BigInteger,
    private val z: BigInteger,
    private val t: BigInteger,
) {
    /** 完整的点加(add-2008-hwcd-3,RFC 8032 §5.1.4),a–h 是中间量。 */
    operator fun plus(other: EdPoint): EdPoint {
        val a = (y - x) * (other.y - other.x) % P
        val b = (y + x) * (other.y + other.x) % P
        val c = t * D2 % P * other.t % P
        val d = z * TWO * other.z % P
        val e = b - a
        val f = d - c
        val g = d + c
        val h = b + a
        return EdPoint(e * f % P, g * h % P, f * g % P, e * h % P)
    }

    /** 逆元 (-x, y, z, -t);x、t 先归约到 [0, P) 再取补,避免负坐标。 */
    operator fun unaryMinus() = EdPoint(P - x.mod(P), y, z, P - t.mod(P))

    operator fun minus(other: EdPoint) = this + (-other)

    /** k·this,k ≥ 0;从最高位到最低位的二进制标量乘。 */
    fun times(k: BigInteger): EdPoint {
        require(k.signum() >= 0)
        var result = IDENTITY
        for (bit in k.bitLength() - 1 downTo 0) {
            result += result
            if (k.testBit(bit)) result += this
        }
        return result
    }

    /** 归约到仿射坐标 (x, y),两值都在 [0, P) 内。 */
    fun affine(): Pair<BigInteger, BigInteger> {
        val zInv = zInverse()
        return (x * zInv).mod(P) to (y * zInv).mod(P)
    }

    /** 32 字节编码:y 按小端铺开,最高位放 x 的奇偶性。 */
    fun encode(): ByteArray {
        val zInv = zInverse()
        val affineX = x * zInv % P
        val affineY = (y * zInv).mod(P)
        val encoded = toLe(affineY, 32)
        if (affineX.mod(P).testBit(0)) encoded[31] = (encoded[31].toInt() or 0x80).toByte()
        return encoded
    }

    /** 同一条曲线上的同一个点:交叉相乘比较 x/z 与 y/z。 */
    override fun equals(other: Any?): Boolean {
        if (other !is EdPoint) return false
        if ((x * other.z - other.x * z).mod(P).signum() != 0) return false
        return (y * other.z - other.y * z).mod(P).signum() == 0
    }

    override fun hashCode(): Int = encode().contentHashCode()

    /** z 在模 P 下的逆;[affine] 与 [encode] 都要用到。 */
    private fun zInverse(): BigInteger = z.mod(P).modInverse(P)

    companion object {
        // 不用 BigInteger.TWO:该字段安卓 13 才有,安卓 12 上一加载该类就抛 NoSuchFieldError,配对时整个进程崩掉
        private val TWO = BigInteger.valueOf(2)
        /** 域模数 2^255 − 19。 */
        val P: BigInteger = TWO.pow(255) - BigInteger.valueOf(19)
        /** 素数阶子群的阶。 */
        val L: BigInteger = TWO.pow(252) + BigInteger("27742317777372353535851937790883648493")
        /** 曲线参数 d = −121665/121666 (mod P)。 */
        private val D: BigInteger = (BigInteger.valueOf(-121665) * BigInteger.valueOf(121666).modInverse(P)).mod(P)
        /** 2d (mod P),点加里乘 T 坐标用。 */
        private val D2 = D * TWO % P
        /** sqrt(−1) (mod P)。 */
        private val SQRT_M1: BigInteger = TWO.modPow((P - BigInteger.ONE).shiftRight(2), P)

        /** 仿射单位元 (0, 1)。 */
        val IDENTITY = EdPoint(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

        val BASE: EdPoint = requireNotNull(
            decode(hex("5866666666666666666666666666666666666666666666666666666666666666")),
        )

        /** RFC 8032 §5.1.3 解码;长度不对、y 越界、解不出 x 或编码不规范都返回 null。 */
        fun decode(bytes: ByteArray): EdPoint? {
            if (bytes.size != 32) return null
            val buf = bytes.copyOf()
            val sign = (buf[31].toInt() ushr 7) and 1
            buf[31] = (buf[31].toInt() and 0x7f).toByte()
            val y = fromLe(buf)
            if (y >= P) return null
            // 由 -x² + y² = 1 + d·x²·y² 得 x = ±sqrt((y²−1)/(d·y²+1))
            val ySquared = y * y % P
            val numerator = (ySquared - BigInteger.ONE).mod(P)
            val denominator = (D * ySquared + BigInteger.ONE) % P
            val vCubed = denominator * denominator % P * denominator % P
            val vSeventh = vCubed * vCubed % P * denominator % P
            var x = numerator * vCubed % P * (numerator * vSeventh % P).modPow((P - BigInteger.valueOf(5)).shiftRight(3), P) % P
            val residual = denominator * x % P * x % P
            x = when {
                residual == numerator -> x
                residual == (P - numerator).mod(P) -> x * SQRT_M1 % P
                else -> return null
            }
            if (x.signum() == 0 && sign == 1) return null
            if (x.testBit(0) != (sign == 1)) x = P - x
            return EdPoint(x, y, BigInteger.ONE, x * y % P)
        }

        fun fromLe(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

        fun toLe(n: BigInteger, len: Int): ByteArray {
            val bigEndian = n.toByteArray()
            val little = ByteArray(len)
            for (i in 0 until minOf(len, bigEndian.size)) little[i] = bigEndian[bigEndian.size - 1 - i]
            require(bigEndian.size <= len || bigEndian.copyOfRange(0, bigEndian.size - len).all { it == 0.toByte() }) { "value too large" }
            return little
        }

        fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

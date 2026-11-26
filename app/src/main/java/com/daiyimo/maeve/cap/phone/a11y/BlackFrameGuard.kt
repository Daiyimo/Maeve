package com.daiyimo.maeve.cap.phone.a11y

/** 启动/转场黑帧不等于防截屏;只有连续黑屏超过宽限期才判为受保护。 */
internal class BlackFrameGuard(
    private val now: () -> Long,
    private val graceMs: Long = 350L,
) {
    private var blackSince: Long? = null

    fun reset() { blackSince = null }

    fun isProtected(black: Boolean?): Boolean {
        if (black != true) {
            blackSince = null
            return false
        }
        val timestamp = now()
        val since = blackSince ?: timestamp.also { blackSince = it }
        return timestamp - since >= graceMs
    }
}

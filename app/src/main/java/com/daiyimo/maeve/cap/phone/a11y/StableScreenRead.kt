package com.daiyimo.maeve.cap.phone.a11y

/**
 * 界面稳定不能只看无障碍事件:自绘页面可能一直不发事件,但内容还在变。
 * 连续读到相同的内容指纹、控件树指纹和前台包,并维持一个安静间隔才算稳定。
 */
internal object StableScreenRead {
    const val POLL_MS = 100L
    const val QUIET_MS = 500L
    data class Reading(val content: Long, val tree: Long, val topPackage: String?)

    suspend fun await(timeoutMs: Long, now: () -> Long, sleep: suspend (Long) -> Unit, read: () -> Reading): Boolean {
        val deadline = now() + timeoutMs.coerceAtLeast(0)
        var previous = read()
        var matchingSince: Long? = null
        while (true) {
            val beforeSleep = now()
            if (beforeSleep >= deadline) return false
            sleep(minOf(POLL_MS, deadline - beforeSleep).coerceAtLeast(1))
            val current = read()
            val observedAt = now()
            if (current == previous) {
                val since = matchingSince ?: observedAt
                matchingSince = since
                if (observedAt - since >= QUIET_MS) return true
            } else {
                previous = current
                matchingSince = null
            }
        }
    }
}

package com.daiyimo.maeve.core.shell

/**
 * Maeve 交给内置助手的心愿：自己的进程被杀后要不要拉起来（[revive]），拉起来前要不要先把 Maeve 的无障碍服务写回系统列表
 * （[accessibility]）。助手读不到 Maeve 的设置，只认 Maeve 最后一次经 Binder 交来的这一份（见 [RestoreWishSender]）。
 */
data class RestoreWish(val revive: Boolean, val accessibility: Boolean) {
    /** 走 AIDL 时传的整数。 */
    val flags: Int get() = (if (revive) REVIVE else 0) or (if (accessibility) ACCESSIBILITY else 0)

    companion object {
        const val REVIVE = 1
        const val ACCESSIBILITY = 2

        /** 助手刚起、Maeve 还没交来心愿时：什么都不做。 */
        val NONE = RestoreWish(revive = false, accessibility = false)

        fun fromFlags(flags: Int) = RestoreWish(flags and REVIVE != 0, flags and ACCESSIBILITY != 0)

        /**
         * 只有胶囊常驻或有任务在跑，才值得被杀后拉起来；否则用户强行停止就让它停着。
         * 无障碍只在用户没在 Maeve 里关掉「替我打开无障碍」（[accessibilityWanted]）、且当前系统列表里确实有 Maeve
         * （[accessibilityOn]）时才写回：用户趁 Maeve 活着在系统设置里关掉的，不替他打开。
         */
        fun of(capsuleAlways: Boolean, taskRunning: Boolean, accessibilityWanted: Boolean, accessibilityOn: Boolean): RestoreWish {
            val revive = capsuleAlways || taskRunning
            val accessibility = revive && accessibilityWanted && accessibilityOn
            return RestoreWish(revive = revive, accessibility = accessibility)
        }
    }
}

/**
 * Maeve 这一侧：把心愿交给当前手里的助手。只在换了助手（新 Binder）或心愿变了时交；交失败不记账，下次重交。
 * [send] 的返回值表示这次到底交到了没有。
 */
class RestoreWishSender<T : Any>(private val send: (T, RestoreWish) -> Boolean) {
    private var boundTarget: T? = null
    private var boundWish: RestoreWish? = null

    @Synchronized
    fun update(current: T?, wish: RestoreWish) {
        if (current == null) return clearDelivery()
        if (current === boundTarget && wish == boundWish) return
        if (!send(current, wish)) return clearDelivery()
        boundTarget = current
        boundWish = wish
    }

    private fun clearDelivery() {
        boundTarget = null
        boundWish = null
    }
}

/** 助手在交接时告诉 Maeve：上次把 Maeve 拉起来的时间、最近窗口内拉了几次、上次因拉得太勤而作罢的时间（墙上毫秒，0 表示没有）。 */
data class HelperRestoreReport(val lastRestoredAtMs: Long, val recent: Int, val gaveUpAtMs: Long) {
    val empty: Boolean get() = lastRestoredAtMs == 0L && gaveUpAtMs == 0L
}

/**
 * 助手这一侧：Maeve 进程没了、过了 [GRACE_MS] 仍未回来时，判断要不要把它拉起来。
 * 只依据 Maeve 最后交来的 [RestoreWish]；[windowMs] 内最多拉 [maxAttempts] 次，拉起来又马上死就停手等窗口过去。
 */
class HelperRestorePolicy(
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val windowMs: Long = WINDOW_MS,
) {
    enum class Decision {
        Restore,
        /** Maeve 没要求（胶囊不常驻，也没有任务在跑）。 */
        NotWanted,
        /** 等的这段时间里 Maeve 自己回来了（或正在交接）。 */
        AppBack,
        /** 最近拉得太频繁：拉起来马上又死，不再拉。 */
        BackedOff,
    }

    private val recentAttempts = ArrayDeque<Long>()
    private var lastRestoreAt = 0L
    private var gaveUpAt = 0L

    @Synchronized
    fun decide(wish: RestoreWish, appBack: Boolean, now: Long): Decision = when {
        appBack -> Decision.AppBack
        !wish.revive -> Decision.NotWanted
        else -> {
            evictStale(now)
            if (recentAttempts.size >= maxAttempts) {
                gaveUpAt = now
                Decision.BackedOff
            } else {
                recentAttempts.addLast(now)
                lastRestoreAt = now
                Decision.Restore
            }
        }
    }

    @Synchronized
    fun report(now: Long): HelperRestoreReport {
        evictStale(now)
        return HelperRestoreReport(lastRestoreAt, recentAttempts.size, gaveUpAt)
    }

    /** 清掉滑出窗口的记录；时钟倒退（now 小于记录）时也清，看不准就不算。 */
    private fun evictStale(now: Long) {
        while (recentAttempts.isNotEmpty() && evictable(recentAttempts.first(), now)) {
            recentAttempts.removeFirst()
        }
    }

    /** 落在 [at] 的这条记录该不该清：滑出了窗口，或时钟倒退（[now] 小于它）看不准。 */
    private fun evictable(at: Long, now: Long): Boolean = now - at >= windowMs || now < at

    companion object {
        /** Maeve 死后先等这么久：系统或用户可能马上把它重新打开，那就不用助手动手。 */
        const val GRACE_MS = 8_000L
        const val MAX_ATTEMPTS = 3
        const val WINDOW_MS = 10 * 60_000L
    }
}

/** 助手拉起 Maeve 前，把无障碍服务写回的命令（安卓原版 settings 的写法）。 */
object RestoreCommands {
    const val ENABLE_ACCESSIBILITY = "settings put secure accessibility_enabled 1"

    /** 列表里没有 Maeve 就追加（不动别的服务），已经有了就只打总开关；[current] 是 `settings get` 的原样输出。 */
    fun accessibility(current: String?, component: String): List<String> =
        if (AccessibilityList.contains(current, component)) listOf(ENABLE_ACCESSIBILITY)
        else PermissionCommands.writeAccessibility(AccessibilityList.with(current, component))
}

/** Maeve 启动时看到的无障碍服务问题：被从系统列表里拿掉，或还在列表里却一直没绑定。 */
enum class A11yLoss { Removed, Unbound }

/** 上次在 Maeve 启动时看到的问题及时间（墙上毫秒）。 */
data class A11yLossRecord(val loss: A11yLoss, val atMs: Long)

object A11yStartCheck {
    /** 等服务连上的时长：进程刚起时系统通常几秒内就会绑定。 */
    const val CONNECT_WAIT_MS = 5_000L

    /**
     * [initial] 是 Maeve 刚起来时的样子，[connectedSoon] 是 [CONNECT_WAIT_MS] 内是否连上；
     * [expected] 为 false（服务从未连上过，例如还没设置）时不算问题。
     */
    fun loss(initial: A11yObservation, connectedSoon: Boolean, expected: Boolean): A11yLoss? = when {
        !expected || initial.connected -> null
        !initial.enabled -> A11yLoss.Removed
        !connectedSoon -> A11yLoss.Unbound
        else -> null
    }
}

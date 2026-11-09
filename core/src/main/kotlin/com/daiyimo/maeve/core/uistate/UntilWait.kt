package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.tool.Until
import com.daiyimo.maeve.core.tool.UntilOutcome
import com.daiyimo.maeve.core.run.waitOrSteer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 两次读屏至少隔这么久:界面事件一阵一阵地来,不必每条都读一遍。 */
private const val MIN_GAP_MS = 200L
/** 端口不报界面事件时,隔这么久重读一次;也是两次查看用户有没有插话的最长间隔。 */
private const val POLL_MS = 500L
/** 等「界面稳定」时,每次交给端口等的上限(端口按自己的安静时长判)。 */
private const val STABLE_SLICE_MS = 1_500L
/** 意外要连着读到几次才算数(页面换页时,新旧窗口会短暂同时在场)。 */
private const val CONFIRM_READS = 2

/**
 * 一批动作里、一步做完之后的等待:按 [Until] 逐次读屏(不登记编号)做字面比较,手机、网页同一套,只经 [ScreenPort] 与读屏结果。
 * 端口有界面事件时提前醒来([ScreenPort.awaitChange]),否则隔一会儿重读。看出意外(弹框、别的包盖上来、换了 App)、
 * 用户插话、到了时限就不再等,交回去由模型判断。基准是开始等时的那一屏:这一步本身引起的切换,由执行入口另外核对。
 */
internal class UntilWait(
    private val screen: ScreenPort,
    private val read: suspend () -> ScreenSnapshot?,
    private val clock: () -> Long,
    private val interrupted: () -> Boolean,
) {
    suspend fun run(until: Until): UntilOutcome {
        val began = clock()
        val limit = until.timeoutMs.coerceIn(0, Until.MAX_MS)
        // 时钟不走(测试、时钟出错)时也一定收场:每轮至少花掉 MIN_GAP_MS
        val roundCap = limit / MIN_GAP_MS + 2
        var reference: ScreenSnapshot? = null
        var stableReported = false
        var oddity: String? = null
        var streak = 0
        fun finish(met: Boolean, stopped: String?) = UntilOutcome(met, stopped, (clock() - began).coerceAtLeast(0))
        repeat(roundCap.toInt()) {
            val frame = read()
            if (frame != null) {
                val first = reference ?: frame.also { reference = it }
                // 有意外时这一轮不算成立:弹框后面露出的字,不代表能接着做
                val issue = unexpected(first, frame, until)
                if (issue == null && (!until.stable || stableReported) && holds(until, frame)) return finish(true, null)
                if (issue != null && issue == oddity) streak++ else { oddity = issue; streak = if (issue == null) 0 else 1 }
                // 页面弹框挂着时页面是停着的,不必再读一次确认
                if (oddity != null && (streak >= CONFIRM_READS || frame.web?.dialog != null)) return finish(false, oddity)
            }
            if (interrupted()) return finish(false, UntilOutcome.NEW_MESSAGE)
            val remaining = limit - (clock() - began)
            if (remaining <= 0) return finish(false, UntilOutcome.TIMEOUT)
            // 每一段等待都在用户插话时立刻结束(见 waitOrSteer),不等到下一轮才看
            if (until.stable) {
                stableReported = waitOrSteer { waitStable(minOf(remaining, STABLE_SLICE_MS)) } ?: return finish(false, UntilOutcome.NEW_MESSAGE)
            } else if (waitOrSteer { awaitChange(minOf(remaining, POLL_MS)) } ?: return finish(false, UntilOutcome.NEW_MESSAGE)) {
                delay(MIN_GAP_MS)
            }
        }
        return finish(false, UntilOutcome.TIMEOUT)
    }

    private suspend fun waitStable(ms: Long): Boolean =
        try { screen.waitStable(ms) } catch (e: CancellationException) { throw e } catch (e: Exception) { delay(ms); false }

    private suspend fun awaitChange(ms: Long): Boolean =
        try { screen.awaitChange(ms) } catch (e: CancellationException) { throw e } catch (e: Exception) { delay(ms); false }

    companion object {
        /** 条件此刻是否成立(只看给了的那几项);和动作结果里 expect 的核对一样,按元素名字比,不分大小写。 */
        fun holds(until: Until, s: ScreenSnapshot): Boolean {
            val labels = s.elements.map { it.name }
            until.appears?.trim()?.let { needle -> if (labels.none { it.contains(needle, ignoreCase = true) }) return false }
            // 什么都没读到(受保护、读不到)不算数,不算「已经没了」
            until.gone?.trim()?.let { absent -> if (labels.isEmpty() || labels.any { it.contains(absent, ignoreCase = true) }) return false }
            until.place?.trim()?.let { where -> if (!inPlace(s, where)) return false }
            return true
        }

        private fun inPlace(s: ScreenSnapshot, p: String) =
            s.packageName?.contains(p, ignoreCase = true) == true || s.web?.url?.contains(p, ignoreCase = true) == true

        /**
         * 和开始等时那一屏 [base] 比,此刻 [now] 有没有明显的意外;没有为 null。
         * 页面弹框挂着、同一 App 多出一个应用窗口,为 [UntilOutcome.POPUP];多出别的包的应用或系统窗口,为 [UntilOutcome.SYSTEM_DIALOG];
         * 前台换成别的 App(且不是 [until] 要等的那个)为 [UntilOutcome.APP_SWITCHED]。网页换站点是正常跳转,不算换了 App。
         */
        fun unexpected(base: ScreenSnapshot, now: ScreenSnapshot, until: Until): String? {
            if (now.web?.dialog != null && base.web?.dialog == null) return UntilOutcome.POPUP
            val foreground = now.packageName
            val foreignWindows = { s: ScreenSnapshot ->
                s.windows.filter { it.type in OVERLAY_TYPES && it.packageName != null && it.packageName != s.packageName }.map { it.id }.toSet()
            }
            if ((foreignWindows(now) - foreignWindows(base)).isNotEmpty()) return UntilOutcome.SYSTEM_DIALOG
            if (now.web == null && foreground != null && foreground != base.packageName && until.place?.let { inPlace(now, it.trim()) } != true) return UntilOutcome.APP_SWITCHED
            val ownWindows = { s: ScreenSnapshot -> s.windows.count { it.type == APPLICATION && it.packageName == foreground } }
            if (foreground == base.packageName && ownWindows(now) > ownWindows(base)) return UntilOutcome.POPUP
            return null
        }

        private const val APPLICATION = "application"
        private val OVERLAY_TYPES = setOf(APPLICATION, "system")
    }
}

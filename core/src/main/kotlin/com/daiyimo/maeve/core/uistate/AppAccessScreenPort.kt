package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.access.AppAccessDenied
import com.daiyimo.maeve.core.access.AppAccessPolicy
import com.daiyimo.maeve.core.access.AppAccessState
import kotlinx.coroutines.delay

/**
 * 套在手机端口外面的应用访问边界。这里把 [ScreenPort] 的每个成员一个一个显式转交,不用 `by` 委托:
 * 端口将来添了新成员,若不在这里显式处理就会落到接口默认实现上,进不到里面的端口——测试逐成员盯着这件事。
 *
 * 读屏与动作前后各核对一次前台窗口归谁;按目标打开的那几个(App、分享、点名了 App 的链接)只核目标。
 * 窗口探针 [packages] 只问归属,不采集内容,检查本身也不创建屏幕;禁用名单为空时不做任何额外的事。
 *
 * 少问窗口:[snapshot]、[peek] 拿读到的这一屏自带的窗口归属核对,不另问;给了 [windowToken](窗口一变就跟着变的计数)后,
 * 已核过的结果在窗口、名单、保护状态都没变且没过 [CHECK_TTL_MS] 时直接沿用(一次读屏后紧接的截图、预览的一帧帧画面)。
 */
class AppAccessScreenPort(
    private val inner: ScreenPort,
    override val appAccess: AppAccessPolicy,
    private val packages: suspend () -> List<String?>,
    private val protectedUi: () -> Boolean = { false },
    private val ownPackage: String? = null,
    private val windowToken: (() -> Long)? = null,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : ScreenPort {
    /** 上一次核对通过时记下的:窗口计数、名单版本、有没有在护 Maeve 自己的界面、通过的时刻。 */
    private class PassRecord(val windowCount: Long, val listRevision: Long, val ownUi: Boolean, val checkedAt: Long)

    @Volatile private var lastPass: PassRecord? = null

    /** 此刻要不要核对窗口:返回 null 是不必(名单空且没护自己的界面);否则带上刚读到的名单与保护状态。 */
    private fun gate(): Pair<AppAccessState, Boolean>? {
        val state = appAccess.current()
        if (!state.available) throw AppAccessDenied("app_access_unavailable", null)
        val guarded = protectedUi()
        if (state.blocked.isEmpty() && !guarded) return null
        return state to guarded
    }

    override suspend fun checkAppAccess() {
        val (state, guarded) = gate() ?: return
        if (cachedPassValid(state, guarded)) return
        val token = windowToken?.invoke()
        runVerify(state, packages(), guarded, token)
    }

    /** 上次核过的结果现在还能不能直接采信:窗口计数、名单版本、保护状态都没动,且距通过不超过 [CHECK_TTL_MS]。 */
    private fun cachedPassValid(state: AppAccessState, guarded: Boolean): Boolean {
        val cached = lastPass ?: return false
        val token = windowToken?.invoke() ?: return false
        return cached.windowCount == token && cached.listRevision == state.revision && cached.ownUi == guarded && clock() - cached.checkedAt <= CHECK_TTL_MS
    }

    /** 核对 [found] 里这些窗口的归属;有读不出归属的,等一会儿再问一次窗口。核过了就记下,给 [cachedPassValid] 之后沿用。 */
    private suspend fun runVerify(state: AppAccessState, found: List<String?>, guarded: Boolean, token: Long?) {
        lastPass = null
        val blind = state.blocked.isNotEmpty() && found.any { it.isNullOrBlank() }
        val (t, targets, current) = if (blind) {
            delay(RETRY_MS)
            Triple(windowToken?.invoke(), packages(), appAccess.current())
        } else {
            Triple(token, found, state)
        }
        if (!current.available) throw AppAccessDenied("app_access_unavailable", null)
        for (pkg in targets) {
            if (guarded && ownPackage != null && pkg == ownPackage) throw AppAccessDenied("app_access_protected", pkg)
            current.denial(pkg)?.let { throw it }
        }
        lastPass = t?.let { PassRecord(it, current.revision, guarded, clock()) }
    }

    /** 读一屏:读屏结果自带窗口归属,读完拿它核对;端口不给窗口层级时再退回问窗口。 */
    private suspend fun observed(read: suspend () -> ScreenSnapshot): ScreenSnapshot {
        val (state, guarded) = gate() ?: return read()
        // 计数要在读之前取:读的过程里窗口变了,下一次才不肯沿用
        val token = windowToken?.invoke()
        val snap = read()
        runVerify(appAccess.current(), snap.accessPackages() ?: packages(), guarded, token)
        return snap
    }

    /** 动作前后各核一次:两次都过了,才把里面的结果交出去。 */
    private suspend fun <T> betweenChecks(body: suspend () -> T): T {
        checkAppAccess()
        val value = body()
        checkAppAccess()
        return value
    }

    /** 按目标打开前先看这名在不在名单里;在就当场拒。 */
    private fun rejectIfBlocked(packageName: String) {
        appAccess.current().denial(packageName)?.let { throw it }
    }

    // ---------- 读屏 ----------
    override suspend fun snapshot() = observed { inner.snapshot() }
    override suspend fun peek() = observed { inner.peek() }
    override suspend fun screenshotPng() = betweenChecks { inner.screenshotPng() }
    override suspend fun readVisibleText() = betweenChecks { inner.readVisibleText() }
    override suspend fun frameGrid() = betweenChecks { inner.frameGrid() }
    override suspend fun isProtected() = betweenChecks { inner.isProtected() }
    override suspend fun checkScreen() = betweenChecks { inner.checkScreen() }

    // ---------- 在当前屏上的动作 ----------
    override suspend fun tapRef(ref: String, snapshotId: String) = betweenChecks { inner.tapRef(ref, snapshotId) }
    override suspend fun tapXY(x: Int, y: Int) = betweenChecks { inner.tapXY(x, y) }
    override suspend fun doubleTapRef(ref: String, snapshotId: String) = betweenChecks { inner.doubleTapRef(ref, snapshotId) }
    override suspend fun doubleTapXY(x: Int, y: Int) = betweenChecks { inner.doubleTapXY(x, y) }
    override suspend fun typeText(ref: String?, snapshotId: String?, text: String) = betweenChecks { inner.typeText(ref, snapshotId, text) }
    override suspend fun clearText(ref: String?, snapshotId: String?) = betweenChecks { inner.clearText(ref, snapshotId) }
    override suspend fun typeAt(x: Int, y: Int, text: String) = betweenChecks { inner.typeAt(x, y, text) }
    override suspend fun clearAt(x: Int, y: Int) = betweenChecks { inner.clearAt(x, y) }
    override suspend fun scroll(ref: String?, snapshotId: String?, down: Boolean) = betweenChecks { inner.scroll(ref, snapshotId, down) }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = betweenChecks { inner.swipe(x1, y1, x2, y2, durationMs) }
    override suspend fun idleScroll(fraction: Double) = betweenChecks { inner.idleScroll(fraction) }
    override suspend fun gesture(paths: List<List<Pair<Int, Int>>>, durationMs: Long) = betweenChecks { inner.gesture(paths, durationMs) }
    override suspend fun back() = betweenChecks { inner.back() }
    override suspend fun pressKey(keyCode: Int) = betweenChecks { inner.pressKey(keyCode) }
    override suspend fun setWheel(ref: String, snapshotId: String, value: String) = betweenChecks { inner.setWheel(ref, snapshotId, value) }

    // ---------- 等待与事件 ----------
    override suspend fun waitStable(timeoutMs: Long) = betweenChecks { inner.waitStable(timeoutMs) }
    override suspend fun awaitChange(timeoutMs: Long) = betweenChecks { inner.awaitChange(timeoutMs) }
    override suspend fun waitFor(condition: String, value: String?, timeoutMs: Long) = betweenChecks { inner.waitFor(condition, value, timeoutMs) }
    override suspend fun nextEvent(kind: String, packageName: String?, timeoutMs: Long) = betweenChecks { inner.nextEvent(kind, packageName, timeoutMs) }
    override suspend fun watchEvents(kind: String, packageName: String?, durationMs: Long, maxEvents: Int) =
        betweenChecks { inner.watchEvents(kind, packageName, durationMs, maxEvents) }

    // ---------- 按目标打开:可以离开受限的 App,只看目标 ----------
    override suspend fun openApp(packageName: String, cold: Boolean): ActionResult {
        rejectIfBlocked(packageName)
        return inner.openApp(packageName, cold)
    }

    override suspend fun openAppCopy(packageName: String, userId: Int, cold: Boolean): ActionResult {
        rejectIfBlocked(packageName)
        return inner.openAppCopy(packageName, userId, cold)
    }

    override suspend fun shareText(packageName: String, text: String): ActionResult {
        rejectIfBlocked(packageName)
        return inner.shareText(packageName, text)
    }

    /** 点名了 App 的在这里先看;没点名的由端口按名单挑接手的 App,谁接的只有端口知道。 */
    override suspend fun openLink(action: String?, uri: String?, packageName: String?): ActionResult {
        packageName?.let(::rejectIfBlocked)
        return inner.openLink(action, uri, packageName)
    }

    /** 只等占用登记,不读屏;目标跟打开时一样先过一遍,受限的 App 不用等,当场拒。 */
    override suspend fun awaitAppFree(packageName: String, userId: Int?, timeoutMs: Long): AppWait {
        rejectIfBlocked(packageName)
        return inner.awaitAppFree(packageName, userId, timeoutMs)
    }

    /** 接手的 App 由端口按名单挑(谁接的只有端口知道);做完后的前台由执行入口照常核对。 */
    override suspend fun systemAction(action: SystemAction) = inner.systemAction(action)

    // ---------- 不碰任何 App 界面的:原样转交 ----------
    override suspend fun systemCommand(command: String) = inner.systemCommand(command)
    override suspend fun listApps() = inner.listApps()
    override suspend fun otherProfiles() = inner.otherProfiles()
    override suspend fun appCopies() = inner.appCopies()
    override val afterTask: AfterTaskMarks? get() = inner.afterTask

    companion object {
        /** 有读不出归属的窗口时,隔多久再问一次(刚开的窗口要过一会儿才读得到)。 */
        const val RETRY_MS = 100L

        /** 核对结果在窗口没变时最多沿用多久;兜的是窗口事件晚到的那一小段。 */
        const val CHECK_TTL_MS = 1_000L
    }
}

/**
 * 这一屏里各 App 窗口都归谁,规则与端口的窗口探针一致:有 App 窗口就是它们的包名(根节点读不到的为 null);
 * 没有 App 窗口时是认得出的前台 App,连前台也没有就是空屏(空列表)。端口没给窗口层级(网页、测试端口)时返回 null,由调用方另问。
 */
internal fun ScreenSnapshot.accessPackages(): List<String?>? {
    val apps = windows.filter { it.type == "application" }
    if (apps.isNotEmpty()) return apps.map { it.packageName }
    if (hasWindow) return null
    return listOfNotNull(unreadableApp)
}

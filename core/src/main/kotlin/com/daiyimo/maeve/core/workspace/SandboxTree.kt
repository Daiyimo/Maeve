package com.daiyimo.maeve.core.workspace

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * `files/sandbox/` 这棵树的主人:凡是往树里写,都要先在这里领许可。进程里只有一份,由 App 构造后交给要写的地方。
 *
 * - [exclusive]:改树的结构、或整片删除时用(安装/解包/换根文件系统、重置、清空或删掉工作区;
 *   App 里走 `SandboxDirs.exclusiveWrite`)。领到它时不会有人在写。
 * - [shared]:往已有的树里添东西时用(技能同步、跑命令、网页下载、附件导入)。可以同时有很多个,
 *   但与 [exclusive] 互斥:重置删到一半时,不会有人又在里面建目录(否则最后的 rmdir 会 ENOTEMPTY)。
 *
 * 只要有人在等 [exclusive],后到的 [shared] 也排在它后面,重置不会被源源的命令饿死。
 * 同一条协程链里已经持有时,再领直接放行,嵌套调用不会把自己锁死。
 *
 * - [lease]:给长驻进程(沙箱里的 stdio MCP server)的 shared。活多久持多久,但可以被收回:
 *   [exclusive] 一拿到入口就收掉所有租约(通知持有者停掉进程,许可当场释放),不必被一个长驻进程挡住几十分钟;
 *   漏关的租约也挡不住重置。换根文件系统、清工作区之后进程本来也跑不下去,停掉比让它在删掉的目录上接着跑干净。
 */
class SandboxTree {
    /** 进门的锁:[shared] 只短暂持有,[exclusive] 全程持有;所以写者排队时,新读者也跟着等。 */
    private val doorway = Mutex()

    /** 有 [shared] 在跑时被锁住。 */
    private val idle = Mutex()
    private val census = Mutex()
    private var active = 0

    /** 这条协程链此刻持有哪些树的许可。 */
    private class Ownership(val trees: Set<SandboxTree>) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Ownership>
    }

    private suspend fun ownedTrees(): Set<SandboxTree> =
        currentCoroutineContext()[Ownership]?.trees.orEmpty()

    suspend fun <T> exclusive(block: suspend () -> T): T {
        val alreadyOwned = ownedTrees()
        if (this in alreadyOwned) return block()
        return doorway.withLock {
            reclaimLeases()
            idle.withLock { withContext(Ownership(alreadyOwned + this)) { block() } }
        }
    }

    /** 已登记、还没交回的租约(包含仍在排队的);[exclusive] 拿着 [doorway] 时逐个收回。 */
    private val registrations = LinkedHashSet<Lease>()

    /**
     * 长期持有的 shared 许可,[Lease.close] 时释放。[exclusive] 要用时先调 [onRevoke](在 IO 线程上,
     * 可以阻塞着等进程退出),再释放许可;之后持有者照常 close,不会出错。排队期间被收回的,会在那次
     * [exclusive] 之后重新排队领取,交回的租约保证没被收回过。持有者在领到之后启动的东西要自己再看一次
     * [Lease.revoked]:收回可能恰好落在领到和启动之间。
     */
    suspend fun lease(onRevoke: () -> Unit): Lease {
        check(this !in ownedTrees()) { "lease() must not be called while holding the tree" }
        while (true) {
            val candidate = Lease(onRevoke)
            // 先登记再排队:排在入口上的租约也收得到,不会出现已经拿到许可却没登记、挡住重置的空档
            synchronized(registrations) { registrations += candidate }
            val answer = CompletableDeferred<Boolean>()
            val keeper = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                try {
                    shared {
                        answer.complete(candidate.markHeld())
                        candidate.released.await()
                    }
                } finally {
                    synchronized(registrations) { registrations -= candidate }
                    answer.complete(false)
                }
            }
            val granted = try {
                answer.await()
            } catch (e: Throwable) {
                candidate.close()
                keeper.cancel()
                throw e
            }
            if (granted) return candidate
        }
    }

    private suspend fun reclaimLeases() {
        val current = synchronized(registrations) { registrations.toList() }
        if (current.isEmpty()) return
        withContext(Dispatchers.IO + NonCancellable) { for (lease in current) lease.revoke() }
    }

    /** 见 [lease]。 */
    class Lease internal constructor(private val onRevoke: () -> Unit) : java.io.Closeable {
        internal val released = CompletableDeferred<Unit>()
        private var held = false

        /** 被 [exclusive] 收回过;收回后许可已经释放,持有者不该再往树里写。 */
        @Volatile
        var revoked = false
            private set

        /** 拿到许可时调用:在此之前已被收回的返回 false。 */
        internal fun markHeld(): Boolean = synchronized(this) {
            if (revoked) {
                released.complete(Unit)
                false
            } else {
                held = true
                true
            }
        }

        internal fun revoke() {
            val wasHeld = synchronized(this) {
                if (revoked || released.isCompleted) return
                revoked = true
                held
            }
            // 还在排队的不用停什么:它会排到这次之后重新领
            if (wasHeld) runCatching { onRevoke() }
            released.complete(Unit)
        }

        override fun close() {
            released.complete(Unit)
        }
    }

    suspend fun <T> shared(block: suspend () -> T): T {
        val alreadyOwned = ownedTrees()
        if (this in alreadyOwned) return block()
        doorway.withLock { census.withLock { if (active++ == 0) idle.lock() } }
        try {
            return withContext(Ownership(alreadyOwned + this)) { block() }
        } finally {
            withContext(NonCancellable) {
                census.withLock { if (--active == 0) idle.unlock() }
            }
        }
    }
}

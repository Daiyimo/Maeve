package com.daiyimo.maeve.core.resource

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * 一组同生共死的系统资源(虚拟屏、网页显示等):用 [acquire] 建,建好当场登记,[close] 时按登记的反序放掉。
 * 「建完交回调用方再记到字段」中间隔着一次挂起,任务恰在这时取消,建好的东西就没人记得放;登记在建的同一段里做,就没有这个空档。
 * 任意线程可用;每个资源只放一次,[close] 可重复调。
 */
class ResourceScope {
    private data class Hold(val context: CoroutineContext, val release: () -> Unit)

    private val monitor = Any()
    private val holds = ArrayDeque<Hold>()
    private var shut = false

    /**
     * 在 [context] 里调 [create],建好立即登记,收尾时同样在 [context] 里调 [release]。
     * 建好之后即使任务被取消(取消在交回时抛出),资源也已归作用域。作用域已关时建出来的立即放掉,并抛 [IllegalStateException]。
     */
    suspend fun <T> acquire(context: CoroutineContext, release: (T) -> Unit, create: suspend () -> T): T =
        withContext(context) {
            val value = create()
            val accepted = synchronized(monitor) {
                if (shut) false else true.also { holds.addLast(Hold(context) { release(value) }) }
            }
            if (!accepted) {
                release(value)
                throw IllegalStateException("resource scope already closed")
            }
            value
        }

    /** 按登记的反序放掉全部;一个放不掉不影响其余,放完后抛出第一个错误。不受取消打断。 */
    suspend fun close() {
        val pending = synchronized(monitor) {
            if (shut) return
            shut = true
            val drained = holds.toList()
            holds.clear()
            drained
        }
        var first: Throwable? = null
        for (index in pending.indices.reversed()) {
            val hold = pending[index]
            try {
                withContext(hold.context + NonCancellable) { hold.release() }
            } catch (error: Throwable) {
                val prior = first
                if (prior == null) first = error else prior.addSuppressed(error)
            }
        }
        first?.let { throw it }
    }
}

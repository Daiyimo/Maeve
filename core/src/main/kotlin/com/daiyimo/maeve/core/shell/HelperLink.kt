package com.daiyimo.maeve.core.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 一条指向常驻助手的「连接」：助手进程不随解绑退出，它的 Binder 由 [source] 给出（没有时为 null）。
 * 对外与按服务绑定的连接一致：[shell] 只在绑定期间有值；[dropped] 表示连上过、现在仍需要、却断了。
 * 解绑时由 [release] 让助手撤掉本次绑定留下的效果（虚拟屏、静音、剪贴板），等同于服务被销毁。
 *
 * [scope] 必须单线程顺序执行：先排队的解绑撤销会早于下一次绑定的发布，新任务建的屏不会被上次的撤销误删。
 */
class HelperLink<T : Any>(
    private val source: StateFlow<T?>,
    private val scope: CoroutineScope,
    private val release: (T) -> Unit,
    private val need: () -> Unit,
) {
    private val _shell = MutableStateFlow<T?>(null)
    val shell: StateFlow<T?> = _shell.asStateFlow()

    private val _dropped = MutableStateFlow(false)
    val dropped: StateFlow<Boolean> = _dropped.asStateFlow()

    private var bound = false

    /** 本次绑定期间拿到过助手：只有拿到过之后再消失，才算「断了」。 */
    private var heldOnce = false

    private var observer: Job? = null

    @Synchronized
    fun bind() {
        if (bound) return
        bound = true
        heldOnce = false
        observer = scope.launch {
            source.collect { current -> if (observe(current)) need() }
        }
    }

    /** 收下助手的最新状态，返回「绑定着却没有助手」：这时该请看门狗再起一个。 */
    @Synchronized
    private fun observe(current: T?): Boolean {
        if (!bound) return false
        _shell.value = current
        if (current == null) {
            _dropped.value = heldOnce
            return true
        }
        heldOnce = true
        _dropped.value = false
        return false
    }

    @Synchronized
    fun unbind() {
        if (!bound) return
        bound = false
        observer?.cancel()
        observer = null
        val previous = _shell.value
        _shell.value = null
        _dropped.value = false
        if (previous != null) releaseLater(previous)
    }

    /** 撤销只清掉本次绑定留下的效果，助手进程还在；放到 [scope] 上跑，别堵住调用方。 */
    private fun releaseLater(helper: T) {
        scope.launch { runCatching { release(helper) } }
    }

    /** 「重新连接」：绑定着但手里没有助手时，让看门狗立刻再起一次。 */
    fun reconnect() {
        val shouldAsk = synchronized(this) { bound && _shell.value == null }
        if (shouldAsk) need()
    }
}

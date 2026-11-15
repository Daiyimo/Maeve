package com.daiyimo.maeve.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/** 删掉一个对话会连带什么:有没有在跑或排队的任务(会被停掉),绑在它上面的定时任务有几条(一起删)。 */
data class DeleteImpact(val runningTask: Boolean, val schedules: Int)
class DeleteIncomplete(val code: String, val stages: List<String>) : IllegalStateException(code)

/**
 * 删掉一个对话:先删绑在它上面的定时任务(不让它们再开始新任务),再停掉它在跑、排队的任务并等它收尾
 * (收尾时存进的那一轮也在删的范围里),然后删它的工作区(附件、用户发来的屏幕、任务写的文件)和对话记录。
 * 运行记录(运行统计用)是全局的,不删。
 */
class ConversationDeleter(
    private val store: ConversationStore,
    /** 有任务在跑或排队的对话。 */
    private val active: StateFlow<List<String>>,
    /** 停掉这个对话所有在跑、排队的任务。 */
    private val cancelTasks: (conversationId: String) -> Unit,
    /** 绑在这个对话上的定时任务的编号。 */
    private val schedulesOf: (conversationId: String) -> List<String>,
    private val removeSchedule: (id: String) -> Unit,
    /** 删掉这个对话的工作区。 */
    private val deleteWorkspace: suspend (conversationId: String) -> Unit,
    /** 停任务后最多等它收尾这么久;超时保留数据并报告 pending。 */
    private val stopWaitMs: Long = 15_000,
    /** 读写定时任务和对话记录在哪儿做。 */
    private val io: CoroutineContext = Dispatchers.IO,
    private val forgetRemoteTasks: (String) -> Unit = {},
    private val deleteDrafts: (String) -> Unit = {},
) {
    private val mutation = Mutex()
    fun impact(conversationId: String): DeleteImpact =
        DeleteImpact(conversationId in active.value, schedulesOf(conversationId).size)

    suspend fun delete(conversationId: String) = mutation.withLock {
        withContext(io) {
            store.beginDeletion(conversationId)
            try { schedulesOf(conversationId).forEach { removeSchedule(it) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw DeleteIncomplete("delete_partial", listOf("schedules")) }
        }
        if (conversationId in active.value) {
            cancelTasks(conversationId)
            val stopped = withTimeoutOrNull(stopWaitMs) { active.first { conversationId !in it }; true } == true
            if (!stopped) throw DeleteIncomplete("delete_pending", listOf("tasks"))
        }
        // 保留会话作为重试入口，不能将附件清理失败宣告为完整删除。
        try { deleteWorkspace(conversationId) } catch (e: CancellationException) { throw e } catch (_: Exception) { throw DeleteIncomplete("delete_partial", listOf("workspace")) }
        withContext(io) {
            try { deleteDrafts(conversationId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw DeleteIncomplete("delete_partial", listOf("drafts")) }
            try { forgetRemoteTasks(conversationId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw DeleteIncomplete("delete_partial", listOf("remote")) }
            try { store.delete(conversationId); store.completeDeletion(conversationId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw DeleteIncomplete("delete_partial", listOf("conversation")) }
        }
    }

    /** 重启/超时后只继续已确认的删除；不重启旧任务或重放外部动作。 */
    suspend fun reconcile() {
        val pending = withContext(io) { store.pendingDeletions() }
        pending.forEach { id ->
            try { delete(id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* 保留 marker，下一轮重试。 */ }
        }
    }
}

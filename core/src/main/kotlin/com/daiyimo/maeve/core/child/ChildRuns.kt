package com.daiyimo.maeve.core.child

import com.daiyimo.maeve.core.agent.AgentLoop
import com.daiyimo.maeve.core.agent.TaskState
import com.daiyimo.maeve.core.run.RunId
import com.daiyimo.maeve.core.run.RunInbox
import com.daiyimo.maeve.core.run.RunOrigin
import com.daiyimo.maeve.core.run.RunSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ChildPhase { Running, Completed, Interrupted, Unknown }

@Serializable
data class ChildRecord(
    val id: String,
    val parentId: String,
    val phase: ChildPhase,
    val result: String? = null,
    val atMs: Long,
)

interface ChildRunStore {
    fun save(record: ChildRecord)
    fun get(id: String): ChildRecord?
    fun all(): List<ChildRecord>
}

class MemoryChildRunStore : ChildRunStore {
    private val records = linkedMapOf<String, ChildRecord>()

    @Synchronized
    override fun save(record: ChildRecord) {
        records[record.id] = record
    }

    @Synchronized
    override fun get(id: String): ChildRecord? = records[id]

    @Synchronized
    override fun all(): List<ChildRecord> = records.values.toList()
}

data class ChildResult(val id: RunId, val phase: ChildPhase, val text: String)

fun interface ChildRunHost {
    suspend fun spawn(spec: RunSpec, format: String?): ChildResult
}

/** 子 Run 先保存结论预览再交给父 Run;父 Run 消失时保留 Unknown 记录,不自动重跑。 */
class ChildAgentRunner(
    private val create: (RunSpec) -> AgentLoop,
    private val store: ChildRunStore,
    private val ownerAlive: (RunId) -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
) : ChildRunHost {
    private val active = HashMap<RunId, MutableSet<AgentLoop>>()

    override suspend fun spawn(spec: RunSpec, format: String?): ChildResult {
        require(spec.parent != null && spec.provenance.origin == RunOrigin.Child)
        val parent = requireNotNull(spec.parent)
        val agent = create(spec)
        track(parent, agent)
        store.save(ChildRecord(spec.id.value, parent.value, ChildPhase.Running, atMs = clock()))
        try {
            val end = agent.run(spec, RunInbox())
            val raw = answerOf(end)
            val phase = if (end == TaskState.Cancelled) ChildPhase.Interrupted else ChildPhase.Completed
            val checked = keepJsonValid(raw, format)
            // 只截短落盘预览;交回父 Run 的原文由 spawn_agent 按本次请求的额度截断并报告原长。
            val preview = checked.take(MAX_RESULT_CHARS)
            store.save(ChildRecord(spec.id.value, parent.value, phase, preview, clock()))
            if (!ownerAlive(parent)) {
                store.save(ChildRecord(spec.id.value, parent.value, ChildPhase.Unknown, preview, clock()))
                return ChildResult(spec.id, ChildPhase.Unknown, checked)
            }
            return ChildResult(spec.id, phase, checked)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                store.save(ChildRecord(spec.id.value, parent.value, ChildPhase.Interrupted, INTERRUPTED, clock()))
            }
            throw cancelled
        } finally {
            untrack(parent, agent)
        }
    }

    fun cancelAll(parent: RunId) {
        val running = synchronized(active) { active[parent]?.toList() }.orEmpty()
        running.forEach(AgentLoop::cancel)
    }

    private fun track(parent: RunId, agent: AgentLoop) {
        synchronized(active) {
            active.getOrPut(parent) { mutableSetOf() }.add(agent)
        }
    }

    private fun untrack(parent: RunId, agent: AgentLoop) {
        synchronized(active) {
            val children = active[parent] ?: return@synchronized
            children.remove(agent)
            if (children.isEmpty()) active.remove(parent)
        }
    }

    private fun answerOf(end: TaskState): String = when (end) {
        is TaskState.Succeeded -> end.answer
        is TaskState.Failed -> end.reason
        TaskState.Cancelled -> INTERRUPTED
        else -> "no result"
    }

    private fun keepJsonValid(raw: String, format: String?): String {
        if (format != "json") return raw
        val parsed = runCatching { Json.parseToJsonElement(raw) }
        return if (parsed.isSuccess) raw else "$raw\n(the child run's JSON did not pass validation; the original text is kept)"
    }

    companion object {
        /** 子任务记录的落盘预览上限,不是交回父任务的结果上限。 */
        const val MAX_RESULT_CHARS = 4000
        private const val INTERRUPTED = "interrupted"
    }
}

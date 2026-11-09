package com.daiyimo.maeve.core.mcp

import com.daiyimo.maeve.core.action.ActionExecutor
import com.daiyimo.maeve.core.action.ActionRecorder
import com.daiyimo.maeve.core.action.ApprovalSettings
import com.daiyimo.maeve.core.action.Origin
import com.daiyimo.maeve.core.action.execution
import com.daiyimo.maeve.core.guard.OperationGuards
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.run.Attendance
import com.daiyimo.maeve.core.run.MemoryRunStore
import com.daiyimo.maeve.core.run.RunEventBus
import com.daiyimo.maeve.core.run.RunId
import com.daiyimo.maeve.core.run.RunOrigin
import com.daiyimo.maeve.core.run.RunPhase
import com.daiyimo.maeve.core.run.RunProvenance
import com.daiyimo.maeve.core.run.RunSpec
import com.daiyimo.maeve.core.run.RunStore
import com.daiyimo.maeve.core.run.StoredRun
import com.daiyimo.maeve.core.tool.ToolDispatcher
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 传输层只要实现这个接口就够了；外部的连接方始终只能拿到运行编号和事件流。 */
fun interface McpTaskHost {
    fun submit(spec: RunSpec): McpTaskLink
}

/** 一次已提交任务的句柄：运行编号、事件流，加一个可选的取消入口。 */
class McpTaskLink(val id: RunId, val events: RunEventBus, val cancel: () -> Unit = {})

/**
 * 一次不经过模型的会话式 Run。
 *
 * 能力目录在创建时定死，每一次工具调用都走 ActionExecutor，
 * 因此和手机上其它来源一样要过操作守卫、审批与记录。
 */
class McpToolSession internal constructor(
    val spec: RunSpec,
    private val tools: ToolDispatcher,
    private val store: RunStore,
    approvals: ApprovalSettings,
    private val clock: () -> Long,
) {
    val events = RunEventBus()

    private val gate = Mutex()
    private var stepNo = 0
    private var closed = false
    private val offeredTools = tools.definitions().map { it.name }.toSet()
    private val executor = ActionExecutor(
        tools,
        OperationGuards(),
        null,
        gate = {},
        recorder = ActionRecorder(events, clock),
        offScreen = tools.offScreenGuard(),
        approvals = approvals,
        provenance = spec.provenance,
        autonomous = true,
    )

    init {
        store.save(StoredRun(spec.id, spec.threadId, spec.goal, RunPhase.Running, roundInProgress = true))
        events.event(RunEvent.State(clock(), "mcp_session_started", spec.id.value))
    }

    fun tools() = tools.definitions()

    suspend fun call(name: String, argumentsJson: String): JsonObject = gate.withLock {
        if (closed) return@withLock refusal("session_closed")
        if (name !in offeredTools) return@withLock refusal("unknown_tool")
        stepNo++
        val callId = "mcp_$stepNo"
        val outcome = executor.run(Part.ToolCall(callId, name, argumentsJson), Origin.McpTool(stepNo))
        val content = outcome.execution().content
        events.event(RunEvent.ToolResult(clock(), stepNo, name, content.toString(), null, null, 0, callId = callId))
        content
    }

    suspend fun close() = gate.withLock {
        if (closed) return@withLock
        closed = true
        savePhase(RunPhase.Ended, roundInProgress = false)
        events.event(RunEvent.State(clock(), "mcp_session_ended", spec.id.value))
    }

    /** 把会话阶段写回运行存储；记录可能已被外部清掉，那时跳过。 */
    private fun savePhase(phase: RunPhase, roundInProgress: Boolean) {
        store.get(spec.id)?.let { record ->
            store.save(record.copy(phase = phase, roundInProgress = roundInProgress))
        }
    }

    /** 工具被拒时也回一段结构化结果，让调用方按 error 码处理，不当成传输失败。 */
    private fun refusal(code: String) = buildJsonObject {
        put("ok", false)
        put("error", code)
    }
}

/** 本机传输桩的核心入口；真正的 socket / 授权层只要把外部操作映射到这两个方法。 */
class McpServer(
    private val assemble: (RunSpec) -> ToolDispatcher,
    private val tasks: McpTaskHost,
    private val store: RunStore = MemoryRunStore(),
    private val approvals: ApprovalSettings = ApprovalSettings.ASK,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** 开一个会话式 Run：调用方拿到能力目录，之后逐步调工具。 */
    fun openToolSession(threadId: String, attendance: Attendance = Attendance.Unattended): McpToolSession {
        val spec = newSpec("MCP tool session", threadId, attendance)
        return McpToolSession(spec, assemble(spec), store, approvals, clock)
    }

    /** 提交一个完整任务：调用方只拿到运行编号和事件流。 */
    fun submitTask(goal: String, threadId: String, attendance: Attendance = Attendance.Unattended): McpTaskLink {
        val spec = newSpec(goal, threadId, attendance)
        return tasks.submit(spec)
    }

    private fun newSpec(goal: String, threadId: String, attendance: Attendance) = RunSpec(
        id = RunId(UUID.randomUUID().toString()),
        goal = goal,
        threadId = threadId,
        provenance = RunProvenance(RunOrigin.Mcp, attendance),
    )
}

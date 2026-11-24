package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.trace.RunTrace
import com.daiyimo.maeve.core.action.ConfirmDecision
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelError
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.Role
import com.daiyimo.maeve.core.model.StreamingChatModel
import com.daiyimo.maeve.core.model.ToolSpec
import com.daiyimo.maeve.core.model.Usage
import com.daiyimo.maeve.core.record.Redactor
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.tool.ToolDispatcher
import com.daiyimo.maeve.core.tool.parseArgs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 一次模型调用:历史只追加,估算越过触发点才压缩一次([ContextCompressor],完整历史不动)、主模型一时的故障按 [retry] 重试、上下文超长时压得更狠再发一次,
 * lightSteps 打开时按 [router] 在大小模型之间分工(轻量模型出错不重试,这一步直接改问主模型)。
 * 主用途可以是 [ModelPool]:按成员能力/健康顺序故障转移,每个成员独立压缩与计量,换成员时剥离私有字段。每个任务开始时 [reset]。
 */
internal class ModelCaller(
    private val model: ChatModel,
    private val tools: ToolDispatcher,
    private val router: ModelRouter?,
    private val flags: SpeedFlags,
    private val retry: RetryPolicy,
    private val emit: (RunEvent) -> Unit,
    private val now: () -> Long,
    private val autonomous: Boolean = false,
    private val trace: RunTrace? = null,
) {
    private val mainPool = model.asPool()

    /** 每个模型一份压缩状态:各自的上下文、各自发出的那份历史,轻量模型那次压缩不连累主模型的请求前缀。 */
    private val compressors = java.util.IdentityHashMap<ChatModel, ContextCompressor>()
    private fun compressorFor(m: ChatModel) =
        compressors.getOrPut(m) { ContextCompressor(tools.index, structured = flags.structuredCompaction,
            diffScreens = tools.inlineScreen, triggerOverride = m.capabilities.compactionTriggerTokens) }
    private val compactions = java.util.IdentityHashMap<ChatModel, CompactionState>()
    /** 本任务里已为重试等过的时间(对着 [RetryPolicy.taskWaitBudgetMs])。 */
    private var taskRetryWaitMs = 0L

    /** 本任务的系统提示词:开工时按发给模型的同一份工具列表拼([SystemPrompt.assemble]),任务内不变。 */
    var systemPrompt: String = ""

    /** specs 的 token 估算缓存:按内容指纹,工具组不变时复用。systemPrompt 不缓存——它可能被直接赋值(不经 reset),
     * 缓存会过期;百 KB 文本的 O(n) 字符计数每步重算开销可接受,真正慢的是 specs 的 60-120 次估算。 */
    private var specsEstimateKey: String? = null
    private var specsEstimateValue: Int = 0

    /** 最近一次调用在成功那次请求之前花掉的时间(失败的请求、退避)。 */
    var lastRetryWaitMs = 0L
        private set

    /** 每条助手回复的实际生产者;换成员时只把私有数据回给产生它的模型。未知来源视同首选模型。 */
    private val producers = java.util.IdentityHashMap<Message, ChatModel>()

    /** 最近一次主用途调用实际回答的成员。旁路/收尾问主模型后,调用方可用它记账。 */
    var lastMainUsed: ChatModel = model
        private set

    /** 带真实用量的回复(按消息身份)和给出它的模型,是量请求大小的锚点([UsageMeter]);压缩改写前缀后清空。 */
    private val anchors = java.util.IdentityHashMap<Message, Pair<ChatModel, UsageMeter.Anchor>>()
    /** 每个模型最近一次请求的固定开销估算;回复进历史时和用量一起记进锚点。 */
    private val lastOverhead = java.util.IdentityHashMap<ChatModel, Int>()

    /** 固定开销估算:systemPrompt 每步重算(可能被直接赋值,缓存会过期),specs 按内容指纹缓存(60-120 个工具的真开销)。 */
    private fun cachedOverhead(specs: List<ToolSpec>): Int {
        val sp = TokenEstimate.of(systemPrompt)
        val key = specs.joinToString("|") { it.name + it.description.length + it.parametersJsonSchema.length }
        val specsPart = if (key == specsEstimateKey) specsEstimateValue
        else specs.sumOf { TokenEstimate.of(it.name + it.description + it.parametersJsonSchema) }.also {
            specsEstimateKey = key; specsEstimateValue = it
        }
        return sp + specsPart
    }

    fun reset(prompt: String) {
        systemPrompt = prompt
        specsEstimateKey = null
        specsEstimateValue = 0
        compactions.clear()
        taskRetryWaitMs = 0
        compressors.values.forEach { it.forget() }
        producers.clear()
        lastMainUsed = model
        anchors.clear()
        lastOverhead.clear()
    }

    /** 回复进了历史:记下实际生产者;有真实 usage 的记成上下文大小的锚点。 */
    fun noteReply(m: Message, used: ChatModel, usage: Usage?) {
        if (m.role == Role.Assistant) producers[m] = used
        val overhead = lastOverhead[used]
        if (usage != null && overhead != null) anchors[m] = used to UsageMeter.Anchor(usage, overhead)
    }

    /** 发给 [using] 的整份请求有多大:最近一条它给的、带真实用量的回复作锚点,没有就全量估算(见 [UsageMeter])。 */
    private fun requestTokens(messages: List<Message>, using: ChatModel, overhead: Int, scale: Double): Int =
        UsageMeter.of(messages, overhead, scale) { m -> anchors[m]?.takeIf { it.first === using }?.second }

    /** 本任务里分工是否在起作用:开关打开、轻量模型能接手机步、没因不可恢复的错误停用。 */
    private val routing: Boolean get() = flags.lightSteps && router?.active == true

    /** 开了分工就在运行记录里记每次调用的模型和角色(停用之后也记,便于对比)。 */
    val recordRoles: Boolean get() = flags.lightSteps && router != null

    fun roleOf(m: ChatModel) = if (router?.isLight(m) == true) ROLE_LIGHT else ROLE_MAIN

    /**
     * 按 [router] 选模型。轻量模型出错、没调工具(不带调用的回复是最终回答,只由主模型给),或要放弃、确认、接管、追问、做不可逆的事,
     * 它的回复不执行(作为 [ROLE_LIGHT_REJECTED] 记下用量),这一步改问主模型,改问前用 [restarted] 报新的起问时刻。
     * 返回回复和实际用的模型。
     */
    suspend fun routed(
        history: List<Message>, specs: List<ToolSpec>, track: StepTrack, step: Int, restarted: (Long) -> Unit,
        onText: ((String) -> Unit)? = null,
    ): Pair<ModelReply, ChatModel> {
        val r = router?.takeIf { routing } ?: return askRouted(history, specs, step, onText = onText)
        val picked = r.pick(track.context(step, history.any { m -> m.parts.any { it is Part.Image } }))
        if (!r.isLight(picked)) return askRouted(history, specs, step, onText = onText)
        // 轻量模型的上下文可能更小:它报超长、它那次折叠多少,都不连累主模型(压缩状态按模型分开记)
        val asked = now()
        val light = try {
            complete(history, specs, picked.asPool(), RetryPolicy.NONE, step, onText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Key、额度、模型名、请求本身不对,重试也没用:本任务余下的步都用主模型
            if (e is ModelError.Auth || e is ModelError.QuotaExceeded || e is ModelError.ModelNotFound ||
                e is ModelError.ModelNotAvailable || e is ModelError.ToolsUnsupported ||
                e is ModelError.ChatGptLimitReached || e is ModelError.ChatGptNotEligible || e is ModelError.SignInExpired || e is ModelError.AccountPlanNotAllowed ||
                (e is ModelError.Server && e.status in 400..499)) r.disable()
            null
        }
        val first = light?.first?.parts?.filterIsInstance<Part.ToolCall>()?.firstOrNull()
        if (light != null && first != null && !needsMain(first)) return light.first to light.second
        r.noteTrouble()
        val text = light?.first?.parts?.filterIsInstance<Part.Text>()?.joinToString("") { it.text }?.trim().orEmpty()
        emit(RunEvent.ModelReply(
            now(), step, RunEvent.clip(Redactor.text(text)), first?.name, first?.let { Redactor.args(tools.verb(it.name), it.argsJson, secretField = true) },
            light?.first?.usage?.inputTokens, light?.first?.usage?.outputTokens, light?.first?.usage?.cachedInputTokens, now() - asked,
            light?.second?.name ?: picked.name, ROLE_LIGHT_REJECTED,
        ))
        emit(RunEvent.State(now(), "light_to_main", first?.name ?: if (light == null) "light_error" else "no_tool"))
        onText?.invoke("")
        restarted(now())
        return askRouted(history, specs, step, onText = onText)
    }

    private fun needsMain(call: Part.ToolCall): Boolean =
        ModelRouter.mainOnly(call.name, tools.verb(call.name)) ||
            (!autonomous && ConfirmDecision.mayBeIrreversible(tools, call.name, parseArgs(call.argsJson)))

    /** 问 [using](默认主模型);主用途可以是池,失败按健康策略换下一个成员。 */
    suspend fun ask(history: List<Message>, specs: List<ToolSpec>, step: Int, using: ChatModel = model, onText: ((String) -> Unit)? = null): ModelReply =
        askRouted(history, specs, step, using, onText).first

    /** 同 [ask],返回实际回答的成员;调用方记用量和消息来源时必须用这个模型。 */
    suspend fun askRouted(
        history: List<Message>, specs: List<ToolSpec>, step: Int, using: ChatModel = model, onText: ((String) -> Unit)? = null,
    ): Pair<ModelReply, ChatModel> {
        val result = if (using === model) complete(history, specs, mainPool, retry, step, onText)
            else complete(history, specs, using.asPool(), retry, step, onText)
        if (using === model) lastMainUsed = result.second
        return result
    }

    /**
     * 发给 [to] 的历史:别的模型给出的回复去掉思考内容和调用上的私有数据(只能原样回给产生它的模型)。
     * 没记到来源的旧历史视同首选模型给出;只看字段在不在,不分服务商。
     */
    private fun viewFor(history: List<Message>, to: ChatModel, preserveUnknown: Boolean = false): List<Message> {
        if (producers.isEmpty() && (to === model || preserveUnknown)) return history
        return history.map { m ->
            val from = producers[m]
            // 未知来源的旧历史保持主模型原样;只有明确发往别的模型才剥,避免无故破坏主模型回放。
            val foreign = m.role == Role.Assistant && (from?.let { !belongsTo(to, it) } ?: !preserveUnknown)
            if (!foreign || m.parts.none { it is Part.Thinking || (it is Part.ToolCall && it.opaque != null) }) m
            else m.copy(parts = m.parts.mapNotNull { p ->
                when {
                    p is Part.Thinking -> null
                    p is Part.ToolCall && p.opaque != null -> p.copy(opaque = null)
                    else -> p
                }
            })
        }
    }

    private fun belongsTo(to: ChatModel, from: ChatModel): Boolean =
        (to as? ModelPool)?.owns(from) ?: (to === from)

    /** 一次调用:逐个成员尝试;每个成员独立压缩、计量锚点和同成员重试。 */
    private suspend fun complete(history: List<Message>, specs: List<ToolSpec>, pool: ModelPool, policy: RetryPolicy, step: Int,
        onText: ((String) -> Unit)? = null): Pair<ModelReply, ChatModel> {
        lastRetryWaitMs = 0
        val began = now()
        val candidates = if (pool is SingleMemberPool) pool.members else pool.ordered(needOf(history, specs))
        if (candidates.isEmpty()) throw ModelError.Server(503)
        var last: Throwable? = null
        for ((index, member) in candidates.withIndex()) {
            val using = member.model
            val memberBegan = now()
            try {
                val memberRetries = if (candidates.size > 1) minOf(policy.maxRetries, 1) else policy.maxRetries
                val preserveUnknown = pool === mainPool && using === mainPool.members.first().model
                val reply = attemptMember(viewFor(history, using, preserveUnknown), specs, using, policy, memberRetries, step, onText, began)
                pool.noteResult(member, now() - memberBegan, null)
                return reply to using
            } catch (e: CancellationException) {
                throw e
            } catch (e: ModelError) {
                last = e
                pool.noteResult(member, now() - memberBegan, e)
                if (!FailoverPolicy.canFailover(e) || index == candidates.lastIndex) throw e
                emit(RunEvent.State(now(), "model_route", "${pool.groupName ?: "group"} ${member.label} -> ${candidates[index + 1].label} ${RetryPolicy.label(e)}"))
            } catch (e: Exception) {
                last = e
                pool.noteResult(member, now() - memberBegan, e)
                throw e
            }
        }
        throw last ?: ModelError.Server(503)
    }

    private fun needOf(history: List<Message>, specs: List<ToolSpec>): ModelNeed {
        val images = history.sumOf { m -> m.parts.count { it is Part.Image } }
        val overhead = cachedOverhead(specs)
        return ModelNeed(specs.isNotEmpty(), images > 0, images, TokenEstimate.of(history) + overhead)
    }

    /** 对单个成员的完整尝试:压缩、发请求、同成员重试、超长再压。 */
    private suspend fun attemptMember(history: List<Message>, specs: List<ToolSpec>, using: ChatModel, policy: RetryPolicy, maxRetries: Int, step: Int,
        onText: ((String) -> Unit)?, callBegan: Long): ModelReply {
        var retries = 0
        /** 空回复重发等掉的时间(算进 [lastRetryWaitMs])。 */
        var emptyWaitMs = 0L
        val overhead = cachedOverhead(specs)
        val contextTokens = using.capabilities.contextTokens
        val compressor = compressorFor(using)
        var compaction = compactions[using] ?: CompactionState()
        var overflowRetried = false
        var overflowRewrote = false
        while (true) {
            val current = ContextRecovery.ensure(history, compressor.current(history, contextTokens))
            val currentTokens = requestTokens(current, using, overhead, compressor.scale)
            val raw = compressor.compress(history, contextTokens, currentTokens, compaction.aggressive, overhead, using.capabilities.maxImages)
            if (raw.rewritten) anchors.clear()
            val restored = ContextRecovery.ensure(history, raw.messages)
            val projected = HistoryPruner.compactPhoneEvidence(restored)
            val sent = if (projected == raw.messages) raw else raw.copy(messages = projected, estimate = TokenEstimate.of(projected))
            val (next, report) = CompactionPolicy.sent(compaction, sent.foldedSteps)
            if (report) emit(RunEvent.Compressed(now(), sent.foldedSteps))
            compaction = next
            compactions[using] = compaction
            try {
                // 差异的 base 被裁掉或折叠时,下一次写操作要发全量
                if (tools.inlineScreen) tools.setVisibleObservations(visibleReads(sent.messages))
                // 这一次之前花掉的(失败的请求、退避)都算重试等待
                lastRetryWaitMs = now() - callBegan
                suspend fun send(): ModelReply = if (onText == null) using.complete(systemPrompt, sent.messages, specs)
                    else if (using is StreamingChatModel) using.completeStreaming(systemPrompt, sent.messages, specs, onText)
                    else using.complete(systemPrompt, sent.messages, specs).also { result ->
                        result.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
                            .takeIf(String::isNotEmpty)?.let(onText)
                    }
                val reply = if (trace == null) send() else {
                    val change = when {
                        overflowRewrote -> "overflow_compaction folded=" + raw.foldedSteps
                        raw.rewritten && current != sent.messages -> "history_rewritten folded=" + raw.foldedSteps
                        else -> null
                    }
                    overflowRewrote = false
                    trace.modelCall(step, using, tools::verb, change, sent.messages) { send() }
                }
                var filled = reply
                // 服务商偶尔回一份空内容:用量里写着这次 0 输出、既没有字也没有调用。这是这次请求根本没生成东西,
                // 同一个请求重发通常就好,不必惊动模型(真机上 step-3.7-flash 报「模型停止了操作」多半是这个)。
                // 没带用量信息的空回复不动它:分不清是服务商抽风还是模型真没话,照旧走提醒那条路。
                var emptyRetries = 0
                while (emptyRetries < EMPTY_RETRIES && filled.isStarved()) {
                    emptyRetries += 1
                    emit(RunEvent.State(now(), "model_empty_retry", "#$emptyRetries"))
                    delay(EMPTY_RETRY_WAIT_MS)
                    emptyWaitMs += EMPTY_RETRY_WAIT_MS
                    filled = send()
                }
                lastRetryWaitMs += emptyWaitMs
                return filled.also { r ->
                    lastOverhead[using] = overhead
                    r.usage?.let { compressor.calibrate(sent.estimate + overhead, it.inputTokens) }
                }
            } catch (e: ModelError.ContextTooLong) {
                onText?.invoke("")
                val (after, again) = CompactionPolicy.overflow(compaction, overflowRetried) {
                    compressor.overflow(history, contextTokens, overhead)
                }
                compaction = after
                compactions[using] = compaction
                overflowRetried = true
                if (again) { anchors.clear(); overflowRewrote = true }
                if (!again) throw e
            } catch (e: ModelError) {
                onText?.invoke("")
                val wait = policy.delayFor(e, retries, taskRetryWaitMs) ?: throw e
                // 多成员池把机会留给下一个;服务商要求长等时不要为备援空等。
                if (retries >= maxRetries || (maxRetries < policy.maxRetries && e is ModelError.RateLimited && wait > FAILOVER_RETRY_WAIT_BUDGET_MS)) throw e
                retries += 1
                taskRetryWaitMs += wait
                emit(RunEvent.State(now(), "model_retry", "${roleOf(using)} ${RetryPolicy.label(e)} #$retries ${wait}ms"))
                // 等待中取消任务就此结束
                delay(wait)
            }
        }
    }

    /** 既没有字也没有调用,而且服务商自己报告这次 0 输出:这次请求什么也没生成,重发一次多半就有了。 */
    private fun ModelReply.isStarved(): Boolean {
        val usage = usage ?: return false
        if (usage.outputTokens != 0) return false
        return parts.none { it is Part.ToolCall || (it is Part.Text && it.text.isNotBlank()) }
    }

    companion object {
        /** 空回复重发的次数;用完还是空就交回去,由循环那套提醒/收尾处理。 */
        private const val EMPTY_RETRIES = 2
        private const val EMPTY_RETRY_WAIT_MS = 500L

        /** 有备援时,超过这段 Retry-After 就直接换成员。 */
        private const val FAILOVER_RETRY_WAIT_BUDGET_MS = 5_000L

        /** [RunEvent.ModelReply.role] 的取值;分工没起作用时为 null。 */
        const val ROLE_MAIN = "main"
        const val ROLE_LIGHT = "light"
        /** 轻量模型的回复没执行,这一步改问了主模型;仍是一次模型调用,用量照记。 */
        const val ROLE_LIGHT_REJECTED = "light_rejected"
    }
}

/** 发出的历史里还完整可见的读取(按编号):带元素列表的读取结果与带 screen 对象的动作结果。 */
internal fun visibleReads(messages: List<Message>): Set<String> {
    val out = HashSet<String>()
    for (m in messages) for (p in m.parts) {
        if (p !is Part.ToolResult) continue
        val obj = try { Json.parseToJsonElement(p.content) as? JsonObject } catch (e: SerializationException) { null } ?: continue
        val shown = (obj["screen"] as? JsonObject) ?: obj.takeIf { it["elements"] is JsonArray } ?: continue
        (shown["snapshotId"] as? JsonPrimitive)?.contentOrNull?.let(out::add)
    }
    return out
}

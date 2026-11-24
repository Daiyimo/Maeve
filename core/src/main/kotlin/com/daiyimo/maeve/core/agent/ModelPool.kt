package com.daiyimo.maeve.core.agent

import com.daiyimo.maeve.core.model.Capabilities
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelError
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.Part
import com.daiyimo.maeve.core.model.Role
import com.daiyimo.maeve.core.model.StreamingChatModel
import com.daiyimo.maeve.core.model.ToolSpec
import com.daiyimo.maeve.core.net.ProxyFailure
import kotlinx.coroutines.CancellationException

/** 一次失败是否值得换成员,以及成员要冷却多久。所有时间都是单调毫秒。 */
object FailoverPolicy {
    /** 请求本身的问题换成员也解决不了;内容拒绝、上下文超长、坏响应和其他 4xx 留在原成员。 */
    fun canFailover(e: Throwable): Boolean = when (e) {
        is ModelError.RateLimited, is ModelError.Network, is ModelError.StreamCut -> true
        is ModelError.Proxy -> e.failure == ProxyFailure.Unreachable
        is ModelError.Server -> e.status >= 500 || e.status == 429
        is ModelError.Auth, is ModelError.QuotaExceeded, is ModelError.ModelNotFound,
        is ModelError.ModelNotAvailable, is ModelError.ToolsUnsupported,
        is ModelError.ChatGptLimitReached, is ModelError.ChatGptNotEligible,
        is ModelError.SignInExpired, is ModelError.AccountPlanNotAllowed -> true
        else -> false
    }

    /** Retry-After 优先;鉴权/额度冷却五分钟;其余可转移错误按连续失败指数退避。 */
    fun cooldownMs(e: Throwable, failures: Int): Long = when (e) {
        is ModelError.RateLimited -> (e.retryAfterMs ?: 30_000L).coerceIn(5_000L, 30 * 60_000L)
        is ModelError.Auth, is ModelError.QuotaExceeded, is ModelError.SignInExpired,
        is ModelError.ChatGptLimitReached, is ModelError.ChatGptNotEligible,
        is ModelError.AccountPlanNotAllowed -> 5 * 60_000L
        else -> (30_000L shl (failures - 1).coerceIn(0, 5)).coerceAtMost(10 * 60_000L)
    }
}

/** 一个成员最近一段时间的健康快照。只在内存里,进程重启清空。 */
data class MemberHealth(
    val consecutiveFailures: Int = 0,
    val calls: Long = 0,
    val failures: Long = 0,
    val cooldownUntilMs: Long = 0,
    val ewmaLatencyMs: Double = 0.0,
    val latencySamples: Int = 0,
    val lastError: String? = null,
) {
    /** 延迟 EWMA 乘连续失败惩罚;没有样本的成员不参与速度排序。 */
    fun score(): Double = if (latencySamples == 0) Double.MAX_VALUE
        else ewmaLatencyMs * (1 + 2.0 * consecutiveFailures)
}

/** 同一服务商的同一模型共享一份健康;不同配置、不同任务看到的冷却是同一份。 */
class ModelHealthRegistry(private val now: () -> Long = ::monotonicMillis) {
    private val states = HashMap<MemberKey, MemberHealth>()

    @Synchronized fun snapshot(key: MemberKey): MemberHealth = states[key] ?: MemberHealth()
    @Synchronized fun available(key: MemberKey): Boolean = now() >= (states[key]?.cooldownUntilMs ?: 0L)

    @Synchronized
    fun noteSuccess(key: MemberKey, latencyMs: Long) {
        val old = states[key] ?: MemberHealth()
        val samples = old.latencySamples + 1
        val ewma = if (old.latencySamples == 0) latencyMs.toDouble()
            else old.ewmaLatencyMs * 0.7 + latencyMs.coerceAtLeast(0) * 0.3
        states[key] = old.copy(
            consecutiveFailures = 0, calls = old.calls + 1, cooldownUntilMs = 0,
            ewmaLatencyMs = ewma, latencySamples = samples, lastError = null,
        )
    }

    @Synchronized
    fun noteFailure(key: MemberKey, error: Throwable) {
        val old = states[key] ?: MemberHealth()
        val unhealthy = FailoverPolicy.canFailover(error)
        val failures = if (unhealthy) old.consecutiveFailures + 1 else old.consecutiveFailures
        val cooldown = if (unhealthy) now() + FailoverPolicy.cooldownMs(error, failures) else old.cooldownUntilMs
        states[key] = old.copy(
            consecutiveFailures = failures, failures = old.failures + 1,
            cooldownUntilMs = cooldown, lastError = error::class.simpleName,
        )
    }

    @Synchronized fun reset() = states.clear()
}

/** 系统单调时钟,避免系统时间被校准后让冷却突然失效或延长。 */
internal fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

/** 同一对话固定在一个成员上;LRU 上限避免进程长期运行时无限增长。 */
class StickyTable(private val limit: Int = 512) {
    init { require(limit > 0) }
    private val map = object : LinkedHashMap<String, MemberKey>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MemberKey>): Boolean = size > limit
    }

    @Synchronized fun get(key: String): MemberKey? = map[key]
    @Synchronized fun put(key: String, value: MemberKey) { map[key] = value }
    @Synchronized fun remove(key: String) { map.remove(key) }
    @Synchronized fun clear() = map.clear()
}

/** Application/进程级共享:任务各建自己的池,健康与粘性不能跟着任务重建。 */
object ModelPools {
    val health: ModelHealthRegistry = ModelHealthRegistry()
    val sticky: StickyTable = StickyTable()
    fun resetForTests() { health.reset(); sticky.clear() }
}

/** 健康按服务商 + 模型共享,不按用途或临时配置 id。 */
data class MemberKey(val providerId: String, val model: String)

/** 池里的一个成员。priority 越小越先,顺序相同时保持构造顺序。 */
data class PoolMember(
    val key: MemberKey,
    val model: ChatModel,
    val priority: Int = 0,
    val providerName: String = "",
) {
    val label: String get() = if (providerName.isBlank()) key.model else "$providerName · ${key.model}"
}

/** 这次请求对成员的要求。estimatedTokens 只用于优先选择窗口够的,不阻止先压缩再试。 */
data class ModelNeed(
    val toolCalls: Boolean = false,
    val vision: Boolean = false,
    val images: Int = 0,
    val estimatedTokens: Int = 0,
)

/** 一次调用可选的一组模型。单模型也包成池,调用方只有一条路径。 */
interface ModelPool {
    val members: List<PoolMember>
    val groupName: String?
    val capabilities: Capabilities
    fun ordered(need: ModelNeed): List<PoolMember>
    fun noteResult(member: PoolMember, latencyMs: Long?, error: Throwable?)
    fun owns(model: ChatModel): Boolean
}

/** 单成员池:不记健康、不换成员,行为和没有池时完全一样。 */
class SingleMemberPool(override val members: List<PoolMember>) : ModelPool {
    init { require(members.isNotEmpty()) }
    constructor(model: ChatModel) : this(listOf(PoolMember(MemberKey("", model.name), model)))
    override val groupName: String? = null
    override val capabilities: Capabilities get() = members.first().model.capabilities
    override fun ordered(need: ModelNeed): List<PoolMember> = members
    override fun noteResult(member: PoolMember, latencyMs: Long?, error: Throwable?) = Unit
    override fun owns(model: ChatModel): Boolean = members.any { it.model === model }
}

fun ChatModel.asPool(): ModelPool = this as? ModelPool ?: SingleMemberPool(this)

/**
 * 最小可用主用途备援池:按 priority 故障转移,按 groupId + sessionId 粘性。
 * 也实现 ChatModel/StreamingChatModel,供不经过 ModelCaller 的旁路调用使用;旁路不知道消息来源,
 * 因此一律剥掉助手消息里的 Thinking 和 ToolCall.opaque。池本身从不执行工具动作。
 */
class GroupPool(
    val groupId: String,
    override val members: List<PoolMember>,
    private val health: ModelHealthRegistry = ModelPools.health,
    private val stickyTable: StickyTable = ModelPools.sticky,
    private val sessionId: String? = null,
) : ModelPool, ChatModel, StreamingChatModel {
    init {
        require(members.isNotEmpty())
        require(members.map { it.key }.distinct().size == members.size)
    }

    override val groupName: String get() = groupId
    override val name: String get() = groupId
    /** 提示词/工具形状保持首选模型的能力;每个成员自己的窗口在 ModelCaller 里独立处理。 */
    override val capabilities: Capabilities = members.first().model.capabilities.let { primary ->
        primary.copy(toolCalls = members.all { it.model.capabilities.toolCalls })
    }
    private val stickyKey: String? get() = sessionId?.takeIf { it.isNotBlank() }?.let { "$groupId:$it" }
    private val order = members.withIndex().associate { it.value.key to it.index }

    override fun ordered(need: ModelNeed): List<PoolMember> = synchronized(lock) {
        val relaxed = eligible(need.copy(estimatedTokens = 0))
        if (relaxed.isEmpty()) return emptyList()
        val strict = eligible(need)
        val sticky = stickyKey?.let { key -> stickyTable.get(key) }
            ?.let { picked -> relaxed.firstOrNull { it.key == picked } }
        // 粘性成员即使估算窗口不够也保留:ModelCaller 先按它的窗口压缩,不因长对话悄悄换模型。
        val pool = if (sticky != null) relaxed else if (strict.isNotEmpty()) strict else relaxed
        val first = sticky ?: pool.minWithOrNull(compareBy({ it.priority }, { order[it.key] ?: Int.MAX_VALUE }))!!
        listOf(first) + (pool - first).sortedWith(compareBy({ it.priority }, { order[it.key] ?: Int.MAX_VALUE }))
    }

    private fun eligible(need: ModelNeed): List<PoolMember> = members.filter { member ->
        val caps = member.model.capabilities
        (!need.toolCalls || caps.toolCalls) && (!need.vision || caps.vision) &&
            (need.images == 0 || need.images <= caps.maxImages) &&
            (need.estimatedTokens == 0 || need.estimatedTokens <= caps.contextTokens) &&
            health.available(member.key)
    }

    override fun noteResult(member: PoolMember, latencyMs: Long?, error: Throwable?) {
        synchronized(lock) {
            if (error == null) {
                health.noteSuccess(member.key, latencyMs ?: 0L)
                stickyKey?.let { stickyTable.put(it, member.key) }
            } else {
                health.noteFailure(member.key, error)
                if (FailoverPolicy.canFailover(error)) stickyKey?.let { stickyTable.remove(it) }
            }
        }
    }

    override fun owns(model: ChatModel): Boolean = members.any { it.model === model }

    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply =
        attempt(system, messages, tools, null)

    override suspend fun completeStreaming(
        system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit,
    ): ModelReply = attempt(system, messages, tools, onText)

    private suspend fun attempt(
        system: String, messages: List<Message>, tools: List<ToolSpec>, onText: ((String) -> Unit)?,
    ): ModelReply {
        val candidates = ordered(needOf(system, messages, tools))
        if (candidates.isEmpty()) throw ModelError.Server(503)
        val view = stripForeignPrivate(messages)
        var last: Throwable? = null
        for ((index, member) in candidates.withIndex()) {
            val started = monotonicMillis()
            try {
                val reply = if (onText != null && member.model is StreamingChatModel) {
                    member.model.completeStreaming(system, view, tools, onText)
                } else {
                    member.model.complete(system, view, tools).also { result ->
                        if (onText != null) result.parts.filterIsInstance<Part.Text>().joinToString("") { it.text }
                            .takeIf { it.isNotEmpty() }?.let(onText)
                    }
                }
                noteResult(member, monotonicMillis() - started, null)
                return reply
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                noteResult(member, monotonicMillis() - started, e)
                last = e
                onText?.invoke("")
                if (!FailoverPolicy.canFailover(e) || index == candidates.lastIndex) throw e
            }
        }
        throw last ?: ModelError.Server(503)
    }

    private fun needOf(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelNeed {
        val images = messages.sumOf { message -> message.parts.count { it is Part.Image } }
        val overhead = TokenEstimate.of(system) + tools.sumOf { TokenEstimate.of(it.name + it.description + it.parametersJsonSchema) }
        return ModelNeed(tools.isNotEmpty(), images > 0, images, TokenEstimate.of(messages) + overhead)
    }

    private val lock = Any()
}

/** 来源未知的旁路历史:私有思考和 opaque 不跨模型重放;工具动作本身不会被池执行。 */
internal fun stripForeignPrivate(messages: List<Message>): List<Message> = messages.map { message ->
    if (message.role != Role.Assistant || message.parts.none { it is Part.Thinking || (it is Part.ToolCall && it.opaque != null) }) message
    else message.copy(parts = message.parts.mapNotNull { part ->
        when {
            part is Part.Thinking -> null
            part is Part.ToolCall && part.opaque != null -> part.copy(opaque = null)
            else -> part
        }
    })
}

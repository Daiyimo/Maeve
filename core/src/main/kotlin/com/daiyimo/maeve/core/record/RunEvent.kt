package com.daiyimo.maeve.core.record

import com.daiyimo.maeve.core.observation.InputHints
import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.Place
import com.daiyimo.maeve.core.observation.PlaceKind
import com.daiyimo.maeve.core.observation.Trust
import com.daiyimo.maeve.core.replay.Anchors
import com.daiyimo.maeve.core.replay.Selector
import com.daiyimo.maeve.core.verify.AnswerSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 运行记录中的一条事件。每行 `events.jsonl` 存一条,由 `type` 字段分辨具体种类。
 *
 * 事件是回放、核查、统计和界面时间线的共同数据源,字段一旦发布就要保持可读:
 * 新字段只能带默认值追加,已有字段的含义和序列化名不改。
 */
@Serializable
sealed interface RunEvent {
    val atMs: Long

    /** 任务开始。[origin] 是发起方的小写名,手机界面发起时为空;[stepLimit] 为空表示不限步数。 */
    @Serializable
    @SerialName("task_started")
    data class TaskStarted(
        override val atMs: Long,
        val goal: String,
        val model: String,
        val origin: String? = null,
        val stepLimit: Int? = null,
    ) : RunEvent

    /** 本次任务发给模型的系统提示词与工具定义的版本摘要。 */
    @Serializable
    @SerialName("prompt_version")
    data class PromptVersion(override val atMs: Long, val hash: String) : RunEvent

    /**
     * 从用户目标里整理出的验收条件。[fallback] 表示没能整理出标准,[criteria]
     * 只是用户原话;`kind` 为空表示旧记录或没有分类。
     */
    @Serializable
    @SerialName("criteria")
    data class Criteria(
        override val atMs: Long,
        val criteria: List<String>,
        val ambiguities: List<String>,
        val kind: String = "",
        val fallback: Boolean = false,
    ) : RunEvent

    /**
     * 主循环收到的一次模型回复。`tool`/`argsJson` 是这一轮真正要执行的调用,
     * 参数在记录前已脱敏;`text` 是回复正文。
     */
    @Serializable
    @SerialName("model_reply")
    data class ModelReply(
        override val atMs: Long,
        val step: Int,
        val text: String,
        val tool: String?,
        val argsJson: String?,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val cachedTokens: Int?,
        /** 最后一次请求发出到回复收完的耗时;失败请求与退避另记 [retryWaitMs]。 */
        val latencyMs: Long = 0,
        /** 本次请求使用的模型;为空表示主模型。 */
        val model: String? = null,
        /** 大小模型分工时是 main / light / light_rejected;其他情况为空。 */
        val role: String? = null,
        /** 重试开销:失败请求本身加上退避等待,不计入 [latencyMs]。 */
        val retryWaitMs: Long = 0,
        /**
         * 回复到达的时刻。带调用的回复要等执行完才记,所以 [atMs] 通常晚于它;
         * 界面上的进度说明、动作或回答从这一刻开始可见。旧记录没有。
         */
        val repliedAtMs: Long? = null,
    ) : RunEvent {
        /** 这条回复是否真的驱动了当前步骤;被改派掉的轻量模型回复不算。 */
        val acted: Boolean get() = role != "light_rejected"
    }

    /**
     * 一次工具执行的记录。`resultJson` 通常整条保留,只有极端大的结果才截短
     * 并留下标记;`changed`/`screenshotId` 是工具报告的副作用与画面编号。
     */
    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        override val atMs: Long,
        val step: Int,
        val tool: String,
        val resultJson: String,
        val changed: Boolean?,
        val screenshotId: Int?,
        /** 工具执行耗时,含端口内等界面稳定;不含等用户(确认、追问、接管)。 */
        val durationMs: Long = 0,
        /** 其中等界面稳定的时间,以及为节奏有意补的停留。 */
        val waitMs: Long = 0,
        /** model | batch | replay | script */
        val source: String = "model",
        /** 模型这次调用的 id,路线用它把动作和结果配对。 */
        val callId: String? = null,
    ) : RunEvent

    /** 回放路线走到第 [step]/[total] 步;[matched] 为 false 表示在此交还给模型。 */
    @Serializable
    @SerialName("replay")
    data class Replay(
        override val atMs: Long,
        val routeId: String,
        val step: Int,
        val total: Int,
        val matched: Boolean,
        val detail: String,
    ) : RunEvent

    /** 一批动作的进度。[stopped] 为 true 时 [reason] 说明停下的原因。 */
    @Serializable
    @SerialName("batch")
    data class Batch(
        override val atMs: Long,
        val step: Int,
        val total: Int,
        val stopped: Boolean,
        val reason: String?,
    ) : RunEvent

    /**
     * 写操作发生时的目标与前后锚点。工具结果可能被截短而缺少完整元素列表,
     * 路线靠这里补齐;[argsJson] 已脱敏。[effective] 表示操作确实产生了效果,
     * [callId] 与同一调用的 [ToolResult.callId] 对应,[protected] 记录动作前
     * 那一屏是否受系统防截屏保护。
     */
    @Serializable
    @SerialName("action_target")
    data class ActionTarget(
        override val atMs: Long,
        val step: Int,
        val tool: String,
        val argsJson: String,
        val target: Selector?,
        val before: Anchors,
        val after: Anchors,
        val effective: Boolean,
        val callId: String? = null,
        val protected: Boolean = false,
        /** model | batch */
        val source: String = "model",
        /** 批里子步取不到目标和锚点时为 true;这种事件不能进路线。 */
        val incomplete: Boolean = false,
    ) : RunEvent

    /**
     * 保存下来的一帧画面:第 [step] 步的第 [mediaId] 张,[place] 是当时所在
     * 的包名或「网站 域名」。旧记录里叫 screen_saved,解码时换成本事件。
     */
    @Serializable
    @SerialName("media")
    data class Media(
        override val atMs: Long,
        val mediaId: Int,
        val step: Int,
        val place: String?,
    ) : RunEvent

    /**
     * 进入证据账本的一条观察。`text` 已脱敏并截短,内容与运行时账本中的那条
     * 一致,评测可以据此重建账本。[id] 是账本编号,回答来源按它回指;[kind]
     * 是观察种类,[source] 是产出它的能力。[inputs]/[focused] 保留读界面时
     * 输入框里的内容,用于区分助手自己输入的字。
     *
     * 旧记录里对应 screen_text,解码时得到编号 0。
     */
    @Serializable
    @SerialName("observed")
    data class Observed(
        override val atMs: Long,
        val step: Int,
        val text: String,
        val id: Int = 0,
        val kind: String = ObsKind.Read.wire,
        val source: String? = null,
        val place: Place? = null,
        val title: String? = null,
        val inputs: List<String>? = null,
        val focused: String? = null,
        val untrusted: Boolean = false,
    ) : RunEvent {
        /** 还原成运行时使用的观察对象。 */
        fun observation(): Observation = Observation(
            kind = ObsKind.ofWire(kind) ?: ObsKind.Read,
            text = text,
            source = source,
            place = place,
            title = title,
            trust = if (untrusted) Trust.Untrusted else Trust.Trusted,
            inputs = inputs?.let { InputHints(it, focused) },
            step = step,
            atMs = atMs,
            id = id,
        )

        companion object {
            /** 由一条运行时观察生成记录事件。 */
            fun of(o: Observation): Observed = Observed(
                atMs = o.atMs,
                step = o.step,
                text = o.text,
                id = o.id,
                kind = o.kind.wire,
                source = o.source,
                place = o.place,
                title = o.title,
                inputs = o.inputs?.names,
                focused = o.inputs?.focused,
                untrusted = o.trust == Trust.Untrusted,
            )
        }
    }

    /**
     * 一次工具调用的进度。读取类可以合并展示,动作类逐条展示;[obsIds] 指向
     * 这一步读到的观察,[title] 是所读页面的标题。
     */
    @Serializable
    @SerialName("activity")
    data class Activity(
        override val atMs: Long,
        val step: Int,
        val kind: String,
        val label: String,
        val target: String? = null,
        val effect: String? = null,
        val obsIds: List<Int> = emptyList(),
        val source: String = "model",
        val callId: String? = null,
        val title: String? = null,
        /** 调用失败;[effect] 此时是错误码。 */
        val failed: Boolean = false,
        /** 不可逆判定认出这是对外动作,停止后统计已发出的次数时按它计。 */
        val outward: Boolean = false,
    ) : RunEvent

    @Serializable
    @SerialName("state")
    data class State(
        override val atMs: Long,
        val name: String,
        val detail: String,
    ) : RunEvent

    @Serializable
    @SerialName("confirmation")
    data class Confirmation(
        override val atMs: Long,
        val action: String,
        val target: String?,
        val content: String?,
        val amount: String?,
        val decision: String?,
    ) : RunEvent

    /**
     * 对一次对外、不可逆或付费动作的审批结论,由代码判定而不是模型自述。
     * [source] 是发起方(model / batch / script / replay);[payment] 表示这步
     * 要花钱;[decision] 为 allowed / required / mismatch / denied。
     */
    @Serializable
    @SerialName("approval")
    data class Approval(
        override val atMs: Long,
        val step: Int,
        val source: String,
        val tool: String,
        val action: String,
        val matched: String,
        val risk: String,
        val payment: Boolean = false,
        val decision: String,
    ) : RunEvent

    @Serializable
    @SerialName("takeover")
    data class Takeover(
        override val atMs: Long,
        val reason: String,
        val outcome: String?,
        val durationMs: Long?,
        val inputCount: Int?,
    ) : RunEvent

    @Serializable
    @SerialName("barrier")
    data class Barrier(override val atMs: Long, val kind: String) : RunEvent

    /**
     * 最终回答的字面核查。[claims] 是回答里可核对的事实数,[unmatched] 是在
     * 已读材料中找不到的几处;[outward] 表示本任务做过对外或不可逆动作,
     * 因此额外请了核查模型。
     */
    @Serializable
    @SerialName("answer_check")
    data class AnswerCheck(
        override val atMs: Long,
        val step: Int,
        val claims: Int,
        val unmatched: List<String>,
        val outward: Boolean = false,
    ) : RunEvent

    /** 交出的回答里退回一次后仍未对上、标为未确认的几处。 */
    @Serializable
    @SerialName("unconfirmed")
    data class Unconfirmed(
        override val atMs: Long,
        val step: Int,
        val items: List<String>,
    ) : RunEvent

    /** 代码从证据账本找到的回答来源(App 或网页、步号、截图),界面上的「来源」用它。 */
    @Serializable
    @SerialName("sources")
    data class Sources(
        override val atMs: Long,
        val items: List<AnswerSource>,
    ) : RunEvent

    /** 核查结论。[step] 是被核查回答所在步;旧记录没有,可能记在回复前后。 */
    @Serializable
    @SerialName("verifier")
    data class Verifier(
        override val atMs: Long,
        val verdict: String,
        val detailJson: String,
        val step: Int? = null,
    ) : RunEvent

    /** 本任务记忆块中实际列出的条目编号,由代码记录。 */
    @Serializable
    @SerialName("memory_used")
    data class MemoryUsed(
        override val atMs: Long,
        val ids: List<String>,
    ) : RunEvent

    /** Agent 用 memory_save 存下的一条记忆;[status] 为 active / pending。 */
    @Serializable
    @SerialName("memory_saved")
    data class MemorySaved(
        override val atMs: Long,
        val id: String,
        val kind: String,
        val status: String,
    ) : RunEvent

    /** 用到的个人技能。[how] 为 viewed(skill_read 看过)或旧记录里的 injected。 */
    @Serializable
    @SerialName("skill_used")
    data class SkillUsed(
        override val atMs: Long,
        val name: String,
        val how: String,
    ) : RunEvent

    @Serializable
    @SerialName("compressed")
    data class Compressed(
        override val atMs: Long,
        val foldedSteps: Int,
    ) : RunEvent

    /**
     * 任务结束。[outcome] 为 succeeded / unverified / failed / cancelled / error;
     * [kind] 是失败类别(FailKind 小写名),其余为 null。
     */
    @Serializable
    @SerialName("task_ended")
    data class TaskEnded(
        override val atMs: Long,
        val outcome: String,
        val answerOrReason: String,
        val kind: String? = null,
    ) : RunEvent

    /**
     * 本次运行经计量的模型调用合计。[calls] 是外层逻辑调用数,含意图判断、
     * 核查、看图、压缩等旁路调用与外层失败重试;[httpCalls] 是真正交给 HTTP
     * 客户端的推理请求数,另含适配器内部重发。旧记录没有这项,不能从 [calls]
     * 反推。[unreported]/[httpUnreported] 分别是未报用量的逻辑调用数和没有
     * 最终用量的请求数。收尾时记一条,位于 [TaskEnded] 之后。
     */
    @Serializable
    @SerialName("model_calls")
    data class ModelCalls(
        override val atMs: Long,
        val calls: Int,
        val unreported: Int,
        val inputTokens: Long,
        val outputTokens: Long,
        val cachedTokens: Long,
        val httpCalls: Int? = null,
        val httpUnreported: Int? = null,
    ) : RunEvent

    /**
     * 回答交付后,另请模型按用户目标判定这次运行的结果。[outcome] 为 done /
     * partial / not_possible / unknown,[reason] 一句话说明差在哪里。记在
     * [TaskEnded] 之后;旧记录没有。
     */
    @Serializable
    @SerialName("outcome")
    data class OutcomeJudged(
        override val atMs: Long,
        val outcome: String,
        val reason: String = "",
        val model: String? = null,
    ) : RunEvent

    companion object {
        const val MAX_FIELD = 2000

        /** 工具结果的上限。它远大于正常元素列表,只挡异常大的输出撑爆文件或内存。 */
        const val MAX_RESULT = 100_000

        val json = Json { classDiscriminator = "type"; ignoreUnknownKeys = true }

        fun encode(e: RunEvent): String = json.encodeToString(serializer(), e)

        /** 解码一行记录;遇到改名前的事件先做兼容转换。 */
        fun decode(line: String): RunEvent {
            val upgraded = LegacyEvents.upgrade(json.parseToJsonElement(line))
            return json.decodeFromJsonElement(serializer(), upgraded)
        }

        /** 记录里写的地点:App 用包名,站点写成「网站 域名」。 */
        fun placeLabel(p: Place?): String? = p?.let {
            if (it.kind == PlaceKind.Site) "网站 ${it.name}" else it.name
        }

        fun clip(s: String): String = shorten(s, MAX_FIELD, "…(已截短)")

        fun clipResult(s: String): String = shorten(s, MAX_RESULT, "…(已截短,原长 ${s.length} 字)")

        private fun shorten(s: String, limit: Int, marker: String): String =
            if (s.length <= limit) s else s.take(limit) + marker
    }
}

/** `run.json` 的内容:由事件累计而来的一次运行概要。 */
@Serializable
data class RunSummary(
    val id: String,
    val goal: String,
    val model: String = "",
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val outcome: String? = null,
    val answerOrReason: String? = null,
    val criteria: List<String> = emptyList(),
    val verdict: String? = null,
    /** 回答交付后判定的结局;失败、取消或旧记录为空。 */
    val judged: String? = null,
    val steps: Int = 0,
    /** 本次运行的步数上限;旧记录或未限制为空。 */
    val stepLimit: Int? = null,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cachedTokens: Int = 0,
    /** 发起方;手机界面发起为空。 */
    val origin: String? = null,
) {
    /** 把一个事件并入概要。 */
    fun with(e: RunEvent): RunSummary = when (e) {
        is RunEvent.TaskStarted -> copy(
            model = e.model,
            origin = e.origin,
            stepLimit = e.stepLimit,
        )
        is RunEvent.Criteria -> copy(criteria = e.criteria)
        is RunEvent.ModelReply -> copy(
            steps = maxOf(steps, e.step),
            inputTokens = inputTokens + (e.inputTokens ?: 0),
            outputTokens = outputTokens + (e.outputTokens ?: 0),
            cachedTokens = cachedTokens + (e.cachedTokens ?: 0),
        )
        is RunEvent.Verifier -> copy(verdict = e.verdict)
        is RunEvent.TaskEnded -> copy(
            endedAtMs = e.atMs,
            outcome = e.outcome,
            answerOrReason = e.answerOrReason,
        )
        is RunEvent.OutcomeJudged -> copy(judged = e.outcome)
        else -> this
    }
}

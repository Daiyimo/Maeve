package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.agent.Turn
import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.observation.Trust
import com.daiyimo.maeve.core.observation.Untrusted
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.record.RunRecords
import com.daiyimo.maeve.core.record.RunSummary
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Concurrency
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.int
import com.daiyimo.maeve.core.tool.okObj
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val HISTORY_READ = "history_read"

/**
 * 回看本对话更早几轮留下的东西。不给 turn 时列出历史轮次(新的在前:第几轮、用户说了什么、结局、时刻);
 * 给了 turn 就精读那一轮(完整结局、执行期间的插话、读到的原文),原文按条分页。
 *
 * 可见范围仅限本次运行开局时带入的轮次([com.daiyimo.maeve.core.run.RunSpec.priorTurns],同一对话),
 * 且只读;原文来自运行记录,落盘前已脱敏截短,不含截图。读到的原文会作为观察交给核查,
 * 因此回答里引用旧内容是有据可查的。只有当运行记录可用、且历史轮次里确实有落盘的运行时,工具才出现。
 */
internal object HistoryRead : AgentTool {
    /** 列表最多列几轮(新的在前)。 */
    const val LIST_MAX = 20
    /** 一页里各条原文加起来大约多少字(至少给一条)。 */
    const val PAGE_CHARS = 8_000
    /** 一条原文最多留多少字。 */
    const val ITEM_CHARS = 2_000
    /** 读一轮时结局最多留多少字。 */
    const val OUTCOME_CHARS = 3_000
    /** 列表里用户原话、结局各留多少字。 */
    private const val GIST_CHARS = 200
    /** 进度说明在结果里的 kind 值。 */
    private const val KIND_NOTE = "note"
    /** 读旧原文得来的观察,出处统一记 history。 */
    private const val OBS_SOURCE = "history"

    private val CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    override val verb = Verb.HistoryRead
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef(HISTORY_READ,
        Prompts.tool(HISTORY_READ, "page" to PAGE_CHARS),
        mapOf(
            "turn" to int("Which earlier turn to read: 1 = the turn just before this one, 2 = the one before that, and so on. Leave out to list the earlier turns.", 1),
            "offset" to int("Item to start from, from nextOffset of the previous call (default 0).", 0),
        ), emptyList())

    override fun available(ctx: ToolContext): Boolean =
        ctx.config.runRecords != null && ctx.config.runSpec?.priorTurns.orEmpty().any { it.runId != null }

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val priorTurns = ctx.config.runSpec!!.priorTurns.asReversed()
        val records = ctx.config.runRecords!!
        val turnNo = a.int("turn", 1, Int.MAX_VALUE)
        val from = a.int("offset", 0, Int.MAX_VALUE) ?: 0
        if (turnNo == null) return listTurns(priorTurns, records)
        val target = priorTurns.getOrNull(turnNo - 1)
            ?: return fail("turn_not_found", Feedback.format("history.turn_not_found", "count" to priorTurns.size))
        return readTurn(turnNo, target, records, from)
    }

    /** 列历史轮次:每轮一行摘要,能查到运行记录的才带时刻。 */
    private suspend fun listTurns(priorTurns: List<Turn>, records: RunRecords): ToolExecution {
        val shown = priorTurns.take(LIST_MAX)
        val zone = ZoneId.systemDefault()
        val summaries = shown.map { t -> t.runId?.let { id -> attempt(null) { records.summary(id) } } }
        return ToolExecution(okObj {
            put("turns", JsonArray(shown.mapIndexed { i, t -> turnRow(i, t, summaries[i], zone) }))
            if (priorTurns.size > shown.size) put("older", priorTurns.size - shown.size)
        })
    }

    /** 一轮在列表里的样子:第几轮、用户原话、结局,查得到记录再带时刻。 */
    private fun turnRow(index: Int, t: Turn, summary: RunSummary?, zone: ZoneId) = buildJsonObject {
        put("turn", index + 1)
        put("said", shorten(t.userText, GIST_CHARS))
        put("status", t.status)
        put("outcome", shorten(t.outcome, GIST_CHARS))
        summary?.let { put("time", stamp(it.startedAtMs, zone)) }
        put("recorded", summary != null)
    }

    /** 精读某一轮:结局 + 插话 + 分页的原文,并把原文转成观察交给核查。 */
    private suspend fun readTurn(turnNo: Int, t: Turn, records: RunRecords, from: Int): ToolExecution {
        val id = t.runId
        val summary = id?.let { attempt(null) { records.summary(it) } }
        val items = if (summary != null) collect(attempt(emptyList()) { records.events(id) }) else emptyList()
        val page = pageOf(items, from)
        val nextOffset = from + page.size
        val content = okObj {
            put("turn", turnNo)
            put("said", t.userText)
            put("status", t.status)
            put("outcome", shorten(t.outcome, OUTCOME_CHARS))
            if (t.steers.isNotEmpty()) put("userAddedDuringTask", JsonArray(t.steers.map { JsonPrimitive(shorten(it, GIST_CHARS)) }))
            summary?.let { put("time", stamp(it.startedAtMs, ZoneId.systemDefault())) }
            put("recorded", summary != null)
            if (summary == null) put("message", Feedback["history.not_recorded"])
            else {
                put("totalItems", items.size)
                put("items", JsonArray(page.mapIndexed { i, item -> itemRow(from + i, item) }))
                if (nextOffset < items.size) put("nextOffset", nextOffset)
            }
        }
        // 出处统一记 history,不指回当时的画面;回答引用旧原文时核查能对上
        return ToolExecution(content, observations = page.mapNotNull { observationOf(it) })
    }

    /** 从 from 起攒一页:满了就不再要,但空页时至少给一条。 */
    private fun pageOf(items: List<HistoryEntry>, from: Int): List<HistoryEntry> {
        val page = mutableListOf<HistoryEntry>()
        var chars = 0
        for (item in items.drop(from)) {
            if (page.isNotEmpty() && chars + item.text.length > PAGE_CHARS) break
            page += item
            chars += item.text.length
        }
        return page
    }

    /** 一条原文在结果里的样子;不可信的字包上 [Untrusted] 再交出去。 */
    private fun itemRow(n: Int, item: HistoryEntry) = buildJsonObject {
        put("n", n)
        put("kind", item.kind)
        item.where?.let { put("where", it) }
        put("text", if (item.untrusted) Untrusted.wrap(item.text) else item.text)
    }

    /** 只有读来的原文(带观察种类)进核查; Maeve 自己的进度说明不进。 */
    private fun observationOf(item: HistoryEntry): Observation? =
        item.obsKind?.let { Observation(it, item.text, source = OBS_SOURCE, title = item.where, trust = if (item.untrusted) Trust.Untrusted else Trust.Trusted) }

    /** 一条可读内容:Maeve 的进度说明(note,取自带调用的回复),或读到的原文(观察的种类)。 */
    private class HistoryEntry(val kind: String, val text: String, val where: String?, val untrusted: Boolean, val obsKind: ObsKind?)

    /** 从事件流里挑可读的条:去重、截短;模型回复只收真正发了调用的那些(纯回答已在 outcome 里)。 */
    private fun collect(events: List<RunEvent>): List<HistoryEntry> {
        val seen = mutableSetOf<String>()
        return events.mapNotNull { e ->
            when (e) {
                is RunEvent.ModelReply -> e.text.trim().takeIf { e.acted && e.tool != null && it.isNotEmpty() && seen.add(it) }
                    ?.let { HistoryEntry(KIND_NOTE, shorten(it, ITEM_CHARS), null, false, null) }
                is RunEvent.Observed -> e.text.trim().takeIf { it.isNotEmpty() && seen.add(it) }?.let {
                    val o = e.observation()
                    HistoryEntry(o.kind.wire, shorten(it, ITEM_CHARS), o.title ?: o.place?.name, e.untrusted, o.kind)
                }
                else -> null
            }
        }
    }

    private fun stamp(ms: Long, zone: ZoneId) = CLOCK.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun shorten(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"

    /** 读记录失败按没有处理:查不到历史不影响当前任务。 */
    private suspend fun <T> attempt(fallback: T, read: suspend () -> T): T =
        try { read() } catch (e: CancellationException) { throw e } catch (e: Exception) { fallback }
}

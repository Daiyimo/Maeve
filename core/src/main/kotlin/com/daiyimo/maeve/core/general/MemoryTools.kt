package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.memory.Change
import com.daiyimo.maeve.core.memory.MemoryIndex
import com.daiyimo.maeve.core.memory.MemoryItem
import com.daiyimo.maeve.core.memory.MemoryKind
import com.daiyimo.maeve.core.memory.MemoryRejected
import com.daiyimo.maeve.core.memory.MemorySource
import com.daiyimo.maeve.core.memory.MemoryStore
import com.daiyimo.maeve.core.memory.UserUtterances
import com.daiyimo.maeve.core.observation.ObsKind
import com.daiyimo.maeve.core.observation.Observation
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Concurrency
import com.daiyimo.maeve.core.tool.EvidenceLog
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.argStr
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.ok
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** 一次检索最多带回几条记忆。 */
private const val SEARCH_HITS = 8

/** 记忆库和用户本次说过的话都就绪才提供。 */
private fun memoryReady(ctx: ToolContext) = ctx.config.memory != null && ctx.config.utterances != null

internal object MemorySearch : AgentTool {
    override val verb = Verb.MemorySearch
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef("memory_search",
        Prompts.tool("memory_search"),
        mapOf("query" to str("Words to look for, 1-100 characters.")), listOf("query"))

    override fun available(ctx: ToolContext) = memoryReady(ctx)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val q = a.str("query", max = 100)!!
        val found = MemoryIndex(ctx.config.memory!!.items.value).search(q, SEARCH_HITS)
        return ok {
            put("memories", JsonArray(found.map { (m, _) -> hitOf(m) }))
        }
    }

    /** 记忆里的是用户以前说的:作为用户给的事实交给核查。 */
    override fun observations(o: JsonObject, r: ToolExecution, log: EvidenceLog): List<Observation> {
        if (r.content["ok"] != JsonPrimitive(true)) return emptyList()
        return listOfNotNull((r.content["memories"] as? JsonArray)?.takeIf { it.isNotEmpty() }?.let { list ->
            Observation(ObsKind.UserSaid, list.joinToString("\n", "记忆检索「${o.argStr("query")}」:\n") { m -> textOf(m) },
                source = "memory")
        })
    }

    /** 一条命中:编号、种类、正文。 */
    private fun hitOf(m: MemoryItem) = buildJsonObject {
        put("id", m.id); put("kind", m.kind.name); put("content", m.content)
    }

    /** 从结果行里取正文,行形状不对时当空。 */
    private fun textOf(row: JsonElement): String = ((row as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()
}

/** 只改 Maeve 自己的记忆,不动屏幕和外部世界。 */
internal object MemorySave : AgentTool {
    override val verb = Verb.MemorySave
    override val risk = Risk.Read
    override val def = ToolDef("memory_save",
        Prompts.tool("memory_save"),
        mapOf(
            "kind" to str("fact = about the user, preference = what the user likes, procedure = how the user wants a kind of task done.", listOf("fact", "preference", "procedure")),
            "content" to str("One sentence in the third person, at most 200 characters, e.g. 用户点外卖不要香菜."),
            "quote" to str("The user's exact words this is based on."),
            "keywords" to buildJsonObject {
                put("type", "array"); put("description", "Optional, up to 8 search words incl. synonyms.")
                put("items", buildJsonObject { put("type", "string") })
            },
        ), listOf("kind", "content", "quote"))

    override fun available(ctx: ToolContext) = memoryReady(ctx)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val kind = kindOf(a)
        val content = a.str("content", max = MemoryStore.MAX_CONTENT)!!
        val quote = a.str("quote", max = 1000)!!
        val keywords = a.strList("keywords", 0, MemoryStore.MAX_KEYWORDS, itemMax = 30) ?: emptyList()
        notFromUser(ctx.config.utterances!!, quote, content, keywords)?.let { return it }
        val store = ctx.config.memory!!
        val added = store.add(kind, content, keywords, sourceOf(ctx, store, quote))
        added.getOrNull()?.let(ctx::noteSaved)
        return added.fold(onSuccess = { c -> savedOk(c) }, onFailure = { e -> rejected(e) })
    }

    /** 种类参数转枚举;不在 fact/preference/procedure 里当参数错。 */
    private fun kindOf(a: Args): MemoryKind =
        a.str("kind")!!.let { k -> MemoryKind.entries.find { it.name == k } ?: throw ArgError("kind", "must be fact, preference or procedure") }

    /**
     * 内容只能来自用户本次说的话:引文要先在用户说过的话里找到,
     * 内容(含关键词)里的具体值(地址、号码、名字)也得是用户说过的,不能从屏幕上补。
     * 返回对应的拒绝,都通过时为 null。
     */
    private fun notFromUser(utterances: UserUtterances, quote: String, content: String, keywords: List<String>): ToolExecution? {
        if (!utterances.contains(quote)) return fail("quote_not_from_user",
            Feedback["memory.quote_not_from_user"], recoverable = false)
        if ((listOf(content) + keywords).any { utterances.unsupportedSpecifics(it, quote).isNotEmpty() }) return fail("content_not_from_user",
            Feedback["memory.content_not_from_user"], recoverable = false)
        return null
    }

    /** 出处:用户这次说的原话(去掉首尾空白),连上这次对话和这次运行的来路。 */
    private fun sourceOf(ctx: ToolContext, store: MemoryStore, quote: String) =
        MemorySource("user_said", quote.trim(), ctx.config.conversationId, store.clock(), ctx.config.runSpec?.provenance?.origin?.name)

    /** 存下了:回编号、种类、状态。 */
    private fun savedOk(c: Change.Added) = ok {
        put("id", c.item.id); put("kind", c.item.kind.name); put("status", c.item.status.name)
    }

    /** 没存成的几种缘由;参数问题和意外原样抛出。 */
    private fun rejected(e: Throwable): ToolExecution =
        when ((e as? MemoryRejected)?.code) {
            "sensitive" -> fail("sensitive", Feedback["memory.sensitive"], recoverable = false)
            "memory_full" -> fail("memory_full", Feedback["memory.full"], recoverable = false)
            "unavailable" -> fail("memory_unavailable", Feedback["memory.unavailable"], recoverable = false)
            "too_long" -> throw ArgError("content", "must be 1-${MemoryStore.MAX_CONTENT} characters")
            else -> throw e
        }
}

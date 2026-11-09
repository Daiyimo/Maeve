package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.knowledge.GuideCatalog
import com.daiyimo.maeve.core.knowledge.GuideInfo
import com.daiyimo.maeve.core.knowledge.GuideView
import com.daiyimo.maeve.core.knowledge.KNOWLEDGE_KINDS
import com.daiyimo.maeve.core.knowledge.KnowledgeIndex
import com.daiyimo.maeve.core.knowledge.PERSONAL_KIND
import com.daiyimo.maeve.core.knowledge.SKILL_KINDS
import com.daiyimo.maeve.core.knowledge.toolsNote
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.skills.PersonalSkills
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Concurrency
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.ok
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 查 App 与网站知识;只读目录,有目录才提供。 */
internal object KnowledgeSearch : AgentTool {
    override val verb = Verb.KnowledgeSearch
    override val label = "查找 App/网站知识"
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    private val KINDS = KnowledgeIndex.KINDS.filter { it in KNOWLEDGE_KINDS }
    override val def = ToolDef("knowledge_search",
        Prompts.tool("knowledge_search"),
        mapOf(
            "query" to queryArg(QUERY_NOTE_KNOWLEDGE),
            "kind" to str("Only this kind: app (a phone app) or site (a website).", KINDS),
        ), listOf("query"))

    override fun available(ctx: ToolContext) = catalogReady(ctx)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val q = a.str("query", max = 200)!!
        val kind = kindArg(a, KINDS)
        return searchMatches(ctx, q, kind, KNOWLEDGE_KINDS)
    }
}

/** 查技能(内置技能与用户在用的个人技能);只读目录,有目录才提供。 */
internal object SkillSearch : AgentTool {
    override val verb = Verb.SkillSearch
    override val label = "查找技能"
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef("skill_search", Prompts.tool("skill_search"), mapOf("query" to queryArg(QUERY_NOTE_SKILL)), listOf("query"))

    override fun available(ctx: ToolContext) = catalogReady(ctx)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution =
        searchMatches(ctx, a.str("query", max = 200)!!, null, SKILL_KINDS)
}

/** 读 App 或网站知识的正文或附件。 */
internal object KnowledgeRead : AgentTool {
    override val verb = Verb.KnowledgeRead
    override val label = "查看 App/网站知识"
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef("knowledge_read",
        Prompts.tool("knowledge_read"),
        mapOf(
            "name" to str(NAME_NOTE_KNOWLEDGE),
            "file_path" to ATTACHMENT_PATH,
        ), listOf("name"))

    override fun available(ctx: ToolContext) = catalogReady(ctx)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = readEntry(a, ctx, KNOWLEDGE_KINDS)
}

/** 读技能的正文或附件,含用户的个人技能。 */
internal object SkillRead : AgentTool {
    override val verb = Verb.SkillRead
    override val label = "查看技能"
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef("skill_read",
        Prompts.tool("skill_read"),
        mapOf(
            "name" to str(NAME_NOTE_SKILL),
            "file_path" to ATTACHMENT_PATH,
        ), listOf("name"))

    override fun available(ctx: ToolContext) = catalogReady(ctx)

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = readEntry(a, ctx, SKILL_KINDS)
}

/** 四个查找/阅读工具共用的前提:技能目录就绪。 */
private fun catalogReady(ctx: ToolContext) = ctx.config.skills != null

/** kind 参数:不给就是不限种类;给了必须是 [kinds] 里有的那种。 */
private fun kindArg(a: Args, kinds: List<String>): String? = a.str("kind")?.also {
    if (it !in kinds) throw ArgError("kind", "must be ${kinds.joinToString(", ")}")
}

/** query 参数的样子:1 到 200 个字的字符串。 */
private fun queryArg(description: String): JsonObject = buildJsonObject {
    put("type", "string"); put("description", description); put("minLength", 1); put("maxLength", 200)
}

/** file_path 参数的样子:附件里的相对路径。 */
private val ATTACHMENT_PATH = str("Optional relative path of an attachment listed in files, e.g. references/stations.md.")

/** 在指定种类的目录里按关键词找,返回名称/展示名/种类/简介的列表。 */
private fun searchMatches(ctx: ToolContext, query: String, kind: String?, among: Set<String>): ToolExecution {
    val found = ctx.config.skills!!.find(query, kind, among = among)
    return ok {
        put("matches", JsonArray(found.map { matchRow(it) }))
    }
}

/** 一条命中的样子:名字、展示名、种类、简介。 */
private fun matchRow(hit: GuideInfo) = buildJsonObject {
    put("name", hit.name); put("display", hit.display); put("kind", hit.kind); put("description", hit.description)
}

/** 读一条的正文,可选带附件路径;顺带给出这一条带的脚本(此刻能不能跑)和个人技能的版本号。 */
private suspend fun readEntry(a: Args, ctx: ToolContext, among: Set<String>): ToolExecution {
    val name = a.str("name", max = 200)!!
    val file = a.str("file_path", max = 500)
    val catalog = ctx.config.skills!!
    val runner = ctx.scripts
    val ready = runner?.ready() == true
    return when (val v = catalog.view(name, file, toolsNote(runner?.offered() == true), among)) {
        is GuideView.Failed -> fail(v.error, v.message)
        is GuideView.Found -> foundEntry(ctx, catalog, v, scriptDirOf(catalog, v.info), ready)
    }
}

/** 这一条能带脚本时,脚本目录在哪;个人技能只有文字,不带脚本。 */
private fun scriptDirOf(catalog: GuideCatalog, info: GuideInfo): String =
    if (info.kind == PERSONAL_KIND) "" else catalog.scriptSource(info)

/** 查到了:正文、附件清单、个人技能版本号,能跑脚本时再带脚本清单和调用时要填的 source。 */
private fun foundEntry(ctx: ToolContext, catalog: GuideCatalog, v: GuideView.Found, scriptDir: String, ready: Boolean): ToolExecution {
    val runner = ctx.scripts!!
    val scripts = if (ready && scriptDir.isNotEmpty()) runner.scripts(scriptDir) else emptyList()
    return ok {
        put("name", v.info.name); put("kind", v.info.kind); put("file", v.file)
        put("content", v.content); put("truncated", v.truncated)
        put("files", JsonArray(v.info.files.map(::JsonPrimitive)))
        personalVersion(ctx, v.info, v.file)?.let { put("version", it) }
        if (scripts.isNotEmpty()) {
            put("scriptSource", scriptDir)
            put("scripts", JsonArray(scripts.map { e ->
                buildJsonObject { put("file", e.file); put("runnable", e.runnable); e.reason?.let { put("reason", it) } }
            }))
        }
    }
}

/** 个人技能正文(不是附件)才给版本号:改写它(skill_save)时带上,中途被别处改过就对不上。 */
private fun personalVersion(ctx: ToolContext, info: GuideInfo, file: String): String? {
    if (info.kind != PERSONAL_KIND || file != info.files.firstOrNull()) return null
    val stored = ctx.config.personalSkills?.get(info.name) ?: return null
    return PersonalSkills.version(stored.markdown)
}

private const val QUERY_NOTE_KNOWLEDGE = "App or website name, alias or topic words, 1-200 characters."
private const val QUERY_NOTE_SKILL = "Words for the kind of task, 1-200 characters."
private const val NAME_NOTE_KNOWLEDGE = "Name from knowledge_search or from the knowledge given to you, optionally as app:<name> or site:<domain>."
private const val NAME_NOTE_SKILL = "Skill name from skill_search or from a skill list given to you, optionally as skill:<name> or personal:<name>."

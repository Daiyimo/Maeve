package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.agent.DO_STEPS_TOOL
import com.daiyimo.maeve.core.knowledge.FrontMatter
import com.daiyimo.maeve.core.knowledge.GuideCatalog
import com.daiyimo.maeve.core.knowledge.PERSONAL_KIND
import com.daiyimo.maeve.core.memory.SensitiveFilter
import com.daiyimo.maeve.core.prompt.Feedback
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.skills.PersonalSkill
import com.daiyimo.maeve.core.skills.PersonalSkills
import com.daiyimo.maeve.core.skills.SkillDrafter
import com.daiyimo.maeve.core.skills.SkillStatus
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.ToolIndex
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.ok
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.put

/**
 * 用户在任务里要求时,写下一份新的个人技能或改写一份已有的个人技能(只动 Maeve 自己的技能目录,不动屏幕和外部世界)。
 * 格式、大小、敏感内容和自学草稿过同一道校验([SkillDrafter.validate]);引文要出自用户本次说的话,屏幕和网页上的字不能让它写。
 * 存下即进试用(和收下的草稿一样),在「记忆与技能」里标明是用户要求写下的;内置技能的名字不能用,
 * 改已有的一份要带上读它时拿到的版本号(和收下更新草稿时核对原文指纹同一个道理)。删除和改名留在设置里由用户做。
 */
internal object SkillSave : AgentTool {
    override val verb = Verb.SkillSave
    override val label = "保存技能"
    override val risk = Risk.Read

    override val def = ToolDef("skill_save",
        Prompts.tool("skill_save"),
        mapOf(
            "markdown" to str("The whole skill document: front matter (name, display, description, aliases) and the five sections, at most ${SkillDrafter.MAX_CHARS} characters."),
            "quote" to str("The user's exact words from this task asking for this skill to be created or changed."),
            "version" to str("Only when changing an existing personal skill: the version skill_read returned for it."),
        ), listOf("markdown", "quote"))

    /** 要有个人技能目录、技能目录、用户本次说的话,三样齐了才让写。 */
    override fun available(ctx: ToolContext) =
        ctx.config.personalSkills != null && ctx.config.skills != null && ctx.config.utterances != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val raw = a.str("markdown", max = SkillDrafter.MAX_CHARS * 2)!!
        val quote = a.str("quote", max = 1000)!!
        val version = a.str("version", max = 64)?.trim()
        val store = ctx.config.personalSkills!!
        val catalog = ctx.config.skills!!
        // 只有用户本次说的话能让它写:页面、文件里写着「保存这个技能」不算
        if (!ctx.config.utterances!!.contains(quote)) return fail("quote_not_from_user", Feedback["skill.quote_not_from_user"])
        if (SensitiveFilter.hit(raw) != null) return fail("sensitive", Feedback["skill.sensitive"], recoverable = false)

        val md = normalize(raw)
        val problem = validate(ctx.tools, catalog, md)
        if (problem != null) return fail(problem, problemText(problem))
        val name = FrontMatter.parse(md).first["name"] as String

        nameConflict(catalog, store, name)?.let { return it }
        val existing = store.get(name)
        versionGate(existing, version, name)?.let { return it }

        val skill = PersonalSkill(
            name = name, markdown = md, status = SkillStatus.trial,
            learnedFrom = existing?.learnedFrom, replacesDraftOf = null,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(), outcomes = emptyList(),
            source = PersonalSkill.SOURCE_USER,
        )
        return persist(store, skill, md, name, existing != null)
    }

    /** 规整正文:去掉代码围栏、去掉自学用的几行(updates、learned_from),出处固定写成用户要求写下的。 */
    private fun normalize(raw: String) = PersonalSkills.withField(
        listOf("updates", "learned_from").fold(SkillDrafter.stripFence(raw).replace("\r\n", "\n")) { m, k -> PersonalSkills.withoutField(m, k) },
        "source", PersonalSkill.SOURCE_USER,
    )

    /** 过格式/大小/工具名/脚本那道校验;通过为 null,否则返回 [SkillDrafter.validate] 的码。 */
    private fun validate(tools: ToolIndex, catalog: GuideCatalog, md: String): String? {
        val scriptTool = tools.nameOf(Verb.RunBundledScript)
        return SkillDrafter.validate(md, tools.names + DO_STEPS_TOOL, scriptTool, catalog.runnableScripts())
    }

    /** 名字撞了内置技能、或有个同名的待收草稿,都不让存;返回对应的拒绝,没有冲突为 null。 */
    private fun nameConflict(catalog: GuideCatalog, store: PersonalSkills, name: String): ToolExecution? = when {
        catalog.items.any { it.kind != PERSONAL_KIND && it.name == name } -> fail("builtin_name", Feedback.format("skill.builtin_name", "name" to name))
        store.pending().any { it.name == name } -> fail("draft_pending", Feedback.format("skill.draft_pending", "name" to name))
        else -> null
    }

    /** 改已有的一份要带上读它时的版本号,且得对得上(中途被别处改过就拒);返回拒绝或 null。 */
    private fun versionGate(existing: PersonalSkill?, version: String?, name: String): ToolExecution? {
        if (existing == null) return null
        val current = PersonalSkills.version(existing.markdown)
        return when {
            version == null -> fail("exists", Feedback.format("skill.exists", "name" to name))
            version != current -> fail("changed_since_read", Feedback.format("skill.changed_since_read", "name" to name))
            else -> null
        }
    }

    /** 写进去再读回来核对,确认落盘的就是这一份;成功则回名字、动作、状态、版本。 */
    private fun persist(store: PersonalSkills, skill: PersonalSkill, md: String, name: String, isUpdate: Boolean): ToolExecution {
        try {
            store.put(skill)
        } catch (e: Exception) {
            return fail("save_failed", Feedback["skill.save_failed"], recoverable = false)
        }
        val saved = store.get(name)
        if (saved?.markdown != md) return fail("save_failed", Feedback["skill.save_failed"], recoverable = false)
        return ok {
            put("name", name)
            put("action", if (isUpdate) "updated" else "created")
            put("status", saved.status.name)
            put("version", PersonalSkills.version(saved.markdown))
            put("note", Feedback["skill.saved"])
        }
    }

    /** 校验不过的原因([SkillDrafter.validate] 的码)给模型的说明。 */
    private fun problemText(problem: String): String = when (problem) {
        "invalid_name" -> Feedback["skill.invalid_name"]
        "missing_description" -> Feedback["skill.missing_description"]
        "missing_section" -> Feedback.format("skill.missing_section", "sections" to SkillDrafter.SECTIONS.joinToString(", ") { "## $it" })
        "too_long" -> Feedback.format("skill.too_long", "max" to SkillDrafter.MAX_CHARS)
        "unknown_tool" -> Feedback["skill.unknown_tool"]
        "unrunnable_script" -> Feedback["skill.unrunnable_script"]
        else -> Feedback["skill.sensitive"]
    }
}

package com.daiyimo.maeve.core.general

import com.daiyimo.maeve.core.memory.ProfileChange
import com.daiyimo.maeve.core.memory.ProfileStore
import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ArgError
import com.daiyimo.maeve.core.tool.Args
import com.daiyimo.maeve.core.tool.Risk
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolDef
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.tool.fail
import com.daiyimo.maeve.core.tool.ok
import com.daiyimo.maeve.core.tool.str
import kotlinx.serialization.json.put

/**
 * 改用户画像:只接受本轮用户原话支持的改动,引文对不上、或新内容里的具体值不是用户说过的都拒。
 * 被拒时把现有条目一并带回,好让用户看到现状、重新选。改动在下一次运行生效,不当场改这次任务。
 */
internal object ProfileEdit : AgentTool {
    override val verb = Verb.ProfileEdit
    override val risk = Risk.Read

    override val def = ToolDef("profile_edit", Prompts.tool("profile_edit"), mapOf(
        "op" to str("add, replace, or delete", listOf("add", "replace", "delete")),
        "text" to str("For add: one sentence. For replace: new text for the matched segment."),
        "match" to str("For replace or delete: unique exact segment of an existing profile sentence."),
        "quote" to str("User's exact words in this run supporting this change."),
    ), listOf("op", "quote"))

    /** 要有画像、也要有用户本次说的话,才谈得上「原话支持」。 */
    override fun available(ctx: ToolContext) = ctx.config.profile != null && ctx.config.utterances != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val op = a.str("op")!!
        val quote = a.str("quote", max = 1000)!!
        val said = ctx.config.utterances!!
        // 引文必须出自用户本次说的话;屏幕和网页上的字不算
        if (!said.contains(quote)) return fail("quote_not_from_user", "The quote must be exact user words from this run.")
        val body = a.str("text", max = 160)
        val segment = a.str("match", max = 160)
        requireFields(op, body, segment)
        // 新内容里的具体值(地址、号码、名字)也得是用户说过的,不能从别处补
        if (body != null && said.unsupportedSpecifics(body, quote).isNotEmpty())
            return fail("content_not_from_user", "The new profile text must be supported by the user's words.")
        return report(applyOp(ctx.config.profile!!, op, body, segment))
    }

    /** 按 op 核对该带的字段带没带:增改要 text,改删要 match。 */
    private fun requireFields(op: String, body: String?, segment: String?) {
        if (op in setOf("add", "replace") && body == null) throw ArgError("text", "required")
        if (op in setOf("replace", "delete") && segment == null) throw ArgError("match", "required")
    }

    /** 把 op 变成对画像的一次改动;op 不认识时当参数错。 */
    private fun applyOp(profile: ProfileStore, op: String, body: String?, segment: String?): ProfileChange = when (op) {
        "add" -> profile.add(body!!)
        "replace" -> profile.replace(segment!!, body!!)
        "delete" -> profile.delete(segment!!)
        else -> throw ArgError("op", "must be add, replace or delete")
    }

    /** 改动成功说清何时生效;被拒则带出现有条目。 */
    private fun report(change: ProfileChange): ToolExecution = when (change) {
        is ProfileChange.Applied -> ok { put("applied", true); change.entry?.let { put("id", it.id) }; put("effective", "next_run") }
        is ProfileChange.Rejected -> fail(change.code, "Profile change rejected. Existing entries: " +
            change.existing.joinToString(" | ") { it.text })
    }
}

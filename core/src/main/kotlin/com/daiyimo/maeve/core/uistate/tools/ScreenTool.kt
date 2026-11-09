package com.daiyimo.maeve.core.uistate.tools

import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.uistate.PressKeys
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.sessions

/**
 * 一种屏(手机、网页)那一套同名动作工具里的一个成员。
 *
 * 工具名是「屏前缀_动作名」,只作用于 [family] 这一块屏;分发器在每次调用前会记下此刻操作的是哪块屏,
 * 工具本身不再过问。说明默认取共用模板 prompts/tools/<动作名>.md,这块屏另备了自己的一份时
 * 改用 <屏>_<动作名>.md;模板里点到的同套工具名,生成说明时都会换成这一套的名字(见 [describe])。
 */
internal abstract class ScreenTool(val family: ScreenFamily, val base: String) : AgentTool {
    final override val name: String = family.tool(base)

    /** 属于哪块屏:屏幕能力的 id。 */
    override val capability: String get() = family.id

    /** 这块屏接上了端口才提供;[usable] 是各工具自己额外的条件。 */
    final override fun available(ctx: ToolContext): Boolean = ctx.sessions.containsKey(family.id) && usable(ctx)

    protected open fun usable(ctx: ToolContext): Boolean = true

    /** 沙箱里的 `maeve phone` 只放行结果是文字的工具;要出图、要视觉模型参与的一律不开放。 */
    override val sandboxCallable: Boolean get() = family.sandboxCallable && verb !in PICTURE_VERBS

    /** 说明出自哪个文件:这块屏准备了专属模板时用它,否则用动作名对应的共用模板。 */
    override val promptFile: String get() = if (base in family.ownPrompts) name else base

    /**
     * 说明全文:模板里 {{动词}} 一类的占位符换成这一套里对应的工具名,{{keys}} 换成这块屏允许按的键,
     * 再加上调用方另外带来的变量。
     */
    protected fun describe(vararg vars: Pair<String, Any?>): String =
        Prompts.tool(promptFile, *templateVars(vars.asList()).toTypedArray())

    override fun toString() = name

    /** 模板变量:同套每个动作的工具名、这块屏能按的键,以及调用方额外给的。 */
    private fun templateVars(extra: List<Pair<String, Any?>>): List<Pair<String, Any?>> =
        MIRRORED.map { it to family.tool(it) } + ("keys" to keyNames(family)) + extra

    companion object {
        /** 结果是一张图、或要视觉模型参与的动词:不开放给沙箱。 */
        private val PICTURE_VERBS = setOf(Verb.Screenshot, Verb.Describe, Verb.FindVisually)

        /** 每块屏都有的动作,按发给模型的顺序。 */
        val MIRRORED = listOf(
            "observe", "list_elements", "screenshot", "describe", "find_visually",
            "tap", "scroll", "swipe", "type_text", "clear_text", "back", "press_key", "diagnose",
        )

        /** 一套工具的构造函数,顺序同 [MIRRORED];[of] 只负责实例化和自检。 */
        private val FACTORIES: List<(ScreenFamily) -> ScreenTool> = listOf(
            ::Observe, ::ListElements, ::Screenshot, ::Describe, ::FindVisually,
            ::Tap, ::Scroll, ::Swipe, ::TypeText, ::ClearText, ::Back, ::PressKey, ::Diagnose,
        )

        /** 一块屏上 press_key 能按的键名;[ScreenFamily.keys] 不限时为全部。 */
        fun keyNames(f: ScreenFamily): String =
            PressKeys.BY_NAME.filterValues { f.keys == null || it in f.keys }.keys.joinToString()

        /** [f] 那块屏的一整套屏幕工具,顺序同 [MIRRORED]。 */
        fun of(f: ScreenFamily): List<AgentTool> = FACTORIES.map { it(f) }.also { tools -> check(tools.map { it.base } == MIRRORED) }
    }
}

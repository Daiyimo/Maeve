package com.daiyimo.maeve.core.settings

import com.daiyimo.maeve.core.prompt.Prompts
import com.daiyimo.maeve.core.tool.AgentTool
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * 模型修改 Maeve 自身设置的唯一入口，由 App 实现，核心只认这几个键。
 *
 * 可改范围由 App 决定：**模型与 Key 一律不给，删除数据也一律不给**——
 * 前者等于让模型换掉自己的脑子并花用户的钱，后者不可逆。
 * 给出的都是可逆的偏好项。
 *
 * 用户没有打开「允许 Maeve 改自己的设置」时，这两个工具根本不会提供，模型碰不到。
 */
interface SelfConfig {
    /** 当前可改的项及各自的值。 */
    fun options(): List<Option>

    /** 修改一项；[value] 必须取自 [Option.choices]。成功返回 null，否则返回一句原因。 */
    fun set(key: String, value: String): String?

    /**
     * 一项设置。
     *
     * @property key 稳定标识
     * @property label 给人看的名字
     * @property value 当前值
     * @property choices 可选值；为空表示自由文本
     */
    data class Option(val key: String, val label: String, val value: String, val choices: List<String> = emptyList())
}

const val SETTINGS_LIST = "settings_list"
const val SETTINGS_SET = "settings_set"

/** 取出端口；没开开关时工具不会提供，走到这里说明装配错了。 */
private fun port(ctx: ToolContext): SelfConfig =
    ctx.config.selfConfig ?: error("settings tools are only offered when the user allows it")

/** 两个设置工具共有的可见条件：用户开了「允许 Maeve 改自己的设置」。 */
private fun selfConfigOffered(ctx: ToolContext): Boolean = ctx.config.selfConfig != null

/** 把可选项渲染成 settings_list 的返回体。 */
private fun kotlinx.serialization.json.JsonObjectBuilder.renderOptions(options: List<SelfConfig.Option>) {
    putJsonArray("settings") {
        options.forEach { o ->
            addJsonObject {
                put("key", o.key)
                put("label", o.label)
                put("value", o.value)
                if (o.choices.isNotEmpty()) putJsonArray("choices") { o.choices.forEach { add(it) } }
            }
        }
    }
}

/** 列出 Maeve 自己能改的设置：键、给人看的名字、当前值、可选值。只读。 */
internal object SettingsList : AgentTool {
    override val verb = Verb.SettingsList
    override val label = "查看设置"
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe

    override fun available(ctx: ToolContext) = selfConfigOffered(ctx)

    override val def = ToolDef(SETTINGS_LIST, Prompts.tool(SETTINGS_LIST), emptyMap(), emptyList())

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution = ok {
        renderOptions(port(ctx).options())
    }
}

/** 修改 Maeve 自己的一项设置；键和值都必须来自 [SettingsList] 的结果。 */
internal object SettingsSet : AgentTool {
    override val verb = Verb.SettingsSet
    override val label = "改设置"
    override val risk = Risk.Write

    /** 不动屏幕：改的是 Maeve 自己的设置，与当前任务看到的屏无关。 */
    override val offScreen = true

    override fun available(ctx: ToolContext) = selfConfigOffered(ctx)

    override val def = ToolDef(
        SETTINGS_SET,
        Prompts.tool(SETTINGS_SET),
        mapOf(
            "key" to str("The setting key from settings_list."),
            "value" to str("The new value, one of that setting's choices."),
        ),
        listOf("key", "value"),
    )

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val key = a.str("key") ?: return fail("missing_key", "Give the setting key from settings_list.")
        val value = a.str("value") ?: return fail("missing_value", "Give the new value.")
        val problem = port(ctx).set(key, value)
        if (problem != null) return fail("bad_value", problem)
        // 回读一次：端口可能对传入值做了规范化，回它真正存下的那个
        val stored = port(ctx).options().firstOrNull { it.key == key }?.value ?: value
        return ok {
            put("key", key)
            put("value", stored)
        }
    }
}

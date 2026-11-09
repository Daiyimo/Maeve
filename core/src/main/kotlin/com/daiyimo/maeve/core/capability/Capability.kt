package com.daiyimo.maeve.core.capability

import com.daiyimo.maeve.core.guard.OffScreenGuard
import com.daiyimo.maeve.core.knowledge.BundledScripts
import com.daiyimo.maeve.core.observation.Look
import com.daiyimo.maeve.core.tool.AgentTool
import com.daiyimo.maeve.core.tool.ToolContext
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.Verb
import com.daiyimo.maeve.core.uistate.ScreenFamily
import com.daiyimo.maeve.core.uistate.Surface
import kotlinx.serialization.json.JsonObject

/** 能力当前是否可用。 */
sealed interface CapabilityStatus {
    /** 完全可用。 */
    data object Available : CapabilityStatus

    /** 可用但能力有缺（运行环境仍在准备、部分子功能未接通）。 */
    data class Degraded(val reason: String) : CapabilityStatus

    /** 不可用，[reason] 说明原因。 */
    data class Unavailable(val reason: String) : CapabilityStatus
}

/**
 * 拼装系统提示词时各能力段落可见的环境信息。
 *
 * @property offered 本任务实际提供给模型的工具名
 * @property inlineScreen 是否把屏幕内容直接内联给模型
 * @property screens 本任务提供的屏幕种类数（收起的能力只算入口工具时也计入）
 * @property open 已展开的能力 id 集合；null 表示全部展开
 * @property openScreens [screens] 中已展开的那几块屏
 */
class PromptEnv(
    val offered: Set<String>,
    val inlineScreen: Boolean,
    val screens: Int = 1,
    val open: Set<String>? = null,
    val openScreens: Int = screens,
) {
    /** 能力 [id] 当前是否处于展开状态。 */
    fun isOpen(id: String): Boolean = open == null || id in open

    /**
     * 过滤一段提示词：行首写成 `<!-- needs: 工具A, 工具B -->` 的行，
     * 只在列出的工具全部出现在 [offered] 中时保留正文（去掉标记行），否则整行删除。
     * 无标记的行原样保留。
     */
    fun gate(text: String): String = text.lines().mapNotNull { line ->
        val marker = NeedsMarker.parse(line) ?: return@mapNotNull line
        if (marker.tools.all { it in offered }) marker.indent + marker.body else null
    }.joinToString("\n")
}

/** 行首的能力依赖标记：`<!-- needs: 工具A, 工具B -->` 加正文。 */
private class NeedsMarker(val indent: String, val tools: List<String>, val body: String) {
    companion object {
        private val PATTERN = Regex("^(\\s*)<!-- needs: ([a-z0-9_, ]+) -->\\s?(.*)$")

        /** 解析一行；不是依赖标记时返回 null。 */
        fun parse(line: String): NeedsMarker? {
            val found = PATTERN.find(line) ?: return null
            val tools = found.groupValues[2].split(',').map(String::trim).filter(String::isNotEmpty)
            return NeedsMarker(found.groupValues[1], tools, found.groupValues[3])
        }
    }
}

/** 系统提示词中的一组段落：能力各贡献一组，核心自己也有一组。 */
internal fun interface PromptSections {
    fun sections(env: PromptEnv): List<String>
}

/**
 * 一种能力（手机、网页、沙箱……）。
 *
 * 核心只通过本接口认识能力，不直接依赖任何具体实现；
 * 每个任务持有独立实例，端口在构造时注入。
 */
internal interface Capability : PromptSections {
    val id: String

    fun status(): CapabilityStatus

    /** 该组的工具是否按需展开（见 `com.daiyimo.maeve.core.tool.ToolGroups`）。 */
    val folds: Boolean get() = false

    /** 本次任务（[task]）是否一开始就展开该组。 */
    fun preload(task: String): Boolean = false

    /** 从用户输入（[text]）里剥掉本能力附加的说明（如浏览器页的页面描述）后剩下的用户原话。 */
    fun userWords(text: String): String = text

    /** 本能力可能提供的全部工具；能否在本任务中使用由各工具自行判断上下文。 */
    fun tools(): List<AgentTool>

    /** 有屏幕的能力给出其屏幕工具组的形态描述（即使未接端口也给出）。 */
    val family: ScreenFamily? get() = null

    /** 已接端口的屏幕；未接通为 null。本能力的屏幕工具只作用于它。 */
    val surface: Surface? get() = null

    /** 能运行技能脚本时提供。 */
    val scripts: BundledScripts? get() = null

    /** 任务开始：把工具需要的端口与状态写入工具上下文。 */
    fun onTaskStart(ctx: ToolContext) {}

    /** 任务中用户有过应答（回答了追问、交回接管）。 */
    fun onUserAnswered(ctx: ToolContext) {}

    /** 即将在本能力的屏幕上执行一次对外动作（发送、提交，见 `com.daiyimo.maeve.core.action.OutwardPacing`）。 */
    fun onOutwardStart(ctx: ToolContext, site: String) {}

    /** 对外动作结束，[done] 表示是否成功完成。 */
    fun onOutwardEnd(ctx: ToolContext, site: String, done: Boolean) {}

    /**
     * 即将执行一次改动屏幕的动作（[verb]）：按本能力的节奏先停顿，
     * 单个动作与批量动作中的每一步都会经过这里。[outward] 表示这一步是对外动作。
     */
    suspend fun beforeWrite(ctx: ToolContext, verb: Verb?, outward: Boolean): Paced = Paced.NONE

    /** 该动作执行完毕（[result] 为其结果）；[beforeWrite] 返回中断时不调用。 */
    fun afterWrite(ctx: ToolContext, verb: Verb?, result: JsonObject) {}

    /** 不操作屏幕的工具使用的守卫，每任务一份；没有则为 null。 */
    fun guard(): OffScreenGuard? = null

    /**
     * 核查前重新观察一次本能力的当前状态（不修改模型持有的任何内容）；
     * [withImage] 为真时附带一帧画面。没有可观察内容时返回 null。
     */
    suspend fun freshLook(ctx: ToolContext, withImage: Boolean): Look? = null
}

/**
 * 改动屏幕前的停顿结果。
 *
 * @property waitMs 实际停顿毫秒数，计入本次调用耗时
 * @property idleScrolls 停顿期间的轻量滚动次数（只进运行记录）
 * @property stop 非 null 时该动作不执行，直接以此作为调用结果
 */
internal class Paced(
    val waitMs: Long = 0,
    val idleScrolls: Int = 0,
    val stop: ToolExecution? = null,
) {
    companion object {
        val NONE = Paced()
    }
}

/** 能力向工具上下文注入条目的键；核心不关心值的具体类型。 */
class CapabilityKey<T : Any>(val name: String)

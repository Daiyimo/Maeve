package com.daiyimo.maeve.core.model

import com.daiyimo.maeve.core.observation.ReadTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 消息里的一个内容段。消息按段拆开装,而不是拼成纯文本:文字、图片、工具调用、
 * 工具结果、思考各是一种段,适配器按段类型决定它发出去时长什么样。
 */
@Serializable
sealed interface Part {
    @Serializable
    data class Text(val text: String) : Part

    /**
     * 一张截图。[width]/[height] 是编码后的像素尺寸,0 为不知道——只在估算这条消息
     * 占多少 token 时用得上,不影响发送。
     * [screenshotId] 记录这张图是哪一次读屏的画面,之后可以用 view_past_screens 回看;
     * 不是屏幕画面(比如读到的图片文件)为 null。
     */
    @Serializable data class Image(
        val pngBase64: String,
        val mime: String = "image/png",
        val width: Int = 0,
        val height: Int = 0,
        val screenshotId: Int? = null,
    ) : Part

    /**
     * 模型发起的一次工具调用,[argsJson] 是参数对象的 JSON 文本(原样带着走,不预先解析)。
     * [opaque] 存的是适配器私有的数据:同一次调用被回放时,认得它的那个适配器要靠它把请求
     * 恢复原样、一字不差地发回去;别的适配器根本不看这个字段。不需要回放的场景为 null。
     */
    @Serializable data class ToolCall(val id: String, val name: String, val argsJson: String, val opaque: String? = null) : Part

    /**
     * 一次工具调用的结果,[isError] 标明这是失败回执,让模型看出这步没成。
     * [reads] 是这次结果里读到、记进证据账本的观察(编号与形态),只在 Maeve 内部流转:
     * 裁剪上下文时按观察的形态决定怎么缩短,缩短后仍指回原编号;它不发给服务商。
     */
    @Serializable data class ToolResult(val callId: String, val content: String, val isError: Boolean, val reads: List<ReadTag> = emptyList()) : Part

    /**
     * 模型的一段私有思考。和 [ToolCall.opaque] 一样,[opaque] 也是适配器私有的:记下这段思考
     * 从哪来(例如兼容接口里收到它的地址和字段),回放时只有认得它的那个适配器用,别的适配器
     * 不看。旧的历史和不需要回放的为 null。
     */
    @Serializable data class Thinking(val text: String, val opaque: String? = null) : Part
}

/** 消息在对话里的角色:系统提示、用户、助手、工具回执。适配器按它决定发成哪一方的消息。 */
@Serializable
enum class Role { System, User, Assistant, Tool }

/** 消息是谁写的:[User] 是用户亲口说的(任务原话、任务中途补充的话与照片),[Maeve] 是 Maeve 生成的(附注、上下文、工具往返)。 */
@Serializable
enum class Author {
    User,
    // ★ 序列化名钉死为 "Maeve"：整个枚举会跟着对话一起写进磁盘，名字一改，
    //   已经存下来的历史对话全部读不出来（kotlinx.serialization 按名字匹配）。
    //   代码里叫 Maeve，磁盘上也永远写 Maeve，两边就靠这行对齐。
    @SerialName("Maeve") Maeve,
}

/**
 * 一条完整的消息:[role] 决定它发出去时算哪一方,[parts] 按顺序装内容段。
 * [author] 必须写明(没有默认值):压缩上下文时靠它认出哪些是用户原话——新加的地方要是
 * 放进了用户的话或照片却没标,它们会被当成 Maeve 的附注裁掉。
 * 这个字段只在 Maeve 内部用,不发给服务商。
 */
@Serializable
data class Message(val role: Role, val parts: List<Part>, val author: Author)

/** 提供给模型的一个工具:名字、给模型看的说明、参数的 JSON Schema,三样缺一不可。 */
data class ToolSpec(val name: String, val description: String, val parametersJsonSchema: String)

/** 模型为什么停下:自然结束、要调工具、撞到长度上限,或别的原因。 */
enum class StopReason { EndTurn, ToolUse, MaxTokens, Other }

/**
 * 一次请求的 token 账。[inputTokens] 是总输入 token(含缓存命中部分,OpenAI prompt_tokens 口径);
 * [outputTokens] 含思考;[reasoningTokens] 是其中服务商单独报出的思考 token(没单独报的为 0,如 Anthropic)。
 */
data class Usage(val inputTokens: Int, val outputTokens: Int, val cachedInputTokens: Int = 0, val reasoningTokens: Int = 0)

/** 一次模型回复:内容段、停止原因、token 账(拿不到计数时为 null)。 */
data class ModelReply(val parts: List<Part>, val stopReason: StopReason, val usage: Usage?)

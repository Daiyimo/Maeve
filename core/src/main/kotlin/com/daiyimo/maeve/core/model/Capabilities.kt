package com.daiyimo.maeve.core.model

/**
 * 一个模型实现对外声明的能力画像。agent 发出请求前先读它:要不要带工具定义、
 * 能不能把截图塞进消息、上下文按多大算余量、服务端提示词缓存要不要显式标注。
 * 这些是请求前的决策依据,和某一次回复的具体内容无关。
 */
data class Capabilities(
    /** 服务端接不接受 tools / function calling 参数;false 的模型只能干不依赖工具的活。 */
    val toolCalls: Boolean,
    /** 请求里能带图片(截图)且模型认得出内容;false 时看图相关的活不派给它。 */
    val vision: Boolean,
    /** 上下文窗口的 token 上限;压缩历史、裁短观察记录都按这个数留余量。 */
    val contextTokens: Int,
    /** 模型会先产出一段私有思考再给正文;true 时回复里可能出现 Thinking 段。 */
    val reasoning: Boolean,
    /**
     * 「在截图上框出元素」这项看图定位的准度已经过验证。没验证的模型框出来的点,
     * find_visually 要再找别的证据佐证才敢交出去。
     */
    val groundingVerified: Boolean = false,
    /**
     * 服务端怎样复用相同的提示词开头(省钱、提速);适配器按它决定请求里带不带缓存标记。
     * 三种形状见 [PromptCaching]。
     */
    val promptCaching: PromptCaching = PromptCaching.Implicit,
    /** 服务商明确公布的一次请求图片上限;未知时不猜,留 [Int.MAX_VALUE] 让调用方自己掂量。 */
    val maxImages: Int = Int.MAX_VALUE,
    /** 可选的本地压缩触发点,按整份请求折算出的 token 数计;null = 不主动压,等上下文放不下再说。 */
    val compactionTriggerTokens: Int? = null,
)

/**
 * 服务端提示词缓存的三种形状,按请求里要不要做事分:
 * [Implicit]:服务端自动按相同开头复用,请求里不用标;[Explicit]:要在消息上标出缓存到哪里
 * (cache_control),不标就不复用;[None]:不复用。
 */
enum class PromptCaching(val wire: String) {
    Implicit("implicit"), Explicit("explicit"), None("none");

    companion object {
        /**
         * 按配置里存的服务商标记找对应条目;null 或对不上(含将来新增的形状)都退回 [Implicit]——
         * 最保守的选择:不往请求里加任何标记,服务端想复用就让它自己复用。
         */
        fun of(wire: String?): PromptCaching = byWire(wire) ?: Implicit

        /** 按 wire 字面量在条目里找;找不到为 null,由调用方决定退回谁。 */
        private fun byWire(wire: String?): PromptCaching? = entries.firstOrNull { it.wire == wire }
    }
}

/** 模型在 Maeve 里的分工:Main 干主活,Vision 看屏幕,Light 跑轻量小请求,互不替代。 */
enum class ModelRole { Main, Vision, Light }

package com.daiyimo.maeve.core.model

/**
 * 一次「问模型要一段完整回复」的抽象。core:agent 只认这个接口跑任务,
 * 背后是哪家服务、走哪套接口形状(chat completions、responses、messages、generateContent)
 * 都由实现方吸收;换模型等于换一个实现,调用方一行都不用改。
 */
interface ChatModel {
    /**
     * 这个实现描述的服务端能力。agent 组织请求前先读它:要不要带工具定义、
     * 能不能把截图塞进消息、上下文留多少余量、提示词缓存要不要显式标注。
     */
    val capabilities: Capabilities

    /**
     * 模型标识,只用于记录:写进日志、追踪和运行台账,不参与路由,也不发给服务商。
     * 默认空串——不想上报名字的实现不用管它。
     */
    val name: String get() = ""

    /**
     * 拿系统提示、历史消息、可用工具问模型要一段完整回复,输入由调用方给全。
     * 网络、鉴权、限流、上下文超长等失败一律抛 [ModelError] 的对应子类,由上层统一解读;
     * 正常路径只可能正常返回或抛 [ModelError]。
     */
    suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply
}

/**
 * 在 [ChatModel] 之上多支持一种收法:文字边生成边回调,不用等整段回复收完。
 * 只实现了 [ChatModel] 的模型不受影响——调用方查出接口不支持时,照旧走一次性 [ChatModel.complete]。
 */
interface StreamingChatModel : ChatModel {
    /**
     * 输入和 [ChatModel.complete] 一样,多一个文字回调。
     * [onText] 的参数是到此刻为止已生成的可见文字全文(不是增量片段),且只含用户该看的部分:
     * 模型私有的思考段、工具调用的参数 JSON 都不会经过它。
     */
    suspend fun completeStreaming(
        system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit,
    ): ModelReply
}

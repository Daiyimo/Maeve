package com.daiyimo.maeve.core.usage

import com.daiyimo.maeve.core.model.Capabilities
import com.daiyimo.maeve.core.model.ChatModel
import com.daiyimo.maeve.core.model.Message
import com.daiyimo.maeve.core.model.ModelReply
import com.daiyimo.maeve.core.model.ModelRole
import com.daiyimo.maeve.core.model.StreamingChatModel
import com.daiyimo.maeve.core.model.ToolSpec
import com.daiyimo.maeve.core.model.Usage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * 一次模型调用是替哪个用途发的。
 *
 * 落盘只认 [key];读到不认识的 key(以后版本写的)统一按 [Other] 处理。
 */
enum class UsagePurpose(val key: String) {
    Main("main"), Vision("vision"), Light("light"), Other("other");

    companion object {
        /** 把模型的角色映射成用途。 */
        fun of(role: ModelRole): UsagePurpose = when (role) {
            ModelRole.Main -> Main
            ModelRole.Vision -> Vision
            ModelRole.Light -> Light
        }

        /** 从落盘的 key 还原用途;认不出来就给 [Other]。 */
        fun of(key: String): UsagePurpose = entries.firstOrNull { candidate -> candidate.key == key } ?: Other
    }
}

/**
 * 记账时随用量一起带上的来源信息:服务商 id、服务商当时的名字、模型名、用途。
 * 服务商后来被删掉了,名字也照样留在账本里。
 */
data class UsageSource(val providerId: String, val providerName: String, val model: String, val purpose: UsagePurpose)

/**
 * 用量的去处。
 *
 * 只关心“有没有用量”的实现覆盖两参 [record] 即可;知道本次实际发了几个 HTTP 请求的实现
 * 再覆盖三参版本。三参版本默认丢掉请求数、退回两参版本。
 */
fun interface UsageSink {
    fun record(source: UsageSource, usage: Usage?)

    /** [httpAttempts] 是这次逻辑调用真正交给 HTTP 客户端的推理请求数。 */
    fun record(source: UsageSource, usage: Usage?, httpAttempts: Int) = record(source, usage)
}

/**
 * 给模型套一层计量。
 *
 * 每次 [ChatModel.complete] 或 [StreamingChatModel.completeStreaming] 结束都上报一笔:成功且带用量才记 token。
 * 适配器为了兼容服务而重发的每个 HTTP 请求都计入请求数,最终回复的用量仍只记一次;本地就失败、没发请求的
 * 调用不会凭空多出请求数。包装对调用方透明:名字与能力照旧,异常原样抛出,逐段文字照常回调,
 * 原来支持逐段文字的模型套上后仍然支持。记账本身出错不影响模型调用。
 */
fun ChatModel.metered(source: UsageSource, sink: UsageSink): ChatModel {
    val meter = Meter(source, sink)
    return when (val target = this) {
        is StreamingChatModel -> MeteredStreamingModel(target, meter)
        else -> MeteredChatModel(target, meter)
    }
}

/** 一次调用前后的计量动作;两个包装类共用,保证两条路径的记账时机完全一致。 */
private class Meter(private val source: UsageSource, private val sink: UsageSink) {
    suspend fun track(call: suspend () -> ModelReply): ModelReply {
        // 在运行里发生的调用同时记进运行合计(见 RunUsageTally)
        val tally = currentCoroutineContext()[RunUsageTally]
        val attempts = ModelHttpAttempts()
        val reply = try {
            withContext(attempts) { call() }
        } catch (failure: Throwable) {
            submit(null, tally, attempts.started)
            throw failure
        }
        submit(reply.usage, tally, attempts.started)
        return reply
    }

    private fun submit(usage: Usage?, tally: RunUsageTally?, httpAttempts: Int) {
        runCatching { sink.record(source, usage, httpAttempts) }
        tally?.add(usage, httpAttempts)
    }
}

/** 普通模型外面的一层壳:只转发调用,顺便让 [Meter] 记一笔。 */
private class MeteredChatModel(private val inner: ChatModel, private val meter: Meter) : ChatModel {
    override val capabilities: Capabilities get() = inner.capabilities

    override val name: String get() = inner.name

    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply =
        meter.track { inner.complete(system, messages, tools) }
}

/** 支持逐段文字的模型外面的一层壳;两条入口都记,转发时保留原来的回调。 */
private class MeteredStreamingModel(private val inner: StreamingChatModel, private val meter: Meter) : StreamingChatModel {
    override val capabilities: Capabilities get() = inner.capabilities

    override val name: String get() = inner.name

    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply =
        meter.track { inner.complete(system, messages, tools) }

    override suspend fun completeStreaming(
        system: String,
        messages: List<Message>,
        tools: List<ToolSpec>,
        onText: (String) -> Unit,
    ): ModelReply = meter.track { inner.completeStreaming(system, messages, tools, onText) }
}

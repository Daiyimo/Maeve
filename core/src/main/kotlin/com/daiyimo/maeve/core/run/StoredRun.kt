package com.daiyimo.maeve.core.run

/** 运行状态的最小持久化记录。运行中断后只告知并允许重跑，不从屏幕中途续跑。 */
data class StoredRun(
    val id: RunId,
    val threadId: String,
    val goal: String,
    val phase: RunPhase,
    val reply: String? = null,
    val deliveryId: String? = null,
    val roundInProgress: Boolean = false,
    val triggerWindowKey: String? = null,
    val runSucceeded: Boolean? = null,
    /** 非本机任务的回复地址与入站编号；旧记录缺省时仍按本机任务恢复。 */
    val channelPeer: ChannelPeer? = null,
    val channelMessageId: String? = null,
    /** Exact on-disk execution record; distinct from the task identity. */
    val recordId: String? = null,
    val submissionId: String? = null,
)

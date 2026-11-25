package com.daiyimo.maeve.task

sealed interface StartResult<out R> {
    data class Started<R>(val run: R) : StartResult<R>
    /** 超出并行上限,排在队里第 [position] 个。 */
    data class Queued<R>(val run: R, val position: Int) : StartResult<R>
    /** 已登记过的渠道消息不再次执行，包括进程重启后服务端重投的消息。 */
    data object DuplicateChannelMessage : StartResult<Nothing>
    data object DuplicateSubmission : StartResult<Nothing>
    /** 这个对话已经有任务(在跑或在排队):照 M1 规则不收,由对话自己排队。 */
    data object AlreadyRunningInConversation : StartResult<Nothing>
}

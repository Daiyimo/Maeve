package com.daiyimo.maeve.core.record

/** 运行记录的接收方。实现方自己吞掉写盘错误:记录失败不能影响任务。 */
interface RunSink {
    fun event(e: RunEvent)

    /** [png] 是第 [mediaId] 张存下的画面。 */
    fun media(mediaId: Int, png: ByteArray) = Unit

    companion object {
        /** 不记录任何东西的接收方;测试和不需要记录的场景用。 */
        val NONE: RunSink = object : RunSink {
            override fun event(e: RunEvent) = Unit
        }
    }
}

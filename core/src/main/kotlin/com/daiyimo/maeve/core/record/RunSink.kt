package com.daiyimo.maeve.core.record

/**
 * 运行记录的接收端。实现方必须自行吞掉写盘异常:记录失败不能反过来影响任务。
 */
interface RunSink {
    fun event(e: RunEvent)

    /** 接收第 [mediaId] 张画面的 PNG 数据。 */
    fun media(mediaId: Int, png: ByteArray) = Unit

    companion object {
        /** 丢弃所有事件的接收端,供测试和不需要记录的路径使用。 */
        val NONE: RunSink = DiscardingSink
    }
}

private object DiscardingSink : RunSink {
    override fun event(e: RunEvent) = Unit
}

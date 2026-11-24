package com.daiyimo.maeve.core.tool

/** 输入是否已经交给端口/系统。sent 不等于界面已生效,unknown 不允许盲目重发。 */
enum class ActionDelivery(val wire: String) {
    Sent("sent"),
    NotSent("not_sent"),
    Unknown("unknown"),
}

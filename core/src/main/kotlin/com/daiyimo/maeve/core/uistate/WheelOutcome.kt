package com.daiyimo.maeve.core.uistate

/**
 * 把滚轮转到一个值的结果(端口 setWheel 的返回值,原样交回调用方)。
 *
 * [reached]:转到了没有;[reached] 为 false 时看 [error] 和 [hint]。
 * [value]:此刻显示的值,读不出为 null。[from]:开始时显示的。
 * [attempts]:做了几次动作——输入、滚一下、拖一下各算一次。
 * [method]:最后用的做法:text(直接输入)、actions(无障碍滚动)、drag(拖动)。
 * [error]:没到时的原因(ref_expired、not_a_wheel、unknown_value、at_end、
 * not_moving、not_reached……)。[hint]:端口的补充说明。
 */
data class WheelOutcome(
    /** 转到了没有;为 false 时看 [error] 和 [hint]。 */
    val reached: Boolean,
    /** 此刻显示的值,读不出为 null。 */
    val value: String?,
    /** 开始时显示的。 */
    val from: String?,
    val attempts: Int,
    val method: String,
    val error: String? = null,
    val hint: String? = null,
)

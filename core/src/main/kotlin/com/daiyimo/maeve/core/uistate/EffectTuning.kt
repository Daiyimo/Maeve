package com.daiyimo.maeve.core.uistate

/**
 * 判动作效果时用到的所有门槛集中在这一处。都是先定下的初值,之后按真机上记录的运行
 * (动作前后的界面与结果)校准;改动时连 ActionEffectTest、FrameDiffTest 的样例一起看。
 * 坐标一律按 0–999 归一化。
 */
object EffectTuning {
    // ---- 界面结构:元素表的差异怎么算 ----
    /** 算「明显变了」至少要有这么多处变化…… */
    const val MAJOR_MIN = 3
    /** ……且变化处数至少占有信息量元素的这个比例。 */
    const val MAJOR_SHARE = 0.10
    /** 算「同一个输入框换了字」:前后两个中心最远相距这么远。 */
    const val NEAR = 50
    /** 读回时认作同一个输入框:两个中心相距不超过这么远。 */
    const val SAME_FIELD = 120
    /** 有信息量的元素不到这个数(自绘界面)时,大面积变了画面也算明显变了。 */
    const val FEW_ELEMENTS = 3

    // ---- 画面:只作旁证,格子与阈值见 FrameTuning ----
    /** 坐标动作要看的落点周围区域(半宽)。 */
    const val POINT_REGION = 60

    // ---- 等待:各阶段的时间上限 ----
    /** 端口没在动作里等过界面时,判效果前等界面稳定的上限。 */
    const val SETTLE_MS = 1_500L
    /** 看起来没变时再等这么久重看一次(界面可能异步更新)。 */
    const val RECHECK_MS = 700L
    /** 动作前看一眼最多花的时间;来不及就只按端口的事实判。 */
    const val PROBE_MS = 1_000L
    /** 动作之后判效果最多再多花的时间(等待加读界面);到了就按已有事实下结论。 */
    const val BUDGET_MS = 2_500L
}

package com.daiyimo.maeve.core.uistate

/**
 * 站点知识、App 知识里 `pace` 一项说的这个网站或 App 对浏览快慢的要求
 * (App 里面的节奏另见 [com.daiyimo.maeve.core.cap.phone.AppPacing])。
 */
enum class PlacePace {
    /** 知识里没有特别说法。 */
    NORMAL,
    /**
     * 这个网站或 App 限制浏览快慢(看得太快、太多会被拦):
     * 每页停得更久,一个任务里能看的页、屏也更少。
     */
    CAREFUL;

    companion object {
        /** 取站点知识头部的值(`careful`;不写为 null)。 */
        fun of(value: String?): PlacePace = if (value == "careful") CAREFUL else NORMAL
    }
}

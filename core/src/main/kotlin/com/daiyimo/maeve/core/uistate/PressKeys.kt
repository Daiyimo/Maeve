package com.daiyimo.maeve.core.uistate

/**
 * press_key 能用的只有导航键和编辑键。音量、电源/休眠/唤醒、Home、最近任务、
 * 搜索/助手、相机、媒体键都作用于整台手机而不是 Maeve 的屏幕,一律拒绝;
 * 外壳进程注入按键时,再查一遍同一张表。
 */
object PressKeys {
    /** 放行的按键名 → 键码。 */
    val BY_NAME: Map<String, Int> = linkedMapOf(
        "KEYCODE_DPAD_UP" to 19, "KEYCODE_DPAD_DOWN" to 20, "KEYCODE_DPAD_LEFT" to 21, "KEYCODE_DPAD_RIGHT" to 22,
        "KEYCODE_DPAD_CENTER" to 23, "KEYCODE_ENTER" to 66, "KEYCODE_NUMPAD_ENTER" to 160, "KEYCODE_DEL" to 67,
        "KEYCODE_FORWARD_DEL" to 112, "KEYCODE_TAB" to 61, "KEYCODE_SPACE" to 62, "KEYCODE_ESCAPE" to 111,
        "KEYCODE_PAGE_UP" to 92, "KEYCODE_PAGE_DOWN" to 93, "KEYCODE_MOVE_HOME" to 122, "KEYCODE_MOVE_END" to 123,
        "KEYCODE_BACK" to 4, "KEYCODE_MENU" to 82,
    )
    /** 放行的键码集合。 */
    private val CODES: Set<Int> = BY_NAME.values.toSet()

    /** 这个键码在放行表里。 */
    fun isAllowed(keyCode: Int): Boolean = keyCode in CODES

    /**
     * 传入名字或数字键码;不在放行表里返回 null。
     * 名字和数字两样都对不上、或对上了但没在放行表里,都算不在。
     */
    fun resolve(key: String): Int? {
        val name = key.trim()
        return lookup(name)?.takeIf(::isAllowed)
    }

    /** 名字(KEYCODE_*)或数字键码;两样都对不上为 null。 */
    private fun lookup(name: String): Int? = name.toIntOrNull() ?: BY_NAME[name]
}

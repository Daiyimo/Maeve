package com.daiyimo.maeve.core.uistate


/**
 * 一种屏幕(手机、网页……)对应的一套同名动词工具:工具名是 `<id>_<动词>`,参数结构相同,各自的说明与允许的取值由这块屏给。
 * 每种有屏幕的能力各备一份;屏没接端口时工具照样登记(按名字查得到),只是不提供。
 */
class ScreenFamily(
    /** 能力的 id,也是这一套工具名的前缀。 */
    val id: String,
    /** 这块屏读屏编号的前缀;编号认不出归哪块屏时靠它判。 */
    val snapshotPrefix: String,
    /** 读屏结果里报「在哪」的字段名。 */
    val placeKey: String = APP_PLACE,
    /** press_key 允许按的键;null 表示不限。 */
    val keys: Set<Int>? = null,
    /** [keys] 不允许时讲给模型听的说明。 */
    val keysHint: String? = null,
    /** 经验路线会记下它的动作并原样回放。 */
    val routable: Boolean = false,
    /** 这一套里结果成文字的工具,沙箱里的 `maeve phone` 调得动。 */
    val sandboxCallable: Boolean = false,
    /** 这些动词的说明用本屏独一份(prompts/tools/<id>_<动词>.md),剩下的用共用模板。 */
    val ownPrompts: Set<String> = emptySet(),
    /** 这块屏上什么都还没打开时,反馈里给模型的提示(键名)。 */
    val emptyHint: String = "empty_screen",
) {
    /** 这一套里做 [base] 这个动作的工具名。 */
    fun tool(base: String) = "${id}_$base"

    companion object {
        /** 手机屏在「在哪」字段里报的名字。 */
        const val APP_PLACE = "currentApp"

        /** 网页屏在「在哪」字段里报的名字。 */
        const val SITE_PLACE = "currentSite"
    }
}

/** 一块接上了端口的屏幕,由有屏幕的能力给出。 */
class Surface(
    val family: ScreenFamily,
    /** 这块屏上的读屏与动作都经它。 */
    val screen: ScreenPort,
    /** 确认申报的 app 字段在这块屏上的写法:不对抛 ArgError,对了给规范写法。 */
    val declaredTarget: (String) -> String,
    /** 申报的 app 字段在这块屏上该写成什么(用于工具参数说明)。 */
    val targetNote: String,
    /** 运行状态里这块屏此刻的版本(如网页的手机版、电脑版);没有就写 null。 */
    val mode: (suspend () -> String?)? = null,
) {
    /** 这块屏的 id,就是家族的 id。 */
    val id: String get() = family.id
}

/**
 * 此刻在操作哪块屏:一次调用进行中,是它作用的那块(从 [enter] 到 [leave]);没有调用在进行时,是最近一次成功操作的那块,任务刚开始时是第一块。
 * 只给不点名屏幕的地方取默认值(确认、接管、核查证据、等界面稳定),不转发任何调用。调用可以嵌套(页面脚本里再发动作)。
 */
class SurfaceFocus(val surfaces: List<Surface>) {
    init {
        require(surfaces.isNotEmpty()) { "need at least one surface" }
    }

    /** 最近一次成功操作的是哪块屏。 */
    @Volatile private var lastSucceeded: Surface = surfaces.first()
    /** 进行中的调用栈:enter 入栈、leave 出栈,调用可嵌套。 */
    private val inFlight = ArrayDeque<Surface>()

    /** 此刻作用中的屏:有调用在进行是栈顶那块,否则是最近成功的,初始为第一块。 */
    val current: Surface get() = synchronized(inFlight) { inFlight.lastOrNull() } ?: lastSucceeded

    /** 按 id 找屏;没有这块屏时给 null。 */
    operator fun get(id: String): Surface? = surfaces.firstOrNull { it.id == id }

    /** 一次调用开始,作用在 [id] 那块屏上;给的不是屏或没给时,沿用此刻这块。 */
    fun enter(id: String?) {
        val s = id?.let(::get) ?: current
        synchronized(inFlight) { inFlight.addLast(s) }
    }

    /** 那次调用结束;[succeeded] 时它那块屏才算最近操作的屏,失败的不作数。 */
    fun leave(succeeded: Boolean) {
        val s = synchronized(inFlight) { inFlight.removeLastOrNull() } ?: return
        if (succeeded) lastSucceeded = s
    }
}

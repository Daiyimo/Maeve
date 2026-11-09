package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.run.SKIPPED_FOR_NEW_MESSAGE
import com.daiyimo.maeve.core.tool.FAILURE_MESSAGES
import com.daiyimo.maeve.core.tool.ToolExecution
import com.daiyimo.maeve.core.tool.fail

/** 换页、换屏到了本任务的上限时用的错误码(见 [PageLimitReached])。 */
const val PAGE_LIMIT = "page_limit"

/**
 * 换页、换屏前的那段停留没让这个动作做成:调用方不做它,按 [code] 报(见 [dwellStopped])。
 * 网页([com.daiyimo.maeve.core.cap.web.PageDwell])和 App 里(手机能力的停留)共用。
 */
internal sealed class DwellStop(message: String) : Exception(message) {
    /** 给调用方的错误码。 */
    abstract val code: String
    /** 跟在说明后面的细节;没有为 null。 */
    open val detail: String? get() = null
}

/**
 * 本任务里这个网站来的页、限制浏览快慢的这个 App 换的屏到了上限:不再换页、换屏,
 * 先回答或问用户。[site] 是网站主机名或 App 包名。
 */
internal class PageLimitReached(val site: String, val pages: Int, val cap: Int) :
    DwellStop("Pages or screens opened in $site in this task: $pages (limit $cap).") {
    override val code get() = PAGE_LIMIT
    override val detail get() = message
}

/** 停留中用户插了话:这次不做,先去读那句话。 */
internal class DwellInterrupted : DwellStop("The pause before this action was cut short by a new message from the user.") {
    override val code get() = SKIPPED_FOR_NEW_MESSAGE
}

/**
 * 不做这个动作的工具结果:说明为什么停、接下来怎么办
 * (到上限时附上这个网站、App 看了几页)。
 */
internal fun dwellStopped(e: DwellStop): ToolExecution {
    val parts = listOfNotNull(FAILURE_MESSAGES.getValue(e.code), e.detail)
    return fail(e.code, parts.joinToString(" "))
}

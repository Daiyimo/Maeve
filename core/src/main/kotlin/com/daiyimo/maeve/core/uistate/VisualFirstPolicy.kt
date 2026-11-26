package com.daiyimo.maeve.core.uistate

import com.daiyimo.maeve.core.access.AppAccessState

/** 读屏模式只按实时应用访问偏好决定,不再维护另一份包名清单。 */
object VisualFirstPolicy {
    fun applies(packageName: String?, state: AppAccessState): Boolean = state.visualFirst(packageName)
}

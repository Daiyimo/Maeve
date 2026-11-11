package com.daiyimo.maeve.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.cap.web.KeptPage
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

private val RowShape = RoundedCornerShape(14.dp)


sealed interface KeptRow {
    data class Live(val page: KeptPage) : KeptRow
    data class Closed(val reason: String?) : KeptRow

    companion object {
        /**
         * [ex] 那一轮下面显示什么:此刻留着的 [live] 就是这一轮留的,是 [Live];这一轮记着留过页、而它已经不在了,是 [Closed]
         * (没记下原因的按 Maeve 退出了说);别的为 null。
         */
        fun of(ex: Exchange, live: KeptPage?): KeptRow? = when {
            live != null && ex.runId != null && live.runId == ex.runId -> Live(live)
            ex.keptPage != null -> Closed(ex.keptPageClosed)
            else -> null
        }
    }
}



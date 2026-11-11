package com.daiyimo.maeve.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.R
import com.daiyimo.maeve.cap.phone.PhoneServices
import com.daiyimo.maeve.confirm.ConfirmationNotifier
import com.daiyimo.maeve.core.agent.TaskState
import com.daiyimo.maeve.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScreenRow(
    val key: String,
    val conversationId: String,
    val goal: String,
    val displayId: Int,
    val state: TaskState,
    val foreground: String? = null,
    val web: Boolean = false,
    /** 这个任务到此刻做过几件事;变了就重查一次最上面的 App。 */
    val acted: Int = 0,
)


fun orderedScreens(rows: List<ScreenRow>): List<ScreenRow> =
    rows.filter { it.displayId >= 0 }.sortedBy { it.displayId } + rows.filter { it.displayId < 0 }


private suspend fun foregroundOf(context: Context, ids: List<Int>): Map<Int, String> {
    if (ids.isEmpty()) return emptyMap()
    val hub = runCatching { PhoneServices.of(context).hub }.getOrNull() ?: return emptyMap()
    return withContext(Dispatchers.IO) {
        ids.mapNotNull { id ->
            val pkg = runCatching {
                val shell = hub.shell.value ?: return@runCatching null
                val task = shell.topTaskId(id)
                if (task < 0) null else shell.packageOfTask(task)
            }.getOrNull()
            pkg?.let { id to ConfirmationNotifier.appLabel(context, it) }
        }.toMap()
    }
}


private fun String.oneLine(): String = replace(Regex("\\s+"), " ").trim()


object ScreensCopy {
    val TITLE: String get() = tr(R.string.screens_title)
    val EMPTY: String get() = tr(R.string.screens_empty)
    val NOTE: String get() = tr(R.string.screens_note)
    val PREVIEW: String get() = tr(R.string.screens_preview)
    val HIDE_PREVIEW: String get() = tr(R.string.screens_hide_preview)

    /** 卡片第二行:正在用哪个 App(或浏览器);还没打开 App、或暂时看不到时各有一句。 */
    fun place(row: ScreenRow): String = when {
        row.web -> tr(R.string.screens_using_browser)
        row.displayId < 0 -> tr(R.string.screens_no_app)
        row.foreground != null -> tr(R.string.screens_using_app, row.foreground)
        else -> tr(R.string.screens_app_unknown)
    }

    fun state(state: TaskState): String = when (state) {
        TaskState.Idle -> tr(R.string.screens_waiting)
        is TaskState.Running -> tr(R.string.task_running)
        // 只是等条件(锁屏、助手重连)的不算要你处理;要你在手机上动一下手的才算
        is TaskState.Held -> if (state.reason.needsYou) tr(R.string.screens_needs_you) else tr(R.string.screens_waiting)
        is TaskState.WaitingTakeover, is TaskState.WaitingInput, is TaskState.WaitingConfirmation -> tr(R.string.screens_needs_you)
        is TaskState.Succeeded -> tr(R.string.task_done)
        is TaskState.Failed -> tr(R.string.task_not_done)
        TaskState.Cancelled -> tr(R.string.task_stopped)
    }
}

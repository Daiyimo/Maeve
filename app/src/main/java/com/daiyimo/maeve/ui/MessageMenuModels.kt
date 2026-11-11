package com.daiyimo.maeve.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.core.agent.QuoteMessage
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics

internal class MessageTarget(
    val key: String,
    val from: QuoteMessage.From,
    val text: String,
    val bounds: Rect,
    val lifted: @Composable () -> Unit,
)


internal class MessageMenuHost(val selecting: String?, val open: (MessageTarget) -> Unit)


internal val LocalMessageMenu = compositionLocalOf<MessageMenuHost?> { null }


internal fun Modifier.behindMenu(open: Boolean, progress: () -> Float): Modifier =
    if (!open) this else this
        .graphicsLayer { alpha = 1f - 0.18f * progress() }
        .clearAndSetSemantics {}


internal fun copyMessage(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Maeve", text))
    Toast.makeText(context, context.getString(R.string.chat_copied), Toast.LENGTH_SHORT).show()
}


private val QuoteShape = RoundedCornerShape(12.dp)

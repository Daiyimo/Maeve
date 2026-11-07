package com.daiyimo.maeve.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.daiyimo.maeve.R
import com.daiyimo.maeve.ui.MarkdownText
import com.daiyimo.maeve.ui.theme.GlassTier
import com.daiyimo.maeve.ui.theme.LocalMaeveSurfaces
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

/**
 * App 内统一弹窗：保留调用方的内容、动作和测试标识，只统一容器、正文层级与操作区。
 * 操作仍由调用方提供；这里只给它们一致的玻璃底、圆角和排布，不把确认逻辑收进组件。
 *
 * 弹窗是独立窗口，取不到页面背后的 backdrop 图层，容器用 sheet 档的填充表达毛玻璃材质。
 */
@Composable
fun MaeveDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val c = MaeveTheme.colors
    val sheet = LocalMaeveSurfaces.current[GlassTier.Sheet]
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = title?.let { content ->
            {
                CompositionLocalProvider(
                    LocalContentColor provides c.ink,
                    LocalTextStyle provides MaeveType.Title.copy(fontSize = 20.sp, lineHeight = 26.sp),
                ) { content() }
            }
        },
        text = text?.let { content ->
            {
                CompositionLocalProvider(
                    LocalContentColor provides c.ink2,
                    LocalTextStyle provides MaeveType.Body.copy(fontSize = 14.5.sp, lineHeight = 23.sp),
                ) { content() }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dismissButton != null) {
                    DialogAction(Modifier.weight(1f), accent = false) { dismissButton() }
                }
                DialogAction(Modifier.weight(1f), accent = true) { confirmButton() }
            }
        },
        shape = MaeveShapes.Card,
        containerColor = sheet.fill,
        titleContentColor = c.ink,
        textContentColor = c.ink2,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

/** 弹窗底部的操作钮:玻璃胶囊;确认的那颗带一圈强调色边。 */
@Composable
private fun DialogAction(modifier: Modifier, accent: Boolean, content: @Composable () -> Unit) {
    val c = MaeveTheme.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    Box(
        modifier.heightIn(min = 44.dp).cardSurface(c, shape, tier = GlassTier.Pill)
            .then(if (accent) Modifier.border(1.dp, c.accent.copy(alpha = 0.45f), shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 通知型弹窗的正文和回答使用同一套 Markdown 排版，不把原始标记直接交给用户。 */
@Composable
fun MaeveNoticeDialog(
    message: String,
    onDismiss: () -> Unit,
    title: String? = null,
    confirmText: String = stringResource(R.string.ok_got_it),
) {
    MaeveDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { MarkdownText(message, MaeveType.Body.copy(color = MaeveTheme.colors.ink2)) },
        confirmButton = { TextButton(onDismiss) { Text(confirmText) } },
    )
}

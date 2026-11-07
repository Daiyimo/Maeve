package com.daiyimo.maeve.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.ui.theme.GlassTier
import com.daiyimo.maeve.ui.theme.LocalMaeveSurfaces
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

/** 操作面板里的一项;[destructive] 为真时文字用出错红。 */
class SheetAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * 点一行后从底部升起的操作面板:顶上一行说明是在对什么操作,下面每项一行。
 * 点任何一项都会先执行它、再关掉面板。
 *
 * 面板是独立窗口,取不到页面背后的 backdrop 图层,所以这里用 sheet 档的填充和发丝边
 * 表达毛玻璃材质(形状、圆角与页内玻璃保持一致)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val c = MaeveTheme.colors
    val sheet = LocalMaeveSurfaces.current[GlassTier.Sheet]
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheet.fill,
        shape = MaeveShapes.Sheet,
    ) {
        Column(
            Modifier.fillMaxWidth().border(sheet.hairlineWidth, sheet.hairline, MaeveShapes.Sheet)
                .navigationBarsPadding().padding(bottom = 8.dp),
        ) {
            if (!title.isNullOrEmpty()) {
                Text(
                    title, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaeveType.Caption, color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            actions.forEachIndexed { i, a ->
                ListRow(
                    null, a.label, first = i == 0,
                    titleColor = if (a.destructive) c.bad else c.ink,
                    onClick = { a.onClick(); onDismiss() },
                )
            }
        }
    }
}

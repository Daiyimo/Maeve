package com.daiyimo.maeve.ui.components

import com.daiyimo.maeve.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.daiyimo.maeve.ui.theme.LocalGlassBackdrop
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MaeveSize
import com.daiyimo.maeve.ui.theme.MaeveSpace

/**
 * 子页面的骨架:页面底 + 带返回的顶栏 + 一列可滚动的内容(左右留 [MaeveSpace.Page],避开导航栏)。
 * 内容里直接放 [GroupLabel] / [GroupCard] / [FooterNote]。
 *
 * [eyebrow] 是标题上方那行等宽小字(如 `MODELS`、`SETTINGS · VOICE`),
 * 让每张子页都像一块仪表而不是一篇文章。给了它顶栏会高 8dp,内容起点跟着让开。
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageBackground(modifier) {
        val backdrop = LocalGlassBackdrop.current
        val statusBar = WindowInsets.statusBars.asPaddingValues()
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .glassSource(backdrop)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = MaeveSpace.Page)
                    // 顶栏总高(状态栏 + 56)留出来,内容从它底下开始;padding 在 scroll 里面,滚起来从玻璃后面过
                    .padding(
                        top = statusBar.calculateTopPadding() + MaeveSize.TopBar + MaeveSpace.S4,
                        bottom = MaeveSpace.S24,
                    ),
            ) {
                content()
            }
            TopBar(title, MaeveIcons.Back, onBack, stringResource(R.string.back), eyebrow = eyebrow)
        }
    }
}

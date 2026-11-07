package com.daiyimo.maeve.ui.components

import com.daiyimo.maeve.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.ui.theme.LocalGlassBackdrop
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveSize
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

/**
 * 二级页的骨架:页面底 + 顶栏(左上角返回)+ 下面可滚动的内容(左右各 16)。
 * 内容很长、要懒加载的页面不用它,自己在 [TopBar] 下面放 LazyColumn。
 *
 * 顶栏是毛玻璃:内容先画、顶栏后画,顶栏取样这一帧的实时内容,列表从它底下滚过去。
 */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    right: (@Composable RowScope.() -> Unit)? = null,
    /** 标题上方那行等宽小字;给了它顶栏高 8dp,内容起点跟着让开。 */
    eyebrow: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PageBackground {
        val backdrop = LocalGlassBackdrop.current
        val statusBar = WindowInsets.statusBars.asPaddingValues()
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier.fillMaxSize()
                    .glassSource(backdrop)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp)
                    .padding(
                        top = statusBar.calculateTopPadding() + MaeveSize.TopBar + 8.dp,
                        bottom = 24.dp,
                    ),
                content = content,
            )
            TopBar(title, MaeveIcons.Back, onBack, stringResource(R.string.back), right = right, eyebrow = eyebrow)
        }
    }
}

/** 行下面的一排操作按钮(小号胶囊),和行的文字对齐。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RowActions(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** 搜索框:浅填充底、圆角 14、高 40,左边放大镜。给了 [onSubmit] 时键盘上是「前往」,按下交给它。 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
) {
    val c = MaeveTheme.colors
    Row(
        Modifier.fillMaxWidth().height(40.dp).keepsKeyboard().cardSurface(c, MaeveShapes.Button).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(MaeveIcons.Search, null, Modifier.size(18.dp), tint = c.ink3)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = MaeveType.Body, color = c.ink3)
            BasicTextField(
                value, onValueChange, modifier.fillMaxWidth(), singleLine = true,
                textStyle = MaeveType.Body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                keyboardOptions = if (onSubmit != null) KeyboardOptions(imeAction = ImeAction.Go) else KeyboardOptions.Default,
                keyboardActions = KeyboardActions(onGo = { onSubmit?.invoke() }),
            )
        }
    }
}

/** 空状态:一两句居中的说明。 */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        style = MaeveType.Body, color = MaeveTheme.colors.ink3, textAlign = TextAlign.Center,
    )
}

package com.daiyimo.maeve.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

/**
 * 提示气泡：一个问号，点开才显示解释。**默认不显示**。
 *
 * 为什么要有它：界面上很多解释是"写给人第一次看"的，第 2 次看就是噪音。
 * 把它们从正文挪进气泡，正文只留一句能独立看懂的话，需要细节的人自己点。
 *
 * 用法上只有一条纪律：**别把主操作依赖的信息藏进来**。
 * 气泡里放的是"为什么"，不是"怎么做"——怎么做应该写在按钮上。
 *
 * @param text 解释。一句话，不换行讲道理。
 * @param title 气泡顶部的短标题，可为空
 */
@Composable
fun InfoTip(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    size: Dp = 15.dp,
    alignStart: Boolean = true,
) {
    val c = MaeveTheme.colors
    var open by remember { mutableStateOf(false) }
    val a by animateFloatAsState(if (open) 1f else 0f, tween(150), label = "tip")

    Column(
        modifier.then(if (alignStart) Modifier.fillMaxWidth() else Modifier),
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        // 触发器：一枚很淡的圆圈问号。不用 i，i 在中文语境里读不出"这里有解释"。
        Box(
            Modifier.size(size).clip(CircleShape)
                .border(0.5.dp, c.rule, CircleShape)
                .clickable(role = Role.Button) { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "?",
                style = MaeveType.Caption.copy(fontSize = (size.value * 0.62f).sp),
                color = c.ink3,
                textAlign = TextAlign.Center,
            )
        }
        if (a > 0.01f) {
            Column(
                Modifier.padding(top = 6.dp).widthIn(max = 300.dp)
                    .alpha(a)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surfaceHigh)
                    .border(0.5.dp, c.hair, RoundedCornerShape(10.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(title, style = MaeveType.Label.copy(fontSize = 12.sp), color = c.ink2)
                }
                Text(text, style = MaeveType.Caption, color = c.ink2)
            }
        }
    }
}

/**
 * 行内版：把问号放在一行文字的末尾，气泡向右展开。
 * 用在列表行、设置项后面——比单独占一行省地方。
 */
@Composable
fun InfoTipInline(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    overlay: Boolean = false,
) {
    val c = MaeveTheme.colors
    var open by remember { mutableStateOf(false) }
    val bubble: @Composable () -> Unit = {
        Text(
            text, Modifier.padding(top = 5.dp).widthIn(max = 300.dp),
            style = MaeveType.Caption, color = c.ink2,
        )
    }
    val button: @Composable () -> Unit = {
        Box(
            Modifier.size(size).clip(CircleShape)
                .border(0.5.dp, c.rule, CircleShape)
                .clickable(role = Role.Button) { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Text("?", style = MaeveType.Caption.copy(fontSize = (size.value * 0.62f).sp), color = c.ink3)
        }
    }
    if (!overlay) {
        Column(modifier) {
            button()
            if (open) bubble()
        }
    } else {
        // ★ overlay：气泡用 Popup 浮在**问号下方**，不参与行布局。
        //   用在列表行尾：那里只有几十 dp，气泡若参与测量会把开关挤出去，
        //   行高也会跳。Popup 自带「点外面关掉」，且能盖住相邻行。
        val d = LocalDensity.current
        Box(modifier) {
            button()
            if (open) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, with(d) { (size + 6.dp).roundToPx() }),
                    onDismissRequest = { open = false },
                ) {
                    Box(Modifier.widthIn(max = 248.dp)) {
                        Text(
                            text,
                            Modifier.background(c.surfaceHigh, RoundedCornerShape(10.dp))
                                .border(0.5.dp, c.rule, RoundedCornerShape(10.dp))
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            style = MaeveType.Caption, color = c.ink2,
                        )
                    }
                }
            }
        }
    }
}

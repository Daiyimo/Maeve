package com.daiyimo.maeve.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.daiyimo.maeve.R
import com.daiyimo.maeve.core.agent.QuoteMessage
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.daiyimo.maeve.ui.components.GlassBlurSupported
import com.daiyimo.maeve.ui.components.cardSurface
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

/**
 * 对话页里长按的一条消息:[key] 认是哪一条(「选择文字」记的就是它),[from] 是谁说的,[text] 是复制、引用用的纯文字
 * (回答去掉了 Markdown 符号,用户的话去掉了附件、屏幕、引用那几行),[bounds] 是它在窗口里的位置和大小,[lifted] 画抬起的那一张。
 */
internal class MessageTarget(
    val key: String,
    val from: QuoteMessage.From,
    val text: String,
    val bounds: Rect,
    val lifted: @Composable () -> Unit,
)

/** 对话页给消息的长按菜单:[selecting] 是此刻处在「选择文字」里的那一条,[open] 弹出菜单。 */
internal class MessageMenuHost(val selecting: String?, val open: (MessageTarget) -> Unit)

/** 没有时(单独测某个部件)消息照旧长按就选中文字。 */
internal val LocalMessageMenu = compositionLocalOf<MessageMenuHost?> { null }

/**
 * 一条消息的文字部分:长按(振一下)弹出复制 / 引用 / 选择文字的菜单;选了「选择文字」的那条换回系统的长按选中([selectTag] 标在选区上)。
 * 文字里的链接照旧点开(点击由里面的文字先处理)。文件卡片、「查看过程」不包在里面。[text] 为空时不弹菜单。
 * [modifier] 是这条消息自己的底(背景、宽度、内边距),抬起的那张按它的位置和大小摆;外边距放在外面。
 */
@Composable
internal fun MessageArea(
    key: String,
    from: QuoteMessage.From,
    text: String,
    selectTag: String,
    modifier: Modifier = Modifier,
    lifted: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val host = LocalMessageMenu.current
    if (host == null || host.selecting == key || text.isBlank()) {
        SelectionContainer(modifier.testTag(selectTag)) { content() }
        return
    }
    val haptics = MaeveTheme.haptics
    val label = stringResource(R.string.chat_message_actions)
    // 在窗口里的位置和大小(不裁掉滚出列表的部分);只在回调里读,不引起重组
    val bounds = remember { mutableStateOf(Rect.Zero) }
    // 手势一直用最新的这一份(流式回答时会不停重组),不因重组重启、丢掉按到一半的长按
    val open by rememberUpdatedState({ host.open(MessageTarget(key, from, text, bounds.value, lifted)) })
    Box(
        Modifier
            .onGloballyPositioned { bounds.value = Rect(it.positionInWindow(), it.size.toSize()) }
            .then(modifier)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    // 长按打开消息菜单 = Medium,统一走 HapticEngine
                    haptics.medium()
                    open()
                })
            }
            .semantics { onLongClick(label) { open(); true } }
            .testTag("message-$key"),
    ) { content() }
}

/**
 * 长按一条消息后的菜单(和侧栏里长按对话同一个样子,见 [LongPressMenu]):那条消息抬起,下面是复制、引用、选择文字;
 * 一条已答完的回答还能「从这里分支」,用户自己的话能「从这里重新问」(见 [onBranch]、[onBranchRetry])。用户自己的话靠右,菜单也靠右。
 */
@Composable
internal fun MessageMenu(
    target: MessageTarget,
    progress: () -> Float,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onWithdraw: (() -> Unit)? = null,
    onBranch: (() -> Unit)? = null,
    onBranchRetry: (() -> Unit)? = null,
) {
    LongPressMenu(
        target.bounds, progress, pane = stringResource(R.string.chat_message_actions), tag = "message-menu",
        entries = buildList {
            add(MenuEntry(stringResource(R.string.chat_copy), MaeveIcons.Copy, "message-menu-copy", onClick = onCopy))
            add(MenuEntry(stringResource(R.string.chat_quote), MaeveIcons.Quote, "message-menu-quote", onClick = onQuote))
            add(MenuEntry(stringResource(R.string.chat_select_text), MaeveIcons.SelectText, "message-menu-select", onClick = onSelect))
            onBranch?.let { add(MenuEntry(stringResource(R.string.chat_branch), MaeveIcons.Route, "message-menu-branch", onClick = it)) }
            onBranchRetry?.let { add(MenuEntry(stringResource(R.string.chat_branch_retry), MaeveIcons.Route, "message-menu-branch-retry", onClick = it)) }
            onEdit?.let { add(MenuEntry(stringResource(R.string.chat_edit_message), MaeveIcons.Edit, "message-menu-edit", onClick = it)) }
            onWithdraw?.let { add(MenuEntry(stringResource(R.string.chat_withdraw_message), MaeveIcons.Close, "message-menu-withdraw", onClick = it)) }
        },
        onDismiss = onDismiss,
        alignEnd = target.from == QuoteMessage.From.You,
        lifted = target.lifted,
    )
}

/**
 * 菜单开着时后面那层：只降透明度，**不挂 renderEffect**。
 *
 * ★ 这里原来是 `graphicsLayer { renderEffect = BlurEffect(...) }`，直接挂在
 *   对话页内容层上。内容层里面有大量开了 layer 的玻璃件，hwui 每帧都要
 *   把这棵子树记进同一个 DirtyStack，只增不减，攒到 512 帧就
 *   `computeTransformImpl` 栈溢出（RenderThread SIGSEGV）。
 *   表现就是「点设置 / 点关于 / 长按消息」随机闪退。
 *
 * 变暗用 alpha 就够，视觉差别很小，但完全没有崩溃风险。
 */
internal fun Modifier.behindMenu(open: Boolean, progress: () -> Float): Modifier =
    if (!open) this else this
        .graphicsLayer { alpha = 1f - 0.18f * progress() }
        .clearAndSetSemantics {}

/** 把消息的纯文字放进剪贴板,提示一句「已复制」。 */
internal fun copyMessage(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Maeve", text))
    Toast.makeText(context, context.getString(R.string.chat_copied), Toast.LENGTH_SHORT).show()
}

/** 引用怎么念给读屏:「引用 Maeve:……」「引用你的话:……」。 */
@Composable
private fun quoteDescription(q: QuoteMessage.Quote): String =
    stringResource(if (q.from == QuoteMessage.From.Maeve) R.string.chat_quoting_maeve else R.string.chat_quoting_you, q.text)

/** 输入框上方待发的引用:左边一道竖线,引的内容最多两行,右边 × 去掉。 */
@Composable
internal fun QuoteChip(quote: QuoteMessage.Quote, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaeveTheme.colors
    val remove = stringResource(R.string.chat_quote_remove)
    val said = quoteDescription(quote)
    Row(
        modifier.fillMaxWidth().testTag("quote-chip").cardSurface(c, QuoteShape).padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(30.dp).background(c.accent, CircleShape))
        Text(
            quote.text, Modifier.weight(1f).padding(start = 10.dp).semantics { contentDescription = said },
            style = MaeveType.Caption, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = remove, onClick = onRemove)
                .semantics { contentDescription = remove }
                .testTag("quote-remove"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MaeveIcons.Close, null, Modifier.size(14.dp), tint = c.ink3)
        }
    }
}

/** 用户的话里引的那条:气泡上方一小块淡色,最多两行。 */
@Composable
internal fun SentQuote(quote: QuoteMessage.Quote, maxWidth: Dp) {
    val c = MaeveTheme.colors
    val said = quoteDescription(quote)
    Row(
        Modifier.padding(bottom = 4.dp).widthIn(max = maxWidth).background(c.ink.copy(alpha = 0.05f), QuoteShape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = said }
            .testTag("sent-quote"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(16.dp).background(c.ink3, CircleShape))
        Text(quote.text, Modifier.padding(start = 8.dp), style = MaeveType.Caption, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private val QuoteShape = RoundedCornerShape(12.dp)

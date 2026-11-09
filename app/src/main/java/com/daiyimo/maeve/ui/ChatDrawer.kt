package com.daiyimo.maeve.ui

import com.daiyimo.maeve.ui.components.glassFill
import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.ui.components.DirectionalIcon
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.daiyimo.maeve.ui.components.MaeveDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import com.daiyimo.maeve.chat.DeleteImpact
import com.daiyimo.maeve.i18n.trPlural
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.chat.ConversationSummary
import com.daiyimo.maeve.settings.Prerequisites
import com.daiyimo.maeve.ui.components.GlassBlurSupported
import com.daiyimo.maeve.ui.components.KeyboardDismissArea
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.toSize
import com.daiyimo.maeve.ui.theme.MaeveMotion
import com.daiyimo.maeve.ui.theme.GlassTier
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.components.MaeveLogo
import com.daiyimo.maeve.ui.components.keepsKeyboard
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.daiyimo.maeve.ui.components.cardSurface
import com.daiyimo.maeve.ui.components.lazyListScrollbar
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MaeveSize
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import com.daiyimo.maeve.i18n.uiLocale

/** 侧栏里的一条对话。[id] 为 null 是还没存进对话列表的当前对话。 */
data class DrawerConversation(
    val id: String?,
    val title: String,
    val subtitle: String,
    val running: Boolean,
    val current: Boolean,
    val enabled: Boolean,
    /** 最后改动的时间(写在标题右边);还没存进对话列表的为空。 */
    val time: String = "",
    /** 置顶了:标题前一个小图钉,长按菜单里是「取消置顶」。 */
    val pinned: Boolean = false,
    /** 在浏览器页上对着一页开始的对话:那个网站(主机名),标题后面一个「网页」小标签;不是为 null。 */
    val site: String? = null,
    /** 从另一个对话分支出来的:标题前一个小分支图标。 */
    val branch: Boolean = false,
)

/** 侧栏里的对话分段:进行中、置顶、最近。 */
data class DrawerSections(
    val running: List<DrawerConversation>,
    val recent: List<DrawerConversation>,
    val pinned: List<DrawerConversation> = emptyList(),
)

/** 当前对话在侧栏里的样子:有没有任务在跑、标题、副标题(在做什么)。 */
data class CurrentConversation(val id: String?, val running: Boolean, val title: String, val subtitle: String)

/**
 * 侧栏的三段:「进行中」是当前对话(有任务时)和别的有任务在跑的对话;「置顶」是其余置顶了的对话,最近置顶的在前;
 * 「最近」是剩下的对话,最近改过的在前。每个对话只出现在一段里:置顶的对话有任务在跑时列在进行中(带图钉),跑完回到置顶。
 * [canSwitch] 为假(任务进行中、没开并行)时只有当前对话可点。
 */
fun drawerSections(
    all: List<ConversationSummary>,
    current: CurrentConversation,
    others: List<String>,
    otherTitle: (String) -> String?,
    canSwitch: Boolean,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): DrawerSections {
    val byId = all.associateBy { it.id }
    fun timeOf(id: String?) = byId[id]?.let { conversationTime(it.updatedAt, now, zone) }.orEmpty()
    fun pinned(id: String?) = byId[id]?.pinnedAt != null
    val running = buildList {
        if (current.running) add(DrawerConversation(current.id, current.title.ifBlank { tr(R.string.chat_new) }, current.subtitle, true, true, true, time = timeOf(current.id), pinned = pinned(current.id), site = byId[current.id]?.page?.host, branch = byId[current.id]?.branchOf != null))
        others.filter { it != current.id }.distinct().forEach { id ->
            val title = (byId[id]?.title ?: otherTitle(id))?.let(ChatCopy::plainTitle)?.ifBlank { null } ?: tr(R.string.drawer_running_task)
            add(DrawerConversation(id, title, tr(R.string.drawer_running), running = true, current = false, enabled = canSwitch, time = timeOf(id), pinned = pinned(id), site = byId[id]?.page?.host, branch = byId[id]?.branchOf != null))
        }
    }
    val runningIds = running.mapNotNull { it.id }.toSet()
    fun entry(s: ConversationSummary): DrawerConversation {
        val isCurrent = s.id == current.id
        return DrawerConversation(s.id, ChatCopy.plainTitle(s.title), "", false, isCurrent, canSwitch || isCurrent, conversationTime(s.updatedAt, now, zone), pinned = s.pinnedAt != null,
            site = s.page?.host, branch = s.branchOf != null)
    }
    val rest = all.filter { it.id !in runningIds }
    val pinnedRows = rest.filter { it.pinnedAt != null }.sortedByDescending { it.pinnedAt }.map(::entry)
    val recent = rest.asSequence().filter { it.pinnedAt == null }.take(MAX_RECENT).map(::entry).toList()
    return DrawerSections(running, recent, pinnedRows)
}

/** 对话的时间:今天写时刻,昨天写「昨天」,一周内写星期几,今年写几月几日,更早带年份。 */
fun conversationTime(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(at).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day: LocalDate = t.toLocalDate()
    val days = today.toEpochDay() - day.toEpochDay()
    return when {
        days <= 0L -> "%d:%02d".format(t.hour, t.minute)
        days == 1L -> tr(R.string.drawer_yesterday)
        days < 7L -> t.dayOfWeek.getDisplayName(DateTextStyle.SHORT, uiLocale())
        day.year == today.year -> tr(R.string.date_month_day, day.monthValue, day.month.getDisplayName(DateTextStyle.SHORT, uiLocale()), day.dayOfMonth)
        else -> tr(R.string.date_year_month_day, day.year, day.monthValue, day.month.getDisplayName(DateTextStyle.SHORT, uiLocale()), day.dayOfMonth)
    }
}

/** 侧栏顶上 Maeve 名字下面那行状态,和它是不是好的(绿点)还是要处理(橙点)。 */
fun readiness(check: Prerequisites.Check): Pair<String, Boolean> = when {
    !check.canStartTask -> tr(R.string.chat_cannot_start) to false
    !check.phoneReady -> tr(R.string.drawer_phone_not_ready) to false
    else -> tr(R.string.all_ready) to true
}

private const val MAX_RECENT = 50

/**
 * 侧栏:从左边滑出、右边圆角。顶部是标志和状态;四个入口;中间是进行中(段标题右边进「后台屏」页)和最近的对话;最底下设置和关于并排。
 * 打开和关上由外面的抽屉管,这里只摆内容,点了什么交给回调。
 */
@Composable
fun ChatDrawer(
    check: Prerequisites.Check,
    sections: DrawerSections,
    canSwitch: Boolean,
    onNew: () -> Unit,
    onPlans: () -> Unit,
    onMemory: () -> Unit,
    onBrowser: () -> Unit,
    onScreens: () -> Unit = {},
    onSettings: () -> Unit,
    onOpen: (DrawerConversation) -> Unit,
    onAbout: () -> Unit = {},
    /** 有新版本:「关于 Maeve」带小红点。 */
    aboutDot: Boolean = false,
    /** 长按一条存过的对话:在它原地弹出操作菜单(置顶、改名、删除);带上那一行在窗口里的位置和大小。 */
    onActions: (DrawerConversation, Rect) -> Unit = { _, _ -> },
) {
    val c = MaeveTheme.colors
    // 自适应宽度：取「244dp」与「屏宽 62%」里较小的那个。
    // 用 LocalConfiguration 而不是 fillMaxWidth —— fillMaxWidth 会把 widthIn 的封顶覆盖掉。
    val screen = LocalConfiguration.current.screenWidthDp.dp
    val width = minOf(DrawerWidth, screen * DRAWER_MAX_FRACTION)
    Column(
        // ★ 不要在这里再写 fillMaxWidth()。
        //   旧版是 `widthIn(max = DrawerWidth).fillMaxWidth()`——后面的 fillMaxWidth
        //   会把宽度重新拉成父级的最大值，widthIn 的封顶直接失效，侧栏铺满整屏。
        Modifier.fillMaxHeight().width(width)
            .cardSurface(c, DrawerShape, tier = GlassTier.Sheet)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
            .testTag("drawer"),
    ) {
        val (state, ok) = readiness(check)
        // 顶部：品牌 + 状态，右边一个实色胶囊的「新对话」。
        // 整个抽屉只留这一个实色元素——它是唯一的主操作，其余全是线条和文字。
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MaeveLogo(34.dp, corner = 10.dp)
            Column(Modifier.weight(1f)) {
                Text("Maeve", style = MaeveType.Title, color = c.ink)
                // 状态是读数,不是句子:等宽、小、宽字距
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(5.dp).background(if (ok) c.ok else c.signal, CircleShape))
                    Text(
                        state, style = MaeveType.EyebrowSmall.copy(letterSpacing = 1.1.sp),
                        color = c.ink3, maxLines = 1,
                    )
                }
            }
            NewChatPill(enabled = canSwitch, onClick = onNew)
        }
        // 入口：裸线条图标，没有底色方块。侧栏是目录，方块底会让四个入口看起来像四个按钮。
        Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            NavEntry(MaeveIcons.Clock, stringResource(R.string.drawer_plans), onClick = onPlans)
            NavEntry(MaeveIcons.Heart, stringResource(R.string.memory_and_skills), onClick = onMemory)
            NavEntry(MaeveIcons.Globe, stringResource(R.string.drawer_browser), onClick = onBrowser)
        }
        // 入口区与对话列表之间一道发丝线。dsh-Go 靠分组和留白分区，
        // 这里没有分组能力，一条极细的线是最省的分界——它让"工具"和"对话"读作两块东西。
        Box(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 4.dp)
                .height(0.5.dp).background(c.hair),
        )
        // 侧栏关着时列表只剩末尾那一项;打开时对话插在它前面,列表会按原来那一项停在最底下。最上面一项变了就回到顶上
        val list = rememberLazyListState()
        val first = when {
            sections.running.isNotEmpty() -> "h-running"
            sections.pinned.isNotEmpty() -> "h-pinned"
            sections.recent.isNotEmpty() -> "h-recent"
            else -> "end"
        }
        LaunchedEffect(first) { list.scrollToItem(0) }
        // 列表不套 Box、不做渐隐遮罩。
        // 旧版用一层「白色玻璃色」渐变贴在上下边缘，侧栏本身是暖纸色，盖上去变成
        // 一整块冷白（#F4F5F6 vs #F0ECF0）——这就是你看到的历史记录「一块白色」。
        // 想做渐隐只能改 ShaderMask(DstIn)，不能盖实色。
        LazyColumn(Modifier.fillMaxWidth().weight(1f).lazyListScrollbar(list), state = list) {
            if (sections.running.isNotEmpty()) {
                // 「进行中」这一段的右边进「后台屏」页:看每个在跑的任务正在用哪个 App、实时画面
                item("h-running") { RunningLabel(onScreens) }
                items(sections.running, key = { "r-${it.id}" }) { ConversationEntry(it, onOpen, onActions) }
            }
            // 换不了对话时的说明放在第一段存着的对话(置顶或最近)上面
            val busyNote: LazyListScope.() -> Unit = {
                if (!canSwitch) {
                    item("busy") {
                        Text(
                            stringResource(R.string.drawer_busy), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 6.dp),
                            style = MaeveType.Hint, color = c.ink3,
                        )
                    }
                }
            }
            if (sections.pinned.isNotEmpty()) {
                item("h-pinned") { SectionLabel(stringResource(R.string.drawer_pinned)) }
                busyNote()
                items(sections.pinned, key = { "p-${it.id}" }) { ConversationEntry(it, onOpen, onActions) }
            }
            if (sections.recent.isNotEmpty()) {
                item("h-recent") { SectionLabel(stringResource(R.string.drawer_recent)) }
                if (sections.pinned.isEmpty()) busyNote()
                items(sections.recent, key = { "c-${it.id}" }) { ConversationEntry(it, onOpen, onActions) }
            }
            item("end") { Box(Modifier.height(12.dp)) }
        }
        // 设置和关于并排固定在侧栏最底下,对话再多也不被挤走:设置靠左,关于靠右,中间留空
        Row(
            Modifier.fillMaxWidth().drawBehind { drawLine(c.hair, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx()) }
                .padding(horizontal = 12.dp, vertical = 6.dp).testTag("drawer-footer"),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NavEntry(MaeveIcons.Gear, stringResource(R.string.settings_title), Modifier, onClick = onSettings)
            NavEntry(MaeveIcons.Info, AboutCopy.TITLE, Modifier, dot = aboutDot, onClick = onAbout)
        }
    }
}

@Composable
private fun NavEntry(
    icon: ImageVector, text: String, modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true, dot: Boolean = false, onClick: () -> Unit,
) {
    val c = MaeveTheme.colors
    Row(
        modifier.height(44.dp).clip(NavShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 裸图标，没有底色方块。灰色方块会让每一行都变成一个「按钮」，
        // 四个入口并排看像工具栏；裸线条更安静，也更像目录。
        Icon(icon, null, Modifier.size(19.dp), tint = c.ink2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text, style = MaeveType.Body, color = c.ink, maxLines = 1, softWrap = false)
            if (dot) UpdateDot()
        }
    }
}

/**
 * 「新对话」：抽屉里唯一的实色元素。
 *
 * 学 dsh-Go 的一个原则——一个容器里只能有一个主操作。侧栏其余部分全是线条、
 * 文字和留白，这个胶囊才有分量；如果每个入口都给底色方块，它就淹没在一片按钮里了。
 */
@Composable
private fun NewChatPill(enabled: Boolean, onClick: () -> Unit) {
    val c = MaeveTheme.colors
    val shape = CircleShape
    Row(
        // wrapContentWidth：Row 在 Row 父级里会吃满剩余宽度，胶囊就变成一条横贯的色带
        Modifier.wrapContentWidth().height(32.dp).clip(shape)
            .background(if (enabled) c.accent else c.neutralSoft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            MaeveIcons.New, null, Modifier.size(16.dp),
            tint = if (enabled) c.onAccent else c.ink3,
        )
        Text(
            stringResource(R.string.chat_new),
            style = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
            color = if (enabled) c.onAccent else c.ink3, maxLines = 1, softWrap = false,
        )
    }
}

/** 「进行中」的段标题,右边一个「后台屏 ›」进后台屏页(只有有任务在跑时才有这一段)。 */
@Composable
private fun RunningLabel(onScreens: () -> Unit) {
    val c = MaeveTheme.colors
    Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) { SectionLabel(stringResource(R.string.drawer_running)) }
        Row(
            Modifier.clip(NavShape).clickable(role = Role.Button, onClick = onScreens)
                .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 4.dp).testTag("drawer-screens"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(MaeveIcons.Screens, null, Modifier.size(14.dp), tint = c.ink3)
            Text(stringResource(R.string.drawer_screens), style = MaeveType.Hint, color = c.ink2)
            DirectionalIcon(MaeveIcons.Chevron, null, Modifier.size(10.dp), tint = c.ink3)
        }
    }
}

/** 段标题:等宽大写 + 宽字距,和设置页的分组标题同一套口音。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 6.dp),
        style = MaeveType.Label.copy(fontSize = 12.5.sp),
        color = MaeveTheme.colors.ink3,
    )
}

/**
 * 一条对话:点开它;长按(带一下振动)在原地弹出它的操作菜单(置顶、改名、删除)。换不了对话时(任务进行中、没开并行)点不开,长按照样能用。
 * 还没存进对话列表的(没有编号)没东西可改,不响应长按。置顶了的标题前有个小图钉。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationEntry(item: DrawerConversation, onOpen: (DrawerConversation) -> Unit, onActions: (DrawerConversation, Rect) -> Unit) {
    val c = MaeveTheme.colors
    val deletable = item.id != null
    val haptics = MaeveTheme.haptics
    // 这一行在窗口里的位置和大小(不裁掉滚出列表的部分),菜单照它摆出抬起的那一行;只在回调里读,不引起重组
    val bounds = remember { mutableStateOf(Rect.Zero) }
    // 选中态用柔和的强调色块，不是左侧一根竖条。
    // 竖条是"提醒你去注意"，色块是"这块就是当前的地方"——后者更安静，也更像列表本身的一部分。
    // 0→1 的过渡靠 animateFloatAsState：切换对话时填充淡入淡出，不是瞬间跳。
    val fill = animateFloatAsState(
        targetValue = if (item.current) 1f else 0f,
        animationSpec = tween(MENU_IN_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "conversation-fill",
    )
    Column(
        Modifier.padding(horizontal = 8.dp).fillMaxWidth()
            .onGloballyPositioned { bounds.value = Rect(it.positionInWindow(), it.size.toSize()) }
            .clip(NavShape)
            .drawBehind {
                if (fill.value > 0.001f) {
                    drawRoundRect(
                        color = c.accent.copy(alpha = 0.10f * fill.value),
                        size = size,
                        cornerRadius = CornerRadius(11.dp.toPx()),
                    )
                }
            }
            .then(
                if (deletable) Modifier.combinedClickable(
                    onClickLabel = null,
                    onLongClickLabel = stringResource(R.string.drawer_actions),
                    // 长按打开会话菜单 = Medium,统一走 HapticEngine
                    onLongClick = { haptics.medium(); onActions(item, bounds.value) },
                    onClick = { if (item.enabled) onOpen(item) },
                ) else Modifier.clickable(enabled = item.enabled, onClick = { onOpen(item) }),
            )
            .testTag("conversation-row")
            .alpha(if (item.enabled) 1f else 0.45f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        ConversationRowContent(item)
    }
}

/** 一条对话里面的东西:进行中的小圆点、图钉、标题、时间,有副标题时下面一行。侧栏里那一行和长按抬起的那一张都用它。 */
@Composable
internal fun ConversationRowContent(item: DrawerConversation) {
    val c = MaeveTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (item.running) Box(Modifier.size(7.dp).background(c.accent, CircleShape))
            if (item.pinned) Icon(MaeveIcons.Pin, stringResource(R.string.drawer_pinned_mark), Modifier.size(14.dp).testTag("conversation-pin"), tint = c.ink3)
            if (item.branch) Icon(MaeveIcons.Route, stringResource(R.string.chat_branch_origin), Modifier.size(14.dp).testTag("conversation-branch"), tint = c.ink3)
            Text(item.title, Modifier.weight(1f, fill = item.site == null), style = ConversationTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.site != null) {
                Text(
                    PageAskCopy.WEB, Modifier.background(c.accentSoft, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp).testTag("conversation-web"),
                    style = ConversationTag, color = c.accent, maxLines = 1,
                )
                Box(Modifier.weight(1f))
            }
            if (item.time.isNotEmpty()) {
                Text(item.time, Modifier.padding(start = 5.dp).testTag("conversation-time"), style = ConversationTime, color = c.ink3, maxLines = 1, softWrap = false)
            }
        }
        val sub = item.subtitle.ifEmpty { item.site.orEmpty() }
        if (sub.isNotEmpty()) {
            Text(sub, Modifier.padding(top = 2.dp), style = ConversationTime, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 给对话改名:输入框里是现在的标题(整段选中,直接打字就替换);空的存不了,两头空白去掉。 */
@Composable
internal fun RenameConversationDialog(title: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember(title) { mutableStateOf(TextFieldValue(title, TextRange(0, title.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ok = value.text.isNotBlank()
    MaeveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_rename_title)) },
        text = {
            KeyboardDismissArea {
                OutlinedTextField(
                    value, { value = it }, Modifier.fillMaxWidth().focusRequester(focus).keepsKeyboard().testTag("rename-conversation-field"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onSave(value.text.trim()) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.text.trim()) }, Modifier.testTag("confirm-rename-conversation"), enabled = ok) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * 删对话前的确认:写明删的是哪个对话;有任务在跑、绑着定时任务时也写明会停掉、一起删掉。
 * 和别处删东西的确认一样:右边红字「删除」,左边「取消」。
 */
@Composable
internal fun DeleteConversationDialog(title: String, impact: DeleteImpact, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val body = listOfNotNull(
        stringResource(R.string.drawer_delete_body, title),
        if (impact.runningTask) stringResource(R.string.drawer_delete_running) else null,
        if (impact.schedules > 0) trPlural(R.plurals.drawer_delete_schedules, impact.schedules) else null,
    ).joinToString("\n")
    MaeveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_delete_title)) },
        text = { Text(body, Modifier.testTag("delete-conversation-body")) },
        confirmButton = {
            TextButton(onClick = onConfirm, Modifier.testTag("confirm-delete-conversation")) {
                Text(stringResource(R.string.confirm_delete), color = MaeveTheme.colors.bad)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal const val MENU_IN_MS = 200
internal const val MENU_OUT_MS = 140
// 侧栏宽度：原来固定 304dp，在 412dp 宽的屏上占 74%，把主界面全挡住了。
//
// 现在两段式：
//   - 窄屏（< 400dp）取 80%，保证列表里「时间 + 一句话」还放得下；
//   - 其余取 244dp，约 59%，右边一定留出主界面。
// 上限 260dp：再宽就变成"换了一页"，抽屉的意义就没了。
private val DrawerWidth = 244.dp
/** 侧栏最多占屏宽的这个比例，防止平板/大屏上一条 244dp 的缝。 */
private const val DRAWER_MAX_FRACTION = 0.62f
private val DrawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
internal val NavShape = RoundedCornerShape(12.dp)

private val ConversationTitle = TextStyle(fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
private val ConversationTime = TextStyle(fontSize = 10.5.sp, lineHeight = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontFeatureSettings = "tnum")
private val ConversationTag = TextStyle(fontSize = 10.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)

/**
 * 对话页和它的侧栏:左上角菜单从左边滑出侧栏,外面一层遮罩;点遮罩、往左滑、按返回都关上。
 * 侧栏打开时才去读对话列表。进了别的页面再回来,侧栏是关着的。[content] 拿到「打开侧栏」放到菜单按钮上。
 */
@Composable
fun ChatWithDrawer(
    vm: ChatViewModel,
    check: Prerequisites.Check,
    reduceMotion: Boolean,
    conversations: () -> List<ConversationSummary>,
    /** 存储里改过的对话(见 `ConversationStore.changes`);侧栏开着时别处存进来的对话也当场列出。 */
    conversationChanges: Flow<String> = emptyFlow(),
    /** 对话起好的标题;当前对话还没存进列表时(任务刚开始)也能叫出名字。 */
    conversationTitle: (String) -> String? = { null },
    onPlans: () -> Unit,
    onMemory: () -> Unit,
    onBrowser: () -> Unit,
    onScreens: () -> Unit = {},
    onSettings: () -> Unit,
    onAbout: () -> Unit = {},
    /** 有新版本:侧栏「关于 Maeve」带小红点。 */
    updateAvailable: Boolean = false,
    /** 从侧栏进去的页面刚退回来:当场打开侧栏,回到进去之前的样子。 */
    reopen: Boolean = false,
    onReopened: () -> Unit = {},
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    LaunchedEffect(reopen) {
        if (reopen) {
            drawer.snapTo(DrawerValue.Open)
            onReopened()
        }
    }
    val ui by vm.ui.collectAsState()
    val others by vm.otherRunning.collectAsState()
    fun close(then: () -> Unit = {}) {
        scope.launch {
            if (reduceMotion) drawer.snapTo(DrawerValue.Closed) else drawer.close()
        }
        then()
    }
    /** 去别的页面:先当场关上侧栏,回来时就是关着的。 */
    fun leave(go: () -> Unit) {
        scope.launch { drawer.snapTo(DrawerValue.Closed) }
        go()
    }
    val open = drawer.targetValue == DrawerValue.Open || drawer.isOpen
    // 对话页上返回(侧滑)先打开侧栏;侧栏开着时再返回才离开 App(退到后台,和桌面返回一样)。
    // 对话页里面板、画中画等自己的返回在内层,先于这里处理
    val activity = LocalContext.current.findActivity()
    BackHandler {
        if (open) activity?.moveTaskToBack(true)
        else scope.launch { if (reduceMotion) drawer.snapTo(DrawerValue.Open) else drawer.open() }
    }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(conversationChanges) { conversationChanges.collect { revision++ } }
    val deleted by vm.deleted.collectAsState()
    /** 长按了的那条和它在窗口里的位置;菜单开着(收起的动画放完才清掉)。 */
    var acting by remember { mutableStateOf<Pair<DrawerConversation, Rect>?>(null) }
    /** 菜单出现的进度:0 是没有,1 是完全出来;关了动画时直接到位。 */
    val lift = remember { Animatable(0f) }
    LaunchedEffect(acting) {
        if (acting != null) {
            if (reduceMotion) lift.snapTo(1f) else lift.animateTo(1f, tween(MENU_IN_MS, easing = MaeveMotion.Decelerate))
        }
    }
    /** 收起菜单:倒着放一遍出现的动画,放完再清掉;[then] 当场做(改名、删除的弹窗不用等菜单收完)。 */
    fun closeMenu(then: () -> Unit = {}) {
        then()
        scope.launch {
            if (reduceMotion) lift.snapTo(0f) else lift.animateTo(0f, tween(MENU_OUT_MS, easing = MaeveMotion.Decelerate))
            acting = null
        }
    }
    /** 正在改名的那条。 */
    var renaming by remember { mutableStateOf<DrawerConversation?>(null) }
    /** 要删的那条和删它会连带什么;确认弹窗开着。 */
    var confirming by remember { mutableStateOf<Pair<DrawerConversation, DeleteImpact>?>(null) }
    val menuOpen = acting != null
    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            // ★ 菜单开着时**不要**给整棵容器套 `graphicsLayer { renderEffect = BlurEffect }`。
            //   这层会把「抽屉 + 主界面」整棵树包进一个 renderNode，再在它内部绘制
            //   已经带 layer 的玻璃件，hwui 的 DirtyStack 只增不减，512 帧后
            //   `computeTransformImpl` 栈溢出 → RenderThread SIGSEGV。
            //   变暗由 ModalNavigationDrawer 自带的 scrim 承担；模糊一律不做。
            modifier = if (!menuOpen) Modifier else Modifier.clearAndSetSemantics {},
            drawerContent = {
                // 只在打开时读盘;每次打开、开着时存储里有对话改过都重读一次。
                // 读盘走 IO:组合期是主线程,对话多了要遍历全部对话文件,同步读会掉帧
                val all by produceState<List<ConversationSummary>>(emptyList(), open, revision) {
                    value = if (open) withContext(Dispatchers.IO) { conversations() } else emptyList()
                }
                val stored by produceState<String?>(null, open, revision, ui.conversationId) {
                    value = if (open) withContext(Dispatchers.IO) { ui.conversationId?.let(conversationTitle) } else null
                }
                val last = ui.exchanges.lastOrNull()
                val running = vm.busy()
                val current = CurrentConversation(
                    ui.conversationId, running,
                    stored ?: ChatCopy.title(ui.exchanges),
                    if (running) ChatCopy.subtitle(last, last?.let(vm::livePlace))?.first.orEmpty() else "",
                )
                val canSwitch = vm.canSwitch()
                // 删掉的对话当场不列,不等后台删完
                val sections = remember(all, current, others, canSwitch, deleted) {
                    drawerSections(all.filter { it.id !in deleted }, current, others.filter { it !in deleted }, vm::runningGoal, canSwitch, System.currentTimeMillis())
                }
                ChatDrawer(
                    check, sections, canSwitch,
                    onNew = { close { vm.newConversation() } },
                    onPlans = { leave(onPlans) },
                    onMemory = { leave(onMemory) },
                    onSettings = { leave(onSettings) },
                    onBrowser = { leave(onBrowser) },
                    onScreens = { leave(onScreens) },
                    onAbout = { leave(onAbout) },
                    aboutDot = updateAvailable,
                    onActions = { item, bounds -> if (item.id != null) acting = item to bounds },
                    onOpen = { item ->
                        close {
                            val id = item.id
                            when {
                                id == null || id == ui.conversationId -> Unit
                                item.running -> scope.launch { vm.switchTo(id) }
                                else -> scope.launch { vm.openConversation(id) }
                            }
                        }
                    },
                )
            },
            drawerState = drawer,
            // 对话页上从左往右划拉出侧栏(拖着跟手);能左右滚的内容(宽表格、代码块等)先自己滚。
            // 从左边缘往里滑是系统的返回手势,上面的返回处理同样是打开侧栏
            gesturesEnabled = true,
            scrimColor = MaeveTheme.colors.floatShadow.copy(alpha = 0.28f),
        ) {
            content { scope.launch { if (reduceMotion) drawer.snapTo(DrawerValue.Open) else drawer.open() } }
        }
        acting?.let { (item, bounds) ->
            val id = item.id ?: return@let
            ConversationMenu(
                item, bounds, progress = { lift.value },
                onPin = { closeMenu { vm.setPinned(id, !item.pinned) } },
                onRename = { closeMenu { renaming = item } },
                onDelete = { closeMenu { confirming = item to vm.deleteImpact(id) } },
                onDismiss = { closeMenu() },
            )
        }
    }
    renaming?.let { item ->
        RenameConversationDialog(
            item.title,
            onSave = { t ->
                renaming = null
                item.id?.let { vm.renameConversation(it, t) }
            },
            onDismiss = { renaming = null },
        )
    }
    confirming?.let { (item, impact) ->
        DeleteConversationDialog(
            item.title, impact,
            onConfirm = {
                confirming = null
                item.id?.let(vm::deleteConversation)
            },
            onDismiss = { confirming = null },
        )
    }
}

package com.daiyimo.maeve.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.ui.components.glassFill
import com.daiyimo.maeve.ui.theme.GlassTier
import com.daiyimo.maeve.ui.theme.LocalGlassBackdrop
import com.daiyimo.maeve.ui.theme.LocalMaeveSurfaces
import com.daiyimo.maeve.ui.theme.MaeveMark
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveMotion
import com.daiyimo.maeve.ui.theme.MaeveSize
import com.daiyimo.maeve.ui.theme.MaeveSpace
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType
import com.daiyimo.maeve.ui.theme.MaeveIcons

// ---------- 等宽微标签 ----------

/**
 * 等宽大写微标签。整套界面的「读数」口音:字距宽、字号小、全大写。
 *
 * 中文转大写无变化,但宽字距会让它读起来像仪表面板上的印刷字,
 * 而不是一句正文——这正是它存在的意义。
 */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaeveTheme.colors.ink3,
    style: TextStyle = MaeveType.Eyebrow,
) {
    Text(text, modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 等宽读数:耗时、步数、token。数字 tabular,跳动时不左右抖。 */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaeveTheme.colors.ink2,
    style: TextStyle = MaeveType.Mono,
) {
    Text(text, modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// ---------- 开关 ----------

/**
 * 开关:44×26,打开时强调色。动画 200ms 减速,系统关了动画时直接到位。
 * [onCheckedChange] 为空时只显示(整行可点时用)。
 */
@Composable
fun MaeveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = MaeveTheme.colors
    val reduce = MaeveTheme.reduceMotion
    val position by animateFloatAsState(if (checked) 1f else 0f, MaeveMotion.switch(reduce), label = "switch")
    val track by animateColorAsState(if (checked) c.accent else c.greySoft, MaeveMotion.switch(reduce), label = "switch-track")
    val thumb = c.thumb
    val ring = c.floatShadow.copy(alpha = 0.12f)
    Box(
        modifier
            .then(
                if (onCheckedChange != null) Modifier.minimumInteractiveComponentSize()
                    .toggleable(checked, enabled = enabled, role = Role.Switch, interactionSource = null, indication = null, onValueChange = onCheckedChange)
                else Modifier,
            )
            .size(44.dp, 26.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .drawBehind {
                drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                val r = 11.dp.toPx()
                val inset = 2.dp.toPx()
                val cx = inset + r + (size.width - 2 * (inset + r)) * position
                val center = Offset(cx, size.height / 2)
                drawCircle(ring, r + 0.6.dp.toPx(), center.copy(y = center.y + 0.5.dp.toPx()))
                drawCircle(thumb, r, center)
            },
    )
}

// ---------- 胶囊 ----------

enum class PillTone { Ok, Warn, Neutral, Accent }

/**
 * 状态胶囊:可用、需要你、已停用;强调色的标明「在用」。
 * 内容是**读数**,所以用等宽 + 宽字距;颜色只表达状态,不表达品牌。
 */
@Composable
fun StatusPill(text: String, tone: PillTone = PillTone.Neutral, modifier: Modifier = Modifier) {
    val c = MaeveTheme.colors
    val (fg, bg) = when (tone) {
        PillTone.Ok -> c.ok to c.okSoft
        PillTone.Warn -> c.warn to c.warnSoft
        PillTone.Accent -> c.accent to c.accentSoft
        PillTone.Neutral -> c.ink2 to c.neutralSoft
    }
    Text(
        text,
        modifier.clip(PillShape).background(bg).padding(horizontal = 7.dp, vertical = 2.dp),
        style = MaeveType.Caption.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium), color = fg, maxLines = 1,
    )
}

private val PillShape = RoundedCornerShape(6.dp)

// ---------- 按钮 ----------

/** 主按钮(强调色实底)、浅色次按钮(纸底描边)、浅填充按钮、文字按钮。 */
enum class ButtonKind { Primary, Secondary, Tonal, Text }

/** 大:页面底部 52;中:卡片里并排 42;小:行内 32 的胶囊。 */
enum class ButtonSize(val height: Dp, val shape: Shape, val text: TextStyle, val padding: Dp) {
    Large(MaeveSize.Button, MaeveShapes.Button, TextStyle(fontSize = 15.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium), 20.dp),
    Medium(42.dp, RoundedCornerShape(11.dp), TextStyle(fontSize = 14.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium), 14.dp),
    Small(32.dp, RoundedCornerShape(9.dp), TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium), 13.dp),
}

/** 按钮。宽度由 [modifier] 定(铺满、按权重分或包住文字)。所有按钮共用这一处按压反馈。 */
@Composable
fun MaeveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
) {
    val c = MaeveTheme.colors
    val haptics = MaeveTheme.haptics
    val shape = size.shape
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> c.accent to c.onAccent
        ButtonKind.Secondary -> Color.Transparent to c.ink
        ButtonKind.Tonal -> Color.Transparent to c.ink
        ButtonKind.Text -> Color.Transparent to c.accent
    }
    // 按下 0.97 倍缩放 + 一次 Tick,替代标准 ripple;系统关动画时直接到位
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState().value
    val scale = animateFloatAsState(
        targetValue = if (pressed && enabled) MaeveMotion.PressScale else 1f,
        animationSpec = MaeveMotion.spec(MaeveMotion.PressMs, MaeveTheme.reduceMotion),
    ).value
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                when (kind) {
                    ButtonKind.Primary -> Modifier.background(bg, shape)
                    ButtonKind.Secondary -> Modifier.glassFill(c, shape, GlassTier.Card)
                    ButtonKind.Tonal -> Modifier.background(c.fill, shape)
                    ButtonKind.Text -> Modifier
                },
            )
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.tick()
                onClick()
            }
            .alpha(if (enabled) 1f else 0.45f)
            .height(size.height)
            .padding(horizontal = size.padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = size.text, color = fg, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- 分段控件 ----------

/** 分段控件:浅填充底,选中的一段是纸面浮起。[tags] 是各段的测试标签。 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tags: List<String>? = null,
) {
    val c = MaeveTheme.colors
    val haptics = MaeveTheme.haptics
    Row(modifier.fillMaxWidth().glassFill(c, SegmentOuter, GlassTier.Pill).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(32.dp)
                    .then(if (on) Modifier.glassFill(c, SegmentInner, GlassTier.Chrome) else Modifier)
                    .clip(SegmentInner)
                    .selectable(on, role = Role.Tab) {
                        if (i != selected) haptics.tick()
                        onSelect(i)
                    }
                    .then(if (tags != null) Modifier.testTag(tags[i]) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = SegmentText, color = if (on) c.ink else c.ink2, maxLines = 1)
            }
        }
    }
}

private val SegmentOuter = RoundedCornerShape(11.dp)
private val SegmentInner = RoundedCornerShape(8.dp)
private val SegmentText = TextStyle(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

/** Mirrors horizontal navigation symbols in RTL while leaving non-directional artwork unchanged. */
@Composable
fun DirectionalIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mirror = rtl && (icon == MaeveIcons.Back || icon == MaeveIcons.Forward || icon == MaeveIcons.Chevron)
    Icon(icon, contentDescription, modifier.graphicsLayer { if (mirror) scaleX = -1f }, tint = tint)
}

// ---------- 顶栏 ----------

/**
 * 顶栏上的圆形图标按钮,40dp。[glass] 为真时是浮在内容上的玻璃圆。
 * [badge] 为真时右上角一个强调色小圆点(有别的事在进行);[enabled] 为假时变淡、不可点。
 */
@Composable
fun BarIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    glass: Boolean = false,
    enabled: Boolean = true,
    badge: Boolean = false,
) {
    val c = MaeveTheme.colors
    Box(
        Modifier.size(MaeveSize.IconButton)
            .then(if (glass) Modifier.cardSurface(c, CircleShape, tier = GlassTier.Pill) else Modifier)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DirectionalIcon(icon, label, Modifier.size(MaeveSize.Icon).alpha(if (enabled) 1f else 0.35f), tint = c.ink)
        if (badge) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 7.dp).size(8.dp)
                    .background(c.bg, CircleShape).padding(1.dp).background(c.accent, CircleShape),
            )
        }
    }
}

/**
 * 顶栏:56 高,左右各一个图标按钮位,标题居中。
 *
 * 三层结构,从上到下:**等宽眉标 → 标题 → 副标题**。
 * 眉标是这套界面最有辨识度的一处——标题上方那行小字 `LOOP 03` / `MODELS` / `DIAGNOSTIC`,
 * 把「这是一个页面」变成「这是一块仪表」。没有眉标时退化为普通的居中标题。
 *
 * 默认是纸(不透明底,内容从底下滚过时不透字);[glass] 为真时才取样背景模糊,
 * 只有浏览器那种整页被内容盖住的场景才需要。
 */
@Composable
fun TopBar(
    title: String,
    left: ImageVector? = null,
    onLeft: () -> Unit = {},
    leftLabel: String = "",
    /** 左边按钮上的小圆点,见 [BarIconButton]。 */
    leftBadge: Boolean = false,
    right: (@Composable RowScope.() -> Unit)? = null,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    background: Color = MaeveTheme.colors.bg,
    /** 标题左边带品牌徽标(对话页用):迷宫小标志,一眼认出是谁在说话。 */
    brand: Boolean = false,
    /** 标题上方那行等宽小字。 */
    eyebrow: String? = null,
    /** 眉标左侧那个小点的颜色。默认强调色。 */
    eyebrowTone: Color = Color.Unspecified,
    /** 需要玻璃顶栏时打开(默认纸)。 */
    glass: Boolean = false,
) {
    val c = MaeveTheme.colors
    val backdrop = LocalGlassBackdrop.current
    val chrome = LocalMaeveSurfaces.current.chrome
    val sampling = glass && backdrop != null && chrome.blurs
    // 对话页是「标志 / 标题 / 一行状态」三层，所以顶栏要高出一档；
    // 其他页只有标题（或标题 + 一行），用标准高度。
    // 这一档是给**字标**留的，不是为了堆更多碎字。
    val tall = brand
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
        if (sampling) {
            // 取样垫排第一个子位:模糊的背后画面,填充和内容依次画上去
            GlassSampler(chrome, backdrop, Modifier.matchParentSize())
        }
        // 这里必须是 fillMaxWidth 而不是 fillMaxSize:外层 Box 是 wrap-content,
        // 内层一旦 fillMaxSize 就会吃掉父级剩余的全部高度——顶栏变成整屏高,
        // 那层不透明底色把后面的内容全盖住,同时把内容顶到屏幕外(全站每一页都会中招)。
        Box(
            Modifier.fillMaxWidth()
                .then(if (sampling) Modifier.background(chrome.fill) else Modifier.background(background)),
        ) {
            Row(
                Modifier.fillMaxWidth().height(if (tall) MaeveSize.TopBar + 18.dp else MaeveSize.TopBar).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (left != null) BarIconButton(left, leftLabel, onLeft, badge = leftBadge) else Spacer(Modifier.size(MaeveSize.IconButton))
                // 标题相对**整条顶栏**居中:左边和右边各占一个固定宽的格子,中间那块永远居中。
                // (之前右边按钮直接挤进 Row,标题就被推偏了。)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 眉标：有副标题时让位（副标题本身就说明了状态），
                        // 没有副标题时仍在标题上方——子页的分类标签（MODELS / SETUP）不该掉到标题下面。
                        if (eyebrow != null && subtitle.isNullOrEmpty()) {
                            Row(
                                Modifier.padding(bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    Modifier.size(4.dp).background(
                                        if (eyebrowTone == Color.Unspecified) c.accent else eyebrowTone,
                                        CircleShape,
                                    ),
                                )
                                Text(
                                    eyebrow,
                                    style = MaeveType.Caption.copy(fontSize = 11.5.sp),
                                    color = c.ink3, maxLines = 1,
                                )
                            }
                        }
                        // 标志独立于标题存在：它是字标，不是行首图标。
                        // 放在标题上方单独一行，标题就回到真正的居中位置。
                        // ★ 必须 wrapContentWidth：MaeveMark 的 Canvas 在 Row/Column 里
                        //    默认吃满可用宽度，会把整组内容撑宽、推到右边。
                        if (brand) {
                            MaeveMark(
                                22.dp, Modifier.wrapContentWidth().padding(bottom = 3.dp),
                                coronaColor = c.accent, darkColor = Color.Transparent,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title, style = MaeveType.Title, color = c.ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 有副标题时它独占标题下面这一行（状态比分类更重要）。
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                subtitle, Modifier.padding(top = 1.dp),
                                style = MaeveType.Hint,
                                color = if (subtitleColor == Color.Unspecified) c.ink3 else subtitleColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
                // 右格宽度固定,和有按钮时一致:没有按钮也留同样宽,标题才能真居中
                Box(Modifier.width(MaeveSize.IconButton), contentAlignment = Alignment.CenterEnd) {
                    if (right != null) Row(verticalAlignment = Alignment.CenterVertically, content = right)
                }
            }
        }
    }
}

// ---------- 选择胶囊 ----------

/** 可选的胶囊(服务商、接口类型):34 高;选中时墨色实底、反色字。[small] 是行内的小号。 */
@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = MaeveTheme.colors
    val shape = if (small) SmallChipShape else MaeveShapes.Pill
    Box(
        modifier
            .then(
                when {
                    selected -> Modifier.background(c.ink, shape)
                    else -> Modifier.glassFill(c, shape, GlassTier.Card)
                },
            )
            .clip(shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .height(if (small) 28.dp else 34.dp)
            .padding(horizontal = if (small) 10.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = if (small) SmallChipText else ChipText, maxLines = 1, softWrap = false,
            color = if (selected) c.bg else c.ink,
        )
    }
}

private val SmallChipShape = RoundedCornerShape(7.dp)
private val ChipText = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
private val SmallChipText = TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)

// ---------- 小标签 ----------

enum class TagTone { Neutral, Accent }

/** 能力小标签(工具调用、看图、64K):等宽小字,浅填充底。 */
@Composable
fun CapabilityTag(text: String, tone: TagTone = TagTone.Neutral) {
    val c = MaeveTheme.colors
    val fg = if (tone == TagTone.Accent) c.accent else c.ink2
    Text(
        text,
        Modifier.clip(TagShape).background(c.fill).padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaeveType.Caption.copy(fontSize = 11.sp), color = fg, maxLines = 1,
    )
}

private val TagShape = RoundedCornerShape(5.dp)

// ---------- 分页点 ----------

/** 上手流程底部的分页点:当前一步是 18 宽的强调色短条,其余是 6 的灰点。 */
@Composable
fun ProgressDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val c = MaeveTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        repeat(count) { i ->
            Box(Modifier.size(if (i == current) 18.dp else 6.dp, 6.dp).background(if (i == current) c.accent else c.hair, CircleShape))
        }
    }
}

/** 一条横向细线:分组之间的分隔,也是台词轨的横版。 */
@Composable
fun RuleLine(modifier: Modifier = Modifier, color: Color = MaeveTheme.colors.hair) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(color))
}

/** 竖排的一个空格位,给 [Spacer] 互补用。 */
@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

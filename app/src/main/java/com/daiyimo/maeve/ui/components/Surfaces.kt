package com.daiyimo.maeve.ui.components

import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.daiyimo.maeve.ui.theme.GlassStyle
import com.daiyimo.maeve.ui.theme.GlassTier
import com.daiyimo.maeve.ui.theme.LocalGlassBackdrop
import com.daiyimo.maeve.ui.theme.LocalMaeveSurfaces
import com.daiyimo.maeve.ui.theme.MaeveBackdrop
import com.daiyimo.maeve.ui.theme.MaeveColors
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveSize
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType
import kotlin.math.roundToInt

/**
 * 页面底:中性渐变 + 三团极光(见 [MaeveBackdrop])。所有页面共用同一张底,切页面时不动。
 */
@Composable
fun PageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    MaeveBackdrop(modifier, content = content)
}

/**
 * **所有的底**。按 [tier] 取材质;Chrome / Sheet 是真的毛玻璃,Card / Pill 只做三层光学特征。
 *
 * ## 一片玻璃是五层叠出来的
 *
 * ```
 *  ⑤ 外投影      ← 只画在形状外部,内部不被弄脏(浮起来才有)
 *  ④ 内描边       ← 0.5dp 一圈,顶亮底暗(玻璃的厚度)
 *  ③ 内高光       ← 顶边一道上白下透明的渐变(光打在玻璃边上)
 *  ② 半透明填充    ← 决定「实」的程度(0.60 → 0.80)
 *  ① 背景模糊      ← 真实采样背后的内容,σ 按档位,带饱和度增益
 * ```
 *
 * ① 是关键。如果只做「半透明白 + 一道描边」,那是半透明塑料片,不是玻璃;
 * 玻璃的定义是**背后的东西被模糊了才透过来**。Chrome / Sheet 两档真实取样,
 * Card / Pill 不长在内容里所以不取样(见
 * [com.daiyimo.maeve.ui.theme.buildSurfaces] 的说明)。
 *
 * 取样要扣掉元素自己的偏移:玻璃看到的是「背后那块」,不是「屏幕左上角那块」,
 * 否则卡片一挪位置,透出来的就是错位的图案。
 *
 * 不支持模糊(API < 31 或设置里关了)时退回 [GlassStyle.fallbackFill]——
 * 接近不透明的表面,层级改用投影承担,不会变成一片透明。
 */
@Composable
fun Modifier.cardSurface(
    colors: MaeveColors,
    shape: Shape = MaeveShapes.Card,
    tier: GlassTier = GlassTier.Card,
): Modifier {
    val style: GlassStyle = LocalMaeveSurfaces.current[tier]
    val backdrop = LocalGlassBackdrop.current
    val bd = backdrop
    // 只有「这一档要模糊 + 有 backdrop + 和内容在同一个窗口」才取样。
    // 弹层、对话框是另一个窗口,坐标根都不一样,取样只会取到错位的东西。
    val sampling = style.blurs && bd != null && bd.samplesHere()

    // 外投影:每一档都有,只是力度不同。平放的卡给一道极浅的(它没浮起来,
    // 只是垫在页面上一层);顶栏/弹层给重的。投影是「玻璃浮在内容之上」这句话的一半。
    val shadowed = then(
        if (style.elevation > 0.dp && style.outerShadow.alpha > 0f) {
            shadow(
                style.elevation,
                shape,
                clip = false,
                ambientColor = style.outerShadow.copy(alpha = style.outerShadow.alpha * 0.62f),
                spotColor = style.outerShadow,
            )
        } else {
            this
        },
    ).clip(shape)

    val hairline = if (style.hairlineWidth > 0.dp && style.hairline.alpha > 0f) {
        Modifier.border(style.hairlineWidth, style.hairline, shape)
    } else {
        Modifier
    }

    // 不取样的路径:半透明填充 + 顶边内高光 + 上亮下暗内描边。
    // 三层叠起来在浅色纸面上已经读作「一块玻璃」,而且零渲染风险。
    if (!sampling || bd == null) {
        return shadowed
            .background(style.fallbackFill, shape)
            .drawBehind { glassRim(style) }
            .drawBehind { glassBevel(style, shape, colors) }
            .then(hairline)
    }

    // 取样的路径:内容节点每录一帧,这里就把那一帧垫在身下,只糊这一帧。
    // 垫进来的层在 .clip(shape) 之后、.background() 之前,所以圆角、填充、
    // 高光、描边的层次都是对的。
    val slot = rememberGlassSlot(bd, style)
    return shadowed
        .onGloballyPositioned {
            slot.origin = it.positionInRoot()
            slot.size = it.size.toSize()
        }
        .drawBehind { drawGlassSample(slot, style) }
        // ② 半透明填充
        .background(style.fill, shape)
        // ③ 内高光(顶边)
        .drawBehind { glassRim(style) }
        // ④ 内描边:上亮下暗,给玻璃一圈厚度
        .drawBehind { glassBevel(style, shape, colors) }
        .then(hairline)
}

/**
 * 顶边高光:光从上方打在玻璃边上,往里渐隐。
 *
 * 高度按元素尺寸的 [GlassStyle.rimFraction] 取上限,再和 14dp 取小——
 * 一枚 28dp 的药丸不该整个被高光填满,一张 200dp 的卡也不该只有一条细线。
 */
private fun DrawScope.glassRim(style: GlassStyle) {
    if (style.rim.alpha <= 0f) return
    val rimHeight = (size.height * style.rimFraction).coerceIn(2f, 26.dp.toPx())
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(style.rim, Color.Transparent),
            startY = 0f,
            endY = rimHeight,
        ),
        size = Size(size.width, rimHeight),
    )
}

/**
 * 内描边:一圈「上亮下暗」的极细渐变边,模拟玻璃的侧面。
 *
 * 做法:先整圈画一条偏暗的边(玻璃侧面),再用竖向渐变画一遍
 * (上半段亮、下半段透明,受光面)。
 * 这样即使填充很透,元素也能靠这一圈立住轮廓,不需要粗描边。
 */
private fun DrawScope.glassBevel(style: GlassStyle, shape: Shape, colors: MaeveColors) {
    if (style.hairlineWidth <= 0.dp) return
    val path = shape.toFlatPath(size, this)
    val w = style.hairlineWidth.toPx()
    // 整圈:偏暗,负责轮廓
    drawPath(path, colors.ink.copy(alpha = if (colors.isDark) 0.22f else 0.155f), style = Stroke(w))
    // 上半段:亮,负责受光
    drawPath(
        path,
        brush = Brush.verticalGradient(
            colors = listOf(
                style.rim.copy(alpha = (style.rim.alpha * 0.85f).coerceAtMost(0.70f)),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.50f,
        ),
        style = Stroke(w),
    )
}

/**
 * 把 [Shape] 摊成一条 [Path] 用于描边。
 *
 * 用 [Shape.createOutline] 拿到系统按密度算好的轮廓,再按类型取几何:
 * 圆角矩形有 `Rect`,圆/胶囊走 `addOval`。不自己猜圆角半径——
 * 猜错了边会和轮廓错开,反而露馅。
 */
private fun Shape.toFlatPath(size: Size, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Path {
    val outline = createOutline(size, androidx.compose.ui.unit.LayoutDirection.Ltr, density)
    return androidx.compose.ui.graphics.Path().apply {
        when (outline) {
            is androidx.compose.ui.graphics.Outline.Rectangle -> addRect(outline.rect)
            is androidx.compose.ui.graphics.Outline.Rounded -> addRoundRect(outline.roundRect)
            is androidx.compose.ui.graphics.Outline.Generic -> addPath(outline.path)
        }
    }
}

/**
 * **轻量玻璃**:给那些原本 `background(某个颜色)` 直接铺底的大块元素用。
 *
 * 和 [cardSurface] 的区别:这个不取样 backdrop、不做高斯模糊——它只做三件事:
 * 一层半透明填充、一圈上亮下暗的内描边、一道顶边高光。
 *
 * 为什么需要它:全站有几十处 `Modifier.background(c.surface)`,它们面积不小
 * (气泡、面板、抽屉选中项、按钮),但因为没走 [cardSurface] 就完全没有玻璃的
 * 光学特征——在极光底上看起来就是一块平的浅色。加这个修饰符只改一行,不重排布局。
 *
 * 真正的模糊仍然交给 [cardSurface]:模糊是贵的,应该留给浮在内容之上的容器;
 * 小面板靠"边 + 高光"就够读作玻璃了。
 */
@Composable
fun Modifier.glassFill(
    colors: MaeveColors,
    shape: Shape,
    tier: GlassTier = GlassTier.Card,
): Modifier {
    val style: GlassStyle = LocalMaeveSurfaces.current[tier]
    return this
        .clip(shape)
        .background(style.fill, shape)
        .drawBehind { glassRim(style) }
        .drawBehind { glassBevel(style, shape, colors) }
}

/** 悬浮元素的阴影(输入栏、浮在内容上的按钮)。 */
fun Modifier.floatShadow(colors: MaeveColors, shape: Shape): Modifier =
    shadow(14.dp, shape, clip = false, ambientColor = colors.floatShadow.copy(alpha = 0.3f), spotColor = colors.floatShadow.copy(alpha = 0.35f))

/** 一张卡片,内容裁在圆角里(行的按压效果不出角)。 */
@Composable
fun MaeveCard(modifier: Modifier = Modifier, shape: Shape = MaeveShapes.Card, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().cardSurface(MaeveTheme.colors, shape).clip(shape), content = content)
}

/**
 * 分组卡片:一组 [ListRow]。下面自带 12dp 间距,和下一组的 [GroupLabel](上 8)合起来是分组之间的 20。
 * 行与行的分隔线由行自己画,从文字处开始。
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    MaeveCard(modifier.padding(bottom = 12.dp), content = content)
}

/** 状态卡:左边圆形图标,右边标题和说明。用于页面顶上的总状态。 */
@Composable
fun StateCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tone: TileTone = TileTone.Neutral,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = MaeveTheme.colors
    MaeveCard(modifier.padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val (bg, fg) = tone.colors(c)
            Box(Modifier.size(36.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = fg)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaeveType.BodyStrong, color = c.ink)
                if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaeveType.Caption, color = c.ink3)
            }
            trailing()
        }
    }
}

// ---------- 悬浮玻璃 ----------

/**
 * 真·背景模糊的总开关。**API 31+ 才有 [android.graphics.RenderEffect]。**
 *
 * 低版本玻璃走 [Modifier.cardSurface] 的不取样分支:半透明填充 + 顶边内高光 +
 * 上亮下暗内描边,不会变成一片透明。
 *
 * ★ 这一版为什么敢开:取样改成了**单向的层传递**,全程没有回读——
 *
 * ```
 * 内容 ──record──▶ 槽位层(renderEffect=Blur) ──drawLayer──▶ 玻璃
 * ```
 *
 * 玻璃看到的层由**内容节点**拥有并逐帧重录;玻璃只是 `drawLayer` 把它垫在身下。
 * 数据永远只有一个方向(内容 → 玻璃),不可能出现「玻璃 → 内容层 → 玻璃」的环。
 *
 * 老方案正是坏在这里:玻璃件直接 `drawLayer(live)` 平移取样录制层,于是「玻璃画 live」
 * 和「live 属于玻璃」互为引用,hwui 的 `computeTransformImpl` 顺着这条引用无限自递归,
 * RenderThread 栈溢出(SIGSEGV),点设置页、关于页必崩——从 v0.4 带到交付。
 *
 * 打开后务必跑一遍:开侧栏 → 设置 → 关于 → 返回,并连续滚动 10 秒以上,
 * `adb logcat | grep -E "Fatal signal|RenderThread"` 应为 0。
 */
val GlassBlurSupported: Boolean = Build.VERSION.SDK_INT >= 31

/**
 * 录制时玻璃四周多录多少像素(σ 的倍数)。
 *
 * 高斯模糊需要玻璃**外面**的真实内容才能糊得自然;只录玻璃自己那一块的话,
 * 边缘会因为 `TileMode.Clamp` 把边像素往外拉,糊出一个可见的"硬框"。
 */
private const val BLUR_BLEED_SIGMA = 2f

/** 溢出的录制区最大多少 dp。防呆:一个尺寸写错的玻璃件不该吃掉几十兆内存。 */
private val MAX_BLEED_DP = 64.dp

/** 单边最多录多少像素。防呆同上。 */
private const val MAX_CAPTURE_PX = 4096

/**
 * 一片玻璃的取样槽位:内容层里「这块玻璃身后那块区域」的录制目标。
 *
 * 玻璃件创建它、注册进 [GlassBackdrop];内容节点([Modifier.glassSource])
 * 每帧把活跃槽位身后的内容录进 [layer];玻璃件再把这一层垫在自己身下。
 * 层归内容节点所有,玻璃只读不写,所以不可能成环。
 */
@Stable
internal class GlassSlot {
    /** 槽位左上角在窗口中的位置。 */
    var origin by mutableStateOf(Offset.Zero)
    /** 槽位尺寸(px)。 */
    var size by mutableStateOf(Size.Zero)
    /** 录制代:每录一帧 +1。玻璃件读它驱动自己重画(因此有一帧滞后)。 */
    var frame by mutableIntStateOf(0)

    /** 录好的那一层:内容 + BlurEffect + 饱和度,由内容节点写入。 */
    internal var layer: GraphicsLayer? = null
    /** 层比玻璃大出来的那一圈(px)。玻璃按 `-pad` 平移到正确位置。 */
    internal var pad = 0f
    /** σ(px)。0 表示这一档不糊。 */
    internal var sigma = 0f
    /** 饱和度增益;iOS 式「透色」,背后的颜色透过玻璃更艳。 */
    internal var filter: ColorFilter? = null
}

/**
 * 页面级玻璃运行时,由 [MaeveBackdrop] 创建、经 LocalGlassBackdrop 提供。
 *
 * 它是一张**槽位注册表**:玻璃件进来登记一个 [GlassSlot],内容节点按登记表
 * 逐帧录制。见 [GlassBlurSupported] 里为什么这样不会崩。
 */
@Stable
class GlassBackdrop internal constructor(
    /** 创建它的那个 View。玻璃件只有和它同窗口才取样。 */
    internal val view: View,
) {
    /** 内容层左上角在窗口中的位置(由 [glassSource] 的内容节点上报)。 */
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
    /** 登记在册的槽位。增删会被内容节点的绘制观察到,触发重录。 */
    internal val slots = mutableStateListOf<GlassSlot>()
    /**
     * 挂了 [glassSource] 的内容节点。同一时刻只有**第一个**负责录制——
     * 一个页面理论上只该有一个 glassSource;真有嵌套的(比如抽屉里又有一层
     * 可滚动内容),让最外层那个录,否则同一块内容会被录两遍、画两遍。
     */
    internal val sources = mutableStateListOf<Any>()

    internal fun add(slot: GlassSlot) {
        slots.add(slot)
    }

    internal fun remove(slot: GlassSlot) {
        slots.remove(slot)
    }
}

/** 不支持模糊时为 null,玻璃件就用不透明底。 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop? {
    if (!GlassBlurSupported) return null
    val view = LocalView.current
    return remember(view) { GlassBackdrop(view) }
}

/** 玻璃件和内容在同一个窗口里才取样。弹层、对话框是另一个窗口,坐标根都不一样。 */
@Composable
private fun GlassBackdrop.samplesHere(): Boolean = LocalView.current === view

/**
 * 玻璃件的取样运行时:一个槽位。
 *
 * σ 和饱和度在这里按密度折算好存进槽位,内容节点录制时直接取用。
 * 离开组合时注销,槽位不会留在注册表里白录。
 */
@Composable
private fun rememberGlassSlot(backdrop: GlassBackdrop, style: GlassStyle): GlassSlot {
    val slot = remember { GlassSlot() }
    val density = LocalDensity.current
    remember(style, density) {
        // blurSigma 记的是 dp,这里折成 px 存进槽位
        slot.sigma = style.blurSigma * density.density
        slot.filter = if (style.saturation == 1f) null else saturationFilter(style.saturation)
    }
    DisposableEffect(backdrop, slot) {
        backdrop.add(slot)
        onDispose { backdrop.remove(slot) }
    }
    return slot
}

/**
 * 把这块内容记成玻璃的实时背景。玻璃本身不能在这块内容里面。
 *
 * ★ 只 **record**,不在这里把层画回屏幕。
 *
 * 每帧先正常画内容,再把同一份内容按槽位录进各自的层——层比槽位大一圈
 * (见 [BLUR_BLEED_SIGMA]),内容相应平移,这样玻璃边缘有真实内容可糊。
 * 层由 `drawWithCache` 的 [androidx.compose.ui.draw.CacheDrawScope.obtainGraphicsLayer]
 * 申请,随缓存失效自动回收,不用自己管寿命。
 *
 * 录制只在内容真的需要重画时发生——静态页面 Compose 根本不调这段,零开销。
 */
fun Modifier.glassSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null) this
    else composed(
        inspectorInfo = { name = "glassSource" },
        factory = {
            // 每个内容节点一个身份牌,用来在 backdrop.sources 里认「谁是第一个」
            val token = remember { Any() }
            DisposableEffect(backdrop, token) {
                backdrop.sources.add(token)
                onDispose { backdrop.sources.remove(token) }
            }
            onGloballyPositioned { backdrop.sourceOrigin = it.positionInRoot() }
                .drawWithCache {
                    // 读槽位数量:有玻璃件挂上来/撤下去时重建缓存,顺手重申请层。
                    val count = backdrop.slots.size
                    // 同一时刻只让第一个内容节点录,避免嵌套 glassSource 录两遍。
                    val designated = backdrop.sources.firstOrNull() === token
                    val layers = List(count) { obtainGraphicsLayer() }
                    val density = this
                    onDrawWithContent {
                        // 先把正常内容画到屏幕上(主界面看得见的唯一来源),
                        // 再把同一份内容按槽位录进层。顺序不能反。
                        drawContent()
                        if (count == 0 || !designated) return@onDrawWithContent
                        val src = backdrop.sourceOrigin
                        for (i in 0 until count) {
                            val slot = backdrop.slots.getOrNull(i) ?: continue
                            val layer = layers.getOrNull(i) ?: continue
                            val gw = slot.size.width
                            val gh = slot.size.height
                            if (gw < 1f || gh < 1f) continue
                            val sigma = slot.sigma
                            val pad = if (sigma > 0f) {
                                (sigma * BLUR_BLEED_SIGMA).coerceAtMost(with(density) { MAX_BLEED_DP.toPx() })
                            } else {
                                0f
                            }
                            // 玻璃看到的是「背后那块」:把内容平移过来,让槽位那一块落在层里
                            val dx = slot.origin.x - src.x
                            val dy = slot.origin.y - src.y
                            val lw = (gw + pad * 2f).roundToInt().coerceIn(1, MAX_CAPTURE_PX)
                            val lh = (gh + pad * 2f).roundToInt().coerceIn(1, MAX_CAPTURE_PX)
                            layer.clip = true
                            layer.renderEffect =
                                if (sigma > 0f) BlurEffect(sigma, sigma, TileMode.Clamp) else null
                            layer.colorFilter = slot.filter
                            try {
                                // ★ record 的 block 里 this 是**这一层自己的** DrawScope,
                                //   canvas 是层的录制画布。把外层 ContentDrawScope 的
                                //   canvas 临时换成它,drawContent() 就录进离屏层;
                                //   录完换回来,屏幕上照常。
                                //   这是 1.7.6 唯一的公开路径:CacheDrawScope.record
                                //   和 DrawScope.record 都是 internal。
                                layer.record(density, layoutDirection, IntSize(lw, lh)) {
                                    val outer = this@onDrawWithContent
                                    val ctx = outer.drawContext
                                    val savedCanvas = ctx.canvas
                                    val savedLayer = ctx.graphicsLayer
                                    ctx.canvas = drawContext.canvas
                                    ctx.graphicsLayer = layer
                                    drawContext.canvas.save()
                                    drawContext.canvas.translate(-(dx - pad), -(dy - pad))
                                    outer.drawContent()
                                    drawContext.canvas.restore()
                                    ctx.canvas = savedCanvas
                                    ctx.graphicsLayer = savedLayer
                                }
                                slot.layer = layer
                                slot.pad = pad
                                slot.frame++
                            } catch (t: Throwable) {
                                // 某一层录失败不该带走整个页面:这一帧不垫,玻璃退回不透明底
                                slot.layer = null
                            }
                        }
                    }
                }
        },
    )

/**
 * 把槽位录下来的那一帧垫在玻璃身下。
 *
 * 层是内容节点录的,这里只负责**摆到正确的位置**:层的左上角对应玻璃左上角
 * 再往外扩 [GlassSlot.pad],所以玻璃本地坐标系里层画在 `(-pad, -pad)`。
 * 圆角由调用方的 `.clip(shape)` 负责——这个 drawBehind 排在它后面。
 *
 * 还没录到东西时(第一帧,或者这一页根本没有 [glassSource])先按不透明底画一笔,
 * 别让背后的内容从半透明填充里直接透出来。
 */
private fun DrawScope.drawGlassSample(slot: GlassSlot, style: GlassStyle) {
    // 读帧号:内容每录一帧,这块玻璃就跟着重画一次(因此有一帧滞后,模糊看不出来)
    if (slot.frame == 0) {
        drawRect(style.fallbackFill)
        return
    }
    val layer = slot.layer
    // 层可能刚被 drawWithCache 的缓存失效回收掉,那时这一帧不垫
    if (layer == null || layer.isReleased) {
        drawRect(style.fallbackFill)
        return
    }
    val pad = slot.pad
    if (pad > 0f) {
        translate(-pad, -pad) { drawLayer(layer) }
    } else {
        drawLayer(layer)
    }
}

/**
 * 玻璃取样垫:把自己身后那块画面画进来。必须排在玻璃容器的**第一个子位**,
 * 填充和内容依次画在它上面。
 */
@Composable
fun GlassSampler(style: GlassStyle, backdrop: GlassBackdrop, modifier: Modifier = Modifier) {
    val slot = rememberGlassSlot(backdrop, style)
    Box(
        modifier
            .onGloballyPositioned {
                slot.origin = it.positionInRoot()
                slot.size = it.size.toSize()
            }
            .drawBehind { drawGlassSample(slot, style) },
    )
}

/**
 * 玻璃输入栏的外壳:chrome 玻璃层级(半透明底 + 背景模糊 + 悬浮阴影 + 内描边 + 顶边高光),
 * 高至少 52。[backdrop] 为 null(不支持模糊、或和内容不在同一个窗口)时用不透明底。
 */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    shape: Shape = MaeveShapes.Pill,
    content: @Composable RowScope.() -> Unit,
) {
    val c = MaeveTheme.colors
    val style = LocalMaeveSurfaces.current.chrome
    val sampling = backdrop != null && style.blurs && backdrop.samplesHere()
    Box(
        modifier.floatShadow(c, shape).clip(shape),
    ) {
        if (sampling) {
            GlassSampler(style, backdrop, Modifier.matchParentSize())
        }
        Row(
            Modifier.fillMaxWidth()
                .background(if (sampling) style.fill else style.fallbackFill)
                .drawBehind { glassRim(style) }
                .drawBehind { glassBevel(style, shape, c) }
                .heightIn(min = MaeveSize.Composer).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/**
 * 饱和度增益的滤镜:背后的颜色透过玻璃更艳(iOS 的「透色」)。
 *
 * 高斯模糊和饱和度都是线性变换,两者可交换,所以糊完之后再做也一样。
 * 矩阵取自 Android `ColorMatrix.setSaturation` 的标准实现(Rec.709 亮度权重)。
 */
private fun saturationFilter(saturation: Float): ColorFilter? {
    if (saturation <= 0f) return null
    val inv = 1f - saturation
    val r = 0.213f * inv
    val g = 0.715f * inv
    val b = 0.072f * inv
    val m = ColorMatrix()
    m[0, 0] = r + saturation
    m[0, 1] = g
    m[0, 2] = b
    m[1, 0] = r
    m[1, 1] = g + saturation
    m[1, 2] = b
    m[2, 0] = r
    m[2, 1] = g
    m[2, 2] = b + saturation
    return ColorFilter.colorMatrix(m)
}

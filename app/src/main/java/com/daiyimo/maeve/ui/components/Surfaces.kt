package com.daiyimo.maeve.ui.components

import android.os.Build
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * 页面底:中性渐变 + 三团极光(见 [MaeveBackdrop])。所有页面共用同一张底,切页面时不动。
 */
@Composable
fun PageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    MaeveBackdrop(modifier, content = content)
}

/**
 * **所有的底**。四档全部是 iOS 毛玻璃,按 [tier] 取材质。
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
 * 玻璃的定义是**背后的东西被模糊了才透过来**。所以这里每一档都真实取样
 * [GlassBackdrop.live]（滚动内容）和 [GlassBackdrop.frost]（极光底）,
 * 再用 [GlassStyle.blurSigma] 做高斯模糊。
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
    var origin by remember { mutableStateOf(Offset.Zero) }

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

    // 不模糊时走这里。
    //
    // ★ 为什么现在**总是**走这里：`record` + `drawLayer` 那套跨层取样会让
    //   hwui 的 DirtyStack 无限增长（512 帧后 RenderThread 栈溢出 SIGSEGV），
    //   点设置页、关于页必崩。见 [rememberGlassBackdrop] 的说明。
    //
    // 所以玻璃的「透」改由**叠层**表达，不再真的模糊背景：
    //   ① 半透明填充（fallbackFill，白度已按档位调过）
    //   ② 顶边内高光（glassRim）—— 光从上方打在玻璃边上
    //   ③ 上亮下暗的内描边（glassBevel）—— 一圈厚度
    // 三层叠起来，在浅色纸面上已经足够读作"一块玻璃"，而且零渲染风险。
    if (!style.blurs || backdrop == null) {
        return shadowed
            .background(style.fallbackFill, shape)
            .drawBehind { glassRim(style) }
            .drawBehind { glassBevel(style, shape, colors) }
            .then(
                if (style.hairlineWidth > 0.dp && style.hairline.alpha > 0f) {
                    Modifier.border(style.hairlineWidth, style.hairline, shape)
                } else {
                    Modifier
                },
            )
    }

    return shadowed
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            // ① 背景:扣掉自身偏移,把「背后那块」画进来
            val d = backdrop.frostOrigin - origin
            translate(d.x, d.y) { drawLayer(backdrop.frost) }
            val dl = backdrop.liveOrigin - origin
            translate(dl.x, dl.y) { drawLayer(backdrop.live) }
        }
        // ★ 这里**不加** graphicsLayer + BlurEffect。
        //   模糊已经在 live / frost 两个录制端各自烘焙好；
        //   如果在这里再套一层 renderEffect，就形成「玻璃 layer 里画 live layer」
        //   的自我引用，hwui 的 computeTransformImpl 会无限递归到栈溢出。
        // ② 半透明填充
        .background(style.fill, shape)
        // ③ 内高光(顶边)
        .drawBehind { glassRim(style) }
        // ④ 内描边:上亮下暗,给玻璃一圈厚度
        .drawBehind { glassBevel(style, shape, colors) }
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
 * 真正的模糊仍然交给 [cardSurface]:模糊是贵的,应该留给大面积的卡片容器;
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
    subtitle: String? = null,
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
 * 真·背景模糊的开关。
 *
 * ★ 当前**关闭**，这是刻意的，不是没做完。
 *
 * 之前的实现用 `GraphicsLayer.record {}` 把主界面内容录进 `live` 层，玻璃件再
 * `drawLayer(live)` 平移到自己身下做取样。这在 EMUI/HarmonyOS 的 hwui 上会让
 * `DirtyStack` 只增不减（上限 512），最终
 * `computeTransformImpl` 递归到栈溢出 —— 点「设置」「关于」整页必崩，
 * 从 v0.4 一直带到了交付。
 *
 * 关掉之后所有玻璃件走 [cardSurface] 的 fallback 分支：半透明填充 + 顶边内高光
 * + 上亮下暗内描边。三层叠出来在浅色纸面上已经读作玻璃，而且完全没有渲染风险。
 *
 * 要重新打开，必须换掉跨层取样方案（比如用 `RenderNode` 自己维护一块模糊位图，
 * 或者干脆用系统级 backdrop blur），**不能**只是把这里改回 true。
 * 打开后务必跑一遍：开侧栏 → 设置 → 关于 → 返回，并检查
 * `adb logcat | grep -E "Fatal signal|RenderThread"` 应为 0。
 */
val GlassBlurSupported: Boolean = false

/**
 * 页面级玻璃运行时,由 [MaeveBackdrop] 创建、经 LocalGlassBackdrop 提供。
 *
 * - [live]:实时内容层。滚动内容用 [glassSource] 录进来;所有玻璃件取样它,
 *   看得见背后真正的滚动内容——这是「玻璃」和「半透明贴纸」的分界线。
 * - [frost]:静态极光层,模糊已烘焙。作为打底,保证玻璃背后没有内容的地方
 *   也有光可透,不会变成一片死灰。
 */
@Stable
class GlassBackdrop internal constructor(
    internal val live: GraphicsLayer,
    internal val frost: GraphicsLayer,
) {
    /** 实时内容层左上角在窗口中的位置(由 [glassSource] 的内容节点上报)。 */
    internal var liveOrigin by mutableStateOf(Offset.Zero)
    /** 极光层左上角在窗口中的位置(由页面根上报)。 */
    internal var frostOrigin by mutableStateOf(Offset.Zero)
}

/** 不支持模糊时为 null,玻璃件就用不透明底。 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop? {
    if (!GlassBlurSupported) return null
    val live = rememberGraphicsLayer()
    val frost = rememberGraphicsLayer()
    return remember(live, frost) {
        // 极光层的模糊在这里烘焙:之后每个玻璃件只是平移着画它,不再有逐件模糊的开销
        frost.renderEffect = BlurEffect(FROST_SIGMA, FROST_SIGMA, TileMode.Clamp)
        // ★ 实时内容层也在这里烘焙一次模糊。模糊必须挂在**录制端**：
        //   取样垫只负责平移绘制，不参与模糊，才不会形成「玻璃 → live → 玻璃」的环。
        GlassBackdrop(live, frost)
    }
}

/** 极光层的预模糊强度。 */
private const val FROST_SIGMA = 18f

/**
 * 实时内容层的预模糊强度。
 *
 * 取值偏小：这一层是**统一**模糊，各玻璃档位的差异（Card 4dp / Sheet 22dp）
 * 靠填充透明度与描边区分，不再靠逐件二次模糊——那样会把上面的自引用问题带回来。
 */
private const val LIVE_SIGMA = 12f

/**
 * 把这块内容记成玻璃的实时背景。玻璃本身不能在这块内容里面。
 *
 * ★ 只 **record**，不在这里 `drawLayer`。
 *
 * 旧版是 `live.record{...}; drawLayer(live)` —— 录制完立刻把同一层画回原处，
 * 等于「图层把自己画进自己」。表面看不出问题，但 hwui 在算 transform 时
 * 会顺着这条引用一直递归到栈溢出（RenderThread SIGSEGV）。正常绘制由
 * `drawWithContent` 自带的 `drawContent()` 完成，这里不需要再画一次。
 */
fun Modifier.glassSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null) this
    else onGloballyPositioned { backdrop.liveOrigin = it.positionInRoot() }
        .drawWithContent {
            // 先把正常内容画出来（这一步是「主界面看得见」的唯一来源），
            // 再把同一份内容录进 live 层供玻璃取样。
            // 顺序不能反：旧的 `record{...}` 里有 drawContent，但那只是录进离屏层，
            // 屏幕上一个像素都不会出现——所以删掉 `drawLayer(live)` 之后主界面全灰了。
            drawContent()
            backdrop.live.record { this@drawWithContent.drawContent() }
        }

/**
 * 玻璃取样垫:把自己身后那块 backdrop 画面(frost 极光 + live 实时内容)画进来。
 * 必须排在玻璃容器的**第一个子位**,填充和内容依次画在它上面。
 *
 * ★ 这里**不能**再加 `graphicsLayer { renderEffect = BlurEffect(...) }`。
 *
 * 之前是在这个取样垫的 layer 上加模糊，而 layer 内部 `drawBehind` 又要把
 * `backdrop.live` 画进来——live 层是同一棵视图树里录制的，于是「玻璃画 live」
 * 和「live 属于玻璃」互相引用，hwui 在 `computeTransformImpl` 里无限自递归，
 * RenderThread 栈溢出（SIGSEGV / `stack pointer is not in a rw map`），
 * 表现为点进设置页直接闪退。
 *
 * 模糊改在**录制端**做：live 层自己带 renderEffect（见 [GlassBackdrop]），
 * 取样垫只负责按偏移把已经模糊好的层画上去，是单向的，不会成环。
 */
@Composable
fun GlassSampler(style: GlassStyle, backdrop: GlassBackdrop, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer { clip = true }
            .drawBehind {
                val d = backdrop.frostOrigin - origin
                translate(d.x, d.y) {
                    drawLayer(backdrop.frost) // 极光垫底:没有内容的地方也有光可透
                    drawLayer(backdrop.live) // 实时内容:滚动从玻璃后面过
                }
            },
    )
}

/**
 * 玻璃输入栏的外壳:chrome 玻璃层级(半透明底 + 背景模糊 + 悬浮阴影 + 内描边 + 顶边高光),
 * 高至少 52。[backdrop] 为 null(不支持模糊、或后面没有内容)时用不透明底。
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
    val sampling = backdrop != null && style.blurs
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

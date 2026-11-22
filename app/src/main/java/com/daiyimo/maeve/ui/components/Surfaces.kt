package com.daiyimo.maeve.ui.components

import android.graphics.BlurMaskFilter as AndroidBlurMaskFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
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
import com.daiyimo.maeve.ui.theme.MaeveRadius
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveSize
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.LocalMaeveColors
import com.daiyimo.maeve.ui.theme.drawAurora
import com.daiyimo.maeve.ui.theme.inactiveFill
import com.daiyimo.maeve.ui.theme.MaeveType
import com.daiyimo.maeve.R
import kotlin.math.abs
import kotlin.math.max
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

    // 外投影使用契约中的 blur/offset,并且只画在形状外部。
    val shadowed = then(glassShadow(style, shape)).clip(shape)

    val hairline = if (style.hairlineWidth > 0.dp && style.hairline.alpha > 0f) {
        Modifier.border(style.hairlineWidth, style.hairline, shape)
    } else {
        Modifier
    }

    // 不取样的路径:半透明填充 + 顶边内高光 + 上亮下暗内描边。
    // 三层叠起来在浅色纸面上已经读作「一块玻璃」,而且零渲染风险。
    if (!sampling || bd == null) {
        // Card/Pill 本来就不采样,必须画半透明 fill;只有 Chrome/Sheet 的
        // 禁用/跨窗口回退才使用不透明 fallbackFill。
        val inactive = style.inactiveFill(tier)
        return shadowed
            .background(inactive, shape)
            .drawBehind { glassRim(style, shape) }
            .drawBehind { glassBevel(style, shape, colors) }
            .then(hairline)
    }

    // 取样的路径:内容节点每录一帧,这里就把那一帧垫在身下,只糊这一帧。
    // 垫进来的层在 .clip(shape) 之后、.background() 之前,所以圆角、填充、
    // 高光、描边的层次都是对的。
    val slot = rememberGlassSlot(bd, style, shape)
    return shadowed
        .onGloballyPositioned {
            slot.origin = it.positionInRoot()
            slot.size = it.size.toSize()
        }
        .drawBehind { drawGlassSample(slot, style) }
        // ② 半透明填充
        .background(style.fill, shape)
        // ③ 内高光(顶边)
        .drawBehind { glassRim(style, shape) }
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
internal fun DrawScope.glassRim(style: GlassStyle, shape: Shape) {
    if (style.sheen.alpha > 0f) {
        val sheenHeight = (size.height * style.sheenStop).coerceAtLeast(1f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(style.sheen, Color.Transparent),
                startY = 0f,
                endY = sheenHeight,
            ),
            size = Size(size.width, sheenHeight),
        )
    }
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
    // 定向受光边:光在左上被接住,右下渐隐 —— 玻璃因此有了光向
    // (glasswork 的 rim light:亮的一角接光,对角渐隐)。
    // 画在**边**上(描边),不是铺满整面:铺满会把背后的颜色洗白。
    if (style.catchLight.alpha > 0f) {
        drawPath(
            shape.toFlatPath(size, this),
            brush = Brush.linearGradient(
                colors = listOf(
                    style.catchLight,
                    style.catchLight.copy(alpha = style.catchLight.alpha * 0.55f),
                    Color.Transparent,
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            style = Stroke(style.hairlineWidth.toPx().coerceAtLeast(1.5f)),
        )
    }
}

/**
 * 内描边 + 斜带:一圈「上亮下暗」的极细渐变边,再往里几像素四条软带,
 * 模拟玻璃的侧面与厚度(glasswork 的 bevel bands)。
 *
 * 做法:
 * 1. 先整圈画一条偏暗的边(玻璃侧面,负责轮廓);
 * 2. 再用竖向渐变画一遍(上半段亮、下半段透明,受光面);
 * 3. 最后裁进形状,贴边画四条软带 —— 顶亮、左右更弱、底暗。
 *    带子始终在边内:宽了会把背后的颜色洗白。
 */
internal fun DrawScope.glassBevel(style: GlassStyle, shape: Shape, colors: MaeveColors) {
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
    // 斜带:裁进形状画,不出边
    val band = style.band.toPx()
    val hasBevel = style.bevelTop.alpha > 0f || style.bevelSide.alpha > 0f || style.bevelBase.alpha > 0f
    if (!hasBevel || band <= 0f || size.height <= band || size.width <= band) return
    clipPath(path) {
        if (style.bevelTop.alpha > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(style.bevelTop, Color.Transparent),
                    startY = 0f,
                    endY = band * 1.2f,
                ),
                size = Size(size.width, band * 1.2f),
            )
        }
        if (style.bevelSide.alpha > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(style.bevelSide, Color.Transparent),
                    startX = 0f,
                    endX = band,
                ),
                size = Size(band, size.height),
            )
            // 右侧弱一档:光主要来自左上
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        style.bevelSide.copy(alpha = style.bevelSide.alpha * 0.55f),
                        Color.Transparent,
                    ),
                    startX = size.width,
                    endX = size.width - band,
                ),
                topLeft = Offset(size.width - band, 0f),
                size = Size(band, size.height),
            )
        }
        if (style.bevelBase.alpha > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(style.bevelBase, Color.Transparent),
                    startY = size.height,
                    endY = size.height - band,
                ),
                topLeft = Offset(0f, size.height - band),
                size = Size(size.width, band),
            )
        }
    }
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

/** 契约中的外阴影:even-odd 裁掉形状内部,只让模糊落在玻璃外。 */
fun Modifier.glassShadow(style: GlassStyle, shape: Shape): Modifier =
    if (style.shadowBlur <= 0.dp || style.outerShadow.alpha <= 0f) {
        this
    } else {
        drawBehind { drawGlassShadow(style, shape) }
    }

private fun DrawScope.drawGlassShadow(style: GlassStyle, shape: Shape) {
    val blur = style.shadowBlur.toPx()
    if (blur <= 0f || style.outerShadow.alpha <= 0f) return
    val offsetY = style.shadowOffsetY.toPx()
    val extent = blur * 3f + abs(offsetY) + 2f
    val shapePath = shape.toFlatPath(size, this)
    val outside = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-extent, -extent, size.width + extent, size.height + extent))
        addPath(shapePath)
    }
    val paint = AndroidPaint().apply {
        color = style.outerShadow.toArgb()
        maskFilter = AndroidBlurMaskFilter(blur, AndroidBlurMaskFilter.Blur.NORMAL)
    }
    clipPath(outside) {
        translate(0f, offsetY) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPath(shapePath.asAndroidPath(), paint)
            }
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
        .drawBehind { glassRim(style, shape) }
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

/** AGSL/RuntimeShader RenderEffect 从 Android 13 起可用。 */
val GlassRefractionSupported: Boolean = Build.VERSION.SDK_INT >= 33

/**
 * 录制时玻璃四周多录多少像素(σ 的倍数)。
 *
 * 高斯模糊需要玻璃**外面**的真实内容才能糊得自然;只录玻璃自己那一块的话,
 * 边缘会因为 `TileMode.Clamp` 把边像素往外拉,糊出一个可见的"硬框"。
 */
private const val BLUR_BLEED_SIGMA = 3f

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

    /** 当前材质与形状;source 录制时按尺寸折算四角半径和光学 uniforms。 */
    internal var style by mutableStateOf<GlassStyle?>(null)
    internal var shape by mutableStateOf<Shape>(RectangleShape)
    internal var radii: FloatArray = FloatArray(4)

    /** API 33 实现隔离在单独类中,低版本不会加载 RuntimeShader 类型。 */
    internal var shaderHolder by mutableStateOf<Any?>(null)

    /** 录好的那一层:内容 + Blur/RuntimeShader Effect,由内容节点写入。 */
    internal var layer: GraphicsLayer? = null
    /** 层比玻璃大出来的那一圈(px)。玻璃按 `-pad` 平移到正确位置。 */
    internal var pad = 0f
    /** 当前生效的 σ(px)。0 表示这一档不糊。 */
    internal var sigma = 0f
    /** API 31-32/blur-only 路径使用的饱和度与色调矩阵。 */
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
    val view = LocalView.current
    if (!GlassBlurSupported || !view.isHardwareAccelerated) return null
    return remember(view) { GlassBackdrop(view) }
}

/** 玻璃件和内容在同一个窗口里才取样。弹层、对话框是另一个窗口,坐标根都不一样。 */
@Composable
internal fun GlassBackdrop.samplesHere(): Boolean = LocalView.current === view

/**
 * 玻璃件的取样运行时:一个槽位。
 *
 * σ 和饱和度在这里按密度折算好存进槽位,内容节点录制时直接取用。
 * 离开组合时注销,槽位不会留在注册表里白录。
 */
@Composable
private fun rememberGlassSlot(
    backdrop: GlassBackdrop,
    style: GlassStyle,
    shape: Shape,
): GlassSlot {
    val slot = remember { GlassSlot() }
    val density = LocalDensity.current
    val context = LocalContext.current
    slot.style = style
    slot.shape = shape
    remember(style, density) {
        slot.sigma = style.blurSigma * density.density
        slot.filter = toneFilter(style.saturation, style.brightness, style.contrast)
    }
    val shaderSource = remember(context) {
        runCatching {
            context.resources.openRawResource(R.raw.glass_refraction)
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull()
    }
    slot.shaderHolder = if (Build.VERSION.SDK_INT >= 33 && shaderSource != null) {
        remember(shaderSource) {
            runCatching { Api33ShaderHolder(shaderSource) }.getOrNull()
        }
    } else {
        null
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
            val token = remember { Any() }
            DisposableEffect(backdrop, token) {
                backdrop.sources.add(token)
                onDispose { backdrop.sources.remove(token) }
            }
            val colors = LocalMaeveColors.current
            onGloballyPositioned { backdrop.sourceOrigin = it.positionInRoot() }
                // The source owns the wallpaper so the recorded backdrop contains
                // the aurora as well as scrolling content. The outer MaeveBackdrop
                // keeps drawing it for pages that do not opt into sampling.
                .drawBehind { drawAurora(colors) }
                .drawWithCache {
                    val count = backdrop.slots.size
                    val designated = backdrop.sources.firstOrNull() === token
                    val layers = List(count) { obtainGraphicsLayer() }
                    val density = this
                    onDrawWithContent {
                        drawContent()
                        if (count == 0 || !designated) return@onDrawWithContent
                        val src = backdrop.sourceOrigin
                        for (i in 0 until count) {
                            val slot = backdrop.slots.getOrNull(i) ?: continue
                            val layer = layers.getOrNull(i) ?: continue
                            val style = slot.style ?: continue
                            val gw = slot.size.width
                            val gh = slot.size.height
                            if (gw < 1f || gh < 1f) continue

                            val refractive = Build.VERSION.SDK_INT >= 33 &&
                                slot.shaderHolder != null && style.refracts
                            val refractiveSigma = style.blurSigma * density.density
                            val fallbackSigma = style.fallbackBlurSigma * density.density
                            val refractionPx = if (refractive) style.refractionStrength.toPx() else 0f
                            // 同时覆盖折射和 shader 失败后的 fallback blur。
                            val pad = max(
                                max(refractiveSigma, fallbackSigma) * BLUR_BLEED_SIGMA,
                                refractionPx + 2.dp.toPx(),
                            ).coerceAtMost(MAX_BLEED_DP.toPx())

                            val dx = slot.origin.x - src.x
                            val dy = slot.origin.y - src.y
                            val lw = (gw + pad * 2f).roundToInt().coerceIn(1, MAX_CAPTURE_PX)
                            val lh = (gh + pad * 2f).roundToInt().coerceIn(1, MAX_CAPTURE_PX)
                            slot.radii = slot.shape.cornerRadiiPx(Size(gw, gh), density, layoutDirection)
                            if (Build.VERSION.SDK_INT >= 33 && refractive) {
                                (slot.shaderHolder as? Api33ShaderHolder)?.updateUniforms(
                                    layerWidth = lw,
                                    layerHeight = lh,
                                    pad = pad,
                                    glassWidth = gw,
                                    glassHeight = gh,
                                    radii = slot.radii,
                                    optics = floatArrayOf(
                                        style.refractionStrength.toPx(),
                                        style.refractionWidth.toPx(),
                                        style.chromaticAberration.toPx(),
                                        style.specularStrength,
                                    ),
                                    tone = floatArrayOf(
                                        style.saturation,
                                        style.brightness,
                                        style.contrast,
                                    ),
                                )
                            }
                            val prepared = prepareSlotEffect(slot, refractiveSigma, fallbackSigma)
                            slot.sigma = prepared.sigma
                            layer.clip = true
                            layer.renderEffect = prepared.renderEffect
                            layer.colorFilter = prepared.colorFilter
                            try {
                                layer.record(density, layoutDirection, IntSize(lw, lh)) {
                                    val outer = this@onDrawWithContent
                                    val ctx = outer.drawContext
                                    val savedCanvas = ctx.canvas
                                    val savedLayer = ctx.graphicsLayer
                                    var saved = false
                                    try {
                                        ctx.canvas = drawContext.canvas
                                        ctx.graphicsLayer = layer
                                        drawContext.canvas.save()
                                        saved = true
                                        drawContext.canvas.translate(-(dx - pad), -(dy - pad))
                                        outer.drawContent()
                                    } finally {
                                        try {
                                            if (saved) drawContext.canvas.restore()
                                        } finally {
                                            ctx.canvas = savedCanvas
                                            ctx.graphicsLayer = savedLayer
                                        }
                                    }
                                }
                                slot.layer = layer
                                slot.pad = pad
                                slot.frame++
                            } catch (_: IllegalArgumentException) {
                                slot.layer = null
                            } catch (_: IllegalStateException) {
                                slot.layer = null
                            }
                        }
                    }
                }
        },
    )

private data class PreparedSlotEffect(
    val renderEffect: RenderEffect?,
    val colorFilter: ColorFilter?,
    val sigma: Float,
)

private fun prepareSlotEffect(
    slot: GlassSlot,
    refractiveSigma: Float,
    fallbackSigma: Float,
): PreparedSlotEffect {
    if (Build.VERSION.SDK_INT >= 33) {
        val holder = slot.shaderHolder as? Api33ShaderHolder
        if (holder != null && (slot.style?.refracts == true)) {
            try {
                return PreparedSlotEffect(
                    renderEffect = createRefractionEffect(holder, refractiveSigma),
                    colorFilter = null,
                    sigma = refractiveSigma,
                )
            } catch (_: IllegalArgumentException) {
                slot.shaderHolder = null
            } catch (_: IllegalStateException) {
                slot.shaderHolder = null
            }
        }
    }
    return PreparedSlotEffect(
        renderEffect = if (fallbackSigma > 0f) BlurEffect(fallbackSigma, fallbackSigma, TileMode.Clamp) else null,
        colorFilter = slot.filter,
        sigma = fallbackSigma,
    )
}

@RequiresApi(33)
private fun createRefractionEffect(holder: Api33ShaderHolder, sigma: Float): RenderEffect {
    val blur = AndroidRenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
    val lens = AndroidRenderEffect.createRuntimeShaderEffect(holder.shader, "content")
    return AndroidRenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
}

@RequiresApi(33)
internal class Api33ShaderHolder(source: String) {
    val shader = RuntimeShader(source)

    fun updateUniforms(
        layerWidth: Int,
        layerHeight: Int,
        pad: Float,
        glassWidth: Float,
        glassHeight: Float,
        radii: FloatArray,
        optics: FloatArray,
        tone: FloatArray,
    ) {
        shader.setFloatUniform("uSize", layerWidth.toFloat(), layerHeight.toFloat())
        shader.setFloatUniform("uGlassRect", pad, pad, glassWidth, glassHeight)
        shader.setFloatUniform("uRadii", radii[0], radii[1], radii[2], radii[3])
        shader.setFloatUniform("uOptics", optics[0], optics[1], optics[2], optics[3])
        shader.setFloatUniform("uTone", tone[0], tone[1], tone[2])
    }
}

private fun Shape.cornerRadiiPx(
    size: Size,
    density: androidx.compose.ui.unit.Density,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
): FloatArray {
    when (val outline = createOutline(size, layoutDirection, density)) {
        is androidx.compose.ui.graphics.Outline.Rectangle -> return FloatArray(4)
        is androidx.compose.ui.graphics.Outline.Rounded -> {
            val r = outline.roundRect
            return floatArrayOf(
                r.topLeftCornerRadius.x,
                r.topRightCornerRadius.x,
                r.bottomRightCornerRadius.x,
                r.bottomLeftCornerRadius.x,
            )
        }
        is androidx.compose.ui.graphics.Outline.Generic -> when (this) {
            MaeveShapes.Sheet -> return FloatArray(4) { with(density) { MaeveRadius.Xl.toPx() } }
            MaeveShapes.Card -> return FloatArray(4) { with(density) { MaeveRadius.Lg.toPx() } }
            MaeveShapes.Button -> return FloatArray(4) { with(density) { MaeveRadius.Md.toPx() } }
            MaeveShapes.Tile -> return FloatArray(4) { with(density) { MaeveRadius.Sm.toPx() } }
            CircleShape -> return FloatArray(4) { minOf(size.width, size.height) / 2f }
            else -> return FloatArray(4) { with(density) { 14.dp.toPx() } }
        }
    }
}

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
fun GlassSampler(
    style: GlassStyle,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
) {
    val slot = rememberGlassSlot(backdrop, style, shape)
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
    val hairline = if (style.hairlineWidth > 0.dp && style.hairline.alpha > 0f) {
        Modifier.border(style.hairlineWidth, style.hairline, shape)
    } else {
        Modifier
    }
    Box(
        modifier.glassShadow(style, shape).clip(shape),
    ) {
        if (sampling && backdrop != null) {
            GlassSampler(style, backdrop, Modifier.matchParentSize(), shape)
        }
        Row(
            Modifier.fillMaxWidth()
                .background(if (sampling) style.fill else style.fallbackFill)
                .drawBehind { glassRim(style, shape) }
                .drawBehind { glassBevel(style, shape, c) }
                .then(hairline)
                .heightIn(min = MaeveSize.Composer).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/**
 * 液态玻璃的采样滤镜:saturate → contrast → brightness(glasswork 的
 * `backdrop-filter` 后三段;按 CSS 的应用顺序依次复合)。
 *
 * - 浅色玻璃 brightness > 1:背后的画面被提亮,像光透过净玻璃;
 * - 深色 < 1:背后的画面被压暗,像烟色玻璃 —— 而不是糊成一片灰。
 *
 * 高斯模糊和这三个都是线性变换,与模糊可交换,所以糊完之后再做也一样。
 * 饱和度矩阵取自 Android `ColorMatrix.setSaturation` 的标准实现(Rec.709 亮度权重)。
 */
private fun toneFilter(saturation: Float, brightness: Float, contrast: Float): ColorFilter? {
    if (saturation == 1f && brightness == 1f && contrast == 1f) return null
    // 三个滤镜都是通道上的仿射变换,按 CSS 的顺序 saturate → contrast → brightness
    // 手工复合(Compose 的 ColorMatrix 没有 postConcat,自己乘)。
    val m = compose3(brightnessMatrix(brightness), contrastMatrix(contrast), saturateMatrix(saturation))
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

/** 饱和度矩阵(Rec.709 亮度权重;Android ColorMatrix.setSaturation 的标准实现)。 */
private fun saturateMatrix(saturation: Float): FloatArray {
    val inv = 1f - saturation
    val r = 0.213f * inv
    val g = 0.715f * inv
    val b = 0.072f * inv
    return floatArrayOf(
        r + saturation, g, b, 0f, 0f,
        r, g + saturation, b, 0f, 0f,
        r, g, b + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** 对比度矩阵:out = (in - 0.5) * c + 0.5。 */
private fun contrastMatrix(c: Float): FloatArray {
    val shift = 0.5f * (1f - c)
    return floatArrayOf(
        c, 0f, 0f, 0f, shift,
        0f, c, 0f, 0f, shift,
        0f, 0f, c, 0f, shift,
        0f, 0f, 0f, 1f, 0f,
    )
}

/** 亮度矩阵:out = in * b。 */
private fun brightnessMatrix(b: Float): FloatArray = floatArrayOf(
    b, 0f, 0f, 0f, 0f,
    0f, b, 0f, 0f, 0f,
    0f, 0f, b, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/**
 * 4×5 行主序仿射矩阵的复合:(A2,a2)∘(A1,a1) = (A2·A1, A2·a1 + a2)。
 * 依次复合三个滤镜(先 saturate,再 contrast,最后 brightness)。
 */
private fun compose3(first: FloatArray, second: FloatArray, third: FloatArray): FloatArray =
    compose(compose(third, second), first)

private fun compose(second: FloatArray, first: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (i in 0 until 4) {
        for (j in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += second[i * 5 + k] * first[k * 5 + j]
            out[i * 5 + j] = sum
        }
        var sum = 0f
        for (k in 0 until 4) sum += second[i * 5 + k] * first[k * 5 + 4]
        out[i * 5 + 4] = sum + second[i * 5 + 4]
    }
    return out
}

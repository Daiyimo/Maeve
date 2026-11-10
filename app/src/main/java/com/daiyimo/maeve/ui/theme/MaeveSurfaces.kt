package com.daiyimo.maeve.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.ui.components.GlassBackdrop
import com.daiyimo.maeve.ui.components.GlassBlurSupported
import com.daiyimo.maeve.ui.components.rememberGlassBackdrop

/**
 * 表面层级。四档都是玻璃,但**只有浮在滚动内容之上的两档真的取样模糊**。
 *
 * | 层级 | 用在哪 | 模糊 σ | 填充白度 | 用途 |
 * |---|---|---|---|---|
 * | [Chrome] | 顶栏、输入栏、浏览器工具条 | 24 | 0.72 | 一直浮在滚动内容上 |
 * | [Sheet] | 底部弹层、菜单、侧栏 | 30 | 0.80 | 盖住更多内容,要更实 |
 * | [Card] | 设置卡、任务卡、审批卡、步骤框 | 0 | 0.60 | 平放的大件 |
 * | [Pill] | 状态胶囊、模型胶囊、标签 | 0 | 0.66 | 小件,模糊太强会变糊点 |
 *
 * ## 为什么 Card / Pill 不取样(这一版最重要的取舍)
 *
 * Card、Pill 是**长在滚动内容里面**的:它们就在 `glassSource` 录制的那块内容里。
 * 一旦让它们也取样,玻璃看到的就是「一张包含自己的旧画面」——下一帧再录进去,
 * 于是一条玻璃里套一条玻璃,越套越深,变成一面无限反射的镜子。所以平放的大件
 * 和小件只做填充 + 顶边内高光 + 上亮下暗内描边三层,不取样、不模糊。
 * 这与 Flutter 工作台那一侧参考设计里 card/pill 无模糊是一致的。
 *
 * Chrome、Sheet 则**从来不在被录制的内容里**(输入栏、抽屉、工具条都是内容的
 * 兄弟节点,画在内容之后),所以它们取样是安全的:看得见背后真正的滚动内容。
 *
 * ## 玻璃的五层构造(iOS 的做法)
 *
 * ```
 *  ① 背景模糊(σ 按档位,带饱和度增益,让背后的颜色透上来更艳)
 *  ② 半透明白/黑填充(决定「实」的程度)
 *  ③ 内高光:顶边一道上白下透明的渐变(光从上面打在玻璃边上)
 *  ④ 内描边:0.5dp 的一圈,上亮下暗(玻璃厚度)
 *  ⑤ 外投影:只画在形状外部,内部不被弄脏
 * ```
 *
 * 这五层在 [com.daiyimo.maeve.ui.components.Modifier.cardSurface] 里实现。
 *
 * 不支持模糊(API < 31 或设置里关了)时,所有档位退回 [GlassStyle.fallbackFill]
 * ——一个接近不透明的表面,层级退化为纯投影,不会变成一片透明。
 */
enum class GlassTier { Chrome, Sheet, Card, Pill }

/** 一种表面材质的全部参数。 */
@Immutable
class GlassStyle(
    val fill: Color,
    /** 关模糊时的填充:必须接近不透明,否则文字会飘在背后的内容上。 */
    val fallbackFill: Color,
    val hairline: Color,
    val hairlineWidth: Dp = 0.75.dp,
    /** 顶边高光:光从上方打在玻璃边上,往里渐隐。 */
    val rim: Color = Color.Transparent,
    /** 高斯模糊 σ,单位 dp;0 = 不模糊。 */
    val blurSigma: Float = 0f,
    /** 模糊后的饱和度增益,iOS 式「透色」——背后的颜色透过玻璃更艳。 */
    val saturation: Float = 1f,
    /** 只画在形状**外部**的投影(玻璃内部不被阴影弄脏)。 */
    val outerShadow: Color = Color.Transparent,
    /** 抬起来的高度;不透明卡也可以是 0。 */
    val elevation: Dp = 0.dp,
    /**
     * 内高光的高度占元素高度的比例上限。
     * 顶边高光不该出现在一枚 28dp 的药丸上——那样整个药丸都被高光填满了。
     */
    val rimFraction: Float = 0.30f,
) {
    val blurs: Boolean get() = blurSigma > 0f
}

/** 四档表面,由 [MaeveTheme] 提供。 */
@Immutable
class MaeveSurfaces(
    val chrome: GlassStyle,
    val sheet: GlassStyle,
    val card: GlassStyle,
    val pill: GlassStyle,
) {
    operator fun get(tier: GlassTier): GlassStyle = when (tier) {
        GlassTier.Chrome -> chrome
        GlassTier.Sheet -> sheet
        GlassTier.Card -> card
        GlassTier.Pill -> pill
    }
}

/** 由 [MaeveColors] 和模糊开关出四档表面。 */
fun buildSurfaces(c: MaeveColors, blurEnabled: Boolean): MaeveSurfaces {
    val canBlur = blurEnabled && GlassBlurSupported
    return MaeveSurfaces(
        // —— 顶栏 / 输入栏:一直压在滚动内容上,模糊要强,投影要明显 ——
        chrome = GlassStyle(
            fill = c.glass,
            fallbackFill = c.surface.copy(alpha = 0.97f),
            hairline = c.glassEdge,
            rim = c.glassRim,
            blurSigma = if (canBlur) 24f else 0f,
            saturation = 1.40f,
            outerShadow = c.floatShadow.copy(alpha = 0.13f),
            elevation = 14.dp,
            rimFraction = 0.42f,
        ),
        // —— 弹层 / 侧栏:盖住大面积内容,要更实、更糊,投影最重 ——
        sheet = GlassStyle(
            fill = if (c.isDark) c.glassSheetDark else c.glassSheet,
            fallbackFill = if (c.isDark) c.surfaceHigh else c.surface,
            hairline = c.glassEdge,
            rim = c.glassRim,
            blurSigma = if (canBlur) 30f else 0f,
            saturation = 1.55f,
            outerShadow = c.floatShadow.copy(alpha = 0.19f),
            elevation = 22.dp,
            rimFraction = 0.16f,
        ),
        // —— 平放的大件:设置卡、任务卡、步骤框 ——
        // blurSigma 恒为 0:卡片长在被录制的内容里,取样会变成「玻璃里套玻璃」的
        // 无限反射(见文件头的说明)。层级靠填充白度、内描边和一道浅投影承担。
        card = GlassStyle(
            fill = c.glassCard,
            fallbackFill = c.surface,
            hairline = c.glassEdge,
            rim = c.glassRim,
            blurSigma = 0f,
            saturation = 1f,
            // 平放的卡也要一道投影:玻璃浮在背景上,哪怕只浮 2dp
            outerShadow = c.floatShadow.copy(alpha = 0.10f),
            elevation = 4.dp,
            rimFraction = 0.34f,
        ),
        // —— 小件:胶囊、芯片、状态标。不取样:两三个字糊成一团反而读不了 ——
        pill = GlassStyle(
            fill = c.glassPill,
            fallbackFill = c.fill,
            hairline = c.glassEdge,
            rim = c.glassRim,
            blurSigma = 0f,
            saturation = 1f,
            outerShadow = Color.Transparent,
            elevation = 0.dp,
            rimFraction = 0.5f,
        ),
    )
}

/** 表面材质的 CompositionLocal;由 [MaeveTheme] 提供。 */
val LocalMaeveSurfaces = staticCompositionLocalOf {
    buildSurfaces(MaevePalette.light(deriveAccent(AccentPreset.Reverie)), blurEnabled = true)
}

/**
 * 页面级玻璃运行时的 CompositionLocal;由 [MaeveBackdrop] 提供。
 * null = 这一页没有 backdrop(或不支持模糊):玻璃退化为不透明底。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * 全屏页面背景:**中性渐变 + 三团极光**。
 *
 * 毛玻璃需要有**东西可透**。如果背景是一张纯色，玻璃模糊之后还是那个纯色，
 * 只是变糊了一点——看不出是玻璃。所以极光回来了：三团低饱和的色斑，
 * 一颗跟着主题色走，两颗是固定的冷/暖衬色。它们不是装饰，是给玻璃的
 * 折射提供原料(见 [buildSurfaces] 的 saturation 增益)。
 *
 * 极光刻意压得很淡(alpha 0.10~0.16)：太艳就会把玻璃染成花纸。
 * 玻璃件还能从中读出「位置感」:卡片在左上角时透出来的颜色和右下角不一样,
 * 界面因此有了空间深度,而不是一层贴纸。
 *
 * **同一主题内所有页共用同一张背景**,切页面时背景不动。
 */
@Composable
fun MaeveBackdrop(
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    Box(
        modifier.fillMaxSize().maeveBackdrop(MaeveTheme.colors),
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            content()
        }
    }
}

/** 背景修饰符(不创建玻璃运行时的轻量版)。 */
fun Modifier.maeveBackdrop(c: MaeveColors, glow: Boolean = true): Modifier = drawBehind {
    drawAurora(c)
}

/**
 * 背景本体:中性竖渐变 + 三团极光。
 *
 * 三团的位置是固定的(左上、右上、中下),不随页面滚动。滚动内容由
 * [com.daiyimo.maeve.ui.components.glassSource] 逐帧录进玻璃槽位,
 * 极光始终静止——玻璃在滚动时透出来的底色因此保持稳定,不会一边滚一边变色。
 */
internal fun DrawScope.drawAurora(c: MaeveColors) {
    // ① 底色:一道极轻的竖向渐变,让大留白有方向感
    drawRect(Brush.verticalGradient(0f to c.bgTop, 0.58f to c.bg, 1f to c.bgBottom))

    val w = size.width
    val h = size.height
    // ② 三团极光。半径按屏幕短边算,保证在任何屏幕比例下都铺得开
    val unit = minOf(w, h)
    drawRect(
        Brush.radialGradient(
            colors = listOf(c.glowPrimary, Color.Transparent),
            center = Offset(w * 0.14f, h * 0.06f),
            radius = unit * 0.92f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(c.glowSecondary, Color.Transparent),
            center = Offset(w * 0.92f, h * 0.26f),
            radius = unit * 0.78f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(c.glowTertiary, Color.Transparent),
            center = Offset(w * 0.44f, h * 1.02f),
            radius = unit * 0.86f,
        ),
    )
}

/** 遮罩:弹层、抽屉后面的那层。玻璃时代需要它把背后的内容压暗,玻璃才显得「浮在上面」。 */
fun Modifier.maeveScrim(c: MaeveColors): Modifier = this.background(c.scrim)

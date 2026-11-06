package com.daiyimo.maeve.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.ui.components.GlassBlurSupported

/**
 * 玻璃层级。决定填充透明度、是否模糊、描边与顶边高光。
 * 铁律:**层次靠材质,不靠阴影**;M3 elevation 一律 0,没有 surfaceTint。
 *
 * | 层级 | 用在哪 | 模糊 |
 * |---|---|---|
 * | [Chrome] | 顶栏、输入条、浮钮 —— 浮在滚动内容上的 | 有 |
 * | [Sheet] | 底部弹层、菜单、抽屉 | 有 |
 * | [Card] | 设置卡、审批卡、工具组 —— 躺在页面上的 | 无 |
 * | [Pill] | 状态胶囊、模型胶囊 —— 列表里的小件 | 无 |
 *
 * 只有 chrome/sheet 真模糊:它们背后是会滚动的内容;card/pill 背后只是静态
 * 背景,半透明填充已经读作"磨砂",再上模糊只是白烧 GPU。
 */
enum class GlassTier { Chrome, Sheet, Card, Pill }

/** 一种玻璃材质的全部参数。 */
@Immutable
class GlassStyle(
    val fill: Color,
    /** 关模糊时的填充(低端机、减少透明):必须接近不透明。 */
    val fallbackFill: Color,
    val hairline: Color,
    val hairlineWidth: androidx.compose.ui.unit.Dp = 0.5.dp,
    /** 顶边高光:光从上方打在玻璃边上。 */
    val rim: Color,
    /** 高斯模糊 σ(逻辑像素);0 = 不模糊。 */
    val blurSigma: Float = 0f,
    /** 模糊后的饱和度增益, iOS 式"透色"。 */
    val saturation: Float = 1f,
    /** 只画在形状**外部**的投影(玻璃内部不被阴影弄脏)。 */
    val outerShadow: Color = Color.Transparent,
) {
    val blurs: Boolean get() = blurSigma > 0f
}

/** 四档玻璃,由 [MaeveTheme] 提供。 */
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

/** 由 [MaeveColors] 和模糊开关出四档玻璃。 */
fun buildSurfaces(c: MaeveColors, blurEnabled: Boolean): MaeveSurfaces {
    fun style(fillAlpha: Float, blurSigma: Float, saturation: Float, shadow: Color): GlassStyle {
        val base = if (c.isDark) c.surfaceHigh else Color.White
        return GlassStyle(
            fill = base.copy(alpha = fillAlpha),
            fallbackFill = base.copy(alpha = 0.94f),
            hairline = c.glassEdge,
            rim = c.glassRim,
            blurSigma = if (blurEnabled && GlassBlurSupported) blurSigma else 0f,
            saturation = saturation,
            outerShadow = shadow,
        )
    }
    return MaeveSurfaces(
        chrome = style(0.70f, 18f, 1.3f, c.floatShadow.copy(alpha = 0.10f)),
        sheet = style(0.82f, 24f, 1.4f, c.floatShadow.copy(alpha = 0.12f)),
        card = style(0.62f, 0f, 1f, Color.Transparent),
        pill = style(0.55f, 0f, 1f, Color.Transparent),
    )
}

/** 玻璃材质的 CompositionLocal;由 [MaeveTheme] 提供。 */
val LocalMaeveSurfaces = staticCompositionLocalOf {
    buildSurfaces(MaevePalette.light(deriveAccent(AccentPreset.Reverie)), blurEnabled = true)
}

/**
 * 全屏页面背景:**竖向渐变 + 两团极淡的光**。
 *
 * - 底层:bgTop → bg → bgBottom 竖向渐变。
 * - 右上:主色光晕(跟随配色)。左下:暮色光晕。透明度都很低,画在大半径
 *   径向渐变里,肉眼只觉得"有点透亮"。
 * - 光晕的作用是给玻璃一点可"透"的颜色;纯平背景上的玻璃看起来只是白块。
 *
 * **同一主题内所有页共用同一张背景**,切页面时背景不动,只有前景内容在变。
 */
fun Modifier.maeveBackdrop(c: MaeveColors, glow: Boolean = true): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(0f to c.bgTop, 0.55f to c.bg, 1f to c.bgBottom))
    if (glow) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(c.glowPrimary, Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.0f),
                radius = size.minDimension * 1.05f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(c.glowSecondary, Color.Transparent),
                center = Offset(size.width * 0.06f, size.height * 1.0f),
                radius = size.minDimension * 0.95f,
            ),
        )
    }
}

/** 页面底 + 内容。所有页面的根。 */
@Composable
fun MaeveBackdrop(
    modifier: Modifier = Modifier,
    glow: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = MaeveTheme.colors
    Box(modifier.fillMaxSize().maeveBackdrop(c, glow), content = content)
}

/**
 * 玻璃片:按 [GlassStyle] 上填充、发丝描边、顶边高光;chrome/sheet 且支持时真模糊
 * (模糊由调用方通过 [com.daiyimo.maeve.ui.components.GlassBackdrop] 提供画面)。
 * 不支持模糊时用 [GlassStyle.fallbackFill],不创建模糊层。
 */
@Composable
fun GlassSurface(
    style: GlassStyle,
    modifier: Modifier = Modifier,
    shape: Shape = MaeveShapes.Card,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = MaeveTheme.colors
    Box(
        modifier
            .clip(shape)
            .background(style.fill)
            .border(style.hairlineWidth, style.hairline, shape)
            .drawBehind {
                // 顶边高光:从形状上沿往里渐隐,光从上方打在玻璃边上
                val rimHeight = 14.dp.toPx().coerceAtMost(size.height / 3f)
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(style.rim, Color.Transparent),
                        startY = 0f,
                        endY = rimHeight,
                    ),
                    size = size.copy(height = rimHeight),
                )
            },
        content = content,
    )
}

/** 遮罩:弹层、抽屉后面的那层。 */
fun Modifier.maeveScrim(c: MaeveColors): Modifier = this.background(c.scrim)

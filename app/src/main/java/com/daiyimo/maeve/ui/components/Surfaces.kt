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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.ui.theme.GlassStyle
import com.daiyimo.maeve.ui.theme.LocalMaeveSurfaces
import com.daiyimo.maeve.ui.theme.MaeveBackdrop
import com.daiyimo.maeve.ui.theme.MaeveColors
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveSize
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

/**
 * 页面底:竖向渐变 + 右上主色光晕 + 左下暮色光晕(见 [MaeveBackdrop])。
 * 所有页面共用同一张背景,切页面时背景不动。
 */
@Composable
fun PageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    MaeveBackdrop(modifier, content = content)
}

/**
 * 卡片的底:玻璃卡层级(半透明填充 + 0.5dp 发丝线 + 顶边 rim 高光)。
 * 浅色下再压一道极淡的阴影帮助分层;深色下只靠材质(铁律:层次靠材质,不靠阴影)。
 */
@Composable
fun Modifier.cardSurface(colors: MaeveColors, shape: Shape = MaeveShapes.Card): Modifier {
    val style: GlassStyle = LocalMaeveSurfaces.current.card
    return this
        .then(
            if (colors.cardShadow.alpha > 0f) {
                shadow(6.dp, shape, clip = false, ambientColor = colors.cardShadow.copy(alpha = 0.45f), spotColor = colors.cardShadow.copy(alpha = 0.16f))
            } else this,
        )
        .background(style.fill, shape)
        .border(style.hairlineWidth, style.hairline, shape)
        .drawBehind { glassRim(style) }
}

/** 顶边高光:光从上方打在玻璃边上,往里渐隐。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.glassRim(style: GlassStyle) {
    val rimHeight = 14.dp.toPx().coerceAtMost(size.height / 3f)
    drawRect(
        Brush.verticalGradient(
            colors = listOf(style.rim, androidx.compose.ui.graphics.Color.Transparent),
            startY = 0f,
            endY = rimHeight,
        ),
        size = size.copy(height = rimHeight),
    )
}

/** 悬浮元素的阴影(输入栏、浮在内容上的按钮)。 */
fun Modifier.floatShadow(colors: MaeveColors, shape: Shape): Modifier =
    shadow(14.dp, shape, clip = false, ambientColor = colors.floatShadow.copy(alpha = 0.3f), spotColor = colors.floatShadow.copy(alpha = 0.35f))

/** 一张卡片,内容裁在圆角里(行的按压效果不出角)。 */
@Composable
fun MikaCard(modifier: Modifier = Modifier, shape: Shape = MaeveShapes.Card, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().cardSurface(MaeveTheme.colors, shape).clip(shape), content = content)
}

/**
 * 分组卡片:一组 [ListRow]。下面自带 12dp 间距,和下一组的 [GroupLabel](上 8)合起来是分组之间的 20。
 * 行与行的分隔线由行自己画,从文字处开始。
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    MikaCard(modifier.padding(bottom = 12.dp), content = content)
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
    MikaCard(modifier.padding(bottom = 12.dp)) {
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

/** 能做背景模糊(安卓 12 起有 RenderEffect);不能时玻璃退回不透明的卡片底。 */
val GlassBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 玻璃后面那层内容的画面:内容用 [glassSource] 画进来,[GlassBar] 取它模糊后垫在自己底下。 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

/** 不支持模糊时为 null,[GlassBar] 就用不透明底。 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop? {
    if (!GlassBlurSupported) return null
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** 把这块内容记成玻璃的背景。玻璃本身不能在这块内容里面。 */
fun Modifier.glassSource(backdrop: GlassBackdrop?): Modifier =
    if (backdrop == null) this
    else onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }

private val GlassBlur = BlurEffect(30f, 30f, TileMode.Clamp)

/**
 * 玻璃输入栏的外壳:chrome 玻璃层级(半透明底 + 背景模糊 + 悬浮阴影 + 发丝线 + 顶边高光),高至少 52。
 * [backdrop] 为 null(不支持模糊、或后面没有内容)时用不透明底。
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
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.floatShadow(c, shape).clip(shape)
            .then(if (backdrop != null) Modifier.onGloballyPositioned { origin = it.positionInRoot() } else Modifier),
    ) {
        if (backdrop != null) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { renderEffect = GlassBlur; clip = true }
                    .drawBehind {
                        val d = backdrop.origin - origin
                        translate(d.x, d.y) { drawLayer(backdrop.layer) }
                    },
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .background(if (backdrop != null) style.fill else style.fallbackFill)
                .border(style.hairlineWidth, style.hairline, shape)
                .drawBehind { glassRim(style) }
                .heightIn(min = MaeveSize.Composer).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

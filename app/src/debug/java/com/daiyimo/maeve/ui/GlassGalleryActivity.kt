package com.daiyimo.maeve.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.ui.components.GlassBar
import com.daiyimo.maeve.ui.components.TopBar
import com.daiyimo.maeve.ui.components.cardSurface
import com.daiyimo.maeve.ui.components.glassSource
import com.daiyimo.maeve.ui.theme.AccentPreset
import com.daiyimo.maeve.ui.theme.GlassDensity
import com.daiyimo.maeve.ui.theme.GlassTier
import com.daiyimo.maeve.ui.theme.LocalGlassBackdrop
import com.daiyimo.maeve.ui.theme.MaeveBackdrop
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType
import com.daiyimo.maeve.ui.theme.deriveAccent

/** Debug-only real-raster gallery used by the emulator screenshot pipeline. */
class GlassGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val mode = intent.getStringExtra("mode") ?: "top"
        val dark = intent.getBooleanExtra("dark", false)
        setContent {
            MaeveTheme(
                dark = dark,
                accent = deriveAccent(AccentPreset.Reverie),
                glassDensity = GlassDensity.Balanced,
            ) {
                GlassGallery(mode)
            }
        }
    }
}

@Composable
private fun GlassGallery(mode: String) {
    MaeveBackdrop {
        val colors = MaeveTheme.colors
        val backdrop = LocalGlassBackdrop.current
        Box(Modifier.fillMaxSize()) {
            // The source is a sibling of every glass surface, never its ancestor.
            Box(
                Modifier.fillMaxSize()
                    .glassSource(backdrop)
                    .drawBehind {
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(
                                    colors.accent.copy(alpha = 0.42f),
                                    colors.okSoft.copy(alpha = 0.78f),
                                    colors.warnSoft.copy(alpha = 0.72f),
                                    colors.accentSoft.copy(alpha = 0.58f),
                                ),
                            ),
                        )
                        val step = 28.dp.toPx()
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(
                                color = colors.ink.copy(alpha = 0.10f),
                                start = Offset(x, 0f),
                                end = Offset(x + size.height, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                            x += step
                        }
                        drawCircle(
                            color = colors.accent.copy(alpha = 0.20f),
                            radius = size.minDimension * 0.34f,
                            center = Offset(size.width * 0.82f, size.height * 0.18f),
                        )
                        drawCircle(
                            color = colors.ink.copy(alpha = 0.06f),
                            radius = size.minDimension * 0.28f,
                            center = Offset(size.width * 0.18f, size.height * 0.78f),
                        )
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 92.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("REFRACTION STUDY", style = MaeveType.Eyebrow, color = colors.accent)
                    Text("让边缘弯起光线", style = MaeveType.Title, color = colors.ink)
                    Text("中心保留细节，边缘产生位移、色散与镜面高光。", style = MaeveType.Body, color = colors.ink2)
                    repeat(5) { index ->
                        Box(
                            Modifier.fillMaxWidth().height(116.dp).background(
                                Brush.horizontalGradient(
                                    listOf(
                                        colors.accent.copy(alpha = 0.58f),
                                        colors.okSoft.copy(alpha = 0.78f),
                                        colors.warnSoft.copy(alpha = 0.72f),
                                        colors.accentSoft.copy(alpha = 0.62f),
                                    ),
                            ),
                            if (index % 2 == 0) MaeveShapes.Card else CircleShape,
                        ).padding(18.dp),
                        ) {
                            Column {
                                Text("0${index + 1}", style = MaeveType.Eyebrow, color = colors.ink)
                                Spacer(Modifier.height(8.dp))
                                Text("真实背景采样 · 低磨砂 · 强折射", style = MaeveType.BodyStrong, color = colors.ink)
                                Spacer(Modifier.height(6.dp))
                                Text("Chrom dispersion / Fresnel rim / directional specular", style = MaeveType.Caption, color = colors.ink2)
                            }
                        }
                    }
                }
            }

            when (mode) {
                "composer" -> Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    GlassBar(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("想做什么？交给 Maeve…", Modifier.weight(1f), style = MaeveType.Body, color = colors.ink3)
                        Icon(MaeveIcons.Up, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    }
                }
                "sheet" -> Box(
                    Modifier.align(Alignment.Center).width(330.dp).height(520.dp)
                        .cardSurface(colors, RoundedCornerShape(28.dp), GlassTier.Sheet)
                        .padding(22.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("对话列表", style = MaeveType.Title, color = colors.ink)
                        Text("Sheet 档折射玻璃", style = MaeveType.Caption, color = colors.ink3)
                        repeat(4) { index ->
                            Box(
                                Modifier.fillMaxWidth().height(64.dp)
                                    .cardSurface(colors, MaeveShapes.Button, GlassTier.Card)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text("会话 0${index + 1} · 背景从边缘弯折", style = MaeveType.Body, color = colors.ink)
                            }
                        }
                    }
                }
                else -> TopBar(
                    title = "液态玻璃",
                    subtitle = "真实背景采样 · Impeller / RuntimeShader",
                    brand = true,
                    glass = true,
                )
            }
        }
    }
}

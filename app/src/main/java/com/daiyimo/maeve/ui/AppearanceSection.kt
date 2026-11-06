package com.daiyimo.maeve.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.ui.components.GroupCard
import com.daiyimo.maeve.ui.components.Hairline
import com.daiyimo.maeve.ui.components.ListRow
import com.daiyimo.maeve.ui.theme.ACCENT_CUSTOM
import com.daiyimo.maeve.ui.theme.ACCENT_WALLPAPER
import com.daiyimo.maeve.ui.theme.AccentPreset
import com.daiyimo.maeve.ui.theme.AccentSpec
import com.daiyimo.maeve.ui.theme.MaeveAccentStore
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MikaType

/**
 * 外观 › 配色:六套预设 + 跟随壁纸。
 *
 * 六套预设取名来自《西部世界》的意象(见 [AccentPreset] 的注释)。每套只定种子,
 * 其余色位由 OKLCH 现推——选预设不是选一张皮,是换一整套色调关系。
 * 换色当场生效:存进 [MaeveAccentStore] 时广播,主题收广播,界面不重建。
 */
@Composable
fun AccentPickerCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { MaeveAccentStore.get(context) }
    val spec by store.accent.collectAsState()
    val haptics = MaeveTheme.haptics
    val c = MaeveTheme.colors
    GroupCard(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.accent_title), style = MikaType.BodyStrong, color = c.ink)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Text(accentName(spec), style = MikaType.Caption, color = c.ink3)
                }
            }
            Text(
                stringResource(R.string.accent_sub),
                Modifier.padding(top = 3.dp),
                style = MikaType.Caption, color = c.ink3,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AccentPreset.entries.forEach { preset ->
                    AccentSwatch(
                        preset = preset,
                        selected = spec.id == preset.id,
                        onClick = {
                            if (spec.id != preset.id) {
                                haptics.tick()
                                store.setPreset(preset)
                            }
                        },
                    )
                }
            }
        }
        // 跟随壁纸:只借系统强调色的色相,明度和对比度仍是 Maeve 自己的(API 31+ 才有系统强调色)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Hairline()
            ListRow(
                icon = MaeveIcons.Photo,
                title = stringResource(R.string.accent_wallpaper_title),
                subtitle = stringResource(R.string.accent_wallpaper_sub),
                onClick = { if (spec.id != ACCENT_WALLPAPER) { haptics.tick(); store.setWallpaper() } },
                trailing = {
                    Switch(
                        checked = spec.id == ACCENT_WALLPAPER,
                        onCheckedChange = { on ->
                            if (on) { haptics.tick(); store.setWallpaper() } else store.setPreset(AccentPreset.Reverie)
                        },
                    )
                },
            )
        }
    }
}

/** 一枚配色:渐变圆(浅种子 → 深种子),选中时一圈强调色描边 + 对勾。 */
@Composable
private fun AccentSwatch(preset: AccentPreset, selected: Boolean, onClick: () -> Unit) {
    val c = MaeveTheme.colors
    val light = Color(preset.lightSeed)
    val dark = Color(preset.darkSeed)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp)
                .then(
                    if (selected) Modifier.border(2.5.dp, c.accent, CircleShape).padding(3.5.dp)
                    else Modifier.padding(5.5.dp),
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(light, dark)))
                .clickable(role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                // 对勾垫一小片暗底:渐变两端明度差得远(石墨尤其),不垫底会有一半看不见
                Box(Modifier.size(20.dp).background(Color.Black.copy(alpha = 0.30f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(MaeveIcons.Check, null, Modifier.size(13.dp), tint = Color.White)
                }
            }
        }
        Text(
            stringResource(preset.labelRes),
            Modifier.padding(top = 5.dp),
            fontSize = 11.sp,
            color = if (selected) c.ink else c.ink3,
        )
    }
}

/** 当前配色的名字:预设按 id 取,自定义/壁纸有专门的说法。 */
@Composable
fun accentName(spec: AccentSpec): String = when (spec.id) {
    ACCENT_WALLPAPER -> stringResource(R.string.accent_wallpaper)
    ACCENT_CUSTOM -> stringResource(R.string.accent_custom)
    else -> stringResource(AccentPreset.byId(spec.id).labelRes)
}

/** 六套预设的名字(各语言共用中文名:它们是《西部世界》的意象,不是普通文案)。 */
val AccentPreset.labelRes: Int
    @StringRes get() = when (this) {
        AccentPreset.Reverie -> R.string.accent_reverie
        AccentPreset.Dolores -> R.string.accent_dolores
        AccentPreset.Maze -> R.string.accent_maze
        AccentPreset.Pearl -> R.string.accent_pearl
        AccentPreset.Sublime -> R.string.accent_sublime
        AccentPreset.Graphite -> R.string.accent_graphite
    }

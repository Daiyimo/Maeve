package com.daiyimo.maeve.ui

import com.daiyimo.maeve.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.ui.components.MaeveButton
import com.daiyimo.maeve.ui.components.PageBackground
import com.daiyimo.maeve.ui.components.ProgressDots
import com.daiyimo.maeve.ui.theme.MaeveMark
import com.daiyimo.maeve.ui.theme.MaeveType
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MaeveTheme

/**
 * 上手第一步。
 *
 * 版式学书籍扉页,不学落地页:一个大到几乎占满上半屏的**迷宫**,一行品牌小字,
 * 一句标题,一段导语;往下是她能做哪几类事——排成编号清单,和对话页的「从这里开始」
 * 同一套表格式。不做功能卡、不做图标网格、不做渐变按钮。
 *
 * 为什么敢留这么大白:这一页一辈子只看一次,它的任务是让人记住「这个 App 长这样」,
 * 而不是把四张卡塞满。
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val c = MaeveTheme.colors
    PageBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // 她醒着：日冕一呼一吸。这是全 App 唯一一处「大得没必要」的标志。
                    MaeveMark(
                        108.dp,
                        Modifier.testTag("welcome-mark"),
                        breathing = true,
                        coronaColor = c.accent,
                        darkColor = c.surfaceHigh,
                    )
                    Text(
                        "MAEVE",
                        Modifier.padding(top = 22.dp),
                        style = MaeveType.Eyebrow.copy(letterSpacing = 6.sp, fontSize = 10.sp),
                        color = c.ink3,
                    )
                    Text(
                        WelcomeCopy.TITLE, Modifier.padding(top = 14.dp),
                        style = MaeveType.Display, color = c.ink, textAlign = TextAlign.Center,
                    )
                    Text(
                        WelcomeCopy.LEAD, Modifier.padding(top = 12.dp),
                        style = MaeveType.Body, color = c.ink2, textAlign = TextAlign.Center,
                    )
                }
                Column(Modifier.padding(top = 38.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("她能做到的", style = MaeveType.Eyebrow.copy(letterSpacing = 2.sp), color = c.ink3)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).height(0.5.dp).background(c.hair))
                    }
                    WelcomeCopy.FEATURES.forEachIndexed { i, f -> Feature(i + 1, f.icon, f.title, f.body) }
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 14.dp)) {
                ProgressDots(Onboarding.DOTS, Onboarding.dot(OnboardingStep.Welcome), Modifier.padding(top = 4.dp))
                Text(
                    WelcomeCopy.NOTE, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
                    style = MaeveType.Hint, color = c.ink3, textAlign = TextAlign.Center,
                )
                MaeveButton(WelcomeCopy.START, onStart, Modifier.fillMaxWidth().testTag("welcome-start"))
            }
        }
    }
}

/**
 * 编号 → 图标 → 标题+说明。编号是等宽的,和对话页的分场表、设置页的分组标题同一个口音。
 */
@Composable
private fun Feature(index: Int, icon: ImageVector, title: String, body: String) {
    val c = MaeveTheme.colors
    Column {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.hair))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                index.toString().padStart(2, '0'), Modifier.width(24.dp).padding(top = 2.dp),
                style = MaeveType.Turn, color = c.ink3,
            )
            Icon(icon, null, Modifier.padding(top = 1.dp).size(16.dp), tint = c.ink2)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaeveType.BodyStrong, color = c.ink)
                Text(body, Modifier.padding(top = 3.dp), style = MaeveType.Caption, color = c.ink2)
            }
        }
    }
}

/** 欢迎页的文字。 */
object WelcomeCopy {
    val TITLE: String get() = tr(R.string.welcome_title)
    val LEAD: String get() = tr(R.string.welcome_lead)
    val NOTE: String get() = tr(R.string.welcome_note)
    val START: String get() = tr(R.string.welcome_start)

    data class Item(val icon: ImageVector, val title: String, val body: String)

    val FEATURES get() = listOf(
        Item(MaeveIcons.Apps, tr(R.string.welcome_apps), tr(R.string.welcome_apps_body)),
        Item(MaeveIcons.Globe, tr(R.string.welcome_web), tr(R.string.welcome_web_body)),
        Item(MaeveIcons.Doc, tr(R.string.welcome_files), tr(R.string.welcome_files_body)),
        Item(MaeveIcons.Clock, tr(R.string.welcome_schedule), tr(R.string.welcome_schedule_body)),
    )
}

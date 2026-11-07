package com.daiyimo.maeve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.ui.theme.GlassTier
import com.daiyimo.maeve.ui.components.glassFill
import com.daiyimo.maeve.ui.components.PageBackground
import com.daiyimo.maeve.ui.components.TopBar
import com.daiyimo.maeve.ui.theme.MaeveMark
import com.daiyimo.maeve.ui.theme.MaeveType
import com.daiyimo.maeve.ui.theme.MazeRing
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.NoteState
import com.daiyimo.maeve.ui.theme.PianoRoll
import com.daiyimo.maeve.ui.theme.calibrationCorners

/**
 * 视觉验收页(`adb shell am start -n com.daiyimo.maeve/.ui.MainActivity --es demo 1`)。
 *
 * 这个 App 跑真任务需要模型和手机权限,装机验收时很难把每个状态都凑齐,所以把
 * 几个最有代表性的视觉状态做成一页静态样板:**不连模型、不发请求、不碰手机**。
 * 只为对着屏幕看颜色和排版是否成立,发布版里保留也无害(入口只有这条 extra)。
 */
@Composable
fun VisualSpecScreen(onBack: () -> Unit) {
    val c = MaeveTheme.colors
    PageBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 18.dp)
                    .padding(top = 60.dp, bottom = 24.dp),
            ) {
                // ---- 1. 日食 ----
                SpecLabel("01", "标志 · ECLIPSE")
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    MaeveMark(96.dp, breathing = true, coronaColor = c.accent, darkColor = c.surfaceHigh)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (s in listOf(44, 32, 24, 18, 14)) {
                        MaeveMark(s.dp, coronaColor = c.ink, darkColor = c.surfaceHigh)
                    }
                    Spacer(Modifier.weight(1f))
                    MazeRing(progress = null, size = 18.dp, track = c.machineEdge, head = c.machine, indeterminate = true)
                    MazeRing(progress = 0.62f, size = 18.dp, track = c.hair, head = c.machine)
                }

                // ---- 2. 诊断条 ----
                SpecLabel("02", "诊断条 · CRADLE")
                DiagSample()
                DiagSample(paused = true)

                // ---- 3. 台词轨 ----
                SpecLabel("03", "台词轨 · SCRIPT RAIL")
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        "订明天下午三点去上海的高铁，二等座靠窗",
                        Modifier.clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                            .background(c.accentBubble)
                            .padding(horizontal = 13.dp, vertical = 10.dp),
                        style = MaeveType.Body.copy(color = c.onAccentBubble),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(1.dp).height(46.dp).background(c.rule))
                    Text(
                        "找到 3 趟符合的车次，最近的一趟 15:07 发车，余票充足。要现在下单吗？",
                        Modifier.padding(start = 15.dp),
                        style = MaeveType.Script, color = c.ink,
                    )
                }

                // ---- 4. 需要你 ----
                SpecLabel("04", "需要你 · NEEDS YOU")
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp))
                        .glassFill(c, RoundedCornerShape(10.dp), GlassTier.Card),
                ) {
                    Box(
                        Modifier.matchParentSize().background(Color.Transparent),
                    )
                    Box(Modifier.width(3.dp).height(96.dp).background(c.signal))
                    Column(Modifier.padding(start = 17.dp, top = 14.dp, bottom = 14.dp, end = 16.dp)) {
                        Text("NEEDS YOU", style = MaeveType.EyebrowSmall.copy(letterSpacing = 1.6.sp), color = c.signal)
                        Text("需要确认付款", Modifier.padding(top = 6.dp), style = MaeveType.BodyStrong, color = c.ink)
                        Text("12306 订单 ¥553.00，付完就把取票号发你。", Modifier.padding(top = 4.dp), style = MaeveType.Caption, color = c.ink2)
                    }
                }

                // ---- 5. 仪表卡 ----
                SpecLabel("05", "仪表卡 · CALIBRATION")
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.machineSoft)
                        .border(0.5.dp, c.machineEdge, RoundedCornerShape(10.dp))
                        .calibrationCorners(c.machine.copy(alpha = 0.45f), arm = 6.dp, inset = 7.dp)
                        .padding(15.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MazeRing(progress = null, size = 12.dp, track = c.machineEdge, head = c.machine)
                        Spacer(Modifier.width(7.dp))
                        Text("SYSTEM", Modifier.weight(1f), style = MaeveType.EyebrowSmall.copy(letterSpacing = 1.6.sp), color = c.machine)
                        Text("READY", style = MaeveType.EyebrowSmall.copy(letterSpacing = 1.6.sp), color = c.machine)
                    }
                    Text("一切就绪", Modifier.padding(top = 9.dp), style = MaeveType.BodyStrong, color = c.ink)
                    Text("模型、手机操作、通知都已就绪", Modifier.padding(top = 3.dp), style = MaeveType.Caption, color = c.ink2)
                }

                // ---- 6. 字阶 ----
                SpecLabel("06", "字阶 · TYPE")
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Display 30", style = MaeveType.Display, color = c.ink)
                    Text("LargeTitle 24 晚上好", style = MaeveType.LargeTitle, color = c.ink)
                    Text("Body 15.5 — 让 Maeve 替你把这条路走完", style = MaeveType.Body, color = c.ink)
                    Text("Script 15.5 — 她说的每一句话都排在这一栏里", style = MaeveType.Script, color = c.ink2)
                    Text("EYEBROW 10.5 · CRADLE RUNNING", style = MaeveType.Eyebrow.copy(letterSpacing = 1.7.sp), color = c.ink3)
                    Text("等宽中文 11.5 · 语言与外观", style = MaeveType.eyebrowFor("语言与外观"), color = c.ink3)
                    Text("Mono 12 · 00:42 · 12,438 tk · v0.1.0-dolores", style = MaeveType.Mono, color = c.ink2)
                    Text("Numeral 22 · 12,438", style = MaeveType.Numeral, color = c.ink)
                }
            }
            TopBar(
                "视觉验收", left = MaeveIcons.Back, leftLabel = "返回", onLeft = onBack,
                eyebrow = "VISUAL SPEC", brand = true,
            )
        }
    }
}

@Composable
private fun SpecLabel(num: String, text: String) {
    val c = MaeveTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(num, style = MaeveType.Turn, color = c.ink3)
        Text(text, style = MaeveType.eyebrowFor(text).copy(letterSpacing = 1.7.sp), color = c.ink3)
        Box(Modifier.weight(1f).height(0.5.dp).background(c.hair))
    }
}

/** 一块诊断条样板,给验收页用。 */
@Composable
private fun DiagSample(paused: Boolean = false) {
    val c = MaeveTheme.colors
    val tone = if (paused) c.signal else c.machine
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.machineSoft)
            .border(0.5.dp, c.machineEdge, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MazeRing(progress = null, size = 13.dp, track = c.machineEdge, head = tone, strokeWidth = 1.1.dp, indeterminate = !paused)
            Spacer(Modifier.width(7.dp))
            Text(
                if (paused) "CRADLE · HOLDING" else "CRADLE · RUNNING",
                Modifier.weight(1f),
                style = MaeveType.EyebrowSmall.copy(letterSpacing = 1.5.sp),
                color = tone,
            )
            Text(if (paused) "01:12" else "00:42", style = MaeveType.MonoSmall, color = tone)
        }
        Spacer(Modifier.height(9.dp))
        PianoRoll(
            listOf(
                NoteState.Done, NoteState.Done, NoteState.Done, NoteState.Done, NoteState.Done,
                NoteState.Done, NoteState.Done, NoteState.Active,
                NoteState.Pending, NoteState.Pending, NoteState.Pending, NoteState.Pending,
            ),
            Modifier.fillMaxWidth(),
            height = 15.dp,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            if (paused) "在 12306 等你确认订单" else "正在 12306 上筛选 15:00 后的车次",
            style = MaeveType.Body.copy(fontSize = 15.sp), color = c.ink,
        )
    }
}

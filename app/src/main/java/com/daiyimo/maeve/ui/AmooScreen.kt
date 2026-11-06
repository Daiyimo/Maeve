package com.daiyimo.maeve.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.daiyimo.maeve.MikaApp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.intercept.AmooBridge
import com.daiyimo.maeve.intercept.AmooSetup
import com.daiyimo.maeve.settings.FeatureFlags
import com.daiyimo.maeve.ui.components.GroupCard
import com.daiyimo.maeve.ui.components.ListRow
import com.daiyimo.maeve.ui.components.PageScaffold
import com.daiyimo.maeve.ui.components.TileTone
import com.daiyimo.maeve.ui.theme.MikaIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Amoo 接入。这一页只留两件事:一键接入(把技能发进 amoo 聊天)和文件桥状态。
 * 注意:amoo 走的是 /sdcard/Android/media/<包名>/amoo 这个共享目录,不经网络,所以状态看的是
 * 文件桥有没有开、最近一次请求是什么时候——不是「电脑连接」(那是给电脑/MCP 用的另一条路)。
 */
@Composable
fun AmooScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val flags = remember { FeatureFlags.get(context) }
    var bridgeOn by remember { mutableStateOf(flags.bridgeAmoo()) }
    var lastAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { flags.bridgeAmooChanges().collect { bridgeOn = it } }
    LaunchedEffect(bridgeOn) {
        if (!bridgeOn) { lastAt = null; return@LaunchedEffect }
        while (true) {
            lastAt = withContext(Dispatchers.IO) { newestResultAt(context) }
            delay(2_000)
        }
    }
    val detail = when {
        !bridgeOn -> stringResource(R.string.amoo_status_off)
        lastAt == null -> stringResource(R.string.amoo_status_listening)
        else -> stringResource(R.string.amoo_status_connected, clock(lastAt!!))
    }
    PageScaffold(stringResource(R.string.amoo_page_title), onBack) {
        AmooSetupCard()
        GroupCard {
            ListRow(
                if (bridgeOn) MikaIcons.Link else MikaIcons.Info,
                stringResource(R.string.amoo_status_title),
                detail,
                first = true,
                tone = if (bridgeOn) TileTone.Ok else TileTone.Neutral,
            )
        }
    }
}

/** 共享目录里最近一次结果的落盘时间;目录还没建出来就是 null。 */
private fun newestResultAt(context: Context): Long? {
    val media = context.getExternalMediaDirs().firstOrNull() ?: return null
    val out = File(File(media, AmooBridge.DIR), AmooBridge.OUT)
    return out.listFiles()?.maxOfOrNull { it.lastModified() }
}
private fun clock(ms: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/**
 * 一键接入:技能只能由 amoo 自己写进沙箱,所以这里替用户把说明「打字 + 发送」进 amoo 聊天框。
 * 点一下之后切到 amoo 聊天即可,进度看下面那行(见 AmooSetup)。
 */

@Composable
private fun AmooSetupCard() {
    val context = LocalContext.current
    val setup = (context.applicationContext as MikaApp).amooSetup
    val stage by setup.stage.collectAsState()
    GuideStep(stringResource(R.string.amoo_setup_step), stringResource(R.string.amoo_setup_sub),
        stringResource(R.string.amoo_setup_button), { setup.start() })
    val line = when (stage) {
        AmooSetup.Stage.Idle -> null
        AmooSetup.Stage.Waiting -> R.string.amoo_setup_waiting
        AmooSetup.Stage.Sending, AmooSetup.Stage.Verifying -> R.string.amoo_setup_sending
        AmooSetup.Stage.Done -> R.string.amoo_setup_done
        AmooSetup.Stage.Failed -> R.string.amoo_setup_failed
    }
    if (line != null) {
        GroupCard {
            ListRow(
                if (stage == AmooSetup.Stage.Done) MikaIcons.Check else MikaIcons.Info,
                stringResource(line),
                first = true,
                tone = if (stage == AmooSetup.Stage.Done) TileTone.Ok else TileTone.Neutral,
            )
        }
    }
}

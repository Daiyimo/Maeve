package com.daiyimo.maeve.ui

import androidx.compose.foundation.gestures.scrollBy
import com.daiyimo.maeve.ui.components.glassFill
import com.daiyimo.maeve.core.agent.QuoteMessage
import com.daiyimo.maeve.core.record.ConversationTokens
import com.daiyimo.maeve.core.cap.web.AttachedPage
import com.daiyimo.maeve.ui.components.DirectionalIcon
import com.daiyimo.maeve.ui.components.keepsKeyboard
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.cap.web.KeptPage
import com.daiyimo.maeve.chat.SharedFile
import com.daiyimo.maeve.confirm.ConfirmationCopy
import com.daiyimo.maeve.settings.Prerequisites
import com.daiyimo.maeve.takeover.Takeover
import com.daiyimo.maeve.task.SteerNote
import com.daiyimo.maeve.task.SteerState
import com.daiyimo.maeve.task.TaskService
import com.daiyimo.maeve.task.unconsumed
import com.daiyimo.maeve.ui.components.BarIconButton
import com.daiyimo.maeve.ui.components.ButtonKind
import com.daiyimo.maeve.ui.components.ButtonSize
import com.daiyimo.maeve.ui.components.GlassBackdrop
import com.daiyimo.maeve.ui.components.GlassBar
import com.daiyimo.maeve.ui.components.MaeveButton
import com.daiyimo.maeve.ui.components.MaeveCard
import com.daiyimo.maeve.ui.components.PageBackground
import com.daiyimo.maeve.ui.components.TopBar
import com.daiyimo.maeve.ui.components.cardSurface
import com.daiyimo.maeve.ui.components.floatShadow
import com.daiyimo.maeve.ui.components.glassSource
import com.daiyimo.maeve.ui.theme.GlassTier
import com.daiyimo.maeve.ui.theme.LocalGlassBackdrop
import com.daiyimo.maeve.ui.theme.MaeveMark
import com.daiyimo.maeve.ui.theme.MaeveType
import com.daiyimo.maeve.ui.theme.MazeRing
import com.daiyimo.maeve.ui.theme.NoteState
import com.daiyimo.maeve.ui.theme.PianoRoll
import com.daiyimo.maeve.ui.theme.MaeveIcons
import com.daiyimo.maeve.ui.theme.MarkState
import com.daiyimo.maeve.ui.theme.MaeveMotion
import com.daiyimo.maeve.ui.theme.MaeveShapes
import com.daiyimo.maeve.ui.theme.MaeveSize
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.viewer.FileViewer
import com.daiyimo.maeve.voice.HoldToTalk
import com.daiyimo.maeve.voice.VoiceSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime

/**
 * 对话页:顶栏(左边打开侧栏、中间对话标题和此刻在做什么、右边新对话),对话在顶栏和玻璃输入栏之间滚动。
 * 进行中是一行状态加一条竖线步骤,不套卡片;要你处理时停在当前步,下面一张白卡;完成后过程收成一行,回答直接排在背景上。
 * Agent 的虚拟屏以悬浮小窗([AgentPip])盖在对话上,不占布局。
 */
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    check: Prerequisites.Check,
    frames: StateFlow<Bitmap?>,
    webOpen: StateFlow<Boolean>,
    reduceMotion: Boolean,
    /** 左上角菜单:打开侧栏。 */
    onOpenDrawer: () -> Unit,
    /** 被拦下的提示里的「去设置」。 */
    openSettings: () -> Unit,
    openRun: (String) -> Unit = {},
    /** 任务要用手机而手机操作没准备好:最后一轮下面放一行去设置。 */
    phoneSetupNeeded: Boolean = false,
    openPhoneSetup: () -> Unit = {},
    /** 按运行记录取依据的来处(「来源」胶囊和点开后的截图);读盘的活由调用方放到后台线程。 */
    loadSource: suspend (runId: String) -> EvidenceSource? = { null },
    openSource: (runId: String?, legacyText: String?) -> Unit = { _, _ -> },
    /** 输入栏按住说话从这里开会话;null 时只能打字。 */
    voice: VoiceSource? = null,
    /** 「还不能语音输入」里的「去选」。 */
    openSpeechModel: () -> Unit = {},
) {
    val ui by vm.ui.collectAsState()
    val others by vm.otherRunning.collectAsState()
    val keptAny by vm.keptPage.collectAsState()
    // 只管这个对话留下的那一页
    val kept = keptAny?.takeIf { it.conversationId == ui.conversationId }
    val live by frames.collectAsState()
    val webLive by webOpen.collectAsState()
    // 任务一结束虚拟屏就释放了;完成时小窗还要停留几秒打勾,继续显示最后一帧
    var lastFrame by remember { mutableStateOf<Bitmap?>(null) }
    var lastFrameWasWeb by remember { mutableStateOf(false) }
    LaunchedEffect(live, webLive, ui.pip.mode) {
        live?.let {
            lastFrame = it
            if (webLive) lastFrameWasWeb = true
            else if (ui.pip.mode != PipMode.Done) lastFrameWasWeb = false
        }
    }
    val frame = live ?: lastFrame.takeIf { ui.pip.visible && ui.pip.mode == PipMode.Done }
    val webContent = if (ui.pip.mode == PipMode.Done) lastFrameWasWeb else webLive
    var input by rememberSaveable { mutableStateOf("") }
    var voiceHolding by remember { mutableStateOf(false) }
    var editRequest by remember { mutableIntStateOf(0) }
    var composerHeight by rememberSaveable { mutableIntStateOf(0) }
    var topHeight by rememberSaveable { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 只有原本跟着最新消息时才继续跟随。这个状态与列表位置一起跨页面保存。
    var followingTail by rememberSaveable { mutableStateOf(true) }
    var previousScrollMarker by rememberSaveable { mutableStateOf<String?>(null) }
    var previousConversation by rememberSaveable { mutableStateOf<String?>(null) }
    val tailThresholdPx = with(density) { 240.dp.roundToPx() }
    LaunchedEffect(listState, tailThresholdPx) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress to listState.nearTail(tailThresholdPx) }.collect { (scrolling, nearTail) ->
            if (scrolling || wasScrolling) followingTail = nearTail
            wasScrolling = scrolling
        }
    }

    // 返回聊天时同一条内容不重新滚动；新进展仅在跟随末尾时滚动。
    val last = ui.exchanges.lastOrNull()
    val scrollMarker = "${ui.conversationId}:${ui.exchanges.size}:${last?.steps?.size}:${last?.steers?.size}:${last?.status?.hashCode()}:${ui.blocked?.hashCode()}"
    LaunchedEffect(scrollMarker) {
        if (previousScrollMarker == scrollMarker) return@LaunchedEffect
        val firstEntry = previousScrollMarker == null
        val changedConversation = previousConversation != ui.conversationId
        previousScrollMarker = scrollMarker
        previousConversation = ui.conversationId
        if (changedConversation) followingTail = true
        if (!firstEntry && !changedConversation && !followingTail) return@LaunchedEffect
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) {
            if (firstEntry || changedConversation || reduceMotion) listState.scrollToItem(count - 1)
            else listState.animateScrollToItem(count - 1)
        }
    }
    // 流式文字增长时跟住底部；用户主动向上翻看过程时不拉回去。
    LaunchedEffect(last?.draft?.length) {
        if (last?.draft.isNullOrEmpty() || !followingTail || listState.isScrollInProgress) return@LaunchedEffect
        val layout = listState.layoutInfo
        val end = layout.totalItemsCount - 1
        val draft = layout.visibleItemsInfo.firstOrNull { it.index == end } ?: return@LaunchedEffect
        val tailGap = draft.offset + draft.size - layout.viewportEndOffset
        if (tailGap > with(density) { 240.dp.roundToPx() }) return@LaunchedEffect
        val viewport = layout.viewportEndOffset - layout.viewportStartOffset
        val offset = (draft.size - viewport + with(density) { 12.dp.roundToPx() }).coerceAtLeast(0)
        listState.scrollToItem(end, offset)
    }

    // 输入栏变高(多半是输入法弹起,也可能是附件、多行输入):原本跟着最新消息时,对话流一起往上顶同样的距离,
    // 最后一条仍贴着输入栏;用户正往上翻看时不动。变矮时列表到底自己会收回来。
    var lastComposerHeight by remember { mutableIntStateOf(composerHeight) }
    LaunchedEffect(composerHeight) {
        val grown = composerHeight - lastComposerHeight
        lastComposerHeight = composerHeight
        if (grown > 0 && followingTail && !listState.isScrollInProgress) listState.scrollBy(grown.toFloat())
    }

    // 已结束各轮的运行小结:每轮结束那一行写这一轮的 token,顶栏闲着时写这个对话合起来的
    val endedRuns = ui.exchanges.filter { !it.status.isActive() }.mapNotNull { it.runId }
    val runMetrics = rememberRunMetrics(endedRuns)

    val c = MaeveTheme.colors
    // 玻璃运行时由页面的 MaeveBackdrop 提供:列表录实时内容,live 层给顶栏和输入栏取样
    val backdrop = LocalGlassBackdrop.current
    val context = LocalContext.current
    /** 长按了的那条消息;菜单开着(收起的动画放完才清掉)。 */
    var menu by remember { mutableStateOf<MessageTarget?>(null) }
    /** 选了「选择文字」的那条:它换回系统的长按选中;换对话时清掉。 */
    var selecting by remember(ui.conversationId) { mutableStateOf<String?>(null) }
    /** 菜单出现的进度:0 是没有,1 是完全出来;关了动画时直接到位。 */
    val lift = remember { Animatable(0f) }
    LaunchedEffect(menu) {
        if (menu != null) {
            if (reduceMotion) lift.snapTo(1f) else lift.animateTo(1f, tween(MENU_IN_MS, easing = MaeveMotion.Decelerate))
        }
    }
    /** 收起菜单:倒着放一遍出现的动画,放完再清掉;[then] 当场做。 */
    fun closeMenu(then: () -> Unit = {}) {
        then()
        scope.launch {
            if (reduceMotion) lift.snapTo(0f) else lift.animateTo(0f, tween(MENU_OUT_MS, easing = MaeveMotion.Decelerate))
            menu = null
        }
    }
    val menuHost = remember(selecting) { MessageMenuHost(selecting) { menu = it } }
    val menuOpen = menu != null
    PageBackground {
        CompositionLocalProvider(LocalMessageMenu provides menuHost) {
            LazyColumn(
                Modifier.fillMaxSize().behindMenu(menuOpen) { lift.value }.voiceDim(voiceHolding, reduceMotion).glassSource(backdrop),
                state = listState,
                contentPadding = with(density) {
                    PaddingValues(
                        start = 16.dp, end = 16.dp, top = topHeight.toDp() + 4.dp, bottom = composerHeight.toDp() + 12.dp,
                    )
                },
            ) {
                if (ui.exchanges.isEmpty() && ui.blocked == null) {
                    item("empty") { EmptyState(ui.conversationId) { input = it } }
                }
                ui.exchanges.forEachIndexed { i, ex ->
                    val backgroundNote = i == ui.exchanges.lastIndex && ui.operatingPhone
                    exchange(
                        i, ex, ui.conversationId, ui.takeoverStage, vm, reduceMotion, backgroundNote, openRun, loadSource, openSource, kept,
                        tokens = ConversationTokens.turn(ex.runId?.let(runMetrics::get)),
                        total = ui.exchanges.size,
                    )
                }
                if (phoneSetupNeeded && ui.exchanges.isNotEmpty()) item("phone-setup") { PhoneSetupLine(openPhoneSetup) }
                ui.blocked?.let { problems ->
                    item("blocked") {
                        NeedCard(
                            stringResource(R.string.chat_cannot_start),
                            stringResource(R.string.chat_missing, problems.joinToString(stringResource(R.string.list_sep)) { StatusCopy.banner(it).title }),
                        ) {
                            CardButton(stringResource(R.string.ok_got_it), onClick = vm::dismissBlocked)
                            CardButton(stringResource(R.string.chat_go_settings), primary = true, onClick = { vm.dismissBlocked(); openSettings() })
                        }
                    }
                }
            }
        }

        // 没有任务在跑、这个对话留着一页在后台运行时:顶栏写「后台保持着 1 个网页」;都没有时写这个对话用了多少 token
        // (对话合计只在最后这一行,不要它时删掉这一行)
        val sub: Pair<String, ChatCopy.Tone?>? = ChatCopy.subtitle(last, last?.let(vm::livePlace))
            ?: kept?.let { ChatCopy.KEPT_PAGE to ChatCopy.Tone.Ok }
            ?: ChatTokensCopy.conversationSubtitle(endedRuns, runMetrics)?.let { it to null }
        Column(Modifier.behindMenu(menuOpen) { lift.value }.onSizeChanged { topHeight = it.height }) {
            TopBar(
                ChatCopy.APP_TITLE,
                left = MaeveIcons.Menu, leftLabel = stringResource(R.string.chat_menu), onLeft = onOpenDrawer,
                // 别的对话里还有任务在跑(并行时):菜单上一个小圆点,侧栏「进行中」里能切过去
                leftBadge = others.isNotEmpty(),
                right = { BarIconButton(MaeveIcons.New, stringResource(R.string.chat_new), { vm.newConversation() }, enabled = vm.canSwitch()) },
                subtitle = sub?.first,
                subtitleColor = when (sub?.second) {
                    ChatCopy.Tone.Accent -> c.accent
                    ChatCopy.Tone.Warn -> c.warn
                    ChatCopy.Tone.Ok -> c.ok
                    null -> Color.Unspecified
                },
                brand = true,
                // 眉标：她会怎么开口，不是状态机术语（旧版写 READY / LOOP 03，像设备日志）
                eyebrow = ChatCopy.eyebrow(ui.exchanges),
            )
        }

        Composer(
            Modifier.align(Alignment.BottomCenter).behindMenu(menuOpen) { lift.value }.onSizeChanged { composerHeight = it.height },
            backdrop = backdrop,
            text = input,
            onText = { input = it },
            mode = ui.composer,
            hasHistory = ui.exchanges.isNotEmpty(),
            onSend = { if (vm.send(input, check)) input = "" },
            onStop = vm::cancel,
            attachPanel = if (ui.attachEnabled && ui.composer == ComposerMode.Idle) ({
                AttachPanelHost(ui.attachments, onPicked = vm::attach, onToggle = vm::toggleAttachment)
            }) else null,
            attachments = ui.attachments,
            importing = ui.importing,
            rejected = ui.rejectedAttachments,
            overLimit = ui.overLimit,
            reduceMotion = reduceMotion,
            onRemove = vm::removeAttachment,
            onDismissRejected = vm::dismissRejected,
            quote = ui.quote,
            onRemoveQuote = vm::clearQuote,
            voice = voice,
            // 说的话接在已有的字后面;任务在跑时发出去就是插话
            onVoiceText = { input = HoldToTalk.join(input, it) },
            onPickSpeech = openSpeechModel,
            steerTarget = last?.goal?.let(ChatCopy::plainTitle),
            onVoiceHolding = { voiceHolding = it },
            editRequest = editRequest,
        )

        AgentPip(
            pip = ui.pip,
            frame = frame,
            webContent = webContent,
            reduceMotion = reduceMotion,
            topInset = topHeight.toFloat(),
            bottomInset = composerHeight.toFloat(),
            onExpand = vm::expandPip,
            onCollapse = vm::collapsePip,
            // 关掉小画面:这个任务结束前只剩右下角的小按钮;提示一句还能去哪看
            onClose = {
                vm.closePip()
                Toast.makeText(context, context.getString(R.string.pip_closed_hint), Toast.LENGTH_SHORT).show()
            },
            onReopen = vm::reopenPip,
            onUnmute = vm::unmutePip,
            // 要你处理时点小窗:收起,滚到要你处理的那张卡片(在最后一问一答里,它后面只可能还有文件卡片和去设置那一行)
            onAttention = {
                vm.attendPip()
                val count = listState.layoutInfo.totalItemsCount
                val after = (if (last?.files?.isNotEmpty() == true) 1 else 0) + (if (phoneSetupNeeded) 1 else 0)
                val card = count - 1 - after
                if (card >= 0) scope.launch { if (reduceMotion) listState.scrollToItem(card) else listState.animateScrollToItem(card) }
            },
        )

        // 长按消息的菜单盖在最上面:复制纯文字;引用放到输入框上方;选择文字让那条换回系统的长按选中;
        // 已经答完的一轮还能从这里分支
        menu?.let { m ->
            val pending = withdrawableSteer(ui.exchanges, m.key)
            val canEdit = input.isBlank() && ui.quote == null && ui.attachments.isEmpty() && !ui.importing
            val branchTurn = vm.branchTurn(m.key)
            val retryTurn = vm.retryTurn(m.key)
            MessageMenu(
                m, progress = { lift.value },
                onCopy = { closeMenu { copyMessage(context, m.text) } },
                onQuote = { closeMenu { vm.quote(m.from, m.text) } },
                onSelect = {
                    closeMenu {
                        selecting = m.key
                        Toast.makeText(context, context.getString(R.string.chat_select_hint), Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { closeMenu() },
                onBranch = branchTurn?.let { turn -> {
                    closeMenu {
                        scope.launch {
                            val id = vm.branchFrom(turn)
                            val copy = vm.branchCopy.value
                            val message = when {
                                id == null -> R.string.chat_branch_failed
                                copy?.truncated == true -> R.string.chat_branch_attachments_skipped
                                else -> R.string.chat_branch_done
                            }
                            if (id != null) input = ""
                            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                            vm.clearBranchCopy()
                        }
                    }
                } },
                onBranchRetry = retryTurn?.let { turn -> {
                    closeMenu {
                        scope.launch {
                            val result = vm.branchBefore(turn)
                            val message = when {
                                result == null -> R.string.chat_branch_failed
                                result.needsReattach -> R.string.chat_branch_attachments_readd
                                vm.branchCopy.value?.truncated == true -> R.string.chat_branch_attachments_skipped
                                else -> R.string.chat_branch_done
                            }
                            if (result != null) input = result.prefill
                            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
                            vm.clearBranchCopy()
                        }
                    }
                } },
                onEdit = if (pending != null && canEdit && editableSteer(pending.text)) ({ closeMenu {
                    vm.editSteer(pending.id)?.let { input = it; editRequest++ }
                } }) else null,
                onWithdraw = pending?.let { n -> ({ closeMenu { vm.withdrawSteer(n.id) } }) },
            )
        }
    }
}

/** 最后一项可能是一段很长的答复，仅仅看到它的开头不代表用户正在看末尾。 */
private fun LazyListState.nearTail(thresholdPx: Int): Boolean {
    val layout = layoutInfo
    val end = layout.totalItemsCount - 1
    val tail = layout.visibleItemsInfo.firstOrNull { it.index == end } ?: return false
    return tail.offset + tail.size - layout.viewportEndOffset <= thresholdPx
}

// ---------- 空对话 ----------

/**
 * 空对话:一座呼吸的迷宫 + 一句问候 + 一份「可以让她做的事」的清单。
 *
 * 清单不做卡片格子,而是一份**分场表**:左边等宽编号、中间一句人话、右边一个箭头。
 * 看起来像剧本目录,读起来是一列能直接点的句子。点一条只填进输入框、不直接发送——
 * 你还有机会改。
 */
@Composable
private fun EmptyState(conversation: String?, onPick: (String) -> Unit) {
    val c = MaeveTheme.colors
    // 顶部留白加大：标志要单独占一块呼吸区，否则压着标题。
    Column(Modifier.fillMaxWidth().padding(top = 52.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // 她醒着：日冕在缓慢呼吸。系统关了动画时静止。
            MaeveMark(
                84.dp, coronaColor = c.accent, darkColor = c.surfaceHigh,
                state = MarkState.Armed,
            )
            Text(
                "MAEVE",
                Modifier.padding(top = 15.dp),
                style = MaeveType.Eyebrow.copy(letterSpacing = 5.sp, fontSize = 10.sp),
                color = c.ink3,
            )
            // 「说事。剩下的我来。」直接接在字标下面。
            // 原来这里是「MAEVE / 晚上好 / 一句提示」三层，中间那层时段问候
            // 把标题和正文隔开，读起来像两个不相干的东西。问候交给顶栏的「我在听」。
            Text(
                ChatCopy.EMPTY_PROMPT, Modifier.padding(top = 20.dp, start = 12.dp, end = 12.dp),
                style = MaeveType.Display, color = c.ink, textAlign = TextAlign.Center,
            )
        }
        Column(Modifier.padding(top = 34.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("从这里开始", style = MaeveType.Eyebrow.copy(letterSpacing = 2.sp), color = c.ink3)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(0.5.dp).background(c.hair))
            }
            ChatCopy.examples.forEachIndexed { i, ex ->
                PromptRow(i + 1, exampleIcon(ex.kind), ex.text) { onPick(ex.text) }
            }
        }
    }
}

private fun exampleIcon(kind: ChatCopy.ExampleKind): ImageVector = when (kind) {
    ChatCopy.ExampleKind.App -> MaeveIcons.Apps
    ChatCopy.ExampleKind.Web -> MaeveIcons.Globe
    ChatCopy.ExampleKind.File -> MaeveIcons.Doc
    ChatCopy.ExampleKind.Schedule -> MaeveIcons.Clock
    ChatCopy.ExampleKind.Device -> MaeveIcons.Alarm
}

/**
 * 分场表的一行:等宽编号 → 小图标 → 一句人话 → 箭头。
 * 行与行之间一道发丝线,像印刷品的目录,不用卡片。
 */
@Composable
private fun PromptRow(index: Int, icon: ImageVector, text: String, onClick: () -> Unit) {
    val c = MaeveTheme.colors
    val haptics = MaeveTheme.haptics
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                if (index == 1) return@drawBehind
                drawLine(c.hair, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 0.5.dp.toPx())
            }
            .clickable(role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString().padStart(2, '0'), Modifier.width(26.dp),
            style = MaeveType.Turn, color = c.ink3,
        )
        Icon(icon, null, Modifier.size(15.dp), tint = c.ink3)
        Text(
            text, Modifier.weight(1f).padding(start = 12.dp, end = 10.dp),
            style = ExampleText, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        DirectionalIcon(MaeveIcons.Chevron, null, Modifier.size(13.dp), tint = c.ink3)
    }
}

// ---------- 一问一答 ----------

/**
 * 一幕的开头:**幕轨 + 幕号**。
 *
 * 每一轮对话是一幕。这一幕的所有内容(你的台词、诊断条、她的唱段、状态卡)都挂在同一条
 * 竖轨上,轨的最上面挂着 `01` 这样的等宽幕号——像剧本的边注。
 *
 * 为什么要这条轨:原来每一轮只是「一串上下堆叠的气泡」,读者要靠间距猜哪句和哪句是
 * 同一轮。有了轨之后一轮的边界是**画出来的**,滚动时长内容再多也不会看串。
 *
 * 轨的颜色比正文淡得多(发丝线);只有**当前正在跑的那一幕**在轨顶上点亮一个小方块,
 * 这也顺手把「现在演到第几幕」变成了空间信息,不用看顶栏。
 */
@Composable
private fun ActLine(
    index: Int,
    total: Int,
    /** 只在这一幕第一行显示幕号,后面几行留空但保留轨。 */
    showNum: Boolean = true,
    /** 最后一幕不画向下的延长线,让轨道有个自然的收尾。 */
    tail: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = MaeveTheme.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.width(ActRail).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showNum) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    Modifier.padding(top = 14.dp, bottom = 5.dp),
                    style = MaeveType.Turn,
                    color = c.ink3,
                )
                // 幕号下面一个小方块:这一幕的印章
                Box(Modifier.size(3.dp).background(c.ink3.copy(alpha = 0.55f)))
            } else {
                Spacer(Modifier.height(14.dp))
            }
            if (!tail) {
                Box(
                    Modifier.width(0.5.dp).weight(1f)
                        .background(c.hair),
                )
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

private val ActRail = 26.dp

private fun LazyListScope.exchange(
    index: Int, ex: Exchange, conversationId: String?, stage: Takeover.Stage, vm: ChatViewModel, reduceMotion: Boolean, backgroundNote: Boolean,
    openRun: (String) -> Unit, loadSource: suspend (String) -> EvidenceSource?, openSource: (String?, String?) -> Unit,
    kept: KeptPage? = null,
    /** 这一轮用了多少 token(结束后、读到运行小结时才有)。 */
    tokens: Long? = null,
    /** 总轮数:幕轨要知道哪一幕是最后一幕(最后一幕不画向下的延长线)。 */
    total: Int = 0,
) {
    // 每一轮 = 一幕。幕轨从这一幕第一行一直画到最后一行,左边挂着 LOOP 01 这样的等宽幕号。
    val lastAct = index == total - 1
    if (ex.goal.isNotEmpty()) item("u$index") { ActLine(index, total) { UserBubble(ex.goal, conversationId, key = "u$index") } }
    val active = ex.status.isActive()
    val answering = ex.status == ExchangeStatus.Working && !ex.draft.isNullOrBlank()
    if (active) {
        if (!answering) item("s$index") { ActLine(index, total, showNum = false) { WorkBlock(ex, reduceMotion, backgroundNote, openRun) } }
    } else if (ex.steps.isNotEmpty() || ex.runId != null) {
        item("s$index") { ActLine(index, total, showNum = false) { DoneLine(ex, reduceMotion, openRun, tokens) } }
    }
    // 任务进行中插的话:用户的原话,下面一行小字说它的去向。
    // 插话之前已经说完的回复照常显示;插话打断的那段留在原处(淡色、标「被你的新消息打断」);接着才是插话。
    // 还没交给模型的插话排在正在长出来的回答后面:那段回答是它到之前就在写的,不是对它的回复。
    fun steer(n: SteerNote) {
        n.finished?.takeIf(String::isNotBlank)?.let { done -> item("a$index-${n.id}") { ActLine(index, total, showNum = false) { FinishedReply(done, "a$index-${n.id}") } } }
        n.interrupted?.takeIf(String::isNotBlank)?.let { cut -> item("i$index-${n.id}") { ActLine(index, total, showNum = false) { InterruptedReply(cut) } } }
        item("m$index-${n.id}") { ActLine(index, total, showNum = false) { UserBubble(n.text, conversationId, steerLabel(n.state), "m$index-${n.id}") } }
    }
    val (joined, waiting) = steersAroundDraft(ex.steers)
    joined.forEach(::steer)
    ex.draft?.takeIf(String::isNotBlank)?.let { draft -> item("d$index") { ActLine(index, total, showNum = false) { DraftText(draft, ex.waitingForModel) } } }
    waiting.forEach(::steer)
    item("r$index") { ActLine(index, total, showNum = false, tail = lastAct) { StatusCard(ex.status, stage, vm, ex.runId, loadSource, openSource, "r$index") } }
    // 这一轮结束后留在后台运行的那一页:还开着是绿色一行(能关),关了是一句原因
    keptPageRow(index, ex, kept, vm::closeKeptPage)
    if (ex.files.isNotEmpty()) item("f$index") { FileCards(ex.files) }
    learningNotes(index, ex, vm)
}

/**
 * 插话分成两段:第一句还没交给模型的([SteerState.unconsumed])之前的,和它连同之后的。
 * 正在长出来的回答排在两段中间。
 */
internal fun steersAroundDraft(steers: List<SteerNote>): Pair<List<SteerNote>, List<SteerNote>> {
    val visible = steers.filterNot { it.state == SteerState.Withdrawn }
    val first = visible.indexOfFirst { it.state.unconsumed }.let { if (it < 0) visible.size else it }
    return visible.take(first) to visible.drop(first)
}

/**
 * 长按的 [key] 是进行中那一问一答里、模型还没取走的手机插话时给出它,菜单据此提供撤回和编辑(编辑另看 [editableSteer])。
 * 电脑追加的([SteerNote.remote])不给:编辑会把只归当前任务的话变成手机插话、可能转成下一件事;撤不撤由电脑定。
 */
internal fun withdrawableSteer(exchanges: List<Exchange>, key: String): SteerNote? = exchanges.mapIndexedNotNull { i, ex ->
    if (ex.status.isActive()) ex.steers.firstOrNull { key == "m$i-${it.id}" && it.state.unconsumed && !it.remote } else null
}.firstOrNull()

private fun ExchangeStatus.isActive() =
    this !is ExchangeStatus.Done && this !is ExchangeStatus.Failed && this !is ExchangeStatus.Cancelled

/**
 * 用户的话:主色气泡(浅色=种子色白字,深色=深色调近白字),圆角 20、右下尾角 6,最宽八成。[note] 是插话的去向。
 * 带着用户此刻的屏幕时上面是截图缩略图和「你此刻的屏幕 · App · 时刻」,附言在下面的气泡里(见 [UserMessage])。
 * 带着附件时上面是照片格子和文件卡片(见 [SentAttachments]),点开在 App 里的查看页看;写的话在下面另起一个气泡,路径那几行不显示。
 * 引用了前面的消息时最上面是引的那条(见 [SentQuote])。长按气泡弹出复制 / 引用 / 选择文字([key] 认是哪一条,见 [MessageArea])。
 * 在浏览器页上对着一页问的,最上面是「网页 · 网站」和选中的字(见 [SentPage]),页面说明那几行不显示。
 */
@Composable
internal fun UserBubble(text: String, conversationId: String?, note: String? = null, key: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paged = remember(text) { AttachedPage.parse(text) }
    val quoted = remember(text) { QuoteMessage.parse(paged.words) }
    val attached = remember(text) { AttachmentMessage.parse(quoted.words) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), contentAlignment = Alignment.CenterEnd) {
        val max = maxWidth * 0.8f
        Column(horizontalAlignment = Alignment.End) {
            paged.page?.let { SentPage(it, max) }
            quoted.quote?.let { SentQuote(it, max) }
            if (attached.hasAttachments) {
                SentAttachments(attached, conversationId, max) { item ->
                    val id = conversationId ?: return@SentAttachments
                    scope.launch { if (!AttachFiles.openSent(context, id, item.path)) Toast.makeText(context, AttachCopy.GONE, Toast.LENGTH_SHORT).show() }
                }
            }
            if (attached.hasAttachments && attached.words.isNotEmpty()) Spacer(Modifier.size(6.dp))
            if ((!attached.hasAttachments || attached.words.isNotEmpty()) && (paged.page == null || quoted.words.isNotEmpty())) UserMessage(conversationId, attached.words.takeIf { attached.hasAttachments } ?: quoted.words, ChatThumbWidth) { words ->
                // 选择文字时气泡上选中的底色用白色半透明,把手照旧是气泡色(画在气泡外面)
                val colors = MaeveTheme.colors
                CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.22f))) {
                    MessageArea(
                        key, QuoteMessage.From.You, words, "selectable-user",
                        // 你的话:高一级表面 + 一道描边。不用彩色实底——
                        // 整站只有一个彩色,留给品牌和主按钮,不留给自己的每一句话。
                        Modifier.widthIn(max = max)
                            .glassFill(colors, MaeveShapes.UserBubble, GlassTier.Card),
                        lifted = { LiftedMessage(MaeveShapes.UserBubble, colors.surfaceHighest) { UserBubbleText(words) } },
                    ) { UserBubbleText(words) }
                }
            }
            note?.let { Text(it, Modifier.padding(top = 3.dp, end = 4.dp), style = MaeveType.Hint, color = MaeveTheme.colors.ink3) }
        }
    }
}

@Composable
private fun UserBubbleText(words: String) {
    Text(words, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = BubbleText, color = MaeveTheme.colors.ink)
}

/** 长按抬起的那条消息:[color] 底、带阴影;太长时(最高半屏)只露开头,下面裁掉。 */
@Composable
private fun LiftedMessage(shape: Shape, color: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.floatShadow(MaeveTheme.colors, shape).background(color, shape).clip(shape)
            .wrapContentHeight(Alignment.Top, unbounded = true),
    ) { content() }
}

/** 对话页里截图缩略图的宽。 */
private val ChatThumbWidth = 110.dp

/** 正在生成的回答:和完成后的回答一样放在淡灰底上。 */
@Composable
private fun DraftText(text: String, generating: Boolean) {
    MarkdownText(
        text, AnswerText.copy(color = MaeveTheme.colors.ink),
        Modifier.fillMaxWidth().padding(bottom = 12.dp).answerSurface(), streaming = generating,
    )
}

/** 插话交给模型之前已经说完的回复:和回答一样的底和字,长按一样能复制、引用、选择文字。 */
@Composable
private fun FinishedReply(text: String, key: String) {
    Box(Modifier.padding(bottom = 4.dp)) {
        AnswerArea(key, text, Modifier.fillMaxWidth().answerSurface()) {
            MarkdownText(text, AnswerText.copy(color = MaeveTheme.colors.ink), streaming = false)
        }
    }
}

/**
 * Maeve 的一段回答:长按弹出复制 / 引用 / 选择文字(见 [MessageArea]),复制、引用的是去掉 Markdown 符号的纯文字;
 * [modifier] 是回答的底,抬起的那张是同样的字放在白卡上。选择文字时选区的把手在单独的弹出窗口里,拖把手不会带出侧栏。
 */
@Composable
private fun AnswerArea(key: String, answer: String, modifier: Modifier, content: @Composable () -> Unit) {
    val plain = remember(answer) { MarkdownModel.toPlainText(answer) }
    MessageArea(
        key, QuoteMessage.From.Maeve, plain, "selectable-answer", modifier,
        lifted = {
            LiftedMessage(MaeveShapes.AnswerBubble, MaeveTheme.colors.surface) {
                MarkdownText(answer, AnswerText.copy(color = MaeveTheme.colors.ink), Modifier.padding(horizontal = 14.dp, vertical = 12.dp), streaming = false)
            }
        },
    ) { content() }
}

/** 被插话打断的那段回复:和回答同样的底,字用次要色,下面一行小字「被你的新消息打断」。 */
@Composable
private fun InterruptedReply(text: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        MarkdownText(text, AnswerText.copy(color = MaeveTheme.colors.ink2), Modifier.fillMaxWidth().answerSurface(), streaming = false)
        Text(stringResource(R.string.steer_interrupted_reply), Modifier.padding(top = 3.dp, start = 4.dp), style = MaeveType.Hint, color = MaeveTheme.colors.ink3)
    }
}

/**
 * 她的回答的底:**一条发丝竖线**(台词轨),文字直接排在纸面上。
 *
 * 没有气泡、没有底色。气泡是「消息」的语法——一来一往、左右对立;
 * 但她的回答不是一条消息,是一段念白,应该像剧本的一栏那样贴着左边站好。
 * 用户的话在右边(实色气泡),她的话在左边(细轨+正文),两个人各自站在自己的位置。
 */
@Composable
private fun Modifier.answerSurface(): Modifier {
    val rule = MaeveTheme.colors.rule
    return drawBehind {
        val w = 1.dp.toPx()
        val inset = 2.dp.toPx()
        drawRect(
            color = rule,
            topLeft = Offset(0f, inset),
            size = Size(w, (size.height - inset * 2f).coerceAtLeast(0f)),
        )
    }.padding(start = 15.dp, top = 1.dp, bottom = 3.dp, end = 2.dp)
}

// ---------- 进度 ----------

/**
 * 进行中:**一块诊断条**。
 *
 * 这是整个 App 最该有特点的地方——她正在一块你看不见的屏上干活,所以这一块不能长得
 * 像聊天,要长得像仪表:
 *
 * ```
 * ⊙ CRADLE · RUNNING                    00:42
 * ▁▃▂▅▄▆▃▅█▆▇ (钢琴卷:已弹过的步 + 正在弹的那一根琥珀色)
 * 正在 12306 上筛选 15:00 后的车次
 * ```
 *
 * 冷青是机器的颜色,琥珀只给「此刻这一步」。用户不用读字就知道她在动、动到哪儿了。
 */
@Composable
private fun WorkBlock(ex: Exchange, reduceMotion: Boolean, backgroundNote: Boolean, openRun: (String) -> Unit) {
    val c = MaeveTheme.colors
    val paused = ChatCopy.paused(ex.status)
    val elapsed = rememberElapsed(ex.startedAt)
    // 正在跑:降到 surfaceLowest 一级(比画布略沉),加一道发丝线。
    // 不染色——「她在干活」用等宽字体和钢琴卷表达,不用冷青。
    val tone = c.ink2
    Column(
        Modifier.fillMaxWidth().padding(bottom = 8.dp)
            .clip(DiagShape)
            .glassFill(c, DiagShape, GlassTier.Card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MazeRing(
                progress = null,
                size = 13.dp,
                track = c.hair,
                head = tone,
                strokeWidth = 1.1.dp,
                indeterminate = !paused && !reduceMotion,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (paused) "等你确认" else "正在执行",
                Modifier.weight(1f),
                style = MaeveType.Caption.copy(fontSize = 12.sp),
                color = if (paused) c.warn else tone, maxLines = 1,
            )
            if (elapsed != null) {
                Text(ChatCopy.clock(elapsed), style = MaeveType.MonoSmall, color = tone)
            }
        }
        if (ex.steps.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            PianoRoll(
                stepNotes(ex), Modifier.fillMaxWidth(), height = 15.dp,
                done = c.ink3, active = c.ink, pending = c.hair,
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            ChatCopy.progressLine(ex), Modifier.fillMaxWidth(), style = NowText, color = c.ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (ex.steps.isNotEmpty()) StepLine(ex.steps, ex.stepActions, live = true, paused = paused)
        // 跑的时候就能进运行过程(页面按秒接着长);结束后收进「做了 N 步 · 查看过程」那一行
        ex.runId?.let { id ->
            Text(
                stringResource(R.string.chat_view_process),
                Modifier.padding(top = 4.dp).clip(MaeveShapes.Pill).clickable(role = Role.Button) { openRun(id) }
                    .padding(horizontal = 3.dp, vertical = 3.dp).testTag("work-view-process"),
                style = MaeveType.MonoSmall.copy(textDecoration = TextDecoration.Underline), color = tone,
            )
        }
        if (backgroundNote) {
            Row(
                Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(MaeveIcons.Eye, null, Modifier.size(14.dp), tint = c.ink3)
                Text(ChatCopy.BACKGROUND_NOTE, style = NoteText, color = c.ink3)
            }
        }
    }
}

/** 步骤 → 钢琴卷的音符状态。等你的那一步算「正在弹」,只是颜色由琥珀变亮。 */
private fun stepNotes(ex: Exchange): List<NoteState> = ex.steps.map {
    when (it.state) {
        StepState.Ok -> NoteState.Done
        StepState.Run, StepState.Wait -> NoteState.Active
        StepState.Dim -> NoteState.Pending
    }
}

private val DiagShape = RoundedCornerShape(10.dp)

/** 输入栏的形状:圆角 16 的纸条,不是胶囊——胶囊读作「聊天软件」,纸条读作「便签」。 */
private val ComposerShape = RoundedCornerShape(16.dp)

/** 从 [startedAt] 起的用时,每秒更新;不知道开始时刻(为 0)时为 null。 */
@Composable
private fun rememberElapsed(startedAt: Long): Long? {
    if (startedAt <= 0L) return null
    val now by produceState(System.currentTimeMillis(), startedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L - value % 1_000L)
        }
    }
    return (now - startedAt).coerceAtLeast(0L)
}

private enum class Dot { Done, Current, Paused, Dim }

/**
 * 竖线步骤。[live] 时最后一步按任务此刻的状态标蓝点或橙点;在跑时只列最近 [ChatCopy.VISIBLE_STEPS] 步,
 * 前面的收成一行,点开看全部。
 */
@Composable
private fun StepLine(steps: List<StepRow>, actions: Map<Int, List<StepAction>>, live: Boolean, paused: Boolean) {
    val c = MaeveTheme.colors
    var all by rememberSaveable { mutableStateOf(!live) }
    // 哪几步点开了:按这一屏里的序号记,列表变长也不乱
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }
    val hidden = if (all) 0 else (steps.size - ChatCopy.VISIBLE_STEPS).coerceAtLeast(0)
    Column(
        Modifier.padding(top = 6.dp).fillMaxWidth().drawBehind {
            val x = StepRail.toPx()
            drawLine(c.hair, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
        },
    ) {
        if (hidden > 0) {
            StepItem(ChatCopy.earlierSteps(hidden), "", Dot.Dim, current = false, link = true, onClick = { all = true })
        }
        steps.drop(hidden).forEachIndexed { i, row ->
            val key = hidden + i
            val isLast = key == steps.lastIndex
            val dot = when {
                live && isLast && paused -> Dot.Paused
                live && isLast && row.state == StepState.Run -> Dot.Current
                row.state == StepState.Dim -> Dot.Dim
                else -> Dot.Done
            }
            val open = expanded[key] == true
            val detail = actions[row.step].orEmpty()
            StepItem(
                row.text, row.subtitle, dot, current = dot == Dot.Current || dot == Dot.Paused,
                actions = detail, open = open,
                // 有具体动作才点得开:点开看这一步实际做了什么、对什么、结果如何
                onClick = if (detail.isEmpty()) null else ({ expanded[key] = !open }),
            )
        }
    }
}

@Composable
private fun StepItem(
    text: String, subtitle: String, dot: Dot, current: Boolean, link: Boolean = false, onClick: (() -> Unit)? = null,
    actions: List<StepAction> = emptyList(), open: Boolean = false,
) {
    val c = MaeveTheme.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier.fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = stringResource(if (open) R.string.collapse else R.string.chat_expand_steps), onClick = onClick)
                } else Modifier,
            )
            .heightIn(min = 28.dp).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(StepRail * 2), contentAlignment = Alignment.Center) { StepDot(dot) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text, style = if (current) StepCurrentText else StepText,
                color = when {
                    link -> c.accent
                    current -> c.ink
                    else -> c.ink2
                },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaeveType.Hint, color = c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // 点开:这一步实际做的几件事,一件一行「做了什么 · 对什么 · 结果」,说法和运行过程页一样
            if (open) actions.forEach { a ->
                Text(
                    ChatCopy.actionLine(a), Modifier.padding(top = 2.dp),
                    style = MaeveType.Hint, color = if (a.failed) c.bad else c.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 能点开的只有有动作的那些:右边一个小箭头,点开时朝下
        if (actions.isNotEmpty()) DirectionalIcon(
            MaeveIcons.Chevron, null,
            Modifier.padding(start = 6.dp).size(12.dp).graphicsLayer { rotationZ = if (open) if (rtl) -90f else 90f else 0f },
            tint = c.ink3,
        )
    }
}

/**
 * 步骤点:一个小方块,不是圆点。
 *
 * 方块读作「一个格子被填上了」,圆点读作「一个消息未读」。步骤是机器的记录,
 * 所以用方。当前那一步外面套一层淡色底,把它从一行里挑出来。
 */
@Composable
private fun StepDot(dot: Dot) {
    val c = MaeveTheme.colors
    val (fill, halo) = when (dot) {
        Dot.Done -> c.machineDim to null
        Dot.Dim -> c.hair to null
        Dot.Current -> c.signal to c.signalSoft
        Dot.Paused -> c.signal to c.signalSoft
    }
    val bg = c.bg
    Box(
        Modifier.size(14.dp).drawBehind {
            val center = Offset(size.width / 2, size.height / 2)
            if (halo != null) {
                drawCircle(bg, 7.dp.toPx(), center)
                drawCircle(halo, 7.dp.toPx(), center)
            }
            val half = 2.dp.toPx()
            drawRoundRect(
                color = fill,
                topLeft = Offset(center.x - half, center.y - half),
                size = Size(half * 2f, half * 2f),
                cornerRadius = CornerRadius(0.8.dp.toPx()),
            )
        },
    )
}

/**
 * 「在忙」的统一画法:**迷宫环**。
 *
 * 全站的转圈都走这里,所以从对话页到通知、小窗、配对页,「她在动」长得一模一样。
 * 转圈只说明「在忙」;迷宫环说明「在往中心走」——方向感和终点感是它比转圈多出来的东西。
 */
@Composable
internal fun Spinner(modifier: Modifier, spinning: Boolean, track: Color, head: Color) {
    MazeRing(
        progress = null,
        modifier = modifier,
        track = track,
        head = head,
        strokeWidth = 1.3.dp,
        indeterminate = spinning,
    )
}

/**
 * 结束后过程收成一行:「做了 N 步 · 用时 · 1.2 万 Token」(点了在下面展开步骤)和「查看过程」(进时间线)。
 * 没做步骤的(只聊了几句)只写 token;[tokens] 为 null(还没读到小结、服务商没报用量)时不写。
 */
@Composable
private fun DoneLine(ex: Exchange, reduceMotion: Boolean, openRun: (String) -> Unit, tokens: Long? = null) {
    val tokenText = tokens?.let(ChatTokensCopy::amount)
    val c = MaeveTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (ex.status is ExchangeStatus.Done) Icon(MaeveIcons.Check, null, Modifier.size(16.dp), tint = c.ink3)
            if (ex.steps.isNotEmpty()) {
                Text(
                    stepsSummary(ex) + tokenText?.let { " · $it" }.orEmpty(),
                    Modifier.clickable(onClickLabel = if (open) stringResource(R.string.chat_collapse_steps) else stringResource(R.string.chat_expand_steps)) { open = !open },
                    style = DoneText, color = c.ink3,
                )
            } else if (tokenText != null) {
                Text(tokenText, style = DoneText, color = c.ink3)
            }
            ex.runId?.let { id ->
                Text(
                    // 灰色斜体加下划线:和回答融在一起,又看得出能点
                    stringResource(R.string.chat_view_process), Modifier.clip(MaeveShapes.Pill).clickable(role = Role.Button) { openRun(id) }.padding(horizontal = 4.dp, vertical = 4.dp),
                    style = DoneText.copy(fontStyle = FontStyle.Italic, textDecoration = TextDecoration.Underline), color = c.ink3,
                )
            }
        }
        Reveal(open && ex.steps.isNotEmpty(), reduceMotion) { StepLine(ex.steps, ex.stepActions, live = false, paused = false) }
    }
}

/** 点开的内容:平时展开收起带动画,关了动画时直接出现、消失。 */
@Composable
private fun Reveal(visible: Boolean, reduceMotion: Boolean, content: @Composable () -> Unit) {
    if (reduceMotion) {
        if (visible) content()
    } else {
        AnimatedVisibility(visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { content() }
    }
}

// ---------- 状态与结果 ----------

@StringRes
fun takeoverReasonLabel(reason: String): Int? = when (reason) {
    "captcha" -> R.string.takeover_reason_captcha
    "login" -> R.string.takeover_reason_login
    "access_blocked" -> R.string.takeover_reason_access_blocked
    "payment" -> R.string.takeover_reason_payment
    "decision" -> R.string.takeover_reason_decision
    else -> null
}

/** 一问一答此刻的状态:要你做的事是一张白卡([NeedCard]),结果直接排在背景上。 */
@Composable
private fun StatusCard(
    status: ExchangeStatus, stage: Takeover.Stage, vm: ChatViewModel, runId: String?,
    loadSource: suspend (String) -> EvidenceSource?, openSource: (String?, String?) -> Unit,
    /** 回答的长按菜单认这一条用的(见 [MessageArea])。 */
    key: String,
) {
    when (status) {
        ExchangeStatus.Working -> Unit
        ExchangeStatus.WaitingUnlock -> NeedCard(stringResource(R.string.chat_wait_unlock), stringResource(R.string.chat_unlock_body))
        is ExchangeStatus.NeedsTakeover -> {
            val label = takeoverReasonLabel(status.reason)
            val title = if (label != null) stringResource(R.string.takeover_title_reason, stringResource(label)) else stringResource(R.string.takeover_title)
            val handedOver = stage == Takeover.Stage.HandedOver
            val text = if (handedOver) stringResource(R.string.takeover_handed_over_hint) else status.message
            // 付款的商品、金额、地址单独成行:这张卡片就是付款前的确认
            val body = listOfNotNull(text, StatusCopy.paymentLines(status.item, status.amount, status.address)).joinToString("\n")
            NeedCard(title, body, footer = { TextLink(stringResource(R.string.action_cancel_task), vm::cancel) }) {
                if (handedOver) {
                    CardButton(stringResource(R.string.action_finished_myself), onClick = vm::finishedMyself)
                    CardButton(stringResource(R.string.action_hand_back), primary = true, onClick = vm::handBack)
                } else {
                    CardButton(stringResource(R.string.action_skip), onClick = vm::skip)
                    CardButton(stringResource(R.string.action_take_over), primary = true, onClick = vm::takeOver)
                }
            }
        }
        is ExchangeStatus.NeedsInput -> {
            val options = status.options.orEmpty()
            val note = stringResource(R.string.no_answer_note, TaskService.INPUT_TIMEOUT_MINUTES)
            NeedCard(
                stringResource(R.string.input_title), stringResource(R.string.chat_input_body, status.prompt, note),
                options = if (options.isEmpty()) null else ({
                    options.forEach { o -> MaeveButton(o, { vm.answer(o) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal, size = ButtonSize.Medium) }
                }),
            ) {
                if (options.isEmpty()) CardButton(stringResource(R.string.action_cancel_task), onClick = vm::cancel)
            }
        }
        // 只有花钱的操作要你同意,每次只同意这一次
        is ExchangeStatus.NeedsConfirmation -> NeedCard(status.title, status.body + "\n" + ConfirmationCopy.NOTE) {
            val haptics = MaeveTheme.haptics
            // 拒绝 = Reject,批准不可逆动作 = Heavy;两者手感必须不同
            CardButton(stringResource(R.string.chat_refuse), onClick = { haptics.reject(); vm.decline(status.key) })
            CardButton(stringResource(R.string.chat_allow_once), primary = true, onClick = { haptics.heavy(); vm.approve(status.key) })
        }
        is ExchangeStatus.Paused -> NeedCard(stringResource(if (status.resumable) R.string.chat_paused else R.string.chat_waiting_for_you), status.why) {
            CardButton(stringResource(R.string.action_cancel_task), onClick = vm::cancel)
            if (status.resumable) CardButton(stringResource(R.string.chat_continue), primary = true, onClick = vm::resume)
        }
        // 排队等开始:轮到时自动开始,点继续不能插队,只给取消
        is ExchangeStatus.Queued -> NeedCard(stringResource(R.string.chat_queued), status.why) {
            CardButton(stringResource(R.string.action_cancel_task), onClick = vm::cancel)
        }
        // 长按整块回答弹菜单;复制、引用的只是回答的字,不带上面的提示和下面的来源
        is ExchangeStatus.Done -> Box(Modifier.padding(bottom = 12.dp)) {
            AnswerArea(key, status.answer, Modifier.fillMaxWidth().answerSurface()) {
                Column {
                    status.unverifiedLine?.let { line ->
                        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).background(MaeveTheme.colors.warnSoft, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(MaeveIcons.Exclaim, null, Modifier.size(11.dp), tint = MaeveTheme.colors.warn)
                            }
                            Text(line, style = MaeveType.Caption, fontWeight = FontWeight.SemiBold, color = MaeveTheme.colors.ink2)
                        }
                    }
                    MarkdownText(status.answer, AnswerText.copy(color = MaeveTheme.colors.ink))
                    // 来源:旧记录里模型写的依据文字,或运行记录里代码记下的来源;都没有就不显示
                    if (status.evidence != null || runId != null) SourceChip(status.evidence, runId, loadSource, openSource)
                }
            }
        }
        is ExchangeStatus.Failed -> Column(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(MaeveTheme.colors.bad, CircleShape))
                Text(stringResource(R.string.chat_not_done), style = MaeveType.BodyStrong, color = MaeveTheme.colors.ink)
            }
            Text(status.reason, Modifier.padding(top = 4.dp), style = CardBody, color = MaeveTheme.colors.ink2)
        }
        // 停止后补上的交代当作这一轮的回答,排在「已停止」下面;还在写时一行小字
        is ExchangeStatus.Cancelled -> Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(stringResource(R.string.chat_stopped), Modifier.padding(start = 2.dp), style = MaeveType.Body, color = MaeveTheme.colors.ink2)
            if (status.summarizing) Text(
                StatusCopy.STOP_SUMMARIZING, Modifier.padding(start = 2.dp, top = 4.dp), style = MaeveType.Caption, color = MaeveTheme.colors.ink3,
            )
            status.summary?.let { summary ->
                Box(Modifier.padding(top = 8.dp)) {
                    AnswerArea(key, summary, Modifier.fillMaxWidth().answerSurface()) {
                        MarkdownText(summary, AnswerText.copy(color = MaeveTheme.colors.ink))
                    }
                }
            }
        }
    }
}

/** 回答下面的「来源」小胶囊,只是入口;依据和截图在独立页面看。 */
@Composable
private fun SourceChip(text: String?, runId: String?, loadSource: suspend (String) -> EvidenceSource?, openSource: (String?, String?) -> Unit) {
    val source by produceState<EvidenceSource?>(null, runId) { value = runId?.let { loadSource(it) } }
    if (text == null && source?.items.isNullOrEmpty() && source?.screenshots.isNullOrEmpty()) return
    val c = MaeveTheme.colors
    Row(
        Modifier.padding(top = 12.dp).heightIn(min = 30.dp)
            .cardSurface(c, MaeveShapes.Pill, tier = GlassTier.Pill).clip(MaeveShapes.Pill)
            .clickable(onClickLabel = stringResource(R.string.chat_view_source)) { openSource(runId, text) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(MaeveIcons.Link, null, Modifier.size(15.dp), tint = c.ink2)
        Text(sourceLabel(source), style = PillText, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 「需要你」卡片(要你处理、提问、确认、暂停、锁屏、被拦下):白卡,标题前一个橙色圆点,下面说明;
 * [options] 是一列可点的答案;按钮并排,次要的在左(浅色)、主要的在右(蓝色);[footer] 在最下面。
 */
@Composable
private fun NeedCard(
    title: String,
    body: String,
    footer: (@Composable () -> Unit)? = null,
    options: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = MaeveTheme.colors
    // 「需要你」是全 App 唯一允许出现琥珀的地方:左边一道实心竖条 + 一行等宽标签,
    // 其余仍是纸面。这样它在一屏灰纸里是唯一亮的东西,不用读字就知道要看你。
    Box(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
            .clip(NeedShape)
            .glassFill(c, NeedShape, GlassTier.Card),
    ) {
        Box(Modifier.matchParentSize().drawBehind {
            drawRect(c.signal, size = Size(3.dp.toPx(), size.height))
        })
        Column(Modifier.padding(start = 17.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Text(title, Modifier.padding(top = 2.dp), style = NeedTitle, color = c.ink)
            Text(body, Modifier.padding(top = 5.dp), style = CardBody, color = c.ink2)
            if (options != null) {
                Column(Modifier.padding(top = 14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { options() }
            }
            if (buttons != null) {
                Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
            }
            footer?.let { Box(Modifier.padding(top = 10.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { it() } }
        }
    }
}

private val NeedShape = RoundedCornerShape(10.dp)

@Composable
private fun RowScope.CardButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    MaeveButton(
        text, onClick, Modifier.weight(1f),
        kind = if (primary) ButtonKind.Primary else ButtonKind.Tonal, size = ButtonSize.Medium,
    )
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(text, Modifier.clip(MaeveShapes.Pill).clickable(onClick = onClick).padding(4.dp), style = MaeveType.Caption, color = MaeveTheme.colors.ink3)
}

/** 导出到「下载/Maeve」的文件:小卡片,名字、大小和存放处;点了能看的在 App 里打开([FileViewer]),其余交给系统里能打开它的 App。 */
@Composable
private fun FileCards(files: List<SharedFile>) {
    val context = LocalContext.current
    val c = MaeveTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (f in files) {
            Row(
                Modifier.fillMaxWidth().cardSurface(c, MaeveShapes.Card).clip(MaeveShapes.Card)
                    .clickable(onClickLabel = stringResource(R.string.chat_open), role = Role.Button) { openShared(context, f) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).background(c.neutralSoft, FileTileShape), contentAlignment = Alignment.Center) {
                    Icon(MaeveIcons.Doc, null, Modifier.size(20.dp), tint = c.ink)
                }
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = FileName, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${ChatViewModel.sizeLabel(f.size)} · ${f.savedAs.substringBeforeLast('/')}",
                        style = FileMeta, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                DirectionalIcon(MaeveIcons.Chevron, null, Modifier.size(MaeveSize.SmallIcon), tint = c.ink3)
            }
        }
    }
}

private fun openShared(context: Context, f: SharedFile) = FileViewer.open(context, Uri.parse(f.uri), f.name)

// ---------- 输入栏 ----------

/** 「＋」面板:关着、开着、正让给键盘(点了输入框:键盘升起来时面板跟着矮下去,键盘到位再关)。 */
private enum class Panel { Closed, Open, Yielding }

/**
 * 玻璃胶囊输入栏:左边「＋」,右边发送(没内容时灰);任务在跑时多一个停止,输入框的灰字提示插话会交给当前任务。
 * 「＋」点开的附件面板([attachPanel])在输入栏下面、占键盘那块位置,和键盘互换:开面板收键盘,
 * 再点「＋」(这时是 ×)或点输入框就收起面板、换成键盘;返回先收面板。待发的附件在输入栏上方一排([PendingTray])。
 */
@Composable
internal fun Composer(
    modifier: Modifier,
    text: String,
    onText: (String) -> Unit,
    mode: ComposerMode,
    hasHistory: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    /** 沙箱就绪、没有任务在跑时才有「＋」;点开是这块面板。 */
    attachPanel: (@Composable () -> Unit)? = null,
    attachments: List<PendingAttachment> = emptyList(),
    importing: Boolean = false,
    rejected: List<String> = emptyList(),
    overLimit: Int = 0,
    onRemove: (String) -> Unit = {},
    onDismissRejected: () -> Unit = {},
    /** 要随下一句话发出去的引用;有时输入框上方一张小条,× 去掉。 */
    quote: QuoteMessage.Quote? = null,
    onRemoveQuote: () -> Unit = {},
    editRequest: Int = 0,
    /** 输入栏后面那层内容的画面;为 null 时输入栏不做模糊、用不透明底。 */
    backdrop: GlassBackdrop? = null,
    reduceMotion: Boolean = false,
    /** 按住说话从这里开会话;null 时输入框只能打字。 */
    voice: VoiceSource? = null,
    /** 说完的字;调用方把它接在已有的字后面,不自动发送。 */
    onVoiceText: (String) -> Unit = {},
    /** 「还不能语音输入」里的「去选」:到设置里选转文字。 */
    onPickSpeech: () -> Unit = {},
    /** 任务在跑时说的话交给谁:卡片顶上写「插话给『…』」。 */
    steerTarget: String? = null,
    /** 按着说话时为 true:对话内容跟着虚化。 */
    onVoiceHolding: (Boolean) -> Unit = {},
) {
    val c = MaeveTheme.colors
    val vh = rememberVoiceHold(voice, onVoiceText)
    val vState = voiceState(vh)
    val voiceHolding = vh?.holding == true
    LaunchedEffect(voiceHolding) { onVoiceHolding(voiceHolding) }
    var fieldFocused by remember { mutableStateOf(false) }
    // 底部从页面底色淡出到透明,输入栏是浮在上面的玻璃
    val fade = remember(c) { Brush.verticalGradient(0f to c.bg.copy(alpha = 0f), 0.6f to c.bg, 1f to c.bg) }
    val busy = mode == ComposerMode.Busy
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val field = remember { FocusRequester() }
    val ime = WindowInsets.ime
    val nav = WindowInsets.navigationBars
    var panel by rememberSaveable { mutableStateOf(Panel.Closed) }
    LaunchedEffect(editRequest) {
        if (editRequest > 0) {
            panel = Panel.Closed
            field.requestFocus()
            keyboard?.show()
        }
    }
    // 面板和键盘一样高:记下见过的键盘高度(在导航栏之上的那段),没见过时用默认高度
    var keyboardPx by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(ime, nav, density) {
        snapshotFlow { ime.getBottom(density) - nav.getBottom(density) }.collect { if (it > keyboardPx) keyboardPx = it }
    }
    // 输入框聚焦时长按留给系统的选字、粘贴(悬浮、分体、实体键盘也一样);没聚焦时输入框上盖一层按住说话的手势
    val voiceGesture = vh != null && (!fieldFocused || voiceHolding)
    // 键盘从弹着到收起(返回键、键盘自己的收起键):输入框跟着失焦,又能按住说话。只看键盘的变化,实体键盘(从不弹出)不受影响
    LaunchedEffect(ime, density, vh != null) {
        if (vh == null) return@LaunchedEffect
        var wasUp = false
        snapshotFlow { ime.getBottom(density) > 0 }.collect { up ->
            if (wasUp && !up && fieldFocused) focus.clearFocus()
            wasUp = up
        }
    }
    // 输入框外面的字一变(说完填进来、点了示例),光标放到末尾,接着说或接着打都在后面
    var fieldValue by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val shownValue = if (fieldValue.text == text) fieldValue else TextFieldValue(text, TextRange(text.length))
    val panelPx = with(density) {
        if (keyboardPx > 0) keyboardPx.coerceIn(PanelMin.roundToPx(), PanelMax.roundToPx()) else PanelDefault.roundToPx()
    }
    LaunchedEffect(attachPanel == null) { if (attachPanel == null) panel = Panel.Closed }
    BackHandler(enabled = panel == Panel.Open) { panel = Panel.Closed }
    // 让给键盘:等键盘升到差不多面板高(没有键盘弹出来时最多等一会儿)再关
    LaunchedEffect(panel, panelPx) {
        if (panel != Panel.Yielding) return@LaunchedEffect
        withTimeoutOrNull(YIELD_MS) {
            snapshotFlow { ime.getBottom(density) - nav.getBottom(density) }.first { it >= panelPx * 9 / 10 }
        }
        panel = Panel.Closed
    }
    val togglePanel: () -> Unit = {
        if (panel == Panel.Open) {
            panel = Panel.Yielding
            field.requestFocus()
            keyboard?.show()
        } else {
            focus.clearFocus()
            keyboard?.hide()
            panel = Panel.Open
        }
    }
    val turn by animateFloatAsState(if (panel == Panel.Open) 45f else 0f, if (reduceMotion) snap() else tween(200), label = "plus")
    Box(
        modifier.fillMaxWidth()
            .background(fade)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            quote?.let { QuoteChip(it, onRemoveQuote, Modifier.padding(bottom = 6.dp)) }
            if (attachments.isNotEmpty()) PendingTray(attachments, onRemove, Modifier.padding(bottom = 2.dp))
            if (importing || rejected.isNotEmpty() || overLimit > 0) {
                Column(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (importing) Text(AttachCopy.IMPORTING, style = MaeveType.Caption, color = c.ink3)
                    if (rejected.isNotEmpty() || overLimit > 0) {
                        val why = listOfNotNull(
                            rejected.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.chat_attach_rejected, it.joinToString(stringResource(R.string.list_sep))) },
                            overLimit.takeIf { it > 0 }?.let(AttachCopy::overLimit),
                        ).joinToString("\n")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(why, Modifier.weight(1f), style = MaeveType.Caption, color = c.warn)
                            TextLink(stringResource(R.string.ok_got_it), onDismissRejected)
                        }
                    }
                }
            }
            if (vh != null) {
                val title = if (busy && !steerTarget.isNullOrBlank()) stringResource(R.string.voice_steer_to, steerTarget)
                else stringResource(R.string.voice_listening)
                VoiceFloat(vh, vState, title, backdrop, reduceMotion)
            }
            Box(Modifier.fillMaxWidth()) {
            // 输入栏是全站唯一一处玻璃:它是唯一一直浮在滚动内容上的东西,模糊在这里才有意义
            GlassBar(Modifier.fillMaxWidth().keepsKeyboard(), backdrop, shape = ComposerShape) {
                if (attachPanel != null) {
                    val label = if (panel == Panel.Open) AttachCopy.CLOSE else AttachCopy.ADD
                    Box(
                        Modifier.size(MaeveSize.IconButton).clip(CircleShape)
                            .clickable(enabled = !importing, onClickLabel = label, onClick = togglePanel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MaeveIcons.Plus, label, Modifier.size(MaeveSize.Icon).graphicsLayer { rotationZ = turn }, tint = c.ink)
                    }
                }
                Box(Modifier.weight(1f).padding(start = if (attachPanel != null) 2.dp else 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
                    val converting = vState is HoldToTalk.State.Converting
                    if (text.isEmpty() && !converting) {
                        if (vh != null) VoicePlaceholder(mode)
                        else Text(ChatCopy.placeholder(mode, hasHistory), style = MaeveType.Body, color = c.ink3, maxLines = 1)
                    }
                    BasicTextField(
                        shownValue,
                        { v -> fieldValue = v; if (v.text != text) onText(v.text) },
                        Modifier.fillMaxWidth().testTag("composer-field").focusRequester(field)
                            .alpha(if (converting) 0f else 1f)
                            // 面板开着时点输入框:面板让给键盘
                            .onFocusChanged {
                                fieldFocused = it.isFocused
                                if (it.isFocused && panel == Panel.Open) panel = Panel.Yielding
                            },
                        textStyle = MaeveType.Body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        maxLines = 5,
                    )
                    // 服务商松开后在转:输入框里转圈,转好的字填进来
                    if (converting) VoiceConverting(Modifier.align(Alignment.CenterStart))
                    if (voiceGesture && vh != null) {
                        Box(Modifier.matchParentSize().holdToTalk(vh) { field.requestFocus(); keyboard?.show() })
                    }
                }
                val canSend = AttachTray.canSend(text, attachments, importing)
                // 键盘出来以后长按输入框留给系统选字;话筒按住才听,点一下只提示按住
                if (fieldFocused && vh != null) {
                    val hint = stringResource(R.string.voice_hold_to_talk)
                    Box(
                        Modifier.size(MaeveSize.IconButton).testTag("composer-mic").holdToTalk(vh) { vh.toast = hint },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MaeveIcons.Mic, hint, Modifier.size(MaeveSize.Icon), tint = c.ink)
                    }
                }
                // 任务在跑时停止一直在;写了字再多一个发送,发出去的话交给当前任务
                if (busy) {
                    Box(
                        Modifier.size(MaeveSize.IconButton).background(c.ink, CircleShape).clip(CircleShape)
                            .clickable(onClickLabel = stringResource(R.string.chat_stop), onClick = onStop),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MaeveIcons.Stop, stringResource(R.string.chat_stop), Modifier.size(26.dp), tint = c.bg)
                    }
                }
                if (!busy || canSend) {
                    val haptics = MaeveTheme.haptics
                    Box(
                        Modifier.size(MaeveSize.IconButton)
                            .background(if (canSend) c.accent else c.accent.copy(alpha = 0.12f), CircleShape)
                            .clip(CircleShape)
                            .clickable(enabled = canSend, onClickLabel = stringResource(R.string.chat_send)) {
                                haptics.light()
                                panel = Panel.Closed
                                onSend()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MaeveIcons.Up, stringResource(R.string.chat_send), Modifier.size(20.dp), tint = if (canSend) c.onAccent else c.accent)
                    }
                }
            }
            if (vh != null) VoiceBarCover(vh, vState, reduceMotion, Modifier.matchParentSize())
            }
            if (vh != null) VoiceSheetHost(vh, onPickSpeech)
            if (panel != Panel.Closed && attachPanel != null) {
                // 键盘还在(刚开面板时正在收、让给键盘时正在升)的那一段从面板里扣掉:输入栏不跟着上下跳
                Box(
                    Modifier.fillMaxWidth().clipToBounds().layout { m, cons ->
                        val keyboardNow = (ime.getBottom(this) - nav.getBottom(this)).coerceAtLeast(0)
                        val shown = (panelPx - keyboardNow).coerceAtLeast(0)
                        val p = m.measure(Constraints.fixed(cons.maxWidth, panelPx))
                        layout(cons.maxWidth, shown) { p.place(0, 0) }
                    },
                ) {
                    Box(Modifier.fillMaxSize().padding(top = 4.dp)) { attachPanel() }
                }
            }
        }
    }
}

/** 面板高度:没见过键盘时的默认值,以及跟着键盘时的上下限。 */
private val PanelDefault = 320.dp
private val PanelMin = 280.dp
private val PanelMax = 420.dp
/** 让给键盘时最多等这么久(毫秒)。 */
private const val YIELD_MS = 700L

/** 竖线的位置(从左边算起);步骤点、转圈的中心都在这条线上。 */
private val StepRail = 8.dp

private val FileTileShape = RoundedCornerShape(10.dp)
private val EmptyLead = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
private val ExampleText = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
/** 空状态示例卡里的说明:三行放得下一句示例。 */
private val ExampleTileText = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp)
private val BubbleText = TextStyle(fontSize = 15.5.sp, lineHeight = 22.5.sp)
/** 回答:正文字号,行距放宽到 1.7。 */
private val AnswerText = TextStyle(fontSize = 15.5.sp, lineHeight = 25.sp)
private val NowText = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
private val ClockText = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, fontFeatureSettings = "tnum")
private val StepText = TextStyle(fontSize = 14.sp, lineHeight = 19.sp)
private val StepCurrentText = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
private val NoteText = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp)
private val DoneText = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 0.3.sp, fontFeatureSettings = "tnum")
private val NeedTitle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
private val CardBody = TextStyle(fontSize = 14.sp, lineHeight = 21.5.sp)
private val PillText = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
private val FileName = TextStyle(fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
private val FileMeta = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp)

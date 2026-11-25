import 'dart:async';

import '../ui/settings/model_settings_page.dart';

import 'package:flutter/material.dart';

import 'maeve_controller.dart';

import '../design/maeve_motifs.dart';
import '../design/maeve_motion.dart';
import '../design/maeve_shapes.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

import 'package:flutter/services.dart';

import '../models/conversation.dart';
import '../ui/common/app_page.dart';
import '../ui/common/app_sheet.dart';
import '../ui/common/haptics.dart';
import '../ui/common/pressable.dart';
import '../ui/common/status.dart';
import '../ui/settings/settings_components.dart';
import '../ui/settings/scheduled_tasks_page.dart';
import '../widgets/glass_surface.dart';
import '../widgets/maeve_mark.dart';
import '../widgets/execution_view.dart';
import '../widgets/maeve_markdown.dart';
import '../widgets/task_share_page.dart';

part 'chat_composer.dart';

/// 聊天页(Maeve 的语言:剧本轨 + 纸条输入条)。
///
/// - 顶栏是**整条玻璃**:品牌标 + 标题 + 一行状态,左边抽屉键、右边两个动作键。
/// - 每一轮对话前面站一个 [ScriptRail](第几幕 + 幕号印章 + 竖线)。
/// - 你的话是"高一级表面 + 一道描边"的气泡(不是彩色实底);
///   她的回答是**一条竖线 + 全宽正文**(剧本边注),没有填充卡片。
/// - 输入条是 16 圆角的纸条,不是胶囊;浮在内容上,下面垫一段渐隐。
class ChatPage extends StatefulWidget {
  const ChatPage({super.key, required this.onMenu});

  final VoidCallback onMenu;

  @override
  State<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends State<ChatPage> {
  final TextEditingController _input = TextEditingController();
  final ScrollController _scroll = ScrollController();
  final Set<String> _openTurns = {};
  bool _showJump = false;
  bool _followLatest = true, _userScrolling = false;
  String _renderedTask = '', _renderedConversation = '';
  String? _draftKey;
  int _draftRestoreRevision = -1;
  MaeveController? _draftController;
  bool _restoringDraft = false, _submitting = false;
  bool _shareOpen = false;

  /// 正在引用的那条消息(引用 chip)。
  ({String text, bool fromUser})? _quote;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
    _input.addListener(_saveDraft);
  }

  @override
  void dispose() {
    _saveDraft();
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (!mounted) return;
    final show =
        !MaeveScope.read(context).active.isEmpty &&
        _scroll.hasClients &&
        _scroll.position.extentAfter > 180;
    if (show != _showJump && mounted) setState(() => _showJump = show);
  }

  void _saveDraft() {
    if (_restoringDraft || _draftKey == null || _draftController == null) {
      return;
    }
    _draftController!.saveDraft(_draftKey!, _input.text, _quote);
  }

  void _bindDraft(MaeveController controller) {
    if (_draftRestoreRevision == controller.draftRestoreRevision) _saveDraft();
    if (_draftKey == controller.draftKey &&
        _draftRestoreRevision == controller.draftRestoreRevision) {
      return;
    }
    _draftRestoreRevision = controller.draftRestoreRevision;
    _draftController = controller;
    _draftKey = controller.draftKey;
    final saved = controller.drafts[_draftKey];
    _restoringDraft = true;
    _quote = saved?.quote;
    _input.text = saved?.text ?? '';
    _restoringDraft = false;
  }

  Future<void> _send(MaeveController controller) async {
    final inputValue = _input.value.copyWith(composing: TextRange.empty);
    final text = _input.text;
    if (text.trim().isEmpty || _submitting) return;
    final quote = _quote, key = _draftKey;
    _saveDraft();
    _submitting = true;
    FocusScope.of(context).unfocus();
    try {
      final accepted = await controller.send(
        text,
        quote: quote?.text,
        quoteFromUser: quote?.fromUser ?? false,
      );
      if (!mounted) return;
      if (!accepted) {
        _notice(context, controller.chatError ?? '宿主未接收，草稿已保留。请检查前置条件。');
        return;
      }
      if (controller.drafts[key] == (text: text, quote: quote)) {
        controller.saveDraft(key!, '', null);
      }
      if (_draftKey == key &&
          _input.value.copyWith(composing: TextRange.empty) == inputValue &&
          _quote == quote) {
        _input.clear();
        setState(() => _quote = null);
      }
      _toBottom();
    } catch (_) {
      if (mounted) {
        _notice(
          context,
          controller.chatError ??
              controller.draftError ??
              '发送结果尚未确认，草稿已保留。请先检查对话，避免重复发送。',
        );
      }
    } finally {
      _submitting = false;
    }
  }

  void _toBottom({bool animate = true}) {
    _followLatest = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_followLatest || !_scroll.hasClients) return;
      if (animate) {
        _scroll.animateTo(
          _scroll.position.maxScrollExtent,
          duration: AppMotion.of(context).standard,
          curve: MaeveMotion.glide,
        );
      } else {
        _scroll.jumpTo(_scroll.position.maxScrollExtent);
      }
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  Future<void> _openShare(MaeveController controller, Message message) async {
    if (_shareOpen) return;
    final conversation = controller.active;
    final index = conversation.messages.indexOf(message);
    if (index < 0 || message.runId == null) return;
    final prompt = conversation.messages
        .take(index)
        .where((m) => m.isUser)
        .lastOrNull;
    _shareOpen = true;
    try {
      await AppPage.open<void>(
        context,
        TaskSharePage(
          bridge: controller.chatBridge,
          message: message,
          prompt: prompt?.text,
          viewport: MediaQuery.sizeOf(context),
        ),
      );
    } finally {
      _shareOpen = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    _bindDraft(controller);
    final conversation = controller.active;
    final last = conversation.messages.lastOrNull;
    final taskSignature =
        '${conversation.id}:${conversation.messages.length}:${last?.executionSteps.length}:${last?.text.length}:${last?.status}';
    if (_renderedTask != taskSignature) {
      final changedConversation = _renderedConversation != conversation.id;
      final following = _followLatest;
      _renderedTask = taskSignature;
      _renderedConversation = conversation.id;
      if (changedConversation) _showJump = false;
      if (changedConversation || following) _toBottom(animate: false);
    }
    final padding = MediaQuery.viewPaddingOf(context);
    final bottomSafe = padding.bottom;
    final reserve =
        MaeveSize.composer +
        (MediaQuery.sizeOf(context).width < 600 ? 4 : MaeveSpace.s12) +
        (MediaQuery.sizeOf(context).width < 600 ? 4 : MaeveSpace.s24) +
        (conversation.isEmpty ? 0 : MaeveSpace.s24) +
        (_quote != null ? 56 : 0) +
        (controller.blockedNotice != null ? 40 : 0);
    final topInset =
        padding.top +
        MaeveSize.topBarBrand +
        (MediaQuery.sizeOf(context).width < 600 ? 4 : 10);
    final bottomInset = bottomSafe + reserve;
    final emptyHeight =
        (MediaQuery.sizeOf(context).height - topInset - bottomInset).clamp(
          0.0,
          double.infinity,
        );

    return Stack(
      children: [
        Positioned.fill(
          child: NotificationListener<ScrollMetricsNotification>(
            onNotification: (notice) {
              if (notice.depth == 0 &&
                  _followLatest &&
                  notice.metrics.extentAfter > 1) {
                _toBottom(animate: false);
              }
              return false;
            },
            child: NotificationListener<ScrollNotification>(
              onNotification: (notice) {
                if (notice.depth != 0) return false;
                if (notice is ScrollStartNotification &&
                    notice.dragDetails != null) {
                  _userScrolling = true;
                  _followLatest = false;
                }
                if (_userScrolling && notice is ScrollEndNotification) {
                  _followLatest = notice.metrics.extentAfter < 140;
                  _userScrolling = false;
                }
                return false;
              },
              child: ListView(
                key: const ValueKey('chat-list'),
                controller: _scroll,
                padding: EdgeInsets.only(top: topInset, bottom: bottomInset),
                children: [
                  Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(
                        maxWidth: MaeveSpace.readingWidth,
                      ),
                      child: Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: MediaQuery.sizeOf(context).width < 600
                              ? MaeveSpace.s8
                              : MaeveSpace.s24,
                        ),
                        child: conversation.isEmpty
                            ? _EmptyHome(minHeight: emptyHeight)
                            : _MessageList(
                                conversation: conversation,
                                openTurns: _openTurns,
                                onToggleTurn: (key) => setState(() {
                                  if (!_openTurns.add(key)) {
                                    _openTurns.remove(key);
                                  }
                                }),
                                onRetry: controller.sendQueued,
                                onLongPress: (m) =>
                                    _messageSheet(context, controller, m),
                                onShare: (message) =>
                                    _openShare(controller, message),
                              ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: TopBar(
            title: conversation.isEmpty ? 'maeve' : conversation.title,
            subtitle: controller.statusLine,
            statusTone: !controller.connected
                ? MaeveTone.idle
                : controller.generating
                ? MaeveTone.running
                : controller.settingsLoaded &&
                      controller.native.currentModelId == null
                ? MaeveTone.warning
                : MaeveTone.ready,
            working: controller.generating,
            brand: true,
            showBack: false,
            leading: _BarAction(
              icon: Icons.menu_rounded,
              label: '打开对话列表',
              onTap: widget.onMenu,
            ),
            actions: [
              _BarAction(
                icon: Icons.schedule_rounded,
                label: '定时任务',
                onTap: () => AppPage.open(context, ScheduledTasksPage(conversationId: controller.activeId)),
              ),
              _BarAction(
                icon: Icons.add_rounded,
                label: '新对话',
                onTap: () => controller.create(),
              ),
            ],
          ),
        ),
        Positioned(
          left: 0,
          right: 0,
          bottom: 0,
          child: _ComposerDock(
            controller: controller,
            input: _input,
            quote: _quote?.text,
            onClearQuote: () => setState(() => _quote = null),
            onSend: () => _send(controller),
            onStop: controller.stop,
          ),
        ),
        if (!conversation.isEmpty && _showJump)
          Positioned(
            right: MaeveSpace.s16,
            bottom: bottomSafe + 104,
            child: MaeveGlassSurface(
              tier: MaeveGlassTier.card,
              shape: const CircleBorder(),
              onTap: _toBottom,
              child: const SizedBox.square(
                dimension: 38,
                child: Icon(Icons.arrow_downward_rounded, size: 18),
              ),
            ),
          ),
      ],
    );
  }

  void _notice(BuildContext context, String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  /// 长按一条消息:复制 / 引用。
  Future<void> _messageSheet(
    BuildContext context,
    MaeveController controller,
    Message message,
  ) {
    unawaited(MaeveHaptics.play(MaeveHaptic.medium));
    final summary = message.text.length > 60
        ? '${message.text.substring(0, 60)}…'
        : message.text;
    return showMaeveSheet<void>(
      context: context,
      builder: (context) => AppSheet(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SheetTitleBar(
              title: message.isUser ? '你的消息' : 'Maeve 的回复',
              subtitle: summary,
            ),
            const SizedBox(height: MaeveSpace.s8),
            ListPageRow(
              title: '复制',
              leading: const Icon(Icons.copy_all_outlined, size: 19),
              onTap: () async {
                await Clipboard.setData(ClipboardData(text: message.text));
                if (context.mounted) {
                  Navigator.of(context).pop();
                  _notice(context, '已复制。');
                }
              },
            ),
            ListPageRow(
              title: '引用',
              subtitle: '放到输入条上方，连同下一条一起发出去',
              leading: const Icon(Icons.format_quote_rounded, size: 19),
              onTap: () {
                setState(() {
                  _quote = (text: summary, fromUser: message.isUser);
                });
                Navigator.of(context).pop();
              },
            ),
            if (message.queued)
              ListPageRow(
                title: '立即发送这条',
                leading: const Icon(Icons.bolt_rounded, size: 19),
                onTap: () {
                  Navigator.of(context).pop();
                  controller.sendQueued(message);
                },
              ),
          ],
        ),
      ),
    );
  }
}

/// 顶栏上的一个动作键:液态玻璃圆钮,48 的可点域,一次轻触感。
class _BarAction extends StatelessWidget {
  const _BarAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: 48,
    child: Center(
      child: MaeveGlassButton(
        icon: icon,
        tooltip: label,
        haptic: MaeveHaptic.tick,
        onPressed: onTap,
      ),
    ),
  );
}

/// Fit the idle stage to the real space between header and composer. A quiet
/// centre is intentional; scroll is reserved for small screens and large text.
class _EmptyHome extends StatelessWidget {
  const _EmptyHome({required this.minHeight});
  final double minHeight;
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final compact = minHeight < 390;
    final scale = MediaQuery.textScalerOf(context).scale(1);
    final minimum = (compact ? 230.0 : 270.0) + (scale - 1).clamp(0, 2) * 100;
    final height = minHeight < minimum ? minimum : minHeight;
    return SizedBox(
      key: const ValueKey('home-stage'),
      height: height,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Spacer(),
          Center(child: MaeveEmblem(size: compact ? 64 : 88)),
          SizedBox(height: compact ? 16 : 22),
          Text(
            '下一幕，\n由你决定。',
            textAlign: TextAlign.center,
            style: MaeveType.largeTitle.copyWith(
              fontSize: compact ? 26 : 29,
              height: 1.27,
              fontWeight: FontWeight.w500,
              letterSpacing: -.6,
              color: c.ink,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            '你继续生活，我在幕后行动。',
            textAlign: TextAlign.center,
            style: MaeveType.caption.copyWith(color: c.ink2),
          ),
          const Spacer(),
        ],
      ),
    );
  }
}

/// 消息列表:每一轮 = 台词轨 + 一条用户气泡 + 一条回答。
class _MessageList extends StatelessWidget {
  const _MessageList({
    required this.conversation,
    required this.openTurns,
    required this.onToggleTurn,
    required this.onRetry,
    required this.onLongPress,
    required this.onShare,
  });

  final Conversation conversation;
  final Set<String> openTurns;
  final ValueChanged<String> onToggleTurn;
  final ValueChanged<Message> onRetry;
  final ValueChanged<Message> onLongPress;
  final ValueChanged<Message> onShare;

  @override
  Widget build(BuildContext context) {
    final messages = conversation.messages;
    final lastAssistant = messages.lastIndexWhere((m) => !m.isUser);
    final children = <Widget>[];
    var act = 0;
    for (var i = 0; i < messages.length; i++) {
      final message = messages[i];
      final key = '${conversation.id}:$i';
      final isLatest = i == messages.length - 1;
      if (message.isUser) {
        act += 1;
        children.add(
          _ActRow(
            act: act,
            child: _UserBubble(
              message: message,
              onResend: () => onRetry(message),
              onLongPress: () => onLongPress(message),
            ),
          ),
        );
        continue;
      }
      final collapsed =
          message.status == 'done' &&
          i != lastAssistant &&
          !openTurns.contains(key);
      children.add(
        _ActRow(
          act: act,
          showNumber: false,
          child: _AssistantTurn(
            message: message,
            collapsed: collapsed,
            onToggle: () => onToggleTurn(key),
            isLatest: isLatest,
            onLongPress: () => onLongPress(message),
            onShare: () => onShare(message),
          ),
        ),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: children,
    );
  }
}

/// 一轮:左边台词轨,右边内容。轨的竖线一直连到下一轮。
class _ActRow extends StatelessWidget {
  const _ActRow({
    required this.act,
    required this.child,
    this.showNumber = true,
  });

  final int act;
  final Widget child;
  final bool showNumber;

  @override
  Widget build(BuildContext context) => Stack(
    children: [
      Positioned(
        left: 0,
        top: 0,
        bottom: 0,
        width: MaeveSize.actRail,
        child: ScriptRail(act: act, showNumber: showNumber),
      ),
      Padding(
        padding: const EdgeInsets.only(left: MaeveSize.actRail + MaeveSpace.s8),
        child: child,
      ),
    ],
  );
}

class _UserBubble extends StatelessWidget {
  const _UserBubble({
    required this.message,
    required this.onResend,
    required this.onLongPress,
  });

  final Message message;
  final VoidCallback onResend;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Padding(
      padding: const EdgeInsets.only(
        top: MaeveSpace.s12,
        bottom: MaeveSpace.s12,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: ConstrainedBox(
              constraints: BoxConstraints(
                maxWidth: MediaQuery.sizeOf(context).width * .8 - 60,
              ),
              child: Pressable(
                onLongPress: onLongPress,
                borderRadius: BorderRadius.circular(MaeveRadius.lg),
                scale: false,
                child: MaeveGlassSurface(
                  tier: MaeveGlassTier.card,
                  shape: MaeveShapes.userBubble,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 10,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (message.quote != null) ...[
                        _QuotedBlock(
                          text: message.quote!,
                          fromUser: message.quoteFromUser,
                        ),
                        const SizedBox(height: MaeveSpace.s6),
                      ],
                      Text(
                        message.text,
                        style: MaeveType.script.copyWith(
                          fontSize: 15.5,
                          height: 22.5 / 15.5,
                          color: colors.ink,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (message.queued)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Pressable(
                onTap: onResend,
                borderRadius: BorderRadius.circular(MaeveRadius.pill),
                child: Text(
                  '已排队 · 立即发送',
                  style: MaeveType.hint.copyWith(color: colors.accent),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _AssistantTurn extends StatelessWidget {
  const _AssistantTurn({
    required this.message,
    required this.collapsed,
    required this.onToggle,
    required this.isLatest,
    required this.onLongPress,
    required this.onShare,
  });

  final Message message;
  final bool collapsed;
  final VoidCallback onToggle;
  final bool isLatest;
  final VoidCallback onLongPress;
  final VoidCallback onShare;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    if (collapsed) {
      return Padding(
        padding: const EdgeInsets.only(bottom: MaeveSpace.s12),
        child: Align(
          alignment: Alignment.centerLeft,
          child: Pressable(
            onTap: onToggle,
            borderRadius: BorderRadius.circular(MaeveRadius.pill),
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: MaeveSpace.s10,
                vertical: 5,
              ),
              decoration: BoxDecoration(
                color: colors.fill,
                borderRadius: BorderRadius.circular(MaeveRadius.pill),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  StatusDot(tone: MaeveTone.ready, size: 5),
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(
                      [
                        message.statusLabel,
                        if (message.steps > 0) '${message.steps} 步',
                        if (message.elapsedLabel != null) message.elapsedLabel!,
                      ].join(' · '),
                      style: MaeveType.hint.copyWith(color: colors.ink3),
                    ),
                  ),
                  const SizedBox(width: 2),
                  Icon(Icons.expand_more, size: 14, color: colors.ink3),
                ],
              ),
            ),
          ),
        ),
      );
    }
    return GestureDetector(
      onLongPress: onLongPress,
      behavior: HitTestBehavior.translucent,
      child: Padding(
        padding: const EdgeInsets.only(bottom: MaeveSpace.s12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ExecutionPanel(
              message: message,
              isLatest: isLatest,
              onShare: onShare,
            ),
            if (message.text.isNotEmpty &&
                (!message.active || message.detail?.isNotEmpty == true) &&
                !(isLatest && message.hasSettlement))
              Container(
                decoration: BoxDecoration(
                  border: Border(
                    left: BorderSide(color: colors.rule, width: 1),
                  ),
                ),
                padding: const EdgeInsets.only(
                  left: 15,
                  top: 1,
                  bottom: 3,
                  right: 2,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    MaeveMarkdown(data: message.text),
                    if (!isLatest) ...[
                      const SizedBox(height: 2),
                      Pressable(
                        onTap: onToggle,
                        borderRadius: BorderRadius.circular(MaeveRadius.pill),
                        child: Text(
                          '收起',
                          style: MaeveType.hint.copyWith(color: colors.ink3),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// 引用块:一条竖线 + 两行摘要。
class _QuotedBlock extends StatelessWidget {
  const _QuotedBlock({required this.text, required this.fromUser});

  final String text;
  final bool fromUser;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Container(
      padding: const EdgeInsets.only(left: MaeveSpace.s8),
      decoration: BoxDecoration(
        border: Border(
          left: BorderSide(color: colors.ink3.withValues(alpha: .5), width: 2),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            fromUser ? '你' : 'Maeve',
            style: MaeveType.eyebrow.copyWith(color: colors.ink3),
          ),
          Text(
            text,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: MaeveType.hint.copyWith(color: colors.ink3),
          ),
        ],
      ),
    );
  }
}

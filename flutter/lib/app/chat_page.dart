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
import '../widgets/glass_surface.dart';
import '../widgets/maeve_mark.dart';

/// 聊天页(Maeve 的语言:剧本轨 + 纸条输入条)。
///
/// - 顶栏是**整条玻璃**:品牌标 + 标题 + 一行状态,左边抽屉键、右边两个动作键。
/// - 每一轮对话前面站一个 [ScriptRail](第几幕 + 幕号印章 + 竖线)。
/// - 你的话是"高一级表面 + 一道描边"的气泡(不是彩色实底);
///   她的回答是**一条竖线 + 全宽正文**(剧本边注),没有填充卡片。
/// - 输入条是 16 圆角的纸条,不是胶囊;浮在内容上,下面垫一段渐隐。
class ChatPage extends StatefulWidget {
  const ChatPage({super.key, required this.onMenu, required this.onQuickPanel});

  final VoidCallback onMenu;
  final VoidCallback onQuickPanel;

  @override
  State<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends State<ChatPage> {
  final TextEditingController _input = TextEditingController();
  final ScrollController _scroll = ScrollController();
  final Set<String> _openTurns = {};
  bool _showJump = false;

  /// 正在引用的那条消息(引用 chip)。
  ({String text, bool fromUser})? _quote;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(_onScroll);
  }

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _onScroll() {
    final show = _scroll.hasClients && _scroll.offset > 220;
    if (show != _showJump && mounted) setState(() => _showJump = show);
  }

  void _send(MaeveController controller) {
    final text = _input.text;
    if (text.trim().isEmpty) return;
    final quote = _quote;
    controller.send(
      text,
      quote: quote?.text,
      quoteFromUser: quote?.fromUser ?? false,
    );
    _input.clear();
    setState(() => _quote = null);
    _toBottom();
  }

  void _toBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      _scroll.animateTo(
        _scroll.position.maxScrollExtent,
        duration: AppMotion.of(context).standard,
        curve: MaeveMotion.glide,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final conversation = controller.active;
    final padding = MediaQuery.viewPaddingOf(context);
    final bottomSafe = padding.bottom;
    // 列表底部要让出浮起的输入条:引文 / 自动同意 / 活动条都会把它撑高。
    final reserve =
        MaeveSize.composer +
        MaeveSpace.s12 +
        MaeveSpace.s24 +
        (_quote != null ? 56 : 0) +
        (controller.blockedNotice != null ? 40 : 0) +
        (controller.generating ? 56 : 0);
    final topInset = padding.top + MaeveSize.topBarBrand + 10;
    final bottomInset = bottomSafe + reserve;
    // 空状态的可用高度:整屏减去顶栏与输入条。用它把问候语**垂直居中** ——
    // 上一版贴着顶栏排,中间空出半屏黑,读作"页面没加载完"。
    final emptyHeight =
        (MediaQuery.sizeOf(context).height - topInset - bottomInset).clamp(
          0.0,
          double.infinity,
        );

    return Stack(
      children: [
        Positioned.fill(
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
                    padding: const EdgeInsets.symmetric(
                      horizontal: MaeveSpace.s24,
                    ),
                    child: conversation.isEmpty
                        ? _EmptyHome(
                            minHeight: emptyHeight,
                            onPick: (text) {
                              _input.text = text;
                              _send(controller);
                            },
                          )
                        : _MessageList(
                            conversation: conversation,
                            openTurns: _openTurns,
                            onToggleTurn: (key) => setState(() {
                              if (!_openTurns.add(key)) _openTurns.remove(key);
                            }),
                            onRetry: controller.sendQueued,
                            onLongPress: (message) =>
                                _messageSheet(context, controller, message),
                          ),
                  ),
                ),
              ),
            ],
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
                icon: Icons.space_dashboard_outlined,
                label: '快捷面板',
                onTap: widget.onQuickPanel,
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
        if (_showJump)
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

/// 顶栏上的一个动作键(48 的可点域,40 的圆)。
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
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Tooltip(
      message: label,
      child: Semantics(
        button: true,
        label: label,
        child: InkWell(
          customBorder: const CircleBorder(),
          splashFactory: NoSplash.splashFactory,
          highlightColor: Colors.transparent,
          onTap: () {
            MaeveHaptics.play(MaeveHaptic.tick);
            onTap();
          },
          child: SizedBox.square(
            dimension: 48,
            child: Center(child: Icon(icon, size: 20, color: colors.ink)),
          ),
        ),
      ),
    );
  }
}

/// Fit the idle stage to the real space between header and composer. A quiet
/// centre is intentional; scroll is reserved for small screens and large text.
class _EmptyHome extends StatelessWidget {
  const _EmptyHome({required this.onPick, required this.minHeight});
  final ValueChanged<String> onPick;
  final double minHeight;
  static const _prompts = [
    ('理清思路', '帮我理清一个决定'),
    ('规划项目', '规划一个项目'),
    ('整理笔记', '把一段想法整理成笔记'),
  ];
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final compact = minHeight < 390;
    final scale = MediaQuery.textScalerOf(context).scale(1);
    final minimum = (compact ? 280.0 : 325.0) + (scale - 1).clamp(0, 2) * 120;
    final height = minHeight < minimum ? minimum : minHeight;
    return SizedBox(
      height: height,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Spacer(flex: compact ? 1 : 2),
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
          const Spacer(flex: 3),
          Text(
            '试着交给 Maeve',
            textAlign: TextAlign.center,
            style: MaeveType.hint.copyWith(color: c.ink3),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              for (var i = 0; i < _prompts.length; i++) ...[
                if (i > 0) const SizedBox(width: 8),
                Expanded(
                  child: Pressable(
                    key: ValueKey('home-prompt-$i'),
                    semanticLabel: _prompts[i].$2,
                    scale: false,
                    onTap: () => onPick(_prompts[i].$2),
                    child: MaeveGlassSurface(
                      shape: RoundedSuperellipseBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      padding: const EdgeInsets.symmetric(
                        vertical: 14,
                        horizontal: 4,
                      ),
                      child: Text(
                        _prompts[i].$1,
                        textAlign: TextAlign.center,
                        style: MaeveType.label.copyWith(
                          color: c.ink2,
                          fontSize: 12.5,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
          const SizedBox(height: 18),
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
  });

  final Conversation conversation;
  final Set<String> openTurns;
  final ValueChanged<String> onToggleTurn;
  final ValueChanged<Message> onRetry;
  final ValueChanged<Message> onLongPress;

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
      final collapsed = i != lastAssistant && !openTurns.contains(key);
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
  Widget build(BuildContext context) => IntrinsicHeight(
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ScriptRail(act: act, showNumber: showNumber),
        const SizedBox(width: MaeveSpace.s8),
        Expanded(child: child),
      ],
    ),
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
  });

  final Message message;
  final bool collapsed;
  final VoidCallback onToggle;
  final bool isLatest;
  final VoidCallback onLongPress;

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
                  Text(
                    '已完成${message.steps > 0 ? ' · ${message.steps} 步' : ''}',
                    style: MaeveType.hint.copyWith(color: colors.ink3),
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
            if (message.steps > 0) ...[
              ThinkingPill(steps: message.steps, lines: message.thinking),
              const SizedBox(height: MaeveSpace.s10),
            ],
            // 回答:左侧一条竖线 + 全宽正文,没有填充卡片。
            Container(
              decoration: BoxDecoration(
                border: Border(left: BorderSide(color: colors.rule, width: 1)),
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
                  Text(
                    message.text,
                    style: MaeveType.script.copyWith(color: colors.ink),
                  ),
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

/// 输入条那一整块:引文 / 自动同意 / 活动条叠在纸条上方,底下垫一段渐隐。
class _ComposerDock extends StatelessWidget {
  const _ComposerDock({
    required this.controller,
    required this.input,
    required this.quote,
    required this.onClearQuote,
    required this.onSend,
    required this.onStop,
  });

  final MaeveController controller;
  final TextEditingController input;
  final String? quote;
  final VoidCallback onClearQuote;
  final VoidCallback onSend;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final bottom = MediaQuery.viewPaddingOf(context).bottom;
    return Container(
      padding: EdgeInsets.only(bottom: bottom + MaeveSpace.s6),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            colors.bg.withValues(alpha: 0),
            colors.bg.withValues(alpha: .92),
            colors.bg,
          ],
          stops: const [0, .55, 1],
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          MaeveSpace.s12,
          MaeveSpace.s10,
          MaeveSpace.s12,
          MaeveSpace.s6,
        ),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: MaeveSpace.readingWidth,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                ExpandReveal(
                  visible: quote != null,
                  child: quote == null
                      ? const SizedBox.shrink()
                      : Padding(
                          padding: const EdgeInsets.only(bottom: MaeveSpace.s6),
                          child: _QuoteChip(
                            text: quote!,
                            onClear: onClearQuote,
                          ),
                        ),
                ),
                ExpandReveal(
                  visible: controller.blockedNotice != null,
                  child: controller.blockedNotice == null
                      ? const SizedBox.shrink()
                      : Padding(
                          padding: const EdgeInsets.only(bottom: MaeveSpace.s6),
                          child: _BlockedNotice(
                            text: controller.blockedNotice!,
                          ),
                        ),
                ),
                if (controller.generating) ...[
                  ActivityStrip(
                    text: '正在走这一步',
                    states: _rollStates(controller),
                    onStop: onStop,
                  ),
                  const SizedBox(height: MaeveSpace.s6),
                ],
                _ComposerBar(
                  controller: controller,
                  input: input,
                  onSend: onSend,
                  onStop: onStop,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 钢琴卷的状态:已完成的步 + 正在走的那一步。
  List<NoteState> _rollStates(MaeveController controller) {
    final steps = controller.active.messages
        .where((m) => !m.isUser)
        .fold<int>(0, (sum, m) => sum + m.steps);
    final total = (steps + 3).clamp(4, 12);
    return [
      for (var i = 0; i < total; i++)
        i < steps
            ? NoteState.done
            : (i == steps ? NoteState.active : NoteState.pending),
    ];
  }
}

/// 16 圆角的纸条输入栏(不是胶囊)。
class _ComposerBar extends StatefulWidget {
  const _ComposerBar({
    required this.controller,
    required this.input,
    required this.onSend,
    required this.onStop,
  });

  final MaeveController controller;
  final TextEditingController input;
  final VoidCallback onSend;
  final VoidCallback onStop;

  @override
  State<_ComposerBar> createState() => _ComposerBarState();
}

class _ComposerBarState extends State<_ComposerBar> {
  final FocusNode _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    widget.input.addListener(_sync);
    _focus.addListener(_sync);
  }

  @override
  void dispose() {
    widget.input.removeListener(_sync);
    _focus.dispose();
    super.dispose();
  }

  void _sync() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final colors = MaeveTheme.of(context).colors;
    final canSend = widget.input.text.trim().isNotEmpty;
    final busy = controller.generating;
    return AnimatedContainer(
      duration: AppMotion.of(context).quick,
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(MaeveRadius.composer + 4),
        border: Border.all(
          color: _focus.hasFocus
              ? colors.accent.withValues(alpha: .55)
              : Colors.transparent,
        ),
      ),
      child: MaeveGlassSurface(
        tier: MaeveGlassTier.chrome,
        shape: MaeveShapes.composer,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: MaeveSize.composer),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                child: TextField(
                  key: const ValueKey('message-input'),
                  controller: widget.input,
                  focusNode: _focus,
                  minLines: 1,
                  maxLines: 5,
                  style: MaeveType.body.copyWith(color: colors.ink),
                  textAlignVertical: TextAlignVertical.center,
                  textInputAction: TextInputAction.newline,
                  cursorColor: colors.accent,
                  decoration: InputDecoration(
                    isDense: true,
                    contentPadding: EdgeInsets.zero,
                    border: InputBorder.none,
                    hintText: controller.connected
                        ? (busy ? '插话给当前这一步…' : '想做什么？交给 Maeve…')
                        : '未连接，暂时不能发送',
                    hintStyle: MaeveType.body.copyWith(color: colors.ink3),
                  ),
                  onSubmitted: (_) => canSend ? widget.onSend() : null,
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.s8),
                child: Row(
                  children: [
                    Expanded(
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: Tooltip(
                          message: '模型配置',
                          child: TextButton.icon(
                            onPressed: () => AppPage.open(
                              context,
                              const ModelSettingsPage(),
                            ),
                            icon: Icon(
                              Icons.auto_awesome_outlined,
                              color: colors.accent,
                              size: 16,
                            ),
                            label: Text(
                              controller.model,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: MaeveType.hint.copyWith(
                                color: colors.ink2,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                    if (busy)
                      _RoundKey(
                        icon: Icons.stop_rounded,
                        tooltip: '停止生成',
                        ink: true,
                        onTap: widget.onStop,
                      ),
                    if (!busy || canSend)
                      _RoundKey(
                        key: const ValueKey('send-message'),
                        icon: Icons.arrow_upward_rounded,
                        tooltip: '发送消息',
                        accent: canSend,
                        enabled: canSend,
                        onTap: canSend ? widget.onSend : null,
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 40 圆键:发送是强调色实心,附件是淡底;不可用时降透明度(不是变灰点不动)。
class _RoundKey extends StatelessWidget {
  const _RoundKey({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onTap,
    this.accent = false,
    this.ink = false,
    this.enabled = true,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onTap;
  final bool accent;
  final bool ink;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final background = accent
        ? colors.accent
        : (ink ? colors.ink : colors.fill);
    final foreground = accent
        ? colors.onAccent
        : (ink ? colors.bg : colors.ink);
    return Tooltip(
      message: tooltip,
      child: Semantics(
        button: true,
        enabled: enabled,
        label: tooltip,
        child: Pressable(
          onTap: enabled ? onTap : null,
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
          haptic: enabled ? MaeveHaptic.light : null,
          child: AnimatedOpacity(
            duration: AppMotion.of(context).quick,
            opacity: accent || ink || enabled ? 1 : .5,
            child: Container(
              width: MaeveSize.iconButton,
              height: MaeveSize.iconButton,
              margin: const EdgeInsets.symmetric(vertical: 6),
              decoration: BoxDecoration(
                color: background,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 20, color: foreground),
            ),
          ),
        ),
      ),
    );
  }
}

/// 发送被拦下的原因(宿主前置条件没满足):贴在输入条上方,warn 色,说清缺什么。
class _BlockedNotice extends StatelessWidget {
  const _BlockedNotice({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;
    return Container(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.s12,
        MaeveSpace.s8,
        MaeveSpace.s12,
        MaeveSpace.s8,
      ),
      decoration: BoxDecoration(
        color: colors.warnSoft,
        borderRadius: BorderRadius.circular(MaeveRadius.md),
        border: Border.all(color: colors.edge, width: .5),
      ),
      child: Row(
        children: [
          Icon(
            Icons.error_outline_rounded,
            size: 15,
            color: theme.semantic.warn,
          ),
          const SizedBox(width: MaeveSpace.s8),
          Expanded(
            child: Text(
              text,
              style: MaeveType.hint.copyWith(color: colors.ink2),
            ),
          ),
        ],
      ),
    );
  }
}

/// 引文 chip:一行摘要 + 取消,贴在输入条上方。
class _QuoteChip extends StatelessWidget {
  const _QuoteChip({required this.text, required this.onClear});

  final String text;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Container(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.s12,
        MaeveSpace.s8,
        MaeveSpace.s6,
        MaeveSpace.s8,
      ),
      decoration: BoxDecoration(
        color: colors.accentSoft,
        borderRadius: BorderRadius.circular(MaeveRadius.md),
        border: Border.all(color: colors.edge, width: .5),
      ),
      child: Row(
        children: [
          Icon(Icons.format_quote_rounded, size: 15, color: colors.accent),
          const SizedBox(width: MaeveSpace.s8),
          Expanded(
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: MaeveType.hint.copyWith(color: colors.ink2),
            ),
          ),
          Pressable(
            onTap: onClear,
            borderRadius: BorderRadius.circular(MaeveRadius.pill),
            semanticLabel: '取消引用',
            child: SizedBox.square(
              dimension: 28,
              child: Icon(Icons.close, size: 15, color: colors.ink3),
            ),
          ),
        ],
      ),
    );
  }
}

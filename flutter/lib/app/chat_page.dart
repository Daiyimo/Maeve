import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../design/maeve_motion.dart';
import '../design/maeve_tokens.dart';
import '../models/conversation.dart';
import '../ui/common/app_sheet.dart';
import '../ui/common/pressable.dart';
import '../ui/common/haptics.dart';
import '../ui/more/more_pages.dart';
import '../ui/settings/settings_components.dart';
import '../ui/common/status.dart';
import '../widgets/glass_surface.dart';
import '../widgets/maeve_mark.dart';
import 'maeve_controller.dart';

/// B3 聊天页 + B4 顶栏 + B5 输入条。
///
/// 顶栏是浮在列表上的几枚玻璃件,列表从它下面穿过去;输入条浮在阅读列上,
/// 不 dock 在屏幕底部 —— 这两件事决定了它不是"安卓工具栏 + 底部输入框"。
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

  /// 正在引用的那条消息(B3 的引用 chip)。
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
    final safeTop = padding.top;
    // Scaffold 自己会为键盘让位(resizeToAvoidBottomInset),这里不要再加一次
    // viewInsets,否则输入条会被顶到屏幕中间。只补底部的系统手势区。
    final safeBottom = padding.bottom;
    // 列表底部要让出浮起的输入条:引文 chip / 自动同意 / 活动条都会把它撑高,
    // 写死一个常数会让最后一条消息被盖住。
    final reserve =
        56 +
        MaeveSpace.floatInset +
        MaeveSpace.lg +
        (_quote != null ? 56 : 0) +
        (controller.autoApprove ? 44 : 0) +
        (controller.generating ? 52 : 0);
    final width = MediaQuery.sizeOf(context).width;
    final composerWidth = (MaeveSpace.floatingWidth).clamp(
      0.0,
      width - 2 * MaeveSpace.floatInset,
    );

    return Stack(
      children: [
        // 列表:顶部给顶栏留白,底部给浮起的输入条留白。
        Positioned.fill(
          child: ListView(
            key: const ValueKey('chat-list'),
            controller: _scroll,
            padding: EdgeInsets.only(
              top: safeTop + MaeveLayout.headerHeight + MaeveSpace.sm,
              bottom: reserve,
            ),
            children: [
              Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: MaeveSpace.readingWidth,
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: MaeveSpace.gutter,
                    ),
                    child: conversation.isEmpty
                        ? _EmptyHome(
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
        // B4:浮动顶栏。
        Positioned(
          top: 0,
          left: 0,
          right: 0,
          child: SafeArea(
            bottom: false,
            child: _ChatHeader(
              title: conversation.title,
              status: controller.statusLine,
              tone: controller.generating
                  ? MaeveTone.running
                  : (controller.connected ? MaeveTone.ready : MaeveTone.idle),
              model: controller.model,
              tier: controller.modelTier,
              showModel: width >= 520,
              onMenu: widget.onMenu,
              onTitle: () => _showConversationDetails(context, controller),
              onModel: () => _showModelSheet(context, controller),
              onNew: () => controller.create(),
              onQuick: widget.onQuickPanel,
              busy: controller.generating,
            ),
          ),
        ),
        // B5:输入条浮在阅读列上,离底部 12。
        Positioned(
          left: 0,
          right: 0,
          bottom: safeBottom + MaeveSpace.floatInset,
          child: Center(
            child: SizedBox(
              width: composerWidth,
              child: _Composer(
                controller: controller,
                input: _input,
                quote: _quote?.text,
                onClearQuote: () => setState(() => _quote = null),
                onSend: () => _send(controller),
                onStop: controller.stop,
                onAttach: () => _attachmentSheet(context),
              ),
            ),
          ),
        ),
        if (_showJump)
          Positioned(
            right: MaeveSpace.gutter,
            bottom: safeBottom + 96,
            child: _JumpToBottom(onTap: _toBottom),
          ),
      ],
    );
  }

  /// 长按一条消息:复制 / 引用。引用会落到输入条上方的 chip 里。
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
            const SizedBox(height: MaeveSpace.sm),
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

  /// 附件来源:列清楚可选的东西,接桥前每项都明确说"还没有"。
  Future<void> _attachmentSheet(BuildContext context) => showMaeveSheet<void>(
    context: context,
    builder: (context) => AppSheet(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SheetTitleBar(title: '添加内容', subtitle: '接入桥接后，这里会打开系统的选择器'),
          const SizedBox(height: MaeveSpace.sm),
          ListPageRow(
            title: '照片',
            subtitle: '系统相册',
            leading: const Icon(Icons.photo_library_outlined, size: 19),
            onTap: () {
              Navigator.of(context).pop();
              _notice(context, '相册选择将通过 Android 平台桥接接入。');
            },
          ),
          ListPageRow(
            title: '文件',
            subtitle: '文稿与下载',
            leading: const Icon(Icons.attach_file_rounded, size: 19),
            onTap: () {
              Navigator.of(context).pop();
              _notice(context, '文件选择将通过 Android 平台桥接接入。');
            },
          ),
          ListPageRow(
            title: '当前屏幕',
            subtitle: '按需截取，只在本机处理',
            leading: const Icon(Icons.screenshot_monitor_outlined, size: 19),
            onTap: () {
              Navigator.of(context).pop();
              _notice(context, '截屏需要先开启屏幕读取权限。');
            },
          ),
        ],
      ),
    ),
  );

  void _notice(BuildContext context, String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _showConversationDetails(
    BuildContext context,
    MaeveController controller,
  ) {
    final conversation = controller.active;
    return showMaeveSheet<void>(
      context: context,
      builder: (context) => AppSheet(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SheetTitleBar(
              title: conversation.title,
              subtitle: '会话详情',
              actions: [
                IconButton(
                  tooltip: '关闭',
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close, size: 20),
                ),
              ],
            ),
            const SizedBox(height: MaeveSpace.md),
            _DetailRow(
              label: '模型',
              value: '${controller.model} · ${controller.modelTier}',
            ),
            _DetailRow(label: '消息', value: '${conversation.messages.length} 条'),
            _DetailRow(
              label: '最近活动',
              value: _relativeTime(conversation.updatedAt),
            ),
            _DetailRow(label: '状态', value: conversation.pinned ? '已置顶' : '未置顶'),
            const SizedBox(height: MaeveSpace.sm),
            ListPageRow(
              title: '分享这段对话',
              subtitle: '复制全文或导出 Markdown',
              leading: const Icon(Icons.ios_share_rounded, size: 19),
              onTap: () {
                Navigator.of(context).pop();
                showShareSheet(context, controller);
              },
            ),
            const SizedBox(height: MaeveSpace.sm),
            Text(
              '接入桥接后，这里会显示真实会话的运行细节。',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showModelSheet(
    BuildContext context,
    MaeveController controller,
  ) => showMaeveSheet<void>(
    context: context,
    builder: (context) => AppSheet(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SheetTitleBar(title: '模型', subtitle: '思考档位越高，越慢也越稳'),
          const SizedBox(height: MaeveSpace.sm),
          for (final tier in ['高', '中', '低'])
            ListPageRow(
              title: 'Maeve · $tier',
              subtitle: switch (tier) {
                '高' => '多步推理，适合复杂任务',
                '中' => '平衡速度与质量',
                _ => '最快，适合短问答',
              },
              selected: controller.modelTier == tier,
              trailing: controller.modelTier == tier
                  ? Icon(
                      Icons.check,
                      size: 18,
                      color: Theme.of(context).colorScheme.primary,
                    )
                  : null,
              onTap: () {
                controller.setModelTier(tier);
                Navigator.of(context).pop();
              },
            ),
        ],
      ),
    ),
  );
}

String _relativeTime(DateTime time) {
  final difference = DateTime.now().difference(time);
  if (difference.inMinutes < 1) return '刚刚';
  if (difference.inHours < 1) return '${difference.inMinutes} 分钟前';
  if (difference.inDays < 1) return '${difference.inHours} 小时前';
  if (difference.inDays == 1) return '昨天';
  return '${difference.inDays} 天前';
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 5),
    child: Row(
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        const Spacer(),
        Text(value, style: Theme.of(context).textTheme.bodyMedium),
      ],
    ),
  );
}

/// B4 顶栏:≡ 圆钮 + 标题胶囊(标题 + 状态行) + 模型胶囊 + ＋ 圆钮。
///
/// 一枚 `BackdropGroup` 罩住整排,四块玻璃共用一次背景采样;件与件之间不重叠,
/// 所以可以共用;整条没有色带,内容滚到栏下才靠页面渐隐挡。
class _ChatHeader extends StatelessWidget {
  const _ChatHeader({
    required this.title,
    required this.status,
    required this.tone,
    required this.model,
    required this.tier,
    required this.showModel,
    required this.onMenu,
    required this.onTitle,
    required this.onModel,
    required this.onNew,
    required this.onQuick,
    required this.busy,
  });

  final String title;
  final String status;
  final MaeveTone tone;
  final String model;
  final String tier;
  final bool showModel;
  final VoidCallback onMenu;
  final VoidCallback onTitle;
  final VoidCallback onModel;
  final VoidCallback onNew;
  final VoidCallback onQuick;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    return BackdropGroup(
      child: SizedBox(
        height: MaeveLayout.headerHeight,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.md),
          child: Row(
            children: [
              _HeaderCircle(
                icon: Icons.menu_rounded,
                tooltip: '打开对话列表',
                onTap: onMenu,
              ),
              const SizedBox(width: MaeveSpace.sm),
              Expanded(
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 360),
                    child: _TitlePill(
                      title: title,
                      status: status,
                      tone: tone,
                      onTap: onTitle,
                    ),
                  ),
                ),
              ),
              if (showModel) ...[
                const SizedBox(width: MaeveSpace.sm),
                _ModelPill(
                  model: model,
                  tier: tier,
                  busy: busy,
                  onTap: onModel,
                  onLongPress: onQuick,
                ),
              ],
              const SizedBox(width: MaeveSpace.sm),
              _HeaderCircle(
                icon: Icons.add_rounded,
                tooltip: '新对话',
                onTap: onNew,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _HeaderCircle extends StatelessWidget {
  const _HeaderCircle({
    required this.icon,
    required this.tooltip,
    required this.onTap,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Tooltip(
    message: tooltip,
    child: MaeveGlassSurface(
      tier: MaeveGlassTier.chrome,
      circle: true,
      grouped: true,
      onTap: onTap,
      child: SizedBox.square(
        dimension: MaeveLayout.touch,
        child: Icon(icon, size: 20),
      ),
    ),
  );
}

/// 标题胶囊:标题 + 状态行(点 + 一句人话)。点按展开会话详情。
class _TitlePill extends StatelessWidget {
  const _TitlePill({
    required this.title,
    required this.status,
    required this.tone,
    required this.onTap,
  });

  final String title;
  final String status;
  final MaeveTone tone;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    label: '$title，$status',
    child: MaeveGlassSurface(
      tier: MaeveGlassTier.chrome,
      stadium: true,
      grouped: true,
      onTap: onTap,
      padding: const EdgeInsets.symmetric(
        horizontal: MaeveSpace.lg,
        vertical: 4,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 1),
          HeaderStatus(text: status, tone: tone, maxWidth: 190),
        ],
      ),
    ),
  );
}

/// 模型胶囊:✦ Maeve · 高 ⌄。
class _ModelPill extends StatelessWidget {
  const _ModelPill({
    required this.model,
    required this.tier,
    required this.busy,
    required this.onTap,
    required this.onLongPress,
  });

  final String model;
  final String tier;
  final bool busy;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return MaeveGlassSurface(
      tier: MaeveGlassTier.chrome,
      stadium: true,
      grouped: true,
      onTap: onTap,
      onLongPress: onLongPress,
      padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.md),
      child: SizedBox(
        height: MaeveLayout.touch,
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.auto_awesome, size: 14, color: scheme.primary),
            const SizedBox(width: 5),
            Text(model, style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(width: 4),
            Text('· $tier', style: Theme.of(context).textTheme.bodySmall),
            Icon(Icons.expand_more, size: 15, color: scheme.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}

class _JumpToBottom extends StatelessWidget {
  const _JumpToBottom({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => MaeveGlassSurface(
    tier: MaeveGlassTier.chrome,
    circle: true,
    onTap: onTap,
    child: const SizedBox.square(
      dimension: 38,
      child: Icon(Icons.arrow_downward_rounded, size: 18),
    ),
  );
}

/// 空状态:品牌标识 + 一句话 + 几条可点的建议。
class _EmptyHome extends StatelessWidget {
  const _EmptyHome({required this.onPick});

  final ValueChanged<String> onPick;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final suggestions = [
      ('帮我理清一个决定', '把利弊摊开，找出真正在意的那一条'),
      ('规划一个项目', '从目标倒推，排出可执行的下一步'),
      ('把一段想法整理成笔记', '保留原意，删掉重复'),
    ];
    return Padding(
      padding: const EdgeInsets.only(top: MaeveSpace.xxl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              const MaeveMark(size: 54, state: MarkState.armed),
              const SizedBox(width: MaeveSpace.lg),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('有声音的宿主', style: maeveEyebrow(context)),
                    const SizedBox(height: 4),
                    Text(
                      '一个念头，从这里开始。',
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: MaeveSpace.lg),
          Text(
            'Maeve 已准备好陪你梳理想法、探索问题，或规划下一步。',
            style: Theme.of(context).textTheme.bodyMedium
                ?.copyWith(color: scheme.onSurfaceVariant),
          ),
          const SizedBox(height: MaeveSpace.xl),
          for (final (title, subtitle) in suggestions)
            Padding(
              padding: const EdgeInsets.only(bottom: MaeveSpace.sm),
              child: Pressable(
                onTap: () => onPick(title),
                borderRadius: BorderRadius.circular(MaeveRadius.md),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: MaeveSpace.md,
                    vertical: MaeveSpace.md,
                  ),
                  decoration: BoxDecoration(
                    color: scheme.onSurface.withValues(alpha: .04),
                    borderRadius: BorderRadius.circular(MaeveRadius.md),
                    border: Border.all(
                      color: scheme.outline.withValues(alpha: .5),
                      width: .5,
                    ),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        Icons.north_east_rounded,
                        size: 15,
                        color: scheme.primary,
                      ),
                      const SizedBox(width: MaeveSpace.md),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              title,
                              style: Theme.of(context).textTheme.bodyLarge,
                            ),
                            Text(
                              subtitle,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// 消息列表:同轮内 8、换轮 24;已完成的轮次可折叠成一行。
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
    for (var i = 0; i < messages.length; i++) {
      final message = messages[i];
      final key = '${conversation.id}:$i';
      final isLatest = i == messages.length - 1;
      if (message.isUser) {
        children.add(
          Padding(
            padding: EdgeInsets.only(
              top: i == 0 ? MaeveSpace.lg : MaeveSpace.betweenTurns,
            ),
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
        Padding(
          padding: EdgeInsets.only(
            top: i == 0 ? MaeveSpace.lg : MaeveSpace.betweenTurns,
          ),
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
    final surfaces = MaeveSurfaces.of(context);
    final bubble = surfaces.userBubble;
    final ink = surfaces.onUserBubble;
    return Align(
      alignment: Alignment.centerRight,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          Container(
            constraints: const BoxConstraints(maxWidth: 560),
            padding: const EdgeInsets.symmetric(
              horizontal: MaeveSpace.lg,
              vertical: MaeveSpace.md,
            ),
            decoration: BoxDecoration(
              color: bubble,
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(MaeveRadius.lg),
                topRight: Radius.circular(MaeveRadius.lg),
                bottomLeft: Radius.circular(MaeveRadius.lg),
                bottomRight: Radius.circular(MaeveRadius.xs),
              ),
            ),
            child: GestureDetector(
              onLongPress: onLongPress,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (message.quote != null) ...[
                    _QuotedBlock(
                      text: message.quote!,
                      fromUser: message.quoteFromUser,
                      onLight: true,
                    ),
                    const SizedBox(height: MaeveSpace.sm),
                  ],
                  Text(
                    message.text,
                    style: Theme.of(context).textTheme.bodyLarge
                        ?.copyWith(color: ink),
                  ),
                ],
              ),
            ),
          ),
          if (message.queued)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Pressable(
                onTap: onResend,
                borderRadius: BorderRadius.circular(MaeveRadius.pill),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: MaeveSpace.sm,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primary
                        .withValues(alpha: .12),
                    borderRadius: BorderRadius.circular(MaeveRadius.pill),
                  ),
                  child: Text(
                    '已排队 · 立即发送',
                    style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
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
    final scheme = Theme.of(context).colorScheme;
    if (collapsed) {
      return Pressable(
        onTap: onToggle,
        borderRadius: BorderRadius.circular(MaeveRadius.pill),
        child: Container(
          padding: const EdgeInsets.symmetric(
            horizontal: MaeveSpace.md,
            vertical: 6,
          ),
          decoration: BoxDecoration(
            color: scheme.onSurface.withValues(alpha: .04),
            borderRadius: BorderRadius.circular(MaeveRadius.pill),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const StatusDot(tone: MaeveTone.idle, size: 5),
              const SizedBox(width: 6),
              Text(
                '已完成${message.steps > 0 ? ' · ${message.steps} 步' : ''}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(width: 2),
              Icon(Icons.expand_more, size: 15, color: scheme.onSurfaceVariant),
            ],
          ),
        ),
      );
    }
    return GestureDetector(
      onLongPress: onLongPress,
      behavior: HitTestBehavior.translucent,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: 2),
            child: MaeveMark(size: 22),
          ),
          const SizedBox(width: MaeveSpace.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (message.steps > 0) ...[
                  ThinkingPill(
                    steps: message.steps,
                    lines: message.thinking,
                    expanded: false,
                  ),
                  const SizedBox(height: MaeveSpace.inTurn),
                ],
                Text(
                  message.text,
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
                if (!isLatest) ...[
                  const SizedBox(height: 2),
                  Pressable(
                    onTap: onToggle,
                    borderRadius: BorderRadius.circular(MaeveRadius.pill),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: Text(
                        '收起',
                        style: Theme.of(context).textTheme.labelSmall,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// B5 输入条:chrome 玻璃 + 圆角 28 + 两颗 40 圆键。
class _Composer extends StatefulWidget {
  const _Composer({
    required this.controller,
    required this.input,
    required this.onSend,
    required this.onStop,
    required this.onAttach,
    required this.onClearQuote,
    this.quote,
  });

  final MaeveController controller;
  final TextEditingController input;
  final VoidCallback onSend;
  final VoidCallback onStop;
  final VoidCallback onAttach;
  final VoidCallback onClearQuote;
  final String? quote;

  @override
  State<_Composer> createState() => _ComposerState();
}

class _ComposerState extends State<_Composer> {
  @override
  void initState() {
    super.initState();
    widget.input.addListener(_sync);
  }

  @override
  void dispose() {
    widget.input.removeListener(_sync);
    super.dispose();
  }

  void _sync() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final scheme = Theme.of(context).colorScheme;
    final canSend = widget.input.text.trim().isNotEmpty;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // 引文 tray:在上方,expand_reveal 进出。
        ExpandReveal(
          visible: widget.quote != null,
          // 收起后不留在树里:读屏不该读到一个 0 高度的"取消引用"。
          child: widget.quote == null
              ? const SizedBox.shrink()
              : Padding(
                  padding: const EdgeInsets.only(bottom: MaeveSpace.sm),
                  child: _QuoteChip(
                    text: widget.quote!,
                    onClear: widget.onClearQuote,
                  ),
                ),
        ),
        // 自动同意开着时一直看得见(状态不能藏在设置里)。
        if (controller.autoApprove) ...[
          Align(
            alignment: Alignment.centerLeft,
            child: AutoApprovePill(onTap: controller.toggleAutoApprove),
          ),
          const SizedBox(height: MaeveSpace.sm),
        ],
        if (controller.generating) ...[
          ActivityStrip(text: 'Maeve 正在思考', onStop: widget.onStop),
          const SizedBox(height: MaeveSpace.sm),
        ],
        MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          borderRadius: const BorderRadius.all(Radius.circular(MaeveRadius.xl)),
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              _RoundKey(
                key: const ValueKey('attach-file'),
                icon: Icons.add_rounded,
                tooltip: '添加内容',
                filled: false,
                onTap: widget.onAttach,
              ),
              const SizedBox(width: MaeveSpace.xs),
              Expanded(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 40),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: MaeveSpace.sm,
                      vertical: 10,
                    ),
                    child: TextField(
                      key: const ValueKey('message-input'),
                      controller: widget.input,
                      minLines: 1,
                      maxLines: 6,
                      style: Theme.of(context).textTheme.bodyLarge,
                      textAlignVertical: TextAlignVertical.center,
                      textInputAction: TextInputAction.newline,
                      cursorColor: scheme.primary,
                      decoration: InputDecoration(
                        isDense: true,
                        border: InputBorder.none,
                        hintText: controller.connected
                            ? '给 Maeve 一点内容…'
                            : '未连接，暂时不能发送',
                        hintStyle: Theme.of(context).textTheme.bodyLarge
                            ?.copyWith(color: scheme.onSurfaceVariant),
                      ),
                      onSubmitted: (_) => canSend ? widget.onSend() : null,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: MaeveSpace.xs),
              _RoundKey(
                key: const ValueKey('send-message'),
                icon: controller.generating
                    ? Icons.stop_rounded
                    : Icons.arrow_upward_rounded,
                tooltip: controller.generating ? '停止生成' : '发送消息',
                filled: !controller.generating && canSend,
                faded: controller.generating,
                onTap: controller.generating
                    ? widget.onStop
                    : (canSend ? widget.onSend : null),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// 40 圆键:发送是主色实心,附件是淡底;不可用时降透明度(不是变灰点不动)。
class _RoundKey extends StatelessWidget {
  const _RoundKey({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onTap,
    this.filled = false,
    this.faded = false,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onTap;
  final bool filled;
  final bool faded;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final enabled = onTap != null;
    final background = filled
        ? scheme.primary
        : (faded
              ? scheme.onSurface.withValues(alpha: .10)
              : scheme.onSurface.withValues(alpha: .06));
    final foreground = filled ? scheme.onPrimary : scheme.onSurface;
    return Tooltip(
      message: tooltip,
      child: Semantics(
        button: true,
        label: tooltip,
        child: Pressable(
          onTap: onTap,
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
          haptic: enabled ? MaeveHaptic.light : null,
          child: AnimatedOpacity(
            duration: const Duration(milliseconds: 160),
            opacity: enabled || faded ? 1 : .55,
            child: Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: background,
                shape: BoxShape.circle,
              ),
              child: Icon(icon, size: 19, color: foreground),
            ),
          ),
        ),
      ),
    );
  }
}

/// 引用块:一条竖线 + 两行摘要。用户气泡里也用它(浅底,降透明度)。
class _QuotedBlock extends StatelessWidget {
  const _QuotedBlock({
    required this.text,
    required this.fromUser,
    this.onLight = false,
  });

  final String text;
  final bool fromUser;
  final bool onLight;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final tone = onLight
        ? scheme.onSurface.withValues(alpha: .55)
        : scheme.onSurfaceVariant;
    return Container(
      padding: const EdgeInsets.only(left: MaeveSpace.sm),
      decoration: BoxDecoration(
        border: Border(
          left: BorderSide(color: tone.withValues(alpha: .5), width: 2),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            fromUser ? '你' : 'Maeve',
            style: Theme.of(context).textTheme.labelSmall
                ?.copyWith(color: tone),
          ),
          Text(
            text,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: tone),
          ),
        ],
      ),
    );
  }
}

/// 输入条上方的引文 chip:一行摘要 + 取消。
class _QuoteChip extends StatelessWidget {
  const _QuoteChip({required this.text, required this.onClear});

  final String text;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.md,
        MaeveSpace.sm,
        MaeveSpace.sm,
        MaeveSpace.sm,
      ),
      decoration: BoxDecoration(
        color: scheme.primary.withValues(alpha: .10),
        borderRadius: BorderRadius.circular(MaeveRadius.md),
        border: Border.all(
          color: scheme.primary.withValues(alpha: .22),
          width: .5,
        ),
      ),
      child: Row(
        children: [
          Icon(Icons.format_quote_rounded, size: 16, color: scheme.primary),
          const SizedBox(width: MaeveSpace.sm),
          Expanded(
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          Pressable(
            onTap: onClear,
            borderRadius: BorderRadius.circular(MaeveRadius.pill),
            semanticLabel: '取消引用',
            child: SizedBox.square(
              dimension: 28,
              child: Icon(
                Icons.close,
                size: 15,
                color: scheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

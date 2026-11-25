part of 'chat_page.dart';

/// 输入条那一整块:引文 / 自动同意 / 活动条叠在纸条上方;玻璃自行采样。
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

  Future<void> _confirmUnresolved(BuildContext context) async {
    final key = controller.draftKey;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => MaeveGlassDialog(
        title: const Text('解除发送等待'),
        content: const Text('上一条任务可能已执行。请先核对对话和实际结果。解除后会保留草稿，发送前可以继续编辑。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('解除等待'),
          ),
        ],
      ),
    );
    if (confirmed == true) await controller.dismissUnconfirmedSubmission(key);
  }

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final bottom = MediaQuery.viewPaddingOf(context).bottom;
    return Container(
      padding: EdgeInsets.only(bottom: bottom + MaeveSpace.s6),
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
                if (controller.draftError != null)
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          controller.draftError!,
                          style: MaeveType.hint.copyWith(color: colors.bad),
                        ),
                      ),
                      MaeveGlassTextButton(
                        label: '重试保存',
                        compact: true,
                        haptic: MaeveHaptic.tick,
                        onPressed: controller.retryDraftStorage,
                      ),
                    ],
                  ),
                if (controller.submissionNeedsReview)
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '上一条任务的结果待核对',
                          style: MaeveType.hint.copyWith(color: colors.bad),
                        ),
                      ),
                      MaeveGlassTextButton(
                        label: '处理未确认的发送',
                        compact: true,
                        onPressed: () => _confirmUnresolved(context),
                      ),
                    ],
                  ),
                if (controller.chatError != null)
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          controller.chatError!,
                          style: MaeveType.hint.copyWith(color: colors.bad),
                        ),
                      ),
                      MaeveGlassTextButton(
                        label: controller.reconnecting ? '连接中…' : '重新连接',
                        compact: true,
                        enabled: !controller.reconnecting,
                        haptic: MaeveHaptic.tick,
                        onPressed: controller.reconnecting
                            ? null
                            : controller.reconnect,
                      ),
                    ],
                  ),
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
        grouped: true,
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
                        child: MaeveGlassTextButton(
                          label: controller.model,
                          icon: Icons.auto_awesome_outlined,
                          compact: true,
                          tooltip: '模型配置',
                          haptic: MaeveHaptic.tick,
                          onPressed: () =>
                              AppPage.open(context, const ModelSettingsPage()),
                        ),
                      ),
                    ),
                    if (busy)
                      _RoundKey(
                        icon: Icons.stop_rounded,
                        tooltip: controller.cancelRequested
                            ? '正在停止，等待任务收尾'
                            : '停止任务',
                        enabled: !controller.cancelRequested,
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

/// 40 圆键:发送是强调色实心,停止是实色墨;不可用时降透明度(不是变灰点不动)。
///
/// 两颗都是液态玻璃圆钮:实色底也带一道白色受光边和顶边提亮,读作"能按的玻璃",
/// 不是一块塑料色块。
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
    return AnimatedOpacity(
      duration: AppMotion.of(context).quick,
      opacity: accent || ink || enabled ? 1 : .5,
      child: MaeveGlassButton(
        icon: icon,
        tooltip: tooltip,
        onPressed: enabled ? onTap : null,
        prominent: accent,
        enabled: enabled,
        haptic: enabled ? MaeveHaptic.light : null,
        fill: ink ? colors.ink : null,
        foreground: ink ? colors.bg : null,
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

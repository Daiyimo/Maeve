import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../app/maeve_controller.dart';
import '../bridge/maeve_chat_bridge.dart';
import '../models/conversation.dart';
import '../models/duration_format.dart';
import '../models/execution.dart';
import '../models/task_report.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../ui/common/app_page.dart';
import '../ui/common/haptics.dart';
import 'execution_token_usage_view.dart';
import 'glass_surface.dart';
import 'live_elapsed_text.dart';
import 'live_execution_feed.dart';
import 'maeve_markdown.dart';
import 'adaptive_execution_layout.dart';
import 'completed_execution_panel.dart';
import 'pointer_evidence.dart';
import 'task_report_view.dart';

export 'live_execution_feed.dart' show LiveExecutionFeed;
export 'pointer_evidence.dart' show PointerEvidencePainter;

class ExecutionPanel extends StatefulWidget {
  const ExecutionPanel({
    super.key,
    required this.message,
    required this.isLatest,
  });
  final Message message;
  final bool isLatest;
  @override
  State<ExecutionPanel> createState() => _ExecutionPanelState();
}

class _ExecutionPanelState extends State<ExecutionPanel> {
  bool? _expanded;
  bool _acting = false;
  String? _error;

  Future<void> _act(String action) async {
    final controller = MaeveScope.read(context);
    final id = controller.hostConversationId;
    if (id == null) return;
    setState(() {
      _acting = true;
      _error = null;
    });
    try {
      final accepted = await controller.chatBridge.taskAction(
        id,
        action,
        key: widget.message.confirmationKey,
      );
      if (!accepted && mounted) setState(() => _error = '任务状态已变化，请按当前提示操作。');
    } catch (_) {
      if (mounted) setState(() => _error = '操作未能送达，请重试。');
    } finally {
      if (mounted) setState(() => _acting = false);
    }
  }

  Future<void> _reviewEffects() async {
    final controller = MaeveScope.read(context);
    final id = controller.hostConversationId;
    if (id == null || _acting) return;
    setState(() {
      _acting = true;
      _error = null;
    });
    try {
      final receipts = await controller.chatBridge.effectReceipts(id);
      if (!mounted) return;
      if (receipts.isEmpty) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('没有待核对的操作回执。')));
      }
      for (final receipt in receipts) {
        if (!mounted) return;
        var checked = false;
        final choice = await showDialog<bool>(
          context: context,
          builder: (context) => StatefulBuilder(
            builder: (context, update) => MaeveGlassDialog(
              title: const Text('核对操作结果'),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '操作开始时间：${DateTime.fromMillisecondsSinceEpoch((receipt['startedAt'] as num).toInt()).toLocal()}',
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      '这个操作没有可靠的完成回执。请到目标应用查看消息、订单、文件等实际结果，再记录核对结论。记录后不会自动继续旧任务，也不会恢复旧授权。',
                    ),
                    CheckboxListTile(
                      contentPadding: EdgeInsets.zero,
                      value: checked,
                      onChanged: (value) =>
                          update(() => checked = value == true),
                      title: const Text('我已在目标应用核对实际结果'),
                      controlAffinity: ListTileControlAffinity.leading,
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('稍后核对'),
                ),
                TextButton(
                  onPressed: checked
                      ? () => Navigator.pop(context, false)
                      : null,
                  child: const Text('确认未执行'),
                ),
                FilledButton(
                  onPressed: checked
                      ? () => Navigator.pop(context, true)
                      : null,
                  child: const Text('确认已执行'),
                ),
              ],
            ),
          ),
        );
        if (choice == null) break;
        await controller.chatBridge.reconcileEffect(id, receipt, choice);
      }
    } catch (_) {
      if (mounted) setState(() => _error = '回执暂不可读或任务仍在运行，请停止任务后重新核对。');
    } finally {
      if (mounted) setState(() => _acting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final message = widget.message;
    final colors = MaeveTheme.of(context).colors;
    final controller = MaeveScope.of(context);
    final expanded =
        _expanded ??
        (message.active ||
            message.failed ||
            (widget.isLatest && message.runId != null));
    final rows = message.executionSteps;
    final showLive =
        widget.isLatest &&
        message.active &&
        controller.hostConversationId != null;
    final showCompleted =
        widget.isLatest && !message.active && message.runId != null;
    MaeveGlassTextButton executionAction({
      required String label,
      required IconData icon,
      required VoidCallback? onPressed,
      bool enabled = true,
      MaeveHaptic haptic = MaeveHaptic.tick,
    }) => MaeveGlassTextButton(
      label: label,
      icon: icon,
      compact: true,
      shape: const StadiumBorder(),
      enabled: enabled,
      haptic: haptic,
      onPressed: onPressed,
    );
    final recordButton = message.runId == null
        ? null
        : executionAction(
            label: '查看过程',
            icon: Icons.receipt_long_outlined,
            onPressed: () => AppPage.open(
              context,
              RunDetailsPage(
                bridge: controller.chatBridge,
                runId: message.runId!,
                follow: message.active,
              ),
            ),
          );
    // 任务跑完才有报告可看:结局、完整回答、关键画面与导出。
    final reportButton = message.runId == null || message.active
        ? null
        : executionAction(
            label: '任务报告',
            icon: Icons.assignment_outlined,
            onPressed: () => AppPage.open(
              context,
              TaskReportPage(
                bridge: controller.chatBridge,
                runId: message.runId!,
              ),
            ),
          );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (message.planSteps.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  message.planTitle ?? '任务计划',
                  style: MaeveType.caption.copyWith(color: colors.ink2),
                ),
                const SizedBox(height: 4),
                for (var i = 0; i < message.planSteps.length; i++)
                  Builder(
                    builder: (context) {
                      final step = message.planSteps[i];
                      final current = i + 1 == message.planCurrentStep;
                      final status = !message.active && current
                          ? (message.status == 'done'
                                ? 'done'
                                : message.status == 'cancelled'
                                ? 'stopped'
                                : message.status == 'failed'
                                ? 'failed'
                                : step.status)
                          : step.status;
                      final icon = switch (status) {
                        'done' => Icons.check_circle_outline,
                        'failed' => Icons.error_outline,
                        'stopped' => Icons.stop_circle_outlined,
                        'waiting' => Icons.hourglass_empty_rounded,
                        'current' => Icons.radio_button_checked,
                        _ => Icons.radio_button_unchecked,
                      };
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(
                              icon,
                              size: 14,
                              color: status == 'failed'
                                  ? colors.bad
                                  : colors.ink3,
                            ),
                            const SizedBox(width: 7),
                            Expanded(
                              child: Text(
                                step.text,
                                style: MaeveType.caption.copyWith(
                                  color: current ? colors.ink : colors.ink3,
                                ),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
              ],
            ),
          ),
        InkWell(
          onTap: rows.isEmpty
              ? null
              : () {
                  unawaited(MaeveHaptics.play(MaeveHaptic.tick));
                  setState(() => _expanded = !expanded);
                },
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: [
                Icon(
                  message.failed
                      ? Icons.error_outline
                      : message.active
                      ? Icons.hourglass_empty_rounded
                      : message.status == 'cancelled'
                      ? Icons.stop_circle_outlined
                      : Icons.check_circle_outline,
                  size: 15,
                  color: message.failed ? colors.bad : colors.ink3,
                ),
                const SizedBox(width: 7),
                Expanded(
                  child: Wrap(
                    spacing: 10,
                    runSpacing: 4,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Text(
                        widget.isLatest &&
                                message.active &&
                                controller.cancelRequested
                            ? '正在停止，等待任务收尾'
                            : message.statusLabel,
                        style: MaeveType.hint.copyWith(
                          color: message.failed ? colors.bad : colors.ink2,
                        ),
                      ),
                      if (message.steps > 0)
                        Text(
                          '${message.steps} 步',
                          style: MaeveType.hint.copyWith(color: colors.ink3),
                        ),
                      if (message.waitingForModel && message.active)
                        LiveElapsedText(
                          elapsedMs: message.elapsedMs ?? 0,
                          style: MaeveType.mono,
                        )
                      else if (message.elapsedLabel != null)
                        Text(
                          message.elapsedLabel!,
                          style: MaeveType.mono.copyWith(
                            color: colors.ink3,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                    ],
                  ),
                ),
                if (rows.isNotEmpty)
                  Icon(
                    expanded ? Icons.expand_less : Icons.expand_more,
                    size: 17,
                    color: colors.ink3,
                  ),
              ],
            ),
          ),
        ),
        if (showLive) ...[
          if (expanded)
            AdaptiveExecutionLayout(
              wideHeight: 340,
              compactHeight: 280,
              left: TaskPreview(
                key: ValueKey(
                  'preview-${controller.hostConversationId}-${message.runId}',
                ),
                bridge: controller.chatBridge,
                conversationId: controller.hostConversationId!,
                imageHeight: 300,
              ),
              right: LiveExecutionFeed(
                key: ValueKey(
                  'live-feed-${message.runId ?? controller.hostConversationId}',
                ),
                steps: rows,
                height: 275,
              ),
              compactLeft: TaskPreview(
                key: ValueKey(
                  'preview-${controller.hostConversationId}-${message.runId}',
                ),
                bridge: controller.chatBridge,
                conversationId: controller.hostConversationId!,
                imageHeight: 200,
              ),
              compactRight: LiveExecutionFeed(
                key: ValueKey(
                  'live-feed-${message.runId ?? controller.hostConversationId}',
                ),
                steps: rows,
                height: 275,
                compact: true,
              ),
            )
          else
            TaskPreview(
              key: ValueKey(
                'preview-${controller.hostConversationId}-${message.runId}',
              ),
              bridge: controller.chatBridge,
              conversationId: controller.hostConversationId!,
            ),
        ],
        if (expanded && showCompleted)
          CompletedExecutionPanel(
            key: ValueKey('completed-${message.runId}'),
            bridge: controller.chatBridge,
            runId: message.runId!,
            detail: message.detail,
            fallbackReport: TaskReport(
              available: true,
              goal: message.planTitle ?? '任务结果',
              outcome: message.statusLabel,
              duration: message.elapsedLabel,
              answer: message.text,
            ),
            onOpenFullReport: () => AppPage.open(
              context,
              TaskReportPage(
                bridge: controller.chatBridge,
                runId: message.runId!,
              ),
            ),
          )
        else if (expanded && widget.isLatest && message.active && !showLive)
          LiveExecutionFeed(
            key: ValueKey(
              'live-feed-${message.runId ?? controller.hostConversationId}',
            ),
            steps: rows,
          )
        else if (expanded && !showLive)
          for (final step in rows) ExecutionStepView(step: step),
        if (widget.isLatest &&
            message.failed &&
            controller.supportsEffectReceipts)
          Align(
            alignment: Alignment.centerLeft,
            child: MaeveGlassTextButton(
              label: '核对操作结果',
              icon: Icons.fact_check_outlined,
              compact: true,
              enabled: !_acting,
              haptic: MaeveHaptic.tick,
              onPressed: _acting ? null : _reviewEffects,
            ),
          ),
        if (widget.isLatest && message.active) ...[
          Wrap(
            spacing: 8,
            children: [
              executionAction(
                label: controller.cancelRequested ? '正在停止…' : '停止任务',
                icon: Icons.stop_circle_outlined,
                enabled: !controller.cancelRequested,
                haptic: MaeveHaptic.light,
                onPressed: controller.cancelRequested ? null : controller.stop,
              ),
              ?recordButton,
              ?reportButton,
            ],
          ),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              if (message.status == 'needs_confirmation' &&
                  message.confirmationKey != null) ...[
                MaeveGlassTextButton(
                  label: '同意这一次',
                  prominent: true,
                  enabled: !_acting,
                  haptic: MaeveHaptic.light,
                  onPressed: _acting ? null : () => _act('approve'),
                ),
                MaeveGlassTextButton(
                  label: '拒绝',
                  enabled: !_acting,
                  haptic: MaeveHaptic.tick,
                  onPressed: _acting ? null : () => _act('decline'),
                ),
              ],
              if (message.status == 'paused' && message.resumable)
                MaeveGlassTextButton(
                  label: '继续任务',
                  prominent: true,
                  enabled: !_acting,
                  haptic: MaeveHaptic.light,
                  onPressed: _acting ? null : () => _act('resume'),
                ),
              if (message.status == 'needs_takeover') ...[
                MaeveGlassTextButton(
                  label: '接管操作',
                  prominent: true,
                  enabled: !_acting,
                  haptic: MaeveHaptic.light,
                  onPressed: _acting ? null : () => _act('takeOver'),
                ),
                MaeveGlassTextButton(
                  label: '交回 Maeve',
                  enabled: !_acting,
                  haptic: MaeveHaptic.tick,
                  onPressed: _acting ? null : () => _act('handBack'),
                ),
              ],
            ],
          ),
        ],
        if (_error != null)
          Text(_error!, style: MaeveType.caption.copyWith(color: colors.bad)),
        if (recordButton != null && !(widget.isLatest && message.active))
          Align(
            alignment: Alignment.centerLeft,
            child: Wrap(
              spacing: 8,
              runSpacing: 4,
              children: [recordButton, ?reportButton],
            ),
          ),
        const SizedBox(height: 6),
      ],
    );
  }
}

class ExecutionStepView extends StatelessWidget {
  const ExecutionStepView({super.key, required this.step});
  final ExecutionStep step;
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return Padding(
      padding: const EdgeInsets.only(left: 2, top: 6, bottom: 9),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 23,
            child: Text(
              '${step.step}'.padLeft(2, '0'),
              style: MaeveType.hint.copyWith(
                color: step.failed ? c.bad : c.ink3,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  step.title,
                  style: MaeveType.caption.copyWith(
                    color: step.failed ? c.bad : c.ink,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                if (step.reason.isNotEmpty && step.reason != step.title)
                  MaeveMarkdown(
                    data: step.reason,
                    style: MaeveType.caption.copyWith(color: c.ink2),
                  ),
                if (step.failed)
                  Text('此步骤未成功', style: MaeveType.hint.copyWith(color: c.bad)),
                if (step.detail.isNotEmpty)
                  Text(
                    step.detail,
                    style: MaeveType.caption.copyWith(
                      color: step.failed ? c.bad : c.ink3,
                    ),
                  ),
                for (final action in step.actions)
                  Padding(
                    padding: const EdgeInsets.only(top: 3),
                    child: Text(
                      [
                        action.label,
                        if (action.target?.isNotEmpty == true) action.target!,
                        if (action.effect?.isNotEmpty == true) action.effect!,
                      ].join(' · '),
                      style: MaeveType.caption.copyWith(
                        color: action.failed ? c.bad : c.ink3,
                      ),
                    ),
                  ),
                if (step.tags.isNotEmpty)
                  Text(
                    step.tags.join(' · '),
                    style: MaeveType.hint.copyWith(color: c.ink3),
                  ),
                if (step.tokens != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: ExecutionTokenUsageView(
                      usage: step.tokens!,
                      compact: true,
                    ),
                  ),
                if (step.timingLabel.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      step.timingLabel,
                      style: MaeveType.hint.copyWith(color: c.ink3),
                    ),
                  ),
                for (final pointer in step.pointers)
                  Padding(
                    padding: const EdgeInsets.only(top: 6),
                    child: Text(
                      pointer.label,
                      style: MaeveType.hint.copyWith(color: c.accent),
                    ),
                  ),
                if (step.declaredPoint != null && step.pointers.isEmpty)
                  Text(
                    step.declaredPoint!,
                    style: MaeveType.hint.copyWith(color: c.ink3),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Pull the existing guarded frame at a bounded rate, only on the visible page.
class TaskPreview extends StatefulWidget {
  const TaskPreview({
    super.key,
    required this.bridge,
    required this.conversationId,
    this.imageHeight = 190,
  });
  final MaeveChatBridge bridge;
  final String conversationId;
  final double imageHeight;
  @override
  State<TaskPreview> createState() => _TaskPreviewState();
}

class _TaskPreviewState extends State<TaskPreview> with WidgetsBindingObserver {
  Uint8List? _bytes;
  bool _loading = false;
  Timer? _timer;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_load());
    });
    _timer = Timer.periodic(const Duration(milliseconds: 800), (_) {
      if (mounted &&
          ModalRoute.of(context)?.isCurrent == true &&
          WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
        unawaited(_load());
      }
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed &&
        mounted &&
        ModalRoute.of(context)?.isCurrent == true) {
      unawaited(_load());
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Future<void> _load() async {
    if (_loading) return;
    _loading = true;
    try {
      final bytes = await widget.bridge.preview(widget.conversationId);
      if (mounted) setState(() => _bytes = bytes);
    } catch (_) {
      if (mounted) setState(() => _bytes = null);
    } finally {
      _loading = false;
    }
  }

  @override
  Widget build(BuildContext context) => _bytes == null
      ? Padding(
          padding: const EdgeInsets.only(bottom: 6),
          child: Text(
            '当前任务尚未产生执行画面',
            style: MaeveType.hint.copyWith(
              color: MaeveTheme.of(context).colors.ink3,
            ),
          ),
        )
      : _Frame(bytes: _bytes!, title: '实时执行画面', height: widget.imageHeight);
}

class _Frame extends StatelessWidget {
  const _Frame({
    required this.bytes,
    required this.title,
    this.pointer,
    this.height = 190,
  });
  final Uint8List bytes;
  final String title;
  final ExecutionPointer? pointer;
  final double height;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: InkWell(
      onTap: () => Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => Scaffold(
            backgroundColor: Colors.black,
            appBar: AppBar(
              title: Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: MaeveType.title.copyWith(color: Colors.white),
              ),
              backgroundColor: Colors.black,
              foregroundColor: Colors.white,
            ),
            body: SafeArea(
              child: Center(
                child: InteractiveViewer(
                  maxScale: 5,
                  child: pointer == null
                      ? Image.memory(bytes, fit: BoxFit.contain)
                      : AspectRatio(
                          aspectRatio: pointer!.width / pointer!.height,
                          child: _PointerImage(bytes: bytes, pointer: pointer),
                        ),
                ),
              ),
            ),
          ),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '$title · 点按放大',
            style: MaeveType.hint.copyWith(
              color: MaeveTheme.of(context).colors.ink3,
            ),
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: SizedBox(
              height: height,
              width: double.infinity,
              child: ColoredBox(
                color: MaeveTheme.of(context).colors.fill,
                child: _PointerImage(bytes: bytes, pointer: pointer),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class RunDetailsPage extends StatefulWidget {
  const RunDetailsPage({
    super.key,
    required this.bridge,
    required this.runId,
    this.follow = false,
  });
  final MaeveChatBridge bridge;
  final String runId;
  final bool follow;
  @override
  State<RunDetailsPage> createState() => _RunDetailsPageState();
}

class _RunDetailsPageState extends State<RunDetailsPage> {
  RunDetails? _record;
  String? _error;
  bool _loading = false;
  Timer? _timer;
  @override
  void initState() {
    super.initState();
    unawaited(_load());
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted &&
          (_record?.running ?? widget.follow) &&
          ModalRoute.of(context)?.isCurrent == true &&
          WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
        unawaited(_load());
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    if (_loading) return;
    _loading = true;
    try {
      final record = await widget.bridge.runDetails(widget.runId);
      if (mounted) {
        setState(() {
          _record = record;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _error = '未能读取执行记录，请重试。');
    } finally {
      _loading = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final record = _record;
    final c = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '执行记录',
      subtitle: record?.running == true ? '任务进行中，记录持续更新' : '来自本机实际运行记录',
      actions: [
        MaeveGlassButton(
          icon: Icons.refresh_rounded,
          tooltip: '刷新执行记录',
          compact: true,
          haptic: MaeveHaptic.tick,
          onPressed: _load,
        ),
      ],
      children: [
        const SizedBox(height: 20),
        if (_error != null)
          Text(_error!, style: MaeveType.body.copyWith(color: c.bad)),
        if (record == null && _error == null) const Text('正在读取执行记录…'),
        if (record != null && !record.available)
          Text(record.running ? '等待本次记录写入' : '这次记录不存在或已被清理'),
        if (record?.available == true) ...[
          Text(
            record!.goal,
            style: MaeveType.bodyStrong.copyWith(color: c.ink),
          ),
          const SizedBox(height: 10),
          Text.rich(
            TextSpan(
              style: MaeveType.caption.copyWith(color: c.ink2),
              children: [
                TextSpan(text: record.outcome),
                if (record.verdict.isNotEmpty)
                  TextSpan(text: ' · ${record.verdict}'),
                if (record.duration != null)
                  TextSpan(
                    text:
                        ' · ${durationLabel(null, fallback: record.duration)}',
                    style: MaeveType.mono.copyWith(fontWeight: FontWeight.w600),
                  ),
              ],
            ),
          ),
          const SizedBox(height: MaeveSpace.s12),
          Align(
            alignment: Alignment.centerLeft,
            child: MaeveGlassTextButton(
              label: '任务报告',
              icon: Icons.assignment_outlined,
              compact: true,
              haptic: MaeveHaptic.tick,
              onPressed: () => AppPage.open(
                context,
                TaskReportPage(bridge: widget.bridge, runId: widget.runId),
              ),
            ),
          ),
          const SizedBox(height: 20),
          if (record.steps.isEmpty) const Text('尚无已记录步骤'),
          if (record.tokens != null) ...[
            Text(
              '本次任务的 Token 用量',
              style: MaeveType.bodyStrong.copyWith(color: c.ink),
            ),
            const SizedBox(height: 10),
            ExecutionTokenUsageView(usage: record.tokens!),
            const SizedBox(height: 8),
            Text(
              record.tokens!.scope == 'loop'
                  ? '当前为主循环已返回用量，辅助调用可能未记录。缓存已计入输入。'
                  : '任务运行期间已返回的用量，缓存已计入输入。',
              style: MaeveType.hint.copyWith(color: c.ink3),
            ),
            const SizedBox(height: 24),
          ],
          if (record.timingSummary.isNotEmpty) ...[
            Text(
              '已记录耗时 · ${record.timingSummary}',
              style: MaeveType.hint.copyWith(color: c.ink2),
            ),
            const SizedBox(height: 16),
          ],
          for (final step in record.steps) ...[
            ExecutionStepView(step: step),
            for (final pointer in step.pointers)
              if (pointer.screenshotId != null)
                _RecordedFrame(
                  key: ValueKey(
                    'pointer-${widget.runId}-${pointer.screenshotId}',
                  ),
                  bridge: widget.bridge,
                  runId: widget.runId,
                  screenId: pointer.screenshotId!,
                  stepNumber: step.step,
                  pointer: pointer,
                ),
            if (step.screenshotId != null)
              _RecordedFrame(
                key: ValueKey('${widget.runId}-${step.screenshotId}'),
                bridge: widget.bridge,
                runId: widget.runId,
                screenId: step.screenshotId!,
                stepNumber: step.step,
              ),
            const Divider(height: 24),
          ],
        ],
      ],
    );
  }
}

class _RecordedFrame extends StatefulWidget {
  const _RecordedFrame({
    super.key,
    required this.bridge,
    required this.runId,
    required this.screenId,
    required this.stepNumber,
    this.pointer,
  });
  final MaeveChatBridge bridge;
  final String runId;
  final int screenId;
  final int stepNumber;
  final ExecutionPointer? pointer;
  @override
  State<_RecordedFrame> createState() => _RecordedFrameState();
}

class _RecordedFrameState extends State<_RecordedFrame> {
  late final Future<Uint8List?> _image = widget.bridge.runScreen(
    widget.runId,
    widget.screenId,
  );
  @override
  Widget build(BuildContext context) => FutureBuilder<Uint8List?>(
    future: _image,
    builder: (context, snapshot) => snapshot.data == null
        ? const Text('此步骤暂无可用截图')
        : _Frame(
            bytes: snapshot.data!,
            title: widget.pointer == null
                ? '步骤 ${widget.stepNumber} 的画面记录'
                : '步骤 ${widget.stepNumber} · 点击前的位置标注',
            pointer: widget.pointer,
          ),
  );
}

class _PointerImage extends StatelessWidget {
  const _PointerImage({required this.bytes, this.pointer});
  final Uint8List bytes;
  final ExecutionPointer? pointer;
  @override
  Widget build(BuildContext context) => CustomPaint(
    foregroundPainter: pointer == null
        ? null
        : PointerEvidencePainter(pointer!),
    child: Image.memory(
      bytes,
      fit: BoxFit.contain,
      gaplessPlayback: true,
      errorBuilder: (_, _, _) => const Center(child: Text('画面暂不可用')),
    ),
  );
}

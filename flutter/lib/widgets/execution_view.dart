import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../app/maeve_controller.dart';
import '../bridge/maeve_chat_bridge.dart';
import '../models/conversation.dart';
import '../models/execution.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../ui/common/app_page.dart';
import 'token_usage_view.dart';

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

  @override
  Widget build(BuildContext context) {
    final message = widget.message;
    final colors = MaeveTheme.of(context).colors;
    final controller = MaeveScope.of(context);
    final expanded = _expanded ?? (message.active || message.failed);
    final rows = message.executionSteps;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InkWell(
          onTap: rows.isEmpty
              ? null
              : () => setState(() => _expanded = !expanded),
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
                  child: Text(
                    message.statusLabel,
                    style: MaeveType.hint.copyWith(
                      color: message.failed ? colors.bad : colors.ink2,
                    ),
                  ),
                ),
                if (message.steps > 0)
                  Text(
                    '${message.steps} 步',
                    style: MaeveType.hint.copyWith(color: colors.ink3),
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
        if (widget.isLatest &&
            message.active &&
            controller.hostConversationId != null)
          TaskPreview(
            key: ValueKey(
              'preview-${controller.hostConversationId}-${message.runId}',
            ),
            bridge: controller.chatBridge,
            conversationId: controller.hostConversationId!,
          ),
        if (expanded)
          for (final step in rows) ExecutionStepView(step: step),
        if (widget.isLatest && message.active) ...[
          if (message.detail != null &&
              message.detail!.isNotEmpty &&
              message.detail != message.text)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                message.detail!,
                style: MaeveType.caption.copyWith(color: colors.ink2),
              ),
            ),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              if (message.status == 'needs_confirmation' &&
                  message.confirmationKey != null) ...[
                FilledButton(
                  onPressed: _acting ? null : () => _act('approve'),
                  child: const Text('同意这一次'),
                ),
                TextButton(
                  onPressed: _acting ? null : () => _act('decline'),
                  child: const Text('拒绝'),
                ),
              ],
              if (message.status == 'paused' && message.resumable)
                FilledButton(
                  onPressed: _acting ? null : () => _act('resume'),
                  child: const Text('继续任务'),
                ),
              if (message.status == 'needs_takeover') ...[
                FilledButton(
                  onPressed: _acting ? null : () => _act('takeOver'),
                  child: const Text('接管操作'),
                ),
                TextButton(
                  onPressed: _acting ? null : () => _act('handBack'),
                  child: const Text('交回 Maeve'),
                ),
              ],
            ],
          ),
        ],
        if (_error != null)
          Text(_error!, style: MaeveType.caption.copyWith(color: colors.bad)),
        if (message.runId != null)
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: () => AppPage.open(
                context,
                RunDetailsPage(
                  bridge: controller.chatBridge,
                  runId: message.runId!,
                  follow: message.active,
                ),
              ),
              icon: const Icon(Icons.receipt_long_outlined, size: 15),
              label: const Text('完整执行记录'),
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
                  Text(
                    step.reason,
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
                    child: TokenUsageView(usage: step.tokens!, compact: true),
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
  });
  final MaeveChatBridge bridge;
  final String conversationId;
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
      : _Frame(bytes: _bytes!, title: '实时执行画面');
}

class _Frame extends StatelessWidget {
  const _Frame({required this.bytes, required this.title, this.pointer});
  final Uint8List bytes;
  final String title;
  final ExecutionPointer? pointer;
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
              height: 190,
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
        IconButton(
          tooltip: '刷新执行记录',
          onPressed: _load,
          icon: const Icon(Icons.refresh_rounded),
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
          Text(
            [
              record.outcome,
              record.verdict,
              if (record.duration != null) record.duration!,
            ].where((s) => s.isNotEmpty).join(' · '),
            style: MaeveType.caption.copyWith(color: c.ink2),
          ),
          const SizedBox(height: 20),
          if (record.steps.isEmpty) const Text('尚无已记录步骤'),
          if (record.tokens != null) ...[
            Text(
              '本次任务的 Token 用量',
              style: MaeveType.bodyStrong.copyWith(color: c.ink),
            ),
            const SizedBox(height: 10),
            TokenUsageView(usage: record.tokens!),
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

/// Input pixels map through BoxFit.contain, including the letterboxed preview.
class PointerEvidencePainter extends CustomPainter {
  const PointerEvidencePainter(this.pointer);
  final ExecutionPointer pointer;
  Rect imageRect(Size canvas) {
    final fitted = applyBoxFit(
      BoxFit.contain,
      Size(pointer.width.toDouble(), pointer.height.toDouble()),
      canvas,
    );
    return Alignment.center.inscribe(fitted.destination, Offset.zero & canvas);
  }

  Offset position(Size canvas, double x, double y) {
    final image = imageRect(canvas);
    return Offset(
      image.left + x / pointer.width * image.width,
      image.top + y / pointer.height * image.height,
    );
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (!pointer.valid) return;
    const purple = Color(0xff7654c5);
    canvas.save();
    canvas.clipRect(imageRect(size));
    if (pointer.method == 'touch') {
      final p = position(size, pointer.x!.toDouble(), pointer.y!.toDouble());
      canvas.drawCircle(
        p,
        11,
        Paint()
          ..color = Colors.white.withValues(alpha: .9)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 4,
      );
      canvas.drawCircle(
        p,
        11,
        Paint()
          ..color = purple
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2,
      );
      canvas.drawCircle(p, 3, Paint()..color = purple);
      canvas.drawLine(
        p + const Offset(-16, 0),
        p + const Offset(16, 0),
        Paint()
          ..color = purple
          ..strokeWidth = 1,
      );
      canvas.drawLine(
        p + const Offset(0, -16),
        p + const Offset(0, 16),
        Paint()
          ..color = purple
          ..strokeWidth = 1,
      );
    } else {
      final b = pointer.bounds;
      final rect = Rect.fromPoints(
        position(size, b[0].toDouble(), b[1].toDouble()),
        position(size, b[2].toDouble(), b[3].toDouble()),
      );
      canvas.drawRect(rect, Paint()..color = purple.withValues(alpha: .12));
      canvas.drawRect(
        rect,
        Paint()
          ..color = purple
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2,
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(PointerEvidencePainter oldDelegate) =>
      oldDelegate.pointer != pointer;
}

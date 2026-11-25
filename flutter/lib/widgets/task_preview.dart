import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/execution.dart';
import '../ui/common/haptics.dart';
import 'execution_section.dart';
import 'glass_surface.dart';
import 'pointer_evidence.dart';
import 'stepx_neo_frame.dart';

/// Pull the existing guarded frame at a bounded rate, only on the visible page.
class TaskPreview extends StatefulWidget {
  const TaskPreview({
    super.key,
    required this.bridge,
    required this.conversationId,
    this.imageHeight = 190,
    this.compact = false,
    this.runId,
    this.recording,
    this.onReturnToLive,
    this.onRefreshRecords,
  });
  final MaeveChatBridge bridge;
  final String conversationId;
  final double? imageHeight;
  final bool compact;
  final String? runId;
  final RecordedPreview? recording;
  final VoidCallback? onReturnToLive;
  final VoidCallback? onRefreshRecords;
  @override
  State<TaskPreview> createState() => _TaskPreviewState();
}

class _TaskPreviewState extends State<TaskPreview> with WidgetsBindingObserver {
  Uint8List? _bytes;
  bool _loading = false;
  bool _paused = false;
  Timer? _timer;
  Future<Uint8List?>? _recordedBytes;
  @override
  void initState() {
    super.initState();
    _loadRecording();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _refresh();
    });
    _timer = Timer.periodic(const Duration(milliseconds: 800), (_) {
      if (mounted &&
          ModalRoute.of(context)?.isCurrent == true &&
          WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed) {
        _refresh();
      }
    });
  }

  @override
  void didUpdateWidget(covariant TaskPreview oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.recording == widget.recording &&
        oldWidget.runId == widget.runId) {
      return;
    }
    _loadRecording();
    if (oldWidget.recording != null && widget.recording == null) {
      _paused = false;
      unawaited(_load());
    }
  }

  void _loadRecording() {
    final recording = widget.recording;
    final runId = widget.runId;
    _recordedBytes = recording == null || runId == null
        ? null
        : widget.bridge.runScreen(runId, recording.screenId);
  }

  void _refresh() {
    widget.onRefreshRecords?.call();
    if (!_paused && widget.recording == null) unawaited(_load());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed &&
        mounted &&
        ModalRoute.of(context)?.isCurrent == true) {
      _refresh();
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
      if (mounted && !_paused && widget.recording == null) {
        setState(() => _bytes = bytes);
      }
    } catch (_) {
      if (mounted && !_paused && widget.recording == null) {
        setState(() => _bytes = null);
      }
    } finally {
      _loading = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final recording = widget.recording;
    if (recording != null) {
      return FutureBuilder<Uint8List?>(
        key: ValueKey((widget.runId, recording.screenId)),
        future: _recordedBytes,
        builder: (context, snapshot) => _Frame(
          bytes: snapshot.data,
          title: widget.compact ? '历史画面' : '步骤 ${recording.step} · 历史画面',
          compactStage: widget.compact,
          historical: true,
          pointer: recording.pointer,
          height: null,
          placeholder: snapshot.connectionState == ConnectionState.waiting
              ? '正在读取画面…'
              : '此步骤暂无可用截图',
          stageAction: MaeveGlassTextButton(
            label: '返回实时',
            compact: true,
            onPressed: widget.onReturnToLive,
          ),
        ),
      );
    }
    if (_bytes == null && widget.imageHeight != null) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(
          '当前任务尚未产生执行画面',
          style: MaeveType.hint.copyWith(
            color: MaeveTheme.of(context).colors.ink3,
          ),
        ),
      );
    }
    return _Frame(
      bytes: _bytes,
      title: _paused
          ? '画面已暂停'
          : widget.compact
          ? '实时画面'
          : '实时执行画面',
      compactStage: widget.compact,
      height: widget.imageHeight,
      stageAction: widget.imageHeight != null
          ? null
          : MaeveGlassButton(
              icon: _paused ? Icons.play_arrow_rounded : Icons.pause_rounded,
              tooltip: _paused ? '继续实时画面' : '暂停实时画面',
              visualDiameter: 28,
              haptic: MaeveHaptic.tick,
              onPressed: () {
                setState(() => _paused = !_paused);
                if (!_paused) unawaited(_load());
              },
            ),
    );
  }
}

typedef RecordedPreview = ({int step, int screenId, ExecutionPointer? pointer});

class _Frame extends StatelessWidget {
  const _Frame({
    required this.bytes,
    required this.title,
    this.pointer,
    this.height = 190,
    this.placeholder = '当前任务尚未产生执行画面',
    this.stageAction,
    this.compactStage = false,
    this.historical = false,
  });
  final Uint8List? bytes;
  final String title;
  final ExecutionPointer? pointer;
  final double? height;
  final String placeholder;
  final Widget? stageAction;
  final bool compactStage;
  final bool historical;
  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final bytes = this.bytes;
    final caption = Text(
      bytes == null ? title : '$title · 点按放大',
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
      style: MaeveType.hint.copyWith(color: colors.ink3),
    );
    final picture = ColoredBox(
      color: colors.fill,
      child: bytes == null
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(6),
                child: Text(
                  placeholder,
                  textAlign: TextAlign.center,
                  style: MaeveType.hint.copyWith(color: colors.ink3),
                ),
              ),
            )
          : _PointerImage(bytes: bytes, pointer: pointer),
    );
    final VoidCallback? openFrame = bytes == null
        ? null
        : () => Navigator.of(context).push(
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
                              child: _PointerImage(
                                bytes: bytes,
                                pointer: pointer,
                              ),
                            ),
                    ),
                  ),
                ),
              ),
            ),
          );
    if (height == null) {
      final zoomLabel = historical ? '放大历史画面' : '放大实时画面';
      final stage = InkWell(
        onTap: openFrame,
        child: Padding(
          padding: EdgeInsets.all(compactStage ? 5 : 10),
          child: FittedBox(
            fit: BoxFit.contain,
            child: StepxNeoFrame(
              viewport: StepxNeoFrame.portraitViewport,
              width: 170,
              child: picture,
            ),
          ),
        ),
      );
      return ExecutionSection(
        title: title,
        frameKey: const ValueKey('live-preview-frame'),
        action: compactStage
            ? stageAction
            : Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ?stageAction,
                  MaeveGlassButton(
                    icon: Icons.open_in_full_rounded,
                    tooltip: zoomLabel,
                    visualDiameter: 28,
                    haptic: MaeveHaptic.tick,
                    onPressed: openFrame,
                  ),
                ],
              ),
        body: DecoratedBox(
          decoration: BoxDecoration(
            gradient: RadialGradient(
              radius: .85,
              colors: [colors.accentSoft, colors.surface],
            ),
          ),
          child: compactStage
              ? Tooltip(message: zoomLabel, child: stage)
              : stage,
        ),
      );
    }
    return InkWell(
      borderRadius: BorderRadius.circular(14),
      onTap: openFrame,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            caption,
            const SizedBox(height: 6),
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: SizedBox(height: height, child: picture),
            ),
          ],
        ),
      ),
    );
  }
}

class RecordedExecutionFrame extends StatefulWidget {
  const RecordedExecutionFrame({
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
  State<RecordedExecutionFrame> createState() => _RecordedExecutionFrameState();
}

class _RecordedExecutionFrameState extends State<RecordedExecutionFrame> {
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

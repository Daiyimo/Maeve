import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_motion.dart';
import '../design/maeve_shapes.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/execution.dart';
import '../models/task_report.dart';
import '../ui/common/app_page.dart';
import '../ui/common/haptics.dart';
import 'glass_surface.dart';
import 'pointer_evidence.dart';
import 'stepx_neo_frame.dart';

/// 可嵌入执行面板或独立页面的操作回放器。
class TaskPlaybackView extends StatefulWidget {
  const TaskPlaybackView({
    super.key,
    required this.bridge,
    required this.runId,
    required this.report,
    this.initialFrame = 0,
    this.imageHeight = 360,
    this.showStages = true,
    this.compact = false,
    this.frameSelection,
    this.phoneStage = false,
  });

  final MaeveChatBridge bridge;
  final String runId;
  final TaskReport report;
  final int initialFrame;
  final double imageHeight;
  final bool showStages;
  final bool compact;
  final ValueNotifier<int?>? frameSelection;
  final bool phoneStage;

  @override
  State<TaskPlaybackView> createState() => _TaskPlaybackViewState();
}

class _TaskPlaybackViewState extends State<TaskPlaybackView>
    with WidgetsBindingObserver {
  late int _index = widget.initialFrame.clamp(
    0,
    widget.report.frames.isEmpty ? 0 : widget.report.frames.length - 1,
  );
  bool _playing = false;
  Timer? _timer;
  final Map<int, Uint8List> _bytes = {};
  int? _ready;

  List<ReplayFrame> get _frames => widget.report.frames;
  ReplayFrame? get _current => _frames.isEmpty ? null : _frames[_index];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.frameSelection?.addListener(_seekFromTimeline);
    unawaited(_fetch(_index));
  }

  @override
  void didUpdateWidget(covariant TaskPlaybackView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.frameSelection != widget.frameSelection) {
      oldWidget.frameSelection?.removeListener(_seekFromTimeline);
      widget.frameSelection?.addListener(_seekFromTimeline);
    }
    if (oldWidget.report.frames.length != widget.report.frames.length &&
        (oldWidget.report.frames.isEmpty ||
            _index >= widget.report.frames.length)) {
      _index = widget.initialFrame.clamp(
        0,
        widget.report.frames.isEmpty ? 0 : widget.report.frames.length - 1,
      );
      unawaited(_fetch(_index));
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.frameSelection?.removeListener(_seekFromTimeline);
    _timer?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) _pause();
  }

  Future<void> _fetch(int index) async {
    if (index < 0 || index >= _frames.length) return;
    final id = _frames[index].screenshotId;
    if (_bytes.containsKey(id)) {
      if (mounted) setState(() => _ready = id);
      return;
    }
    Uint8List? data;
    try {
      data = await widget.bridge.runScreen(widget.runId, id);
    } catch (_) {
      /* This frame may have been cleared. */
    }
    if (!mounted) return;
    setState(() {
      if (data != null) _bytes[id] = data;
      if (_current?.screenshotId == id) _ready = id;
    });
    if (_current?.screenshotId == id && _playing) _schedule();
    if (index + 1 < _frames.length) unawaited(_prefetch(index + 1));
  }

  Future<void> _prefetch(int index) async {
    if (index < 0 || index >= _frames.length) return;
    final id = _frames[index].screenshotId;
    if (_bytes.containsKey(id)) return;
    try {
      final data = await widget.bridge.runScreen(widget.runId, id);
      if (data != null && mounted) setState(() => _bytes[id] = data);
    } catch (_) {
      /* 预取失败不影响当前帧。 */
    }
  }

  void _pause() {
    _timer?.cancel();
    _timer = null;
    if (_playing && mounted) setState(() => _playing = false);
  }

  void _play() {
    if (_frames.isEmpty) return;
    if (_index >= _frames.length - 1) _go(0);
    setState(() => _playing = true);
    _schedule();
  }

  void _schedule() {
    _timer?.cancel();
    final frame = _current;
    if (frame == null || !_playing) return;
    if (_ready != frame.screenshotId) return;
    final ms = frame.durationMs.round().clamp(200, 20000);
    _timer = Timer(Duration(milliseconds: ms), () {
      if (!mounted || !_playing) return;
      if (_index >= _frames.length - 1) {
        _pause();
        return;
      }
      _go(_index + 1);
      _schedule();
    });
  }

  void _go(int index) {
    final next = index.clamp(0, _frames.isEmpty ? 0 : _frames.length - 1);
    if (next == _index) return;
    setState(() => _index = next);
    widget.frameSelection?.value = next;
    unawaited(_fetch(next));
    unawaited(MaeveHaptics.play(MaeveHaptic.tick));
  }

  void _seekFromTimeline() {
    final next = widget.frameSelection?.value;
    if (next == null || next == _index) return;
    _pause();
    _go(next);
  }

  int _stageFrame(PlaybackStage stage) {
    final id = stage.screenshotId;
    if (id != null) {
      final exact = _frames.indexWhere((f) => f.screenshotId == id);
      if (exact >= 0) return exact;
    }
    final byStep = _frames.indexWhere((f) => f.stepIndex >= stage.stepIndex);
    return byStep < 0 ? 0 : byStep;
  }

  void _fullscreen(Uint8List bytes, ReplayFrame frame) {
    unawaited(
      Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => Scaffold(
            backgroundColor: Colors.black,
            appBar: AppBar(
              title: Text(
                '第 ${frame.step} 步 · ${frame.beforeAction ? '操作前' : '操作后'}',
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
                  child: _PointerCanvas(bytes: bytes, pointer: frame.pointer),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final frame = _current;
    if (_frames.isEmpty) {
      return SizedBox(
        height: widget.imageHeight,
        child: Center(
          child: Text(
            '这次任务没有记录画面。',
            style: MaeveType.body.copyWith(color: c.ink2),
          ),
        ),
      );
    }
    final bytes = frame == null ? null : _bytes[frame.screenshotId];
    final reduce = MediaQuery.disableAnimationsOf(context);
    final preview = _PlaybackImage(
      bytes: bytes,
      pointer: frame?.pointer,
      height: widget.phoneStage ? null : widget.imageHeight,
      phoneStage: widget.phoneStage,
      transition: reduce ? Duration.zero : MaeveMotion.emphasized,
      onTap: bytes == null || frame == null
          ? null
          : () => _fullscreen(bytes, frame),
    );
    Widget evidenceNote(String label, TextStyle style) => widget.phoneStage
        ? Tooltip(
            message: label,
            child: Text(
              label,
              style: style,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          )
        : Text(label, style: style);

    return Column(
      key: const ValueKey('task-playback-view'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              frame?.title ?? '记录画面',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: (widget.compact ? MaeveType.caption : MaeveType.bodyStrong)
                  .copyWith(color: c.ink, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: MaeveSpace.s4),
            Wrap(
              spacing: MaeveSpace.s8,
              children: [
                if (frame != null && !widget.compact)
                  Text(
                    frame.beforeAction == true ? '操作前' : '操作后',
                    style: MaeveType.label.copyWith(
                      color: frame.beforeAction == true ? c.accent : c.ink3,
                    ),
                  ),
                Text(
                  widget.compact
                      ? '第 ${_index + 1} / ${_frames.length} 帧'
                      : '第 ${_index + 1} / ${_frames.length} 帧 · 来自本机记录',
                  maxLines: widget.compact ? 1 : null,
                  overflow: widget.compact ? TextOverflow.ellipsis : null,
                  style: MaeveType.label.copyWith(color: c.ink3),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.s10),
        if (widget.phoneStage) Expanded(child: preview) else preview,
        if (frame?.failed == true) ...[
          const SizedBox(height: MaeveSpace.s8),
          evidenceNote(
            frame!.failure!,
            MaeveType.caption.copyWith(color: c.bad),
          ),
        ],
        if (frame?.pointer != null) ...[
          const SizedBox(height: MaeveSpace.s6),
          evidenceNote(
            frame!.pointer!.label,
            MaeveType.hint.copyWith(color: c.ink3),
          ),
        ],
        if (!widget.compact || _frames.length > 1) ...[
          const SizedBox(height: MaeveSpace.s8),
          _controls(),
        ],

        if (widget.showStages && widget.report.stages.isNotEmpty) ...[
          const SizedBox(height: MaeveSpace.s12),
          Text('阶段', style: MaeveType.label.copyWith(color: c.ink3)),
          const SizedBox(height: MaeveSpace.s8),
          SizedBox(
            height: 44,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: widget.report.stages.length,
              separatorBuilder: (_, _) => const SizedBox(width: MaeveSpace.s8),
              itemBuilder: (context, i) {
                final stage = widget.report.stages[i];
                return ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 180),
                  child: MaeveGlassTextButton(
                    label: stage.title,
                    compact: true,
                    selected: stage.stepIndex == frame?.stepIndex,
                    haptic: MaeveHaptic.tick,
                    onPressed: () {
                      _pause();
                      _go(_stageFrame(stage));
                    },
                  ),
                );
              },
            ),
          ),
        ],
        if (widget.report.missingFrames > 0) ...[
          const SizedBox(height: MaeveSpace.s8),
          Text(
            '已跳过 ${widget.report.missingFrames} 张不可用画面。',
            style: MaeveType.hint.copyWith(color: c.ink3),
          ),
        ],
      ],
    );
  }

  Widget _controls() => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 232),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth;
          final mix = ((width - 120) / 112).clamp(0.0, 1.0);
          final slot = width / 3;
          final skipSize = math.min(
            slot * .82,
            widget.compact ? 27 + 7 * mix : 30 + 10 * mix,
          );
          final playSize = math.min(
            slot * .94,
            widget.compact ? 33 + 7 * mix : 36 + 12 * mix,
          );
          final thumbRadius = 6 + 2 * mix;
          final slider = SliderTheme(
            data: SliderTheme.of(context).copyWith(
              trackHeight: 2.5 + mix,
              trackShape: const RoundedRectSliderTrackShape(),
              thumbShape: RoundSliderThumbShape(
                enabledThumbRadius: thumbRadius,
              ),
              overlayShape: RoundSliderOverlayShape(
                overlayRadius: thumbRadius + 5,
              ),
            ),
            child: Slider(
              value: _index.toDouble(),
              max: (_frames.length - 1).toDouble(),
              divisions: _frames.length > 1 ? _frames.length - 1 : null,
              label: '第 ${_index + 1} 帧',
              semanticFormatterCallback: (value) => '第 ${value.round() + 1} 帧',
              onChanged: (value) => _seek(value.round()),
            ),
          );
          return Column(
            key: const ValueKey('playback-controls'),
            children: [
              SizedBox(
                height: widget.compact ? 32 : 36 + 8 * mix,
                width: double.infinity,
                child: slider,
              ),
              Row(
                children: [
                  Expanded(
                    child: Center(
                      child: MaeveGlassButton(
                        icon: Icons.skip_previous_rounded,
                        tooltip: '上一帧',
                        visualDiameter: skipSize,
                        haptic: MaeveHaptic.tick,
                        onPressed: _index == 0 ? null : () => _seek(_index - 1),
                      ),
                    ),
                  ),
                  Expanded(
                    child: Center(
                      child: MaeveGlassButton(
                        icon: _playing
                            ? Icons.pause_rounded
                            : Icons.play_arrow_rounded,
                        tooltip: _playing ? '暂停' : '播放',
                        visualDiameter: playSize,
                        prominent: true,
                        haptic: MaeveHaptic.light,
                        onPressed: _playing ? _pause : _play,
                      ),
                    ),
                  ),
                  Expanded(
                    child: Center(
                      child: MaeveGlassButton(
                        icon: Icons.skip_next_rounded,
                        tooltip: '下一帧',
                        visualDiameter: skipSize,
                        haptic: MaeveHaptic.tick,
                        onPressed: _index >= _frames.length - 1
                            ? null
                            : () => _seek(_index + 1),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          );
        },
      ),
    ),
  );

  void _seek(int index) {
    _pause();
    _go(index);
  }
}

/// 独立详情页保留，主流程使用 [TaskPlaybackView] 嵌入执行面板。
class TaskPlaybackPage extends StatelessWidget {
  const TaskPlaybackPage({
    super.key,
    required this.bridge,
    required this.runId,
    required this.report,
    this.initialFrame = 0,
  });
  final MaeveChatBridge bridge;
  final String runId;
  final TaskReport report;
  final int initialFrame;

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '操作回放',
    children: [
      const SizedBox(height: MaeveSpace.s16),
      TaskPlaybackView(
        bridge: bridge,
        runId: runId,
        report: report,
        initialFrame: initialFrame,
      ),
    ],
  );
}

class _PlaybackImage extends StatelessWidget {
  const _PlaybackImage({
    required this.bytes,
    required this.pointer,
    required this.height,
    required this.transition,
    required this.phoneStage,
    this.onTap,
  });
  final Uint8List? bytes;
  final ExecutionPointer? pointer;
  final double? height;
  final Duration transition;
  final bool phoneStage;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final picture = AnimatedSwitcher(
      duration: transition,
      switchInCurve: MaeveMotion.decelerate,
      child: bytes == null
          ? Center(
              key: const ValueKey('loading'),
              child: Text(
                '画面没保存或已被清理',
                style: MaeveType.hint.copyWith(color: c.ink3),
              ),
            )
          : KeyedSubtree(
              key: ValueKey(identityHashCode(bytes)),
              child: _PointerCanvas(bytes: bytes!, pointer: pointer),
            ),
    );
    return ClipRRect(
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      child: ColoredBox(
        color: phoneStage ? c.accentSoft : c.fill,
        child: SizedBox(
          height: height,
          width: double.infinity,
          child: GestureDetector(
            onTap: onTap,
            child: phoneStage
                ? Padding(
                    padding: const EdgeInsets.all(8),
                    child: FittedBox(
                      fit: BoxFit.contain,
                      child: StepxNeoFrame(
                        viewport: StepxNeoFrame.portraitViewport,
                        width: 170,
                        child: picture,
                      ),
                    ),
                  )
                : picture,
          ),
        ),
      ),
    );
  }
}

class _PointerCanvas extends StatelessWidget {
  const _PointerCanvas({required this.bytes, this.pointer});
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

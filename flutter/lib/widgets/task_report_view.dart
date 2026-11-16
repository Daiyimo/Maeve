import 'dart:async';
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
import '../ui/common/app_sheet.dart';
import '../ui/common/haptics.dart';
import 'execution_view.dart' show PointerEvidencePainter;
import 'glass_surface.dart';
import 'maeve_markdown.dart';

/// 任务报告:结局 + 完整回答 + 关键页 + 导出。
///
/// 与「查看过程」的分工:过程页逐步列出做了什么,报告页只说做成了什么、
/// 依据是哪几页。画面全部来自本机记录,标记是执行入口报出的真实落点。
class TaskReportPage extends StatefulWidget {
  const TaskReportPage({
    super.key,
    required this.bridge,
    required this.runId,
    this.initialFrame = 0,
  });
  final MaeveChatBridge bridge;
  final String runId;

  /// 从关键页跳进回放时的起始帧。
  final int initialFrame;

  @override
  State<TaskReportPage> createState() => _TaskReportPageState();
}

class _TaskReportPageState extends State<TaskReportPage> {
  TaskReport? _report;
  String? _error;
  String? _notice;
  bool _loading = false;
  bool _exporting = false;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    if (_loading) return;
    _loading = true;
    try {
      final report = await widget.bridge.runReport(widget.runId);
      if (mounted) {
        setState(() {
          _report = report;
          _error = null;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _error = '未能读取任务报告，请重试。');
    } finally {
      _loading = false;
    }
  }

  Future<void> _export() async {
    if (_exporting) return;
    final format = await showMaeveSheet<ReportFormat>(
      context: context,
      builder: (sheetContext) => _ExportSheet(runId: widget.runId),
    );
    if (format == null || !mounted) return;
    setState(() {
      _exporting = true;
      _notice = null;
    });
    String state;
    try {
      state = await widget.bridge.exportRunReport(widget.runId, format);
    } catch (_) {
      state = 'failed';
    }
    if (!mounted) return;
    setState(() {
      _exporting = false;
      _notice = switch (state) {
        'saved' => '报告已保存',
        'cancelled' => '已取消导出',
        _ => '没能保存，请检查记录是否还在、所选位置能否写入后重试。',
      };
    });
    if (state == 'saved') unawaited(MaeveHaptics.play(MaeveHaptic.light));
    if (state == 'failed') unawaited(MaeveHaptics.play(MaeveHaptic.reject));
  }

  void _replay({int frame = 0}) {
    final report = _report;
    if (report == null || report.frames.isEmpty) return;
    unawaited(
      AppPage.open(
        context,
        TaskPlaybackPage(
          bridge: widget.bridge,
          runId: widget.runId,
          report: report,
          initialFrame: frame.clamp(0, report.frames.length - 1),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final report = _report;
    return AppPage.list(
      title: '任务报告',
      subtitle: report?.running == true ? '任务进行中，报告随后补齐' : '任务结果 · 完整回答 · 关键画面',
      actions: [
        MaeveGlassButton(
          icon: Icons.refresh_rounded,
          tooltip: '刷新任务报告',
          compact: true,
          haptic: MaeveHaptic.tick,
          onPressed: _load,
        ),
      ],
      children: [
        const SizedBox(height: MaeveSpace.s20),
        if (_error != null)
          Text(_error!, style: MaeveType.body.copyWith(color: c.bad)),
        if (report == null && _error == null)
          Text('正在读取任务报告…', style: MaeveType.body.copyWith(color: c.ink2)),
        if (report != null && !report.available)
          Text(
            report.running ? '等待本次记录写入' : '这次记录不存在或已被清理',
            style: MaeveType.body.copyWith(color: c.ink2),
          ),
        if (report != null && report.available) ...[
          _ReportCard(report: report),
          const SizedBox(height: MaeveSpace.s16),
          Wrap(
            spacing: MaeveSpace.s8,
            runSpacing: MaeveSpace.s8,
            children: [
              MaeveGlassTextButton(
                label: '操作回放',
                icon: Icons.play_circle_outline_rounded,
                prominent: true,
                haptic: MaeveHaptic.light,
                onPressed: report.frames.isEmpty ? null : () => _replay(),
              ),
              MaeveGlassTextButton(
                label: '导出',
                icon: Icons.ios_share_rounded,
                haptic: MaeveHaptic.tick,
                enabled: !_exporting,
                onPressed: _export,
              ),
            ],
          ),
          const SizedBox(height: MaeveSpace.s8),
          Text(
            '你自己挑保存位置，不需要存储权限。',
            style: MaeveType.hint.copyWith(color: c.ink3),
          ),
          if (_notice != null) ...[
            const SizedBox(height: MaeveSpace.s8),
            Text(
              _notice!,
              style: MaeveType.caption.copyWith(
                color: _notice == '报告已保存' ? c.ok : c.ink2,
              ),
            ),
          ],
          if (report.pages.isNotEmpty) ...[
            const SizedBox(height: MaeveSpace.s24),
            Text(
              '关键页面',
              style: MaeveType.bodyStrong.copyWith(color: c.ink),
            ),
            const SizedBox(height: MaeveSpace.s12),
            for (final page in report.pages) ...[
              _KeyPage(
                bridge: widget.bridge,
                runId: widget.runId,
                page: page,
                onOpen: () => _replay(frame: page.stepIndex),
              ),
              const SizedBox(height: MaeveSpace.s12),
            ],
          ],
          if (report.missingFrames > 0) ...[
            const SizedBox(height: MaeveSpace.s8),
            Text(
              '已跳过 ${report.missingFrames} 张不可用画面。',
              style: MaeveType.hint.copyWith(color: c.ink3),
            ),
          ],
          const SizedBox(height: MaeveSpace.s24),
          Text(
            '记录保存在本机，画面可能包含页面内容；沿用最近 30 次任务的保留规则。',
            style: MaeveType.hint.copyWith(color: c.ink3),
          ),
        ],
      ],
    );
  }
}

/// 报告主卡:目标、结局条、做成的标准、完整回答。
class _ReportCard extends StatelessWidget {
  const _ReportCard({required this.report});
  final TaskReport report;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final summary = [
      report.outcome,
      report.verdict,
      if (report.duration != null) report.duration!,
    ].where((s) => s.isNotEmpty).join(' · ');
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      shape: MaeveShapes.card,
      padding: const EdgeInsets.all(MaeveSpace.s20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            report.goal,
            style: MaeveType.title.copyWith(
              fontSize: 21,
              fontWeight: FontWeight.w700,
              color: c.ink,
              height: 1.35,
            ),
          ),
          if (summary.isNotEmpty) ...[
            const SizedBox(height: MaeveSpace.s16),
            // 结局条:左侧一道强调色竖条 + 淡底,和过程页的结论行同一语言。
            Container(
              decoration: BoxDecoration(
                color: c.accent.withValues(alpha: .10),
                borderRadius: BorderRadius.circular(MaeveRadius.sm),
                border: Border(
                  left: BorderSide(color: c.accent, width: 3),
                ),
              ),
              padding: const EdgeInsets.symmetric(
                horizontal: MaeveSpace.s12,
                vertical: MaeveSpace.s10,
              ),
              child: Text(
                summary,
                style: MaeveType.bodyStrong.copyWith(color: c.ink),
              ),
            ),
          ],
          if (report.criteria.isNotEmpty) ...[
            const SizedBox(height: MaeveSpace.s16),
            Text(
              '做成的标准',
              style: MaeveType.label.copyWith(color: c.ink3),
            ),
            const SizedBox(height: MaeveSpace.s6),
            for (final item in report.criteria)
              Padding(
                padding: const EdgeInsets.only(bottom: MaeveSpace.s4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('· ', style: MaeveType.body.copyWith(color: c.ink3)),
                    Expanded(
                      child: Text(
                        item,
                        style: MaeveType.body.copyWith(color: c.ink2),
                      ),
                    ),
                  ],
                ),
              ),
          ],
          const SizedBox(height: MaeveSpace.s20),
          Text('回答', style: MaeveType.label.copyWith(color: c.ink3)),
          const SizedBox(height: MaeveSpace.s8),
          if (report.hasAnswer)
            MaeveMarkdown(data: report.answer)
          else
            Text(
              '这次任务没有保存最终回答。',
              style: MaeveType.body.copyWith(color: c.ink3),
            ),
        ],
      ),
    );
  }
}

/// 关键页:缩略图 + 标题,点按直达对应回放帧。
class _KeyPage extends StatefulWidget {
  const _KeyPage({
    required this.bridge,
    required this.runId,
    required this.page,
    required this.onOpen,
  });
  final MaeveChatBridge bridge;
  final String runId;
  final ReportPage page;
  final VoidCallback onOpen;
  @override
  State<_KeyPage> createState() => _KeyPageState();
}

class _KeyPageState extends State<_KeyPage> {
  late final Future<Uint8List?> _image = widget.bridge.runScreen(
    widget.runId,
    widget.page.screenshotId,
  );

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      shape: MaeveShapes.card,
      padding: const EdgeInsets.all(MaeveSpace.s12),
      onTap: widget.onOpen,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(MaeveRadius.xs),
            child: SizedBox(
              width: 46,
              height: 72,
              child: ColoredBox(
                color: c.fill,
                child: FutureBuilder<Uint8List?>(
                  future: _image,
                  builder: (context, snapshot) => snapshot.data == null
                      ? const SizedBox.shrink()
                      : Image.memory(
                          snapshot.data!,
                          fit: BoxFit.cover,
                          gaplessPlayback: true,
                          errorBuilder: (_, _, _) => const SizedBox.shrink(),
                        ),
                ),
              ),
            ),
          ),
          const SizedBox(width: MaeveSpace.s12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.page.title,
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                  style: MaeveType.bodyStrong.copyWith(color: c.ink),
                ),
                const SizedBox(height: MaeveSpace.s4),
                Text(
                  '第 ${widget.page.stepIndex + 1} 步 · 在回放里查看',
                  style: MaeveType.hint.copyWith(color: c.ink3),
                ),
              ],
            ),
          ),
          Icon(Icons.chevron_right_rounded, size: 20, color: c.ink3),
        ],
      ),
    );
  }
}

class _ExportSheet extends StatelessWidget {
  const _ExportSheet({required this.runId});
  final String runId;

  @override
  Widget build(BuildContext context) => AppSheet(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SheetTitleBar(
          title: '导出任务报告',
          subtitle: 'Maeve-$runId',
          showGrabber: true,
        ),
        const SizedBox(height: MaeveSpace.s16),
        for (final format in ReportFormat.values)
          Padding(
            padding: const EdgeInsets.only(bottom: MaeveSpace.s8),
            child: MaeveGlassTextButton(
              label: '${format.label} · .${format.extension}',
              icon: switch (format) {
                ReportFormat.markdown => Icons.description_outlined,
                ReportFormat.word => Icons.article_outlined,
                ReportFormat.text => Icons.notes_rounded,
              },
              // 选中即收起弹层,那一下由弹层路由发,按钮不再重复。
              haptic: null,
              onPressed: () => Navigator.of(context).pop(format),
            ),
          ),
        MaeveGlassTextButton(
          label: '关闭',
          compact: true,
          haptic: null,
          onPressed: () => Navigator.of(context).pop(),
        ),
      ],
    ),
  );
}

/// 操作回放:按记录里的真实画面逐帧播放,操作前那一帧带实际落点标记。
///
/// 播放节奏由原生给出(操作前 1.2s、操作后 2.4s),倍速只缩放这个节奏。
/// 页面不可见时暂停,不空转定时器。
class TaskPlaybackPage extends StatefulWidget {
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
  State<TaskPlaybackPage> createState() => _TaskPlaybackPageState();
}

class _TaskPlaybackPageState extends State<TaskPlaybackPage>
    with WidgetsBindingObserver {
  static const _speeds = <double>[.5, 1, 2];

  late int _index = widget.initialFrame.clamp(
    0,
    widget.report.frames.isEmpty ? 0 : widget.report.frames.length - 1,
  );
  double _speed = 1;
  bool _playing = false;
  Timer? _timer;

  /// 已经解码好的帧;只有当前帧就绪才计时,避免网络/磁盘慢时跳帧。
  final Map<int, Uint8List> _bytes = {};
  int? _ready;

  List<ReplayFrame> get _frames => widget.report.frames;
  ReplayFrame? get _current => _frames.isEmpty ? null : _frames[_index];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_fetch(_index));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
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
    try {
      final data = await widget.bridge.runScreen(widget.runId, id);
      if (!mounted) return;
      if (data != null) _bytes[id] = data;
      setState(() => _ready = id);
    } catch (_) {
      if (mounted) setState(() => _ready = id);
    }
    // 顺手预取下一帧,切换时不闪空。
    if (index + 1 < _frames.length) unawaited(_prefetch(index + 1));
  }

  Future<void> _prefetch(int index) async {
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
    // 当前帧还没解码好就等它就绪,不按空画面计时。
    if (_ready != frame.screenshotId) return;
    final ms = (frame.durationMs / _speed).round().clamp(200, 20000);
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
    unawaited(_fetch(next));
    unawaited(MaeveHaptics.play(MaeveHaptic.tick));
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final frame = _current;
    final reduce = MediaQuery.disableAnimationsOf(context);
    if (_frames.isEmpty) {
      return AppPage.list(
        title: '操作回放',
        children: [
          const SizedBox(height: MaeveSpace.s20),
          Text(
            '这次任务没有记录画面。',
            style: MaeveType.body.copyWith(color: c.ink2),
          ),
        ],
      );
    }
    final bytes = frame == null ? null : _bytes[frame.screenshotId];
    return AppPage.list(
      title: '操作回放',
      subtitle: '第 ${_index + 1} / ${_frames.length} 帧 · 来自本机记录',
      actions: [
        MaeveGlassButton(
          icon: Icons.fullscreen_rounded,
          tooltip: '全屏',
          compact: true,
          haptic: MaeveHaptic.tick,
          onPressed: bytes == null ? null : () => _fullscreen(bytes, frame!),
        ),
      ],
      children: [
        const SizedBox(height: MaeveSpace.s16),
        MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          shape: MaeveShapes.card,
          padding: const EdgeInsets.all(MaeveSpace.s12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      frame?.title ?? '记录画面',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: MaeveType.bodyStrong.copyWith(color: c.ink),
                    ),
                  ),
                  const SizedBox(width: MaeveSpace.s8),
                  Text(
                    frame?.beforeAction == true ? '操作前' : '操作后',
                    style: MaeveType.label.copyWith(
                      color: frame?.beforeAction == true ? c.accent : c.ink3,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: MaeveSpace.s10),
              _PlaybackImage(
                bytes: bytes,
                pointer: frame?.pointer,
                transition: reduce ? Duration.zero : MaeveMotion.emphasized,
                onTap: bytes == null || frame == null
                    ? null
                    : () => _fullscreen(bytes, frame),
              ),
              if (frame?.failed == true) ...[
                const SizedBox(height: MaeveSpace.s8),
                Text(
                  frame!.failure!,
                  style: MaeveType.caption.copyWith(color: c.bad),
                ),
              ],
              if (frame?.pointer != null) ...[
                const SizedBox(height: MaeveSpace.s8),
                Text(
                  frame!.pointer!.label,
                  style: MaeveType.hint.copyWith(color: c.ink3),
                ),
              ] else if (frame?.beforeAction == true) ...[
                const SizedBox(height: MaeveSpace.s8),
                Text(
                  '这次操作没有保存落点。',
                  style: MaeveType.hint.copyWith(color: c.ink3),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: MaeveSpace.s12),
        Slider(
          value: _index.toDouble(),
          max: (_frames.length - 1).toDouble(),
          divisions: _frames.length > 1 ? _frames.length - 1 : null,
          label: '第 ${_index + 1} 帧',
          semanticFormatterCallback: (value) => '第 ${value.round() + 1} 帧',
          onChanged: (value) {
            _pause();
            _go(value.round());
          },
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            MaeveGlassButton(
              icon: Icons.skip_previous_rounded,
              tooltip: '上一帧',
              haptic: MaeveHaptic.tick,
              onPressed: _index == 0
                  ? null
                  : () {
                      _pause();
                      _go(_index - 1);
                    },
            ),
            const SizedBox(width: MaeveSpace.s8),
            MaeveGlassButton(
              icon: _playing
                  ? Icons.pause_rounded
                  : Icons.play_arrow_rounded,
              tooltip: _playing ? '暂停' : '播放',
              prominent: true,
              haptic: MaeveHaptic.light,
              onPressed: _playing ? _pause : _play,
            ),
            const SizedBox(width: MaeveSpace.s8),
            MaeveGlassButton(
              icon: Icons.skip_next_rounded,
              tooltip: '下一帧',
              haptic: MaeveHaptic.tick,
              onPressed: _index >= _frames.length - 1
                  ? null
                  : () {
                      _pause();
                      _go(_index + 1);
                    },
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.s12),
        Wrap(
          alignment: WrapAlignment.center,
          spacing: MaeveSpace.s8,
          children: [
            for (final speed in _speeds)
              MaeveGlassTextButton(
                label: '$speed×',
                compact: true,
                selected: _speed == speed,
                semanticLabel: '倍速 $speed×',
                haptic: MaeveHaptic.tick,
                onPressed: () {
                  setState(() => _speed = speed);
                  if (_playing) _schedule();
                },
              ),
          ],
        ),
        if (widget.report.stages.isNotEmpty) ...[
          const SizedBox(height: MaeveSpace.s20),
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
        const SizedBox(height: MaeveSpace.s16),
        Text(
          '紫色标记是实际派发的输入位置。',
          style: MaeveType.hint.copyWith(color: c.ink3),
        ),
        if (widget.report.missingFrames > 0) ...[
          const SizedBox(height: MaeveSpace.s4),
          Text(
            '已跳过 ${widget.report.missingFrames} 张不可用画面。',
            style: MaeveType.hint.copyWith(color: c.ink3),
          ),
        ],
      ],
    );
  }

  /// 阶段直达:优先它自己那张证据画面,否则落到该步的第一帧。
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
}

/// 回放画面:换帧用交叉淡入,标记画在 BoxFit.contain 之后的真实位置上。
class _PlaybackImage extends StatelessWidget {
  const _PlaybackImage({
    required this.bytes,
    required this.pointer,
    required this.transition,
    this.onTap,
  });
  final Uint8List? bytes;
  final ExecutionPointer? pointer;
  final Duration transition;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return ClipRRect(
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      child: ColoredBox(
        color: c.fill,
        child: SizedBox(
          height: 360,
          width: double.infinity,
          child: GestureDetector(
            onTap: onTap,
            child: AnimatedSwitcher(
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
            ),
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

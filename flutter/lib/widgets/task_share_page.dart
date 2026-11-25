import 'dart:async';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_accent.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/conversation.dart';
import '../models/task_report.dart';
import '../ui/common/app_page.dart';
import '../ui/common/haptics.dart';
import 'glass_surface.dart';
import 'maeve_mark.dart';
import 'stepx_neo_frame.dart';

/// 分享海报清理 Markdown，优先保留结论与体验码等关键结果，再按句界截断。
({String lead, List<String> codes}) shareAnswerSummary(String answer) {
  final paragraphs = <String>[];
  var inCode = false;
  for (final rawLine in answer.replaceAll('\r\n', '\n').split('\n')) {
    final line = rawLine.trim();
    if (line.startsWith('```')) {
      inCode = !inCode;
      continue;
    }
    if (inCode || line.isEmpty) continue;
    if (RegExp(r'^#{1,6}\s+').hasMatch(line) ||
        RegExp(r'^([-*_]\s*){3,}$').hasMatch(line) ||
        RegExp(r'^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$').hasMatch(line)) {
      continue;
    }
    final text = line
        .replaceFirst(RegExp(r'^>\s?'), '')
        .replaceFirst(RegExp(r'^[-*+]\s+'), '')
        .replaceFirst(RegExp(r'^\d+[.)]\s+'), '')
        .replaceAll(RegExp(r'!\[[^\]]*\]\([^)]*\)'), '')
        .replaceAllMapped(
          RegExp(r'\[([^\]]+)\]\([^)]*\)'),
          (match) => match.group(1) ?? '',
        )
        .replaceAll(RegExp(r'<[^>]+>'), '')
        .replaceAll('`', '')
        .replaceAll('**', '')
        .replaceAllMapped(
          RegExp(r'(?<!\w)(_{1,2}|\*)(\S.*?)(?<!\s)\1(?!\w)'),
          (match) => match[2]!,
        );
    final compact = _compactShareText(text);
    if (compact.isNotEmpty) paragraphs.add(compact);
  }
  if (paragraphs.isEmpty) return (lead: '', codes: const []);
  final plain = paragraphs.join('\n');
  final codeMatches = RegExp(
    r'体验码\s*[#＃]?\s*(\d{1,2})\s*[：:为]\s*([A-Za-z0-9][A-Za-z0-9_-]{3,31})',
  ).allMatches(plain).toList();
  final codes = <String, String>{};
  for (final match in codeMatches) {
    final label = match.group(1);
    final code = match.group(2);
    if (label != null && code != null) codes[label] = code;
  }
  if (codes.isNotEmpty) {
    final prefix = plain.substring(0, codeMatches.first.start);
    final sentenceEnd = prefix.lastIndexOf(RegExp(r'[。！？.!?]'));
    final lead = _compactShareText(
      sentenceEnd >= 0 ? prefix.substring(0, sentenceEnd + 1) : prefix,
    );
    return (
      lead: _shortenShareLead(lead),
      codes: [
        for (final entry in codes.entries) '体验码 ${entry.key}：${entry.value}',
      ],
    );
  }
  return (lead: _shortenShareLead(paragraphs.first), codes: const []);
}

String _shortenShareLead(String excerpt) {
  const maxCharacters = 140;
  if (excerpt.length <= maxCharacters) return excerpt;
  final window = excerpt.substring(0, maxCharacters - 1);
  final sentenceEnd = window.lastIndexOf(RegExp(r'[。！？.!?]'));
  final commaEnd = window.lastIndexOf(RegExp(r'[，、；;,：:]'));
  final cut = sentenceEnd >= 0
      ? sentenceEnd + 1
      : commaEnd >= maxCharacters ~/ 2
      ? commaEnd + 1
      : maxCharacters - 1;
  return '${excerpt.substring(0, cut).trimRight()}…';
}

String _compactShareText(String text) => text
    .replaceAll(RegExp(r'\s+'), ' ')
    .replaceAll(RegExp(r'(?<=[\u4e00-\u9fff])\s+(?=[\u4e00-\u9fff])'), '')
    .replaceAllMapped(RegExp(r'\s+([，。！？；：、）】》])'), (match) => match[1]!)
    .replaceAllMapped(RegExp(r'([（【《])\s+'), (match) => match[1]!)
    .trim();

class TaskSharePage extends StatefulWidget {
  const TaskSharePage({
    super.key,
    required this.bridge,
    required this.message,
    required this.prompt,
    required this.viewport,
    this.report,
    this.initialFrameIndex,
  });

  final MaeveChatBridge bridge;
  final Message message;
  final String? prompt;
  final Size viewport;
  final TaskReport? report;
  final int? initialFrameIndex;

  @override
  State<TaskSharePage> createState() => _TaskSharePageState();
}

class _TaskSharePageState extends State<TaskSharePage> {
  final _poster = GlobalKey();
  ({TaskReport report, Uint8List? frame, int selectedIndex})? _snapshot;
  final Map<int, Future<Uint8List?>> _frames = {};
  bool _sharing = false;
  String? _editedTitle;
  String? _error;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    final message = widget.message;
    final runId = message.runId!;
    const timeout = Duration(seconds: 5);
    var report =
        widget.report ??
        TaskReport(
          available: true,
          goal: widget.prompt ?? message.planTitle ?? '任务结算',
          outcome: message.statusLabel,
          duration: message.elapsedLabel,
          answer: message.text,
        );
    try {
      if (widget.report == null) {
        final loaded = await widget.bridge.runReport(runId).timeout(timeout);
        if (loaded.available && !loaded.running) report = loaded;
      }
    } catch (_) {
      // Older or cleared records can still share the saved conversation turn.
    }
    if (!mounted) return;
    final selectedIndex = report.frames.isEmpty
        ? 0
        : (widget.initialFrameIndex ?? report.frames.length - 1).clamp(
            0,
            report.frames.length - 1,
          );
    Uint8List? frame;
    if (report.frames.isNotEmpty) {
      try {
        frame = await _frameBytes(report, selectedIndex);
        if (!mounted) return;
      } catch (_) {
        frame = null;
      }
    }
    if (mounted) {
      setState(
        () => _snapshot = (
          report: report,
          frame: frame,
          selectedIndex: selectedIndex,
        ),
      );
    }
  }

  Future<Uint8List?> _frameBytes(TaskReport report, int index) {
    final runId = widget.message.runId!;
    final screenshotId = report.frames[index].screenshotId;
    return _frames.putIfAbsent(
      index,
      () => widget.bridge
          .runScreen(runId, screenshotId)
          .timeout(const Duration(seconds: 5)),
    );
  }

  Future<void> _selectFrame(int index) async {
    final current = _snapshot;
    if (current == null || index == current.selectedIndex) return;
    setState(
      () => _snapshot = (
        report: current.report,
        frame: null,
        selectedIndex: index,
      ),
    );
    Uint8List? frame;
    try {
      frame = await _frameBytes(current.report, index);
    } catch (_) {
      frame = null;
    }
    if (mounted && _snapshot?.selectedIndex == index) {
      setState(
        () => _snapshot = (
          report: current.report,
          frame: frame,
          selectedIndex: index,
        ),
      );
    }
  }

  String _originalTitle(TaskReport report) => report.goal.trim().isNotEmpty
      ? report.goal.trim()
      : widget.prompt ?? '任务结算';

  Future<void> _editTitle(TaskReport report) async {
    final title = await showDialog<String>(
      context: context,
      builder: (_) =>
          _ShareTitleDialog(title: _editedTitle ?? _originalTitle(report)),
    );
    if (mounted && title != null) setState(() => _editedTitle = title);
  }

  Future<void> _share() async {
    if (_sharing) return;
    setState(() {
      _sharing = true;
      _error = null;
    });
    try {
      final boundary =
          _poster.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final image = await boundary.toImage(pixelRatio: 2);
      final ByteData? png;
      try {
        png = await image.toByteData(format: ui.ImageByteFormat.png);
      } finally {
        image.dispose();
      }
      if (!mounted) return;
      if (png == null ||
          !await widget.bridge.shareTaskImage(
            png.buffer.asUint8List(png.offsetInBytes, png.lengthInBytes),
          )) {
        throw StateError('Image share is unavailable');
      }
    } catch (error) {
      debugPrint('Task share image: ${error.runtimeType}');
      if (mounted) setState(() => _error = '未能分享图片，请重试。');
    } finally {
      if (mounted) setState(() => _sharing = false);
    }
  }

  Widget _framePicker(TaskReport report, int selectedIndex) => SizedBox(
    height: 92,
    child: ListView.separated(
      scrollDirection: Axis.horizontal,
      itemCount: report.frames.length,
      separatorBuilder: (_, _) => const SizedBox(width: 8),
      itemBuilder: (context, index) => FutureBuilder<Uint8List?>(
        future: _frameBytes(report, index),
        builder: (context, snapshot) {
          final bytes = snapshot.data;
          return Semantics(
            selected: index == selectedIndex,
            button: true,
            label: '第 ${index + 1} 帧',
            child: InkWell(
              key: ValueKey('share-frame-$index'),
              onTap: () => _selectFrame(index),
              borderRadius: BorderRadius.circular(10),
              child: Container(
                width: 76,
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: index == selectedIndex
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).dividerColor,
                    width: index == selectedIndex ? 2 : 1,
                  ),
                ),
                child: Column(
                  children: [
                    Expanded(
                      child: bytes == null
                          ? const Center(
                              child: Icon(
                                Icons.image_not_supported_outlined,
                                size: 18,
                              ),
                            )
                          : Image.memory(bytes, fit: BoxFit.contain),
                    ),
                    const SizedBox(height: 2),
                    Text('第 ${index + 1} 帧', style: MaeveType.hint),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final snapshot = _snapshot;
    return AppPage(
      title: '分享图',
      child: SafeArea(
        top: false,
        minimum: const EdgeInsets.fromLTRB(16, MaeveSize.topBar + 8, 16, 12),
        child: snapshot == null
            ? const Center(child: Text('正在生成分享图…'))
            : Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: FittedBox(
                      fit: BoxFit.contain,
                      child: RepaintBoundary(
                        key: _poster,
                        child: _TaskSharePoster(
                          report: snapshot.report,
                          message: widget.message,
                          title:
                              _editedTitle ?? _originalTitle(snapshot.report),
                          editedTitle: _editedTitle != null,
                          viewport: widget.viewport,
                          frame: snapshot.frame,
                          frameNumber: snapshot.selectedIndex + 1,
                        ),
                      ),
                    ),
                  ),
                  if (snapshot.report.frames.length > 1) ...[
                    const SizedBox(height: 8),
                    _framePicker(snapshot.report, snapshot.selectedIndex),
                  ],
                  if (_error case final error?)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(error, textAlign: TextAlign.center),
                    ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: MaeveGlassTextButton(
                          label: '编辑标题',
                          icon: Icons.edit_outlined,
                          onPressed: _sharing
                              ? null
                              : () => _editTitle(snapshot.report),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: MaeveGlassTextButton(
                          label: _sharing ? '正在分享…' : '分享图片',
                          icon: Icons.ios_share_outlined,
                          prominent: true,
                          enabled: !_sharing,
                          haptic: MaeveHaptic.light,
                          onPressed: _share,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
      ),
    );
  }
}

class _ShareTitleDialog extends StatefulWidget {
  const _ShareTitleDialog({required this.title});
  final String title;

  @override
  State<_ShareTitleDialog> createState() => _ShareTitleDialogState();
}

class _ShareTitleDialogState extends State<_ShareTitleDialog> {
  late final _title = TextEditingController(text: widget.title);

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaeveGlassDialog(
    title: const Text('编辑分享图标题'),
    content: TextField(
      controller: _title,
      autofocus: true,
      maxLength: 80,
      maxLines: 2,
      decoration: const InputDecoration(labelText: '标题'),
      onChanged: (_) => setState(() {}),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('取消'),
      ),
      FilledButton(
        onPressed: _title.text.trim().isEmpty
            ? null
            : () => Navigator.pop(context, _title.text.trim()),
        child: const Text('应用'),
      ),
    ],
  );
}

/// 暖白画报、紫色状态签；左侧为结算事实与回答摘要，右侧为任务画面。
class _TaskSharePoster extends StatelessWidget {
  const _TaskSharePoster({
    required this.report,
    required this.message,
    required this.title,
    required this.editedTitle,
    required this.viewport,
    required this.frame,
    required this.frameNumber,
  });

  static const _paper = Color(0xfffaf6f4);
  static const _ink = Color(0xff25202b);
  static const _muted = Color(0xff62556b);
  static const _rule = Color(0xffcfc3d6);
  static const _accent = Color(0xff7651c7);

  final TaskReport report;
  final Message message;
  final String title;
  final bool editedTitle;
  final Size viewport;
  final Uint8List? frame;
  final int frameNumber;

  @override
  Widget build(BuildContext context) {
    final outcome = report.outcome.isEmpty
        ? message.statusLabel
        : report.outcome;
    final verdict = report.verdict.trim();
    final duration = message.elapsedLabel ?? report.duration;
    final answer = shareAnswerSummary(report.answer);
    final leadLines = 6 - answer.codes.length;
    final answerStyle = MaeveType.caption.copyWith(color: _ink, height: 1.45);
    const phoneWidth = 208.0;
    final portrait = StepxNeoFrame.portraitViewport;
    final screen = viewport.width > viewport.height
        ? portrait.flipped
        : portrait;
    final frame = this.frame;
    return MediaQuery(
      data: MediaQuery.of(context).copyWith(textScaler: TextScaler.noScaling),
      child: ColoredBox(
        color: _paper,
        child: SizedBox(
          width: 540,
          child: Padding(
            padding: const EdgeInsets.all(27.5),
            child: DefaultTextStyle(
              style: MaeveType.body.copyWith(color: _ink),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    children: [
                      const MaeveMark(size: 23, color: _accent),
                      const SizedBox(width: 4),
                      Text(
                        'Maeve',
                        style: MaeveType.bodyStrong.copyWith(fontSize: 22),
                      ),
                      const Spacer(),
                      Text(
                        '本机任务实录',
                        style: MaeveType.caption.copyWith(color: _muted),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  const ColoredBox(color: _ink, child: SizedBox(height: 1)),
                  const SizedBox(height: 16),
                  Text(
                    title,
                    maxLines: editedTitle ? null : 2,
                    overflow: TextOverflow.ellipsis,
                    style: MaeveType.largeTitle.copyWith(
                      fontSize: 34,
                      height: 1.15,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 17),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            Text(
                              '结算状态',
                              style: MaeveType.caption.copyWith(color: _muted),
                            ),
                            const SizedBox(height: 6),
                            MaeveThemeScope(
                              brightness: Brightness.light,
                              accent: deriveAccent(MaeveAccentPreset.reverie),
                              child: MaeveGlassSurface(
                                tier: MaeveGlassTier.card,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(7),
                                ),
                                tint: _accent,
                                tintAlpha: .13,
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 7,
                                  vertical: 6,
                                ),
                                child: Text(
                                  verdict.isEmpty ? outcome : verdict,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: MaeveType.caption.copyWith(
                                    color: const Color(0xff533182),
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ),
                            ),
                            if (verdict.isNotEmpty && verdict != outcome) ...[
                              const SizedBox(height: 5),
                              Text(
                                outcome,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: MaeveType.caption.copyWith(
                                  color: _muted,
                                ),
                              ),
                            ],
                            const SizedBox(height: 13),
                            if (message.steps > 0)
                              _fact('执行步数', '${message.steps} 步'),
                            if (duration != null && duration.isNotEmpty)
                              _fact('任务耗时', duration),
                            if (report.reviewClaims case (
                              matched: final matched,
                              total: final total,
                            ))
                              _fact('可核对事实', '$matched / $total 项'),
                            const SizedBox(height: 12),
                            const ColoredBox(
                              color: _rule,
                              child: SizedBox(height: .5),
                            ),
                            const SizedBox(height: 10),
                            Text(
                              '回答',
                              style: MaeveType.label.copyWith(color: _muted),
                            ),
                            const SizedBox(height: 5),
                            if (leadLines > 0 &&
                                (answer.lead.isNotEmpty ||
                                    answer.codes.isEmpty))
                              Text(
                                answer.lead.isEmpty
                                    ? '这次任务没有保存最终回答。'
                                    : answer.lead,
                                key: const ValueKey('share-answer'),
                                maxLines: leadLines,
                                overflow: TextOverflow.ellipsis,
                                style: answerStyle,
                              ),
                            for (final code in answer.codes)
                              SizedBox(
                                width: double.infinity,
                                child: FittedBox(
                                  fit: BoxFit.scaleDown,
                                  alignment: Alignment.centerLeft,
                                  child: Text(
                                    code,
                                    maxLines: 1,
                                    style: answerStyle,
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                      if (frame != null) ...[
                        const SizedBox(width: 16),
                        Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            StepxNeoFrame(
                              viewport: screen,
                              width: phoneWidth,
                              child: Image.memory(
                                frame,
                                fit: BoxFit.contain,
                                semanticLabel: '第 $frameNumber 帧操作画面',
                                errorBuilder: (_, _, _) =>
                                    const SizedBox.shrink(),
                              ),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              '第 $frameNumber 帧操作画面',
                              style: MaeveType.hint.copyWith(color: _muted),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                  const SizedBox(height: 22),
                  const ColoredBox(color: _rule, child: SizedBox(height: .5)),
                  const SizedBox(height: 10),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '下一幕，由你决定',
                        style: MaeveType.caption.copyWith(
                          color: _ink,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        'github.com/Daiyimo/Maeve',
                        style: MaeveType.hint.copyWith(color: _muted),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _fact(String label, String value) => Container(
    padding: const EdgeInsets.symmetric(vertical: 8),
    decoration: const BoxDecoration(
      border: Border(top: BorderSide(color: _rule, width: .5)),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: MaeveType.caption.copyWith(color: _muted)),
        const SizedBox(height: 3),
        Text(
          value,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: MaeveType.bodyStrong.copyWith(fontSize: 16),
        ),
      ],
    ),
  );
}

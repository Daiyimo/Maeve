import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_accent.dart';
import '../design/maeve_surfaces.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/conversation.dart';
import '../models/task_report.dart';
import '../ui/common/app_page.dart';
import '../ui/common/haptics.dart';
import 'completed_execution_panel.dart';
import 'glass_surface.dart';
import 'maeve_mark.dart';

class TaskSharePage extends StatefulWidget {
  const TaskSharePage({
    super.key,
    required this.bridge,
    required this.message,
    required this.prompt,
    required this.viewport,
    required this.screen,
  });

  final MaeveChatBridge bridge;
  final Message message;
  final String? prompt;
  final Size viewport;
  final Widget Function(SettlementSnapshot) screen;

  @override
  State<TaskSharePage> createState() => _TaskSharePageState();
}

class _TaskSharePageState extends State<TaskSharePage> {
  final _poster = GlobalKey();
  SettlementSnapshot? _snapshot;
  bool _sharing = false;
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
    var report = TaskReport(
      available: true,
      goal: widget.prompt ?? message.planTitle ?? '任务结算',
      outcome: message.statusLabel,
      duration: message.elapsedLabel,
      answer: message.text,
    );
    try {
      final loaded = await widget.bridge.runReport(runId).timeout(timeout);
      if (loaded.available && !loaded.running) report = loaded;
    } catch (_) {
      // Older or cleared records can still share the saved conversation turn.
    }
    if (!mounted) return;
    Uint8List? frame;
    if (report.frames.isNotEmpty) {
      try {
        frame = await widget.bridge
            .runScreen(runId, report.frames.last.screenshotId)
            .timeout(timeout);
        if (!mounted) return;
        if (frame != null) await precacheImage(MemoryImage(frame), context);
      } catch (_) {
        frame = null;
      }
    }
    if (mounted) setState(() => _snapshot = (report: report, frame: frame));
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
                          title: widget.prompt,
                          viewport: widget.viewport,
                          screen: widget.screen(snapshot),
                        ),
                      ),
                    ),
                  ),
                  if (_error case final error?)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(error, textAlign: TextAlign.center),
                    ),
                  const SizedBox(height: 12),
                  MaeveGlassTextButton(
                    label: _sharing ? '正在分享…' : '分享图片',
                    icon: Icons.ios_share_outlined,
                    prominent: true,
                    enabled: !_sharing,
                    haptic: MaeveHaptic.light,
                    onPressed: _share,
                  ),
                ],
              ),
      ),
    );
  }
}

/// 暖白画报、紫色玻璃状态签、右侧保留原设备比例的主页结算截屏。
class _TaskSharePoster extends StatelessWidget {
  const _TaskSharePoster({
    required this.report,
    required this.message,
    required this.title,
    required this.viewport,
    required this.screen,
  });

  static const _paper = Color(0xfffaf6f4);
  static const _ink = Color(0xff25202b);
  static const _muted = Color(0xff62556b);
  static const _rule = Color(0xffcfc3d6);
  static const _accent = Color(0xff7651c7);

  final TaskReport report;
  final Message message;
  final String? title;
  final Size viewport;
  final Widget screen;

  @override
  Widget build(BuildContext context) {
    final goal = report.goal.trim().isNotEmpty
        ? report.goal.trim()
        : (title ?? '任务结算');
    final outcome = report.outcome.isEmpty
        ? message.statusLabel
        : report.outcome;
    final verdict = report.verdict.trim();
    final duration = message.elapsedLabel ?? report.duration;
    final deviceHeight = _SharePhone.heightFor(viewport);
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
                    goal,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: MaeveType.largeTitle.copyWith(
                      fontSize: 34,
                      height: 1.15,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 17),
                  SizedBox(
                    height: math.max(deviceHeight, 248),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Text(
                                '结算状态',
                                style: MaeveType.caption.copyWith(
                                  color: _muted,
                                ),
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
                            ],
                          ),
                        ),
                        const SizedBox(width: 12),
                        Center(
                          child: _SharePhone(
                            viewport: viewport,
                            screen: screen,
                          ),
                        ),
                      ],
                    ),
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

class _SharePhone extends StatelessWidget {
  const _SharePhone({required this.viewport, required this.screen});

  static const _width = 351.0;
  static const _rim = 5.5;
  final Size viewport;
  final Widget screen;

  static double heightFor(Size viewport) =>
      (_width - _rim * 2) / viewport.aspectRatio + _rim * 2;

  @override
  Widget build(BuildContext context) => Container(
    width: _width,
    height: heightFor(viewport),
    padding: const EdgeInsets.all(_rim),
    decoration: BoxDecoration(
      color: const Color(0xff211c27),
      borderRadius: BorderRadius.circular(28),
      border: Border.all(
        color: const Color(0xff504758),
        width: .5,
        strokeAlign: BorderSide.strokeAlignInside,
      ),
      boxShadow: const [
        BoxShadow(
          color: Color(0x402d1838),
          offset: Offset(0, 11),
          blurRadius: 21,
          spreadRadius: -9,
        ),
      ],
    ),
    child: ClipRRect(
      borderRadius: BorderRadius.circular(22.5),
      child: FittedBox(
        fit: BoxFit.contain,
        child: SizedBox.fromSize(
          size: viewport,
          child: ExcludeSemantics(
            child: ExcludeFocus(
              child: IgnorePointer(
                child: Stack(
                  fit: StackFit.expand,
                  children: [const MaeveBackdrop(), screen],
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_shapes.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/conversation.dart';
import '../models/task_report.dart';
import '../ui/common/app_page.dart';
import '../ui/common/haptics.dart';
import 'glass_surface.dart';
import 'task_playback_view.dart';
import 'task_settlement_view.dart';
import 'task_share_page.dart';

export 'task_playback_view.dart' show TaskPlaybackPage, TaskPlaybackView;

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
          TaskReportCard(report: report),
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
              TaskReportExportButton(
                bridge: widget.bridge,
                runId: widget.runId,
                onNotice: (notice) => setState(() => _notice = notice),
              ),
              if (!report.running)
                MaeveGlassTextButton(
                  label: '分享图',
                  icon: Icons.ios_share_outlined,
                  onPressed: () => AppPage.open(
                    context,
                    TaskSharePage(
                      bridge: widget.bridge,
                      message: Message(
                        report.answer,
                        isUser: false,
                        runId: widget.runId,
                      ),
                      prompt: report.goal,
                      viewport: MediaQuery.sizeOf(context),
                      report: report,
                    ),
                  ),
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
            Text('关键页面', style: MaeveType.bodyStrong.copyWith(color: c.ink)),
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

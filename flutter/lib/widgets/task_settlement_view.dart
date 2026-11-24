import 'dart:async';

import 'package:flutter/material.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_shapes.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/duration_format.dart';
import '../models/task_report.dart';
import '../ui/common/app_sheet.dart';
import '../ui/common/haptics.dart';
import 'glass_surface.dart';
import 'maeve_markdown.dart';

/// 结算主卡：目标、结局、标准与最终回答。
class TaskReportCard extends StatelessWidget {
  const TaskReportCard({
    super.key,
    required this.report,
    this.compact = false,
    this.detail,
    this.embedded = false,
  });
  final TaskReport report;
  final bool compact;
  final String? detail;
  final bool embedded;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final summary = [
      report.outcome,
      report.verdict,
    ].where((s) => s.isNotEmpty).join(' · ');
    final duration = report.duration == null
        ? null
        : durationLabel(null, fallback: report.duration);
    final bodyStyle = compact
        ? MaeveType.caption.copyWith(fontSize: 11.5, height: 16 / 11.5)
        : MaeveType.body;
    final strongStyle = compact
        ? bodyStyle.copyWith(fontWeight: FontWeight.w600)
        : MaeveType.bodyStrong;
    final labelStyle = compact
        ? MaeveType.hint.copyWith(fontSize: 10.5, height: 14 / 10.5)
        : MaeveType.label;
    final gap = compact ? 8.0 : 16.0;
    final details = [
      if (report.reviewDetail case final String review) review,
      if (detail case final String messageDetail) messageDetail,
    ].where((s) => s.trim().isNotEmpty && s != report.answer).toSet();
    final padding = EdgeInsets.all(compact ? MaeveSpace.s12 : MaeveSpace.s20);
    final content = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          report.goal,
          style: MaeveType.title.copyWith(
            fontSize: compact ? 14.5 : 21,
            fontWeight: FontWeight.w700,
            color: c.ink,
            height: compact ? 1.3 : 1.35,
          ),
        ),
        if (summary.isNotEmpty) ...[
          SizedBox(height: gap),
          Container(
            decoration: BoxDecoration(
              color: c.accent.withValues(alpha: .10),
              borderRadius: BorderRadius.circular(MaeveRadius.sm),
              border: Border(left: BorderSide(color: c.accent, width: 3)),
            ),
            padding: EdgeInsets.symmetric(
              horizontal: compact ? MaeveSpace.s8 : MaeveSpace.s12,
              vertical: compact ? 6 : MaeveSpace.s10,
            ),
            child: Text.rich(
              TextSpan(
                style: strongStyle.copyWith(color: c.ink),
                children: [
                  TextSpan(text: summary),
                  if (duration != null)
                    TextSpan(
                      text: ' · $duration',
                      style: MaeveType.mono.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
        for (final item in details) ...[
          SizedBox(height: gap / 2),
          Text(item, style: bodyStyle.copyWith(color: c.ink2)),
        ],
        if (report.criteria.isNotEmpty) ...[
          SizedBox(height: gap),
          Text('做成的标准', style: labelStyle.copyWith(color: c.ink3)),
          SizedBox(height: gap / 2),
          for (final item in report.criteria)
            Padding(
              padding: const EdgeInsets.only(bottom: MaeveSpace.s4),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('· ', style: bodyStyle.copyWith(color: c.ink3)),
                  Expanded(
                    child: Text(item, style: bodyStyle.copyWith(color: c.ink2)),
                  ),
                ],
              ),
            ),
        ],
        SizedBox(height: compact ? 12 : MaeveSpace.s20),
        Text('回答', style: labelStyle.copyWith(color: c.ink3)),
        SizedBox(height: gap / 2),
        if (report.hasAnswer)
          MaeveMarkdown(data: report.answer, style: compact ? bodyStyle : null)
        else
          Text('这次任务没有保存最终回答。', style: bodyStyle.copyWith(color: c.ink3)),
      ],
    );
    return embedded
        ? Padding(padding: padding, child: content)
        : MaeveGlassSurface(
            tier: MaeveGlassTier.card,
            shape: MaeveShapes.card,
            padding: padding,
            child: content,
          );
  }
}

/// 任务报告页的导出按钮。
class TaskReportExportButton extends StatefulWidget {
  const TaskReportExportButton({
    super.key,
    required this.bridge,
    required this.runId,
    this.onNotice,
  });
  final MaeveChatBridge bridge;
  final String runId;
  final ValueChanged<String>? onNotice;

  @override
  State<TaskReportExportButton> createState() => _TaskReportExportButtonState();
}

class _TaskReportExportButtonState extends State<TaskReportExportButton> {
  bool _exporting = false;

  Future<void> _export() async {
    if (_exporting) return;
    final choice =
        await showMaeveSheet<({ReportFormat format, String redaction})>(
          context: context,
          builder: (_) => _ExportSheet(runId: widget.runId),
        );
    if (choice == null || !mounted) return;
    setState(() => _exporting = true);
    String state;
    try {
      state = await widget.bridge.exportRunReport(
        widget.runId,
        choice.format,
        redaction: choice.redaction,
      );
    } catch (_) {
      state = 'failed';
    }
    if (!mounted) return;
    setState(() => _exporting = false);
    final notice = switch (state) {
      'saved' => '报告已保存',
      'cancelled' => '已取消导出',
      _ => '没能保存，请检查记录是否还在、所选位置能否写入后重试。',
    };
    widget.onNotice?.call(notice);
    if (state == 'saved') unawaited(MaeveHaptics.play(MaeveHaptic.light));
    if (state == 'failed') unawaited(MaeveHaptics.play(MaeveHaptic.reject));
  }

  @override
  Widget build(BuildContext context) => MaeveGlassTextButton(
    label: '导出',
    icon: Icons.ios_share_rounded,
    compact: true,
    enabled: !_exporting,
    haptic: MaeveHaptic.tick,
    onPressed: _exporting ? null : _export,
  );
}

class _ExportSheet extends StatefulWidget {
  const _ExportSheet({required this.runId});
  final String runId;

  @override
  State<_ExportSheet> createState() => _ExportSheetState();
}

class _ExportSheetState extends State<_ExportSheet> {
  bool _includePii = false;

  @override
  Widget build(BuildContext context) => AppSheet(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SheetTitleBar(
          title: '导出任务报告',
          subtitle: 'Maeve-${widget.runId}',
          showGrabber: true,
        ),
        const SizedBox(height: MaeveSpace.s16),
        Material(
          type: MaterialType.transparency,
          child: SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: _includePii,
            onChanged: (value) => setState(() => _includePii = value),
            title: const Text('同时隐藏手机号、邮箱和私有路径'),
            subtitle: const Text('默认始终隐藏密钥;截图和附件字节本身不会匿名化。'),
          ),
        ),
        const SizedBox(height: MaeveSpace.s8),
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
              haptic: null,
              onPressed: () => Navigator.of(context).pop((
                format: format,
                redaction: _includePii ? 'secretsAndPii' : 'secrets',
              )),
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

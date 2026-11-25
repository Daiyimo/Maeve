import 'dart:async';

import 'package:flutter/material.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/execution.dart';
import '../models/task_report.dart';
import 'adaptive_execution_layout.dart';
import 'execution_section.dart';
import 'glass_surface.dart';
import 'live_execution_feed.dart';
import 'task_playback_view.dart';
import 'task_preview.dart';
import 'task_settlement_view.dart';

/// 任务完成后的同一执行面板：左侧回放，右侧结算。
class CompletedExecutionPanel extends StatefulWidget {
  const CompletedExecutionPanel({
    super.key,
    required this.bridge,
    required this.runId,
    this.fallbackReport,
    this.detail,
    this.stopped = false,
  });
  final MaeveChatBridge bridge;
  final String? runId;
  final TaskReport? fallbackReport;
  final String? detail;
  final bool stopped;

  @override
  State<CompletedExecutionPanel> createState() =>
      _CompletedExecutionPanelState();
}

class _CompletedExecutionPanelState extends State<CompletedExecutionPanel> {
  TaskReport? _report;
  String? _error;
  bool _loading = false;
  int _attempt = 0;
  final _frameSelection = ValueNotifier<int?>(null);
  bool _showTimeline = false;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  @override
  void dispose() {
    _frameSelection.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant CompletedExecutionPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.runId != widget.runId) {
      _report = null;
      _error = null;
      _attempt = 0;
      unawaited(_load());
    }
  }

  Future<void> _load() async {
    final runId = widget.runId;
    if (_loading || runId == null) return;
    _loading = true;
    _attempt++;
    try {
      final report = await widget.bridge.runReport(runId);
      if (!mounted) return;
      setState(() {
        _report = report;
        _error = null;
      });
      if (report.running && _attempt < 5) {
        _loading = false;
        await Future<void>.delayed(const Duration(milliseconds: 800));
        if (mounted) await _load();
      }
    } catch (_) {
      if (mounted) setState(() => _error = '未能读取任务报告，请重试。');
    } finally {
      _loading = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final loaded = _report;
    final report =
        loaded?.available == true && !(widget.stopped && loaded!.running)
        ? loaded
        : widget.fallbackReport;
    final error = _error;
    final errorView = error == null
        ? null
        : Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(error, style: MaeveType.body.copyWith(color: c.bad)),
                const SizedBox(height: 8),
                MaeveGlassTextButton(
                  label: '重试',
                  compact: true,
                  onPressed: _load,
                ),
              ],
            ),
          );
    if (errorView != null && (!widget.stopped || report == null)) {
      return errorView;
    }
    if (report == null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Text(
          loaded == null || loaded.running ? '结算正在生成…' : '这次记录不存在或已被清理。',
          style: MaeveType.body.copyWith(color: c.ink2),
        ),
      );
    }
    final runId = widget.runId;
    Widget playback({required bool compact}) {
      final player = runId == null
          ? null
          : TaskPlaybackView(
              bridge: widget.bridge,
              runId: runId,
              report: report,
              initialFrame: report.frames.isEmpty
                  ? 0
                  : report.frames.length - 1,
              showStages: false,
              compact: compact,
              phoneStage: true,
              frameSelection: _frameSelection,
            );
      return ExecutionSection(
        title: widget.stopped || compact ? '操作回放' : null,
        frameKey: ValueKey(
          widget.stopped
              ? 'stopped-playback-frame'
              : 'completed-playback-frame',
        ),
        glass: true,
        body: player == null || report.frames.isEmpty
            ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(MaeveSpace.s8),
                  child: Text(
                    '这次任务没有记录画面。',
                    textAlign: TextAlign.center,
                    style: MaeveType.caption.copyWith(color: c.ink2),
                  ),
                ),
              )
            : Padding(
                padding: EdgeInsets.symmetric(
                  horizontal: compact ? MaeveSpace.s4 : MaeveSpace.s12,
                  vertical: compact ? MaeveSpace.s8 : MaeveSpace.s12,
                ),
                child: player,
              ),
      );
    }

    final frameByStep = {
      for (final entry in report.frames.asMap().entries)
        entry.value.stepIndex: entry.key,
    };
    final recordings = <int, RecordedPreview>{
      for (final entry in frameByStep.entries)
        entry.key: (
          step: entry.key,
          screenId: report.frames[entry.value].screenshotId,
          pointer: report.frames[entry.value].pointer,
        ),
    };
    final steps = [
      for (final entry in frameByStep.entries)
        ExecutionStep(
          step: entry.key,
          title:
              report.frames[entry.value].title ??
              '步骤 ${report.frames[entry.value].step}',
          detail:
              report.frames[entry.value].failure ?? '第 ${entry.value + 1} 帧',
          resultFailed: report.frames[entry.value].failed,
        ),
    ];
    final hasTimeline = runId != null && steps.isNotEmpty;

    Widget timeline({required bool showHeader}) => ValueListenableBuilder<int?>(
      valueListenable: _frameSelection,
      builder: (context, index, _) => LiveExecutionFeed(
        steps: steps,
        height: 175,
        compact: true,
        showHeader: showHeader,
        recordings: recordings,
        selected:
            recordings[report
                .frames[(index ?? report.frames.length - 1).clamp(
                  0,
                  report.frames.length - 1,
                )]
                .stepIndex],
        onSelect: (recording) =>
            _frameSelection.value = frameByStep[recording.step],
      ),
    );

    Widget settlement({required bool compact}) {
      final showTimeline = compact && hasTimeline && _showTimeline;
      final card = TaskReportCard(
        key: const ValueKey('task-settlement-view'),
        report: report,
        detail: widget.detail,
        compact: compact,
        embedded: true,
      );
      final body = SingleChildScrollView(
        child: errorView == null
            ? card
            : Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [card, errorView],
              ),
      );
      return ExecutionSection(
        title: showTimeline
            ? '执行时间线'
            : widget.stopped || compact
            ? '任务结算'
            : null,
        action: compact && hasTimeline
            ? MaeveGlassTextButton(
                label: showTimeline ? '结算' : '时间线',
                compact: true,
                onPressed: () => setState(() => _showTimeline = !_showTimeline),
              )
            : null,
        frameKey: ValueKey(
          widget.stopped
              ? 'stopped-settlement-frame'
              : 'completed-settlement-frame',
        ),
        glass: true,
        body: showTimeline
            ? timeline(showHeader: false)
            : !compact && hasTimeline
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(height: 175, child: timeline(showHeader: true)),
                  Expanded(child: body),
                ],
              )
            : body,
      );
    }

    return AdaptiveExecutionLayout(
      key: const ValueKey('completed-execution-layout'),
      wideHeight: 520,
      compactHeight: switch (report.frames.length) {
        0 => 302,
        1 => 334,
        _ => 378,
      },
      left: playback(compact: false),
      compactLeft: playback(compact: true),
      right: settlement(compact: false),
      compactRight: settlement(compact: true),
    );
  }
}

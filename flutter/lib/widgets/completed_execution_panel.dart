import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/task_report.dart';
import 'adaptive_execution_layout.dart';
import 'execution_section.dart';
import 'glass_surface.dart';
import 'task_playback_view.dart';
import 'task_settlement_view.dart';

typedef SettlementSnapshot = ({TaskReport report, Uint8List? frame});

/// 任务完成后的同一执行面板：左侧回放，右侧结算。
class CompletedExecutionPanel extends StatefulWidget {
  const CompletedExecutionPanel({
    super.key,
    required this.bridge,
    required this.runId,
    this.fallbackReport,
    this.detail,
    this.snapshot,
    this.stopped = false,
  });
  final MaeveChatBridge bridge;
  final String? runId;
  final TaskReport? fallbackReport;
  final String? detail;
  final SettlementSnapshot? snapshot;
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

  @override
  void initState() {
    super.initState();
    if (widget.snapshot == null) unawaited(_load());
  }

  @override
  void didUpdateWidget(covariant CompletedExecutionPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.runId != widget.runId) {
      _report = null;
      _error = null;
      _attempt = 0;
      if (widget.snapshot == null) unawaited(_load());
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
        widget.snapshot?.report ??
        (loaded?.available == true && !(widget.stopped && loaded!.running)
            ? loaded
            : widget.fallbackReport);
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
    final frozenFrame = widget.snapshot == null
        ? null
        : (bytes: widget.snapshot!.frame);
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
              imageHeight: compact ? 150 : 280,
              showStages: false,
              compact: compact,
              frozenFrame: frozenFrame,
            );
      return ExecutionSection(
        title: widget.stopped ? '操作回放' : null,
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
            : SingleChildScrollView(
                padding: EdgeInsets.symmetric(
                  horizontal: compact ? MaeveSpace.s4 : MaeveSpace.s12,
                  vertical: compact ? MaeveSpace.s8 : MaeveSpace.s12,
                ),
                child: player,
              ),
      );
    }

    Widget settlement({required bool compact}) {
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
        title: widget.stopped ? '任务结算' : null,
        frameKey: ValueKey(
          widget.stopped
              ? 'stopped-settlement-frame'
              : 'completed-settlement-frame',
        ),
        glass: true,
        body: body,
      );
    }

    return AdaptiveExecutionLayout(
      key: const ValueKey('completed-execution-layout'),
      wideHeight: 520,
      compactHeight: 360,
      left: playback(compact: false),
      compactLeft: playback(compact: true),
      right: settlement(compact: false),
      compactRight: settlement(compact: true),
    );
  }
}

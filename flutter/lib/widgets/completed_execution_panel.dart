import 'dart:async';

import 'package:flutter/material.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/task_report.dart';
import 'adaptive_execution_layout.dart';
import 'glass_surface.dart';
import 'task_playback_view.dart';
import 'task_settlement_view.dart';

/// 任务完成后的同一执行面板：左侧回放，右侧结算。
class CompletedExecutionPanel extends StatefulWidget {
  const CompletedExecutionPanel({
    super.key,
    required this.bridge,
    required this.runId,
    this.fallbackReport,
    this.detail,
    this.onOpenFullReport,
  });
  final MaeveChatBridge bridge;
  final String runId;
  final TaskReport? fallbackReport;
  final String? detail;
  final VoidCallback? onOpenFullReport;

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
    unawaited(_load());
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
    if (_loading) return;
    _loading = true;
    _attempt++;
    try {
      final report = await widget.bridge.runReport(widget.runId);
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
    final report = loaded?.available == true ? loaded : widget.fallbackReport;
    if (report == null && _error == null) {
      return const Padding(padding: EdgeInsets.all(24), child: Text('正在生成结算…'));
    }
    if (_error != null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(_error!, style: MaeveType.body.copyWith(color: c.bad)),
            const SizedBox(height: 8),
            MaeveGlassTextButton(label: '重试', compact: true, onPressed: _load),
          ],
        ),
      );
    }
    if (report == null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Text(
          loaded?.running == true ? '结算正在生成…' : '这次记录不存在或已被清理。',
          style: MaeveType.body.copyWith(color: c.ink2),
        ),
      );
    }
    return AdaptiveExecutionLayout(
      key: const ValueKey('completed-execution-layout'),
      wideHeight: 520,
      compactHeight: 360,
      left: TaskPlaybackView(
        bridge: widget.bridge,
        runId: widget.runId,
        report: report,
        initialFrame: report.frames.isEmpty ? 0 : report.frames.length - 1,
        imageHeight: 280,
        showStages: false,
      ),
      compactLeft: SingleChildScrollView(
        child: TaskPlaybackView(
          bridge: widget.bridge,
          runId: widget.runId,
          report: report,
          initialFrame: report.frames.isEmpty ? 0 : report.frames.length - 1,
          imageHeight: 150,
          showStages: false,
          compact: true,
        ),
      ),
      right: SingleChildScrollView(
        child: TaskSettlementView(
          bridge: widget.bridge,
          runId: widget.runId,
          report: report,
          detail: widget.detail,
          onOpenFullReport: widget.onOpenFullReport,
        ),
      ),
      compactRight: SingleChildScrollView(
        child: TaskSettlementView(
          bridge: widget.bridge,
          runId: widget.runId,
          report: report,
          detail: widget.detail,
          onOpenFullReport: widget.onOpenFullReport,
          compact: true,
        ),
      ),
    );
  }
}

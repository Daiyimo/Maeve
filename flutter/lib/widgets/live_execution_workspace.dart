import 'dart:async';

import 'package:flutter/material.dart';

import '../bridge/maeve_chat_bridge.dart';
import '../models/execution.dart';
import 'adaptive_execution_layout.dart';
import 'live_execution_feed.dart';
import 'task_preview.dart';

class LiveExecutionWorkspace extends StatefulWidget {
  const LiveExecutionWorkspace({
    super.key,
    required this.bridge,
    required this.conversationId,
    required this.runId,
    required this.steps,
  });

  final MaeveChatBridge bridge;
  final String conversationId;
  final String? runId;
  final List<ExecutionStep> steps;

  @override
  State<LiveExecutionWorkspace> createState() => _LiveExecutionWorkspaceState();
}

class _LiveExecutionWorkspaceState extends State<LiveExecutionWorkspace> {
  Map<int, RecordedPreview> _recordings = const {};
  RecordedPreview? _selected;
  bool _reading = false;
  int _refreshCount = 0;

  Future<void> _refreshRecordings() async {
    final runId = widget.runId;
    if (runId == null ||
        _reading ||
        widget.steps.every((step) => _recordings.containsKey(step.step))) {
      return;
    }
    // The guarded preview ticks every 800 ms. Reading the run log every third
    // tick gives the recorder time to finish writing the current step.
    if (_refreshCount++ % 3 != 0) return;
    _reading = true;
    try {
      final details = await widget.bridge.runDetails(runId);
      if (!mounted) return;
      final recordings = {..._recordings};
      for (final step in details.steps) {
        if (recordings.containsKey(step.step)) continue;
        final pointer = step.pointers.reversed
            .where((pointer) => pointer.screenshotId != null)
            .firstOrNull;
        final id = pointer?.screenshotId ?? step.screenshotId;
        if (id != null) {
          recordings[step.step] = (
            step: step.step,
            screenId: id,
            pointer: pointer,
          );
        }
      }
      if (recordings.length != _recordings.length) {
        setState(() => _recordings = recordings);
      }
    } catch (_) {
      // The live picture is still available while the run log is being written.
    } finally {
      _reading = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    Widget preview({required bool compact}) => TaskPreview(
      bridge: widget.bridge,
      conversationId: widget.conversationId,
      imageHeight: null,
      compact: compact,
      runId: widget.runId,
      recording: _selected,
      onReturnToLive: () => setState(() => _selected = null),
      onRefreshRecords: () => unawaited(_refreshRecordings()),
    );
    Widget timeline({required bool compact}) => LiveExecutionFeed(
      key: ValueKey('live-feed-${widget.runId ?? widget.conversationId}'),
      steps: widget.steps,
      height: 275,
      compact: compact,
      recordings: _recordings,
      selected: _selected,
      onSelect: (recording) => setState(() => _selected = recording),
    );
    return AdaptiveExecutionLayout(
      wideHeight: 390,
      left: preview(compact: false),
      compactLeft: preview(compact: true),
      right: timeline(compact: false),
      compactRight: timeline(compact: true),
    );
  }
}

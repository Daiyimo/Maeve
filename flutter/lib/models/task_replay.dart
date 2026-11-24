part of 'task_report.dart';

/// 报告里的关键页：标题来自代码判定的回答来源，截图是那一页真存下的画面。
class ReportPage {
  const ReportPage({
    required this.title,
    required this.stepIndex,
    required this.screenshotId,
  });
  final String title;
  final int stepIndex, screenshotId;
  factory ReportPage.fromMap(Map map) => ReportPage(
    title: map['title'] as String? ?? '',
    stepIndex: (map['stepIndex'] as num?)?.toInt() ?? 0,
    screenshotId: (map['screenshotId'] as num?)?.toInt() ?? 0,
  );
}

/// 回放里的阶段入口：开始、输入、关键页。
class PlaybackStage {
  const PlaybackStage({
    required this.title,
    required this.stepIndex,
    this.screenshotId,
  });
  final String title;
  final int stepIndex;
  final int? screenshotId;
  factory PlaybackStage.fromMap(Map map) => PlaybackStage(
    title: map['title'] as String? ?? '',
    stepIndex: (map['stepIndex'] as num?)?.toInt() ?? 0,
    screenshotId: (map['screenshotId'] as num?)?.toInt(),
  );
}

/// 播放序列里的一帧。[after] 为 false 是操作前（带真实落点），true 是操作后，
/// null 是旧记录里只有一张的画面。
class ReplayFrame {
  const ReplayFrame({
    required this.stepIndex,
    required this.screenshotId,
    required this.step,
    this.after,
    this.durationMs = 2400,
    this.title,
    this.failure,
    this.pointer,
  });
  final int stepIndex, screenshotId, step, durationMs;
  final bool? after;
  final String? title, failure;
  final ExecutionPointer? pointer;

  bool get beforeAction => after == false;
  bool get failed => failure != null && failure!.isNotEmpty;

  factory ReplayFrame.fromMap(Map map) {
    final raw = map['pointer'];
    final pointer = raw is Map ? ExecutionPointer.fromMap(raw) : null;
    return ReplayFrame(
      stepIndex: (map['stepIndex'] as num?)?.toInt() ?? 0,
      screenshotId: (map['screenshotId'] as num?)?.toInt() ?? 0,
      step: (map['step'] as num?)?.toInt() ?? 0,
      after: map['after'] as bool?,
      durationMs: (map['durationMs'] as num?)?.toInt() ?? 2400,
      title: map['title'] as String?,
      failure: map['failure'] as String?,
      pointer: pointer != null && pointer.valid ? pointer : null,
    );
  }
}

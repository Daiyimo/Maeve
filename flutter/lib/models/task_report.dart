import 'execution.dart';

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

class TaskReport {
  const TaskReport({
    this.available = false,
    this.running = false,
    this.goal = '',
    this.outcome = '',
    this.verdict = '',
    this.reviewDetail,
    this.duration,
    this.answer = '',
    this.criteria = const [],
    this.pages = const [],
    this.stages = const [],
    this.frames = const [],
    this.missingFrames = 0,
  });
  final bool available, running;
  final String goal, outcome, verdict, answer;
  final String? duration, reviewDetail;
  final List<String> criteria;
  final List<ReportPage> pages;
  final List<PlaybackStage> stages;
  final List<ReplayFrame> frames;
  final int missingFrames;

  bool get hasAnswer => answer.trim().isNotEmpty;

  factory TaskReport.fromMap(Map map) => TaskReport(
    available: map['available'] == true,
    running: map['running'] == true,
    goal: map['goal'] as String? ?? '',
    outcome: map['outcome'] as String? ?? '',
    verdict: map['verdict'] as String? ?? '',
    reviewDetail: map['reviewDetail'] as String?,
    duration: map['duration'] as String?,
    answer: map['answer'] as String? ?? '',
    missingFrames: (map['missingFrames'] as num?)?.toInt() ?? 0,
    criteria: [
      for (final c in map['criteria'] as List? ?? const [])
        if (c is String) c,
    ],
    pages: [
      for (final p in map['pages'] as List? ?? const [])
        if (p is Map) ReportPage.fromMap(p),
    ],
    stages: [
      for (final s in map['stages'] as List? ?? const [])
        if (s is Map) PlaybackStage.fromMap(s),
    ],
    frames: [
      for (final f in map['frames'] as List? ?? const [])
        if (f is Map) ReplayFrame.fromMap(f),
    ],
  );
}

/// 导出格式；名字要和原生 TaskReportExport.Format 对得上。
enum ReportFormat {
  markdown('markdown', 'Markdown', 'md'),
  word('word', 'Word', 'docx'),
  text('text', '纯文本', 'txt');

  const ReportFormat(this.wire, this.label, this.extension);
  final String wire, label, extension;
}

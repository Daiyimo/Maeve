import 'execution.dart';

part 'task_replay.dart';

class TaskReport {
  const TaskReport({
    this.available = false,
    this.running = false,
    this.goal = '',
    this.outcome = '',
    this.verdict = '',
    this.reviewDetail,
    this.reviewClaims,
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
  final ({int matched, int total})? reviewClaims;
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
    reviewClaims: switch (map['reviewClaims']) {
      {'matched': final int matched, 'total': final int total}
          when total > 0 && matched >= 0 && matched <= total =>
        (matched: matched, total: total),
      _ => null,
    },
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

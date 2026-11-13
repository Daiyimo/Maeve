import 'token_usage.dart';

class ExecutionAction {
  const ExecutionAction({
    required this.label,
    this.target,
    this.effect,
    this.failed = false,
  });
  final String label;
  final String? target, effect;
  final bool failed;
  factory ExecutionAction.fromMap(Map map) => ExecutionAction(
    label: map['label'] as String? ?? '',
    target: map['target'] as String?,
    effect: map['effect'] as String?,
    failed: map['failed'] == true,
  );
}

class ExecutionPointer {
  const ExecutionPointer({
    required this.width,
    required this.height,
    required this.method,
    this.x,
    this.y,
    this.bounds = const [],
    this.clicks = 1,
    this.screenshotId,
  });
  final int width, height, clicks;
  final String method;
  final int? x, y, screenshotId;
  final List<int> bounds;
  bool get valid =>
      width > 0 &&
      height > 0 &&
      (method == 'touch'
          ? x != null &&
                y != null &&
                x! >= 0 &&
                x! < width &&
                y! >= 0 &&
                y! < height
          : method == 'accessibility' &&
                bounds.length == 4 &&
                bounds[2] > bounds[0] &&
                bounds[3] > bounds[1]);
  String get label => method == 'touch'
      ? '${clicks == 2 ? '实际双击' : '实际触摸'} · ($x, $y) px'
      : '无障碍控件 · 范围 (${bounds[0]}, ${bounds[1]})–(${bounds[2]}, ${bounds[3]}) px';
  factory ExecutionPointer.fromMap(Map map) => ExecutionPointer(
    width: (map['width'] as num?)?.toInt() ?? 0,
    height: (map['height'] as num?)?.toInt() ?? 0,
    method: map['method'] as String? ?? '',
    x: (map['x'] as num?)?.toInt(),
    y: (map['y'] as num?)?.toInt(),
    screenshotId: (map['screenshotId'] as num?)?.toInt(),
    clicks: (map['clicks'] as num?)?.toInt() ?? 1,
    bounds: [
      for (final n in map['bounds'] as List? ?? const [])
        if (n is num) n.toInt(),
    ],
  );
}

class ExecutionStep {
  const ExecutionStep({
    required this.step,
    required this.title,
    this.reason = '',
    this.detail = '',
    this.state = 'dim',
    this.actions = const [],
    this.screenshotId,
    this.tags = const [],
    this.resultFailed = false,
    this.timingLabel = '',
    this.pointers = const [],
    this.declaredPoint,
    this.tokens,
  });
  final int step;
  final String title, reason, detail, state;
  final List<ExecutionAction> actions;
  final int? screenshotId;
  final List<String> tags;
  final bool resultFailed;
  final String timingLabel;
  final String? declaredPoint;
  final TokenUsage? tokens;
  final List<ExecutionPointer> pointers;
  bool get failed => resultFailed || actions.any((action) => action.failed);
  factory ExecutionStep.fromMap(Map map) => ExecutionStep(
    step: (map['step'] as num?)?.toInt() ?? 0,
    title: map['title'] as String? ?? '',
    reason: map['reason'] as String? ?? '',
    detail: map['detail'] as String? ?? '',
    state: map['state'] as String? ?? 'dim',
    resultFailed: map['failed'] == true,
    timingLabel: map['timingLabel'] as String? ?? '',
    declaredPoint: map['declaredPoint'] as String?,
    tokens: map['tokens'] is Map
        ? TokenUsage.fromMap(map['tokens'] as Map)
        : null,
    pointers: [
      for (final p in map['pointers'] as List? ?? const [])
        if (p is Map) ExecutionPointer.fromMap(p),
    ].where((p) => p.valid).toList(),
    screenshotId: (map['screenshotId'] as num?)?.toInt(),
    tags: [
      for (final tag in map['tags'] as List? ?? const [])
        if (tag is Map) tag['text'] as String? ?? '',
    ],
    actions: [
      for (final action in map['actions'] as List? ?? const [])
        if (action is Map) ExecutionAction.fromMap(action),
    ],
  );
}

class RunDetails {
  const RunDetails({
    this.available = false,
    this.running = false,
    this.goal = '',
    this.outcome = '',
    this.verdict = '',
    this.duration,
    this.steps = const [],
    this.timingSummary = '',
    this.tokens,
  });
  final bool available, running;
  final String goal, outcome, verdict;
  final String timingSummary;
  final TokenUsage? tokens;
  final String? duration;
  final List<ExecutionStep> steps;
  factory RunDetails.fromMap(Map map) => RunDetails(
    available: map['available'] == true,
    running: map['running'] == true,
    goal: map['goal'] as String? ?? '',
    outcome: map['outcome'] as String? ?? '',
    verdict: map['verdict'] as String? ?? '',
    timingSummary: map['timingSummary'] as String? ?? '',
    tokens: map['tokens'] is Map
        ? TokenUsage.fromMap(map['tokens'] as Map)
        : null,
    duration: map['duration'] as String?,
    steps: [
      for (final step in map['steps'] as List? ?? const [])
        if (step is Map) ExecutionStep.fromMap(step),
    ],
  );
}

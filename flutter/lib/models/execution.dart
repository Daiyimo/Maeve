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
  });
  final int step;
  final String title, reason, detail, state;
  final List<ExecutionAction> actions;
  final int? screenshotId;
  final List<String> tags;
  final bool resultFailed;
  bool get failed => resultFailed || actions.any((action) => action.failed);
  factory ExecutionStep.fromMap(Map map) => ExecutionStep(
    step: (map['step'] as num?)?.toInt() ?? 0,
    title: map['title'] as String? ?? '',
    reason: map['reason'] as String? ?? '',
    detail: map['detail'] as String? ?? '',
    state: map['state'] as String? ?? 'dim',
    resultFailed: map['failed'] == true,
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
  });
  final bool available, running;
  final String goal, outcome, verdict;
  final String? duration;
  final List<ExecutionStep> steps;
  factory RunDetails.fromMap(Map map) => RunDetails(
    available: map['available'] == true,
    running: map['running'] == true,
    goal: map['goal'] as String? ?? '',
    outcome: map['outcome'] as String? ?? '',
    verdict: map['verdict'] as String? ?? '',
    duration: map['duration'] as String?,
    steps: [
      for (final step in map['steps'] as List? ?? const [])
        if (step is Map) ExecutionStep.fromMap(step),
    ],
  );
}

import 'execution.dart';

/// 从宿主会话快照映射的一条消息与过程摘要。
class Message {
  Message(
    this.text, {
    required this.isUser,
    this.steps = 0,
    this.thinking = const [],
    this.queued = false,
    this.failed = false,
    this.quote,
    this.quoteFromUser = false,
    this.status = 'done',
    this.executionSteps = const [],
    this.runId,
    this.detail,
    this.confirmationKey,
    this.resumable = false,
  });

  final String text;
  final String status;
  final List<ExecutionStep> executionSteps;
  final String? runId, detail;
  final int? confirmationKey;
  final bool resumable;
  bool get active => !const ['done', 'failed', 'cancelled'].contains(status);
  String get statusLabel => switch (status) {
    'done' => '已完成',
    'failed' => '任务失败',
    'cancelled' => '已停止',
    'needs_confirmation' => '等待确认',
    'needs_input' => '等待回答',
    'needs_takeover' => '等待接管',
    'paused' => '已暂停',
    'queued' => '排队中',
    'waiting_unlock' => '等待解锁',
    _ => '执行中',
  };
  final bool isUser;

  /// 这一轮思考了几步(0 = 没有过程可折叠)。
  final int steps;

  /// 思考过程摘要(只在展开时显示)。
  final List<String> thinking;

  /// 生成中插话排队的消息。
  final bool queued;

  /// 失败的重试点(桥接后由执行层给)。
  final bool failed;

  /// 引用上一条消息的摘要(引用 chip 发出后留在气泡里)。
  final String? quote;

  /// 引用的那条是不是用户自己说的。
  final bool quoteFromUser;
}

/// 会话。
class Conversation {
  Conversation({
    required this.id,
    required this.title,
    DateTime? updatedAt,
    this.pinned = false,
    this.folder,
  }) : updatedAt = updatedAt ?? DateTime.now();

  final String id;
  String title;
  DateTime updatedAt;
  bool pinned;

  /// 文件夹名。null = 不在文件夹里。
  String? folder;

  final List<Message> messages = [];

  bool get isEmpty => messages.isEmpty;
}

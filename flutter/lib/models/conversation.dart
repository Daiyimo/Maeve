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
  });

  final String text;
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

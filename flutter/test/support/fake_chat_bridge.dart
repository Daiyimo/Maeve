import 'dart:async';

import 'package:maeve_flutter/bridge/maeve_chat_bridge.dart';

/// 测试用的假对话桥:不碰平台通道,事实由测试摆,事件由测试发。
///
/// 界面代码只认 MaeveChatBridge,所以假桥驱动下的断言测的就是**真实契约**:
/// ui 事件驱动界面、发送转发宿主、停止转发宿主。
class FakeChatBridge extends MaeveChatBridge {
  FakeChatBridge({List<Map<String, dynamic>> conversations = const []})
    : _conversations = [...conversations];

  final List<Map<String, dynamic>> _conversations;
  final _events = StreamController<dynamic>.broadcast();

  /// 发出去的话(按顺序);测试据此断言"真的发了"。
  final sent = <String>[];

  /// 随发送带出去的引用(与 [sent] 一一对应)。
  final quotes = <String?>[];

  /// 宿主当前对话快照;[emitUi] 之后由 [uiOf] 拼。
  Map<String, dynamic> ui = const {
    'kind': 'ui',
    'conversationId': null,
    'composer': 'idle',
    'blocked': null,
    'exchanges': <Map<String, dynamic>>[],
  };

  int cancelCalls = 0;
  int newCalls = 0;
  final pinned = <String, bool>{};
  final deleted = <String>[];
  final opened = <String>[];

  @override
  Future<List<Map<String, dynamic>>> conversations() async =>
      List.of(_conversations);

  @override
  Future<void> openConversation(String id) async => opened.add(id);

  @override
  Future<bool> send(
    String text, {
    String? quote,
    bool quoteFromUser = false,
  }) async {
    sent.add(text);
    quotes.add(quote);
    return sendResult;
  }

  /// send 的返回值;默认真(发出去)。测拦截时设成 false。
  bool sendResult = true;

  @override
  Future<void> cancel() async => cancelCalls++;

  @override
  Future<void> newConversation() async => newCalls++;

  @override
  Future<void> setPinned(String id, bool isPinned) async =>
      pinned[id] = isPinned;

  @override
  Future<void> delete(String id) async => deleted.add(id);

  @override
  Stream<dynamic> get chatStream => _events.stream;

  /// 摆一个会话摘要(id / title / updatedAt / pinned)。
  static Map<String, dynamic> conversation(
    String id,
    String title, {
    bool pinned = false,
    DateTime? updatedAt,
  }) => {
    'id': id,
    'title': title,
    'updatedAt':
        (updatedAt ?? DateTime(2026, 11, 11, 12)).millisecondsSinceEpoch,
    'pinned': pinned,
  };

  /// 摆一轮问答(字段与宿主 MaeveChatPlugin 的映射一致)。
  static Map<String, dynamic> exchange(
    String goal, {
    String status = 'done',
    String? answer,
    String? detail,
    String line = '',
    int steps = 0,
    bool live = false,
    String? runId,
  }) => {
    'goal': goal,
    'status': status,
    'answer': answer,
    'detail': detail,
    'line': line,
    'steps': steps,
    'live': live,
    'runId': runId,
  };

  /// 发一个 ui 快照(宿主那边 _ui 变了就会推的那种)。
  void emitUi({
    String? conversationId,
    String composer = 'idle',
    List<String>? blocked,
    List<Map<String, dynamic>> exchanges = const [],
  }) {
    ui = {
      'kind': 'ui',
      'conversationId': conversationId,
      'composer': composer,
      'blocked': blocked,
      'exchanges': exchanges,
    };
    _events.add(ui);
  }

  Future<void> dispose() => _events.close();
}

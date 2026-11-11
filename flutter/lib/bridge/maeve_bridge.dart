import 'package:flutter/services.dart';

class MaeveBridge {
  static const methods = MethodChannel('maeve/ui');
  static const events = EventChannel('maeve/ui/events');
  static const entryEvents = EventChannel('maeve/ui/entry');
  static const chatMethods = MethodChannel('maeve/chat');
  static const chatEvents = EventChannel('maeve/chat/events');
  static Future<void> openSettings() => methods.invokeMethod('open_settings');
  static Stream<dynamic> get status => events.receiveBroadcastStream();

  /// 入口事件(通知点击、任务深链接):{kind: 'open_conversation', threadId}。
  /// 详见宿主侧 MaeveEntryPlugin。
  static Stream<dynamic> get entries => entryEvents.receiveBroadcastStream();

  // —— 对话桥(宿主侧 MaeveChatPlugin):会话列表与轮次都来自进程内那份真实数据 ——

  /// 会话摘要:[{id, title, updatedAt(epoch ms), pinned}]。
  static Future<List<Map<String, dynamic>>> conversations() async {
    final list = await chatMethods.invokeMethod<List<Object?>>('conversations');
    return (list ?? [])
        .map((e) => Map<String, dynamic>.from(e as Map))
        .toList();
  }

  /// 让宿主切到某个对话(轮次快照随后从 [chatStream] 到)。
  static Future<void> openConversation(String id) =>
      chatMethods.invokeMethod<void>('open', {'id': id});

  /// 发送;返回 false = 被前置条件拦下(缺模型/无障碍没开…),
  /// 具体问题码随后从 [chatStream] 的 ui 事件里带回来。
  /// [quote]/[quoteFromUser] 有值时由宿主按 QuoteMessage 格式拼进话里。
  static Future<bool> send(
    String text, {
    String? quote,
    bool quoteFromUser = false,
  }) async =>
      (await chatMethods.invokeMethod<bool>('send', {
        'text': text,
        if (quote != null && quote.isNotEmpty) 'quote': quote,
        'quoteFromUser': quoteFromUser,
      })) ??
      false;

  /// 停止当前任务(宿主 TaskService.cancel)。
  static Future<void> cancel() => chatMethods.invokeMethod<void>('cancel');

  /// 新对话:宿主切到全新状态(还没说话就没有 id)。
  static Future<void> newConversation() =>
      chatMethods.invokeMethod<void>('new');

  /// 置顶/取消置顶(宿主 ConversationStore.setPinned)。
  static Future<void> setPinned(String id, bool pinned) =>
      chatMethods.invokeMethod<void>('setPinned', {'id': id, 'pinned': pinned});

  /// 删除对话(宿主 ConversationStore.delete)。
  static Future<void> delete(String id) =>
      chatMethods.invokeMethod<void>('delete', {'id': id});

  /// 对话事件流:{kind: 'conversations', items} / {kind: 'ui', ...}。
  static Stream<dynamic> get chatStream => chatEvents.receiveBroadcastStream();
}

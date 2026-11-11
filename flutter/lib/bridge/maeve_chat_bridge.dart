import 'dart:async';

import 'maeve_bridge.dart';

/// 对话桥的接口。壳只认这个接口;真机上由 [HostChatBridge] 走方法通道接宿主,
/// 测试里换 [FakeChatBridge] 之类的实现,界面代码一行不动。
abstract class MaeveChatBridge {
  const MaeveChatBridge();

  /// 会话摘要:[{id, title, updatedAt(epoch ms), pinned}]。
  Future<List<Map<String, dynamic>>> conversations();

  /// 让宿主切到某个对话(轮次快照随后从 [chatStream] 到)。
  Future<void> openConversation(String id);

  /// 发送;false = 被前置条件拦下,问题码随后从 [chatStream] 的 ui 事件带回。
  Future<bool> send(String text, {String? quote, bool quoteFromUser = false});

  /// 停止当前任务。
  Future<void> cancel();

  /// 新对话(宿主切到全新状态,还没说话就没有 id)。
  Future<void> newConversation();

  /// 置顶 / 取消置顶。
  Future<void> setPinned(String id, bool pinned);

  /// 删除对话。
  Future<void> delete(String id);

  /// 事件流:{kind: 'conversations', items} / {kind: 'ui', ...}。
  Stream<dynamic> get chatStream;
}

/// 真机实现:全部转发给平台通道(见 MaeveBridge / 宿主 MaeveChatPlugin)。
class HostChatBridge extends MaeveChatBridge {
  const HostChatBridge();

  @override
  Future<List<Map<String, dynamic>>> conversations() =>
      MaeveBridge.conversations();

  @override
  Future<void> openConversation(String id) => MaeveBridge.openConversation(id);

  @override
  Future<bool> send(String text, {String? quote, bool quoteFromUser = false}) =>
      MaeveBridge.send(text, quote: quote, quoteFromUser: quoteFromUser);

  @override
  Future<void> cancel() => MaeveBridge.cancel();

  @override
  Future<void> newConversation() => MaeveBridge.newConversation();

  @override
  Future<void> setPinned(String id, bool pinned) =>
      MaeveBridge.setPinned(id, pinned);

  @override
  Future<void> delete(String id) => MaeveBridge.delete(id);

  @override
  Stream<dynamic> get chatStream => MaeveBridge.chatStream;
}

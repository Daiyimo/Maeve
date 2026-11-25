import 'dart:async';

import 'maeve_bridge.dart';

import 'dart:typed_data';

import '../models/execution.dart';
import '../models/task_report.dart';

part 'host_chat_bridge.dart';

enum SubmissionReceiptState { accepted, rejected, notReceived, unknown }

/// 对话桥的接口。壳只认这个接口;真机上由 [HostChatBridge] 走方法通道接宿主,
/// 测试里换 [FakeChatBridge] 之类的实现,界面代码一行不动。
abstract class MaeveChatBridge {
  const MaeveChatBridge();
  Future<Set<String>> supportedFeatures() async => const {
    'submissionReceipts',
    'draftRecovery',
    'deleteImpact',
    'awaitDeletion',
    'actionEffectReceipts',
  };

  /// 会话摘要:[{id, title, updatedAt(epoch ms), pinned}]。
  Future<List<Map<String, dynamic>>> conversations();

  /// 让宿主切到某个对话(轮次快照随后从 [chatStream] 到)。
  Future<void> openConversation(String id);

  /// 发送;false = 被前置条件拦下,问题码随后从 [chatStream] 的 ui 事件带回。
  Future<bool> send(String text, {String? quote, bool quoteFromUser = false});

  /// 停止当前任务。
  Future<bool?> submit(
    String requestId,
    String conversationId,
    String text, {
    String? quote,
    bool quoteFromUser = false,
  }) => send(text, quote: quote, quoteFromUser: quoteFromUser);
  Future<bool?> submissionReceipt(String requestId) async => null;
  Future<SubmissionReceiptState> lookupSubmission(String requestId) async =>
      switch (await submissionReceipt(requestId)) {
        true => SubmissionReceiptState.accepted,
        false => SubmissionReceiptState.rejected,
        null => SubmissionReceiptState.unknown,
      };
  Future<String?> loadDrafts() async => null;
  Future<void> saveDrafts(String state) async {}
  Future<({int revision, String? state})> loadDraftState() async =>
      (revision: 0, state: await loadDrafts());
  Future<int> saveDraftState(String state, int revision) async {
    await saveDrafts(state);
    return revision + 1;
  }

  Future<List<Map<String, dynamic>>> effectReceipts(
    String conversationId,
  ) async => [];
  Future<void> reconcileEffect(
    String conversationId,
    Map<String, dynamic> receipt,
    bool applied,
  ) async => throw UnsupportedError('effect_receipts');

  Future<void> cancel();
  Future<void> cancelConversation(String conversationId) => cancel();

  /// 新对话(宿主切到全新状态,还没说话就没有 id)。
  Future<void> newConversation();
  Future<String?> createConversation() async {
    await newConversation();
    return null;
  }

  /// 置顶 / 取消置顶。
  Future<void> setPinned(String id, bool pinned);

  /// 删除对话。
  Future<void> delete(String id);
  Future<Map<String, dynamic>> deleteImpact(String id) async => {};
  Future<Uint8List?> preview(String conversationId);
  Future<RunDetails> runDetails(String runId);

  /// 任务报告:结局、完整回答、关键页与可播放的画面序列。
  Future<TaskReport> runReport(String runId);

  /// 导出报告到用户自选位置(系统文件选择器)。返回 saved / failed / cancelled。
  Future<String> exportRunReport(
    String runId,
    ReportFormat format, {
    String redaction = 'secrets',
  });
  Future<bool> shareTaskImage(Uint8List png) async => false;
  Future<List<Map<String, dynamic>>> runs();
  Future<Uint8List?> runScreen(String runId, int screenId);
  Future<bool> taskAction(String conversationId, String action, {int? key});

  /// 事件流:{kind: 'conversations', items} / {kind: 'ui', ...}。
  Stream<dynamic> get chatStream;
}

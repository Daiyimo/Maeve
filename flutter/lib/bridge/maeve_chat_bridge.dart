import 'dart:async';

import 'maeve_bridge.dart';

import 'dart:typed_data';

import '../models/execution.dart';
import '../models/task_report.dart';

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
  Future<List<Map<String, dynamic>>> runs();
  Future<Uint8List?> runScreen(String runId, int screenId);
  Future<bool> taskAction(String conversationId, String action, {int? key});

  /// 事件流:{kind: 'conversations', items} / {kind: 'ui', ...}。
  Stream<dynamic> get chatStream;
}

/// 真机实现:全部转发给平台通道(见 MaeveBridge / 宿主 MaeveChatPlugin)。
class HostChatBridge extends MaeveChatBridge {
  const HostChatBridge();
  @override
  Future<Set<String>> supportedFeatures() async {
    final snapshot = await MaeveBridge.methods
        .invokeMapMethod<String, dynamic>('status')
        .timeout(const Duration(seconds: 5));
    if (((snapshot?['bridgeVersion'] as num?)?.toInt() ?? 0) < 2) return {};
    return {
      for (final e in (snapshot?['capabilities'] as Map? ?? const {}).entries)
        if (e.value == true) e.key as String,
    };
  }

  @override
  Future<({int revision, String? state})> loadDraftState() async {
    final value = await MaeveBridge.chatMethods
        .invokeMapMethod<String, dynamic>('loadDrafts')
        .timeout(const Duration(seconds: 5));
    return (
      revision: value!['revision'] as int,
      state: value['state'] as String?,
    );
  }

  @override
  Future<int> saveDraftState(String state, int revision) async =>
      (await MaeveBridge.chatMethods
          .invokeMethod<int>('saveDrafts', {
            'state': state,
            'revision': revision,
          })
          .timeout(const Duration(seconds: 5)))!;
  @override
  Future<List<Map<String, dynamic>>> effectReceipts(
    String conversationId,
  ) async => [
    for (final row
        in await MaeveBridge.chatMethods.invokeListMethod('effectReceipts', {
              'conversationId': conversationId,
            }) ??
            [])
      Map<String, dynamic>.from(row as Map),
  ];
  @override
  Future<void> reconcileEffect(
    String conversationId,
    Map<String, dynamic> receipt,
    bool applied,
  ) => MaeveBridge.chatMethods.invokeMethod<void>('reconcileEffect', {
    'conversationId': conversationId,
    'effectId': receipt['effectId'],
    'sequence': receipt['sequence'],
    'applied': applied,
  });
  @override
  Future<void> cancelConversation(String conversationId) => MaeveBridge
      .chatMethods
      .invokeMethod<void>('cancel', {'conversationId': conversationId});
  @override
  Future<List<Map<String, dynamic>>> runs() async => [
    for (final row
        in await MaeveBridge.chatMethods.invokeListMethod('runs') ?? const [])
      if (row is Map) Map<String, dynamic>.from(row),
  ];
  @override
  Future<Uint8List?> preview(String conversationId) => MaeveBridge.chatMethods
      .invokeMethod<Uint8List>('preview', {'conversationId': conversationId});
  @override
  Future<RunDetails> runDetails(String runId) async => RunDetails.fromMap(
    await MaeveBridge.chatMethods.invokeMapMethod('runDetails', {
          'runId': runId,
        }) ??
        const {},
  );
  @override
  Future<TaskReport> runReport(String runId) async => TaskReport.fromMap(
    await MaeveBridge.chatMethods.invokeMapMethod('runReport', {
          'runId': runId,
        }) ??
        const {},
  );
  @override
  Future<String> exportRunReport(
    String runId,
    ReportFormat format, {
    String redaction = 'secrets',
  }) async {
    final reply = await MaeveBridge.methods.invokeMapMethod('exportRunReport', {
      'runId': runId,
      'format': format.wire,
      'redaction': redaction,
    });
    return reply?['state'] as String? ?? 'failed';
  }

  @override
  Future<Uint8List?> runScreen(String runId, int screenId) =>
      MaeveBridge.chatMethods.invokeMethod<Uint8List>('runScreen', {
        'runId': runId,
        'screenId': screenId,
      });
  @override
  Future<bool> taskAction(
    String conversationId,
    String action, {
    int? key,
  }) async =>
      await MaeveBridge.chatMethods.invokeMethod<bool>('taskAction', {
        'conversationId': conversationId,
        'action': action,
        'key': ?key,
      }) ??
      false;

  @override
  Future<List<Map<String, dynamic>>> conversations() =>
      MaeveBridge.conversations();

  @override
  Future<void> openConversation(String id) => MaeveBridge.openConversation(id);

  @override
  Future<bool> send(String text, {String? quote, bool quoteFromUser = false}) =>
      MaeveBridge.send(text, quote: quote, quoteFromUser: quoteFromUser);

  @override
  Future<bool?> submit(
    String requestId,
    String conversationId,
    String text, {
    String? quote,
    bool quoteFromUser = false,
  }) async {
    final receipt = await MaeveBridge.chatMethods
        .invokeMapMethod('submit', {
          'requestId': requestId,
          'conversationId': conversationId,
          'text': text,
          'quote': quote,
          'quoteFromUser': quoteFromUser,
        })
        .timeout(const Duration(seconds: 15));
    return receipt?['accepted'] as bool?;
  }

  @override
  Future<bool?> submissionReceipt(String requestId) async {
    final receipt = await MaeveBridge.chatMethods
        .invokeMapMethod('sendReceipt', {'requestId': requestId})
        .timeout(const Duration(seconds: 5));
    return receipt?['accepted'] as bool?;
  }

  @override
  Future<void> cancel() => MaeveBridge.cancel();

  @override
  Future<void> newConversation() => MaeveBridge.newConversation();

  @override
  Future<String?> createConversation() async {
    final state = await MaeveBridge.chatMethods.invokeMapMethod('new');
    return state?['conversationId'] as String?;
  }

  @override
  Future<void> setPinned(String id, bool pinned) =>
      MaeveBridge.setPinned(id, pinned);

  @override
  Future<void> delete(String id) => MaeveBridge.delete(id);
  @override
  Future<Map<String, dynamic>> deleteImpact(String id) async =>
      Map<String, dynamic>.from(
        await MaeveBridge.chatMethods.invokeMapMethod('deleteImpact', {
              'id': id,
            }) ??
            {},
      );

  @override
  Stream<dynamic> get chatStream => MaeveBridge.chatStream;
}

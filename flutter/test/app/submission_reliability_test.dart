import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

class ReceiptBridge extends FakeChatBridge {
  ReceiptBridge()
    : super(
        conversations: [
          FakeChatBridge.conversation('a', '会话 A'),
          FakeChatBridge.conversation('b', '会话 B'),
        ],
      );
  final reply = Completer<bool?>();
  bool? receipt;
  int submissions = 0, queries = 0;
  String? stored;
  bool diskFails = false;
  bool cancelFails = false;
  Set<String> features = {
    'submissionReceipts',
    'draftRecovery',
    'awaitDeletion',
    'deleteImpact',
    'actionEffectReceipts',
  };
  @override
  Future<Set<String>> supportedFeatures() async => features;
  @override
  Future<void> cancelConversation(String conversationId) async {
    if (cancelFails) throw StateError('channel detached');
  }

  @override
  Future<String?> loadDrafts() async => stored;
  @override
  Future<void> saveDrafts(String state) async {
    if (diskFails) throw StateError('disk full');
    stored = state;
  }

  @override
  Future<bool?> submit(
    String requestId,
    String conversationId,
    String text, {
    String? quote,
    bool quoteFromUser = false,
  }) {
    submissions++;
    return reply.future;
  }

  @override
  Future<bool?> submissionReceipt(String requestId) async {
    queries++;
    return receipt;
  }
}

void main() {
  late ReceiptBridge bridge;
  late MaeveController controller;
  setUp(() async {
    bridge = ReceiptBridge();
    controller = MaeveController(
      bridge: bridge,
      settingsBridge: FakeSettingsBridge(),
    );
    controller.attach();
    await Future<void>.delayed(Duration.zero);
  });
  tearDown(() async {
    controller.dispose();
    await bridge.dispose();
  });

  test('double submit waits for the original receipt', () async {
    final first = controller.send('你好');
    await Future<void>.delayed(Duration.zero);
    expect(await controller.send('你好'), isFalse);
    expect(bridge.submissions, 1);
    bridge.reply.complete(true);
    expect(await first, isTrue);
  });

  test('lost accepted reply queries the persisted receipt', () async {
    final result = controller.send('你好');
    await Future<void>.delayed(Duration.zero);
    bridge.receipt = true;
    bridge.reply.completeError(StateError('channel detached'));
    expect(await result, isTrue);
    expect(bridge.queries, 1);
    expect(bridge.submissions, 1);
  });

  test('unknown receipt blocks resending until reconciled', () async {
    final result = controller.send('你好');
    await Future<void>.delayed(Duration.zero);
    bridge.reply.completeError(StateError('channel detached'));
    await expectLater(result, throwsStateError);
    await expectLater(controller.send('你好'), throwsStateError);
    expect(bridge.submissions, 1);
    bridge.receipt = true;
    expect(await controller.send('你好'), isTrue);
    expect(bridge.submissions, 1);
  });

  test(
    'restart preserves draft quote and queries original unknown request',
    () async {
      controller.saveDraft(controller.draftKey, '你好', (
        text: '引用',
        fromUser: true,
      ));
      final result = controller.send('你好');
      await Future<void>.delayed(Duration.zero);
      bridge.reply.completeError(StateError('process stopped'));
      await expectLater(result, throwsStateError);
      final stored = bridge.stored;
      expect(stored, contains('ui_'));
      controller.dispose();
      controller = MaeveController(
        bridge: bridge,
        settingsBridge: FakeSettingsBridge(),
      );
      controller.attach();
      await Future<void>.delayed(Duration.zero);
      expect(controller.drafts[controller.draftKey]?.quote?.text, '引用');
      await expectLater(controller.send('你好'), throwsStateError);
      expect(bridge.submissions, 1);
      bridge.receipt = true;
      expect(await controller.send('你好'), isTrue);
      expect(bridge.submissions, 1);
    },
  );

  test('failed local persistence stops before submitting to host', () async {
    bridge.diskFails = true;
    await expectLater(controller.send('你好'), throwsStateError);
    expect(bridge.submissions, 0);
    expect(controller.draftError, isNotNull);
  });

  test('draft storage corruption blocks new submissions', () async {
    controller.dispose();
    bridge.stored = '{';
    controller = MaeveController(
      bridge: bridge,
      settingsBridge: FakeSettingsBridge(),
    );
    controller.attach();
    await Future<void>.delayed(Duration.zero);
    await expectLater(controller.send('你好'), throwsStateError);
    expect(bridge.submissions, 0);
    expect(bridge.stored, '{');
  });

  test('accepted draft is removed durably before a restart', () async {
    controller.saveDraft(controller.draftKey, '你好', null);
    bridge.reply.complete(true);
    expect(await controller.send('你好'), isTrue);
    controller.dispose();
    controller = MaeveController(
      bridge: bridge,
      settingsBridge: FakeSettingsBridge(),
    );
    controller.attach();
    await Future<void>.delayed(Duration.zero);
    expect(controller.drafts, isEmpty);
  });

  test('stop stays pending until native terminal state and never fakes cancellation', () async {
    bridge.emitUi(
      conversationId: 'a',
      exchanges: [FakeChatBridge.exchange('你好', status: 'working')],
    );
    await Future<void>.delayed(Duration.zero);
    await controller.requestStop();
    expect(controller.cancelRequested, isTrue);
    expect(controller.active.messages.last.status, 'working');
    bridge.emitUi(
      conversationId: 'a',
      exchanges: [FakeChatBridge.exchange('你好', status: 'cancelled', steps: 1)],
    );
    await Future<void>.delayed(Duration.zero);
    expect(controller.cancelRequested, isFalse);
    expect(controller.active.messages.last.status, 'cancelled');
  });

  test(
    'stop channel error allows retry and shows a recovery message',
    () async {
      bridge.cancelFails = true;
      await controller.requestStop();
      expect(controller.cancelRequested, isFalse);
      expect(controller.chatError, contains('停止请求'));
    },
  );

  test('late UI does not switch back to a different conversation', () async {
    controller.select('b');
    bridge.emitUi(
      conversationId: 'a',
      exchanges: [FakeChatBridge.exchange('old')],
    );
    await Future<void>.delayed(Duration.zero);
    expect(controller.activeId, 'b');
    expect(controller.active.messages, isEmpty);
  });

  test('late UI cannot resurrect a confirmed deletion', () async {
    await controller.delete('a');
    bridge.emitUi(
      conversationId: 'a',
      exchanges: [FakeChatBridge.exchange('old')],
    );
    bridge.emitConversations([
      FakeChatBridge.conversation('a', 'stale'),
      FakeChatBridge.conversation('b', '会话 B'),
    ]);
    await Future<void>.delayed(Duration.zero);
    expect(controller.activeId, 'b');
    expect(controller.conversations.map((c) => c.id), ['b']);
  });

  test(
    'missing host capabilities never fall back to an unreceipted submission',
    () async {
      bridge.features = {};
      await controller.reconnect();
      controller.saveDraft(controller.draftKey, '保留输入', null);
      expect(await controller.send('保留输入'), isFalse);
      expect(bridge.submissions, 0);
      expect(controller.chatError, contains('不支持可靠发送'));
      expect(controller.drafts[controller.draftKey]?.text, '保留输入');
      await expectLater(controller.delete('a'), throwsUnsupportedError);
    },
  );

  test('new host epoch accepts its low revision and ignores late events from retired host', () async {
    bridge.emitUi(
      conversationId: 'a',
      epoch: 'old-host',
      revision: 100,
      exchanges: [FakeChatBridge.exchange('old')],
    );
    bridge.emitUi(
      conversationId: 'a',
      epoch: 'new-host',
      revision: 1,
      exchanges: [FakeChatBridge.exchange('fresh')],
    );
    bridge.emitUi(
      conversationId: 'a',
      epoch: 'old-host',
      revision: 101,
      exchanges: [FakeChatBridge.exchange('stale')],
    );
    await Future<void>.delayed(Duration.zero);
    expect(controller.active.messages.first.text, 'fresh');
  });

  test('reconnecting after stream error preserves unknown request and never replays it', () async {
    final first = controller.send('hello');
    await Future<void>.delayed(Duration.zero);
    bridge.reply.completeError(StateError('channel lost'));
    await expectLater(first, throwsStateError);
    bridge.emitError(StateError('stream closed'));
    await Future<void>.delayed(Duration.zero);
    expect(controller.connected, isFalse);
    await controller.reconnect();
    await Future<void>.delayed(Duration.zero);
    expect(bridge.submissions, 1);
    await expectLater(controller.send('hello'), throwsStateError);
    expect(bridge.submissions, 1);
  });
}

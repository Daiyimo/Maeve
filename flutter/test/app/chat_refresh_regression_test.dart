import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

void main() {
  test(
    'An attached new turn survives a summary that precedes persistence',
    () async {
      final bridge = FakeChatBridge();
      final controller = MaeveController(
        bridge: bridge,
        settingsBridge: FakeSettingsBridge(),
      );
      addTearDown(controller.dispose);
      addTearDown(bridge.dispose);
      controller.attach();
      await Future<void>.delayed(Duration.zero);
      bridge.emitUi(
        conversationId: 'new',
        composer: 'busy',
        exchanges: [FakeChatBridge.exchange('你好', live: true, line: '正在思考')],
      );
      await Future<void>.delayed(Duration.zero);
      bridge.emitConversations([]);
      await Future<void>.delayed(Duration.zero);
      expect(controller.activeId, 'new');
      expect(controller.active.messages.first.text, '你好');
    },
  );

  test(
    'Starting a fresh conversation leaves the previous history cached',
    () async {
      final bridge = FakeChatBridge(
        conversations: [FakeChatBridge.conversation('c1', '问候')],
      );
      final controller = MaeveController(
        bridge: bridge,
        settingsBridge: FakeSettingsBridge(),
      );
      addTearDown(controller.dispose);
      addTearDown(bridge.dispose);
      controller.attach();
      await Future<void>.delayed(Duration.zero);
      bridge.emitUi(
        conversationId: 'c1',
        exchanges: [FakeChatBridge.exchange('你好', answer: '你好！')],
      );
      await Future<void>.delayed(Duration.zero);
      bridge.emitUi();
      await Future<void>.delayed(Duration.zero);
      expect(controller.activeId, '');
      expect(controller.conversations.single.messages, hasLength(2));
      expect(controller.conversations.single.messages.last.text, '你好！');
    },
  );
  test(
    'A completed native live exchange stops the execution indicator',
    () async {
      final bridge = FakeChatBridge(
        conversations: [FakeChatBridge.conversation('c1', '问候')],
      );
      final controller = MaeveController(
        bridge: bridge,
        settingsBridge: FakeSettingsBridge(),
      );
      addTearDown(controller.dispose);
      addTearDown(bridge.dispose);
      controller.attach();
      await Future<void>.delayed(Duration.zero);
      bridge.emitUi(
        conversationId: 'c1',
        composer: 'busy',
        exchanges: [
          FakeChatBridge.exchange(
            '你好',
            status: 'working',
            live: true,
            line: '正在思考',
          ),
        ],
      );
      await Future<void>.delayed(Duration.zero);
      expect(controller.generating, isTrue);
      // Native Exchange.live tracks the attached turn, and stays true at Succeeded.
      bridge.emitUi(
        conversationId: 'c1',
        composer: 'idle',
        exchanges: [
          FakeChatBridge.exchange(
            '你好',
            status: 'done',
            live: true,
            answer: '你好！',
          ),
        ],
      );
      await Future<void>.delayed(Duration.zero);
      expect(controller.generating, isFalse);
      expect(controller.active.messages.last.text, '你好！');
    },
  );
  for (final running in [true, false]) {
    test(
      'Summary events preserve ${running ? 'live' : 'finished'} exchanges',
      () async {
        final bridge = FakeChatBridge(
          conversations: [FakeChatBridge.conversation('c1', '问候')],
        );
        final controller = MaeveController(
          bridge: bridge,
          settingsBridge: FakeSettingsBridge(),
        );
        addTearDown(controller.dispose);
        addTearDown(bridge.dispose);
        controller.attach();
        await Future<void>.delayed(Duration.zero);
        bridge.emitUi(
          conversationId: 'c1',
          composer: running ? 'busy' : 'idle',
          exchanges: [
            FakeChatBridge.exchange(
              '你好',
              answer: running ? '' : '你好，有什么可以帮你？',
              line: running ? '正在思考' : '',
              status: running ? 'working' : 'done',
              live: running,
            ),
          ],
        );
        await Future<void>.delayed(Duration.zero);
        expect(controller.active.messages, hasLength(2));
        bridge.emitConversations([
          FakeChatBridge.conversation('c1', '新的标题', pinned: true),
        ]);
        await Future<void>.delayed(Duration.zero);
        expect(controller.active.messages, hasLength(2));
        expect(controller.active.messages.first.text, '你好');
        expect(controller.active.title, '新的标题');
        expect(controller.active.pinned, isTrue);
        expect(controller.generating, running);
      },
    );
  }

  test(
    'A blank native draft does not suppress an API failure reason',
    () async {
      final bridge = FakeChatBridge(
        conversations: [FakeChatBridge.conversation('c1', '问候')],
      );
      final controller = MaeveController(
        bridge: bridge,
        settingsBridge: FakeSettingsBridge(),
      );
      addTearDown(controller.dispose);
      addTearDown(bridge.dispose);
      controller.attach();
      await Future<void>.delayed(Duration.zero);
      bridge.emitUi(
        conversationId: 'c1',
        exchanges: [
          FakeChatBridge.exchange(
            '你好',
            status: 'failed',
            answer: '',
            detail: '模型接口请求失败：HTTP 401',
          ),
        ],
      );
      await Future<void>.delayed(Duration.zero);
      expect(controller.active.messages.last.text, '模型接口请求失败：HTTP 401');
      expect(controller.active.messages.last.failed, isTrue);
    },
  );
}

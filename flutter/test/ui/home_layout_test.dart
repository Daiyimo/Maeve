import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

void main() {
  for (final size in [
    const Size(320, 568),
    const Size(369, 822),
    const Size(412, 915),
  ]) {
    testWidgets(
      'Idle home fits $size without scrolling and reserves breathing room',
      (tester) async {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1;
        tester.view.padding = const FakeViewPadding(top: 28, bottom: 24);
        final chat = FakeChatBridge();
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        final channel = MethodChannel(MaeveBridge.entryEvents.name);
        messenger.setMockMethodCallHandler(channel, (_) async => null);
        addTearDown(() async {
          await tester.pumpWidget(const SizedBox.shrink());
          await chat.dispose();
          tester.view.reset();
          messenger.setMockMethodCallHandler(channel, null);
        });
        await tester.pumpWidget(
          MaeveApp(
            controller: MaeveController(
              bridge: chat,
              settingsBridge: FakeSettingsBridge(),
            ),
          ),
        );
        await tester.pumpAndSettle();
        final scroll = find.descendant(
          of: find.byKey(const ValueKey('chat-list')),
          matching: find.byType(Scrollable),
        );
        expect(
          tester.state<ScrollableState>(scroll.first).position.maxScrollExtent,
          closeTo(0, 1),
        );
        expect(
          find.byKey(const ValueKey('home-prompt-2')).hitTestable(),
          findsOneWidget,
        );
        if (size.height > 700) {
          final middle =
              tester.getRect(find.text('试着交给 Maeve')).top -
              tester.getRect(find.text('你继续生活，我在幕后行动。')).bottom;
          expect(middle, greaterThan(48));
        }
        expect(tester.takeException(), isNull);
      },
    );
  }
}

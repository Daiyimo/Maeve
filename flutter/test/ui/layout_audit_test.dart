import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';

import '../support/fake_chat_bridge.dart';
import '../support/visual_scenarios.dart';

void main() {
  for (final (size, scale, mode) in [
    (const Size(320, 568), 1.0, ThemeMode.dark),
    (const Size(412, 915), 1.6, ThemeMode.light),
    (const Size(768, 1024), 1.0, ThemeMode.dark),
  ]) {
    testWidgets('Every Flutter page fits $size, text $scale, $mode', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      tester.view.padding = const FakeViewPadding(top: 28, bottom: 24);
      tester.platformDispatcher.textScaleFactorTestValue = scale;
      final bridge = FakeChatBridge();
      final controller = MaeveController(bridge: bridge)..mode = mode;
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      final entry = MethodChannel(MaeveBridge.entryEvents.name);
      messenger.setMockMethodCallHandler(entry, (_) async => null);
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox.shrink());
        await bridge.dispose();
        tester.view.reset();
        tester.platformDispatcher.clearTextScaleFactorTestValue();
        messenger.setMockMethodCallHandler(entry, null);
      });
      await tester.pumpWidget(MaeveApp(controller: controller));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull, reason: 'home');
      for (final page in visualPages.entries) {
        final context = tester.element(
          find.byKey(const ValueKey('message-input')),
        );
        unawaited(AppPage.open(context, page.value));
        await tester.pump();
        // Route + glass entrance are finite; live indicators may keep ticking.
        await tester.pump(const Duration(milliseconds: 600));
        expect(tester.takeException(), isNull, reason: '${page.key} at top');
        final scrollables = find.descendant(
          of: find.byType(page.value.runtimeType),
          matching: find.byType(Scrollable),
        );
        if (scrollables.evaluate().isNotEmpty) {
          final position = tester
              .state<ScrollableState>(scrollables.first)
              .position;
          position.jumpTo(position.maxScrollExtent);
          await tester.pump(const Duration(milliseconds: 300));
          expect(
            tester.takeException(),
            isNull,
            reason: '${page.key} at bottom',
          );
        }
        tester.state<NavigatorState>(find.byType(Navigator).first).pop();
        await tester.pumpAndSettle();
      }
    });
  }
}

import 'dart:convert';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/ui/common/haptics.dart';

import '../test/support/fake_chat_bridge.dart';
import '../test/support/fake_settings_bridge.dart';

/// Runs on an Android emulator only. Each test owns one surface conversion;
/// integration_test reverts the Flutter image automatically at test teardown.
Future<(FakeChatBridge, MaeveController)> _pumpApp(WidgetTester tester) async {
  expect(ui.ImageFilter.isShaderFilterSupported, isTrue);
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  tester.view.padding = const FakeViewPadding(top: 28, bottom: 24);
  addTearDown(tester.view.reset);
  MaeveHaptics.offline = true;

  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final entryChannel = MethodChannel(MaeveBridge.entryEvents.name);
  messenger.setMockMethodCallHandler(entryChannel, (_) async => null);
  addTearDown(() => messenger.setMockMethodCallHandler(entryChannel, null));

  final chat = FakeChatBridge(
    conversations: [
      FakeChatBridge.conversation('c1', '液态玻璃对比', pinned: true),
      FakeChatBridge.conversation('c2', '把复杂的事说清楚'),
      FakeChatBridge.conversation('c3', '今天的安排'),
    ],
  );
  addTearDown(chat.dispose);
  final controller = MaeveController(
    bridge: chat,
    settingsBridge: FakeSettingsBridge(),
  )..mode = ThemeMode.light;

  await tester.pumpWidget(MaeveApp(controller: controller));
  await tester.pumpAndSettle();
  chat.emitUi(
    conversationId: 'c1',
    exchanges: [
      FakeChatBridge.exchange(
        '帮我把这套玻璃做得更有折射感',
        answer: '先把背景采样救回来，再让边缘把光线弯起来。',
        steps: 3,
      ),
      FakeChatBridge.exchange(
        '输入栏也要保留真实背景',
        answer: '渐隐层不能盖在玻璃前面，否则再强的着色器也救不回细节。',
        steps: 2,
      ),
      FakeChatBridge.exchange(
        '最后检查抽屉和两端参数',
        answer: '抽屉使用同一份 sheet 光学契约，Compose 与 Flutter 逐项对齐。',
        steps: 4,
      ),
    ],
  );
  await tester.pumpAndSettle();
  return (chat, controller);
}

Future<void> _capture(
  IntegrationTestWidgetsFlutterBinding binding,
  WidgetTester tester,
  String name,
) async {
  await tester.pump(const Duration(milliseconds: 400));
  await binding.convertFlutterSurfaceToImage();
  await tester.pump();
  final bytes = await binding.takeScreenshot(name);
  // ignore: avoid_print
  print('GLASS_SCREENSHOT:$name:${base64Encode(bytes)}');
}

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('capture Flutter chat chrome', (tester) async {
    await _pumpApp(tester);
    await _capture(binding, tester, 'flutter-chat');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture Flutter refractive drawer', (tester) async {
    await _pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await _capture(binding, tester, 'flutter-drawer');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture Flutter refractive sheet', (tester) async {
    await _pumpApp(tester);
    await tester.longPress(
      find.text('抽屉使用同一份 sheet 光学契约，Compose 与 Flutter 逐项对齐。'),
    );
    await tester.pumpAndSettle();
    await _capture(binding, tester, 'flutter-sheet');
    expect(tester.takeException(), isNull);
  });
}

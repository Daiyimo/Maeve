import 'dart:convert';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/common/haptics.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';

import '../test/support/fake_chat_bridge.dart';
import '../test/support/fake_settings_bridge.dart';

const _variant = String.fromEnvironment(
  'MAEVE_VARIANT',
  defaultValue: 'variant',
);

Future<(FakeChatBridge, MaeveController)> _pumpApp(WidgetTester tester) async {
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
  await tester.pump(const Duration(milliseconds: 350));
  await binding.convertFlutterSurfaceToImage();
  await tester.pump();
  final bytes = await binding.takeScreenshot('$_variant-$name');
  // ignore: avoid_print
  print('GLASS_SCREENSHOT:$_variant-$name:${base64Encode(bytes)}');
}

Future<void> _openSettings(WidgetTester tester) async {
  await tester.tap(find.byTooltip('打开对话列表'));
  await tester.pumpAndSettle();
  await tester.tap(find.byIcon(Icons.settings_outlined));
  await tester.pumpAndSettle();
  expect(find.byType(SettingsHubPage), findsOneWidget);
}

Future<void> _scrollTo(WidgetTester tester, Finder target) async {
  final scrollable = find
      .descendant(
        of: find.byType(SettingsHubPage),
        matching: find.byType(Scrollable),
      )
      .first;
  await tester.scrollUntilVisible(target, 280, scrollable: scrollable);
  await tester.pumpAndSettle();
  for (var i = 0; i < 8; i++) {
    final rect = tester.getRect(target);
    final header = find.descendant(
      of: find.byType(SettingsHubPage),
      matching: find.byType(TopBar),
    );
    final top = tester.getRect(header).bottom + 12;
    if (rect.top < top) {
      await tester.drag(scrollable, const Offset(0, 60));
    } else if (rect.bottom > tester.view.physicalSize.height - 80) {
      await tester.drag(scrollable, const Offset(0, -60));
    } else {
      break;
    }
    await tester.pumpAndSettle();
  }
  expect(target.hitTestable(), findsOneWidget);
}

Future<void> _openSettingsRow(WidgetTester tester, String label) async {
  await _openSettings(tester);
  final row = find.text(label);
  await _scrollTo(tester, row);
  await tester.tap(row);
  await tester.pumpAndSettle();
}

void main() {
  final binding = IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('capture light theme chat', (tester) async {
    expect(ui.ImageFilter.isShaderFilterSupported, isTrue);
    await _pumpApp(tester);
    await _capture(binding, tester, 'chat');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme drawer', (tester) async {
    await _pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await _capture(binding, tester, 'drawer');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme message sheet', (tester) async {
    await _pumpApp(tester);
    await tester.longPress(
      find.text('抽屉使用同一份 sheet 光学契约，Compose 与 Flutter 逐项对齐。'),
    );
    await tester.pumpAndSettle();
    await _capture(binding, tester, 'sheet');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme settings', (tester) async {
    await _pumpApp(tester);
    await _openSettings(tester);
    await _capture(binding, tester, 'settings');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme glass density', (tester) async {
    await _pumpApp(tester);
    await _openSettings(tester);
    await _scrollTo(tester, find.text('液态玻璃'));
    await _capture(binding, tester, 'settings-glass');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme appearance', (tester) async {
    await _pumpApp(tester);
    await _openSettingsRow(tester, '主题与配色');
    await _capture(binding, tester, 'appearance');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme model settings', (tester) async {
    await _pumpApp(tester);
    await _openSettingsRow(tester, '模型配置');
    await _capture(binding, tester, 'model');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme about', (tester) async {
    await _pumpApp(tester);
    await _openSettingsRow(tester, '关于 Maeve');
    await _capture(binding, tester, 'about');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme run history', (tester) async {
    await _pumpApp(tester);
    await _openSettingsRow(tester, '执行记录');
    await _capture(binding, tester, 'history');
    expect(tester.takeException(), isNull);
  });

  testWidgets('capture light theme welcome', (tester) async {
    await _pumpApp(tester);
    await _openSettingsRow(tester, '新手引导');
    await _capture(binding, tester, 'welcome');
    expect(tester.takeException(), isNull);
  });
}

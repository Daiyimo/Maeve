import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';
import 'package:maeve_flutter/ui/settings/model_settings_page.dart';
import 'package:maeve_flutter/ui/settings/system_access_page.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

void main() {
  Future<MaeveController> mount(
    WidgetTester tester,
    FakeSettingsBridge settings,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    final chat = FakeChatBridge();
    final controller = MaeveController(bridge: chat, settingsBridge: settings);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final entry = MethodChannel(MaeveBridge.entryEvents.name);
    messenger.setMockMethodCallHandler(entry, (_) async => null);
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      await chat.dispose();
      tester.view.reset();
      messenger.setMockMethodCallHandler(entry, null);
    });
    await tester.pumpWidget(MaeveApp(controller: controller));
    await tester.pumpAndSettle();
    return controller;
  }

  Future<void> open(WidgetTester tester, Widget page) async {
    AppPage.open(
      tester.element(find.byKey(const ValueKey('message-input'))),
      page,
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'Settings retain native configuration and real preferences, without account or billing stubs',
    (tester) async {
      await mount(tester, FakeSettingsBridge());
      await open(tester, const SettingsHubPage());
      expect(find.text('模型配置'), findsOneWidget);
      expect(find.text('test-model'), findsOneWidget);
      for (final label in [
        '本机',
        '余额',
        '账单',
        '登录以同步',
        '人格与提示词',
        '插件',
        '远程主机',
        '引擎设置',
      ]) {
        expect(find.text(label), findsNothing);
      }
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'Adding a model saves the supplied configuration through the native bridge',
    (tester) async {
      final settings = FakeSettingsBridge(configured: false);
      final controller = await mount(tester, settings);
      await open(tester, const ModelSettingsPage());
      await tester.tap(find.text('添加模型接入'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('model-base-url')),
        'https://example.test/v1',
      );
      await tester.enterText(
        find.byKey(const ValueKey('model-name')),
        'actual-model-id',
      );
      await tester.enterText(
        find.byKey(const ValueKey('model-api-key')),
        'fixture-key',
      );
      final field = tester.widget<EditableText>(
        find.descendant(
          of: find.byKey(const ValueKey('model-api-key')),
          matching: find.byType(EditableText),
        ),
      );
      expect(field.obscureText, isTrue);
      await tester.ensureVisible(find.text('保存并使用'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('保存并使用'));
      await tester.pumpAndSettle();
      expect(settings.savedModels.single['model'], 'actual-model-id');
      expect(settings.savedModels.single['apiKey'], 'fixture-key');
      expect(controller.model, 'actual-model-id');
      expect(find.text('actual-model-id'), findsWidgets);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'System access displays native facts and opens the native authorization target',
    (tester) async {
      final settings = FakeSettingsBridge();
      await mount(tester, settings);
      await open(tester, const SystemAccessPage());
      expect(find.text('已开启'), findsOneWidget);
      expect(find.text('未允许'), findsOneWidget);
      await tester.tap(find.text('无障碍服务'));
      await tester.pumpAndSettle();
      expect(settings.targets, ['accessibility']);
      expect(find.text('已开启'), findsOneWidget);
      expect(find.textContaining('测试通过'), findsNothing);
    },
  );
  testWidgets('Appearance choices survive a controller restart', (
    tester,
  ) async {
    final settings = FakeSettingsBridge();
    final controller = await mount(tester, settings);
    controller.setAccent(MaeveAccentPreset.dolores);
    controller.setMode(ThemeMode.light);
    controller.toggleReduceTransparency();
    await tester.pumpAndSettle();
    final fresh = MaeveController(
      settingsBridge: settings,
      bridge: FakeChatBridge(),
    );
    addTearDown(fresh.dispose);
    await fresh.refreshSettings();
    expect(fresh.accentPreset, MaeveAccentPreset.dolores);
    expect(fresh.mode, ThemeMode.light);
    expect(fresh.reduceTransparency, isTrue);
  });
}

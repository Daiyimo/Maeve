import 'dart:async';

import 'package:flutter/material.dart';
import 'package:maeve_flutter/ui/common/selection_field.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/bridge/maeve_settings_bridge.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/settings/model_settings_page.dart';
import 'package:maeve_flutter/ui/settings/about_pages.dart';
import 'package:maeve_flutter/ui/settings/welcome_page.dart';
import 'package:maeve_flutter/ui/settings/settings_components.dart';
import 'package:maeve_flutter/models/token_usage.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

class DelayedCatalogBridge extends FakeSettingsBridge {
  final response = Completer<Map<String, dynamic>>();
  @override
  Future<Map<String, dynamic>> listModels(Map<String, String> values) =>
      response.future;
}

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

  Future<void> enter(WidgetTester tester, String key, String value) async {
    final field = find.byKey(ValueKey(key));
    await tester.ensureVisible(field);
    await tester.enterText(field, value);
    await tester.pumpAndSettle();
  }

  testWidgets(
    'Fresh defaults are light reverie and welcome completion is saved',
    (tester) async {
      expect(const NativeSettings().mode, 'light');
      expect(NativeSettings.fromMap({}).accent, 'reverie');
      final settings = FakeSettingsBridge(configured: false)
        ..values['welcomeSeen'] = false;
      final controller = await mount(tester, settings);
      expect(controller.mode, ThemeMode.light);
      expect(find.byType(WelcomePage), findsOneWidget);
      final start = find.text('开始使用');
      await tester.ensureVisible(start);
      await tester.tap(start);
      await tester.pumpAndSettle();
      expect(settings.values['welcomeSeen'], true);
      expect(find.byKey(const ValueKey('message-input')), findsOneWidget);
    },
  );

  testWidgets('About stays focused and the data notice lives in the guide', (
    tester,
  ) async {
    await mount(tester, FakeSettingsBridge());
    await open(tester, const AboutPage());
    expect(find.text('许可与致谢'), findsNothing);
    expect(find.text('开源许可'), findsNothing);
    expect(find.textContaining('对话与配置保存在设备上'), findsNothing);
    Navigator.of(tester.element(find.byType(AboutPage))).pop();
    await tester.pumpAndSettle();
    await open(tester, const WelcomePage());
    expect(find.textContaining('对话与配置保存在设备上'), findsOneWidget);
  });

  testWidgets(
    'Catalogue selection saves image capability and real reasoning effort',
    (tester) async {
      final settings = FakeSettingsBridge()
        ..listedModels = {
          'models': [
            {
              'id': 'step-3.7-flash',
              'name': 'Step 3.7 Flash',
              'vision': true,
              'reasoningLevels': ['low', 'medium', 'high'],
            },
          ],
        };
      await mount(tester, settings);
      await open(tester, const AddModelPage());
      final fetch = find.byKey(const ValueKey('fetch-models'));
      expect(tester.widget<OutlinedButton>(fetch).onPressed, isNull);
      await enter(tester, 'model-api-key', 'fixture-key');
      await tester.ensureVisible(fetch);
      await tester.tap(fetch);
      await tester.pumpAndSettle();
      expect(
        settings.listRequests.single['baseUrl'],
        'https://api.stepfun.com/v1',
      );
      final listed = find.byWidgetPredicate(
        (w) =>
            w is MaeveSelectField<String> &&
            w.decoration.labelText == '选择获取的模型',
      );
      await tester.ensureVisible(listed);
      await tester.tap(listed);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Step 3.7 Flash').last);
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<SettingsSwitchTile>(
              find.byKey(const ValueKey('model-vision')),
            )
            .value,
        true,
      );
      final effort = find.byWidgetPredicate(
        (w) =>
            w is MaeveSelectField<String> && w.decoration.labelText == '思考强度',
      );
      await tester.ensureVisible(effort);
      await tester.tap(effort);
      await tester.pumpAndSettle();
      await tester.tap(find.text('深入 · 复杂规划').last);
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('保存并使用'));
      await tester.tap(find.text('保存并使用'));
      await tester.pumpAndSettle();
      expect(settings.savedModels.single['model'], 'step-3.7-flash');
      expect(settings.savedModels.single['vision'], 'true');
      expect(settings.savedModels.single['reasoningEffort'], 'high');
    },
  );

  testWidgets('A stale model response cannot overwrite a changed key', (
    tester,
  ) async {
    final settings = DelayedCatalogBridge();
    await mount(tester, settings);
    await open(tester, const AddModelPage());
    await enter(tester, 'model-api-key', 'old-fixture-key');
    final fetch = find.byKey(const ValueKey('fetch-models'));
    await tester.ensureVisible(fetch);
    await tester.tap(fetch);
    await tester.pumpAndSettle();
    await enter(tester, 'model-api-key', 'new-fixture-key');
    settings.response.complete({
      'models': [
        {'id': 'old-result', 'name': 'Old result'},
      ],
    });
    await tester.pumpAndSettle();
    expect(find.text('选择获取的模型'), findsNothing);
    expect(settings.savedModels, isEmpty);
  });

  testWidgets('An unavailable catalogue still allows a manual model', (
    tester,
  ) async {
    final settings = FakeSettingsBridge()
      ..listedModels = {'models': [], 'error': '这个服务不提供模型列表'};
    await mount(tester, settings);
    await open(tester, const AddModelPage());
    await enter(tester, 'model-api-key', 'fixture-key');
    final fetch = find.byKey(const ValueKey('fetch-models'));
    await tester.ensureVisible(fetch);
    await tester.tap(fetch);
    await tester.pumpAndSettle();
    await enter(tester, 'model-name', 'step-3.5-flash');
    await tester.ensureVisible(find.text('保存并使用'));
    await tester.tap(find.text('保存并使用'));
    await tester.pumpAndSettle();
    expect(settings.savedModels.single['model'], 'step-3.5-flash');
    expect(settings.savedModels.single['vision'], 'false');
    expect(settings.savedModels.single['reasoningEffort'], 'default');
  });

  testWidgets(
    'Token totals exclude cached duplication and unknown counts remain explicit',
    (tester) async {
      final settings = FakeSettingsBridge()
        ..usage = {
          'totals': {'input': 100, 'output': 20, 'cached': 90},
          'models': [
            {
              'model': 'test-model',
              'provider': 'Step',
              'totals': {'input': 100, 'output': 20, 'cached': 90},
            },
          ],
        };
      await mount(tester, settings);
      await open(tester, const ModelSettingsPage());
      expect(find.text('120 tokens'), findsOneWidget);
      expect(find.textContaining('缓存命中 90'), findsOneWidget);
      expect(TokenUsage.fromMap({'calls': 2, 'unreported': 2}).reported, false);
    },
  );

  testWidgets(
    'An obsolete saved effort remains editable instead of breaking the form',
    (tester) async {
      await mount(tester, FakeSettingsBridge());
      await open(
        tester,
        const AddModelPage(
          config: NativeModelConfig(
            id: 'old',
            name: 'Step',
            model: 'old-model',
            kind: 'OpenAiCompat',
            baseUrl: 'https://api.stepfun.com/v1',
            reasoningEffort: 'high',
            reasoningLevels: [],
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      final effort = find.byWidgetPredicate(
        (w) =>
            w is MaeveSelectField<String> && w.decoration.labelText == '思考强度',
      );
      expect(
        tester.widget<MaeveSelectField<String>>(effort).initialValue,
        'default',
      );
    },
  );
}

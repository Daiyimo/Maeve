import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/bridge/maeve_settings_bridge.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';
import 'package:maeve_flutter/ui/settings/model_settings_page.dart';
import 'package:maeve_flutter/ui/settings/system_access_page.dart';
import 'package:maeve_flutter/ui/settings/amoo_page.dart';
import 'package:maeve_flutter/ui/settings/amoo_wechat_page.dart';
import 'package:maeve_flutter/ui/settings/settings_components.dart';

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

  testWidgets('Settings first group sits close below the navigation bar', (
    tester,
  ) async {
    await mount(tester, FakeSettingsBridge());
    await open(tester, const SettingsHubPage());
    final headerBottom = tester.getBottomLeft(find.byType(TopBar)).dy;
    final groupTop = tester.getTopLeft(find.text('执行与设备')).dy;
    expect(groupTop - headerBottom, inInclusiveRange(12, 28));
  });

  testWidgets(
    'WeChat lives under Amoo and execution can be disabled without stopping chat',
    (tester) async {
      final settings = FakeSettingsBridge();
      settings.wechatStatus.addAll({
        'bound': true,
        'enabled': true,
        'executionEnabled': true,
        'state': 'connected',
        'amooReady': true,
      });
      await mount(tester, settings);
      await open(tester, const AmooPage());
      await tester.ensureVisible(find.text('微信连接'));
      await tester.tap(find.text('微信连接'));
      await tester.pumpAndSettle();
      expect(find.byType(AmooWechatPage), findsOneWidget);
      expect(find.text('微信已连接'), findsOneWidget);
      await tester.ensureVisible(find.text('通过 Maeve 执行任务'));
      await tester.tap(find.text('通过 Maeve 执行任务'));
      await tester.pumpAndSettle();
      expect(settings.wechatStatus['executionEnabled'], isFalse);
      expect(settings.wechatStatus['enabled'], isTrue);
      expect(settings.wechatActions, contains('setExecution'));
    },
  );

  testWidgets(
    'WeChat background entry requires helper and preserves receiving',
    (tester) async {
      final settings = FakeSettingsBridge();
      settings.wechatStatus.addAll({
        'bound': true,
        'enabled': true,
        'backgroundReady': true,
      });
      await mount(tester, settings);
      await open(tester, const AmooWechatPage());
      expect(find.text('系统 Amoo · 前台'), findsOneWidget);
      await tester.ensureVisible(find.text('后台接入 Amoo'));
      await tester.tap(find.text('后台接入 Amoo'));
      await tester.pumpAndSettle();
      expect(settings.wechatStatus['background'], isTrue);
      expect(settings.wechatStatus['enabled'], isTrue);
      expect(find.text('系统 Amoo · 后台'), findsOneWidget);
      expect(settings.wechatActions, contains('setBackground'));
    },
  );

  testWidgets('WeChat cannot enable background entry without helper', (
    tester,
  ) async {
    final settings = FakeSettingsBridge();
    await mount(tester, settings);
    await open(tester, const AmooWechatPage());
    await tester.ensureVisible(find.text('后台接入 Amoo'));
    await tester.tap(find.text('后台接入 Amoo'));
    await tester.pumpAndSettle();
    expect(settings.wechatActions, isNot(contains('setBackground')));
  });

  testWidgets('WeChat read errors recover and login can be cancelled', (
    tester,
  ) async {
    final settings = FakeSettingsBridge();
    await mount(tester, settings);
    settings.fail = true;
    await open(tester, const AmooWechatPage());
    expect(find.text('微信连接更新失败，请重试'), findsOneWidget);
    settings.fail = false;
    await tester.tap(find.byTooltip('刷新微信状态'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('获取绑定二维码'));
    await tester.tap(find.text('获取绑定二维码'));
    await tester.pumpAndSettle();
    expect(find.text('取消扫码'), findsOneWidget);
    await tester.tap(find.text('取消扫码'));
    await tester.pumpAndSettle();
    expect(
      settings.wechatActions,
      containsAllInOrder(['login', 'cancelLogin']),
    );
  });

  test(
    'WeChat host bridge preserves execution and receiving switches',
    () async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(HostSettingsBridge.channel, (
        call,
      ) async {
        expect(call.method, 'wechat');
        expect(call.arguments, {'action': 'setExecution', 'enabled': false});
        return {'executionEnabled': false, 'enabled': true};
      });
      addTearDown(
        () => messenger.setMockMethodCallHandler(
          HostSettingsBridge.channel,
          null,
        ),
      );
      final status = await const HostSettingsBridge().wechat(
        'setExecution',
        enabled: false,
      );
      expect(status['enabled'], isTrue);
      expect(status['executionEnabled'], isFalse);
    },
  );

  testWidgets(
    'Amoo is reachable from settings and uses native start and cancel',
    (tester) async {
      final settings = FakeSettingsBridge();
      await mount(tester, settings);
      await open(tester, const SettingsHubPage());
      await tester.tap(find.text('Amoo'));
      await tester.pumpAndSettle();
      expect(find.byType(AmooPage), findsOneWidget);
      expect(settings.amooActions, ['snapshot']);
      await tester.tap(find.text('开始接入'));
      await tester.pumpAndSettle();
      expect(find.text('等待 Amoo 聊天页，请现在切换过去'), findsOneWidget);
      expect(settings.amooActions.last, 'start');
      await tester.tap(find.text('取消接入'));
      await tester.pumpAndSettle();
      expect(settings.amooActions.last, 'cancel');
      expect(find.text('已开启'), findsOneWidget);
      expect(find.text('接入验证已完成'), findsNothing);
    },
  );

  testWidgets('Amoo refreshes native progress and recovers from a read error', (
    tester,
  ) async {
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    final settings = FakeSettingsBridge();
    await mount(tester, settings);
    settings.fail = true;
    await open(tester, const AmooPage());
    expect(find.text('未能读取 Amoo 状态，请重试。'), findsOneWidget);
    expect(settings.amooActions, ['snapshot']);
    settings.fail = false;
    settings.amooStatus = const NativeAmooStatus(
      stage: 'verifying',
      installed: true,
      accessibility: true,
      bridgeEnabled: true,
    );
    await tester.tap(find.byTooltip('刷新 Amoo 状态'));
    await tester.pumpAndSettle();
    expect(find.text('已发送，等待 Amoo 完成验证'), findsOneWidget);
    settings.amooStatus = const NativeAmooStatus(
      stage: 'done',
      installed: true,
      accessibility: true,
      bridgeEnabled: true,
    );
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    expect(find.text('接入验证已完成'), findsOneWidget);
    expect(settings.amooActions, everyElement('snapshot'));
  });

  testWidgets('Amoo cannot start without its app and live accessibility', (
    tester,
  ) async {
    final settings = FakeSettingsBridge()
      ..amooStatus = const NativeAmooStatus();
    await mount(tester, settings);
    await open(tester, const AmooPage());
    expect(find.text('未检测到 Amoo'), findsOneWidget);
    expect(find.text('开启无障碍服务'), findsOneWidget);
    await tester.tap(find.text('开始接入'), warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(settings.amooActions, ['snapshot']);
    await tester.tap(find.text('开启无障碍服务'));
    await tester.pumpAndSettle();
    expect(settings.targets, ['accessibility']);
  });

  test(
    'Amoo platform bridge decodes native state and preserves the action',
    () async {
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(HostSettingsBridge.channel, (
        call,
      ) async {
        expect(call.method, 'amoo');
        expect(call.arguments, {'action': 'snapshot'});
        return {
          'stage': 'waiting',
          'installed': true,
          'accessibility': false,
          'bridgeEnabled': true,
          'lastResultAt': 123000,
        };
      });
      addTearDown(
        () => messenger.setMockMethodCallHandler(
          HostSettingsBridge.channel,
          null,
        ),
      );
      final status = await const HostSettingsBridge().amoo('snapshot');
      expect(status.active, isTrue);
      expect(status.bridgeEnabled, isTrue);
      expect(status.accessibility, isFalse);
      expect(status.lastResultAt!.millisecondsSinceEpoch, 123000);
    },
  );

  testWidgets(
    'One Step preset switches API and Plan without discarding its key',
    (tester) async {
      final settings = FakeSettingsBridge(configured: false);
      await mount(tester, settings);
      await open(tester, const AddModelPage());
      expect(find.widgetWithText(SettingsRow, '阶跃星辰'), findsOneWidget);
      expect(find.text('DeepSeek'), findsNothing);
      expect(find.text('OpenAI Responses'), findsNothing);
      final field = find.byKey(const ValueKey('model-base-url'));
      expect(
        tester.widget<TextFormField>(field).controller!.text,
        'https://api.stepfun.com/v1',
      );
      final key = find.byKey(const ValueKey('model-api-key'));
      await tester.ensureVisible(key);
      await tester.enterText(key, 'fixture-step-key');
      await tester.pumpAndSettle();
      tester.testTextInput.hide();
      await tester.pumpAndSettle();
      // AppPage's glass header overlays the top of its scroll view.
      await Scrollable.ensureVisible(
        tester.element(find.text('Plan')),
        alignment: .35,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Plan'));
      await tester.pumpAndSettle();
      expect(
        tester.widget<TextFormField>(field).controller!.text,
        'https://api.stepfun.com/step_plan/v1',
      );
      expect(
        tester.widget<TextFormField>(key).controller!.text,
        'fixture-step-key',
      );
      expect(settings.savedModels, isEmpty);
    },
  );

  testWidgets(
    'Settings retain native configuration and real preferences, without account or billing stubs',
    (tester) async {
      await mount(tester, FakeSettingsBridge());
      await open(tester, const SettingsHubPage());
      expect(find.text('模型配置'), findsOneWidget);
      expect(find.text('test-model'), findsOneWidget);
      expect(find.text('Amoo'), findsOneWidget);
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
    'Wireless pairing explains the actual developer settings destination',
    (tester) async {
      final settings = FakeSettingsBridge();
      await mount(tester, settings);
      await open(tester, const SystemAccessPage());
      final pairing = find.text('通过无线调试配对');
      await tester.ensureVisible(pairing);
      await tester.tap(pairing);
      await tester.pumpAndSettle();
      expect(settings.targets, ['pairing']);
      expect(find.textContaining('打开开发者选项'), findsWidgets);
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
      expect(find.text('已连接'), findsOneWidget);
      expect(find.text('未允许'), findsOneWidget);
      await tester.tap(find.text('无障碍服务'));
      await tester.pumpAndSettle();
      expect(settings.targets, ['accessibility']);
      expect(find.text('已连接'), findsOneWidget);
      expect(find.textContaining('测试通过'), findsNothing);
    },
  );
  testWidgets(
    'Pairing guides notification permission before starting discovery',
    (tester) async {
      final settings = FakeSettingsBridge()..values['notifications'] = false;
      final controller = await mount(tester, settings);
      await open(tester, const SystemAccessPage());
      final pairing = find.text('通过无线调试配对');
      await tester.ensureVisible(pairing);
      expect(find.textContaining('先允许通知，以便'), findsOneWidget);
      await tester.tap(pairing);
      await tester.pumpAndSettle();
      expect(settings.targets, ['notifications']);
      settings.values['notifications'] = true;
      await controller.refreshSettings();
      await tester.pumpAndSettle();
      await tester.ensureVisible(pairing);
      await tester.tap(pairing);
      await tester.pumpAndSettle();
      expect(settings.targets, ['notifications', 'pairing']);
    },
  );

  testWidgets(
    'Enabled accessibility without a connected service is not ready',
    (tester) async {
      final settings = FakeSettingsBridge()
        ..values['accessibilityRunning'] = false;
      await mount(tester, settings);
      await open(tester, const SystemAccessPage());
      expect(find.text('等待连接'), findsOneWidget);
      expect(find.textContaining('服务尚未连接'), findsOneWidget);
      expect(find.text('已连接'), findsNothing);
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

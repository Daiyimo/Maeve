import 'package:flutter/material.dart';
import 'package:maeve_flutter/ui/common/selection_field.dart';
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
import 'package:maeve_flutter/ui/settings/phone_readiness_page.dart';
import 'package:maeve_flutter/ui/settings/scheduled_tasks_page.dart';
import 'package:maeve_flutter/ui/settings/amoo_page.dart';
import 'package:maeve_flutter/ui/settings/amoo_wechat_page.dart';
import 'package:maeve_flutter/ui/settings/about_pages.dart';
import 'package:maeve_flutter/ui/settings/welcome_page.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

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
    // 顶部现在是染色玻璃状态卡:标题距顶栏 = 12 外距 + 16 卡内距。
    final groupTop = tester.getTopLeft(find.text('手机操作状态检测')).dy;
    expect(groupTop - headerBottom, inInclusiveRange(12, 28));
  });

  testWidgets('Daily plan can be enabled from home and run independently', (
    tester,
  ) async {
    final settings = FakeSettingsBridge();
    final actions = <Map<String, Object>>[];
    Map<String, dynamic>? plan;
    settings.scheduleHandler = (args) async {
      actions.add(args);
      if (args['action'] == 'create') {
        plan = {
          'id': 'daily',
          'threadId': 'daily-thread',
          'goal': args['goal'],
          'time': args['time'],
          'nextAt': 2000000000000,
          'enabled': true,
        };
      }
      if (args['action'] == 'runNow') {
        plan!.addAll({
          'state': 'waiting',
          'blocked': 'offline',
          'inFlight': true,
        });
      }
      return {
        'exactAlarm': true,
        'plans': [?plan],
        'deferred': args['action'] == 'runNow' ? 'offline' : null,
      };
    };
    final controller = await mount(tester, settings);
    await tester.tap(find.byTooltip('定时任务'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('新建定时任务'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, '每日签到');
    await tester.pump();
    await tester.tap(find.text('启用'));
    await tester.pumpAndSettle();
    expect(
      actions.singleWhere((action) => action['action'] == 'create')['time'],
      '09:00',
    );
    expect(find.text('每日签到'), findsOneWidget);
    await tester.tap(find.text('立即执行'));
    await tester.pumpAndSettle();
    expect(find.byType(ScheduledTasksPage), findsNothing);
    expect(controller.activeId, 'daily-thread');
    expect(
      actions.singleWhere((action) => action['action'] == 'runNow')['id'],
      'daily',
    );
  });

  testWidgets('A saved calendar schedule sends an edit for the same plan', (
    tester,
  ) async {
    final settings = FakeSettingsBridge();
    final edits = <Map<String, Object>>[];
    settings.scheduleHandler = (args) async {
      if (args['action'] == 'reschedule') edits.add(args);
      return {
        'exactAlarm': true,
        'plans': [
          {
            'id': 'weekly',
            'threadId': 'same-thread',
            'goal': '每周核对进展',
            'nextAt': 2000000000000,
            'time': '09:30',
            'calendarZone': 'Asia/Shanghai',
            'calendarDays': [1, 5],
            'enabled': false,
          },
        ],
      };
    };
    await mount(tester, settings);
    await tester.tap(find.byTooltip('定时任务'));
    await tester.pumpAndSettle();
    expect(find.text('每周一、五 09:30 · 已暂停'), findsOneWidget);
    await tester.ensureVisible(find.text('改时间'));
    await tester.tap(find.text('改时间'));
    await tester.pumpAndSettle();
    final picker = find.byType(TimePickerDialog);
    expect(picker, findsOneWidget);
    final context = tester.element(picker);
    await tester.tap(
      find.text(MaterialLocalizations.of(context).okButtonLabel),
    );
    await tester.pumpAndSettle();
    expect(edits, [
      {'action': 'reschedule', 'id': 'weekly', 'time': '09:30'},
    ]);
    expect(find.text('每周一、五 09:30 · 已暂停'), findsOneWidget);
  });

  testWidgets('Amoo example creates the built-in check-in preset on enable', (
    tester,
  ) async {
    const example = {
      'id': 'amoo_growth_check_in',
      'title': 'Amoo 成长计划签到',
      'goal': '进入 STEPX 账号页的 Amoo 成长计划，未签到时签到一次。',
    };
    final creates = <Map<String, Object>>[];
    Map<String, Object?>? plan;
    final settings = FakeSettingsBridge();
    settings.scheduleHandler = (request) async {
      if (request['action'] == 'create') {
        creates.add(request);
        plan = {
          'id': 'amoo',
          'title': example['title'],
          'preset': example['id'],
          'goal': example['goal'],
          'time': request['time'],
          'nextAt': 2000000000000,
          'enabled': true,
        };
      }
      return {
        'exactAlarm': true,
        'examples': [example],
        'plans': [?plan],
      };
    };
    await mount(tester, settings);
    await tester.tap(find.byTooltip('定时任务'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(example['title']!));
    await tester.pumpAndSettle();
    expect(find.text(example['goal']!), findsOneWidget);
    expect(creates, isEmpty);
    await tester.tap(find.text('启用'));
    await tester.pumpAndSettle();
    expect(creates.single['preset'], example['id']);
    expect(creates.single['time'], '09:00');
    expect(
      find.byKey(ValueKey('schedule-example-${example['id']}')),
      findsNothing,
    );
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
      expect(
        tester
            .widget<MaeveSegments<bool>>(find.byType(MaeveSegments<bool>))
            .value,
        isFalse,
      );
      await tester.ensureVisible(find.text('后台'));
      await tester.tap(find.text('后台'));
      await tester.pumpAndSettle();
      expect(settings.wechatStatus['background'], isTrue);
      expect(settings.wechatStatus['enabled'], isTrue);
      expect(
        tester
            .widget<MaeveSegments<bool>>(find.byType(MaeveSegments<bool>))
            .value,
        isTrue,
      );
      expect(settings.wechatActions, contains('setBackground'));
    },
  );

  testWidgets('WeChat cannot enable background entry without helper', (
    tester,
  ) async {
    final settings = FakeSettingsBridge();
    await mount(tester, settings);
    await open(tester, const AmooWechatPage());
    await tester.ensureVisible(find.text('后台'));
    await tester.tap(find.text('后台'));
    await tester.pumpAndSettle();
    expect(settings.wechatActions, isNot(contains('setBackground')));
  });

  testWidgets('WeChat shows progress while Amoo forwards a background task', (
    tester,
  ) async {
    final settings = FakeSettingsBridge();
    settings.wechatStatus.addAll({
      'bound': true,
      'enabled': true,
      'background': true,
      'amooActive': true,
      'amooProgress': '正在等待 Amoo 响应',
    });
    await mount(tester, settings);
    await open(tester, const AmooWechatPage());
    expect(find.text('正在等待 Amoo 响应'), findsOneWidget);

    settings.wechatStatus['amooProgress'] = 'Maeve 正在手机上执行';
    await tester.tap(find.byTooltip('刷新微信状态'));
    await tester.pumpAndSettle();
    expect(find.text('正在等待 Amoo 响应'), findsNothing);
    expect(find.text('Maeve 正在手机上执行'), findsOneWidget);
  });

  testWidgets(
    'Presets and custom protocols populate independent connection fields',
    (tester) async {
      final settings = FakeSettingsBridge();
      await mount(tester, settings);
      await open(tester, const AddModelPage());
      await tester.tap(find.byKey(const ValueKey('provider-preset-阶跃星辰')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Anthropic').last);
      await tester.pumpAndSettle();
      String field(String key) => tester
          .widget<TextFormField>(find.byKey(ValueKey(key)))
          .controller!
          .text;
      expect(field('model-base-url'), 'https://api.anthropic.com');
      expect(find.text('Plan'), findsNothing);
      await tester.enterText(
        find.byKey(const ValueKey('model-api-key')),
        'temporary-key',
      );
      await tester.ensureVisible(
        find.byKey(const ValueKey('provider-preset-Anthropic')),
      );
      await tester.tap(find.byKey(const ValueKey('provider-preset-Anthropic')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('自定义').last);
      await tester.pumpAndSettle();
      expect(field('model-base-url'), isEmpty);
      expect(field('model-api-key'), isEmpty);
      expect(
        find.byKey(const ValueKey('custom-protocol-OpenAiCompat')),
        findsOneWidget,
      );
      await tester.enterText(
        find.byKey(const ValueKey('model-base-url')),
        'https://custom.example/v1',
      );
      await tester.enterText(
        find.byKey(const ValueKey('model-name')),
        'custom-model',
      );
      FocusManager.instance.primaryFocus?.unfocus();
      await tester.pumpAndSettle();
      await Scrollable.ensureVisible(
        tester.element(find.text('保存并使用')),
        alignment: .7,
      );
      await tester.scrollUntilVisible(
        find.text('保存并使用'),
        200,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('保存并使用'));
      await tester.pumpAndSettle();
      expect(
        settings.savedModels.single['baseUrl'],
        'https://custom.example/v1',
      );
      expect(settings.savedModels.single['kind'], 'OpenAiCompat');
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Long selectors search and cancel without changing the chosen value',
    (tester) async {
      final settings = FakeSettingsBridge();
      settings.values['presets'] = [
        ...(settings.values['presets'] as List),
        for (var i = 0; i < 20; i++)
          {
            'id': 'provider-$i',
            'label': '服务商 $i',
            'kind': 'OpenAiCompat',
            'baseUrl': 'https://provider$i.example/v1',
          },
      ];
      await mount(tester, settings);
      await open(tester, const AddModelPage());
      await tester.tap(find.byKey(const ValueKey('provider-preset-阶跃星辰')));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('selection-search')),
        '服务商 17',
      );
      await tester.pumpAndSettle();
      expect(find.widgetWithText(ListTile, '服务商 17'), findsOneWidget);
      expect(find.text('服务商 16'), findsNothing);
      await tester.tap(find.byTooltip('关闭选择'));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('provider-preset-阶跃星辰')),
        findsOneWidget,
      );
      expect(settings.savedModels, isEmpty);
    },
  );

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
      await tester.tap(find.text('连接更多'));
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

  testWidgets(
    'Amoo shows setup verification time without fabricating a task result',
    (tester) async {
      final settings = FakeSettingsBridge()
        ..amooStatus = NativeAmooStatus(
          stage: 'done',
          installed: true,
          accessibility: true,
          bridgeEnabled: true,
          lastSetupAt: DateTime(2026, 11, 15, 10, 20, 30),
        );
      await mount(tester, settings);
      await open(tester, const AmooPage());
      expect(find.text('最近接入验证成功'), findsOneWidget);
      expect(find.text('2026-11-15 10:20:30'), findsOneWidget);
      await tester.ensureVisible(find.text('最近收到的任务结果'));
      expect(find.text('尚无任务结果，收到任务回传后更新'), findsOneWidget);
    },
  );

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
      expect(find.text('阶跃星辰'), findsWidgets);
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
      expect(find.text('连接更多'), findsOneWidget);
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
  testWidgets('Privacy disclosure is reachable from every data entry', (
    tester,
  ) async {
    await mount(tester, FakeSettingsBridge());
    await open(tester, const SettingsHubPage());
    final settingsEntry = find.text('隐私声明');
    await tester.scrollUntilVisible(
      settingsEntry,
      320,
      scrollable: find.byType(Scrollable).last,
    );
    await Scrollable.ensureVisible(
      tester.element(settingsEntry),
      alignment: .5,
    );
    await tester.pumpAndSettle();
    await tester.tap(settingsEntry);
    await tester.pumpAndSettle();
    expect(find.byType(PrivacyPage), findsOneWidget);
    expect(find.text(privacyModelDisclosure), findsOneWidget);
    expect(find.text(privacyLocalDisclosure), findsOneWidget);
    expect(find.text(privacyChannelDisclosure), findsOneWidget);
    Navigator.of(tester.element(find.byType(PrivacyPage))).pop();
    await tester.pumpAndSettle();

    AppPage.open(
      tester.element(find.byType(SettingsHubPage)),
      const ModelSettingsPage(),
    );
    await tester.pumpAndSettle();
    final modelEntry = find.text('隐私与数据去向');
    await tester.scrollUntilVisible(
      modelEntry,
      320,
      scrollable: find.byType(Scrollable).last,
    );
    await Scrollable.ensureVisible(tester.element(modelEntry), alignment: .5);
    await tester.pumpAndSettle();
    await tester.tap(modelEntry);
    await tester.pumpAndSettle();
    expect(find.byType(PrivacyPage), findsOneWidget);
    Navigator.of(tester.element(find.byType(PrivacyPage))).pop();
    await tester.pumpAndSettle();

    AppPage.open(
      tester.element(find.byType(ModelSettingsPage)),
      const WelcomePage(),
    );
    await tester.pumpAndSettle();
    final welcomeEntry = find.text('查看隐私声明');
    await tester.scrollUntilVisible(
      welcomeEntry,
      320,
      scrollable: find.byType(Scrollable).last,
    );
    await Scrollable.ensureVisible(tester.element(welcomeEntry), alignment: .5);
    await tester.pumpAndSettle();
    await tester.tap(welcomeEntry);
    await tester.pumpAndSettle();
    expect(find.byType(PrivacyPage), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

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
    controller.setGlassDensity(MaeveGlassDensity.dense);
    await tester.pumpAndSettle();
    final fresh = MaeveController(
      settingsBridge: settings,
      bridge: FakeChatBridge(),
    );
    addTearDown(fresh.dispose);
    await fresh.refreshSettings();
    expect(fresh.accentPreset, MaeveAccentPreset.dolores);
    expect(fresh.mode, ThemeMode.light);
    expect(fresh.glassDensity, MaeveGlassDensity.dense);
  });

  testWidgets(
    'Phone readiness: fresh install shows pending count on top and guides each permission',
    (tester) async {
      final settings = FakeSettingsBridge()
        ..values['accessibility'] = false
        ..values['accessibilityRunning'] = false
        ..values['overlay'] = false
        ..values['notifications'] = false
        ..values['helperRunning'] = false
        ..values['helperPaired'] = false
        ..values['developerOptions'] = false
        ..values['wirelessDebugging'] = false;
      await mount(tester, settings);
      await open(tester, const SettingsHubPage());
      // 设置页最上面就是状态行:新装设备一眼看到待办数。
      expect(find.text('手机操作状态检测'), findsOneWidget);
      expect(find.text('6 项待完成'), findsOneWidget);
      // 状态卡 = 染色玻璃(语义色洗层),不是平面面板(visual-tokens 液态玻璃条款)。
      expect(
        tester
            .widgetList<MaeveGlassSurface>(find.byType(MaeveGlassSurface))
            .where((g) => g.tint != null),
        isNotEmpty,
        reason: 'hub status card must be tinted glass',
      );
      await tester.tap(find.text('手机操作状态检测'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widgetList<MaeveGlassSurface>(
              find.descendant(
                of: find.byType(PhoneReadinessPage),
                matching: find.byType(MaeveGlassSurface),
              ),
            )
            .where((g) => g.tint != null),
        isNotEmpty,
        reason: 'guide hero must be tinted glass',
      );
      // 引导页:下一步按钮指向第一个未完成项,每项讲用途、给去处。
      expect(find.text('去完成：无障碍服务'), findsOneWidget);
      expect(find.text('读取界面并执行操作'), findsOneWidget);
      expect(find.text('显示任务进度与确认卡'), findsOneWidget);
      await tester.tap(find.text('去完成：无障碍服务'));
      await tester.pumpAndSettle();
      expect(settings.targets, ['accessibility']);
      await tester.ensureVisible(find.text('开发者选项'));
      await tester.tap(find.text('开发者选项'));
      await tester.pumpAndSettle();
      expect(settings.targets, ['accessibility', 'developer']);
      // 通知未允许时,无线调试先引导去补通知(配对码依赖通知)。
      await tester.ensureVisible(find.text('无线调试'));
      await tester.tap(find.text('无线调试'));
      await tester.pumpAndSettle();
      expect(settings.targets, ['accessibility', 'developer', 'notifications']);
    },
  );

  testWidgets('Phone readiness: everything granted shows ready, no next step', (
    tester,
  ) async {
    final settings = FakeSettingsBridge()
      ..values['overlay'] = true
      ..values['helperRunning'] = true;
    await mount(tester, settings);
    await open(tester, const SettingsHubPage());
    expect(find.text('已就绪'), findsOneWidget);
    await tester.tap(find.text('手机操作状态检测'));
    await tester.pumpAndSettle();
    expect(find.textContaining('手机操作已就绪'), findsOneWidget);
    expect(find.textContaining('去完成'), findsNothing);
  });

  testWidgets(
    'Phone readiness: paired helper waits, unsupported devices get the Android 12 note',
    (tester) async {
      final settings = FakeSettingsBridge();
      final controller = await mount(tester, settings);
      await open(tester, const PhoneReadinessPage());
      // 已配对未连接:助手项是"等待连接",不是待办跳转。
      expect(find.text('等待连接'), findsOneWidget);
      settings.values['phoneSupported'] = false;
      await controller.refreshSettings();
      await tester.pumpAndSettle();
      expect(find.textContaining('Android 12'), findsWidgets);
      expect(find.text('开发者选项'), findsNothing);
    },
  );

  testWidgets(
    'Phone readiness: missing wireless debugging falls back to the USB note',
    (tester) async {
      final settings = FakeSettingsBridge()
        ..values['wirelessDebugging'] = false
        ..values['wirelessDebuggingMissing'] = true
        ..values['helperPaired'] = false
        ..values['helperRunning'] = false;
      final controller = await mount(tester, settings);
      await open(tester, const PhoneReadinessPage());
      expect(find.text('系统未提供'), findsOneWidget);
      // USB 兜底说明在连接组之后:懒列表里先滚到可见再断言。
      await tester.scrollUntilVisible(
        find.textContaining('可用电脑经 USB 启动手机助手'),
        200,
        scrollable: find
            .descendant(
              of: find.byType(PhoneReadinessPage),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      expect(find.textContaining('可用电脑经 USB 启动手机助手'), findsWidgets);
      await controller.refreshSettings();
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    },
  );
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/ui/settings/scheduled_tasks_page.dart';

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
}

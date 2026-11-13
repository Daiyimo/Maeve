import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/settings/system_access_page.dart';
import 'package:maeve_flutter/ui/settings/execution_settings_page.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';
import 'package:maeve_flutter/ui/settings/run_history_page.dart';
import 'package:maeve_flutter/models/execution.dart';
import 'package:maeve_flutter/widgets/execution_view.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

void main() {
  testWidgets(
    'Execution evidence shows measured time and actual pointer coordinates',
    (tester) async {
      final bridge = FakeChatBridge();
      final controller = MaeveController(
        bridge: bridge,
        settingsBridge: FakeSettingsBridge(),
      );
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox.shrink());
        await bridge.dispose();
      });
      final step = ExecutionStep.fromMap({
        'step': 2,
        'title': '点击',
        'timingLabel': '模型 2.1 秒 · 操作 0.6 秒 · 等待 1.2 秒',
        'pointers': [
          {
            'x': 301,
            'y': 702,
            'width': 1200,
            'height': 2670,
            'method': 'touch',
            'clicks': 1,
          },
        ],
      });
      await tester.pumpWidget(MaeveApp(controller: controller));
      await tester.pumpAndSettle();
      AppPage.open(
        tester.element(find.byType(Scaffold).first),
        AppPage.list(
          title: '测试记录',
          children: [ExecutionStepView(step: step)],
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('模型 2.1 秒 · 操作 0.6 秒 · 等待 1.2 秒'), findsOneWidget);
      expect(find.text('实际触摸 · (301, 702) px'), findsOneWidget);
    },
  );

  Future<MaeveController> mount(
    WidgetTester tester,
    FakeChatBridge bridge,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      MethodChannel(MaeveBridge.entryEvents.name),
      (_) async => null,
    );
    final controller = MaeveController(
      bridge: bridge,
      settingsBridge: FakeSettingsBridge(mode: 'light'),
    );
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      await bridge.dispose();
      tester.view.reset();
      messenger.setMockMethodCallHandler(
        MethodChannel(MaeveBridge.entryEvents.name),
        null,
      );
    });
    await tester.pumpWidget(MaeveApp(controller: controller));
    await tester.pumpAndSettle();
    return controller;
  }

  testWidgets('Home has no workbench entry or right-edge workbench', (
    tester,
  ) async {
    await mount(tester, FakeChatBridge());
    expect(find.byTooltip('快捷面板'), findsNothing);
    await tester.dragFrom(const Offset(388, 400), const Offset(-230, 0));
    await tester.pumpAndSettle();
    expect(find.text('工作台'), findsNothing);
  });

  testWidgets(
    'The native run list can open retained records without a conversation link',
    (tester) async {
      final bridge = FakeChatBridge()
        ..runRecords.add({
          'id': 'record-actual',
          'goal': '实际点击位置验证',
          'status': 'interrupted',
          'startedAtMs': 1794474463000,
        })
        ..record = const RunDetails(
          available: true,
          goal: '实际点击位置验证',
          outcome: '记录已中断',
        );
      await mount(tester, bridge);
      AppPage.open(
        tester.element(find.byKey(const ValueKey('message-input'))),
        RunHistoryPage(bridge: bridge),
      );
      await tester.pumpAndSettle();
      expect(find.text('已中断'), findsOneWidget);
      await tester.tap(find.text('实际点击位置验证'));
      await tester.pumpAndSettle();
      expect(find.text('记录已中断'), findsOneWidget);
      expect(bridge.detailsRequests, ['record-actual']);
    },
  );

  testWidgets(
    'The capsule has one persistent switch and failed saves keep its real state',
    (tester) async {
      final controller = await mount(tester, FakeChatBridge());
      final settings = controller.settingsBridge as FakeSettingsBridge;
      AppPage.open(
        tester.element(find.byKey(const ValueKey('message-input'))),
        const SettingsHubPage(),
      );
      await tester.pumpAndSettle();
      expect(find.text('悬浮胶囊'), findsOneWidget);
      final tile = find.byKey(const ValueKey('capsule-toggle'));
      await tester.scrollUntilVisible(
        tile,
        180,
        scrollable: find.byType(Scrollable).last,
      );
      await Scrollable.ensureVisible(tester.element(tile), alignment: .4);
      await tester.pumpAndSettle();
      await tester.tap(
        find.descendant(of: tile, matching: find.byType(Switch)),
      );
      await tester.pumpAndSettle();
      expect(settings.values['capsuleEnabled'], isFalse);
      settings.fail = true;
      await Scrollable.ensureVisible(tester.element(tile), alignment: .4);
      await tester.pumpAndSettle();
      await tester.tap(
        find.descendant(of: tile, matching: find.byType(Switch)),
      );
      await tester.pumpAndSettle();
      expect(settings.values['capsuleEnabled'], isFalse);
      expect(find.text('悬浮胶囊开关未能保存，请重试。'), findsOneWidget);
    },
  );

  testWidgets(
    'Native model waiting is distinct from a phone action in progress',
    (tester) async {
      final bridge = FakeChatBridge();
      await mount(tester, bridge);
      bridge.emitUi(
        conversationId: 'c1',
        composer: 'busy',
        exchanges: [
          {
            ...FakeChatBridge.exchange('查看屏幕', status: 'working'),
            'waitingForModel': true,
          },
        ],
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.text('等待模型响应'), findsOneWidget);
    },
  );

  test(
    'Pointer coordinates map through the letterbox rather than the whole card',
    () {
      final p = ExecutionPointer.fromMap({
        'width': 1200,
        'height': 2400,
        'method': 'touch',
        'x': 600,
        'y': 1200,
      });
      final painter = PointerEvidencePainter(p);
      expect(
        painter.imageRect(const Size(400, 200)),
        const Rect.fromLTWH(150, 0, 100, 200),
      );
      expect(
        painter.position(const Size(400, 200), 600, 1200),
        const Offset(200, 100),
      );
      expect(
        painter.position(const Size(400, 200), 0, 0),
        const Offset(150, 0),
      );
      expect(
        ExecutionPointer.fromMap({
          'width': 0,
          'height': 0,
          'method': 'touch',
          'x': 1,
          'y': 2,
        }).valid,
        isFalse,
      );
    },
  );

  testWidgets(
    'Pace changes persist through the native bridge and report failed saves',
    (tester) async {
      final controller = await mount(tester, FakeChatBridge());
      final settings = controller.settingsBridge as FakeSettingsBridge;
      AppPage.open(
        tester.element(find.byKey(const ValueKey('message-input'))),
        const ExecutionSettingsPage(),
      );
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('快速'),
        180,
        scrollable: find.byType(Scrollable).last,
      );
      await tester.tap(find.text('快速'));
      await tester.pumpAndSettle();
      expect(controller.native.operatingPace, 'fast');
      expect((await settings.load()).operatingPace, 'fast');
      settings.fail = true;
      await tester.ensureVisible(find.text('标准'));
      await tester.tap(find.text('标准'));
      await tester.pumpAndSettle();
      expect(controller.native.operatingPace, 'fast');
      expect(settings.paceChanges, ['fast']);
    },
  );

  testWidgets('Multiline task messages never overlap the execution panel', (
    tester,
  ) async {
    final bridge = FakeChatBridge();
    await mount(tester, bridge);
    tester.platformDispatcher.textScaleFactorTestValue = 1.6;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    const goal = '打开系统计算器，计算1+1，告诉我结果。不要进行其他操作。';
    bridge.emitUi(
      conversationId: 'c1',
      composer: 'busy',
      exchanges: [
        FakeChatBridge.exchange(goal, status: 'working', line: '正在打开计算器'),
      ],
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(tester.takeException(), isNull);
    expect(
      tester.getRect(find.text(goal)).bottom,
      lessThanOrEqualTo(tester.getRect(find.text('执行中')).top),
    );
  });

  testWidgets(
    'Approval mode persists through native settings and failed saves keep the old value',
    (tester) async {
      final bridge = FakeChatBridge();
      final controller = await mount(tester, bridge);
      final settings = controller.settingsBridge as FakeSettingsBridge;
      AppPage.open(
        tester.element(find.byKey(const ValueKey('message-input'))),
        const ExecutionSettingsPage(),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('自主执行'));
      await tester.pumpAndSettle();
      expect(controller.native.autonomous, isTrue);
      expect((await settings.load()).autonomous, isTrue);
      settings.fail = true;
      await tester.tap(find.text('询问模式'));
      await tester.pumpAndSettle();
      expect(controller.native.autonomous, isTrue);
      expect(find.text('确认方式未能保存，请重试。'), findsOneWidget);
      expect(settings.approvalChanges, [true]);
    },
  );

  testWidgets(
    'Live preview is conversation-scoped and stops requesting frames in the background',
    (tester) async {
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      final bridge = FakeChatBridge()
        ..frame = base64Decode(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=',
        );
      await mount(tester, bridge);
      bridge.emitUi(
        conversationId: 'live',
        composer: 'busy',
        exchanges: [
          FakeChatBridge.exchange('查看设置', status: 'working', line: '读取当前页面'),
        ],
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pump();
      expect(bridge.previewConversations, isNotEmpty);
      expect(find.text('实时执行画面 · 点按放大'), findsOneWidget);
      expect(bridge.previewConversations, everyElement('live'));
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      final count = bridge.previewConversations.length;
      await tester.pump(const Duration(seconds: 3));
      expect(bridge.previewConversations.length, count);
      for (final state in [
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pump();
      bridge.emitUi(
        conversationId: 'live',
        exchanges: [FakeChatBridge.exchange('查看设置', answer: '完成')],
      );
      await tester.pumpAndSettle();
      expect(find.byType(TaskPreview), findsNothing);
    },
  );

  testWidgets(
    'Historical log opens the real run and its saved screenshot ids',
    (tester) async {
      final bridge = FakeChatBridge()
        ..frame = base64Decode(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=',
        )
        ..record = const RunDetails(
          available: true,
          goal: '打开设置',
          outcome: '任务失败',
          steps: [
            ExecutionStep(
              step: 2,
              title: '打开系统设置',
              detail: '未能打开页面',
              screenshotId: 7,
              resultFailed: true,
            ),
          ],
        );
      await mount(tester, bridge);
      bridge.emitUi(
        conversationId: 'c1',
        exchanges: [
          FakeChatBridge.exchange(
            '打开设置',
            status: 'failed',
            detail: '未能打开页面',
            runId: 'record-1',
          ),
        ],
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('完整执行记录'));
      await tester.pumpAndSettle();
      expect(find.byType(RunDetailsPage), findsOneWidget);
      expect(find.text('打开系统设置'), findsOneWidget);
      expect(find.text('未能打开页面'), findsOneWidget);
      expect(find.text('此步骤未成功'), findsOneWidget);
      expect(bridge.screenRequests, ['record-1:7']);
      await tester.tap(find.text('步骤 2 的画面记录 · 点按放大'));
      await tester.pumpAndSettle();
      expect(
        tester.widget<Text>(find.text('步骤 2 的画面记录')).style?.color,
        Colors.white,
      );
    },
  );

  testWidgets(
    'A confirmation response carries its conversation and current request key',
    (tester) async {
      final bridge = FakeChatBridge();
      await mount(tester, bridge);
      bridge.emitUi(
        conversationId: 'current',
        composer: 'busy',
        exchanges: [
          {
            ...FakeChatBridge.exchange(
              '继续任务',
              status: 'needs_confirmation',
              detail: '需要确认本次操作',
            ),
            'confirmationKey': 42,
          },
        ],
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.tap(find.text('同意这一次'));
      await tester.pump();
      expect(bridge.taskActions, ['current:approve:42']);
    },
  );

  testWidgets('Native action results are visible during task execution', (
    tester,
  ) async {
    final bridge = FakeChatBridge();
    await mount(tester, bridge);
    bridge.emitUi(
      conversationId: 'c1',
      composer: 'busy',
      exchanges: [
        {
          ...FakeChatBridge.exchange(
            '打开设置',
            status: 'working',
            steps: 1,
            line: '正在操作',
          ),
          'executionSteps': [
            {
              'step': 1,
              'title': '点击系统设置',
              'detail': '点击没有生效',
              'state': 'wait',
              'reason': '',
              'actions': [
                {
                  'label': '点击',
                  'target': '设置',
                  'effect': '点击没有生效',
                  'failed': true,
                },
              ],
            },
          ],
        },
      ],
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('点击系统设置'), findsOneWidget);
    expect(find.textContaining('点击没有生效'), findsWidgets);
  });

  testWidgets('Previous failed turns are not labelled completed', (
    tester,
  ) async {
    final bridge = FakeChatBridge();
    await mount(tester, bridge);
    bridge.emitUi(
      conversationId: 'c1',
      composer: 'busy',
      exchanges: [
        FakeChatBridge.exchange('上一个任务', status: 'failed', detail: '无法打开目标应用'),
        FakeChatBridge.exchange('新任务', status: 'working', line: '正在思考'),
      ],
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('任务失败'), findsOneWidget);
    expect(find.text('已完成'), findsNothing);
  });

  testWidgets(
    'Compact light sidebar fits at least ten single-line conversations',
    (tester) async {
      await mount(
        tester,
        FakeChatBridge(
          conversations: [
            for (var i = 0; i < 24; i++)
              FakeChatBridge.conversation(
                'c$i',
                '会话 ${i + 1}',
                updatedAt: DateTime(
                  2026,
                  11,
                  12,
                ).subtract(Duration(minutes: i)),
              ),
          ],
        ),
      );
      await tester.tap(find.byTooltip('打开对话列表'));
      await tester.pumpAndSettle();
      expect(find.text('会话 10').hitTestable(), findsOneWidget);
      final title = tester.widget<Text>(find.text('会话 1'));
      expect(title.maxLines, 1);
      expect(title.style!.fontSize, lessThanOrEqualTo(15));
    },
  );

  testWidgets('System authorization exposes the native confirmation mode', (
    tester,
  ) async {
    await mount(tester, FakeChatBridge());
    AppPage.open(
      tester.element(find.byKey(const ValueKey('message-input'))),
      const SystemAccessPage(),
    );
    await tester.pumpAndSettle();
    expect(find.text('操作确认方式'), findsOneWidget);
  });
}

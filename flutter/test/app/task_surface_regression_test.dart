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
import 'package:maeve_flutter/models/execution.dart';
import 'package:maeve_flutter/widgets/execution_view.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

void main() {
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

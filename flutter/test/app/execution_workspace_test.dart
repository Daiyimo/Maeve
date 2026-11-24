import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/models/task_report.dart';
import 'package:maeve_flutter/widgets/execution_view.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';
import 'package:maeve_flutter/widgets/task_playback_view.dart';
import 'package:maeve_flutter/widgets/task_settlement_view.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=',
);

TaskReport _report({bool frames = true}) => TaskReport(
  available: true,
  goal: '打开设置',
  outcome: '已完成',
  verdict: '核查通过',
  duration: '12 秒',
  answer: '已打开系统设置。',
  stages: const [
    PlaybackStage(title: '开始', stepIndex: 0),
    PlaybackStage(title: '完成', stepIndex: 0, screenshotId: 2),
  ],
  frames: frames
      ? const [
          ReplayFrame(stepIndex: 0, screenshotId: 1, step: 1, title: '打开设置'),
          ReplayFrame(
            stepIndex: 0,
            screenshotId: 2,
            step: 1,
            title: '设置已打开',
            after: true,
          ),
        ]
      : const [],
);

Future<MaeveController> _mount(
  WidgetTester tester,
  FakeChatBridge bridge, {
  required Size size,
}) async {
  tester.view.physicalSize = size;
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

void _emitWorking(FakeChatBridge bridge, {String? detail, String? line}) =>
    bridge.emitUi(
      conversationId: 'live',
      composer: 'busy',
      exchanges: [
        {
          ...FakeChatBridge.exchange(
            '打开设置',
            status: 'working',
            steps: 1,
            detail: detail,
            line: line ?? '',
          ),
          'executionSteps': [
            {'step': 1, 'title': '点击系统设置', 'detail': '操作已送达'},
          ],
        },
      ],
    );

void _emitDone(FakeChatBridge bridge, {String? detail}) => bridge.emitUi(
  conversationId: 'live',
  composer: 'idle',
  exchanges: [
    FakeChatBridge.exchange(
      '打开设置',
      status: 'done',
      answer: '已打开系统设置。',
      detail: detail,
      runId: 'run-1',
    ),
  ],
);

void main() {
  testWidgets('Wide live execution puts preview left and records right', (
    tester,
  ) async {
    final bridge = FakeChatBridge()..frame = _png;
    await _mount(tester, bridge, size: const Size(1200, 900));
    _emitWorking(bridge);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();

    final preview = tester.getRect(find.byType(TaskPreview));
    final records = tester.getRect(find.byType(LiveExecutionFeed));
    expect(preview.right, lessThanOrEqualTo(records.left));
    expect(preview.top, records.top);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Compact live execution keeps preview left of records', (
    tester,
  ) async {
    final bridge = FakeChatBridge()..frame = _png;
    await _mount(tester, bridge, size: const Size(320, 568));
    _emitWorking(bridge);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();

    final preview = tester.getRect(find.byType(TaskPreview));
    final records = tester.getRect(find.byType(LiveExecutionFeed));
    expect(preview.right, lessThanOrEqualTo(records.left));
    expect(preview.top, records.top);
    final recordTitle = tester.widget<Text>(find.text('01  点击系统设置'));
    expect(recordTitle.style?.fontSize, lessThanOrEqualTo(12));
    expect(
      find.byIcon(Icons.keyboard_double_arrow_down_rounded),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.vertical_align_bottom_rounded), findsNothing);
    expect(find.byTooltip('正在跟随最新'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Active status detail waits for the settlement pane', (
    tester,
  ) async {
    final bridge = FakeChatBridge()
      ..frame = _png
      ..report = _report();
    await _mount(tester, bridge, size: const Size(390, 844));
    _emitWorking(bridge, line: '正在核对当前页面');
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('正在核对当前页面'), findsNothing);

    _emitDone(bridge, detail: '已核对 10 步执行记录');
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byType(TaskSettlementView),
        matching: find.text('已核对 10 步执行记录'),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'Completed wide task shows last-frame playback beside settlement',
    (tester) async {
      final bridge = FakeChatBridge()
        ..frame = _png
        ..report = _report();
      await _mount(tester, bridge, size: const Size(1200, 900));
      _emitDone(bridge);
      await tester.pumpAndSettle();

      expect(find.text('第 2 / 2 帧 · 来自本机记录'), findsOneWidget);
      expect(find.byIcon(Icons.play_arrow_rounded), findsOneWidget);
      expect(find.byIcon(Icons.fullscreen_rounded), findsNothing);
      expect(find.text('0.5×'), findsNothing);
      expect(find.text('1×'), findsNothing);
      expect(find.text('2×'), findsNothing);
      expect(find.text('已打开系统设置。'), findsOneWidget);
      final processButton = tester.widget<MaeveGlassTextButton>(
        find.widgetWithText(MaeveGlassTextButton, '查看过程'),
      );
      final reportButton = tester.widget<MaeveGlassTextButton>(
        find.widgetWithText(MaeveGlassTextButton, '任务报告'),
      );
      expect(processButton.compact, reportButton.compact);
      expect(processButton.prominent, reportButton.prominent);
      expect(processButton.selected, reportButton.selected);
      expect(processButton.fill, reportButton.fill);
      expect(processButton.foreground, reportButton.foreground);
      expect(processButton.shape, reportButton.shape);
      expect(processButton.enabled, reportButton.enabled);
      final playback = tester.getRect(find.byType(TaskPlaybackView));
      final settlement = tester.getRect(find.byType(TaskSettlementView));
      expect(playback.right, lessThanOrEqualTo(settlement.left));

      await tester.tap(find.byIcon(Icons.play_arrow_rounded));
      await tester.pump();
      expect(find.text('第 1 / 2 帧 · 来自本机记录'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Home elapsed time accumulates through model and phone states', (
    tester,
  ) async {
    final bridge = FakeChatBridge();
    await _mount(tester, bridge, size: const Size(390, 844));

    void emit({
      bool waiting = false,
      int? elapsedMs,
      String? runId,
      bool done = false,
    }) {
      bridge.emitUi(
        conversationId: 'live',
        composer: done ? 'idle' : 'busy',
        exchanges: [
          {
            ...FakeChatBridge.exchange(
              '打开设置',
              status: done ? 'done' : 'working',
              answer: done ? '已打开系统设置。' : null,
              elapsedMs: elapsedMs,
              runId: runId,
            ),
            'waitingForModel': waiting,
          },
        ],
      );
    }

    emit(waiting: true);
    await tester.pump();
    await tester.pump();
    expect(find.text('等待模型响应'), findsOneWidget);
    await tester.pump(const Duration(seconds: 3));
    expect(find.text('3 秒'), findsOneWidget);

    emit(runId: 'run-clock');
    await tester.pump();
    expect(find.text('执行中'), findsOneWidget);
    expect(find.text('3 秒'), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
    expect(find.text('5 秒'), findsOneWidget);

    emit(waiting: true, elapsedMs: 4400, runId: 'run-clock');
    await tester.pump();
    expect(find.text('等待模型响应'), findsOneWidget);
    expect(find.text('5 秒'), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
    expect(find.text('7 秒'), findsOneWidget);

    emit(done: true, elapsedMs: 7200, runId: 'run-clock');
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    expect(find.text('已完成'), findsAtLeastNWidgets(1));
    expect(
      find.descendant(
        of: find.byType(ExecutionPanel),
        matching: find.text('7 秒'),
      ),
      findsOneWidget,
    );
    expect(find.text('9 秒'), findsNothing);
  });

  testWidgets('Completed narrow task keeps playback left of settlement', (
    tester,
  ) async {
    final bridge = FakeChatBridge()
      ..frame = _png
      ..report = _report();
    await _mount(tester, bridge, size: const Size(390, 844));
    _emitDone(bridge);
    await tester.pumpAndSettle();

    final playback = tester.getRect(find.byType(TaskPlaybackView));
    final settlement = tester.getRect(find.byType(TaskSettlementView));
    expect(playback.right, lessThanOrEqualTo(settlement.left));
    expect(playback.top, settlement.top);
    final goal = tester.widget<Text>(
      find.descendant(
        of: find.byType(TaskSettlementView),
        matching: find.text('打开设置'),
      ),
    );
    expect(goal.style?.fontSize, lessThanOrEqualTo(15));
    expect(tester.takeException(), isNull);
  });

  testWidgets('Compact task keeps prompt and execution in one viewport', (
    tester,
  ) async {
    final bridge = FakeChatBridge()..frame = _png;
    await _mount(tester, bridge, size: const Size(369, 844));
    const goal = '打开系统计算器，计算 1+1，告诉我结果，不要进行其他操作。';
    bridge.emitUi(
      conversationId: 'live',
      composer: 'busy',
      exchanges: [
        {
          ...FakeChatBridge.exchange(goal, status: 'working', steps: 1),
          'executionSteps': [
            {'step': 1, 'title': '点击系统设置', 'detail': '操作已送达'},
          ],
        },
      ],
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();

    final feed = find.byType(LiveExecutionFeed);
    expect(find.text(goal).hitTestable(), findsOneWidget);
    expect(feed.hitTestable(), findsOneWidget);
    expect(
      tester.getRect(feed).bottom,
      lessThanOrEqualTo(
        tester.getTopLeft(find.byKey(const ValueKey('message-input'))).dy,
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Missing frames keep settlement visible without empty controls', (
    tester,
  ) async {
    final bridge = FakeChatBridge()..report = _report(frames: false);
    await _mount(tester, bridge, size: const Size(390, 844));
    _emitDone(bridge);
    await tester.pumpAndSettle();

    expect(find.text('这次任务没有记录画面。'), findsOneWidget);
    expect(find.byType(TaskSettlementView), findsOneWidget);
    expect(find.byIcon(Icons.play_arrow_rounded), findsNothing);
    expect(tester.takeException(), isNull);
  });
}

import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/app/chat_page.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/models/execution.dart';
import 'package:maeve_flutter/models/task_report.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/settings/run_history_page.dart';
import 'package:maeve_flutter/widgets/execution_view.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';
import 'package:maeve_flutter/widgets/task_playback_view.dart';
import 'package:maeve_flutter/widgets/task_share_page.dart';
import 'package:maeve_flutter/widgets/task_settlement_view.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

final _png = base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAQAAAAICAIAAABRUclSAAAAFElEQVR4nGNcNmsvAwwwwVlU5wAAzl8CDZzzQqQAAAAASUVORK5CYII=',
);

class _ShareBridge extends FakeChatBridge {
  late Completer<Uint8List> png;

  @override
  Future<bool> shareTaskImage(Uint8List image) async {
    png.complete(image);
    return true;
  }
}

TaskReport _report({bool frames = true}) => TaskReport(
  available: true,
  goal: '打开设置',
  outcome: '已完成',
  verdict: '核查通过',
  duration: '12 秒',
  answer: '已打开系统设置。',
  stages: const [
    PlaybackStage(title: '开始', stepIndex: 0),
    PlaybackStage(title: '完成', stepIndex: 1, screenshotId: 2),
  ],
  frames: frames
      ? const [
          ReplayFrame(stepIndex: 0, screenshotId: 1, step: 1, title: '打开设置'),
          ReplayFrame(
            stepIndex: 1,
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
  FakeSettingsBridge? settings,
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
    settingsBridge: settings ?? FakeSettingsBridge(mode: 'light'),
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

void _emitWorking(
  FakeChatBridge bridge, {
  String? detail,
  String? line,
  String? runId,
}) => bridge.emitUi(
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
        runId: runId,
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
  testWidgets('A completed run can be scheduled using its original goal', (
    tester,
  ) async {
    const goal = '打开 Amoo 成长计划，未签到时签到';
    final requests = <Map<String, Object>>[];
    final settings = FakeSettingsBridge(mode: 'light');
    settings.scheduleHandler = (request) async {
      requests.add(request);
      return {'exactAlarm': true, 'plans': []};
    };
    final bridge = FakeChatBridge();
    await _mount(
      tester,
      bridge,
      size: const Size(390, 844),
      settings: settings,
    );
    bridge.emitUi(
      conversationId: 'amoo-thread',
      composer: 'idle',
      exchanges: [
        {
          ...FakeChatBridge.exchange(
            goal,
            status: 'done',
            answer: '已签到',
            runId: 'amoo-success',
            steps: 4,
          ),
          'scheduleGoal': goal,
        },
        FakeChatBridge.exchange(
          '把这个任务存为定时任务',
          status: 'done',
          answer: '你希望每天什么时间签到？',
          runId: 'needs-time',
          steps: 1,
        ),
      ],
    );
    await tester.pumpAndSettle();

    final schedule = find.widgetWithText(MaeveGlassTextButton, '设为定时');
    expect(schedule, findsNothing);
    await tester.tap(find.textContaining('4 步'));
    await tester.pumpAndSettle();
    expect(schedule, findsOneWidget);
    await tester.ensureVisible(schedule);
    await tester.tap(schedule);
    await tester.pumpAndSettle();
    expect(
      tester.widget<TextField>(find.byType(TextField).last).controller!.text,
      goal,
    );
    expect(requests.where((request) => request['action'] == 'create'), isEmpty);

    await tester.tap(find.text('启用'));
    await tester.pumpAndSettle();
    final create = requests.singleWhere(
      (request) => request['action'] == 'create',
    );
    expect(create['goal'], goal);
    expect(create['time'], '09:00');
    expect(create['conversationId'], 'amoo-thread');
  });

  testWidgets('Wide live execution puts preview left and records right', (
    tester,
  ) async {
    final bridge = FakeChatBridge()..frame = _png;
    await _mount(tester, bridge, size: const Size(1200, 900));
    _emitWorking(bridge);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();

    final previewFrame = find.byKey(const ValueKey('live-preview-frame'));
    final preview = tester.getRect(previewFrame);
    final records = tester.getRect(
      find.byKey(const ValueKey('live-execution-feed')),
    );
    expect(preview.right, lessThanOrEqualTo(records.left));
    expect(preview.top, records.top);
    expect(preview.bottom, records.bottom);
    final previewTitle = tester.getRect(find.text('实时执行画面'));
    final recordTitle = tester.getRect(find.text('执行时间线'));
    expect(previewTitle.top, recordTitle.top);
    expect(previewTitle.bottom, recordTitle.bottom);
    expect(previewTitle.bottom, lessThan(preview.top));
    final liveImage = find.descendant(
      of: previewFrame,
      matching: find.byType(Image),
    );
    expect(tester.getRect(liveImage).height, greaterThan(300));
    expect(find.byKey(const ValueKey('stepx-neo-ai-key')), findsOneWidget);
    expect(tester.widget<Image>(liveImage).fit, BoxFit.contain);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Compact live execution keeps the phone left of the timeline', (
    tester,
  ) async {
    final bridge = FakeChatBridge()..frame = _png;
    await _mount(tester, bridge, size: const Size(320, 568));
    _emitWorking(bridge);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();

    final previewFrame = find.byKey(const ValueKey('live-preview-frame'));
    final preview = tester.getRect(previewFrame);
    final records = tester.getRect(
      find.byKey(const ValueKey('live-execution-feed')),
    );
    expect(preview.right, lessThan(records.left));
    expect(preview.top, records.top);
    expect(preview.bottom, records.bottom);
    final previewTitle = tester.getRect(find.text('实时画面'));
    final recordHeading = tester.getRect(find.text('执行时间线'));
    expect(previewTitle.top, recordHeading.top);
    expect(previewTitle.bottom, lessThan(preview.top));
    expect(320 - records.right, closeTo(20, .1));
    final liveImage = find.descendant(
      of: previewFrame,
      matching: find.byType(Image),
    );
    expect(tester.getRect(liveImage).height, greaterThan(200));
    expect(find.text('点击系统设置'), findsOneWidget);
    expect(find.byTooltip('放大实时画面'), findsOneWidget);
    expect(find.byTooltip('暂停实时画面'), findsOneWidget);
    expect(find.byTooltip('正在跟随最新'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Selecting a recorded step opens its actual saved frame', (
    tester,
  ) async {
    final bridge = FakeChatBridge()
      ..frame = _png
      ..record = const RunDetails(
        available: true,
        steps: [ExecutionStep(step: 1, title: '点击系统设置', screenshotId: 7)],
      );
    await _mount(tester, bridge, size: const Size(1200, 900));
    _emitWorking(bridge, runId: 'run-live');
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();
    expect(bridge.detailsRequests, ['run-live']);
    expect(find.byIcon(Icons.image_outlined), findsOneWidget);

    await tester.tap(find.text('点击系统设置'));
    await tester.pump();
    await tester.pump();
    expect(bridge.screenRequests, ['run-live:7']);
    expect(find.text('步骤 1 · 历史画面'), findsOneWidget);

    await tester.tap(find.text('返回实时'));
    await tester.pump();
    expect(find.text('实时执行画面'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  for (final hasRecording in [false, true]) {
    testWidgets(
      'Stopping keeps the execution columns aligned (recorded: $hasRecording)',
      (tester) async {
        final bridge = FakeChatBridge()
          ..frame = _png
          ..report = TaskReport(
            available: true,
            goal: '打开设置',
            outcome: '已停止',
            frames: _report().frames,
          );
        await _mount(tester, bridge, size: const Size(390, 844));
        _emitWorking(bridge);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 500));
        final stop = find.widgetWithText(MaeveGlassTextButton, '停止任务');
        await tester.ensureVisible(stop);
        await tester.tap(stop);
        await tester.pump();

        expect(bridge.cancelCalls, 1);
        expect(find.text('正在停止，等待任务收尾'), findsOneWidget);
        final left = tester.getRect(
          find.byKey(const ValueKey('live-preview-frame')),
        );
        final right = tester.getRect(
          find.byKey(const ValueKey('live-execution-feed')),
        );
        expect(left.right, lessThan(right.left));
        expect(left.top, right.top);
        expect(left.bottom, right.bottom);

        bridge.emitUi(
          conversationId: 'live',
          exchanges: [
            FakeChatBridge.exchange(
              '打开设置',
              status: 'cancelled',
              runId: hasRecording ? 'run-1' : null,
            ),
          ],
        );
        await tester.pumpAndSettle();
        final replay = tester.getRect(
          find.byKey(const ValueKey('stopped-playback-frame')),
        );
        final summary = tester.getRect(
          find.byKey(const ValueKey('stopped-settlement-frame')),
        );
        expect(replay.right, lessThan(summary.left));
        expect(replay.top, summary.top);
        expect(replay.bottom, summary.bottom);
        expect(replay.left, left.left);
        expect(summary.right, right.right);
        expect(
          tester.getTopLeft(find.text('操作回放')).dy,
          tester.getTopLeft(find.text('任务结算')).dy,
        );
        expect(find.text('已停止'), findsWidgets);
        expect(
          find.text('这次任务没有记录画面。'),
          hasRecording ? findsNothing : findsOneWidget,
        );
        expect(
          find.byIcon(Icons.play_arrow_rounded),
          hasRecording ? findsOneWidget : findsNothing,
        );
        expect(tester.takeException(), isNull);
      },
    );
  }

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
        of: find.byType(TaskReportCard),
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
      final settlement = tester.getRect(find.byType(TaskReportCard));
      expect(playback.right, lessThanOrEqualTo(settlement.left));
      final replayFrame = find.byKey(
        const ValueKey('completed-playback-frame'),
      );
      final summaryFrame = find.byKey(
        const ValueKey('completed-settlement-frame'),
      );
      final replayGlass = tester.widget<MaeveGlassSurface>(replayFrame);
      final summaryGlass = tester.widget<MaeveGlassSurface>(summaryFrame);
      expect(replayGlass.tier, MaeveGlassTier.card);
      expect(replayGlass.tier, summaryGlass.tier);
      expect(replayGlass.shape, summaryGlass.shape);
      expect(
        tester.getTopLeft(replayFrame).dy,
        tester.getTopLeft(summaryFrame).dy,
      );
      expect(
        tester.getBottomLeft(replayFrame).dy,
        tester.getBottomLeft(summaryFrame).dy,
      );
      expect(find.text('导出'), findsNothing);
      expect(find.text('完整报告'), findsNothing);
      expect(tester.getSize(find.byType(Slider)).width, 232);
      expect(
        tester
            .widget<MaeveGlassButton>(
              find.widgetWithIcon(MaeveGlassButton, Icons.play_arrow_rounded),
            )
            .visualDiameter,
        48,
      );

      final timeline = find.byKey(const ValueKey('live-execution-feed'));
      await tester.tap(
        find.descendant(of: timeline, matching: find.text('打开设置')),
      );
      await tester.pump();
      expect(find.text('第 1 / 2 帧 · 来自本机记录'), findsOneWidget);
      await tester.tap(
        find.descendant(of: timeline, matching: find.text('设置已打开')),
      );
      await tester.pump();
      expect(find.text('第 2 / 2 帧 · 来自本机记录'), findsOneWidget);

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

  testWidgets(
    'Completed narrow task keeps replay beside the outcome and timeline',
    (tester) async {
      final bridge = FakeChatBridge()
        ..frame = _png
        ..report = _report();
      await _mount(tester, bridge, size: const Size(369, 822));
      _emitDone(bridge);
      await tester.pumpAndSettle();

      final playback = tester.getRect(find.byType(TaskPlaybackView));
      final replayFrame = tester.getRect(
        find.byKey(const ValueKey('completed-playback-frame')),
      );
      final settlement = tester.getRect(
        find.byKey(const ValueKey('completed-settlement-frame')),
      );
      expect(playback.right, lessThanOrEqualTo(settlement.left));
      expect(replayFrame.top, settlement.top);
      expect(replayFrame.bottom, settlement.bottom);
      expect(369 - settlement.right, closeTo(20, .1));
      final controls = tester.getRect(
        find.byKey(const ValueKey('playback-controls')),
      );
      final play = tester.widget<MaeveGlassButton>(
        find.widgetWithIcon(MaeveGlassButton, Icons.play_arrow_rounded),
      );
      final previous = tester.widget<MaeveGlassButton>(
        find.widgetWithIcon(MaeveGlassButton, Icons.skip_previous_rounded),
      );
      final next = tester.widget<MaeveGlassButton>(
        find.widgetWithIcon(MaeveGlassButton, Icons.skip_next_rounded),
      );
      expect(controls.width, lessThanOrEqualTo(playback.width));
      expect(
        tester
            .getRect(
              find.widgetWithIcon(MaeveGlassButton, Icons.play_arrow_rounded),
            )
            .bottom,
        lessThanOrEqualTo(replayFrame.bottom),
      );
      expect(play.visualDiameter, lessThan(48));
      expect(play.visualDiameter, greaterThan(previous.visualDiameter!));
      expect(previous.visualDiameter, next.visualDiameter);
      final goal = tester.widget<Text>(
        find.descendant(
          of: find.byType(TaskReportCard),
          matching: find.text('打开设置'),
        ),
      );
      expect(goal.style?.fontSize, lessThanOrEqualTo(15));
      await tester.tap(find.text('时间线'));
      await tester.pump();
      final timeline = find.byKey(const ValueKey('live-execution-feed'));
      expect(tester.getRect(timeline).left, greaterThan(replayFrame.right));
      await tester.tap(
        find.descendant(of: timeline, matching: find.text('打开设置')),
      );
      await tester.pump();
      expect(find.text('第 1 / 2 帧'), findsOneWidget);
      await tester.tap(find.text('结算'));
      await tester.pump();
      expect(find.byType(TaskReportCard), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Share uses the selected completion and exports an actual PNG', (
    tester,
  ) async {
    const viewport = Size(320, 720);
    final bridge = _ShareBridge()
      ..report = _report()
      ..frame = _png;
    await _mount(tester, bridge, size: viewport);
    _emitDone(bridge);
    await tester.pumpAndSettle();

    final share = find.widgetWithText(MaeveGlassTextButton, '分享图');
    final process = find.widgetWithText(MaeveGlassTextButton, '查看过程');
    final report = find.widgetWithText(MaeveGlassTextButton, '任务报告');
    expect(tester.getTopLeft(share).dy, tester.getTopLeft(process).dy);
    expect(tester.getTopLeft(share).dy, tester.getTopLeft(report).dy);
    await tester.ensureVisible(share);
    await tester.tap(
      find.widgetWithIcon(MaeveGlassButton, Icons.skip_previous_rounded),
    );
    await tester.pump();
    await tester.tap(share);
    await tester.pumpAndSettle();

    final preview = find.byType(TaskSharePage);
    expect(bridge.screenRequests, contains('run-1:1'));
    expect(
      find.descendant(of: preview, matching: find.byType(ChatPage)),
      findsNothing,
    );
    Rect phonePart(String key) => tester.getRect(
      find.descendant(of: preview, matching: find.byKey(ValueKey(key))),
    );
    final body = phonePart('stepx-neo-phone-body');
    final volume = phonePart('stepx-neo-volume-rocker');
    final power = phonePart('stepx-neo-power-key');
    final ai = phonePart('stepx-neo-ai-key');
    final camera = phonePart('stepx-neo-front-camera');
    for (final button in [volume, power, ai]) {
      expect(button.left, lessThan(body.right));
      expect(button.right, greaterThan(body.right));
    }
    expect(volume.bottom, lessThan(power.top));
    expect(power.bottom, lessThan(ai.top));
    expect(volume.height, greaterThan(power.height));
    expect((ai.top - body.top) / body.height, closeTo(.387, .002));
    expect(camera.center.dx, closeTo(body.center.dx, .01));
    expect((camera.center.dy - body.top) / body.height, closeTo(.034, .004));
    expect(
      find.descendant(
        of: preview,
        matching: find.byWidgetPredicate(
          (widget) => widget is Image && widget.semanticLabel == '第 1 帧操作画面',
        ),
      ),
      findsOneWidget,
    );
    expect(bridge.screenRequests, contains('run-1:1'));
    expect(find.byKey(const ValueKey('share-frame-1')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('share-frame-1')));
    await tester.pumpAndSettle();
    expect(bridge.screenRequests, contains('run-1:2'));
    expect(
      find.descendant(
        of: preview,
        matching: find.byWidgetPredicate(
          (widget) => widget is Image && widget.semanticLabel == '第 2 帧操作画面',
        ),
      ),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);

    await tester.tap(find.text('编辑标题'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, '标题'), '设置检查已完成');
    await tester.tap(find.text('应用'));
    await tester.pumpAndSettle();
    expect(
      find.descendant(of: preview, matching: find.text('设置检查已完成')),
      findsOneWidget,
    );

    final shareImage = tester.widget<MaeveGlassTextButton>(
      find.widgetWithText(MaeveGlassTextButton, '分享图片'),
    );
    final png = await tester.runAsync(() async {
      bridge.png = Completer<Uint8List>();
      shareImage.onPressed!();
      return bridge.png.future.timeout(const Duration(seconds: 15));
    });
    expect(png, isNotNull);
    expect(png!.take(8), [137, 80, 78, 71, 13, 10, 26, 10]);
    expect(ByteData.sublistView(png).getUint32(16), 1080);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('A report opened from run history can edit its share title', (
    tester,
  ) async {
    final bridge = FakeChatBridge()..report = _report(frames: false);
    bridge.runRecords.add({
      'id': 'history-run',
      'goal': '历史执行',
      'status': 'succeeded',
    });
    await _mount(tester, bridge, size: const Size(390, 844));
    AppPage.open(
      tester.element(find.byType(ChatPage)),
      RunHistoryPage(bridge: bridge),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('历史执行'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('分享图'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('编辑标题'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, '标题'), '历史任务新标题');
    await tester.tap(find.text('应用'));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byType(TaskSharePage),
        matching: find.text('历史任务新标题'),
      ),
      findsOneWidget,
    );
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
    expect(find.byType(TaskReportCard), findsOneWidget);
    expect(find.byIcon(Icons.play_arrow_rounded), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'A long compact task keeps the outcome visible with a single frame',
    (tester) async {
      const goal = '进入手机应用并检查对应页面，然后整理执行结果给我。';
      final bridge = FakeChatBridge()
        ..frame = _png
        ..report = TaskReport(
          available: true,
          goal: goal,
          outcome: '证据不足',
          frames: [_report().frames.last],
        );
      await _mount(tester, bridge, size: const Size(369, 822));
      _emitDone(bridge);
      await tester.pumpAndSettle();

      final goalText = tester.widget<Text>(find.text(goal));
      expect(goalText.maxLines, 2);
      expect(find.text('证据不足').hitTestable(), findsOneWidget);
      expect(find.byIcon(Icons.play_arrow_rounded), findsNothing);
      expect(find.text('第 1 / 1 帧'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('Compact playback controls fit with long frame evidence', (
    tester,
  ) async {
    final bridge = FakeChatBridge()
      ..frame = _png
      ..report = const TaskReport(
        available: true,
        goal: '打开设置',
        outcome: '部分完成',
        frames: [
          ReplayFrame(stepIndex: 0, screenshotId: 1, step: 1, title: '打开设置'),
          ReplayFrame(
            stepIndex: 1,
            screenshotId: 2,
            step: 2,
            title: '点击设置列表中的目标项目',
            failure: '当前页面没有找到对应的控件，已经保留点击之前的画面，请核对页面并确认下一步操作。',
            pointer: ExecutionPointer(
              width: 1200,
              height: 2670,
              method: 'accessibility',
              bounds: [110, 230, 1005, 1190],
            ),
          ),
        ],
      );
    await _mount(tester, bridge, size: const Size(369, 822));
    _emitDone(bridge);
    await tester.pumpAndSettle();

    final frame = find.byKey(const ValueKey('completed-playback-frame'));
    final controls = find.byKey(const ValueKey('playback-controls'));
    final controlsRect = tester.getRect(controls);
    expect(
      controlsRect.bottom,
      lessThanOrEqualTo(tester.getRect(frame).bottom),
    );
    expect(
      find.descendant(of: frame, matching: find.byType(Scrollable)),
      findsNothing,
    );
    final play = find.widgetWithIcon(
      MaeveGlassButton,
      Icons.play_arrow_rounded,
    );
    expect(play.hitTestable(), findsOneWidget);
    await tester.tap(play);
    await tester.pump();
    expect(find.text('第 1 / 2 帧'), findsOneWidget);
    expect(tester.getRect(controls), controlsRect);
    expect(tester.takeException(), isNull);
  });
}

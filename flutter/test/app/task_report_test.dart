import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/models/task_report.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/widgets/task_report_view.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

/// 1×1 的透明 PNG:够 Image.memory 解码,不用摆真实截图。
Uint8List get _png => base64Decode(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
);

TaskReport _report({int frames = 3}) => TaskReport.fromMap({
  'available': true,
  'goal': '查一下账户余额',
  'outcome': '办成了',
  'verdict': '核查通过',
  'duration': '12 秒',
  'criteria': ['看到余额数字'],
  'answer': '账户余额 **128 元**。',
  'missingFrames': 1,
  'pages': [
    {'title': '账户余额 128 元', 'stepIndex': 1, 'screenshotId': 2},
  ],
  'stages': [
    {'title': '开始任务', 'stepIndex': 0},
    {'title': '账户余额 128 元', 'stepIndex': 1, 'screenshotId': 2},
  ],
  'frames': [
    for (var i = 0; i < frames; i++)
      {
        'stepIndex': i,
        'screenshotId': i + 1,
        'step': i + 1,
        'after': i.isOdd,
        'durationMs': i.isOdd ? 2400 : 1200,
        'title': '点按',
        if (!i.isOdd)
          'pointer': {
            'x': 301,
            'y': 702,
            'width': 1200,
            'height': 2670,
            'method': 'touch',
            'clicks': 1,
            'screenshotId': i + 1,
          },
      },
  ],
});

void main() {
  /// 挂一个真实的壳,再把二级页推进去:测的就是真机上的那条路径。
  Future<FakeChatBridge> mount(
    WidgetTester tester,
    Widget Function(FakeChatBridge) page,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    final bridge = FakeChatBridge()
      ..frame = _png
      ..report = _report();
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
    AppPage.open(tester.element(find.byType(Scaffold).first), page(bridge));
    await tester.pumpAndSettle();
    return bridge;
  }

  testWidgets('报告页读记录并渲染结局、回答与关键页', (tester) async {
    final bridge = await mount(
      tester,
      (b) => TaskReportPage(bridge: b, runId: 'run-1'),
    );
    expect(bridge.reportRequests, ['run-1']);
    expect(find.text('查一下账户余额'), findsOneWidget);
    expect(find.text('办成了 · 核查通过 · 12 秒'), findsOneWidget);
    expect(find.text('看到余额数字'), findsOneWidget);
    expect(find.text('账户余额 128 元'), findsOneWidget);
    expect(find.text('已跳过 1 张不可用画面。'), findsOneWidget);
    expect(find.text('操作回放'), findsOneWidget);
    expect(find.text('导出'), findsOneWidget);
  });

  testWidgets('导出走系统选择器，成功后如实提示', (tester) async {
    final bridge = await mount(
      tester,
      (b) => TaskReportPage(bridge: b, runId: 'run-1'),
    );
    await tester.tap(find.text('导出'));
    await tester.pumpAndSettle();
    expect(find.text('导出任务报告'), findsOneWidget);
    await tester.tap(find.text('Word · .docx'));
    await tester.pumpAndSettle();
    expect(bridge.exportRequests, ['run-1:word']);
    expect(find.text('报告已保存'), findsOneWidget);
  });

  testWidgets('导出失败时不谎报已保存', (tester) async {
    final bridge = await mount(
      tester,
      (b) => TaskReportPage(bridge: b, runId: 'run-1'),
    );
    bridge.exportState = 'failed';
    await tester.tap(find.text('导出'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Markdown · .md'));
    await tester.pumpAndSettle();
    expect(bridge.exportRequests, ['run-1:markdown']);
    expect(find.text('报告已保存'), findsNothing);
    expect(find.textContaining('没能保存'), findsOneWidget);
  });

  testWidgets('回放逐帧前进，操作前那一帧带真实落点说明', (tester) async {
    final bridge = await mount(
      tester,
      (b) => TaskPlaybackPage(bridge: b, runId: 'run-1', report: _report()),
    );
    expect(find.text('第 1 / 3 帧 · 来自本机记录'), findsOneWidget);
    expect(find.text('操作前'), findsOneWidget);
    expect(find.text('实际触摸 · (301, 702) px'), findsOneWidget);
    expect(bridge.screenRequests.first, 'run-1:1');

    await tester.tap(find.byTooltip('下一帧'));
    await tester.pumpAndSettle();
    expect(find.text('第 2 / 3 帧 · 来自本机记录'), findsOneWidget);
    expect(find.text('操作后'), findsOneWidget);
    expect(bridge.screenRequests, contains('run-1:2'));
  });

  testWidgets('阶段入口直达它自己那张证据画面', (tester) async {
    final bridge = await mount(
      tester,
      (b) => TaskPlaybackPage(bridge: b, runId: 'run-1', report: _report()),
    );
    await tester.tap(find.text('账户余额 128 元'));
    await tester.pumpAndSettle();
    expect(find.text('第 2 / 3 帧 · 来自本机记录'), findsOneWidget);
    expect(bridge.screenRequests, contains('run-1:2'));
  });

  testWidgets('没有画面时回放页只说没有记录，不摆空播放器', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    final bridge = FakeChatBridge();
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
    AppPage.open(
      tester.element(find.byType(Scaffold).first),
      TaskPlaybackPage(
        bridge: bridge,
        runId: 'run-1',
        report: const TaskReport(available: true),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('这次任务没有记录画面。'), findsOneWidget);
    expect(find.byType(Slider), findsNothing);
  });
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';
import 'package:maeve_flutter/widgets/maeve_markdown.dart';
import 'package:maeve_flutter/widgets/execution_view.dart';
import 'package:maeve_flutter/models/execution.dart';

import '../support/fake_chat_bridge.dart';
import '../support/fake_settings_bridge.dart';

void main() {
  Future<MaeveController> mount(
    WidgetTester tester,
    FakeChatBridge bridge,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      MethodChannel(MaeveBridge.entryEvents.name),
      (_) async => null,
    );
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      await bridge.dispose();
      tester.view.reset();
      messenger.setMockMethodCallHandler(
        MethodChannel(MaeveBridge.entryEvents.name),
        null,
      );
      messenger.setMockMethodCallHandler(MaeveBridge.methods, null);
    });
    final c = MaeveController(
      bridge: bridge,
      settingsBridge: FakeSettingsBridge(),
    );
    await tester.pumpWidget(MaeveApp(controller: c));
    await tester.pumpAndSettle();
    return c;
  }

  testWidgets(
    'Completed answer renders Markdown and preserves measured task summary',
    (tester) async {
      final bridge = FakeChatBridge();
      final c = await mount(tester, bridge);
      bridge.emitUi(
        conversationId: 'c1',
        exchanges: [
          FakeChatBridge.exchange(
            '查看今日密码',
            steps: 17,
            elapsedMs: 97599,
            answer: '## 今日密码\n\n**核对完成**\n\n| 地图 | 密码 |\n| --- | --- |\n| 零号大坝 | 2258 |\n\n- 已关闭弹窗\n\n> 来自当前页面\n\n```text\nfinished\n```',
          ),
        ],
      );
      await tester.pumpAndSettle();
      expect(find.text('已完成'), findsOneWidget);
      expect(find.text('17 步'), findsOneWidget);
      expect(find.text('1 分 38 秒'), findsOneWidget);
      expect(c.active.messages.last.elapsedMs, 97599);
      expect(find.text('今日密码'), findsOneWidget);
      expect(find.text('核对完成'), findsOneWidget);
      expect(find.text('2258'), findsOneWidget);
      expect(find.byType(Table), findsOneWidget);
      expect(find.text('**核对完成**'), findsNothing);
      expect(find.text('## 今日密码'), findsNothing);
      final text = tester.widget<Text>(find.text('核对完成'));
      final spans = <TextSpan>[];
      void visit(InlineSpan span) {
        if (span is TextSpan) {
          spans.add(span);
          for (final child in span.children ?? <InlineSpan>[]) {
            visit(child);
          }
        }
      }

      visit(text.textSpan!);
      expect(
        spans.any(
          (s) => s.text == '核对完成' && s.style?.fontWeight == FontWeight.w600,
        ),
        isTrue,
      );
      expect(tester.takeException(), isNull);
    },
  );

  for (final mode in [ThemeMode.light, ThemeMode.dark]) {
    testWidgets('Wide Markdown tables and code fit narrow large-text $mode', (
      tester,
    ) async {
      final bridge = FakeChatBridge();
      final c = await mount(tester, bridge);
      c.setMode(mode);
      tester.view.physicalSize = const Size(320, 568);
      final context = tester.element(
        find.byKey(const ValueKey('message-input')),
      );
      AppPage.open(
        context,
        MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(textScaler: const TextScaler.linear(1.6)),
          child: AppPage.list(
            title: '回答',
            children: const [
              MaeveMarkdown(
                data: '| 型号及说明 | 输入 Token | 输出 Token | 缓存命中 |\n| --- | --- | --- | --- |\n| 较长的中文型号与推荐说明 | 123456 | 9876 | 65432 |\n\n```text\na_very_long_code_line_that_must_scroll_horizontally_without_overflow_012345678901234567890\n```',
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(Table), findsOneWidget);
      final horizontal = tester
          .widgetList<SingleChildScrollView>(find.byType(SingleChildScrollView))
          .where((w) => w.scrollDirection == Axis.horizontal);
      expect(horizontal.length, greaterThanOrEqualTo(2));
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets(
    'Markdown web links open only after tapping and reject non-web schemes',
    (tester) async {
      await mount(tester, FakeChatBridge());
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(MaeveBridge.methods, (call) async {
            calls.add(call);
            return true;
          });
      AppPage.open(
        tester.element(find.byKey(const ValueKey('message-input'))),
        AppPage.list(
          title: '回答',
          children: const [
            MaeveMarkdown(
              data: '[官方指南](https://platform.stepfun.com/docs/zh/)\n\n[不可执行](javascript:alert(1))',
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      expect(calls, isEmpty);
      await tester.tap(find.text('官方指南'));
      await tester.pumpAndSettle();
      expect(calls.single.method, 'open_url');
      expect(calls.single.arguments, {
        'url': 'https://platform.stepfun.com/docs/zh/',
      });
      await tester.tap(find.text('不可执行'));
      await tester.pumpAndSettle();
      expect(calls.length, 1);
    },
  );

  testWidgets('About settings section omits the license entry', (tester) async {
    await mount(tester, FakeChatBridge());
    AppPage.open(
      tester.element(find.byKey(const ValueKey('message-input'))),
      const SettingsHubPage(),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('关于 Maeve'),
      250,
      scrollable: find
          .descendant(
            of: find.byType(SettingsHubPage),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    await tester.pumpAndSettle();
    expect(find.text('关于 Maeve'), findsOneWidget);
    expect(find.text('开源许可'), findsNothing);
  });

  testWidgets(
    'Recorded model reply renders Markdown through the real record page',
    (tester) async {
      final bridge = FakeChatBridge()
        ..record = const RunDetails(
          available: true,
          goal: '查看任务结果',
          outcome: '已完成',
          duration: '1 分 38 秒',
          steps: [
            ExecutionStep(
              step: 17,
              title: '模型回复',
              reason: '## 核对结果\n\n**内容已确认**',
            ),
          ],
        );
      await mount(tester, bridge);
      AppPage.open(
        tester.element(find.byKey(const ValueKey('message-input'))),
        RunDetailsPage(bridge: bridge, runId: 'actual-record'),
      );
      await tester.pumpAndSettle();
      expect(bridge.detailsRequests, ['actual-record']);
      expect(find.text('核对结果'), findsOneWidget);
      expect(find.text('内容已确认'), findsOneWidget);
      expect(find.text('**内容已确认**'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
}

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/app/onboarding.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/ui/more/more_pages.dart';

/// 不需要真机也能验的页面:引导、终端、网页壳、社区、分享。
void main() {
  Widget app(Widget child) => MaterialApp(
    theme: maeveTheme(brightness: Brightness.dark),
    builder: (context, child) => MaeveThemeScope(
      brightness: Brightness.dark,
      accent: deriveAccent(MaeveAccentPreset.reverie),
      child: child ?? const SizedBox.shrink(),
    ),
    home: MaeveScope(controller: MaeveController(), child: child),
  );

  Future<void> pump(WidgetTester tester, Widget child) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(app(child));
    await tester.pumpAndSettle();
  }

  testWidgets('引导:只数要回答的问题,四步走完能进主界面', (tester) async {
    await pump(tester, const OnboardingPage());

    expect(find.text('SHE RUNS THE LOOP'), findsOneWidget);
    expect(find.text('开始'), findsOneWidget);

    await tester.tap(find.text('开始'));
    await tester.pumpAndSettle();
    expect(find.text('让它在哪儿跑'), findsOneWidget);
    expect(find.text('1 / 4'), findsOneWidget);
    expect(find.text('本机'), findsOneWidget);
    expect(find.text('远程主机'), findsOneWidget);

    await tester.tap(find.text('继续'));
    await tester.pumpAndSettle();
    expect(find.text('看起来像什么'), findsOneWidget);
    await tester.tap(find.text('黄铜'));
    await tester.pumpAndSettle();
    expect(find.text('2 / 4'), findsOneWidget);

    await tester.tap(find.text('继续'));
    await tester.pumpAndSettle();
    expect(find.text('要不要让它看见屏幕'), findsOneWidget);
    await tester.tap(find.text('稍后再说'));
    await tester.pumpAndSettle();
    expect(find.text('3 / 4'), findsOneWidget);

    await tester.tap(find.text('继续'));
    await tester.pumpAndSettle();
    expect(find.text('用自己的密钥'), findsOneWidget);
    await tester.tap(find.text('跳过'));
    await tester.pumpAndSettle();
    expect(find.text('4 / 4'), findsOneWidget);

    await tester.tap(find.text('继续'));
    await tester.pumpAndSettle();
    expect(find.text('准备好了'), findsOneWidget);
    expect(find.text('进入 Maeve'), findsOneWidget);
  });

  testWidgets('终端:输入命令有回应,并明确说没执行', (tester) async {
    await pump(tester, const TerminalPage());
    expect(find.text('终端'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('terminal-input')),
      'ls -la',
    );
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    expect(find.text('\$ ls -la'), findsOneWidget);
    expect(find.textContaining('没有真的跑'), findsOneWidget);
  });

  testWidgets('网页版:壳做好了,内容等 WebView 接上', (tester) async {
    await pump(tester, const WebViewPage());
    expect(find.text('网页版'), findsWidgets);
    expect(find.text('maeve.local/session'), findsOneWidget);
    expect(find.text('WebView 还没接上'), findsOneWidget);
  });

  testWidgets('社区:三个入口都在,点击只说明要走桥', (tester) async {
    await pump(tester, const CommunityPage());
    expect(find.text('讨论区'), findsOneWidget);
    expect(find.text('更新日志'), findsOneWidget);
    expect(find.text('反馈问题'), findsOneWidget);

    await tester.tap(find.text('讨论区'));
    await tester.pumpAndSettle();
    expect(find.textContaining('平台桥接'), findsOneWidget);
  });

  testWidgets('分享:能从会话详情打开,复制项可用', (tester) async {
    final controller = MaeveController();
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveTheme(brightness: Brightness.dark),
        builder: (context, child) => MaeveThemeScope(
          brightness: Brightness.dark,
          accent: deriveAccent(MaeveAccentPreset.reverie),
          child: child ?? const SizedBox.shrink(),
        ),
        home: MaeveScope(
          controller: controller,
          child: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: FilledButton(
                  onPressed: () => showShareSheet(context, controller),
                  child: const Text('分享'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('分享'));
    await tester.pumpAndSettle();

    expect(find.text('分享这段对话'), findsOneWidget);
    expect(find.text('复制全文'), findsOneWidget);
    expect(find.text('复制为 Markdown'), findsOneWidget);
    expect(find.text('系统分享'), findsOneWidget);
  });
}

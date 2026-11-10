import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/app/onboarding.dart';
import 'package:maeve_flutter/design/maeve_tokens.dart';
import 'package:maeve_flutter/main.dart';
import 'package:maeve_flutter/ui/more/more_pages.dart';

/// 引导、分享、终端、社区这些"不需要真机也能验"的页面。
void main() {
  Future<void> pump(WidgetTester tester, Widget child) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveThemeForTest,
        home: MaeveScope(
          controller: MaeveController(),
          child: Builder(builder: (context) => child),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('引导:只数要回答的问题,四步走完能进主界面', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaeveApp());
    await tester.pumpAndSettle();

    // 从"关于 → 重新查看首次引导"的路径直接进引导页。
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveThemeForTest,
        home: MaeveScope(
          controller: MaeveController(),
          child: const OnboardingPage(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('有声音的宿主'), findsWidgets);
    expect(find.text('开始'), findsOneWidget);

    await tester.tap(find.text('开始'));
    await tester.pumpAndSettle();
    // 运行时是第 1 个要回答的问题,计数从 1 开始(默认本机)。
    expect(find.text('让它在哪儿跑'), findsOneWidget);
    expect(find.text('1 / 4'), findsOneWidget);
    expect(find.text('本机'), findsOneWidget);
    expect(find.text('远程主机'), findsOneWidget);

    await tester.tap(find.text('继续'));
    await tester.pumpAndSettle();
    expect(find.text('看起来像什么'), findsOneWidget);
    // 选一套配色,这一步就答完了。
    await tester.tap(find.text('松绿'));
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
    expect(find.text('网页版'), findsOneWidget);
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

  testWidgets('分享:会话详情里能打开,复制项可用', (tester) async {
    final controller = MaeveController();
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveThemeForTest,
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

/// 测试用的主题:雾紫 + 允许模糊。
final maeveThemeForTest = maeveTheme(Brightness.dark);

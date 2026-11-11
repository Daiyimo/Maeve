import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/main.dart';
import 'package:maeve_flutter/app/quick_panel.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/settings/appearance_page.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';

import 'support/fake_chat_bridge.dart';

import 'package:maeve_flutter/widgets/maeve_mark.dart';

/// 壳层行为。行为断言与上一版一一对应;视觉断言按新设计更新(理由随断言写在注释里)。
void main() {
  Future<void> pumpApp(
    WidgetTester tester, {
    Size? size,
    MaeveController? controller,
  }) async {
    tester.view.physicalSize = size ?? const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final entryChannel = MethodChannel(MaeveBridge.entryEvents.name);
    messenger.setMockMethodCallHandler(entryChannel, (_) async => null);
    final bridge = controller == null ? FakeChatBridge() : null;
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      await bridge?.dispose();
      messenger.setMockMethodCallHandler(entryChannel, null);
    });
    await tester.pumpWidget(
      MaeveApp(controller: controller ?? MaeveController(bridge: bridge)),
    );
    await tester.pumpAndSettle();
  }

  /// 抽屉是否真的能点到:关着时它整体平移/裁到屏幕外,点不到。
  bool drawerOnScreen(WidgetTester tester) => find
      .byKey(const ValueKey('new-conversation'))
      .hitTestable()
      .evaluate()
      .isNotEmpty;

  /// 把某一行滚到**顶栏下面、看得见**的位置:整条玻璃顶栏会挡住滚动内容,
  /// 停在它底下的行点不到。
  Future<void> scrollTo(
    WidgetTester tester,
    Finder target, {
    required Finder page,
  }) async {
    final scrollable = find
        .descendant(of: page, matching: find.byType(Scrollable))
        .first;
    await tester.scrollUntilVisible(target, 240, scrollable: scrollable);
    await tester.pumpAndSettle();
    // 让行落到安全区里(顶栏 ~100,底部留 80)。
    for (var i = 0; i < 8; i++) {
      await tester.pumpAndSettle();
      final rect = tester.getRect(target);
      final screen =
          tester.view.physicalSize.height / tester.view.devicePixelRatio;
      final header = find.descendant(of: page, matching: find.byType(TopBar));
      final top = tester.getRect(header).bottom + 12;
      if (rect.top < top) {
        await tester.drag(scrollable, const Offset(0, 60));
      } else if (rect.bottom > screen - 80) {
        await tester.drag(scrollable, const Offset(0, -60));
      } else {
        break;
      }
      await tester.pumpAndSettle();
    }
    expect(
      target.hitTestable(),
      findsOneWidget,
      reason: 'The row must be reachable below the page header.',
    );
  }

  Future<void> swipeFromRight(WidgetTester tester) async {
    final size = tester.view.physicalSize / tester.view.devicePixelRatio;
    await tester.timedDragFrom(
      Offset(size.width - 4, size.height * .46),
      Offset(-size.width * .68, 0),
      const Duration(milliseconds: 260),
    );
    await tester.pumpAndSettle();
  }

  for (final (size, mode, scale) in [
    (const Size(320, 568), ThemeMode.dark, 1.0),
    (const Size(412, 915), ThemeMode.light, 1.0),
    (const Size(412, 915), ThemeMode.dark, 1.6),
    (const Size(1200, 900), ThemeMode.light, 1.0),
  ]) {
    testWidgets('Chat remains usable at $size / $mode / text $scale', (
      tester,
    ) async {
      tester.platformDispatcher.textScaleFactorTestValue = scale;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      final bridge = FakeChatBridge();
      addTearDown(bridge.dispose);
      await pumpApp(
        tester,
        size: size,
        controller: MaeveController(bridge: bridge)..mode = mode,
      );
      expect(tester.takeException(), isNull);
      final input = find.byKey(const ValueKey('message-input'));
      await tester.enterText(input, '帮我整理今天的安排');
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('send-message')).hitTestable(),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('send-message')));
      await tester.pumpAndSettle();
      expect(bridge.sent, ['帮我整理今天的安排']);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('Keyboard and multiline drafts keep send and attach reachable', (
    tester,
  ) async {
    await pumpApp(tester);
    tester.view.viewInsets = const FakeViewPadding(bottom: 300);
    await tester.pump();
    await tester.enterText(
      find.byKey(const ValueKey('message-input')),
      '第一行\n第二行\n第三行',
    );
    await tester.pumpAndSettle();
    final send = find.byKey(const ValueKey('send-message'));
    expect(send.hitTestable(), findsOneWidget);
    expect(find.byTooltip('模型配置').hitTestable(), findsOneWidget);
    expect(
      tester.getRect(send).bottom,
      lessThanOrEqualTo(
        tester.view.physicalSize.height - tester.view.viewInsets.bottom,
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('聊天页:整条玻璃顶栏 + 纸条输入条 + 空状态示例', (tester) async {
    final bridge = FakeChatBridge(
      conversations: [FakeChatBridge.conversation('c1', '选择之道', pinned: true)],
    );
    await pumpApp(tester, controller: MaeveController(bridge: bridge));
    // 顶栏只说两件事:这是哪个对话 / 现在什么状态(一行,不再叠等宽眉标)。
    expect(find.text('maeve'), findsOneWidget);
    expect(find.text('本机 · 就绪'), findsOneWidget);
    expect(find.text('HOST · READY'), findsNothing);
    // A7:窄屏也有可见的快捷面板入口(不靠边缘手势)。
    expect(find.byTooltip('快捷面板'), findsOneWidget);
    expect(find.byTooltip('打开对话列表'), findsOneWidget);
    expect(find.byKey(const ValueKey('message-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('send-message')), findsOneWidget);
    // 空状态:一句展示字 + 一张示例卡(断句由设计给,断在逗号后)。
    // 打字机入场由 pumpAndSettle 推到完成,所以这里拿到整句。
    expect(find.text('下一幕，\n由你决定。'), findsOneWidget);
    expect(find.byKey(const ValueKey('home-prompt-0')), findsOneWidget);
  });

  testWidgets('发送:宿主应答回来后出现你的气泡、过程,然后是回答', (tester) async {
    final bridge = FakeChatBridge(
      conversations: [FakeChatBridge.conversation('c1', '选择之道')],
    );
    await pumpApp(tester, controller: MaeveController(bridge: bridge));
    await tester.enterText(
      find.byKey(const ValueKey('message-input')),
      '帮我理一下这件事',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();

    // 话真的转给了宿主(不再是本地假回复)
    expect(bridge.sent, ['帮我理一下这件事']);

    // 宿主回报在跑:气泡 + 过程行
    bridge.emitUi(
      conversationId: 'c1',
      composer: 'busy',
      exchanges: [
        FakeChatBridge.exchange(
          '帮我理一下这件事',
          status: 'working',
          steps: 3,
          line: '正在走这一步',
          live: true,
        ),
      ],
    );
    await tester.pump();
    expect(find.text('帮我理一下这件事'), findsOneWidget);
    expect(find.text('正在走这一步'), findsWidgets);
    expect(
      tester
          .widgetList<MaeveMark>(find.byType(MaeveMark))
          .where((mark) => mark.state == MarkState.running),
      isNotEmpty,
    );

    // 宿主回报完成:回答 + 步数小结
    bridge.emitUi(
      conversationId: 'c1',
      exchanges: [
        FakeChatBridge.exchange(
          '帮我理一下这件事',
          answer: '我收到了，接下来交给你。',
          steps: 3,
          runId: 'r1',
        ),
      ],
    );
    await tester.pumpAndSettle();
    expect(find.text('我收到了，接下来交给你。'), findsOneWidget);
    expect(find.text('思考了 3 步'), findsOneWidget);
    expect(
      tester
          .widgetList<MaeveMark>(find.byType(MaeveMark))
          .where((mark) => mark.state == MarkState.running),
      isEmpty,
    );
  });

  testWidgets('停止按钮取消这一轮', (tester) async {
    final bridge = FakeChatBridge(
      conversations: [FakeChatBridge.conversation('c1', '选择之道')],
    );
    await pumpApp(tester, controller: MaeveController(bridge: bridge));
    await tester.enterText(find.byKey(const ValueKey('message-input')), '请停下');
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();
    expect(bridge.sent, ['请停下']);

    // 宿主回报在跑:过程行 + 停止键
    bridge.emitUi(
      conversationId: 'c1',
      composer: 'busy',
      exchanges: [
        FakeChatBridge.exchange(
          '请停下',
          status: 'working',
          steps: 1,
          line: '正在走这一步',
          live: true,
        ),
      ],
    );
    await tester.pump();
    expect(find.text('正在走这一步'), findsWidgets);

    await tester.tap(find.byTooltip('停止生成'));
    await tester.pump();
    // 停止真的转给了宿主
    expect(bridge.cancelCalls, 1);

    // 宿主回报已停:过程行消失,也不会再冒出回答
    bridge.emitUi(
      conversationId: 'c1',
      exchanges: [
        FakeChatBridge.exchange('请停下', status: 'cancelled', steps: 1),
      ],
    );
    await tester.pumpAndSettle();
    expect(find.text('正在走这一步'), findsNothing);
    expect(find.text('我收到了，接下来交给你。'), findsNothing);
  });

  testWidgets('空状态示例点一下直接发出去', (tester) async {
    final bridge = FakeChatBridge();
    await pumpApp(tester, controller: MaeveController(bridge: bridge));
    await tester.tap(find.byKey(const ValueKey('home-prompt-0')));
    await tester.pump();
    // 点示例 = 把示例当话发给宿主
    expect(bridge.sent, ['帮我理清一个决定']);
    // 宿主把这一轮回报回来后,气泡出现
    bridge.emitUi(
      conversationId: 'n1',
      exchanges: [FakeChatBridge.exchange('帮我理清一个决定', answer: '好，从这里开始。')],
    );
    await tester.pumpAndSettle();
    expect(find.text('帮我理清一个决定'), findsWidgets);
  });

  testWidgets('长按消息可以引用,引用随下一条一起发出去', (tester) async {
    final bridge = FakeChatBridge(
      conversations: [FakeChatBridge.conversation('c1', '选择之道')],
    );
    await pumpApp(tester, controller: MaeveController(bridge: bridge));
    // 先有一轮真实回答(宿主回报回来的)
    bridge.emitUi(
      conversationId: 'c1',
      exchanges: [
        FakeChatBridge.exchange('第一句', answer: '我收到了，接下来交给你。', steps: 3),
      ],
    );
    await tester.pumpAndSettle();

    await tester.longPress(find.text('我收到了，接下来交给你。'));
    await tester.pumpAndSettle();
    expect(find.text('Maeve 的回复'), findsOneWidget);

    await tester.tap(find.text('引用'));
    await tester.pumpAndSettle();
    expect(find.bySemanticsLabel('取消引用'), findsOneWidget);

    await tester.enterText(find.byKey(const ValueKey('message-input')), '接着这句');
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();

    // 引用随下一句一起转给了宿主(宿主按 QuoteMessage 格式拼进话里),
    // 发出去后壳上的引用 chip 清掉。
    expect(bridge.sent, ['接着这句']);
    expect(bridge.quotes.last, '我收到了，接下来交给你。');
    expect(find.bySemanticsLabel('取消引用'), findsNothing);

    // 宿主持久化的话带着引用块回报回来时,壳把它拆回 引用块 + 正文渲染。
    bridge.emitUi(
      conversationId: 'c1',
      exchanges: [
        FakeChatBridge.exchange('第一句', answer: '我收到了，接下来交给你。', steps: 3),
        FakeChatBridge.exchange(
          '> [Quoting your earlier reply]\n> 我收到了，接下来交给你。\n\n接着这句',
        ),
      ],
    );
    await tester.pumpAndSettle();
    expect(find.text('我收到了，接下来交给你。'), findsNWidgets(2));
  });

  testWidgets('抽屉:分组、搜索、新对话', (tester) async {
    final bridge = FakeChatBridge(
      conversations: [
        FakeChatBridge.conversation('c1', '选择之道', pinned: true),
        FakeChatBridge.conversation('c2', '搭一个安静的工作台'),
        FakeChatBridge.conversation('c3', '新的开始'),
      ],
    );
    await pumpApp(tester, controller: MaeveController(bridge: bridge));
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('new-conversation')), findsOneWidget);
    expect(find.text('置顶'), findsWidgets);
    // 真实数据没有"文件夹"这一层:分组是 置顶 → 按天。
    expect(find.text('搭一个安静的工作台'), findsOneWidget);
    expect(find.text('新的开始'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('conversation-search')),
      '安静',
    );
    await tester.pumpAndSettle();
    expect(find.text('搭一个安静的工作台'), findsOneWidget);
    expect(find.text('新的开始'), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('conversation-search')),
      'no such conversation',
    );
    await tester.pumpAndSettle();
    expect(find.text('没有匹配的对话'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('conversation-search')),
      '',
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('new-conversation')));
    await tester.pumpAndSettle();
    // 新对话转发给宿主;宿主还没给出 id,壳回到空态(标题回落成 App 名)。
    expect(bridge.newCalls, 1);
    expect(find.text('搭一个安静的工作台'), findsNothing);
    expect(find.text('下一幕，\n由你决定。'), findsOneWidget);
  });

  testWidgets('会话置顶走宿主，删除必须确认后才提交', (tester) async {
    final bridge = FakeChatBridge(
      conversations: [FakeChatBridge.conversation('c1', '保留的重要对话')],
    );
    addTearDown(bridge.dispose);
    await pumpApp(tester, controller: MaeveController(bridge: bridge));
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await tester.longPress(find.text('保留的重要对话'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('置顶').last);
    await tester.pumpAndSettle();
    expect(bridge.pinned, {'c1': true});

    Future<void> requestDelete() async {
      await tester.longPress(find.text('保留的重要对话'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('删除'));
      await tester.pumpAndSettle();
    }

    await requestDelete();
    expect(bridge.deleted, isEmpty);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(bridge.deleted, isEmpty);
    expect(find.text('保留的重要对话'), findsOneWidget);
    await requestDelete();
    await tester.tap(find.widgetWithText(TextButton, '删除'));
    await tester.pumpAndSettle();
    expect(bridge.deleted, ['c1']);
    expect(find.text('保留的重要对话'), findsNothing);
  });

  testWidgets('抽屉遮罩点一下关掉,不换页', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    expect(drawerOnScreen(tester), isTrue);

    final scrim = tester.getRect(find.byKey(const ValueKey('shell-scrim')));
    await tester.tapAt(Offset(scrim.right - 24, scrim.center.dy));
    await tester.pumpAndSettle();
    expect(drawerOnScreen(tester), isFalse);
    expect(find.text('下一幕，\n由你决定。'), findsOneWidget);
  });

  testWidgets('A7:窄屏靠顶栏按钮打开快捷面板', (tester) async {
    await pumpApp(tester, size: const Size(412, 915));
    await tester.tap(find.byTooltip('快捷面板'));
    await tester.pumpAndSettle();
    // The sheet samples once; scrolling feature cards must not resample it.
    expect(
      find.descendant(
        of: find.byType(QuickPanel),
        matching: find.byType(BackdropFilter),
      ),
      findsOneWidget,
    );
    // 面板标题走人话("快捷"),不再是等宽大写眉标。
    expect(find.text('工作台'), findsOneWidget);
    expect(find.text('系统授权'), findsOneWidget);
  });

  testWidgets('两个抽屉互斥:拉开一个,另一个让位', (tester) async {
    await pumpApp(tester, size: const Size(412, 915));
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    expect(drawerOnScreen(tester), isTrue);

    await swipeFromRight(tester);
    expect(find.text('工作台'), findsOneWidget);
    expect(drawerOnScreen(tester), isFalse);
  });

  testWidgets('C2:品牌珠、顶栏与输入条的模糊有上限', (tester) async {
    await pumpApp(tester, size: const Size(412, 915));
    // 旧壳是四枚浮动胶囊各建一层;新壳顶栏是**一条**玻璃,输入条一条。
    expect(find.byType(BackdropGroup), findsOneWidget);
    expect(find.byType(BackdropFilter), findsNWidgets(2));

    // 设置页同样是整条顶栏玻璃。
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    expect(find.byType(BackdropFilter), findsNWidgets(1));
  });

  testWidgets('外观页换配色:整棵树换强调色', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    await scrollTo(
      tester,
      find.text('主题与配色'),
      page: find.byType(SettingsHubPage),
    );
    await tester.tap(find.text('主题与配色'));
    await tester.pumpAndSettle();

    expect(find.byType(MaeveAccentGrid), findsOneWidget);
    final before = Theme.of(tester.element(find.byType(MaeveAccentGrid)))
        .colorScheme
        .primary;

    await tester.tap(find.text('丝绒红'));
    await tester.pumpAndSettle();
    final after = Theme.of(tester.element(find.byType(MaeveAccentGrid)))
        .colorScheme
        .primary;
    expect(after, isNot(before));
    expect(after, deriveAccent(MaeveAccentPreset.dolores).darkSeed);
  });

  testWidgets('减少透明效果:整棵树不再建 BackdropFilter', (tester) async {
    await pumpApp(tester);
    expect(find.byType(BackdropFilter), findsWidgets);

    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    await scrollTo(
      tester,
      find.text('透明与动效'),
      page: find.byType(SettingsHubPage),
    );
    await tester.tap(find.text('透明与动效'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('减少透明效果'));
    await tester.pumpAndSettle();

    expect(find.byType(BackdropFilter), findsNothing);

    await tester.tap(find.bySemanticsLabel('返回'));
    await tester.pumpAndSettle();
    await tester.tap(find.bySemanticsLabel('返回'));
    await tester.pumpAndSettle();
    expect(find.byType(BackdropFilter), findsNothing);
    expect(find.text('下一幕，\n由你决定。'), findsOneWidget);
  });

  testWidgets('宽屏:列表常驻,≡ 折叠', (tester) async {
    await pumpApp(tester, size: const Size(1200, 900));
    expect(drawerOnScreen(tester), isTrue);

    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    expect(drawerOnScreen(tester), isFalse);

    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    expect(drawerOnScreen(tester), isTrue);
  });
}

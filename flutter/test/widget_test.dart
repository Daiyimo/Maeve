import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/main.dart';
import 'package:maeve_flutter/ui/phone/phone_control_page.dart';
import 'package:maeve_flutter/ui/settings/appearance_page.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';

/// 壳层行为。行为断言与上一版一一对应;视觉断言按新设计更新(理由随断言写在注释里)。
void main() {
  Future<void> pumpApp(WidgetTester tester, {Size? size}) async {
    if (size != null) {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
    }
    await tester.pumpWidget(const MaeveApp());
    await tester.pumpAndSettle();
  }

  /// 抽屉是否真的能点到:关着时它整体平移/裁到屏幕外,点不到。
  bool drawerOnScreen(WidgetTester tester) => find
      .byKey(const ValueKey('new-conversation'))
      .hitTestable()
      .evaluate()
      .isNotEmpty;

  /// 页面上有"呼吸"的状态点时 pumpAndSettle 永远不会停,用固定帧推进。
  Future<void> settleFrames(WidgetTester tester) async {
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 420));
    await tester.pump(const Duration(milliseconds: 420));
  }

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
      if (rect.top < 110) {
        await tester.drag(scrollable, const Offset(0, 60));
      } else if (rect.bottom > screen - 80) {
        await tester.drag(scrollable, const Offset(0, -60));
      } else {
        break;
      }
      await tester.pumpAndSettle();
    }
  }

  Future<void> swipeFromRight(WidgetTester tester) async {
    await tester.timedDragFrom(
      const Offset(408, 420),
      const Offset(-280, 0),
      const Duration(milliseconds: 260),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('聊天页:整条玻璃顶栏 + 纸条输入条 + 空状态示例', (tester) async {
    await pumpApp(tester);
    // 顶栏三段:等宽眉标 / 标题 / 状态行。
    expect(find.text('HOST · READY'), findsOneWidget);
    expect(find.text('选择之道'), findsWidgets);
    expect(find.text('本机 · 就绪'), findsOneWidget);
    // A7:窄屏也有可见的快捷面板入口(不靠边缘手势)。
    expect(find.byTooltip('快捷面板'), findsOneWidget);
    expect(find.byTooltip('打开对话列表'), findsOneWidget);
    expect(find.byKey(const ValueKey('message-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('send-message')), findsOneWidget);
    // 空状态:一句展示字 + 三张示例卡。
    expect(find.text('一个念头，从这里开始。'), findsOneWidget);
    expect(find.text('帮我理清一个决定'), findsOneWidget);
  });

  testWidgets('发送:出现你的气泡、活动条,然后是回答', (tester) async {
    await pumpApp(tester);
    await tester.enterText(
      find.byKey(const ValueKey('message-input')),
      '帮我理一下这件事',
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();

    expect(find.text('帮我理一下这件事'), findsOneWidget);
    expect(find.text('正在走这一步'), findsWidgets);

    await tester.pump(const Duration(milliseconds: 800));
    await tester.pumpAndSettle();
    expect(find.text('我收到了，接下来交给你。'), findsOneWidget);
    expect(find.text('思考了 3 步'), findsOneWidget);
  });

  testWidgets('停止按钮取消这一轮', (tester) async {
    await pumpApp(tester);
    await tester.enterText(find.byKey(const ValueKey('message-input')), '请停下');
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();
    expect(find.text('正在走这一步'), findsWidgets);

    await tester.tap(find.byTooltip('停止生成'));
    await tester.pumpAndSettle();
    expect(find.text('正在走这一步'), findsNothing);

    await tester.pump(const Duration(milliseconds: 900));
    await tester.pumpAndSettle();
    expect(find.text('我收到了，接下来交给你。'), findsNothing);
  });

  testWidgets('空状态示例点一下直接发出去', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.text('帮我理清一个决定'));
    await tester.pump();
    expect(find.text('帮我理清一个决定'), findsWidgets);
    await tester.pump(const Duration(milliseconds: 800));
    await tester.pumpAndSettle();
    expect(find.text('我收到了，接下来交给你。'), findsOneWidget);
  });

  testWidgets('长按消息可以引用,引用随下一条一起发出去', (tester) async {
    await pumpApp(tester);
    await tester.enterText(find.byKey(const ValueKey('message-input')), '第一句');
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump(const Duration(milliseconds: 800));
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
    await settleFrames(tester);

    // 引用的原文再出现一次(原回答 + 引用块)。
    expect(find.text('我收到了，接下来交给你。'), findsNWidgets(2));
    expect(find.bySemanticsLabel('取消引用'), findsNothing);
  });

  testWidgets('附件入口列出来源,并说明还要接桥', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.byKey(const ValueKey('attach-file')));
    await tester.pumpAndSettle();
    expect(find.text('添加内容'), findsOneWidget);
    expect(find.text('照片'), findsOneWidget);
    expect(find.text('文件'), findsOneWidget);
    expect(find.text('当前屏幕'), findsOneWidget);
  });

  testWidgets('抽屉:分组、搜索、新对话', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('new-conversation')), findsOneWidget);
    expect(find.text('置顶'), findsWidgets);
    expect(find.text('工作'), findsOneWidget);
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
    expect(find.text('新对话'), findsWidgets);
  });

  testWidgets('抽屉遮罩点一下关掉,不换页', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    expect(drawerOnScreen(tester), isTrue);

    await tester.tap(
      find.byKey(const ValueKey('shell-scrim')),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();
    expect(drawerOnScreen(tester), isFalse);
    expect(find.text('一个念头，从这里开始。'), findsOneWidget);
  });

  testWidgets('A7:窄屏靠顶栏按钮打开快捷面板', (tester) async {
    await pumpApp(tester, size: const Size(412, 915));
    await tester.tap(find.byTooltip('快捷面板'));
    await tester.pumpAndSettle();
    expect(find.text('QUICK'), findsOneWidget);
    expect(find.text('手机操控'), findsOneWidget);
  });

  testWidgets('两个抽屉互斥:拉开一个,另一个让位', (tester) async {
    await pumpApp(tester, size: const Size(412, 915));
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    expect(drawerOnScreen(tester), isTrue);

    await swipeFromRight(tester);
    expect(find.text('QUICK'), findsOneWidget);
    expect(drawerOnScreen(tester), isFalse);
  });

  testWidgets('C2:聊天页只有两处真模糊(顶栏 + 输入条)', (tester) async {
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

  testWidgets('设置 hub:六组卡 + 一览 + 底部版本', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();

    expect(find.text('外观、能力与主机'), findsOneWidget);
    for (final group in ['AI', '能力', '外观', '通用', '主机', '帮助与关于']) {
      await scrollTo(
        tester,
        find.text(group),
        page: find.byType(SettingsHubPage),
      );
      expect(find.text(group), findsOneWidget, reason: group);
    }
    await scrollTo(
      tester,
      find.textContaining('MAEVE 0.1.0'),
      page: find.byType(SettingsHubPage),
    );
    expect(find.textContaining('MAEVE 0.1.0'), findsOneWidget);
    // hub 上不能出现放宽保护或删数据的入口。
    expect(find.textContaining('解除限制'), findsNothing);
    expect(find.textContaining('清除'), findsNothing);
  });

  testWidgets('外观页换配色:整棵树换强调色', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    await scrollTo(tester, find.text('配色'), page: find.byType(SettingsHubPage));
    await tester.tap(find.text('配色'));
    await tester.pumpAndSettle();

    expect(find.byType(MaeveAccentGrid), findsOneWidget);
    final before = Theme.of(tester.element(find.byType(MaeveAccentGrid)))
        .colorScheme
        .primary;

    await tester.tap(find.text('黄铜'));
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
      find.text('动效与性能'),
      page: find.byType(SettingsHubPage),
    );
    await tester.tap(find.text('动效与性能'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('减少透明效果'));
    await tester.pumpAndSettle();

    expect(find.byType(BackdropFilter), findsNothing);

    await tester.tap(find.bySemanticsLabel('返回'));
    await tester.pumpAndSettle();
    await tester.tap(find.bySemanticsLabel('返回'));
    await tester.pumpAndSettle();
    expect(find.byType(BackdropFilter), findsNothing);
    expect(find.text('一个念头，从这里开始。'), findsOneWidget);
  });

  testWidgets('手机操控:首次开启要按住确认', (tester) async {
    await pumpApp(tester, size: const Size(412, 915));
    await tester.tap(find.byTooltip('快捷面板'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('手机操控'));
    await settleFrames(tester);

    expect(find.byType(PhoneControlPage), findsOneWidget);
    expect(find.text('允许 Maeve 操作这台手机'), findsOneWidget);

    await tester.tap(find.text('允许 Maeve 操作这台手机'));
    await settleFrames(tester);
    expect(find.text('开启前，请先看清楚'), findsOneWidget);
    expect(find.text('按住开启'), findsOneWidget);

    // 直接点不会开启。
    await tester.tap(find.text('按住开启'));
    await tester.pump();
    expect(find.text('开启前，请先看清楚'), findsOneWidget);
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

  testWidgets('设置搜索:输入即过滤', (tester) async {
    await pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.settings_outlined));
    await tester.pumpAndSettle();
    await tester.tap(find.text('搜索设置'));
    await tester.pumpAndSettle();

    expect(find.byType(SettingsSearchPage), findsOneWidget);
    await tester.enterText(find.byKey(const ValueKey('settings-search')), '配色');
    await tester.pumpAndSettle();
    expect(find.text('外观'), findsOneWidget);
    expect(find.text('手机操控'), findsNothing);
  });
}

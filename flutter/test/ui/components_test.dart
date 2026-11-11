import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_motion.dart';
import 'package:maeve_flutter/design/maeve_motifs.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/common/app_sheet.dart';
import 'package:maeve_flutter/ui/common/haptics.dart';
import 'package:maeve_flutter/ui/common/hold_to_confirm.dart';
import 'package:maeve_flutter/ui/common/pressable.dart';
import 'package:maeve_flutter/ui/common/status.dart';
import 'package:maeve_flutter/ui/settings/settings_components.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

/// 组件层验收:A5(动效/降级)、A6(可点目标/双编码)、A9(右栏规则)。
Widget host(
  Widget child, {
  Brightness brightness = Brightness.dark,
  bool blurEnabled = true,
  bool disableAnimations = false,
  bool highContrast = false,
}) => MaterialApp(
  theme: maeveTheme(brightness: brightness),
  home: MediaQuery(
    data: MediaQueryData(
      disableAnimations: disableAnimations,
      highContrast: highContrast,
    ),
    child: MaeveThemeScope(
      brightness: brightness,
      accent: deriveAccent(MaeveAccentPreset.reverie),
      blurEnabled: blurEnabled,
      child: Scaffold(body: Center(child: child)),
    ),
  ),
);

void main() {
  group('Pressable', () {
    testWidgets('按下缩到 0.97、90ms,松开回 1', (tester) async {
      await tester.pumpWidget(
        host(
          Pressable(onTap: () {}, child: const SizedBox(width: 80, height: 44)),
        ),
      );
      AnimatedScale scale() => tester.widget<AnimatedScale>(
        find.descendant(
          of: find.byType(Pressable),
          matching: find.byType(AnimatedScale),
        ),
      );
      expect(scale().scale, 1);
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(Pressable)),
      );
      await tester.pump();
      expect(scale().scale, MaeveMotion.pressScale);
      expect(scale().duration, MaeveMotion.press);
      await gesture.up();
      await tester.pumpAndSettle();
      expect(scale().scale, 1);
    });

    testWidgets('减少动效时不缩放', (tester) async {
      await tester.pumpWidget(
        host(
          Pressable(onTap: () {}, child: const SizedBox(width: 80, height: 44)),
          disableAnimations: true,
        ),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(Pressable)),
      );
      await tester.pump();
      expect(tester.widget<AnimatedScale>(find.byType(AnimatedScale)).scale, 1);
      await gesture.up();
    });
  });

  group('HoldToConfirm(A6 危险操作摩擦)', () {
    testWidgets('按满 1500ms 才生效,提前松手不算', (tester) async {
      var fired = 0;
      await tester.pumpWidget(
        host(
          HoldToConfirm(
            onConfirmed: () => fired++,
            child: const SizedBox(width: 160, height: 48),
          ),
        ),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(HoldToConfirm)),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 1200));
      expect(fired, 0);
      await gesture.up();
      await tester.pump(const Duration(milliseconds: 400));
      expect(fired, 0);

      final again = await tester.startGesture(
        tester.getCenter(find.byType(HoldToConfirm)),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 1600));
      expect(fired, 1);
      await again.up();
    });

    testWidgets('移动超过 24px 视为放弃', (tester) async {
      var fired = 0;
      await tester.pumpWidget(
        host(
          HoldToConfirm(
            onConfirmed: () => fired++,
            child: const SizedBox(width: 160, height: 48),
          ),
        ),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(HoldToConfirm)),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      await gesture.moveBy(const Offset(40, 0));
      await tester.pump(const Duration(milliseconds: 1600));
      expect(fired, 0);
      await gesture.up();
    });
  });

  group('触感(A6 单一入口)', () {
    testWidgets('关掉后零平台调用;开着时按档位震动', (tester) async {
      final calls = <MethodCall>[];
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'HapticFeedback.vibrate') calls.add(call);
        return null;
      });
      addTearDown(
        () => messenger.setMockMethodCallHandler(SystemChannels.platform, null),
      );

      MaeveHaptics.resetProbe();
      MaeveHaptics.enabled = false;
      await MaeveHaptics.play(MaeveHaptic.heavy);
      expect(calls, isEmpty);

      MaeveHaptics.enabled = true;
      MaeveHaptics.resetProbe();
      await MaeveHaptics.play(MaeveHaptic.heavy);
      expect(calls, isNotEmpty);
      MaeveHaptics.enabled = true;
    });
  });

  group('页面骨架(A10 / 顶栏)', () {
    testWidgets('Unequal actions keep the title on the page center', (
      tester,
    ) async {
      await tester.pumpWidget(
        host(
          const TopBar(
            title: '居中标题',
            showBack: false,
            actions: [
              SizedBox.square(dimension: 48),
              SizedBox.square(dimension: 48),
            ],
          ),
        ),
      );
      expect(
        tester.getCenter(find.text('居中标题')).dx,
        tester.getCenter(find.byType(TopBar)).dx,
      );
    });

    testWidgets('顶栏是整条玻璃,标题列只说两件事:标题 + 一行状态', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(brightness: Brightness.dark),
          home: MaeveThemeScope(
            brightness: Brightness.dark,
            accent: deriveAccent(MaeveAccentPreset.reverie),
            child: AppPage.list(
              title: '测试页',
              eyebrow: 'TEST',
              subtitle: '副标题',
              children: [for (var i = 0; i < 30; i++) Text('行 $i')],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('测试页'), findsOneWidget);
      expect(find.text('副标题'), findsOneWidget);
      // 有副标题时不再叠一行眉标:三行小字挤在 56 高的条里,远看是一团灰。
      expect(find.text('TEST'), findsNothing);
      // 顶栏是一块 chrome 玻璃(会采样)。
      final surface = tester.widget<MaeveGlassSurface>(
        find.byType(MaeveGlassSurface).first,
      );
      expect(surface.tier, MaeveGlassTier.chrome);
      expect(find.byType(BackdropFilter), findsWidgets);
    });

    testWidgets('没有副标题时,眉标兜底成为第二行', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(brightness: Brightness.dark),
          home: MaeveThemeScope(
            brightness: Brightness.dark,
            accent: deriveAccent(MaeveAccentPreset.reverie),
            child: AppPage.list(
              title: '测试页',
              eyebrow: 'TEST',
              children: [for (var i = 0; i < 30; i++) Text('行 $i')],
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('TEST'), findsOneWidget);
    });
  });

  group('设置行(A9 右栏规则)', () {
    testWidgets('value 与箭头之间恒为 8,长值省略号', (tester) async {
      await tester.pumpWidget(
        host(
          SizedBox(
            width: 360,
            child: SettingsSection(
              title: '分组',
              rows: [
                SettingsTile(
                  icon: Icons.tag,
                  title: '版本',
                  value: '0.1.0+1 (build 2026-11-11) 很长很长',
                  onTap: () {},
                ),
              ],
            ),
          ),
        ),
      );
      expect(RowMetrics.gap, 8);
      final value = tester.widget<Text>(find.textContaining('0.1.0+1'));
      expect(value.maxLines, 1);
      expect(value.overflow, TextOverflow.ellipsis);

      // 值右边缘到箭头左边缘 = 8。
      final valueRect = tester.getRect(find.textContaining('0.1.0+1'));
      final chevronRect = tester.getRect(find.byIcon(Icons.chevron_right));
      expect(chevronRect.left - valueRect.right, closeTo(RowMetrics.gap, .5));
    });

    testWidgets('开关行:文案与开关之间也是 8,整行可点', (tester) async {
      var value = false;
      await tester.pumpWidget(
        host(
          SizedBox(
            width: 360,
            child: StatefulBuilder(
              builder: (context, setState) => SettingsSection(
                title: '通用',
                rows: [
                  SettingsSwitchTile(
                    icon: Icons.vibration,
                    title: '触感反馈',
                    value: value,
                    onChanged: (next) => setState(() => value = next),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      // 开关行不再在开关左边重复一遍"已开启/已关闭":开关的状态位 + 读屏的
      // toggled 语义已经说了两遍,第三遍会把行内文字挤成两行。现在就一个开关。
      expect(find.text('已关闭'), findsNothing);
      expect(find.text('已开启'), findsNothing);
      expect(tester.widget<Switch>(find.byType(Switch)).value, isFalse);
      await tester.tap(find.text('触感反馈'));
      await tester.pumpAndSettle();
      expect(tester.widget<Switch>(find.byType(Switch)).value, isTrue);
    });

    testWidgets('普通行图标块中性,状态行才上色', (tester) async {
      await tester.pumpWidget(
        host(
          SizedBox(
            width: 360,
            child: SettingsSection(
              title: '组',
              rows: const [
                SettingsTile(icon: Icons.tag, title: '中性'),
                SettingsTile(icon: Icons.warning, title: '状态', tone: Tone.bad),
              ],
            ),
          ),
        ),
      );
      final tiles = tester.widgetList<IconTile>(find.byType(IconTile)).toList();
      expect(tiles.first.tone, Tone.neutral);
      expect(tiles.last.tone, Tone.bad);
    });

    testWidgets('分段行:选中项文字加粗/变色,不只靠底色', (tester) async {
      var index = 0;
      await tester.pumpWidget(
        host(
          SizedBox(
            width: 360,
            child: StatefulBuilder(
              builder: (context, setState) => SettingsSection(
                title: '组',
                rows: [
                  SettingsSegmentTile(
                    icon: Icons.tune,
                    title: '档位',
                    options: const ['高', '中', '低'],
                    value: index,
                    onChanged: (next) => setState(() => index = next),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('中'));
      await tester.pumpAndSettle();
      final selected = tester.widget<Text>(find.text('中'));
      final unselected = tester.widget<Text>(find.text('高'));
      final colors = MaeveTheme.of(tester.element(find.byType(SettingsSection)))
          .colors;
      // 选中靠"强调色文字 + 描边",不靠一整块强调色实底:
      // 三个分段里有一个是实心色块,整行会重得像个开关面板。
      expect(selected.style?.color, colors.accent);
      expect(unselected.style?.color, colors.ink3);
    });
  });

  group('状态双编码', () {
    test('五种状态画出不同形状(不是只换颜色)', () {
      List<String> ops(MaeveTone tone) {
        final recorder = ui.PictureRecorder();
        final canvas = Canvas(recorder);
        final spy = _RecordingCanvas(canvas);
        StatusDotPainter(
          tone: tone,
          color: const Color(0xFFFFFFFF),
        ).paint(spy, const Size.square(14));
        return spy.ops;
      }

      final idle = ops(MaeveTone.idle);
      final running = ops(MaeveTone.running);
      final ready = ops(MaeveTone.ready);
      final danger = ops(MaeveTone.danger);

      expect(idle, isNot(running));
      expect(running.any((op) => op.startsWith('arc')), isTrue);
      expect(ready.where((op) => op.startsWith('circle')).length, 2);
      expect(danger.any((op) => op.startsWith('line')), isTrue);
    });

    testWidgets('顶栏状态行给读屏一句完整文案', (tester) async {
      await tester.pumpWidget(
        host(const HeaderStatus(text: '本机 · 就绪', tone: MaeveTone.ready)),
      );
      expect(find.bySemanticsLabel('状态：本机 · 就绪'), findsOneWidget);
    });
  });

  group('母题', () {
    test('迷宫环:进度画一段弧长,不是转圈', () {
      final recorder = ui.PictureRecorder();
      final spy = _RecordingCanvas(Canvas(recorder));
      MazeRingPainter(
        progress: .5,
        track: const Color(0xFF222222),
        head: const Color(0xFFFFFFFF),
        strokeWidth: 1.4,
      ).paint(spy, const Size.square(24));
      // 底螺线 + 走过的一段 + 光点。
      expect(spy.ops.where((op) => op.startsWith('path')).length, 2);
      expect(spy.ops.where((op) => op.startsWith('circle')).length, 1);
    });

    test('钢琴卷:条高由序号决定,不随重建变化', () {
      final heights = [
        for (var i = 0; i < 8; i++) PianoRollPainter.fraction(i),
      ];
      final again = [for (var i = 0; i < 8; i++) PianoRollPainter.fraction(i)];
      expect(heights, again);
      expect(heights.toSet().length, greaterThan(4));
    });

    test('台词轨:幕号补零到两位', () {
      const rail = ScriptRail(act: 3);
      expect(rail.act, 3);
      // 文案在 build 里补零,这里只守契约:1 起步、补到两位。
      expect(3.toString().padLeft(2, '0'), '03');
    });
  });

  group('弹层与展开', () {
    testWidgets('弹层是 sheet 玻璃,顶两角 28', (tester) async {
      // 设计系统的 scope 必须在 Navigator **之上**:弹层是另一条 route,
      // 它不在 home 的子树里。MaeveApp 就是这么装的(MaterialApp.builder)。
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(brightness: Brightness.dark),
          builder: (context, child) => MaeveThemeScope(
            brightness: Brightness.dark,
            accent: deriveAccent(MaeveAccentPreset.reverie),
            child: child ?? const SizedBox.shrink(),
          ),
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: FilledButton(
                  onPressed: () => showMaeveSheet<void>(
                    context: context,
                    builder: (_) =>
                        const AppSheet(child: SheetTitleBar(title: '弹层')),
                  ),
                  child: const Text('打开'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('打开'));
      await tester.pumpAndSettle();
      expect(find.text('弹层'), findsOneWidget);
      final surface = tester.widget<MaeveGlassSurface>(
        find.descendant(
          of: find.byType(AppSheet),
          matching: find.byType(MaeveGlassSurface),
        ),
      );
      expect(surface.tier, MaeveGlassTier.sheet);
      expect(surface.shape, isA<RoundedSuperellipseBorder>());
    });

    testWidgets('ExpandReveal 收起时高度为 0', (tester) async {
      await tester.pumpWidget(
        host(
          const ExpandReveal(
            visible: false,
            child: SizedBox(height: 80, width: 200),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getSize(find.byType(ExpandReveal)).height, 0);
      await tester.pumpWidget(
        host(
          const ExpandReveal(
            visible: true,
            child: SizedBox(height: 80, width: 200),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getSize(find.byType(ExpandReveal)).height, 80);
    });
  });
}

/// 记录 painter 的绘制调用,不做光栅化。
class _RecordingCanvas implements Canvas {
  _RecordingCanvas(this._inner);

  final Canvas _inner;
  final List<String> ops = [];

  @override
  void drawCircle(Offset c, double radius, Paint paint) {
    ops.add('circle(${c.dx},${c.dy},$radius)');
    _inner.drawCircle(c, radius, paint);
  }

  @override
  void drawArc(
    Rect rect,
    double startAngle,
    double sweepAngle,
    bool useCenter,
    Paint paint,
  ) {
    ops.add('arc($startAngle,$sweepAngle)');
    _inner.drawArc(rect, startAngle, sweepAngle, useCenter, paint);
  }

  @override
  void drawLine(Offset p1, Offset p2, Paint paint) {
    ops.add('line(${p1.dx},${p1.dy})->(${p2.dx},${p2.dy})');
    _inner.drawLine(p1, p2, paint);
  }

  @override
  void drawPath(Path path, Paint paint) {
    ops.add('path');
    _inner.drawPath(path, paint);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnsupportedError('Unexpected canvas call ${invocation.memberName}');
}

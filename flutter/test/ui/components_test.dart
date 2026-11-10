import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_motion.dart';
import 'package:maeve_flutter/design/maeve_tokens.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/ui/common/app_sheet.dart';
import 'package:maeve_flutter/ui/common/haptics.dart';
import 'package:maeve_flutter/ui/common/hold_to_confirm.dart';
import 'package:maeve_flutter/ui/common/pressable.dart';
import 'package:maeve_flutter/ui/common/status.dart';
import 'package:maeve_flutter/ui/settings/settings_components.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

/// 第 6 节 C2/C3/C8/C11/C12 里能用无头测试守住的部分。
Widget host(Widget child, {Brightness brightness = Brightness.dark}) =>
    MaterialApp(
      theme: maeveTheme(brightness),
      home: Scaffold(body: Center(child: child)),
    );

void main() {
  group('Pressable(C5 按压)', () {
    testWidgets('按下缩到 0.97,松开回 1', (tester) async {
      await tester.pumpWidget(
        host(
          Pressable(onTap: () {}, child: const SizedBox(width: 80, height: 40)),
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

    testWidgets('减少动画时不缩放', (tester) async {
      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(disableAnimations: true),
          child: host(
            Pressable(
              onTap: () {},
              child: const SizedBox(width: 80, height: 40),
            ),
          ),
        ),
      );
      final gesture = await tester.startGesture(
        tester.getCenter(find.byType(Pressable)),
      );
      await tester.pump();
      final scale = tester.widget<AnimatedScale>(find.byType(AnimatedScale));
      expect(scale.scale, 1);
      await gesture.up();
    });
  });

  group('HoldToConfirm(C12 危险操作摩擦)', () {
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
      await tester.pump(const Duration(milliseconds: 1200));
      expect(fired, 0);

      // 中途松手:进度回退,不生效。
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

    testWidgets('手指移动超过 24px 视为放弃', (tester) async {
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
      await tester.pump(const Duration(milliseconds: 400));
      await gesture.moveBy(const Offset(40, 0));
      await tester.pump(const Duration(milliseconds: 1600));
      expect(fired, 0);
      await gesture.up();
    });
  });

  group('触感入口(C8)', () {
    testWidgets('关掉后一次平台调用都不发', (tester) async {
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

  group('AppPage(C11 顶渐隐)', () {
    testWidgets('静止不出现渐隐,滚下去才出现', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(Brightness.dark),
          home: AppPage.list(
            title: '测试页',
            children: [
              for (var i = 0; i < 40; i++)
                SizedBox(height: 60, child: Text('行 $i')),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();

      double fadeOpacity() {
        final finder = find.descendant(
          of: find.byType(AppPage),
          matching: find.byType(AnimatedOpacity),
        );
        return tester.widget<AnimatedOpacity>(finder.first).opacity;
      }

      expect(fadeOpacity(), 0);
      expect(find.text('测试页'), findsOneWidget);

      await tester.drag(find.byType(ListView), const Offset(0, -300));
      await tester.pumpAndSettle();
      expect(fadeOpacity(), 1);
    });
  });

  group('设置组件', () {
    testWidgets('一张卡里所有行共享组色相', (tester) async {
      const hue = Color(0xFF34766F);
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(Brightness.dark),
          home: Scaffold(
            body: SettingsSection(
              title: '能力',
              hue: hue,
              rows: const [
                SettingsTile(icon: Icons.smartphone, title: '手机操控'),
                SettingsTile(icon: Icons.mic, title: '语音'),
              ],
            ),
          ),
        ),
      );
      final glyphs = tester.widgetList<SettingsGlyph>(
        find.byType(SettingsGlyph),
      );
      expect(glyphs.length, 2);
      for (final glyph in glyphs) {
        expect(glyph.tint, hue);
      }
    });

    testWidgets('开关行整行可点,右侧有开关态文案', (tester) async {
      var value = false;
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(Brightness.dark),
          home: StatefulBuilder(
            builder: (context, setState) => Scaffold(
              body: SettingsSection(
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
      expect(find.text('已关闭'), findsOneWidget);
      await tester.tap(find.text('触感反馈'));
      await tester.pumpAndSettle();
      expect(find.text('已开启'), findsOneWidget);
    });

    testWidgets('数值用等宽数字', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(Brightness.dark),
          home: Scaffold(
            body: SettingsSection(
              title: '关于',
              rows: const [
                SettingsTile(
                  icon: Icons.tag,
                  title: '版本',
                  value: '0.1.0',
                  mono: true,
                ),
              ],
            ),
          ),
        ),
      );
      final text = tester.widget<Text>(find.text('0.1.0'));
      expect(text.style?.fontFamily, 'JetBrainsMono');
      expect(
        text.style?.fontFeatures,
        contains(const FontFeature.tabularFigures()),
      );
    });
  });

  group('弹层(AppSheet)', () {
    testWidgets('顶两角 28 的 sheet 玻璃', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(Brightness.dark),
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: FilledButton(
                  onPressed: () => showMaeveSheet<void>(
                    context: context,
                    builder: (_) =>
                        const AppSheet(child: SheetTitleBar(title: '测试弹层')),
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
      expect(find.text('测试弹层'), findsOneWidget);

      final surface = tester.widget<MaeveGlassSurface>(
        find.descendant(
          of: find.byType(AppSheet),
          matching: find.byType(MaeveGlassSurface),
        ),
      );
      expect(surface.tier, MaeveGlassTier.sheet);
      expect(
        surface.borderRadius,
        const BorderRadius.vertical(top: Radius.circular(MaeveRadius.xl)),
      );
    });
  });

  group('状态双编码(C9)', () {
    testWidgets('空闲是空心环,就绪是实心点', (tester) async {
      await tester.pumpWidget(
        host(
          const Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              StatusDot(tone: MaeveTone.idle),
              StatusDot(tone: MaeveTone.ready),
            ],
          ),
        ),
      );
      final dots = tester
          .widgetList<Container>(
            find.descendant(
              of: find.byType(StatusDot),
              matching: find.byType(Container),
            ),
          )
          .toList();
      // 每个点一个 Container:空心靠 border,实心靠 color。
      final idle = dots.firstWhere(
        (c) => (c.decoration! as BoxDecoration).border != null,
      );
      final ready = dots.firstWhere(
        (c) =>
            (c.decoration! as BoxDecoration).color != null &&
            (c.decoration! as BoxDecoration).color != Colors.transparent,
      );
      expect((idle.decoration! as BoxDecoration).color, Colors.transparent);
      expect(
        (ready.decoration! as BoxDecoration).color,
        isNot(Colors.transparent),
      );
    });

    testWidgets('顶栏状态行给读屏一句完整文案', (tester) async {
      await tester.pumpWidget(
        host(const HeaderStatus(text: '本机 · 就绪', tone: MaeveTone.ready)),
      );
      expect(find.bySemanticsLabel('状态：本机 · 就绪'), findsOneWidget);
    });
  });

  group('展开与尺寸', () {
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

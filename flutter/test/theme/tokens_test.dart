import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_motion.dart';
import 'package:maeve_flutter/design/maeve_palette.dart';
import 'package:maeve_flutter/design/maeve_surfaces.dart';
import 'package:maeve_flutter/design/maeve_tokens.dart';

/// 第 6 节 C5(动效取值)、C7(圆角)、C10(断点)、C14(字体)里能被代码验证的部分。
void main() {
  group('C7 圆角', () {
    test('六档数值与用途固定', () {
      expect(MaeveRadius.xs, 6);
      expect(MaeveRadius.sm, 10);
      expect(MaeveRadius.md, 14);
      expect(MaeveRadius.lg, 20);
      expect(MaeveRadius.xl, 28);
      expect(MaeveRadius.pill, 999);
    });

    test('派生形状取对档位', () {
      expect(
        MaeveShapes.card.borderRadius,
        const BorderRadius.all(Radius.circular(MaeveRadius.md)),
      );
      expect(
        MaeveShapes.bubble.borderRadius,
        const BorderRadius.all(Radius.circular(MaeveRadius.lg)),
      );
      expect(
        MaeveShapes.dialog.borderRadius,
        const BorderRadius.all(Radius.circular(MaeveRadius.xl)),
      );
      // 底部弹层只有顶两角。
      expect(
        MaeveShapes.sheet.borderRadius,
        const BorderRadius.vertical(top: Radius.circular(MaeveRadius.xl)),
      );
    });
  });

  group('C5 动效', () {
    test('时长按四档取值', () {
      expect(MaeveMotion.press.inMilliseconds, 90);
      expect(MaeveMotion.quick.inMilliseconds, 160);
      expect(MaeveMotion.standard.inMilliseconds, 240);
      expect(MaeveMotion.collapse.inMilliseconds, 280);
      expect(MaeveMotion.emphasized.inMilliseconds, 340);
      expect(MaeveMotion.sheetIn.inMilliseconds, 380);
      expect(MaeveMotion.sheetOut.inMilliseconds, 260);
      expect(MaeveMotion.glassIn.inMilliseconds, 120);
      expect(MaeveMotion.page.inMilliseconds, 300);
      expect(MaeveMotion.push.inMilliseconds, 300);
      expect(MaeveMotion.slow.inMilliseconds, 480);
      expect(MaeveMotion.breath.inMilliseconds, 2400);
      expect(MaeveMotion.stagger.inMilliseconds, 30);
      expect(MaeveMotion.rise, 8);
      expect(MaeveMotion.pressScale, .97);
      expect(MaeveMotion.rubberBandLimit, 48);
    });

    test('抽屉/弹层用 iOS 长尾减速,不是 linear', () {
      expect(MaeveMotion.glide, isNot(Curves.linear));
      expect(MaeveMotion.glide, isNot(Curves.easeInOut));
      // 两条都是起步快、收尾慢;长尾减速在中段必须比标准曲线跑得更前。
      expect(MaeveMotion.glide.transform(.5), greaterThan(.5));
      expect(MaeveMotion.std.transform(.5), greaterThan(.5));
    });

    test('弹簧:按压无回弹,甩出 0.15', () {
      double ratio(SpringDescription spring) =>
          spring.damping / (2 * math.sqrt(spring.mass * spring.stiffness));
      expect(MaeveMotion.pressSpring.mass, 1);
      expect(ratio(MaeveMotion.pressSpring), closeTo(1, .001));
      expect(ratio(MaeveMotion.sheetSpring), closeTo(1, .001));
      expect(ratio(MaeveMotion.flingSpring), closeTo(.85, .001));
    });

    test('减少动画:所有时长归零,按压不缩放', () {
      const still = AppMotion(true);
      expect(still.press, Duration.zero);
      expect(still.standard, Duration.zero);
      expect(still.sheetIn, Duration.zero);
      expect(still.breath, Duration.zero);
      expect(still.stagger(4), Duration.zero);
      expect(still.pressedScale, 1);
      // 正常态保持原值。
      const normal = AppMotion(false);
      expect(normal.press, MaeveMotion.press);
      expect(normal.stagger(4).inMilliseconds, 120);
      // 只错开前 8 项。
      expect(normal.stagger(30).inMilliseconds, 240);
    });
  });

  group('C10 自适应', () {
    test('断点是 840,阅读列 720,浮动件 696', () {
      expect(MaeveLayout.breakpoint, 840);
      expect(MaeveSpace.readingWidth, 720);
      expect(MaeveSpace.floatingWidth, 696);
      expect(MaeveSpace.floatInset, 12);
      expect(MaeveSpace.readingWidth - 2 * MaeveSpace.floatInset, 696);
    });

    test('间距是 4 的倍数为主', () {
      expect(MaeveSpace.xxs, 2);
      expect(MaeveSpace.xs, 4);
      expect(MaeveSpace.sm, 8);
      expect(MaeveSpace.md, 12);
      expect(MaeveSpace.lg, 16);
      expect(MaeveSpace.xl, 20);
      expect(MaeveSpace.xxl, 24);
      expect(MaeveSpace.xxxl, 32);
      expect(MaeveSpace.gutter, 16);
      expect(MaeveSpace.inTurn, 8);
      expect(MaeveSpace.betweenTurns, 24);
    });
  });

  group('C14 字体', () {
    testWidgets('字重只 300/400/600,等宽只走统一出口', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(Brightness.light),
          home: const SizedBox(),
        ),
      );
      final context = tester.element(find.byType(SizedBox));
      final theme = Theme.of(context);
      final allowed = {FontWeight.w300, FontWeight.w400, FontWeight.w600};
      for (final style in [
        theme.textTheme.displayLarge,
        theme.textTheme.headlineSmall,
        theme.textTheme.titleMedium,
        theme.textTheme.bodyLarge,
        theme.textTheme.bodyMedium,
        theme.textTheme.bodySmall,
        theme.textTheme.labelLarge,
        theme.textTheme.labelSmall,
      ]) {
        expect(allowed.contains(style?.fontWeight), isTrue, reason: '$style');
      }
      // 不打包中文字体:正文走平台默认(Roboto + 系统中文字体),不是等宽家族。
      expect(theme.textTheme.bodyMedium?.fontFamily, isNot('JetBrainsMono'));
    });

    testWidgets('等宽:JetBrains Mono + 等宽数字 + 关连字', (tester) async {
      await tester.pumpWidget(
        MaterialApp(theme: maeveTheme(Brightness.dark), home: const SizedBox()),
      );
      final context = tester.element(find.byType(SizedBox));
      final mono = MaeveFonts.mono(context);
      expect(mono.fontFamily, 'JetBrainsMono');
      final features = mono.fontFeatures ?? const <FontFeature>[];
      expect(features, contains(const FontFeature.tabularFigures()));
      expect(features, contains(FontFeature.disable('liga')));
      expect(features, contains(FontFeature.disable('calt')));
    });
  });

  group('主题装配', () {
    testWidgets('主题带着当前配色的材质扩展', (tester) async {
      for (final palette in MaevePalette.all) {
        for (final brightness in Brightness.values) {
          late ThemeData theme;
          await tester.pumpWidget(
            Theme(
              data: maeveTheme(brightness, palette: palette),
              child: Builder(
                builder: (context) {
                  theme = Theme.of(context);
                  return const SizedBox();
                },
              ),
            ),
          );
          expect(theme.brightness, brightness);
          expect(theme.extension<MaeveSurfaces>(), isNotNull);
          expect(theme.colorScheme.primary, palette.primaryOf(brightness));
          expect(
            theme.scaffoldBackgroundColor,
            palette.neutrals(brightness).canvas,
          );
          // 背景与光晕跟着配色换,不是写死一套。
          expect(
            theme.extension<MaeveSurfaces>()!.backdropTop,
            palette.neutrals(brightness).backdropTop,
          );
          expect(theme.colorScheme.surfaceTint, Colors.transparent);
        }
      }
    });

    test('玻璃数值不被配色带偏(σ / 饱和度沿用 3.2 基线)', () {
      for (final palette in MaevePalette.all) {
        final light = palette.surfaces(Brightness.light);
        final dark = palette.surfaces(Brightness.dark);
        expect(light.chrome.blurSigma, 18);
        expect(light.chrome.saturation, 1.3);
        expect(light.sheet.blurSigma, 24);
        expect(light.sheet.saturation, 1.4);
        expect(dark.chrome.blurSigma, 18);
        expect(dark.chrome.saturation, 1.2);
        // card / pill 不模糊。
        expect(light.card.blurSigma, 0);
        expect(light.pill.blurSigma, 0);
        expect(dark.card.blurSigma, 0);
        expect(dark.pill.blurSigma, 0);
        // 降级填充必须近乎不透明,否则没有模糊时背后的字会透出来。
        expect(light.chrome.fallbackFill.a, greaterThanOrEqualTo(.95));
        expect(dark.chrome.fallbackFill.a, greaterThanOrEqualTo(.95));
        expect(light.sheet.fallbackFill.a, greaterThanOrEqualTo(.95));
      }
    });
  });
}

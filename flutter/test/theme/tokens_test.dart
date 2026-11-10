import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_colors.dart';
import 'package:maeve_flutter/design/maeve_motion.dart';
import 'package:maeve_flutter/design/maeve_oklch.dart';
import 'package:maeve_flutter/design/maeve_shapes.dart';
import 'package:maeve_flutter/design/maeve_surfaces.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/design/maeve_type.dart';

/// A1 的可计算部分:token 与设计契约 / Kotlin 参考实现对得上,且命名是 Maeve 自己的。
void main() {
  group('形状', () {
    test('半径六档与输入条专用值', () {
      expect(MaeveRadius.xs, 6);
      expect(MaeveRadius.sm, 10);
      expect(MaeveRadius.md, 14);
      expect(MaeveRadius.lg, 20);
      expect(MaeveRadius.xl, 28);
      // 输入条不是胶囊:16 的纸条(Compose 侧 ComposerShape 同值)。
      expect(MaeveRadius.composer, 16);
    });

    test('派生形状:气泡带尾角,输入条是纸条', () {
      final user = MaeveShapes.userBubble.borderRadius as BorderRadius;
      expect(user.bottomRight, const Radius.circular(MaeveRadius.xs));
      expect(user.topLeft, const Radius.circular(MaeveRadius.lg));
      final answer = MaeveShapes.answerBubble.borderRadius as BorderRadius;
      expect(answer.bottomLeft, const Radius.circular(MaeveRadius.xs));
      expect(
        MaeveShapes.composer.borderRadius,
        const BorderRadius.all(Radius.circular(16)),
      );
      expect(
        (MaeveShapes.card.borderRadius as BorderRadius).topLeft,
        const Radius.circular(20),
      );
      expect(
        (MaeveShapes.button.borderRadius as BorderRadius).topLeft,
        const Radius.circular(14),
      );
    });
  });

  group('间距与尺寸', () {
    test('4 的倍数阶梯 + 页面留白 + 阅读列', () {
      expect(MaeveSpace.s2, 2);
      expect(MaeveSpace.s4, 4);
      expect(MaeveSpace.s8, 8);
      expect(MaeveSpace.s12, 12);
      expect(MaeveSpace.s16, 16);
      expect(MaeveSpace.s20, 20);
      expect(MaeveSpace.s24, 24);
      expect(MaeveSpace.s32, 32);
      expect(MaeveSpace.readingWidth, 720);
    });

    test('控件尺寸:顶栏 56 / 圆钮 40 / 行 52 / 图标块 30 / 台词轨 26', () {
      expect(MaeveSize.topBar, 56);
      expect(MaeveSize.iconButton, 40);
      expect(MaeveSize.rowMin, 52);
      expect(MaeveSize.tile, 30);
      expect(MaeveSize.actRail, 26);
      // 可点目标 ≥44(A6)。
      expect(MaeveSize.touch, greaterThanOrEqualTo(44));
    });

    test('断点 840', () => expect(MaeveBreakpoints.wide, 840));
  });

  group('字阶', () {
    test('字重只用 400 / 500 / 600', () {
      final styles = [
        MaeveType.display,
        MaeveType.largeTitle,
        MaeveType.title,
        MaeveType.body,
        MaeveType.bodyStrong,
        MaeveType.caption,
        MaeveType.label,
        MaeveType.hint,
        MaeveType.script,
        MaeveType.eyebrow,
        MaeveType.mono,
        MaeveType.numeral,
      ];
      for (final style in styles) {
        expect(
          {
            FontWeight.w400,
            FontWeight.w500,
            FontWeight.w600,
          }.contains(style.fontWeight),
          isTrue,
          reason: '$style',
        );
      }
    });

    test('大字负字距(精密感的来源)', () {
      expect(MaeveType.display.letterSpacing, lessThan(0));
      expect(MaeveType.largeTitle.letterSpacing, lessThan(0));
      expect(MaeveType.title.letterSpacing, lessThan(0));
    });

    test('等宽出口唯一:系统等宽 + tabular + 关连字', () {
      for (final style in [
        MaeveType.mono,
        MaeveType.monoSmall,
        MaeveType.numeral,
        MaeveType.turn,
      ]) {
        expect(style.fontFamily, 'monospace');
        expect(
          style.fontFeatures,
          contains(const FontFeature.tabularFigures()),
        );
      }
      expect(
        MaeveType.mono.fontFeatures,
        contains(FontFeature.disable('liga')),
      );
      expect(
        MaeveType.mono.fontFeatures,
        contains(FontFeature.disable('calt')),
      );
    });

    test('中文微标签自动升号,值自动等宽', () {
      expect(MaeveType.eyebrowFor('LOOP 03').fontSize, 10.5);
      expect(MaeveType.eyebrowFor('第一幕').fontSize, 11.5);
      expect(MaeveType.valueFor('0.1.0').fontFamily, 'monospace');
      expect(MaeveType.valueFor('已开启').fontFamily, isNull);
    });
  });

  group('动效', () {
    test('时长分档与契约一致', () {
      expect(MaeveMotion.pressMs, 90);
      expect(MaeveMotion.quickMs, 160);
      expect(MaeveMotion.standardMs, 240);
      expect(MaeveMotion.collapseMs, 280);
      expect(MaeveMotion.emphasizedMs, 340);
      expect(MaeveMotion.sheetInMs, 380);
      expect(MaeveMotion.glassInMs, 120);
      expect(MaeveMotion.breathMs, 2400);
      expect(MaeveMotion.pageMs, 220);
      expect(MaeveMotion.holdToConfirmMs, 1500);
      expect(MaeveMotion.pressScale, .97);
    });

    test('曲线:glide 比减速更早到位', () {
      expect(MaeveMotion.glide, isNot(Curves.linear));
      expect(MaeveMotion.glide.transform(.5), greaterThan(.5));
      expect(MaeveMotion.decelerate.transform(.2), greaterThan(.2));
    });

    test('减少动效:所有时长归零、按压不缩放、循环停静止帧', () {
      const still = AppMotion(true);
      for (final d in [
        still.press,
        still.quick,
        still.standard,
        still.collapse,
        still.emphasized,
        still.sheetIn,
        still.glassIn,
        still.breath,
        still.page,
      ]) {
        expect(d, Duration.zero);
      }
      expect(still.pressedScale, 1);
      const normal = AppMotion(false);
      expect(normal.press, MaeveMotion.press);
      expect(normal.pressedScale, .97);
    });
  });

  group('配色预设', () {
    test('六套、Maeve 命名、默认梅子', () {
      expect(MaeveAccentPreset.values.length, 6);
      expect(MaeveAccentPreset.values.map((p) => p.label).toList(), [
        '梅子',
        '黄铜',
        '沙原',
        '珍珠',
        '夜靛',
        '石墨',
      ]);
      expect(MaeveAccentPreset.reverie.id, 'reverie');
      expect(MaeveAccentPreset.byId('nope'), MaeveAccentPreset.reverie);
    });

    test('推导阶梯:按下更深、容器更浅、深色种子更亮', () {
      for (final preset in MaeveAccentPreset.values) {
        final a = deriveAccent(preset);
        expect(
          _oklchL(a.lightPressed),
          lessThan(_oklchL(a.lightSeed)),
          reason: preset.label,
        );
        expect(
          _oklchL(a.darkPressed),
          lessThan(_oklchL(a.darkSeed)),
          reason: preset.label,
        );
        expect(_oklchL(a.lightContainer), greaterThan(.85));
        expect(_oklchL(a.darkContainer), lessThan(.5));
        expect(_oklchL(a.darkSeed), greaterThan(_oklchL(a.lightSeed)));
      }
    });

    test('自定义色走同一条推导,彩度被压到 0.13 以内', () {
      final custom = deriveCustom(hue: 30, chroma: .4);
      expect(custom.id, 'custom');
      expect(custom.chroma, lessThanOrEqualTo(.1301));
      expect(custom.lightSeed, isNot(custom.darkSeed));
    });
  });

  group('表面', () {
    test('四档:只有 chrome / sheet 模糊,σ 与饱和度按契约', () {
      final c = MaeveColors.dark(deriveAccent(MaeveAccentPreset.reverie));
      final s = buildSurfaces(c);
      expect(s.chrome.blurSigma, 24);
      expect(s.chrome.saturation, 1.40);
      expect(s.sheet.blurSigma, 30);
      expect(s.sheet.saturation, 1.55);
      expect(s.card.blurSigma, 0);
      expect(s.pill.blurSigma, 0);
      // 发丝线 0.75,顶边高光比例按档位。
      expect(s.chrome.hairlineWidth, .75);
      expect(s.pill.rimFraction, .5);
    });

    test('关模糊后每一档都 still 有近不透明填充,且不再有 σ', () {
      final c = MaeveColors.light(deriveAccent(MaeveAccentPreset.reverie));
      final s = buildSurfaces(c, blurEnabled: false);
      expect(s.blurEnabled, isFalse);
      for (final style in [s.chrome, s.sheet, s.card, s.pill]) {
        expect(style.blurSigma, 0);
        expect(style.fallbackFill.a, greaterThanOrEqualTo(.98));
      }
    });

    test('玻璃填充是半透明的(否则读不出玻璃)', () {
      final c = MaeveColors.light(deriveAccent(MaeveAccentPreset.reverie));
      final s = buildSurfaces(c);
      expect(s.chrome.fill.a, lessThan(1));
      expect(s.sheet.fill.a, lessThan(1));
    });
  });

  group('主题装配', () {
    testWidgets('手写 scheme:没有 fromSeed 的痕迹,surfaceTint 透明、elevation 0', (
      tester,
    ) async {
      final theme = maeveTheme(
        brightness: Brightness.dark,
        accent: deriveAccent(MaeveAccentPreset.dolores),
      );
      expect(theme.colorScheme.surfaceTint, Colors.transparent);
      expect(
        theme.colorScheme.primary,
        deriveAccent(MaeveAccentPreset.dolores).darkSeed,
      );
      expect(theme.cardTheme.elevation, 0);
      expect(theme.appBarTheme.surfaceTintColor, Colors.transparent);
    });

    testWidgets('MaeveThemeScope 把解析后的亮度和四档表面发给子树', (tester) async {
      late MaeveThemeData data;
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(brightness: Brightness.dark),
          home: MaeveThemeScope(
            brightness: Brightness.dark,
            accent: deriveAccent(MaeveAccentPreset.maze),
            child: Builder(
              builder: (context) {
                data = MaeveTheme.of(context);
                return const SizedBox();
              },
            ),
          ),
        ),
      );
      expect(data.colors.isDark, isTrue);
      expect(data.accent.id, 'maze');
      expect(data.surfaces.chrome.blurSigma, 24);
      expect(data.motion.reduced, isFalse);
    });
  });
}

/// 取一个颜色的 OKLCH 感知明度(测试用)。
double _oklchL(Color color) => MaeveOklch.from(color).l;

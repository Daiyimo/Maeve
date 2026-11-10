import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_palette.dart';
import 'package:maeve_flutter/design/maeve_tokens.dart';
import 'package:maeve_flutter/design/oklch.dart';

/// 第 6 节 C1:六套配色 × 浅/深,逐条验对比度。
///
/// 阈值来自 WCAG 与第 4.4 节:
/// - 文本 ≥ 4.5:1(1.4.3)
/// - 非文本 / 大号 ≥ 3:1(1.4.11)
/// - container 上的 onContainer ≥ 7:1
/// - 用户气泡内主/次文字 ≥ 5.8:1
void main() {
  final cases = <(MaevePalette, Brightness)>[
    for (final palette in MaevePalette.all)
      for (final brightness in Brightness.values) (palette, brightness),
  ];

  String label(MaevePalette palette, Brightness brightness) =>
      '${palette.label} · ${brightness == Brightness.dark ? '深' : '浅'}';

  test('六套配色 × 浅深,每一对都在表内', () {
    expect(MaevePalette.all.length, 6);
    expect(cases.length, 12);
    // 默认第一套是雾紫(品牌默认色)。
    expect(MaevePalette.all.first.id, MaevePaletteId.reverie);
  });

  for (final (palette, brightness) in cases) {
    final name = label(palette, brightness);
    final neutrals = palette.neutrals(brightness);
    final scheme = palette.scheme(brightness);

    test('$name:正文与次级文字在页面上 ≥4.5', () {
      expect(
        Oklch.contrast(neutrals.ink, neutrals.canvas),
        greaterThanOrEqualTo(4.5),
        reason: '$name 主文字对比度不足',
      );
      expect(
        Oklch.contrast(neutrals.secondary, neutrals.canvas),
        greaterThanOrEqualTo(4.5),
        reason: '$name 次级文字对比度不足',
      );
      // 卡片上的说明文字也不能掉,卡片底与页面底只差一点。
      expect(
        Oklch.contrast(neutrals.secondary, neutrals.surface),
        greaterThanOrEqualTo(4.5),
        reason: '$name 卡片上的次级文字对比度不足',
      );
    });

    test('$name:占位符等三级文字 ≥3,不隐形', () {
      expect(
        Oklch.contrast(neutrals.tertiary, neutrals.canvas),
        greaterThanOrEqualTo(3),
      );
    });

    test('$name:主色 vs 页面 ≥3,主色上的字 ≥4.5', () {
      expect(
        Oklch.contrast(scheme.primary, neutrals.canvas),
        greaterThanOrEqualTo(3),
      );
      expect(
        Oklch.contrast(scheme.onPrimary, scheme.primary),
        greaterThanOrEqualTo(4.5),
      );
    });

    test('$name:container / onContainer ≥7', () {
      expect(
        Oklch.contrast(scheme.onPrimaryContainer, scheme.primaryContainer),
        greaterThanOrEqualTo(7),
      );
    });

    test('$name:用户气泡内主/次文字 ≥5.8', () {
      final bubble = palette.bubbleOf(brightness);
      expect(
        Oklch.contrast(palette.bubbleInkOf(brightness), bubble),
        greaterThanOrEqualTo(5.8),
      );
      expect(
        Oklch.contrast(palette.bubbleSecondaryOf(brightness), bubble),
        greaterThanOrEqualTo(5.8),
      );
    });

    test('$name:玻璃上的文字仍然可读(降级与玻璃两种底)', () {
      final surfaces = palette.surfaces(brightness);
      for (final tier in [surfaces.chrome, surfaces.sheet]) {
        final glass = Color.alphaBlend(tier.fill, neutrals.backdropBottom);
        expect(Oklch.contrast(neutrals.ink, glass), greaterThanOrEqualTo(4.5));
        final fallback = Color.alphaBlend(
          tier.fallbackFill,
          neutrals.backdropBottom,
        );
        expect(
          Oklch.contrast(neutrals.ink, fallback),
          greaterThanOrEqualTo(4.5),
        );
      }
    });

    test('$name:语义色在页面上 ≥4.5', () {
      for (final color in [
        MaeveTokens.success(brightness),
        MaeveTokens.warning(brightness),
        scheme.error,
      ]) {
        expect(
          Oklch.contrast(color, neutrals.canvas),
          greaterThanOrEqualTo(4.5),
        );
      }
    });
  }

  test('换配色时各级文字对比度不变(中性色明度不动)', () {
    for (final brightness in Brightness.values) {
      double spread(Color Function(MaeveNeutrals) pick) {
        final ratios = [
          for (final palette in MaevePalette.all)
            Oklch.contrast(
              pick(palette.neutrals(brightness)),
              palette.neutrals(brightness).canvas,
            ),
        ];
        return ratios.reduce((a, b) => a > b ? a : b) -
            ratios.reduce((a, b) => a < b ? a : b);
      }

      // 只有彩度在变,对比度漂移必须小到看不出来。
      expect(spread((n) => n.ink), lessThan(.10));
      expect(spread((n) => n.secondary), lessThan(.20));
    }
  });

  test('六套配色彼此可辨:种子色不重复,至多一套是无彩色', () {
    final seeds = {for (final palette in MaevePalette.all) palette.seed};
    expect(seeds.length, 6);
    // 石墨是刻意的低彩度(neutral)方案,其余五套必须有明显色相。
    final achromatic = MaevePalette.all
        .where((palette) => Oklch.fromColor(palette.seed).c < .03)
        .toList();
    expect(achromatic.length, lessThanOrEqualTo(1));
    // 五个有彩方案的色相两两至少差 5°。
    final hues = [
      for (final palette in MaevePalette.all)
        if (Oklch.fromColor(palette.seed).c >= .03)
          Oklch.fromColor(palette.seed).hue,
    ];
    for (var i = 0; i < hues.length; i++) {
      for (var j = i + 1; j < hues.length; j++) {
        expect((hues[i] - hues[j]).abs(), greaterThan(5));
      }
    }
  });
}

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_colors.dart';
import 'package:maeve_flutter/design/maeve_oklch.dart';
import 'package:maeve_flutter/design/maeve_semantic.dart';
import 'package:maeve_flutter/design/maeve_surfaces.dart';

/// A2:每个预设 × 明暗,对比度**算出来**验,不靠眼。
///
/// 阈值来自设计契约 §1:文本 ≥4.5、非文本 ≥3;契约更严的地方按契约
/// (种子上的字也要 4.5,由 `guardSeed` 保证)。
///
/// 另外守两件事:
/// - **换预设时对比度不变**:中性色相/彩度固定、只是强调色一族在换,
///   所以同一级文字在不同预设下的对比度必须**完全相等**(不是"接近")。
/// - 玻璃上的字仍然可读:玻璃填充叠在画布上之后,正文 ≥4.5。
void main() {
  final cases = <(MaeveAccentPreset, Brightness)>[
    for (final preset in MaeveAccentPreset.values)
      for (final brightness in Brightness.values) (preset, brightness),
  ];

  String label(MaeveAccentPreset preset, Brightness brightness) =>
      '${preset.label} · ${brightness == Brightness.dark ? '深' : '浅'}';

  MaeveColors colorsOf(MaeveAccentPreset preset, Brightness brightness) {
    final accent = deriveAccent(preset);
    return brightness == Brightness.dark
        ? MaeveColors.dark(accent)
        : MaeveColors.light(accent);
  }

  test('六套预设 × 浅深,一共 12 组', () {
    expect(MaeveAccentPreset.values.length, 6);
    expect(cases.length, 12);
    // 默认是梅子。
    expect(MaeveAccentPreset.reverie.label, '梅子');
  });

  for (final (preset, brightness) in cases) {
    final name = label(preset, brightness);
    final c = colorsOf(preset, brightness);
    final semantic = MaeveSemantic.of(brightness);

    test('$name:正文 / 次级文字 ≥4.5', () {
      expect(
        MaeveOklch.contrast(c.ink, c.bg),
        greaterThanOrEqualTo(4.5),
        reason: '$name 主文字',
      );
      expect(
        MaeveOklch.contrast(c.ink2, c.bg),
        greaterThanOrEqualTo(4.5),
        reason: '$name 次级文字',
      );
    });

    test('$name:辅助文字与图标 ≥3', () {
      expect(
        MaeveOklch.contrast(c.ink3, c.bg),
        greaterThanOrEqualTo(3),
        reason: '$name ink3 用于说明与图标',
      );
      // ink4 只做极弱提示(占位符的光标位置等),契约给的 2.2。
      expect(MaeveOklch.contrast(c.ink4, c.bg), greaterThanOrEqualTo(2.2));
    });

    test('$name:强调色是信号,不是文字(≥3),上面的字 ≥4.5', () {
      expect(
        MaeveOklch.contrast(c.accent, c.bg),
        greaterThanOrEqualTo(3),
        reason: '$name 强调色对画布(焦点环/圆点)',
      );
      expect(
        MaeveOklch.contrast(c.onAccent, c.accent),
        greaterThanOrEqualTo(4.5),
        reason: '$name 主按钮上的字',
      );
      expect(
        MaeveOklch.contrast(c.onAccentContainer, c.accentContainer),
        greaterThanOrEqualTo(4.5),
        reason: '$name 容器上的字',
      );
    });

    test('$name:状态色在画布上 ≥4.5', () {
      for (final color in [semantic.ok, semantic.warn, semantic.bad]) {
        expect(MaeveOklch.contrast(color, c.bg), greaterThanOrEqualTo(4.5));
      }
    });

    test('$name:玻璃上的字仍然可读', () {
      final surfaces = buildSurfaces(c);
      for (final style in [surfaces.chrome, surfaces.sheet, surfaces.card]) {
        final glass = Color.alphaBlend(style.fill, c.bg);
        expect(MaeveOklch.contrast(c.ink, glass), greaterThanOrEqualTo(4.5));
        final fallback = Color.alphaBlend(style.fallbackFill, c.bg);
        expect(MaeveOklch.contrast(c.ink, fallback), greaterThanOrEqualTo(4.5));
      }
    });

    test('$name:种子守卫真的守住了(浅色种子配白字 ≥4.5)', () {
      final accent = deriveAccent(preset);
      expect(
        MaeveOklch.contrast(accent.lightSeed, const Color(0xFFFFFFFF)),
        greaterThanOrEqualTo(4.5),
        reason: '$name lightSeed',
      );
      final darkSeed = MaeveOklch.from(accent.darkSeed);
      final onDark = MaeveOklch.color(
        l: .231,
        c: darkSeed.c * .39,
        hue: darkSeed.hue,
      );
      expect(
        MaeveOklch.contrast(accent.darkSeed, onDark),
        greaterThanOrEqualTo(4.5),
        reason: '$name darkSeed',
      );
    });
  }

  test('换预设时各级文字对比度完全不变(中性色不随强调色转)', () {
    for (final brightness in Brightness.values) {
      double spread(Color Function(MaeveColors) pick) {
        final ratios = [
          for (final preset in MaeveAccentPreset.values)
            MaeveOklch.contrast(
              pick(colorsOf(preset, brightness)),
              colorsOf(preset, brightness).bg,
            ),
        ];
        return ratios.reduce((a, b) => a > b ? a : b) -
            ratios.reduce((a, b) => a < b ? a : b);
      }

      expect(spread((c) => c.ink), lessThan(1e-9));
      expect(spread((c) => c.ink2), lessThan(1e-9));
      expect(spread((c) => c.ink3), lessThan(1e-9));
    }
  });

  test('六套预设彼此可辨:种子不重复,至多一套近无色', () {
    final seeds = {
      for (final preset in MaeveAccentPreset.values)
        deriveAccent(preset).lightSeed,
    };
    expect(seeds.length, 6);
    final achromatic = MaeveAccentPreset.values.where((preset) {
      return MaeveOklch.from(deriveAccent(preset).lightSeed).c < .02;
    }).length;
    expect(achromatic, lessThanOrEqualTo(1));
  });

  test('guard:不够就抬明度,不换色相', () {
    final low = MaeveOklch.color(l: .86, c: .05, hue: 70);
    const bg = Color(0xFFF9F9FB);
    expect(MaeveOklch.contrast(low, bg), lessThan(4.5));
    final guarded = MaeveOklch.guard(low, bg, 4.5);
    expect(MaeveOklch.contrast(guarded, bg), greaterThanOrEqualTo(4.5));
    // 色相与彩度不变,只有明度在走。
    final before = MaeveOklch.from(low);
    final after = MaeveOklch.from(guarded);
    expect(after.hue, closeTo(before.hue, .5));
    expect(after.c, closeTo(before.c, .01));
    expect(after.l, lessThan(before.l));
  });
}

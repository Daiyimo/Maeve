import 'dart:math' as math;

import 'package:flutter/painting.dart';

/// OKLCH ↔ sRGB,以及对比度守卫。
///
/// 这套界面的每一级表面、每一级文字、每一个状态色都从这里推出来,
/// 没有一处是手挑的色值 —— 手挑的颜色换一套屏幕就会脏。
///
/// 公式是 Björn Ottosson 的 OKLab 参考实现(公开、无版权负担),
/// 与 Compose 侧 `MaeveOklch.kt` 同一套数;两边算出来的颜色必须一致。
abstract final class MaeveOklch {
  /// 越界时**只压彩度、不动明度**:sRGB 直接截断会让感知亮度跳变
  /// (金色截断后会发橙、发脏)。
  static Color color({
    required double l,
    required double c,
    required double hue,
  }) {
    final radians = hue * math.pi / 180;
    var chroma = c;
    var rgb = _toLinearSrgb(
      l,
      chroma * math.cos(radians),
      chroma * math.sin(radians),
    );
    var guard = 0;
    while (!_inGamut(rgb) && guard++ < 64) {
      chroma *= .96;
      rgb = _toLinearSrgb(
        l,
        chroma * math.cos(radians),
        chroma * math.sin(radians),
      );
    }
    return Color.from(
      alpha: 1,
      red: _linearToSrgb(rgb[0]),
      green: _linearToSrgb(rgb[1]),
      blue: _linearToSrgb(rgb[2]),
    );
  }

  /// 取一个颜色的 OKLCH 三元组(自定义色 / 壁纸取色用)。
  static ({double l, double c, double hue}) from(Color color) {
    final r = _srgbToLinear(color.r);
    final g = _srgbToLinear(color.g);
    final b = _srgbToLinear(color.b);
    final l = .4122214708 * r + .5363325363 * g + .0514459929 * b;
    final m = .2119034982 * r + .6806995451 * g + .1073969566 * b;
    final s = .0883024619 * r + .2817188376 * g + .6299787005 * b;
    final l_ = _cbrt(l);
    final m_ = _cbrt(m);
    final s_ = _cbrt(s);
    final okL = .2104542553 * l_ + .7936177850 * m_ - .0040720468 * s_;
    final okA = 1.9779984951 * l_ - 2.4285922050 * m_ + .4505937099 * s_;
    final okB = .0259040371 * l_ + .7827717662 * m_ - .8086757660 * s_;
    final chroma = math.sqrt(okA * okA + okB * okB);
    var hue = math.atan2(okB, okA) * 180 / math.pi;
    if (hue < 0) hue += 360;
    return (l: okL, c: chroma, hue: hue);
  }

  static List<double> _toLinearSrgb(double l, double a, double b) {
    final l_ = l + .3963377774 * a + .2158037573 * b;
    final m_ = l - .1055613458 * a - .0638541728 * b;
    final s_ = l - .0894841775 * a - 1.2914855480 * b;
    final ll = l_ * l_ * l_;
    final mm = m_ * m_ * m_;
    final ss = s_ * s_ * s_;
    return <double>[
      4.0767416621 * ll - 3.3077115913 * mm + .2309699292 * ss,
      -1.2684380046 * ll + 2.6097574011 * mm - .3413193965 * ss,
      -.0041960863 * ll - .7034186147 * mm + 1.7076147010 * ss,
    ];
  }

  static bool _inGamut(List<double> rgb) =>
      rgb.every((channel) => channel >= -.0005 && channel <= 1.0005);

  static double _linearToSrgb(double channel) =>
      (channel <= .0031308
              ? 12.92 * channel
              : 1.055 * math.pow(channel, 1 / 2.4) - .055)
          .clamp(0.0, 1.0);

  static double _srgbToLinear(double channel) => channel <= .04045
      ? channel / 12.92
      : math.pow((channel + .055) / 1.055, 2.4).toDouble();

  static double _cbrt(double value) => value < 0
      ? -math.pow(-value, 1 / 3).toDouble()
      : math.pow(value, 1 / 3).toDouble();

  /// WCAG 相对亮度。
  static double luminance(Color color) =>
      .2126 * _srgbToLinear(color.r) +
      .7152 * _srgbToLinear(color.g) +
      .0722 * _srgbToLinear(color.b);

  /// WCAG 对比度(1–21)。半透明前景先与背景合成,和屏幕上看到的一致。
  static double contrast(Color foreground, Color background) {
    final fg = foreground.a < 1
        ? Color.alphaBlend(foreground, background)
        : foreground;
    final bg = background.a < 1
        ? Color.alphaBlend(background, const Color(0xFFFFFFFF))
        : background;
    final la = luminance(fg);
    final lb = luminance(bg);
    return (math.max(la, lb) + .05) / (math.min(la, lb) + .05);
  }

  /// 二分调明度,直到 [foreground] 对 [background] 达到 [minimum]。
  ///
  /// 只动明度、不动色相与彩度 —— 这是"换配色各级文字对比度不变"的兜底:
  /// 万一某级灰在某个画布上不够,也只往深/浅走,不换色。
  static Color guard(
    Color foreground,
    Color background,
    double minimum, {
    int steps = 24,
  }) {
    if (contrast(foreground, background) >= minimum) return foreground;
    final base = from(foreground);
    // 前景比背景亮 → 往更亮走;前景比背景暗 → 往更暗走。
    final lighter = luminance(foreground) >= luminance(background);
    var low = lighter ? base.l : 0.0;
    var high = lighter ? 1.0 : base.l;
    var best = foreground;
    for (var i = 0; i < steps; i++) {
      final mid = (low + high) / 2;
      final candidate = color(l: mid, c: base.c, hue: base.hue);
      if (contrast(candidate, background) >= minimum) {
        best = candidate;
        // 记住刚好达标的边界,再往原色方向收一点
        if (lighter) {
          high = mid;
        } else {
          low = mid;
        }
      } else {
        if (lighter) {
          low = mid;
        } else {
          high = mid;
        }
      }
    }
    return best;
  }

  /// 同色相、改明度(推导阶梯用)。
  static Color withLightness(Color base, double lightness) {
    final ok = from(base);
    return color(l: lightness, c: ok.c, hue: ok.hue);
  }
}

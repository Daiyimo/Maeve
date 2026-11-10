import 'dart:math' as math;
import 'dart:ui' show Color;

/// OKLCH → sRGB,以及 WCAG 对比度计算。
///
/// 中性色阶只在这里生成:同一套明度 L,换配色只换色相与彩度倍率,
/// 所以各级文字的对比度不随配色变化(第 6 节 C1)。
///
/// 用的矩阵是 Björn Ottosson 的 OKLab 参考实现,和 CSS `oklch()` 同一个色彩空间。
/// 预设配色已在第 4.4 节烘焙成十六进制,这里只负责中性色与"自定义色/取色"的推导。
abstract final class Oklch {
  /// 色相(角度 0–360)、彩度、明度(0–1)。
  static Color toColor({
    required double l,
    required double c,
    required double hue,
  }) {
    final radians = hue * math.pi / 180;
    final a = c * math.cos(radians);
    final b = c * math.sin(radians);
    // 越界的彩度按二分回退到色域内:直接裁剪会让色相漂移。
    var low = 0.0;
    var high = c;
    var rgb = _linearToSrgb(_oklabToLinear(l, a, b));
    if (!_inGamut(rgb)) {
      for (var i = 0; i < 24; i++) {
        final mid = (low + high) / 2;
        rgb = _linearToSrgb(
          _oklabToLinear(l, mid * math.cos(radians), mid * math.sin(radians)),
        );
        if (_inGamut(rgb)) {
          low = mid;
        } else {
          high = mid;
        }
      }
      rgb = _linearToSrgb(
        _oklabToLinear(l, low * math.cos(radians), low * math.sin(radians)),
      );
    }
    return Color.from(
      alpha: 1,
      red: _clamp01(rgb[0]),
      green: _clamp01(rgb[1]),
      blue: _clamp01(rgb[2]),
    );
  }

  /// 取一个颜色的 OKLCH 三元组(用于从 seed 反推中性色色相)。
  static ({double l, double c, double hue}) fromColor(Color color) {
    final r = _srgbToLinear(color.r);
    final g = _srgbToLinear(color.g);
    final b = _srgbToLinear(color.b);
    final l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b;
    final m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b;
    final s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b;
    final l_ = _cbrt(l);
    final m_ = _cbrt(m);
    final s_ = _cbrt(s);
    final okL = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_;
    final okA = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_;
    final okB = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_;
    final chroma = math.sqrt(okA * okA + okB * okB);
    var hue = math.atan2(okB, okA) * 180 / math.pi;
    if (hue < 0) hue += 360;
    return (l: okL, c: chroma, hue: hue);
  }

  static List<double> _oklabToLinear(double l, double a, double b) {
    final l_ = l + 0.3963377774 * a + 0.2158037573 * b;
    final m_ = l - 0.1055613458 * a - 0.0638541728 * b;
    final s_ = l - 0.0894841775 * a - 1.2914855480 * b;
    final ll = l_ * l_ * l_;
    final mm = m_ * m_ * m_;
    final ss = s_ * s_ * s_;
    return <double>[
      4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss,
      -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss,
      -0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss,
    ];
  }

  static List<double> _linearToSrgb(List<double> rgb) => <double>[
    for (final channel in rgb)
      channel <= 0.0031308
          ? 12.92 * channel
          : 1.055 * math.pow(channel, 1 / 2.4) - 0.055,
  ];

  static double _srgbToLinear(double channel) => channel <= 0.04045
      ? channel / 12.92
      : math.pow((channel + 0.055) / 1.055, 2.4).toDouble();

  static bool _inGamut(List<double> rgb) =>
      rgb.every((channel) => channel >= -0.0005 && channel <= 1.0005);

  static double _clamp01(double value) => value.clamp(0.0, 1.0);

  static double _cbrt(double value) => value < 0
      ? -math.pow(-value, 1 / 3).toDouble()
      : math.pow(value, 1 / 3).toDouble();

  /// WCAG 相对亮度。
  static double luminance(Color color) =>
      0.2126 * _srgbToLinear(color.r) +
      0.7152 * _srgbToLinear(color.g) +
      0.0722 * _srgbToLinear(color.b);

  /// WCAG 对比度(1–21)。半透明前景先与背景合成再算,和屏幕上看到的一致。
  static double contrast(Color a, Color b) {
    final foreground = a.a < 1 ? Color.alphaBlend(a, b) : a;
    final background = b.a < 1
        ? Color.alphaBlend(b, const Color(0xFFFFFFFF))
        : b;
    final la = luminance(foreground);
    final lb = luminance(background);
    final lighter = math.max(la, lb);
    final darker = math.min(la, lb);
    return (lighter + 0.05) / (darker + 0.05);
  }
}

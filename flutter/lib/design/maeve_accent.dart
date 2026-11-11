import 'package:flutter/foundation.dart';

import 'maeve_oklch.dart';

import 'package:flutter/painting.dart';

/// Six complete atmospheres, with stable IDs for existing appearance choices.
/// Accent, canvas temperature and surface depth belong to the same palette.
enum MaeveAccentPreset {
  reverie('reverie', '觉醒紫', 0xFF7651C7, 0xFFCBB7FF, 1),
  dolores('dolores', '丝绒红', 0xFF944666, 0xFFF0B2CF, .95),
  maze('maze', '雾松', 0xFF38634B, 0xFFB5D5A4, .7),
  pearl('pearl', '冰川', 0xFF31667F, 0xFFA0D5DF, .8),
  sublime('sublime', '暮光', 0xFF4A57A4, 0xFFB4BFF5, .9),
  graphite('graphite', '月白', 0xFF514B42, 0xFFE2D9C9, .3);

  const MaeveAccentPreset(
    this.id,
    this.label,
    this.lightSeed,
    this.darkSeed,
    this.tintChroma,
  );

  final String id;
  final String label;
  final int lightSeed;
  final int darkSeed;

  /// Relative warmth/saturation of the accompanying neutral surfaces.
  final double tintChroma;

  static MaeveAccentPreset byId(String? id) =>
      values.firstWhere((preset) => preset.id == id, orElse: () => reverie);
}

/// 一套配色推导完的全部色位。
///
/// 阶梯(OKLCH 明度 / 彩度倍数,与 Compose 侧逐项一致):
/// - 浅色:seed L 0.53→pressed −0.06;container L 0.93 ×0.26;bubble L 0.918 ×0.29;
///   glow L 0.819 ×0.68。
/// - 深色:seed L 0.764 ×0.76;pressed −0.06;onSeed L 0.231 ×0.39;container L 0.361 ×0.51;
///   bubble L 0.314 ×0.29 配 onBubble L 0.93;glow L 0.52 ×0.72。
/// - haze = 色相 +24° 的邻近色,给第三团极光和第二层透色用。
@immutable
class MaeveAccent {
  const MaeveAccent({
    required this.id,
    required this.label,
    required this.hue,
    required this.chroma,
    required this.lightSeed,
    required this.lightPressed,
    required this.lightContainer,
    required this.lightOnContainer,
    required this.lightBubble,
    required this.lightGlow,
    required this.darkSeed,
    required this.darkPressed,
    required this.darkOnSeed,
    required this.darkContainer,
    required this.darkOnContainer,
    required this.darkBubble,
    required this.darkOnBubble,
    required this.darkGlow,
    required this.hazeLight,
    required this.hazeDark,
  });

  final String id;
  final String label;
  final double hue;
  final double chroma;

  final Color lightSeed;
  final Color lightPressed;
  final Color lightContainer;
  final Color lightOnContainer;
  final Color lightBubble;
  final Color lightGlow;

  final Color darkSeed;
  final Color darkPressed;
  final Color darkOnSeed;
  final Color darkContainer;
  final Color darkOnContainer;
  final Color darkBubble;
  final Color darkOnBubble;
  final Color darkGlow;

  final Color hazeLight;
  final Color hazeDark;

  /// 按亮度取种子。
  Color seedFor(Brightness brightness) =>
      brightness == Brightness.dark ? darkSeed : lightSeed;

  static MaeveAccent of(MaeveAccentPreset preset) => deriveAccent(preset);
}

/// 从预设推出一套完整色位。三条来源(预设 / 自定义 / 壁纸)走同一个入口。
MaeveAccent deriveAccent(MaeveAccentPreset preset) {
  final light = MaeveOklch.from(Color(preset.lightSeed));
  final dark = MaeveOklch.from(Color(preset.darkSeed));
  final hue = light.hue;
  final lightSeed = guardSeed(light, onLight: true);
  final darkSeed = guardSeed(dark, onLight: false);
  return _spec(
    id: preset.id,
    label: preset.label,
    hue: hue,
    lightSeed: lightSeed,
    darkSeed: darkSeed,
  );
}

/// 自定义色:色轮只给色相和彩度,明度按浅/深各自的带现推。
MaeveAccent deriveCustom({
  required double hue,
  required double chroma,
  String label = '自定义',
}) {
  final c = chroma.clamp(0.0, .13);
  final lightSeed = guardSeed((
    l: .53,
    c: c,
    hue: _normDeg(hue),
  ), onLight: true);
  final darkSeed = guardSeed((
    l: .764,
    c: c * .76,
    hue: _normDeg(hue),
  ), onLight: false);
  return _spec(
    id: 'custom',
    label: label,
    hue: MaeveOklch.from(lightSeed).hue,
    lightSeed: lightSeed,
    darkSeed: darkSeed,
  );
}

MaeveAccent _spec({
  required String id,
  required String label,
  required double hue,
  required Color lightSeed,
  required Color darkSeed,
}) {
  final c = MaeveOklch.from(lightSeed).c;
  final cd = MaeveOklch.from(darkSeed).c;
  final ls = MaeveOklch.from(lightSeed);
  final ds = MaeveOklch.from(darkSeed);
  Color at(double l, double chroma, [double? h]) =>
      MaeveOklch.color(l: l, c: chroma, hue: h ?? hue);

  return MaeveAccent(
    id: id,
    label: label,
    hue: hue,
    chroma: c,
    lightSeed: lightSeed,
    lightPressed: at(ls.l - .06, c * .95),
    lightContainer: at(.93, c * .26),
    lightOnContainer: at(.33, c * .70),
    lightBubble: at(.918, c * .29),
    lightGlow: at(.819, c * .68),
    darkSeed: darkSeed,
    darkPressed: at(ds.l - .06, cd * .95),
    darkOnSeed: at(.231, cd * .39),
    darkContainer: at(.361, cd * .51),
    darkOnContainer: at(.909, cd * .24),
    darkBubble: at(.314, cd * .29),
    darkOnBubble: at(.93, cd * .06),
    // 极光要抬到 .58:极光是背景里唯一有颜色的光,太暗读作"一块脏印子"。
    darkGlow: at(.580, cd * .72),
    hazeLight: at(.80, c * .45, _normDeg(hue + 24)),
    hazeDark: at(.50, cd * .62, _normDeg(hue + 24)),
  );
}

/// 种子守卫:浅色种子上的白字、深色种子上的深墨字都要 ≥ 4.5。
/// 不够就挪明度(色相、彩度不动),最多挪 12 步 —— 和 Compose 侧同一套规则。
Color guardSeed(
  ({double l, double c, double hue}) seed, {
  required bool onLight,
}) {
  final on = onLight
      ? const Color(0xFFFFFFFF)
      : MaeveOklch.color(l: .231, c: seed.c * .39, hue: seed.hue);
  var current = seed;
  for (var i = 0; i < 13; i++) {
    final candidate = MaeveOklch.color(
      l: current.l,
      c: current.c,
      hue: current.hue,
    );
    if (MaeveOklch.contrast(candidate, on) >= 4.5) return candidate;
    current = (
      l: (current.l + (onLight ? -.02 : .02)).clamp(0.0, 1.0),
      c: current.c,
      hue: current.hue,
    );
  }
  return MaeveOklch.color(l: current.l, c: current.c, hue: current.hue);
}

double _normDeg(double degrees) {
  var value = degrees % 360;
  if (value < 0) value += 360;
  return value;
}

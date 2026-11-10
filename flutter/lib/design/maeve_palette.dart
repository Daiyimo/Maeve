import 'package:flutter/material.dart';

import 'maeve_surfaces.dart';
import 'oklch.dart';

/// 一套配色的身份。顺序即设置页展示顺序,第一套是 Maeve 品牌默认色。
enum MaevePaletteId { reverie, tide, dusk, pine, amber, graphite }

/// 派生中性色需要的槽位。
///
/// 明度(lightness)是固定的,只有色相与彩度随配色走 —— 所以换配色时
/// 各级文字的对比度不变(第 6 节 C1):中性色阶不会被"顺手调深一点"。
@immutable
class MaeveNeutrals {
  const MaeveNeutrals({
    required this.canvas,
    required this.surface,
    required this.elevated,
    required this.sunken,
    required this.ink,
    required this.secondary,
    required this.tertiary,
    required this.line,
    required this.hairline,
    required this.backdropTop,
    required this.backdropBottom,
    required this.scrim,
    required this.bubbleInk,
    required this.bubbleSecondary,
  });

  /// 页面底色。
  final Color canvas;

  /// 抬一层的面(卡片底、设置行底)。
  final Color surface;

  /// 再抬一层(对话框、选中的行)。
  final Color elevated;

  /// 凹下去的区域(代码井、轨道)。
  final Color sunken;

  /// 主文字。
  final Color ink;

  /// 次级文字(说明、时间戳)。
  final Color secondary;

  /// 三级文字(占位符、禁用态)。
  final Color tertiary;

  /// 分隔线。
  final Color line;

  /// 发丝描边(玻璃边缘)。
  final Color hairline;

  /// 页面渐变上端。
  final Color backdropTop;

  /// 页面渐变下端。
  final Color backdropBottom;

  /// 抽屉 / 模态遮罩。
  final Color scrim;

  /// 用户气泡内的主文字。
  final Color bubbleInk;

  /// 用户气泡内的次级文字(必须 ≥5.8,气泡底色浅、字不能灰)。
  final Color bubbleSecondary;
}

/// 一套完整配色:15 个色值 + 中性色的色相 / 彩度倍率。
///
/// 色值来自第 4.4 节(已烘焙),中性色阶用 [Oklch] 按 `tintHue`/`tintChroma`
/// 现算 —— 明度不动,所以对比度稳定。
@immutable
class MaevePalette {
  const MaevePalette({
    required this.id,
    required this.label,
    required this.seed,
    required this.pressed,
    required this.container,
    required this.onContainer,
    required this.bubble,
    required this.glow,
    required this.seedDark,
    required this.pressedDark,
    required this.onSeedDark,
    required this.containerDark,
    required this.onContainerDark,
    required this.bubbleDark,
    required this.glowDark,
    required this.haze,
    required this.hazeDark,
    this.tintHue,
    this.tintChroma = 1,
  });

  final MaevePaletteId id;

  /// 设置页上的中文名。
  final String label;

  final Color seed;
  final Color pressed;
  final Color container;
  final Color onContainer;
  final Color bubble;
  final Color glow;

  final Color seedDark;
  final Color pressedDark;
  final Color onSeedDark;
  final Color containerDark;
  final Color onContainerDark;
  final Color bubbleDark;
  final Color glowDark;

  /// 左下光晕(主色的邻近色)。
  final Color haze;
  final Color hazeDark;

  /// 中性色色相(角度)。null = 跟随 seed 的色相(雾紫就是这么定的)。
  final double? tintHue;

  /// 中性色彩度倍率(0–1):石墨几乎无色,松绿偏冷。
  final double tintChroma;

  Color get activeSeed => seed;

  /// 当前明暗下的主色。
  Color primaryOf(Brightness brightness) =>
      brightness == Brightness.dark ? seedDark : seed;

  Color onPrimaryOf(Brightness brightness) =>
      brightness == Brightness.dark ? onSeedDark : const Color(0xFFFFFFFF);

  double get resolvedTintHue => tintHue ?? Oklch.fromColor(seed).hue;

  /// 中性色阶:明度固定,色相 = [resolvedTintHue],彩度 × [tintChroma]。
  MaeveNeutrals neutrals(Brightness brightness) {
    final hue = resolvedTintHue;
    final dark = brightness == Brightness.dark;
    Color ramp(double lightness, double chroma, {double alpha = 1}) =>
        Oklch.toColor(
          l: lightness,
          c: chroma * tintChroma,
          hue: hue,
        ).withValues(alpha: alpha);

    if (dark) {
      final ink = ramp(.955, .010);
      return MaeveNeutrals(
        canvas: ramp(.205, .016),
        surface: ramp(.245, .015),
        elevated: ramp(.288, .015),
        sunken: ramp(.170, .014),
        ink: ink,
        secondary: ramp(.760, .016),
        tertiary: ramp(.600, .014),
        line: ink.withValues(alpha: .14),
        hairline: ink.withValues(alpha: .09),
        backdropTop: ramp(.205, .018),
        backdropBottom: ramp(.168, .016),
        scrim: const Color(0x7A000000),
        bubbleInk: ink,
        bubbleSecondary: ramp(.780, .014),
      );
    }
    final ink = ramp(.255, .016);
    return MaeveNeutrals(
      canvas: ramp(.965, .010),
      surface: ramp(.985, .006),
      elevated: ramp(1, 0),
      sunken: ramp(.945, .012),
      ink: ink,
      secondary: ramp(.505, .020),
      tertiary: ramp(.625, .014),
      line: ink.withValues(alpha: .12),
      hairline: ink.withValues(alpha: .08),
      backdropTop: ramp(.965, .012),
      backdropBottom: ramp(.930, .030),
      scrim: ink.withValues(alpha: .32),
      bubbleInk: ink,
      bubbleSecondary: ramp(.420, .020),
    );
  }

  /// 用户气泡底色。
  Color bubbleOf(Brightness brightness) =>
      brightness == Brightness.dark ? bubbleDark : bubble;

  ColorScheme scheme(Brightness brightness) {
    final dark = brightness == Brightness.dark;
    final n = neutrals(brightness);
    return ColorScheme(
      brightness: brightness,
      primary: primaryOf(brightness),
      onPrimary: onPrimaryOf(brightness),
      primaryContainer: dark ? containerDark : container,
      onPrimaryContainer: dark ? onContainerDark : onContainer,
      secondary: dark ? seedDark : pressed,
      onSecondary: dark ? onSeedDark : const Color(0xFFFFFFFF),
      tertiary: dark ? glowDark : glow,
      onTertiary: dark ? onSeedDark : n.ink,
      error: dark ? const Color(0xFFE8A0A6) : const Color(0xFFA03C46),
      onError: dark ? const Color(0xFF2A1114) : const Color(0xFFFFFFFF),
      surface: n.canvas,
      onSurface: n.ink,
      surfaceContainerLowest: n.canvas,
      surfaceContainerLow: n.surface,
      surfaceContainer: n.surface,
      surfaceContainerHigh: n.elevated,
      surfaceContainerHighest: n.elevated,
      onSurfaceVariant: n.secondary,
      outline: n.line,
      outlineVariant: n.hairline,
      shadow: const Color(0xFF000000),
      scrim: n.scrim,
      inverseSurface: dark ? n.surface : n.ink,
      onInverseSurface: dark ? n.ink : n.surface,
      inversePrimary: dark ? seed : seedDark,
      surfaceTint: Colors.transparent,
    );
  }

  /// 页面材质:背景渐变、两团光晕、遮罩、四层玻璃。
  ///
  /// 玻璃本身的数值沿用第 3.2 节基线(α / σ / 饱和度);这里只把底色、
  /// 光晕、遮罩、气泡换成配色的。深色玻璃底取中性色 elevated,浅色取白。
  MaeveSurfaces surfaces(Brightness brightness, {bool blurEnabled = true}) {
    final dark = brightness == Brightness.dark;
    final n = neutrals(brightness);
    final glowTone = dark ? glowDark : glow;
    final hazeTone = dark ? hazeDark : haze;
    final glassBase = dark ? n.elevated : const Color(0xFFFFFFFF);
    final sheetBase = dark ? n.surface : n.surface;

    return MaeveSurfaces(
      backdropTop: n.backdropTop,
      backdropBottom: n.backdropBottom,
      glowPrimary: glowTone.withValues(alpha: dark ? .30 : .32),
      glowSecondary: hazeTone.withValues(alpha: dark ? .24 : .28),
      scrim: n.scrim,
      userBubble: bubbleOf(brightness),
      onUserBubble: n.bubbleInk,
      onUserBubbleSecondary: n.bubbleSecondary,
      blurEnabled: blurEnabled,
      chrome: MaeveGlassStyle(
        fill: glassBase.withValues(alpha: dark ? .72 : .70),
        fallbackFill: glassBase.withValues(alpha: dark ? .97 : .96),
        hairline: n.hairline,
        rim: (dark ? Colors.white : Colors.white).withValues(
          alpha: dark ? .12 : .85,
        ),
        blurSigma: 18,
        saturation: dark ? 1.2 : 1.3,
        shadows: [
          BoxShadow(
            color: (dark ? Colors.black : n.ink).withValues(
              alpha: dark ? .35 : .08,
            ),
            blurRadius: 24,
            offset: const Offset(0, 8),
          ),
          BoxShadow(
            color: (dark ? Colors.black : n.ink).withValues(
              alpha: dark ? .25 : .05,
            ),
            blurRadius: 3,
            offset: const Offset(0, 1),
          ),
        ],
      ),
      sheet: MaeveGlassStyle(
        fill: sheetBase.withValues(alpha: dark ? .86 : .82),
        fallbackFill: sheetBase.withValues(alpha: dark ? .97 : 1),
        hairline: n.hairline,
        rim: Colors.white.withValues(alpha: dark ? .10 : .90),
        blurSigma: 24,
        saturation: dark ? 1.2 : 1.4,
        shadows: [
          BoxShadow(
            color: (dark ? Colors.black : n.ink).withValues(
              alpha: dark ? .40 : .10,
            ),
            blurRadius: 32,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      card: MaeveGlassStyle(
        fill: (dark ? Colors.white : Colors.white).withValues(
          alpha: dark ? .05 : .62,
        ),
        fallbackFill: (dark ? Colors.white : Colors.white).withValues(
          alpha: dark ? .07 : .72,
        ),
        hairline: n.hairline,
        rim: Colors.white.withValues(alpha: dark ? .06 : .70),
      ),
      pill: MaeveGlassStyle(
        fill: Colors.white.withValues(alpha: dark ? .06 : .55),
        fallbackFill: Colors.white.withValues(alpha: dark ? .08 : .66),
        hairline: n.hairline,
        rim: Colors.white.withValues(alpha: dark ? .08 : .75),
      ),
    );
  }

  /// 用户气泡内文字用的对比色。
  Color bubbleInkOf(Brightness brightness) => neutrals(brightness).bubbleInk;
  Color bubbleSecondaryOf(Brightness brightness) =>
      neutrals(brightness).bubbleSecondary;

  static const reverie = MaevePalette(
    id: MaevePaletteId.reverie,
    label: '雾紫',
    seed: Color(0xFF4B67B4),
    pressed: Color(0xFF3D569D),
    container: Color(0xFFDEE8FF),
    onContainer: Color(0xFF213262),
    bubble: Color(0xFFD9E4FD),
    glow: Color(0xFFABC3FC),
    seedDark: Color(0xFF97B1F0),
    pressedDark: Color(0xFF859EDC),
    onSeedDark: Color(0xFF121C34),
    containerDark: Color(0xFF2E3C5F),
    onContainerDark: Color(0xFFD8E1F6),
    bubbleDark: Color(0xFF293144),
    glowDark: Color(0xFF51679D),
    haze: Color(0xFFD5CBEB),
    hazeDark: Color(0xFF514765),
    tintChroma: 1,
  );

  static const tide = MaevePalette(
    id: MaevePaletteId.tide,
    label: '黛青',
    seed: Color(0xFF236185),
    pressed: Color(0xFF145071),
    container: Color(0xFFDBEAF5),
    onContainer: Color(0xFF123950),
    bubble: Color(0xFFD6E7F3),
    glow: Color(0xFFA2CAE5),
    seedDark: Color(0xFF8CB9D7),
    pressedDark: Color(0xFF79A6C4),
    onSeedDark: Color(0xFF0D1F2B),
    containerDark: Color(0xFF274152),
    onContainerDark: Color(0xFFD5E3ED),
    bubbleDark: Color(0xFF26333C),
    glowDark: Color(0xFF466E88),
    haze: Color(0xFFC7D0EC),
    hazeDark: Color(0xFF444C66),
    tintHue: 238,
    tintChroma: .9,
  );

  static const dusk = MaevePalette(
    id: MaevePaletteId.dusk,
    label: '暮霞',
    seed: Color(0xFF726291),
    pressed: Color(0xFF61517D),
    container: Color(0xFFEAE5F4),
    onContainer: Color(0xFF392F4C),
    bubble: Color(0xFFE6E1F1),
    glow: Color(0xFFC8BDE1),
    seedDark: Color(0xFFB8ABD2),
    pressedDark: Color(0xFFA598BF),
    onSeedDark: Color(0xFF1F1A29),
    containerDark: Color(0xFF40394F),
    onContainerDark: Color(0xFFE2DEEC),
    bubbleDark: Color(0xFF332F3B),
    glowDark: Color(0xFF6D6284),
    haze: Color(0xFFE6C7D7),
    hazeDark: Color(0xFF5F4353),
    tintHue: 295,
    tintChroma: .85,
  );

  static const pine = MaevePalette(
    id: MaevePaletteId.pine,
    label: '松绿',
    seed: Color(0xFF34766F),
    pressed: Color(0xFF296760),
    container: Color(0xFFDBECE9),
    onContainer: Color(0xFF123D39),
    bubble: Color(0xFFD6E8E6),
    glow: Color(0xFFA3CEC8),
    seedDark: Color(0xFF8DBEB7),
    pressedDark: Color(0xFF7BABA4),
    onSeedDark: Color(0xFF0D211F),
    containerDark: Color(0xFF274340),
    onContainerDark: Color(0xFFD6E5E2),
    bubbleDark: Color(0xFF263533),
    glowDark: Color(0xFF47726D),
    haze: Color(0xFFB9D7E3),
    hazeDark: Color(0xFF34525E),
    tintHue: 190,
    tintChroma: .7,
  );

  static const amber = MaevePalette(
    id: MaevePaletteId.amber,
    label: '琥珀',
    seed: Color(0xFF8D6636),
    pressed: Color(0xFF7A5528),
    container: Color(0xFFF2E6D9),
    onContainer: Color(0xFF483011),
    bubble: Color(0xFFEEE2D3),
    glow: Color(0xFFDCBE9D),
    seedDark: Color(0xFFCCAC87),
    pressedDark: Color(0xFFB99975),
    onSeedDark: Color(0xFF271A0C),
    containerDark: Color(0xFF4C3A25),
    onContainerDark: Color(0xFFEADFD3),
    bubbleDark: Color(0xFF392F24),
    glowDark: Color(0xFF7F6343),
    haze: Color(0xFFEAC8C1),
    hazeDark: Color(0xFF64443D),
    tintHue: 75,
    tintChroma: .75,
  );

  static const graphite = MaevePalette(
    id: MaevePaletteId.graphite,
    label: '石墨',
    seed: Color(0xFF2C2E32),
    pressed: Color(0xFF1D1F23),
    container: Color(0xFFE3E6EC),
    onContainer: Color(0xFF2C2E32),
    bubble: Color(0xFFE0E3E8),
    glow: Color(0xFFBBBEC3),
    seedDark: Color(0xFFE5E8ED),
    pressedDark: Color(0xFFCED1D6),
    onSeedDark: Color(0xFF191B1E),
    containerDark: Color(0xFF3B3D41),
    onContainerDark: Color(0xFFDEE1E7),
    bubbleDark: Color(0xFF2E3034),
    glowDark: Color(0xFF616368),
    haze: Color(0xFFCED1D6),
    hazeDark: Color(0xFF4A4D52),
    tintHue: 262,
    tintChroma: .25,
  );

  /// 展示顺序 = 设置页顺序,第一套是默认。
  static const all = <MaevePalette>[reverie, tide, dusk, pine, amber, graphite];

  static MaevePalette byId(MaevePaletteId id) =>
      all.firstWhere((palette) => palette.id == id, orElse: () => reverie);
}

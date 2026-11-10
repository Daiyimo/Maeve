import 'dart:ui' show lerpDouble;

import 'package:flutter/material.dart';

import 'maeve_palette.dart';

/// 玻璃层级。决定填充透明度、是否模糊、描边与阴影。
///
/// | 层级 | 用在哪 | 模糊 |
/// |---|---|---|
/// | [chrome] | 输入条、顶栏胶囊、圆钮 —— 浮在滚动内容上的 | 有 |
/// | [sheet] | 侧栏、底部弹层、抽屉 | 有 |
/// | [card] | 设置卡、记忆卡、回答卡 —— 躺在页面背景上的 | 无 |
/// | [pill] | 用户气泡、图标 chip、状态胶囊 —— 列表里的小件 | 无 |
///
/// 只有 chrome / sheet 真模糊:它们背后是会滚动的内容,模糊才有意义;
/// card / pill 背后只是静态背景,半透明填充就已经读作"磨砂",
/// 再上 `BackdropFilter` 只是白白烧 GPU(列表里几十个就是几十次模糊)。
enum MaeveGlassTier { chrome, sheet, card, pill }

/// 一种玻璃材质的全部参数。
@immutable
class MaeveGlassStyle {
  const MaeveGlassStyle({
    required this.fill,
    required this.fallbackFill,
    required this.hairline,
    required this.rim,
    this.blurSigma = 0,
    this.saturation = 1,
    this.hairlineWidth = 0.5,
    this.shadows = const [],
  });

  /// 开启模糊时的填充(带透明度)。
  final Color fill;

  /// 关闭模糊时的填充(低端机、省电、"减少透明效果"、高对比度模式)。
  /// 必须接近不透明,否则没有模糊时背后的字会透出来干扰阅读。
  final Color fallbackFill;

  /// 发丝描边:界定玻璃边缘。浅色是冷墨 8%,深色是白 9%。
  final Color hairline;
  final double hairlineWidth;

  /// 顶边高光起始色,向下 45% 处渐隐到透明 —— 光从上方打在玻璃边上。
  final Color rim;

  /// 高斯模糊 σ(逻辑像素)。0 = 不模糊。
  final double blurSigma;

  /// 模糊后的饱和度增益(1 = 不变)。iOS 式"透色":背后的颜色透过来更鲜一点。
  final double saturation;

  /// 只画在形状**外部**的投影(玻璃内部不被阴影弄脏)。
  final List<BoxShadow> shadows;

  bool get blurs => blurSigma > 0;

  MaeveGlassStyle copyWith({
    Color? fill,
    Color? fallbackFill,
    Color? hairline,
    Color? rim,
    double? blurSigma,
    double? saturation,
    double? hairlineWidth,
    List<BoxShadow>? shadows,
  }) {
    return MaeveGlassStyle(
      fill: fill ?? this.fill,
      fallbackFill: fallbackFill ?? this.fallbackFill,
      hairline: hairline ?? this.hairline,
      rim: rim ?? this.rim,
      blurSigma: blurSigma ?? this.blurSigma,
      saturation: saturation ?? this.saturation,
      hairlineWidth: hairlineWidth ?? this.hairlineWidth,
      shadows: shadows ?? this.shadows,
    );
  }

  static MaeveGlassStyle lerp(MaeveGlassStyle a, MaeveGlassStyle b, double t) {
    return MaeveGlassStyle(
      fill: Color.lerp(a.fill, b.fill, t)!,
      fallbackFill: Color.lerp(a.fallbackFill, b.fallbackFill, t)!,
      hairline: Color.lerp(a.hairline, b.hairline, t)!,
      rim: Color.lerp(a.rim, b.rim, t)!,
      blurSigma: lerpDouble(a.blurSigma, b.blurSigma, t)!,
      saturation: lerpDouble(a.saturation, b.saturation, t)!,
      hairlineWidth: lerpDouble(a.hairlineWidth, b.hairlineWidth, t)!,
      shadows: BoxShadow.lerpList(a.shadows, b.shadows, t) ?? const [],
    );
  }
}

/// 页面材质扩展:背景渐变、光晕、遮罩、四种玻璃。
///
/// 与 `MaeveTokens` 不同,这里随明暗变,也随"是否允许模糊"变。
class MaeveSurfaces extends ThemeExtension<MaeveSurfaces> {
  const MaeveSurfaces({
    required this.backdropTop,
    required this.backdropBottom,
    required this.glowPrimary,
    required this.glowSecondary,
    required this.scrim,
    required this.userBubble,
    required this.onUserBubble,
    required this.onUserBubbleSecondary,
    required this.chrome,
    required this.sheet,
    required this.card,
    required this.pill,
    this.blurEnabled = true,
  });

  /// 页面竖向渐变的顶 / 底。
  final Color backdropTop;
  final Color backdropBottom;

  /// 右上角主色光晕(已含透明度)。
  final Color glowPrimary;

  /// 左下角光晕(已含透明度):配主色的邻近色,紫罗兰配雾蓝。
  final Color glowSecondary;

  /// 抽屉 / 模态遮罩(已含透明度)。
  final Color scrim;

  /// 用户气泡底色(随配色换)。
  final Color userBubble;

  /// 用户气泡内主文字。
  final Color onUserBubble;

  /// 用户气泡内次级文字(必须 ≥5.8)。
  final Color onUserBubbleSecondary;

  final MaeveGlassStyle chrome;
  final MaeveGlassStyle sheet;
  final MaeveGlassStyle card;
  final MaeveGlassStyle pill;

  /// 总开关。关掉后所有层级都走 `fallbackFill`,不再创建 `BackdropFilter`。
  /// 由设置里的"减少透明效果"或高对比度模式决定。
  final bool blurEnabled;

  static MaeveSurfaces of(BuildContext context) =>
      Theme.of(context).extension<MaeveSurfaces>() ?? MaeveSurfaces.light;

  MaeveGlassStyle glass(MaeveGlassTier tier) => switch (tier) {
    MaeveGlassTier.chrome => chrome,
    MaeveGlassTier.sheet => sheet,
    MaeveGlassTier.card => card,
    MaeveGlassTier.pill => pill,
  };

  /// 默认配色的浅色材质(= 雾紫)。没有主题扩展时兜底用。
  static MaeveSurfaces get light =>
      MaevePalette.reverie.surfaces(Brightness.light);

  /// 默认配色的深色材质。
  static MaeveSurfaces get dark =>
      MaevePalette.reverie.surfaces(Brightness.dark);

  /// 默认配色的材质,可关掉模糊(降级路径)。
  static MaeveSurfaces forBrightness(
    Brightness brightness, {
    bool blurEnabled = true,
  }) => MaevePalette.reverie.surfaces(brightness, blurEnabled: blurEnabled);

  @override
  MaeveSurfaces copyWith({
    Color? backdropTop,
    Color? backdropBottom,
    Color? glowPrimary,
    Color? glowSecondary,
    Color? scrim,
    Color? userBubble,
    Color? onUserBubble,
    Color? onUserBubbleSecondary,
    MaeveGlassStyle? chrome,
    MaeveGlassStyle? sheet,
    MaeveGlassStyle? card,
    MaeveGlassStyle? pill,
    bool? blurEnabled,
  }) {
    return MaeveSurfaces(
      backdropTop: backdropTop ?? this.backdropTop,
      backdropBottom: backdropBottom ?? this.backdropBottom,
      glowPrimary: glowPrimary ?? this.glowPrimary,
      glowSecondary: glowSecondary ?? this.glowSecondary,
      scrim: scrim ?? this.scrim,
      userBubble: userBubble ?? this.userBubble,
      onUserBubble: onUserBubble ?? this.onUserBubble,
      onUserBubbleSecondary:
          onUserBubbleSecondary ?? this.onUserBubbleSecondary,
      chrome: chrome ?? this.chrome,
      sheet: sheet ?? this.sheet,
      card: card ?? this.card,
      pill: pill ?? this.pill,
      blurEnabled: blurEnabled ?? this.blurEnabled,
    );
  }

  @override
  MaeveSurfaces lerp(covariant MaeveSurfaces? other, double t) {
    if (other == null) return this;
    return MaeveSurfaces(
      backdropTop: Color.lerp(backdropTop, other.backdropTop, t)!,
      backdropBottom: Color.lerp(backdropBottom, other.backdropBottom, t)!,
      glowPrimary: Color.lerp(glowPrimary, other.glowPrimary, t)!,
      glowSecondary: Color.lerp(glowSecondary, other.glowSecondary, t)!,
      scrim: Color.lerp(scrim, other.scrim, t)!,
      userBubble: Color.lerp(userBubble, other.userBubble, t)!,
      onUserBubble: Color.lerp(onUserBubble, other.onUserBubble, t)!,
      onUserBubbleSecondary: Color.lerp(
        onUserBubbleSecondary,
        other.onUserBubbleSecondary,
        t,
      )!,
      chrome: MaeveGlassStyle.lerp(chrome, other.chrome, t),
      sheet: MaeveGlassStyle.lerp(sheet, other.sheet, t),
      card: MaeveGlassStyle.lerp(card, other.card, t),
      pill: MaeveGlassStyle.lerp(pill, other.pill, t),
      blurEnabled: t < 0.5 ? blurEnabled : other.blurEnabled,
    );
  }
}

/// 页面背景:竖向渐变 + 右上 / 左下两团光晕。
///
/// 铺在界面最底层,玻璃的"透色"与高光都靠它才看得出来。
class MaeveBackdrop extends StatelessWidget {
  const MaeveBackdrop({super.key});

  @override
  Widget build(BuildContext context) {
    final surfaces = MaeveSurfaces.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [surfaces.backdropTop, surfaces.backdropBottom],
        ),
      ),
      child: CustomPaint(
        painter: _MaeveGlowPainter(
          primary: surfaces.glowPrimary,
          secondary: surfaces.glowSecondary,
        ),
        size: Size.infinite,
      ),
    );
  }
}

class _MaeveGlowPainter extends CustomPainter {
  const _MaeveGlowPainter({required this.primary, required this.secondary});

  final Color primary;
  final Color secondary;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    // 右上:主色光晕;左下:邻近色光晕。半径按短边取,横竖屏都撑得开。
    final radius = size.shortestSide * 0.9;
    final topRight = Offset(size.width * 0.88, -size.height * 0.06);
    final bottomLeft = Offset(-size.width * 0.05, size.height * 1.02);
    canvas.drawRect(
      Offset.zero & size,
      Paint()
        ..shader = RadialGradient(
          colors: [primary, primary.withValues(alpha: 0)],
          radius: 0.62,
        ).createShader(Rect.fromCircle(center: topRight, radius: radius)),
    );
    canvas.drawRect(
      Offset.zero & size,
      Paint()
        ..shader = RadialGradient(
          colors: [secondary, secondary.withValues(alpha: 0)],
          radius: 0.58,
        ).createShader(Rect.fromCircle(center: bottomLeft, radius: radius)),
    );
  }

  @override
  bool shouldRepaint(_MaeveGlowPainter old) =>
      old.primary != primary || old.secondary != secondary;
}

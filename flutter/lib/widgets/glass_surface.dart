import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../design/maeve_surfaces.dart';
import '../design/maeve_tokens.dart';

export 'package:maeve_flutter/design/maeve_surfaces.dart'
    show MaeveGlassStyle, MaeveGlassTier, MaeveSurfaces;

/// 玻璃表面:**模糊背后的内容 → 叠半透明填充 → 发丝描边 + 顶边高光 → 外投影**。
///
/// ```dart
/// MaeveGlassSurface(
///   tier: MaeveGlassTier.chrome,
///   padding: const EdgeInsets.fromLTRB(12, 6, 6, 6),
///   child: Row(children: [/* 输入条内容 */]),
/// )
/// ```
///
/// ## 性能约定
///
/// - 只有 [MaeveGlassTier.chrome] / [MaeveGlassTier.sheet] 默认真模糊;同屏真模糊的
///   元素控制在 3 个以内(输入条、侧栏、弹层)。
/// - 模糊用 `ImageFilterConfig.blur(bounded: true)`:采样不越出形状边界,
///   边缘不会被外面的颜色"渗"进来(iOS 同款),且必须配裁剪,模糊产物不出形状。
/// - 降级:主题里 `MaeveSurfaces.blurEnabled == false`(设置"减少透明效果")
///   或系统高对比度时,不创建 `BackdropFilter`,改用近乎不透明的
///   `fallbackFill` —— 观感是"磨砂纸",可读性不降。
/// - 按压缩放做在内容上(`InkWell` 在填充之内),不做在整个玻璃上:
///   缩放一个会模糊的面板会每帧按新尺寸重采样背景。
///
/// ## 为什么阴影要单独画
///
/// 玻璃内部是半透明的,普通 `BoxShadow` 画在形状下方会透过玻璃、又被模糊
/// 一次,玻璃中间发灰。这里用 even-odd 裁剪,只在形状**外部**画投影。
class MaeveGlassSurface extends StatelessWidget {
  const MaeveGlassSurface({
    super.key,
    required this.child,
    this.tier = MaeveGlassTier.card,
    this.padding,
    this.tint,
    this.tintAlpha,
    this.blur,
    this.grouped = false,
    this.settled,
    this.style,
    this.borderRadius,
    this.circle = false,
    this.stadium = false,
    this.onTap,
    this.onLongPress,
  }) : assert(!(circle && stadium), 'circle 与 stadium 只能二选一');

  final Widget child;

  /// 材质层级,决定默认的填充 / 模糊 / 阴影 / 圆角。
  final MaeveGlassTier tier;

  final EdgeInsetsGeometry? padding;

  /// 着色(用户气泡的主色、页面图标的强调色)。null = 不加色。
  final Color? tint;

  /// 着色强度。null = 按明暗取默认值。
  final double? tintAlpha;

  /// 强制开 / 关模糊。null = 跟随层级默认值与主题总开关。
  final bool? blur;

  /// 与同一 `BackdropGroup` 下的其他玻璃共享一次背景采样。
  final bool grouped;

  /// 会动的玻璃(升起、被拖动的弹层)。0 = 在动:不采样背景,用不透明填充。
  /// 停稳后走向 1:模糊立刻开,藏在近乎不透明的填充后面,填充渐变回玻璃,所以
  /// 模糊出现时不跳。和 `blur: false` 不同,切换时模糊层留在树里,子树不重建。
  /// null = 一直是玻璃。
  final double? settled;

  /// 覆盖主题给的材质(极少用)。
  final MaeveGlassStyle? style;

  /// 覆盖层级默认圆角。
  final BorderRadiusGeometry? borderRadius;

  /// 圆形(圆钮、发送键底座)。
  final bool circle;

  /// 胶囊(半径永远等于高度一半)。
  final bool stadium;

  final VoidCallback? onTap;

  /// 长按(快捷入口:模型胶囊长按开 QuickPanel)。
  final VoidCallback? onLongPress;

  BorderRadiusGeometry get _radius =>
      borderRadius ??
      switch (tier) {
        // 第 4.1 节:chrome / sheet 28(composer 必须 28),card 14。
        MaeveGlassTier.chrome || MaeveGlassTier.sheet => const BorderRadius.all(
          Radius.circular(MaeveRadius.xl),
        ),
        MaeveGlassTier.card => const BorderRadius.all(
          Radius.circular(MaeveRadius.md),
        ),
        MaeveGlassTier.pill => const BorderRadius.all(Radius.circular(999)),
      };

  bool get _isStadium =>
      stadium ||
      (!circle && borderRadius == null && tier == MaeveGlassTier.pill);

  ShapeBorder get _shape {
    if (circle) return const CircleBorder();
    if (_isStadium) return const StadiumBorder();
    return RoundedSuperellipseBorder(borderRadius: _radius);
  }

  @override
  Widget build(BuildContext context) {
    final surfaces = MaeveSurfaces.of(context);
    final s = style ?? surfaces.glass(tier);
    final dark = Theme.of(context).brightness == Brightness.dark;
    final highContrast = MediaQuery.maybeHighContrastOf(context) ?? false;
    final reduce = !surfaces.blurEnabled || highContrast;
    final useBlur = !reduce && (blur ?? s.blurs);
    var fill = useBlur
        ? Color.lerp(s.fallbackFill, s.fill, settled ?? 1)!
        : (reduce || s.blurs ? s.fallbackFill : s.fill);
    final tintColor = tint;
    if (tintColor != null) {
      fill = Color.alphaBlend(
        tintColor.withValues(alpha: tintAlpha ?? (dark ? .34 : .58)),
        fill,
      );
    }
    final shape = _shape;
    final textDirection = Directionality.maybeOf(context);

    Widget content = child;
    if (padding != null) {
      content = Padding(padding: padding!, child: content);
    }
    if (onTap != null || onLongPress != null) {
      // 按压反馈做在内容上,不做在玻璃上:缩放一个会模糊的面板会每帧
      // 按新尺寸重采样背景。
      content = Material(
        type: MaterialType.transparency,
        child: InkWell(
          customBorder: shape,
          onTap: onTap,
          onLongPress: onLongPress,
          child: content,
        ),
      );
    }

    content = CustomPaint(
      foregroundPainter: _MaeveGlassEdgePainter(
        shape: shape,
        hairline: s.hairline,
        hairlineWidth: s.hairlineWidth,
        rim: s.rim,
        textDirection: textDirection,
      ),
      child: DecoratedBox(
        decoration: ShapeDecoration(shape: shape, color: fill),
        child: content,
      ),
    );

    if (useBlur) {
      final config = _filterFor(s.blurs ? s.blurSigma : 14, s.saturation);
      final sampling = settled != 0;
      content = grouped
          ? BackdropFilter.grouped(
              filterConfig: config,
              enabled: sampling,
              child: content,
            )
          : BackdropFilter(
              filterConfig: config,
              enabled: sampling,
              child: content,
            );
      // 有界模糊只限制采样来源,不裁剪产物;不裁剪会在形状外看到模糊残影。
      content = _clip(content);
    }

    if (s.shadows.isNotEmpty) {
      content = CustomPaint(
        painter: _MaeveGlassShadowPainter(
          shape: shape,
          shadows: s.shadows,
          textDirection: textDirection,
        ),
        child: content,
      );
    }
    return content;
  }

  Widget _clip(Widget child) {
    if (circle) return ClipOval(child: child);
    if (_isStadium) {
      return ClipRRect(
        borderRadius: const BorderRadius.all(Radius.circular(999)),
        child: child,
      );
    }
    return ClipRSuperellipse(borderRadius: _radius, child: child);
  }

  static ImageFilterConfig _filterFor(double sigma, double saturation) {
    final blur = ImageFilterConfig.blur(
      sigmaX: sigma,
      sigmaY: sigma,
      bounded: true,
    );
    if (saturation == 1) return blur;
    return ImageFilterConfig.compose(
      outer: ImageFilterConfig(ColorFilter.matrix(_saturate(saturation))),
      inner: blur,
    );
  }

  /// 保亮度的饱和度矩阵(Rec. 709 权重)。
  static List<double> _saturate(double s) {
    final r = 0.2126 * (1 - s);
    final g = 0.7152 * (1 - s);
    final b = 0.0722 * (1 - s);
    return <double>[
      r + s, g, b, 0, 0, //
      r, g + s, b, 0, 0, //
      r, g, b + s, 0, 0, //
      0, 0, 0, 1, 0, //
    ];
  }
}

/// 发丝描边 + 顶边高光。描边画在形状内侧,裁剪时不会被切掉一半。
class _MaeveGlassEdgePainter extends CustomPainter {
  _MaeveGlassEdgePainter({
    required this.shape,
    required this.hairline,
    required this.hairlineWidth,
    required this.rim,
    required this.textDirection,
  });

  final ShapeBorder shape;
  final Color hairline;
  final double hairlineWidth;
  final Color rim;
  final TextDirection? textDirection;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rect = Offset.zero & size;
    final width = math.max(hairlineWidth, 0.5);
    final path = shape.getOuterPath(
      rect.deflate(width / 2),
      textDirection: textDirection,
    );
    if (hairline.a > 0) {
      canvas.drawPath(
        path,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = width
          ..color = hairline,
      );
    }
    if (rim.a > 0) {
      final highlight = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = math.max(width, 0.8)
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [rim, rim.withValues(alpha: 0)],
          stops: const [0, 0.45],
        ).createShader(rect);
      canvas.drawPath(path, highlight);
    }
  }

  @override
  bool shouldRepaint(_MaeveGlassEdgePainter old) =>
      old.shape != shape ||
      old.hairline != hairline ||
      old.hairlineWidth != hairlineWidth ||
      old.rim != rim ||
      old.textDirection != textDirection;
}

/// 只在形状外部画的投影。
class _MaeveGlassShadowPainter extends CustomPainter {
  _MaeveGlassShadowPainter({
    required this.shape,
    required this.shadows,
    required this.textDirection,
  });

  final ShapeBorder shape;
  final List<BoxShadow> shadows;
  final TextDirection? textDirection;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rect = Offset.zero & size;
    var extent = 0.0;
    for (final s in shadows) {
      extent = math.max(
        extent,
        s.blurRadius * 2 + s.spreadRadius.abs() + s.offset.distance,
      );
    }
    final outside = Path()
      ..fillType = PathFillType.evenOdd
      ..addRect(rect.inflate(extent + 2))
      ..addPath(
        shape.getOuterPath(rect, textDirection: textDirection),
        Offset.zero,
      );
    canvas.save();
    canvas.clipPath(outside);
    for (final s in shadows) {
      canvas.drawPath(
        shape.getOuterPath(
          rect.shift(s.offset).inflate(s.spreadRadius),
          textDirection: textDirection,
        ),
        s.toPaint(),
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_MaeveGlassShadowPainter old) =>
      old.shape != shape ||
      !_sameShadows(old.shadows, shadows) ||
      old.textDirection != textDirection;

  static bool _sameShadows(List<BoxShadow> a, List<BoxShadow> b) {
    if (identical(a, b)) return true;
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }
}

/// 玻璃圆钮:顶栏的加号、发送键的底座。
///
/// 圆钮浮在静态背景上(顶栏、侧栏),按约定不真模糊,只拿玻璃的填充、
/// 发丝描边与顶边高光;`prominent` 用实色主色(发送键),不加描边。
class MaeveGlassButton extends StatelessWidget {
  const MaeveGlassButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.prominent = false,
    this.compact = false,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;
  final bool prominent;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final surfaces = MaeveSurfaces.of(context);
    final chrome = surfaces.chrome;
    final scheme = Theme.of(context).colorScheme;
    final dimension = compact ? 38.0 : 44.0;
    final shape = const CircleBorder();
    final fill = prominent ? scheme.primary : chrome.fill;
    final foreground = prominent ? scheme.onPrimary : scheme.onSurface;

    Widget button = CustomPaint(
      foregroundPainter: prominent
          ? null
          : _MaeveGlassEdgePainter(
              shape: shape,
              hairline: chrome.hairline,
              hairlineWidth: chrome.hairlineWidth,
              rim: chrome.rim,
              textDirection: Directionality.maybeOf(context),
            ),
      child: DecoratedBox(
        decoration: ShapeDecoration(shape: shape, color: fill),
        child: SizedBox.square(
          dimension: dimension,
          child: Center(
            child: Icon(icon, size: compact ? 18 : 20, color: foreground),
          ),
        ),
      ),
    );
    if (!prominent && chrome.shadows.isNotEmpty) {
      button = CustomPaint(
        painter: _MaeveGlassShadowPainter(
          shape: shape,
          shadows: chrome.shadows,
          textDirection: Directionality.maybeOf(context),
        ),
        child: button,
      );
    }

    return Tooltip(
      message: tooltip,
      child: Semantics(
        button: true,
        label: tooltip,
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(customBorder: shape, onTap: onPressed, child: button),
        ),
      ),
    );
  }
}

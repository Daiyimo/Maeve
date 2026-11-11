import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../design/maeve_shapes.dart';
import '../design/maeve_surfaces.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

export '../design/maeve_surfaces.dart'
    show MaeveGlassStyle, MaeveGlassTier, MaeveSurfaces;

/// 玻璃表面:**模糊背后的内容 → 半透明填充 → 发丝描边 + 顶边高光 → 外投影**。
///
/// ```dart
/// MaeveGlassSurface(
///   tier: MaeveGlassTier.chrome,
///   shape: MaeveShapes.composer,
///   child: Row(children: [/* 输入条内容 */]),
/// )
/// ```
///
/// ## 约定
///
/// - 只有 [MaeveGlassTier.chrome] / [MaeveGlassTier.sheet] 默认真模糊;
///   同屏真模糊的元素控制在 3 个以内,顶栏多件共用一个 [BackdropGroup]。
/// - 有界模糊 `ImageFilterConfig.blur(bounded: true)`:采样不越出形状边界。
/// - 降级:主题里 `blurEnabled == false`(设置"减少透明效果")或系统高对比度时,
///   不创建 [BackdropFilter],改用近乎不透明的 `fallbackFill`。
/// - 按压缩放做在内容上,不做在整个玻璃上:缩放一个会模糊的面板会每帧重采样背景。
///
/// ## 为什么阴影要单独画
///
/// 玻璃内部是半透明的,普通 `BoxShadow` 画在形状下方会透过玻璃、又被模糊一次,
/// 玻璃中间发灰。这里用 even-odd 裁剪,只在形状**外部**画投影。
class MaeveGlassSurface extends StatelessWidget {
  const MaeveGlassSurface({
    super.key,
    required this.child,
    this.tier = MaeveGlassTier.card,
    this.shape,
    this.padding,
    this.tint,
    this.tintAlpha,
    this.blur,
    this.grouped = false,
    this.settled,
    this.onTap,
    this.onLongPress,
    this.framed = true,
  });

  final Widget child;

  /// 材质层级,决定默认的填充 / 模糊 / 阴影 / 形状。
  final MaeveGlassTier tier;

  /// 覆盖层级默认形状(顶栏、抽屉用无圆角矩形)。
  final ShapeBorder? shape;

  final EdgeInsetsGeometry? padding;

  /// 强调色淡淡洗一层(选中行、进行中的卡)。null = 不加色。
  final Color? tint;
  final double? tintAlpha;

  /// 强制开 / 关模糊。null = 跟随层级默认值与主题总开关。
  final bool? blur;

  /// 与同一 [BackdropGroup] 下的其他玻璃共享一次背景采样。
  final bool grouped;

  /// 会动的玻璃(升起、被拖动的弹层)。0 = 在动:不采样背景,用不透明填充。
  final double? settled;

  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  /// Page chrome shares the canvas without a surrounding card outline.
  final bool framed;

  ShapeBorder get _shape =>
      shape ??
      switch (tier) {
        MaeveGlassTier.chrome => MaeveShapes.button,
        MaeveGlassTier.sheet => MaeveShapes.sheet,
        MaeveGlassTier.card => MaeveShapes.card,
        MaeveGlassTier.pill => MaeveShapes.pill,
      };

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final surfaces = theme.surfaces;
    final s = surfaces.tier(tier);
    final highContrast = MediaQuery.maybeHighContrastOf(context) ?? false;
    final reduce = !surfaces.blurEnabled || highContrast;
    final useBlur = !reduce && (blur ?? s.blurs);
    var fill = useBlur
        ? Color.lerp(s.fallbackFill, s.fill, settled ?? 1)!
        : (reduce || s.blurs ? s.fallbackFill : s.fill);
    final tintColor = tint;
    if (tintColor != null) {
      fill = Color.alphaBlend(
        tintColor.withValues(alpha: tintAlpha ?? .10),
        fill,
      );
    }
    // Unframed chrome should dissolve into the canvas, not form a grey bar.
    // Keep the opaque accessibility fallback intact.
    if (!framed && !reduce) fill = fill.withValues(alpha: fill.a * .38);
    final shape = _shape;
    final textDirection = Directionality.maybeOf(context);

    Widget content = child;
    if (padding != null) {
      content = Padding(padding: padding!, child: content);
    }
    if (onTap != null || onLongPress != null) {
      content = Material(
        type: MaterialType.transparency,
        child: InkWell(
          customBorder: shape,
          splashFactory: NoSplash.splashFactory,
          highlightColor: Colors.transparent,
          onTap: onTap,
          onLongPress: onLongPress,
          child: content,
        ),
      );
    }

    content = CustomPaint(
      foregroundPainter: !framed
          ? null
          : _GlassEdgePainter(
              shape: shape,
              hairline: s.hairline,
              hairlineWidth: s.hairlineWidth,
              rim: s.rim,
              rimFraction: s.rimFraction,
              textDirection: textDirection,
            ),
      child: DecoratedBox(
        decoration: ShapeDecoration(shape: shape, color: fill),
        child: _Sheen(
          shape: shape,
          sheen: framed ? s.sheen : Colors.transparent,
          stop: s.sheenStop,
          child: content,
        ),
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
      content = _clip(content, shape);
    }

    if (framed && s.shadows.isNotEmpty) {
      content = CustomPaint(
        painter: _GlassShadowPainter(
          shape: shape,
          shadows: s.shadows,
          textDirection: textDirection,
        ),
        child: content,
      );
    }
    return content;
  }

  Widget _clip(Widget child, ShapeBorder shape) {
    if (shape is CircleBorder) return ClipOval(child: child);
    if (shape is StadiumBorder) {
      return ClipRRect(
        borderRadius: const BorderRadius.all(Radius.circular(999)),
        child: child,
      );
    }
    if (shape is RoundedSuperellipseBorder) {
      return ClipRSuperellipse(borderRadius: shape.borderRadius, child: child);
    }
    if (shape is RoundedRectangleBorder) {
      return ClipRRect(borderRadius: shape.borderRadius, child: child);
    }
    return child;
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
    final r = .2126 * (1 - s);
    final g = .7152 * (1 - s);
    final b = .0722 * (1 - s);
    return <double>[
      r + s, g, b, 0, 0, //
      r, g + s, b, 0, 0, //
      r, g, b + s, 0, 0, //
      0, 0, 0, 1, 0, //
    ];
  }
}

/// 玻璃内部自上而下的一道提亮(见 [MaeveGlassStyle.sheen])。
class _Sheen extends StatelessWidget {
  const _Sheen({
    required this.shape,
    required this.sheen,
    required this.stop,
    required this.child,
  });

  final ShapeBorder shape;
  final Color sheen;
  final double stop;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (sheen.a <= 0) return child;
    return DecoratedBox(
      // 自上而下的一道提亮:玻璃"被上方的光照到"。
      decoration: ShapeDecoration(
        shape: shape,
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [sheen, sheen.withValues(alpha: 0)],
          stops: [0, stop.clamp(.05, 1.0)],
        ),
      ),
      child: child,
    );
  }
}

/// 发丝描边 + 顶边高光。描边画在形状内侧,裁剪时不会被切掉一半。
class _GlassEdgePainter extends CustomPainter {
  _GlassEdgePainter({
    required this.shape,
    required this.hairline,
    required this.hairlineWidth,
    required this.rim,
    required this.rimFraction,
    required this.textDirection,
  });

  final ShapeBorder shape;
  final Color hairline;
  final double hairlineWidth;
  final Color rim;
  final double rimFraction;
  final TextDirection? textDirection;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rect = Offset.zero & size;
    final width = math.max(hairlineWidth, .5);
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
      canvas.drawPath(
        path,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = math.max(width, .8)
          ..shader = LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [rim, rim.withValues(alpha: 0)],
            stops: [0, rimFraction.clamp(.05, 1.0)],
          ).createShader(rect),
      );
    }
  }

  @override
  bool shouldRepaint(_GlassEdgePainter old) =>
      old.shape != shape ||
      old.hairline != hairline ||
      old.hairlineWidth != hairlineWidth ||
      old.rim != rim ||
      old.rimFraction != rimFraction;
}

/// 只在形状外部画的投影。
class _GlassShadowPainter extends CustomPainter {
  _GlassShadowPainter({
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
  bool shouldRepaint(_GlassShadowPainter old) =>
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

/// 玻璃圆钮:顶栏动作键、输入条的两颗圆键。
///
/// 圆钮浮在静态背景上,按约定不取样,只拿玻璃的填充、发丝描边与顶边高光;
/// [prominent] 用实色强调色(发送键)。
class MaeveGlassButton extends StatelessWidget {
  const MaeveGlassButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.prominent = false,
    this.compact = false,
    this.enabled = true,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;
  final bool prominent;
  final bool compact;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;
    final chrome = theme.surfaces.chrome;
    final dimension = compact ? 36.0 : MaeveSize.iconButton;
    const shape = CircleBorder();
    final active = enabled && onPressed != null;
    final fill = prominent ? colors.accent : chrome.fill;
    final foreground = prominent
        ? colors.onAccent
        : (active ? colors.ink : colors.ink3);

    Widget button = CustomPaint(
      foregroundPainter: prominent
          ? null
          : _GlassEdgePainter(
              shape: shape,
              hairline: chrome.hairline,
              hairlineWidth: chrome.hairlineWidth,
              rim: chrome.rim,
              rimFraction: .5,
              textDirection: Directionality.maybeOf(context),
            ),
      child: DecoratedBox(
        decoration: ShapeDecoration(shape: shape, color: fill),
        child: _Sheen(
          shape: shape,
          sheen: prominent ? Colors.transparent : chrome.sheen,
          stop: .5,
          child: SizedBox.square(
            dimension: dimension,
            child: Center(
              child: Icon(icon, size: compact ? 18 : 20, color: foreground),
            ),
          ),
        ),
      ),
    );
    if (!prominent && chrome.shadows.isNotEmpty) {
      button = CustomPaint(
        painter: _GlassShadowPainter(
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
        enabled: active,
        label: tooltip,
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            customBorder: shape,
            splashFactory: NoSplash.splashFactory,
            highlightColor: Colors.transparent,
            onTap: active ? onPressed : null,
            child: button,
          ),
        ),
      ),
    );
  }
}

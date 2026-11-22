import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../design/maeve_shapes.dart';
import '../design/maeve_surfaces.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../ui/common/haptics.dart';
import '../ui/common/pressable.dart';

export '../design/maeve_surfaces.dart'
    show MaeveGlassDensity, MaeveGlassStyle, MaeveGlassTier, MaeveSurfaces;

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
/// - 降级:系统高对比度(或主题 `blurEnabled == false`)时不创建 [BackdropFilter],
///   改用近乎不透明的 `fallbackFill`。
/// - 浓度(清透 / 均衡 / 浓郁)来自主题的 [MaeveSurfaces.density],由设置页
///   "液态玻璃"档位驱动,见 [MaeveGlassDensity]。
/// - 按压缩放做在内容上,不做在整个玻璃上:缩放一个会模糊的面板会每帧重采样背景。
///
/// ## 为什么阴影要单独画
///
/// 玻璃内部是半透明的,普通 `BoxShadow` 画在形状下方会透过玻璃、又被模糊一次,
/// 玻璃中间发灰。这里用 even-odd 裁剪,只在形状**外部**画投影。
class MaeveGlassSurface extends StatefulWidget {
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
    this.shaderEnabled,
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

  /// 会动的玻璃(升起、被拖动的弹层)。只有 1 表示完全停稳并允许采样。
  final double? settled;

  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  /// false 时只省略外阴影;边、斜带和顶光仍然绘制。
  final bool framed;

  /// null 跟随 Impeller 能力;false 可强制走原生模糊回退。
  final bool? shaderEnabled;

  ShapeBorder get _shape =>
      shape ??
      switch (tier) {
        MaeveGlassTier.chrome => MaeveShapes.button,
        MaeveGlassTier.sheet => MaeveShapes.sheet,
        MaeveGlassTier.card => MaeveShapes.card,
        MaeveGlassTier.pill => MaeveShapes.pill,
      };

  @override
  State<MaeveGlassSurface> createState() => _MaeveGlassSurfaceState();
}

class _MaeveGlassSurfaceState extends State<MaeveGlassSurface> {
  // Host widget tests compile the shader but disable its runtime GPU path.
  // Emulator/device builds keep it enabled by default.
  static const bool _runtimeShaderEnabled = bool.fromEnvironment(
    'MAEVE_GLASS_SHADER',
    defaultValue: true,
  );

  ui.FragmentProgram? _program;
  ui.FragmentShader? _shader;
  Object? _shaderKey;

  bool get _shaderSupported =>
      _runtimeShaderEnabled &&
      ui.ImageFilter.isShaderFilterSupported &&
      (widget.shaderEnabled ?? true);

  @override
  void initState() {
    super.initState();
    unawaited(_loadShader());
  }

  @override
  void didUpdateWidget(covariant MaeveGlassSurface oldWidget) {
    super.didUpdateWidget(oldWidget);
    final wasSupported =
        _runtimeShaderEnabled &&
        ui.ImageFilter.isShaderFilterSupported &&
        (oldWidget.shaderEnabled ?? true);
    if (_shaderSupported && !wasSupported) {
      unawaited(_loadShader());
    }
  }

  Future<void> _loadShader() async {
    if (!_shaderSupported) return;
    try {
      final program = await ui.FragmentProgram.fromAsset(
        'shaders/liquid_glass.frag',
      );
      if (!mounted) return;
      setState(() => _program = program);
    } catch (_) {
      if (!mounted) return;
      setState(() => _program = null);
    }
  }

  @override
  void dispose() {
    _shader?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final surfaces = theme.surfaces;
    final s = surfaces.tier(widget.tier);
    final highContrast = MediaQuery.maybeHighContrastOf(context) ?? false;
    final reduce = !surfaces.blurEnabled || highContrast;
    final useBlur = !reduce && (widget.blur ?? s.blurs);
    final sampling = useBlur && (widget.settled ?? 1) == 1;
    var fill = sampling
        ? s.fill
        : (useBlur || reduce ? s.fallbackFill : s.fill);
    final tintColor = widget.tint;
    if (tintColor != null) {
      fill = Color.alphaBlend(
        tintColor.withValues(alpha: widget.tintAlpha ?? .10),
        fill,
      );
    }
    final shape = widget._shape;
    final textDirection = Directionality.maybeOf(context);

    Widget content = widget.child;
    if (widget.padding != null) {
      content = Padding(padding: widget.padding!, child: content);
    }
    if (widget.onTap != null || widget.onLongPress != null) {
      content = Material(
        type: MaterialType.transparency,
        child: InkWell(
          customBorder: shape,
          splashFactory: NoSplash.splashFactory,
          highlightColor: Colors.transparent,
          onTap: widget.onTap,
          onLongPress: widget.onLongPress,
          child: content,
        ),
      );
    }

    // 从下到上:折射背景 → 斜带 → 填充 → 顶光 → 内容 → 受光边。
    content = CustomPaint(
      painter: _GlassBevelPainter(
        style: s,
        shape: shape,
        textDirection: textDirection,
      ),
      child: DecoratedBox(
        decoration: ShapeDecoration(shape: shape, color: fill),
        child: _Sheen(
          shape: shape,
          sheen: s.sheen,
          stop: s.sheenStop,
          child: content,
        ),
      ),
    );
    content = CustomPaint(
      foregroundPainter: _GlassEdgePainter(
        style: s,
        shape: shape,
        textDirection: textDirection,
      ),
      child: content,
    );

    if (sampling) {
      // Capture the original child before assigning content to the builder;
      // capturing the mutable variable would make the builder wrap itself.
      final child = content;
      if (_runtimeShaderEnabled) {
        content = LayoutBuilder(
          builder: (context, constraints) {
            final config = _filterConfig(
              s,
              size: constraints.biggest,
              shape: shape,
              textDirection: textDirection,
            );
            return _clip(_backdrop(config, child), shape);
          },
        );
      } else {
        final config = _filterFor(
          s.fallbackBlurSigma,
          s.saturation,
          s.brightness,
          s.contrast,
        );
        content = _clip(_backdrop(config, child), shape);
      }
    }

    if (widget.framed && s.shadows.isNotEmpty) {
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

  Widget _backdrop(ImageFilterConfig config, Widget child) => widget.grouped
      ? BackdropFilter.grouped(
          filterConfig: config,
          enabled: true,
          child: child,
        )
      : BackdropFilter(filterConfig: config, enabled: true, child: child);

  ImageFilterConfig _filterConfig(
    MaeveGlassStyle style, {
    required Size size,
    required ShapeBorder shape,
    required TextDirection? textDirection,
  }) {
    if (!size.isFinite || size.isEmpty) {
      return _filterFor(
        style.fallbackBlurSigma,
        style.saturation,
        style.brightness,
        style.contrast,
      );
    }
    final sigma = style.blurs ? style.blurSigma : style.fallbackBlurSigma;
    final program = _program;
    final refractive =
        style.refractionStrength > 0 ||
        style.chromaticAberration > 0 ||
        style.specularStrength > 0;
    if (!_shaderSupported || program == null || !refractive) {
      return _filterFor(
        style.fallbackBlurSigma,
        style.saturation,
        style.brightness,
        style.contrast,
      );
    }

    final radii = _radiiFor(shape, size, textDirection);
    final key = (size, radii, style);
    if (_shader == null || _shaderKey != key) {
      _shader?.dispose();
      final shader = program.fragmentShader();
      final width = size.width <= 0 ? 1.0 : size.width;
      _setVec4(shader, 'uRadii', [
        (radii.$1 / width).clamp(0, .5),
        (radii.$2 / width).clamp(0, .5),
        (radii.$3 / width).clamp(0, .5),
        (radii.$4 / width).clamp(0, .5),
      ]);
      _setVec2(shader, 'uSurfaceSize', [size.width, size.height]);
      _setVec4(shader, 'uOptics', [
        style.refractionStrength,
        style.refractionWidth,
        style.chromaticAberration,
        style.specularStrength,
      ]);
      _setVec4(shader, 'uTone', [
        style.brightness,
        style.contrast,
        style.saturation,
        0,
      ]);
      _shader = shader;
      _shaderKey = key;
    }

    return ImageFilterConfig.compose(
      outer: ImageFilterConfig(ui.ImageFilter.shader(_shader!)),
      inner: ImageFilterConfig.blur(
        sigmaX: sigma,
        sigmaY: sigma,
        bounded: true,
      ),
    );
  }

  static void _setVec2(ui.FragmentShader shader, String name, List<double> v) {
    for (var i = 0; i < 2; i++) {
      shader.getUniformFloat(name, i).set(v[i]);
    }
  }

  static void _setVec4(ui.FragmentShader shader, String name, List<double> v) {
    for (var i = 0; i < 4; i++) {
      shader.getUniformFloat(name, i).set(v[i]);
    }
  }

  static (double, double, double, double) _radiiFor(
    ShapeBorder shape,
    Size size,
    TextDirection? textDirection,
  ) {
    if (shape is CircleBorder || shape is StadiumBorder) {
      final radius = size.shortestSide / 2;
      return (radius, radius, radius, radius);
    }
    if (shape is RoundedRectangleBorder) {
      final r = shape.borderRadius.resolve(textDirection);
      return (r.topLeft.x, r.topRight.x, r.bottomRight.x, r.bottomLeft.x);
    }
    if (shape is RoundedSuperellipseBorder) {
      final r = shape.borderRadius.resolve(textDirection);
      return (r.topLeft.x, r.topRight.x, r.bottomRight.x, r.bottomLeft.x);
    }
    return (0, 0, 0, 0);
  }

  static Widget _clip(Widget child, ShapeBorder shape) {
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
}

/// Native blur + tone fallback used when the refractive shader is unavailable.
ImageFilterConfig _filterFor(
  double sigma,
  double saturation,
  double brightness,
  double contrast,
) {
  final blur = ImageFilterConfig.blur(
    sigmaX: sigma,
    sigmaY: sigma,
    bounded: true,
  );
  final plain = saturation == 1 && brightness == 1 && contrast == 1;
  if (plain) return blur;
  return ImageFilterConfig.compose(
    outer: ImageFilterConfig(
      ColorFilter.matrix(_tone(saturation, contrast, brightness)),
    ),
    inner: blur,
  );
}

/// 保亮度的饱和度矩阵(Rec. 709 权重)。
List<double> _saturate(double s) {
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

/// 把 saturate / contrast / brightness 三个滤镜按 CSS 的顺序合成一个 4×5 矩阵。
List<double> _tone(double s, double c, double b) {
  final sat = _asAffine(_saturate(s));
  final con = _asAffine(<double>[
    c, 0, 0, 0, .5 * (1 - c), //
    0, c, 0, 0, .5 * (1 - c), //
    0, 0, c, 0, .5 * (1 - c), //
    0, 0, 0, 1, 0,
  ]);
  final bri = _asAffine(<double>[
    b, 0, 0, 0, 0, //
    0, b, 0, 0, 0, //
    0, 0, b, 0, 0, //
    0, 0, 0, 1, 0,
  ]);
  final (m, o) = _compose(_compose(bri, con), sat);
  return <double>[
    for (var row = 0; row < 4; row++) ...[
      m[row][0],
      m[row][1],
      m[row][2],
      m[row][3],
      o[row],
    ],
  ];
}

(List<List<double>>, List<double>) _asAffine(List<double> m) {
  final a = <List<double>>[];
  final o = <double>[];
  for (var row = 0; row < 4; row++) {
    a.add(m.sublist(row * 5, row * 5 + 4));
    o.add(m[row * 5 + 4]);
  }
  return (a, o);
}

(List<List<double>>, List<double>) _compose(
  (List<List<double>>, List<double>) second,
  (List<List<double>>, List<double>) first,
) {
  final (a2, o2) = second;
  final (a1, o1) = first;
  final a = List.generate(4, (_) => List<double>.filled(4, 0));
  final o = List<double>.filled(4, 0);
  for (var i = 0; i < 4; i++) {
    for (var j = 0; j < 4; j++) {
      var sum = 0.0;
      for (var k = 0; k < 4; k++) {
        sum += a2[i][k] * a1[k][j];
      }
      a[i][j] = sum;
    }
    for (var j = 0; j < 4; j++) {
      var sum = 0.0;
      for (var k = 0; k < 4; k++) {
        sum += a2[i][k] * o1[k];
      }
      o[i] = sum + o2[i];
    }
  }
  return (a, o);
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

/// 液态玻璃的边:四条声明里的 `box-shadow` 那一半。
///
/// glasswork 的一片玻璃,光是这样落在边上的(从外到内):
///
/// 1. **均匀发丝线** —— 玻璃唯一"硬"的轮廓(那条 even line);
/// 2. **定向受光边** —— 光在**左上**被接住,右下渐隐(不是一整圈一样亮);
/// 3. **顶边高光** —— 光从上方打来,沿受光面往里渐隐;
/// 4. **斜面带** —— 贴在边内几像素的四条软带:顶亮、左右更弱、底暗,
///    读出玻璃的"厚度"。带子始终在边内 —— 宽了会把背后的颜色洗白。
class _GlassEdgePainter extends CustomPainter {
  _GlassEdgePainter({
    required this.style,
    required this.shape,
    required this.textDirection,
    this.rimFraction,
  });

  final MaeveGlassStyle style;
  final ShapeBorder shape;
  final TextDirection? textDirection;

  /// 覆盖层级的顶边高光比例(圆钮用 .5:28dp 的药丸不该整块都是高光)。
  final double? rimFraction;

  double get _rimFraction => (rimFraction ?? style.rimFraction).clamp(.05, 1.0);

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rect = Offset.zero & size;
    final width = math.max(style.hairlineWidth, .5);
    final path = shape.getOuterPath(
      rect.deflate(width / 2),
      textDirection: textDirection,
    );

    // ① 均匀发丝线:轮廓。太淡整块玻璃没有边界,读作一片发白的水渍。
    if (style.hairline.a > 0) {
      canvas.drawPath(
        path,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = width
          ..color = style.hairline,
      );
    }

    // ② 定向受光边:左上接住光,右下渐隐 —— 玻璃因此有了光向。
    if (style.catchLight.a > 0) {
      canvas.drawPath(
        path,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = math.max(width, .8)
          ..shader = LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              style.catchLight,
              style.catchLight.withValues(alpha: style.catchLight.a * .55),
              style.catchLight.withValues(alpha: 0),
            ],
            stops: const [0, .45, 1],
          ).createShader(rect),
      );
    }

    // ③ 顶边高光:受光面,往里渐隐。
    if (style.rim.a > 0) {
      canvas.drawPath(
        path,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = math.max(width, .8)
          ..shader = LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [style.rim, style.rim.withValues(alpha: 0)],
            stops: [0, _rimFraction],
          ).createShader(rect),
      );
    }
  }

  @override
  bool shouldRepaint(_GlassEdgePainter old) =>
      old.style != style ||
      old.shape != shape ||
      old.rimFraction != rimFraction ||
      old.textDirection != textDirection;
}

/// 斜带 painter:玻璃的"厚度",画在**填充之上、内容之下**
/// (CSS 里 inset box-shadow 的位置)。
///
/// 四条软带贴在边内几像素:顶亮、左右更弱、底暗。裁进形状画,不出边。
class _GlassBevelPainter extends CustomPainter {
  const _GlassBevelPainter({
    required this.style,
    required this.shape,
    this.textDirection,
  });

  final MaeveGlassStyle style;
  final ShapeBorder shape;
  final TextDirection? textDirection;

  bool get _hasBevel =>
      style.bevelTop.a > 0 || style.bevelSide.a > 0 || style.bevelBase.a > 0;

  @override
  void paint(Canvas canvas, Size size) {
    final band = style.band;
    if (!_hasBevel || band <= 0 || size.height <= band || size.width <= band) {
      return;
    }
    final rect = Offset.zero & size;
    canvas.save();
    canvas.clipPath(shape.getOuterPath(rect, textDirection: textDirection));
    void bandRect(Rect area, Gradient gradient) {
      if (area.width <= 0 || area.height <= 0) return;
      canvas.drawRect(area, Paint()..shader = gradient.createShader(area));
    }

    if (style.bevelTop.a > 0) {
      bandRect(
        Rect.fromLTWH(0, 0, size.width, band * 1.2),
        LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [style.bevelTop, style.bevelTop.withValues(alpha: 0)],
        ),
      );
    }
    if (style.bevelSide.a > 0) {
      bandRect(
        Rect.fromLTWH(0, 0, band, size.height),
        LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: [style.bevelSide, style.bevelSide.withValues(alpha: 0)],
        ),
      );
      // 右侧弱一档:光主要来自左上。
      bandRect(
        Rect.fromLTWH(size.width - band, 0, band, size.height),
        LinearGradient(
          begin: Alignment.centerRight,
          end: Alignment.centerLeft,
          colors: [
            style.bevelSide.withValues(alpha: style.bevelSide.a * .55),
            style.bevelSide.withValues(alpha: 0),
          ],
        ),
      );
    }
    if (style.bevelBase.a > 0) {
      bandRect(
        Rect.fromLTWH(0, size.height - band, size.width, band),
        LinearGradient(
          begin: Alignment.bottomCenter,
          end: Alignment.topCenter,
          colors: [style.bevelBase, style.bevelBase.withValues(alpha: 0)],
        ),
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_GlassBevelPainter old) =>
      old.style != style ||
      old.shape != shape ||
      old.textDirection != textDirection;
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

/// 玻璃圆钮:顶栏动作键、输入条的两颗圆键、抽屉的关闭键、页面顶栏的刷新键。
///
/// 圆钮浮在静态背景上,按约定不取样,只拿玻璃的填充、受光边、斜带与顶边高光;
/// [prominent] 用实色强调色(发送键)——仍然带一道白色受光边,读作"能按的玻璃",
/// 不是一块塑料色块。[fill] / [foreground] 可覆盖底色与图形色(停止键用实色墨)。
/// 视觉 36/40,可点域恒 48(顶栏动作的触控契约);按压反馈走 Pressable:
/// 内容缩 0.97 + 触点径向流光 + 一次触感。圆钮不取样,缩放不会引发重采样。
class MaeveGlassButton extends StatelessWidget {
  const MaeveGlassButton({
    super.key,
    required this.icon,
    required this.tooltip,
    required this.onPressed,
    this.prominent = false,
    this.compact = false,
    this.enabled = true,
    this.fill,
    this.foreground,
    this.haptic,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback? onPressed;
  final bool prominent;
  final bool compact;
  final bool enabled;

  /// 覆盖填充(prominent 的强调色、停止键的实色墨)。
  final Color? fill;

  /// 覆盖图形色。
  final Color? foreground;

  /// 点按时给的触感;null = 不震。
  final MaeveHaptic? haptic;

  /// 实色底上的受光边:白斜带 + 一道暗轮廓,让色块也读出玻璃的光向。
  static MaeveGlassStyle _solidEdge(MaeveGlassStyle base) => base.copyWith(
    catchLight: const Color(0xFFFFFFFF).withValues(alpha: .55),
    bevelTop: const Color(0xFFFFFFFF).withValues(alpha: .20),
    bevelSide: const Color(0xFFFFFFFF).withValues(alpha: .09),
    bevelBase: const Color(0xFF000000).withValues(alpha: .08),
  );

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;
    final chrome = theme.surfaces.chrome;
    final dimension = compact ? 36.0 : MaeveSize.iconButton;
    const shape = CircleBorder();
    final active = enabled && onPressed != null;
    final solid = prominent || fill != null;
    final edge = solid ? _solidEdge(chrome) : chrome;
    final buttonFill = fill ?? (prominent ? colors.accent : chrome.fill);
    final foreground =
        this.foreground ??
        (prominent ? colors.onAccent : (active ? colors.ink : colors.ink3));

    final textDirection = Directionality.maybeOf(context);
    Widget button = CustomPaint(
      painter: _GlassBevelPainter(
        style: edge,
        shape: shape,
        textDirection: textDirection,
      ),
      child: DecoratedBox(
        decoration: ShapeDecoration(shape: shape, color: buttonFill),
        child: _Sheen(
          shape: shape,
          // 实色底也给一道提亮:色块因此是"被照到的玻璃",不是一块塑料。
          sheen: solid
              ? const Color(0xFFFFFFFF).withValues(alpha: .12)
              : chrome.sheen,
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
    button = CustomPaint(
      foregroundPainter: _GlassEdgePainter(
        style: edge,
        shape: shape,
        rimFraction: .5,
        textDirection: textDirection,
      ),
      child: button,
    );
    if (chrome.shadows.isNotEmpty) {
      button = CustomPaint(
        painter: _GlassShadowPainter(
          shape: shape,
          shadows: chrome.shadows,
          textDirection: Directionality.maybeOf(context),
        ),
        child: button,
      );
    }

    // 48 的可点域包着 36/40 的玻璃:触控契约不随视觉尺寸缩水。
    return Tooltip(
      message: tooltip,
      child: Semantics(
        button: true,
        enabled: active,
        label: tooltip,
        child: MergeSemantics(
          child: Pressable(
            borderRadius: BorderRadius.circular(MaeveRadius.pill),
            enabled: active,
            haptic: haptic,
            onTap: active ? onPressed : null,
            child: SizedBox.square(dimension: 48, child: Center(child: button)),
          ),
        ),
      ),
    );
  }
}

/// 液态玻璃文字钮:抽屉的"新对话 / 删除选中对话"、输入条里的"模型配置"、
/// 执行面板与设置页的动作按钮。
///
/// 默认是 pill 档玻璃(不取样:按钮长在已经取样或实心的面板上,再取样就是
/// 玻璃套玻璃);[prominent] 用强调色实底 + 白色受光边,读作主操作。
/// 形状默认按钮档(14 连续曲率);[compact] 用胶囊(标签、chip)。
/// [selected] 给 chip 用:勾选标记 + 语义选中态——色彩不是状态唯一线索。
/// 按压反馈与圆钮同一套:Pressable 内容缩放 + 触点径向流光 + 一次触感。
class MaeveGlassTextButton extends StatelessWidget {
  const MaeveGlassTextButton({
    super.key,
    required this.label,
    this.icon,
    required this.onPressed,
    this.prominent = false,
    this.compact = false,
    this.enabled = true,
    this.selected = false,
    this.shape,
    this.semanticLabel,
    this.fill,
    this.foreground,
    this.tooltip,
    this.haptic,
  });

  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final bool prominent;
  final bool compact;
  final bool enabled;

  /// chip 的选中态:勾选标记 + Semantics.selected(色彩不是唯一线索)。
  final bool selected;

  final ShapeBorder? shape;
  final String? semanticLabel;
  final Color? fill;

  /// 覆盖文字/图形色(自定义深底时用)。
  final Color? foreground;

  /// 悬浮提示(输入条里的"模型配置"用)。
  final String? tooltip;

  /// 点按时给的触感;null = 不震。
  final MaeveHaptic? haptic;

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;
    final pill = theme.surfaces.pill;
    final resolved = shape ?? (compact ? MaeveShapes.pill : MaeveShapes.button);
    final active = enabled && onPressed != null;
    final solid = prominent || fill != null;
    final edge = solid ? MaeveGlassButton._solidEdge(pill) : pill;
    final buttonFill = fill ?? (prominent ? colors.accent : pill.fill);
    final foreground =
        this.foreground ??
        (prominent ? colors.onAccent : (active ? colors.ink : colors.ink3));
    // 触控高度:主操作对齐 FilledButton(52),普通动作对齐 TextButton(44),
    // chip/行内小钮 40(与 ChoiceChip 同族,不小于旧 chip 的触控)。
    final minHeight = compact
        ? 40.0
        : (prominent ? MaeveSize.button : MaeveSize.touch);
    final rippleRadius = compact || resolved is StadiumBorder
        ? MaeveRadius.pill
        : MaeveRadius.md;

    final textDirection = Directionality.maybeOf(context);
    final leading = selected ? Icons.check_rounded : icon;
    Widget button = CustomPaint(
      painter: _GlassBevelPainter(
        style: edge,
        shape: resolved,
        textDirection: textDirection,
      ),
      child: DecoratedBox(
        decoration: ShapeDecoration(shape: resolved, color: buttonFill),
        child: _Sheen(
          shape: resolved,
          sheen: solid
              ? const Color(0xFFFFFFFF).withValues(alpha: .12)
              : pill.sheen,
          stop: .5,
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: minHeight),
            child: Padding(
              padding: EdgeInsets.symmetric(
                horizontal: compact ? MaeveSpace.s12 : MaeveSpace.s16,
                vertical: compact ? MaeveSpace.s6 : MaeveSpace.s10,
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (leading != null) ...[
                    Icon(leading, size: compact ? 15 : 17, color: foreground),
                    const SizedBox(width: MaeveSpace.s6),
                  ],
                  Text(
                    label,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: MaeveType.bodyStrong.copyWith(
                      fontSize: compact ? 12.5 : 14.5,
                      color: foreground,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    button = CustomPaint(
      foregroundPainter: _GlassEdgePainter(
        style: edge,
        shape: resolved,
        textDirection: textDirection,
      ),
      child: button,
    );

    Widget result = Semantics(
      button: true,
      enabled: active,
      selected: selected,
      label: semanticLabel ?? label,
      child: MergeSemantics(
        child: Pressable(
          borderRadius: BorderRadius.circular(rippleRadius),
          enabled: active,
          haptic: haptic,
          onTap: active ? onPressed : null,
          child: button,
        ),
      ),
    );
    final tip = tooltip;
    if (tip != null && tip.isNotEmpty) {
      result = Tooltip(message: tip, child: result);
    }
    return result;
  }
}

/// 液态玻璃对话框:确认类"小菜单"(删除确认、开启自主执行、解绑微信…)。
///
/// 和 [AlertDialog] 同一套签名(title / content / actions),换掉它之后:
/// 表面是 sheet 档玻璃(弹层停稳后才开始取样,所以升起途中不闪),
/// 形状是 [MaeveShapes.dialog]。actions 里的按钮沿用主题的
/// TextButton / FilledButton —— 按钮归按钮,玻璃归玻璃。
///
/// 外层 [Dialog] 的参数全部显式给出:Flutter 3.47 的 showDialog 会把
/// DialogTheme 改成"铺满窗口、左上对齐、零 inset"(_FullWindowDialogWrapper),
/// 不显式压过去,玻璃就会铺满整屏、内容贴顶,遮罩点按关闭也失效。
/// 背景必须透明:玻璃自己画填充,Dialog 的 Material 卡若垫一层不透明色,
/// 采样采到的就是实色底,透不出背后的画面。
class MaeveGlassDialog extends StatelessWidget {
  const MaeveGlassDialog({
    super.key,
    this.title,
    this.content,
    this.actions = const [],
  });

  final Widget? title;
  final Widget? content;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final animation = ModalRoute.of(context)?.animation;
    // 弹层一起床就容易闪:scheduled 采样要等它停稳(与 AppSheet 同一约定)。
    final settled = animation == null
        ? 1.0
        : Curves.easeOut.transform(((animation.value - .55) / .45).clamp(0, 1));
    return Semantics(
      namesRoute: true,
      label: MaterialLocalizations.of(context).dialogLabel,
      // 关掉对话框和退页同一语义:点遮罩、系统返回都不经过按钮,
      // 反馈挂在路由上(与 maevePageRoute / showMaeveSheet 一致)。
      child: PopScope(
        canPop: true,
        onPopInvokedWithResult: (didPop, _) {
          if (didPop) unawaited(MaeveHaptics.play(MaeveHaptic.tick));
        },
        child: Dialog(
          backgroundColor: Colors.transparent,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          insetPadding: const EdgeInsets.symmetric(
            horizontal: 40,
            vertical: 24,
          ),
          alignment: Alignment.center,
          constraints: const BoxConstraints(minWidth: 280, maxWidth: 560),
          shape: const RoundedRectangleBorder(),
          clipBehavior: Clip.none,
          child: Material(
            type: MaterialType.transparency,
            child: MaeveGlassSurface(
              tier: MaeveGlassTier.sheet,
              shape: MaeveShapes.dialog,
              settled: settled,
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.s24,
                MaeveSpace.s20,
                MaeveSpace.s24,
                MaeveSpace.s12,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (title != null)
                    DefaultTextStyle(
                      style: text.titleLarge ?? const TextStyle(),
                      child: title!,
                    ),
                  if (content != null) ...[
                    const SizedBox(height: MaeveSpace.s12),
                    // Flexible:长内容先缩,不撑破屏幕(与 AlertDialog 同一约定)。
                    Flexible(
                      child: DefaultTextStyle(
                        style: text.bodyMedium ?? const TextStyle(),
                        // 内容里可能有 ListTile / 选择框:给它自己的 Material,
                        // 免得墨水画在玻璃底上被盖掉(Flutter 的 DecoratedBox 断言)。
                        child: Material(
                          type: MaterialType.transparency,
                          child: content!,
                        ),
                      ),
                    ),
                  ],
                  if (actions.isNotEmpty) ...[
                    const SizedBox(height: MaeveSpace.s16),
                    OverflowBar(
                      alignment: MainAxisAlignment.end,
                      spacing: MaeveSpace.s8,
                      overflowAlignment: OverflowBarAlignment.end,
                      children: actions,
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

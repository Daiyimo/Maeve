import 'package:flutter/material.dart';

import '../design/maeve_motion.dart';
import '../design/maeve_theme.dart';
import 'eclipse_geometry.dart';

enum MarkState { idle, running }

/// Equilibrium at rest; localized divergence only while a real task is running.
class MaeveMark extends StatefulWidget {
  const MaeveMark({
    super.key,
    required this.size,
    this.state = MarkState.idle,
    this.color,
  });
  final double size;
  final MarkState state;
  final Color? color;

  @override
  State<MaeveMark> createState() => _MaeveMarkState();
}

class _MaeveMarkState extends State<MaeveMark>
    with SingleTickerProviderStateMixin {
  late final _phase = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 5),
  );
  bool _reduced = true, _visible = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _reduced = AppMotion.of(context).reduced;
    _visible = TickerMode.valuesOf(context).enabled;
    _sync();
  }

  @override
  void didUpdateWidget(MaeveMark oldWidget) {
    super.didUpdateWidget(oldWidget);
    _sync();
  }

  void _sync() {
    final active = widget.state == MarkState.running && !_reduced && _visible;
    if (active) {
      if (!_phase.isAnimating) _phase.repeat();
    } else {
      _phase.stop();
      _phase.value = 0;
    }
  }

  @override
  void dispose() {
    _phase.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Semantics(
    label: 'Maeve',
    image: true,
    child: RepaintBoundary(
      child: CustomPaint(
        size: Size.square(widget.size),
        painter: MaeveMarkPainter(
          widget.color ?? MaeveTheme.of(context).colors.accent,
          running: widget.state == MarkState.running,
          phase: _phase,
        ),
      ),
    ),
  );
}

class MaeveMarkPainter extends CustomPainter {
  MaeveMarkPainter(this.color, {this.running = false, this.phase})
    : super(repaint: phase);
  final Color color;
  final bool running;
  final Animation<double>? phase;

  @override
  void paint(Canvas canvas, Size size) {
    final side = size.shortestSide;
    if (side <= 0) return;
    final minimum = .55 / side;
    Offset offset(EclipsePoint p) => Offset(p.x * side, p.y * side);
    final path = Path()..fillType = PathFillType.evenOdd;
    final points = EclipseGeometry.corona(minimum: minimum);
    path.moveTo(points.first.x * side, points.first.y * side);
    for (final p in points.skip(1)) {
      path.lineTo(p.x * side, p.y * side);
    }
    path.close();
    canvas.drawPath(path, Paint()..color = color);

    // Small marks retain the silhouette; sub-pixel particles would only alias.
    if (side < 28) {
      if (running) {
        for (final ray in EclipseGeometry.rays(
          running: true,
          phase: phase?.value ?? 0,
          count: 64,
          minimum: minimum,
        )) {
          if ((offset(ray.end) - offset(ray.start)).distance < .6) continue;
          canvas.drawLine(
            offset(ray.start),
            offset(ray.end),
            Paint()
              ..color = color.withValues(alpha: color.a * ray.alpha)
              ..strokeWidth = .65
              ..strokeCap = StrokeCap.round,
          );
        }
      }
      return;
    }
    final count = (side * 3).round().clamp(100, 320);
    for (final ray in EclipseGeometry.rays(
      running: running,
      phase: phase?.value ?? 0,
      count: count,
      minimum: minimum,
    )) {
      canvas.drawLine(
        offset(ray.start),
        offset(ray.end),
        Paint()
          ..color = color.withValues(alpha: color.a * ray.alpha)
          ..strokeWidth = (.0018 * side).clamp(.35, .75),
      );
    }
    if (side >= 56) {
      for (final grain in EclipseGeometry.grains()) {
        canvas.drawCircle(
          offset(grain.center),
          grain.radius * side,
          Paint()..color = color.withValues(alpha: color.a * grain.alpha),
        );
      }
    }
  }

  @override
  bool shouldRepaint(MaeveMarkPainter old) =>
      old.color != color || old.running != running || old.phase != phase;
}

/// The eclipse is the hero itself: no solid pearl behind its empty center.
class MaeveEmblem extends StatelessWidget {
  const MaeveEmblem({super.key, this.size = 96, this.state = MarkState.idle});
  final double size;
  final MarkState state;

  @override
  Widget build(BuildContext context) {
    final motion = AppMotion.of(context);
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: motion.reduced ? 1 : 0, end: 1),
      duration: motion.emphasized,
      curve: MaeveMotion.glide,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, (1 - value) * 6),
          child: child,
        ),
      ),
      child: MaeveMark(size: size, state: state),
    );
  }
}

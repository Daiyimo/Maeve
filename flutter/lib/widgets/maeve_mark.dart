import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';

class MaeveMark extends StatelessWidget {
  const MaeveMark({super.key, required this.size, this.state = MarkState.idle});
  final double size;
  final MarkState state;

  @override
  Widget build(BuildContext context) => CustomPaint(
    size: Size.square(size),
    painter: MaeveMarkPainter(
      MaeveTheme.of(context).colors.ink,
      Theme.of(context).scaffoldBackgroundColor,
      armed: state == MarkState.armed,
    ),
  );
}

enum MarkState { idle, armed }

/// Public so tests can record the draw calls for each mark state.
@visibleForTesting
class MaeveMarkPainter extends CustomPainter {
  const MaeveMarkPainter(this.color, this.background, {this.armed = false});
  final Color color;
  final Color background;
  final bool armed;

  static const arcStart = -.6;
  static const arcSweep = 4.4;
  static const fillRatio = .67;

  /// Direction of the middle of the ring opening when armed.
  static double get gapDirection =>
      arcStart + arcSweep + (2 * math.pi - arcSweep) / 2;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide * .3;
    final stroke = size.shortestSide * .045;
    final outline = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round;
    if (armed) {
      // The eclipse ring opens into an arc once Maeve is ready to work. The
      // tick sits in the middle of that opening, outside the inner disk.
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        arcStart,
        arcSweep,
        false,
        outline,
      );
      final direction = Offset(math.cos(gapDirection), math.sin(gapDirection));
      canvas.drawLine(
        center + direction * (radius * .9),
        center + direction * (radius * fillRatio),
        Paint()
          ..color = color
          ..style = PaintingStyle.stroke
          ..strokeCap = StrokeCap.round
          ..strokeWidth = stroke,
      );
    } else {
      canvas.drawCircle(center, radius, outline);
    }
    canvas.drawCircle(center, radius * fillRatio, Paint()..color = color);
    canvas.drawCircle(center, radius * .4, Paint()..color = background);
  }

  @override
  bool shouldRepaint(covariant MaeveMarkPainter oldDelegate) =>
      oldDelegate.color != color ||
      oldDelegate.background != background ||
      oldDelegate.armed != armed;
}

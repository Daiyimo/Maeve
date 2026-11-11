import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';
import '../design/maeve_motion.dart';

/// 醒瞳: the control pearl sits inside an opening gaze. The upper contour leaves
/// its loop, recalling Maeve seeing the system and choosing her own direction.
class MaeveMark extends StatelessWidget {
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
  Widget build(BuildContext context) => Semantics(
    label: 'Maeve',
    image: true,
    child: CustomPaint(
      size: Size.square(size),
      painter: MaeveMarkPainter(
        color ?? MaeveTheme.of(context).colors.accent,
        MaeveTheme.of(context).colors.bg,
        armed: state == MarkState.armed,
      ),
    ),
  );
}

enum MarkState { idle, armed }

class MaeveMarkPainter extends CustomPainter {
  const MaeveMarkPainter(this.color, this.background, {this.armed = false});
  final Color color, background;
  final bool armed;
  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width, size.height);
    final stroke = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = .078
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final upper = Path()
      ..moveTo(.10, .54)
      ..cubicTo(.27, .28, .53, .22, .76, .34)
      ..lineTo(.91, .22);
    final lower = Path()
      ..moveTo(.11, .55)
      ..cubicTo(.31, .83, .65, .84, .89, .53);
    canvas.drawPath(upper, stroke);
    canvas.drawPath(lower, stroke);
    canvas.drawCircle(
      const Offset(.51, .52),
      armed ? .125 : .115,
      Paint()..color = color,
    );
    canvas.drawCircle(
      const Offset(.475, .483),
      .032,
      Paint()..color = background,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(MaeveMarkPainter old) =>
      old.color != color || old.background != background || old.armed != armed;
}

class MaeveEmblem extends StatelessWidget {
  const MaeveEmblem({super.key, this.size = 96});
  final double size;
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors, motion = AppMotion.of(context);
    final pearl = Color.lerp(c.accent, Colors.white, .48)!;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: motion.reduced ? 1 : 0, end: 1),
      duration: motion.emphasized,
      curve: MaeveMotion.glide,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, (1 - value) * 10),
          child: Transform.scale(scale: .94 + .06 * value, child: child),
        ),
      ),
      child: Container(
        width: size,
        height: size,
        padding: EdgeInsets.all(size * .17),
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: RadialGradient(
            center: const Alignment(-.6, -.7),
            radius: 1.5,
            colors: [
              Colors.white,
              Color.lerp(pearl, Colors.white, .38)!,
              pearl,
              Color.lerp(c.accent, pearl, .25)!,
            ],
            stops: const [0, .22, .7, 1],
          ),
          border: Border.all(
            color: Colors.white.withValues(alpha: .65),
            width: .7,
          ),
          boxShadow: [
            BoxShadow(
              color: c.accent.withValues(alpha: .14),
              blurRadius: 38,
              spreadRadius: 1,
            ),
            BoxShadow(
              color: Colors.black.withValues(alpha: c.isDark ? .12 : .04),
              blurRadius: 18,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: MaeveMark(
          size: size * .66,
          state: MarkState.armed,
          color: c.isDark ? Color.lerp(c.onAccent, c.accent, .3) : c.accent,
        ),
      ),
    );
  }
}

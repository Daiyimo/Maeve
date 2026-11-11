import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';
import '../design/maeve_motion.dart';
import 'glass_surface.dart';

/// A quiet, illuminated control pearl. The two folds leave the centre open:
/// Maeve acts behind the screen while the user keeps the choice.
class MaeveEmblem extends StatelessWidget {
  const MaeveEmblem({super.key, this.size = 100});
  final double size;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final motion = AppMotion.of(context);
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: motion.reduced ? 1 : 0, end: 1),
      duration: motion.emphasized,
      curve: MaeveMotion.glide,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, (1 - value) * 12),
          child: Transform.scale(scale: .92 + .08 * value, child: child),
        ),
      ),
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [c.accent.withValues(alpha: .34), c.accentSoft, c.bg],
          ),
          boxShadow: [
            BoxShadow(
              color: c.accent.withValues(alpha: .16),
              blurRadius: 44,
              spreadRadius: 6,
            ),
          ],
        ),
        child: MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          shape: const CircleBorder(),
          padding: EdgeInsets.all(size * .24),
          child: MaeveMark(size: size * .52, state: MarkState.armed),
        ),
      ),
    );
  }
}

/// Two folded curtains form an M; the clear centre belongs to the user's screen.
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
  final Color color;
  final Color background;
  final bool armed;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.scale(size.width, size.height);
    final curtain = Path()
      ..moveTo(.10, .81)
      ..lineTo(.10, .21)
      ..cubicTo(.10, .095, .22, .065, .29, .16)
      ..lineTo(.50, .45)
      ..lineTo(.40, .61)
      ..lineTo(.285, .445)
      ..lineTo(.285, .81)
      ..quadraticBezierTo(.285, .90, .19, .90)
      ..quadraticBezierTo(.10, .90, .10, .81)
      ..close();
    canvas.drawPath(curtain, Paint()..color = color);
    canvas.save();
    canvas.translate(1, 0);
    canvas.scale(-1, 1);
    canvas.drawPath(curtain, Paint()..color = color.withValues(alpha: .76));
    canvas.restore();
    final light = Paint()
      ..color = color
      ..strokeWidth = .055
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(
      Offset(.50, armed ? .67 : .76),
      const Offset(.50, .86),
      light,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(MaeveMarkPainter old) =>
      old.color != color || old.armed != armed || old.background != background;
}

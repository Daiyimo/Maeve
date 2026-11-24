import 'package:flutter/material.dart';

import '../models/execution.dart';

/// Input pixels map through BoxFit.contain, including the letterboxed preview.
class PointerEvidencePainter extends CustomPainter {
  const PointerEvidencePainter(this.pointer);
  final ExecutionPointer pointer;

  Rect imageRect(Size canvas) {
    final fitted = applyBoxFit(
      BoxFit.contain,
      Size(pointer.width.toDouble(), pointer.height.toDouble()),
      canvas,
    );
    return Alignment.center.inscribe(fitted.destination, Offset.zero & canvas);
  }

  Offset position(Size canvas, double x, double y) {
    final image = imageRect(canvas);
    return Offset(
      image.left + x / pointer.width * image.width,
      image.top + y / pointer.height * image.height,
    );
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (!pointer.valid) return;
    const purple = Color(0xff7654c5);
    canvas.save();
    canvas.clipRect(imageRect(size));
    if (pointer.method == 'touch') {
      final p = position(size, pointer.x!.toDouble(), pointer.y!.toDouble());
      canvas.drawCircle(
        p,
        11,
        Paint()
          ..color = Colors.white.withValues(alpha: .9)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 4,
      );
      canvas.drawCircle(
        p,
        11,
        Paint()
          ..color = purple
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2,
      );
      canvas.drawCircle(p, 3, Paint()..color = purple);
      canvas.drawLine(
        p + const Offset(-16, 0),
        p + const Offset(16, 0),
        Paint()
          ..color = purple
          ..strokeWidth = 1,
      );
      canvas.drawLine(
        p + const Offset(0, -16),
        p + const Offset(0, 16),
        Paint()
          ..color = purple
          ..strokeWidth = 1,
      );
    } else {
      final b = pointer.bounds;
      final rect = Rect.fromPoints(
        position(size, b[0].toDouble(), b[1].toDouble()),
        position(size, b[2].toDouble(), b[3].toDouble()),
      );
      canvas.drawRect(rect, Paint()..color = purple.withValues(alpha: .12));
      canvas.drawRect(
        rect,
        Paint()
          ..color = purple
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2,
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(PointerEvidencePainter oldDelegate) =>
      oldDelegate.pointer != pointer;
}

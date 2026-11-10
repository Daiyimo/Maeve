import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../design/maeve_motion.dart';
import '../../design/maeve_tokens.dart';
import 'haptics.dart';

/// 长按确认:按住 1500ms 走一圈进度才算同意。
///
/// 危险操作(解除限制、放行付款)必须走这里 —— 不能一键通过(第 6 节 C12)。
///
/// - 用**原始指针**监听,不走手势识别器:手势竞技场里长按会被列表/滚动抢走,
///   按住时列表一滑确认就断了,那是最糟的手感。
/// - 起手一次 Tick、完成一次 Heavy;中途松手没有反馈。
/// - 手指移动超过 24px 视为放弃(防止按住后拖走)。
///
/// ```dart
/// HoldToConfirm(onConfirmed: _grant, child: Text('按住解除限制'))
/// ```
class HoldToConfirm extends StatefulWidget {
  const HoldToConfirm({
    super.key,
    required this.child,
    required this.onConfirmed,
    this.duration = const Duration(milliseconds: 1500),
    this.borderRadius = const BorderRadius.all(Radius.circular(MaeveRadius.md)),
    this.progressColor,
    this.enabled = true,
    this.semanticLabel,
  });

  final Widget child;
  final VoidCallback onConfirmed;
  final Duration duration;
  final BorderRadius borderRadius;
  final Color? progressColor;
  final bool enabled;
  final String? semanticLabel;

  @override
  State<HoldToConfirm> createState() => _HoldToConfirmState();
}

class _HoldToConfirmState extends State<HoldToConfirm>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: widget.duration,
  )..addStatusListener(_onStatus);
  Offset? _origin;

  void _onStatus(AnimationStatus status) {
    if (status != AnimationStatus.completed) return;
    unawaited(MaeveHaptics.play(MaeveHaptic.heavy));
    widget.onConfirmed();
    _controller.value = 0;
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _down(PointerDownEvent event) {
    if (!widget.enabled) return;
    _origin = event.position;
    unawaited(MaeveHaptics.play(MaeveHaptic.tick));
    _controller.forward(from: 0);
  }

  void _move(PointerMoveEvent event) {
    final origin = _origin;
    if (origin == null) return;
    if ((event.position - origin).distance > 24) _cancel();
  }

  void _cancel() {
    _origin = null;
    if (_controller.isAnimating) {
      _controller.animateBack(0, duration: AppMotion.of(context).quick);
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final progress = widget.progressColor ?? scheme.primary;
    // 减少动画时不画进度环,但 1500ms 的按住时长不变 —— 摩擦是安全的一部分。
    final showRing = !AppMotion.of(context).reduced;
    return Semantics(
      button: true,
      label: widget.semanticLabel ?? '按住确认',
      child: Listener(
        // 必须是 opaque:子件可能是纯 SizedBox / Column 这类不绘制的东西,
        // deferToChild 会让按住直接落空。
        behavior: HitTestBehavior.opaque,
        onPointerDown: _down,
        onPointerMove: _move,
        onPointerUp: (_) => _cancel(),
        onPointerCancel: (_) => _cancel(),
        child: AnimatedBuilder(
          animation: _controller,
          builder: (context, child) => Stack(
            fit: StackFit.passthrough,
            children: [
              child!,
              Positioned.fill(
                child: IgnorePointer(
                  child: CustomPaint(
                    painter: showRing
                        ? _HoldRingPainter(
                            progress: _controller.value,
                            color: progress,
                            radius: widget.borderRadius,
                          )
                        : null,
                  ),
                ),
              ),
            ],
          ),
          child: widget.child,
        ),
      ),
    );
  }
}

class _HoldRingPainter extends CustomPainter {
  const _HoldRingPainter({
    required this.progress,
    required this.color,
    required this.radius,
  });

  final double progress;
  final Color color;
  final BorderRadius radius;

  @override
  void paint(Canvas canvas, Size size) {
    if (progress <= 0 || size.isEmpty) return;
    final rect = Offset.zero & size;
    // 内外两条同形状的轮廓差 = 一圈 2px 的进度环,贴着形状走。
    final outer = radius.toRRect(rect.deflate(1));
    final inner = radius.toRRect(rect.deflate(3));
    final ring = Path()
      ..fillType = PathFillType.evenOdd
      ..addRRect(outer)
      ..addRRect(inner);
    canvas.save();
    canvas.clipPath(ring);
    final sweep = rect.width * 1.6 * progress;
    canvas.drawRect(
      Rect.fromLTWH(rect.left - rect.width, rect.top, sweep, rect.height),
      Paint()..color = color.withValues(alpha: .55),
    );
    canvas.restore();
    // 端点加一颗小圆,读得出"还在走"。
    canvas.drawCircle(
      Offset(rect.left - rect.width + sweep, rect.center.dy),
      2.5,
      Paint()..color = color,
    );
  }

  @override
  bool shouldRepaint(_HoldRingPainter old) =>
      old.progress != progress || old.color != color || old.radius != radius;
}

/// 环形进度(读屏测试、导出进度等):确定进度用 [value],不确定用 [indeterminate]。
class MaeveProgressRing extends StatelessWidget {
  const MaeveProgressRing({
    super.key,
    this.value,
    this.size = 20,
    this.strokeWidth = 2,
    this.color,
    this.indeterminate = false,
  });

  final double? value;
  final double size;
  final double strokeWidth;
  final Color? color;
  final bool indeterminate;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final tone = color ?? scheme.primary;
    final motion = AppMotion.of(context);
    if (indeterminate) {
      // 减少动画时停在静止帧(一根 3/4 弧),不转圈。
      if (motion.reduced) {
        return SizedBox.square(
          dimension: size,
          child: CustomPaint(
            painter: _StaticArcPainter(color: tone, strokeWidth: strokeWidth),
          ),
        );
      }
      return SizedBox.square(
        dimension: size,
        child: CircularProgressIndicator(strokeWidth: strokeWidth, color: tone),
      );
    }
    return SizedBox.square(
      dimension: size,
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: (value ?? 0).clamp(0, 1)),
        duration: motion.standard,
        curve: MaeveMotion.std,
        builder: (context, progress, _) => CustomPaint(
          painter: _RingPainter(
            progress: progress,
            color: tone,
            strokeWidth: strokeWidth,
            track: scheme.onSurface.withValues(alpha: .12),
          ),
        ),
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  const _RingPainter({
    required this.progress,
    required this.color,
    required this.strokeWidth,
    required this.track,
  });

  final double progress;
  final Color color;
  final double strokeWidth;
  final Color track;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(strokeWidth / 2);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(rect, 0, math.pi * 2, false, paint..color = track);
    canvas.drawArc(
      rect,
      -math.pi / 2,
      math.pi * 2 * progress,
      false,
      paint..color = color,
    );
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.progress != progress || old.color != color;
}

class _StaticArcPainter extends CustomPainter {
  const _StaticArcPainter({required this.color, required this.strokeWidth});

  final Color color;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = (Offset.zero & size).deflate(strokeWidth / 2);
    canvas.drawArc(
      rect,
      -math.pi / 2,
      math.pi * 1.5,
      false,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = strokeWidth
        ..strokeCap = StrokeCap.round
        ..color = color,
    );
  }

  @override
  bool shouldRepaint(_StaticArcPainter old) => old.color != color;
}

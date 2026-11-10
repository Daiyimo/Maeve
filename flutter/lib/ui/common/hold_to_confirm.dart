import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'haptics.dart';

import '../../design/maeve_motifs.dart';
import '../../design/maeve_motion.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';

/// 长按确认:按住 1500ms 走一圈进度才算同意。
///
/// 危险操作(解除限制、清除数据、放行付款)必须走这里 —— 不能一键通过。
///
/// - 用**原始指针**监听,不走手势识别器:手势竞技场里长按会被列表/滚动抢走。
/// - 起手一次 Tick、完成一次 Heavy;中途松手没有反馈。
/// - 手指移动超过 24px 视为放弃(防止按住后拖走)。
class HoldToConfirm extends StatefulWidget {
  const HoldToConfirm({
    super.key,
    required this.child,
    required this.onConfirmed,
    this.duration = const Duration(milliseconds: MaeveMotion.holdToConfirmMs),
    this.shape,
    this.progressColor,
    this.enabled = true,
    this.semanticLabel,
  });

  final Widget child;
  final VoidCallback onConfirmed;
  final Duration duration;
  final ShapeBorder? shape;
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
    final theme = MaeveTheme.of(context);
    final progress = widget.progressColor ?? theme.colors.accent;
    final shape = widget.shape ?? MaeveShapes.button;
    // 减少动效时不画进度环,但 1500ms 的按住时长不变 —— 摩擦是安全的一部分。
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
                            shape: shape,
                            textDirection: Directionality.maybeOf(context),
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
    required this.shape,
    required this.textDirection,
  });

  final double progress;
  final Color color;
  final ShapeBorder shape;
  final TextDirection? textDirection;

  @override
  void paint(Canvas canvas, Size size) {
    if (progress <= 0 || size.isEmpty) return;
    final rect = Offset.zero & size;
    // 内外两条同形状的轮廓差 = 一圈 2px 的进度环,贴着形状走。
    final outer = shape.getOuterPath(
      rect.deflate(1),
      textDirection: textDirection,
    );
    final inner = shape.getOuterPath(
      rect.deflate(3),
      textDirection: textDirection,
    );
    final ring = Path()
      ..fillType = PathFillType.evenOdd
      ..addPath(outer, Offset.zero)
      ..addPath(inner, Offset.zero);
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
      old.progress != progress || old.color != color || old.shape != shape;
}

/// 确定 / 不确定进度。**这不是转圈**:它走的是迷宫环(螺线),有方向、有终点。
class MaeveProgress extends StatelessWidget {
  const MaeveProgress({
    super.key,
    this.value,
    this.size = 18,
    this.color,
    this.indeterminate = false,
  });

  final double? value;
  final double size;
  final Color? color;
  final bool indeterminate;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return MazeRing(
      progress: value,
      size: size,
      strokeWidth: math.max(1.2, size / 12),
      indeterminate: indeterminate,
      track: colors.hair,
      head: color ?? colors.accent,
    );
  }
}

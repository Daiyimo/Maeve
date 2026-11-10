import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../widgets/maeve_mark.dart';

import 'maeve_motion.dart';
import 'maeve_theme.dart';
import 'maeve_type.dart';

/// Maeve 的四个图形母题。它们不是装饰,每一个都对应界面里一件真事:
///
/// | 母题 | 对应的事 |
/// |---|---|
/// | [MazeRing] | 一个任务从入口走到中心的过程。**替代全站所有转圈** |
/// | [PianoRoll] | 一个循环里已经弹过和正在弹的那一步。**替代"正在思考…"** |
/// | [ScriptRail] | 这是第几轮对话、她说到哪儿了。剧本的边注 |
/// | [CalibrationCorners] | 这块是仪表,不是正文。四角刻度 |
///
/// 四个都用矢量现画,没有位图、没有第三方素材 —— 和 [MaeveMark] 同一个来源。

/// 迷宫环:一条从外向内收的阿基米德螺线。光点沿螺线往里走,走到中心 = 这件事办完了。
///
/// 为什么不用转圈:转圈只表达"在忙",不表达**进展** —— 转十圈和转一圈长得一样,
/// 用户没法判断她是快到了还是刚出发。螺线天生有方向、有距离、有终点。
class MazeRing extends StatefulWidget {
  const MazeRing({
    super.key,
    this.progress,
    this.size = 16,
    this.strokeWidth = 1.4,
    this.indeterminate = false,
    this.track,
    this.head,
  });

  /// 0..1;null 表示不确定进度。
  final double? progress;
  final double size;
  final double strokeWidth;

  /// 没有明确进度时,光点在外圈缓慢巡游。
  final bool indeterminate;

  final Color? track;
  final Color? head;

  @override
  State<MazeRing> createState() => _MazeRingState();
}

class _MazeRingState extends State<MazeRing>
    with SingleTickerProviderStateMixin {
  late final AnimationController _spin = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1800),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(MazeRing old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    final animate = widget.indeterminate && !AppMotion.of(context).reduced;
    if (animate && !_spin.isAnimating) {
      _spin.repeat();
    } else if (!animate && _spin.isAnimating) {
      _spin.stop();
      _spin.value = 0;
    }
  }

  @override
  void dispose() {
    _spin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    return AnimatedBuilder(
      animation: _spin,
      builder: (context, _) => CustomPaint(
        size: Size.square(widget.size),
        painter: MazeRingPainter(
          progress:
              widget.progress ?? (widget.indeterminate ? _spin.value : null),
          track: widget.track ?? theme.colors.hair,
          head: widget.head ?? theme.colors.ink2,
          strokeWidth: widget.strokeWidth,
        ),
      ),
    );
  }
}

/// 画一次迷宫环。公开以便单测记录绘制调用。
@visibleForTesting
class MazeRingPainter extends CustomPainter {
  const MazeRingPainter({
    required this.progress,
    required this.track,
    required this.head,
    required this.strokeWidth,
  });

  final double? progress;
  final Color track;
  final Color head;
  final double strokeWidth;

  /// 从外到内的阿基米德螺线。圈数随半径走:78 的标志盘 2.25 圈,
  /// 13 的进度环若也 2.25 圈会糊成一个黑点,所以小尺寸退化成 1.2 圈。
  static Path spiral(Size size, double stroke) {
    final cx = size.width / 2;
    final cy = size.height / 2;
    final outer = size.shortestSide / 2 - stroke;
    final turns = (outer / 17).clamp(1.2, 2.25);
    final inner = outer * .16;
    const steps = 120;
    final path = Path();
    for (var i = 0; i <= steps; i++) {
      final t = i / steps;
      final angle = (-90 + t * 360 * turns) * math.pi / 180;
      final radius = outer - (outer - inner) * t;
      final x = cx + radius * math.cos(angle);
      final y = cy + radius * math.sin(angle);
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    return path;
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final path = spiral(size, strokeWidth);
    final base = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round
      ..color = track;
    canvas.drawPath(path, base);
    final p = progress;
    if (p == null) return;
    final metric = path.computeMetrics().first;
    final length = metric.length * p.clamp(0.0, 1.0);
    if (length <= .5) return;
    canvas.drawPath(
      metric.extractPath(0, length),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = strokeWidth
        ..strokeCap = StrokeCap.round
        ..color = head,
    );
    final tangent = metric.getTangentForOffset(length);
    if (tangent != null) {
      canvas.drawCircle(
        tangent.position,
        strokeWidth * 1.55,
        Paint()..color = head,
      );
    }
  }

  @override
  bool shouldRepaint(MazeRingPainter old) =>
      old.progress != progress ||
      old.track != track ||
      old.head != head ||
      old.strokeWidth != strokeWidth;
}

/// 钢琴卷里一根音符条的状态。
enum NoteState { done, active, pending, failed }

/// 钢琴卷:把"已经跑到第几步"画成一条卷带。
///
/// 每一根音符条的**高度**是它自己的编号推出来的伪随机值 —— 固定不变,
/// 所以同一轮对话里来回滚动时卷带不会跳。看起来像谱子,读起来是进度。
class PianoRoll extends StatelessWidget {
  const PianoRoll({
    super.key,
    required this.states,
    this.height = 18,
    this.note = 3,
    this.gap = 2.5,
    this.done,
    this.active,
    this.pending,
    this.failed,
  });

  final List<NoteState> states;
  final double height;
  final double note;
  final double gap;
  final Color? done;
  final Color? active;
  final Color? pending;
  final Color? failed;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final reduced = AppMotion.of(context).reduced;
    return SizedBox(
      height: height,
      width: double.infinity,
      child: TweenAnimationBuilder<double>(
        tween: Tween(begin: 0, end: 1),
        duration: reduced ? Duration.zero : const Duration(milliseconds: 760),
        builder: (context, pulse, _) => CustomPaint(
          painter: PianoRollPainter(
            states: states,
            note: note,
            gap: gap,
            done: done ?? colors.ink3,
            active: active ?? colors.accent,
            pending: pending ?? colors.hair,
            failed: failed ?? colors.bad,
            pulse: states.contains(NoteState.active) ? .55 + .45 * pulse : 1,
          ),
        ),
      ),
    );
  }
}

@visibleForTesting
class PianoRollPainter extends CustomPainter {
  const PianoRollPainter({
    required this.states,
    required this.note,
    required this.gap,
    required this.done,
    required this.active,
    required this.pending,
    required this.failed,
    required this.pulse,
  });

  final List<NoteState> states;
  final double note;
  final double gap;
  final Color done;
  final Color active;
  final Color pending;
  final Color failed;
  final double pulse;

  /// 第 [i] 根音符条的高度系数:确定性伪随机,不随重建变化。
  static double fraction(int i) {
    final x = math.sin(i * 12.9898) * 43758.5453;
    return (x - x.floorToDouble()).abs().clamp(.12, 1.0);
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (states.isEmpty || size.isEmpty) return;
    final cell = size.width / states.length;
    final bar = (cell - gap).clamp(1.2, note);
    for (var i = 0; i < states.length; i++) {
      final barHeight = (size.height * (.26 + .74 * fraction(i))).clamp(
        0.0,
        size.height,
      );
      final color = switch (states[i]) {
        NoteState.done => done,
        NoteState.active => active.withValues(alpha: pulse),
        NoteState.pending => pending,
        NoteState.failed => failed,
      };
      canvas.drawRect(
        Rect.fromLTWH(i * cell, size.height - barHeight, bar, barHeight),
        Paint()..color = color,
      );
    }
  }

  @override
  bool shouldRepaint(PianoRollPainter old) =>
      old.states != states || old.pulse != pulse || old.active != active;
}

/// 校准角:四角的细刻度。用在"这块是机器读数"的地方(运行、用量、自检)。
class CalibrationCorners extends StatelessWidget {
  const CalibrationCorners({
    super.key,
    required this.child,
    this.arm = 5,
    this.inset = 6,
    this.color,
    this.strokeWidth = 1,
  });

  final Widget child;
  final double arm;
  final double inset;
  final Color? color;
  final double strokeWidth;

  @override
  Widget build(BuildContext context) => CustomPaint(
    foregroundPainter: _CalibrationPainter(
      color: color ?? MaeveTheme.of(context).colors.hair,
      arm: arm,
      inset: inset,
      strokeWidth: strokeWidth,
    ),
    child: child,
  );
}

class _CalibrationPainter extends CustomPainter {
  const _CalibrationPainter({
    required this.color,
    required this.arm,
    required this.inset,
    required this.strokeWidth,
  });

  final Color color;
  final double arm;
  final double inset;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.square;
    final a = arm;
    final i = inset;
    final w = size.width;
    final h = size.height;
    void line(Offset from, Offset to) => canvas.drawLine(from, to, paint);
    // 左上
    line(Offset(i, i + a), Offset(i, i));
    line(Offset(i, i), Offset(i + a, i));
    // 右上
    line(Offset(w - i - a, i), Offset(w - i, i));
    line(Offset(w - i, i), Offset(w - i, i + a));
    // 左下
    line(Offset(i, h - i - a), Offset(i, h - i));
    line(Offset(i, h - i), Offset(i + a, h - i));
    // 右下
    line(Offset(w - i - a, h - i), Offset(w - i, h - i));
    line(Offset(w - i, h - i), Offset(w - i, h - i - a));
  }

  @override
  bool shouldRepaint(_CalibrationPainter old) =>
      old.color != color || old.arm != arm || old.inset != inset;
}

/// 台词轨:第几轮 + 幕号印章 + 向下的竖线。剧本的边注。
///
/// 每一轮对话前面站一个它,纵向扫读时能立刻知道"这是第几幕"。
class ScriptRail extends StatelessWidget {
  const ScriptRail({
    super.key,
    required this.act,
    this.showNumber = true,
    this.tail = true,
    this.color,
  });

  /// 从 1 开始的轮次号。
  final int act;
  final bool showNumber;

  /// 最后一幕不画向下的延长线,让轨道有个自然的收尾。
  final bool tail;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final tone = color ?? colors.ink3;
    return SizedBox(
      width: MaeveSize.actRail,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          if (showNumber) ...[
            Padding(
              padding: const EdgeInsets.only(top: 14, bottom: 5),
              child: Text(
                act.toString().padLeft(2, '0'),
                style: MaeveType.turn.copyWith(color: tone),
              ),
            ),
            // 幕号下面一个小方块:这一幕的印章
            Container(width: 3, height: 3, color: tone.withValues(alpha: .55)),
          ],
          if (tail)
            Expanded(
              child: Container(
                width: .5,
                margin: const EdgeInsets.only(top: 6),
                color: colors.hair,
              ),
            ),
        ],
      ),
    );
  }
}

/// 细线网格:极淡的一层方格。只用在"叶子面板"(自检、用量、运行过程)。
class HairlineLattice extends StatelessWidget {
  const HairlineLattice({
    super.key,
    required this.child,
    this.step = 24,
    this.color,
  });

  final Widget child;
  final double step;
  final Color? color;

  @override
  Widget build(BuildContext context) => CustomPaint(
    foregroundPainter: _LatticePainter(
      color: color ?? MaeveTheme.of(context).colors.grid,
      step: step,
    ),
    child: child,
  );
}

class _LatticePainter extends CustomPainter {
  const _LatticePainter({required this.color, required this.step});

  final Color color;
  final double step;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = 1;
    var x = step;
    while (x < size.width) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
      x += step;
    }
    var y = step;
    while (y < size.height) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
      y += step;
    }
  }

  @override
  bool shouldRepaint(_LatticePainter old) =>
      old.color != color || old.step != step;
}

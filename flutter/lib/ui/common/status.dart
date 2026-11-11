import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'pressable.dart';

import '../../design/maeve_colors.dart';
import '../../design/maeve_motifs.dart';
import '../../design/maeve_motion.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';

import '../../widgets/glass_surface.dart';

/// 尺寸变化平滑过渡(展开/收起、消息进出)。
class SmoothSize extends StatelessWidget {
  const SmoothSize({
    super.key,
    required this.child,
    this.alignment = Alignment.topCenter,
  });

  final Widget child;
  final AlignmentGeometry alignment;

  @override
  Widget build(BuildContext context) => AnimatedSize(
    duration: AppMotion.of(context).standard,
    curve: MaeveMotion.collapseCurve,
    alignment: alignment,
    child: child,
  );
}

/// 由 `visible` 驱动的展开:高度 + 淡入一起走(思考块、引文 tray、附件条)。
class ExpandReveal extends StatelessWidget {
  const ExpandReveal({
    super.key,
    required this.visible,
    required this.child,
    this.duration,
    this.axis = Axis.vertical,
  });

  final bool visible;
  final Widget child;
  final Duration? duration;
  final Axis axis;

  @override
  Widget build(BuildContext context) {
    final motion = AppMotion.of(context);
    final value = visible ? 1.0 : 0.0;
    return ClipRect(
      child: AnimatedAlign(
        alignment: axis == Axis.vertical
            ? Alignment.topCenter
            : Alignment.centerLeft,
        heightFactor: axis == Axis.vertical ? value : null,
        widthFactor: axis == Axis.horizontal ? value : null,
        duration: duration ?? motion.collapse,
        curve: MaeveMotion.collapseCurve,
        child: AnimatedOpacity(
          opacity: value,
          duration: duration ?? motion.quick,
          curve: Curves.easeOut,
          child: child,
        ),
      ),
    );
  }
}

/// 状态的语义色调。颜色只是辅助,永远配文字或形状。
enum MaeveTone { idle, running, ready, warning, danger }

Color toneColor(BuildContext context, MaeveTone tone) {
  final theme = MaeveTheme.of(context);
  return switch (tone) {
    MaeveTone.idle => theme.colors.ink3,
    MaeveTone.running => theme.colors.accent,
    MaeveTone.ready => theme.semantic.ok,
    MaeveTone.warning => theme.semantic.warn,
    MaeveTone.danger => theme.semantic.bad,
  };
}

/// 状态点:**形状 + 颜色双编码**,不靠颜色单独表状态。
///
/// - 空闲:空心环(还没开始)
/// - 进行中:开口环 + 呼吸(和品牌标 armed 态同一个母题)
/// - 就绪:实心核(环里填上了)
/// - 警告/危险:实心点(配合文字)
///
/// 任何一处使用都必须同时给出文字(见 [HeaderStatus] / [StatusChip])。
class StatusDot extends StatefulWidget {
  const StatusDot({super.key, this.tone = MaeveTone.idle, this.size = 7});

  final MaeveTone tone;
  final double size;

  @override
  State<StatusDot> createState() => _StatusDotState();
}

class _StatusDotState extends State<StatusDot>
    with SingleTickerProviderStateMixin {
  late final AnimationController _breath = AnimationController(
    vsync: this,
    duration: MaeveMotion.breath,
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(StatusDot oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.tone != widget.tone) _sync();
  }

  void _sync() {
    final animate =
        widget.tone == MaeveTone.running && !AppMotion.of(context).reduced;
    if (animate && !_breath.isAnimating) {
      _breath.repeat(reverse: true);
    } else if (!animate && _breath.isAnimating) {
      _breath.stop();
      _breath.value = .5;
    }
  }

  @override
  void dispose() {
    _breath.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = toneColor(context, widget.tone);
    return SizedBox.square(
      dimension: widget.size * 2,
      child: AnimatedBuilder(
        animation: _breath,
        builder: (context, _) => CustomPaint(
          painter: StatusDotPainter(
            tone: widget.tone,
            color: color,
            breath: _breath.value,
          ),
        ),
      ),
    );
  }
}

/// 公开以便单测记录绘制调用(形状差异是验收项)。
@visibleForTesting
class StatusDotPainter extends CustomPainter {
  const StatusDotPainter({
    required this.tone,
    required this.color,
    this.breath = .5,
  });

  final MaeveTone tone;
  final Color color;
  final double breath;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide * .32;
    final stroke = math.max(1.1, size.shortestSide * .16);
    final outline = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke
      ..strokeCap = StrokeCap.round
      ..color = color;
    final solid = Paint()..color = color;
    switch (tone) {
      case MaeveTone.idle:
        canvas.drawCircle(center, radius, outline);
      case MaeveTone.running:
        // 开口环 + 呼吸:和品牌标的 armed 态同一个动作。
        canvas.drawArc(
          Rect.fromCircle(center: center, radius: radius),
          -.6,
          4.4,
          false,
          outline,
        );
        canvas.drawCircle(
          center,
          radius * .42,
          Paint()..color = color.withValues(alpha: .35 + .45 * breath),
        );
      case MaeveTone.ready:
        // 环 + 实心核 + 镂空中心(和品牌标 idle 态同形)。
        canvas.drawCircle(center, radius, outline);
        canvas.drawCircle(center, radius * .66, solid);
      case MaeveTone.warning:
      case MaeveTone.danger:
        canvas.drawCircle(center, radius, solid);
        if (tone == MaeveTone.danger) {
          canvas.drawLine(
            Offset(center.dx, center.dy - radius * .45),
            Offset(center.dx, center.dy + radius * .15),
            Paint()
              ..color = Colors.white.withValues(alpha: .9)
              ..strokeWidth = stroke * .7
              ..strokeCap = StrokeCap.round,
          );
        }
    }
  }

  @override
  bool shouldRepaint(StatusDotPainter old) =>
      old.tone != tone || old.color != color || old.breath != breath;
}

/// 顶栏状态行:状态点 + 一句人话。读屏单独给一句完整文案。
class HeaderStatus extends StatelessWidget {
  const HeaderStatus({
    super.key,
    required this.text,
    this.tone = MaeveTone.idle,
    this.maxWidth = 200,
  });

  final String text;
  final MaeveTone tone;
  final double maxWidth;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Semantics(
      container: true,
      label: '状态：$text',
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          StatusDot(tone: tone, size: 6),
          const SizedBox(width: 5),
          ConstrainedBox(
            constraints: BoxConstraints(maxWidth: maxWidth),
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: MaeveType.caption.copyWith(color: colors.ink3),
            ),
          ),
        ],
      ),
    );
  }
}

/// 空状态:图标块 + 一句话 + 一个动作。
class EmptyView extends StatelessWidget {
  const EmptyView({
    super.key,
    required this.icon,
    required this.title,
    this.body,
    this.action,
    this.compact = false,
    this.tone,
  });

  final IconData icon;
  final String title;
  final String? body;
  final Widget? action;
  final bool compact;
  final Color? tone;

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;
    final tint = tone ?? colors.accent;
    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: MaeveSpace.s20,
        vertical: compact ? MaeveSpace.s24 : MaeveSpace.s32,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: compact ? 38 : 46,
            height: compact ? 38 : 46,
            decoration: BoxDecoration(
              color: colors.accentSoft,
              borderRadius: BorderRadius.circular(MaeveRadius.sm),
            ),
            child: Icon(icon, size: compact ? 19 : 22, color: tint),
          ),
          const SizedBox(height: MaeveSpace.s16),
          Text(title, style: MaeveType.bodyStrong.copyWith(color: colors.ink)),
          if (body != null) ...[
            const SizedBox(height: MaeveSpace.s6),
            Text(body!, style: MaeveType.caption.copyWith(color: colors.ink3)),
          ],
          if (action != null) ...[
            const SizedBox(height: MaeveSpace.s16),
            action!,
          ],
        ],
      ),
    );
  }
}

/// 一行说明(不是错误弹窗):图标 + 一句话 + 可选动作。
class BriefNotice extends StatelessWidget {
  const BriefNotice({
    super.key,
    required this.text,
    this.icon = Icons.info_outline,
    this.tone = MaeveTone.idle,
    this.action,
  });

  final String text;
  final IconData icon;
  final MaeveTone tone;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final color = tone == MaeveTone.idle
        ? colors.ink3
        : toneColor(context, tone);
    return Container(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.s12,
        MaeveSpace.s10,
        MaeveSpace.s12,
        MaeveSpace.s10,
      ),
      decoration: BoxDecoration(
        color: colors.surface,
        borderRadius: BorderRadius.circular(MaeveRadius.md),
        border: Border.all(color: colors.hair, width: .5),
      ),
      child: Row(
        children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(width: MaeveSpace.s8),
          Expanded(
            child: Text(
              text,
              style: MaeveType.hint.copyWith(color: colors.ink2),
            ),
          ),
          if (action != null) ...[
            const SizedBox(width: MaeveSpace.s8),
            action!,
          ],
        ],
      ),
    );
  }
}

/// 底部活动条:在做的事 + 钢琴卷 + 停止。浮在阅读列上,不遮输入条。
///
/// 进度用钢琴卷而不是转圈:卷带表达"第几步、还剩几步",转圈只表达"在忙"。
class ActivityStrip extends StatelessWidget {
  const ActivityStrip({
    super.key,
    required this.text,
    this.onStop,
    this.step,
    this.states = const [],
    this.tone = MaeveTone.running,
  });

  final String text;

  /// 第几步(可选,给自动化任务用)。
  final int? step;

  /// 钢琴卷的状态;空列表时只显示文字。
  final List<NoteState> states;

  final VoidCallback? onStop;
  final MaeveTone tone;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      shape: MaeveShapes.button,
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.s12,
        MaeveSpace.s8,
        MaeveSpace.s8,
        MaeveSpace.s8,
      ),
      child: Row(
        children: [
          StatusDot(tone: tone, size: 6),
          const SizedBox(width: MaeveSpace.s8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  text,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: MaeveType.caption.copyWith(color: colors.ink2),
                ),
                if (states.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  PianoRoll(states: states, height: 10, note: 2.5, gap: 2),
                ],
              ],
            ),
          ),
          if (step != null) ...[
            Text(
              '第 $step 步',
              style: MaeveType.monoSmall.copyWith(color: colors.ink3),
            ),
            const SizedBox(width: MaeveSpace.s8),
          ],
          if (onStop != null)
            Pressable(
              onTap: onStop,
              borderRadius: BorderRadius.circular(MaeveRadius.pill),
              semanticLabel: '停止',
              child: Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: colors.ink,
                  shape: BoxShape.circle,
                ),
                child: Icon(Icons.stop_rounded, size: 16, color: colors.bg),
              ),
            ),
        ],
      ),
    );
  }
}

/// 思考块:收起一行,展开看过程。生成结束后自动折叠。
class ThinkingPill extends StatefulWidget {
  const ThinkingPill({
    super.key,
    required this.steps,
    this.lines = const [],
    this.expanded = false,
    this.onToggle,
    this.busy = false,
  });

  final int steps;
  final List<String> lines;
  final bool expanded;
  final VoidCallback? onToggle;
  final bool busy;

  @override
  State<ThinkingPill> createState() => _ThinkingPillState();
}

class _ThinkingPillState extends State<ThinkingPill> {
  late bool _open = widget.expanded;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final motion = AppMotion.of(context);
    final canToggle = widget.onToggle != null || widget.lines.isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Pressable(
          onTap: canToggle
              ? () {
                  setState(() => _open = !_open);
                  widget.onToggle?.call();
                }
              : null,
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
          child: Container(
            padding: const EdgeInsets.symmetric(
              horizontal: MaeveSpace.s10,
              vertical: 5,
            ),
            decoration: BoxDecoration(
              color: colors.fill,
              borderRadius: BorderRadius.circular(MaeveRadius.pill),
              border: Border.all(color: colors.hair, width: .5),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (widget.busy)
                  const MazeRing(size: 11, indeterminate: true)
                else
                  StatusDot(tone: MaeveTone.ready, size: 5),
                const SizedBox(width: 6),
                Text(
                  widget.busy ? '正在走这一步' : '思考了 ${widget.steps} 步',
                  style: MaeveType.hint.copyWith(color: colors.ink3),
                ),
                if (canToggle) ...[
                  const SizedBox(width: 2),
                  AnimatedRotation(
                    turns: _open ? .5 : 0,
                    duration: motion.quick,
                    child: Icon(
                      Icons.expand_more,
                      size: 14,
                      color: colors.ink3,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
        ExpandReveal(
          visible: _open && widget.lines.isNotEmpty,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(6, MaeveSpace.s8, 0, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final line in widget.lines)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Text(
                      line,
                      style: MaeveType.hint.copyWith(color: colors.ink3),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// 权限胶囊:有件事需要用户点头。
class PermissionPill extends StatelessWidget {
  const PermissionPill({
    super.key,
    required this.text,
    this.onTap,
    this.tone = MaeveTone.warning,
  });

  final String text;
  final VoidCallback? onTap;
  final MaeveTone tone;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final color = toneColor(context, tone);
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.pill),
      child: Container(
        padding: const EdgeInsets.fromLTRB(10, 6, 12, 6),
        decoration: BoxDecoration(
          color: colors.warnSoft,
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
          border: Border.all(color: color.withValues(alpha: .35), width: .5),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.shield_outlined, size: 13, color: color),
            const SizedBox(width: 6),
            Text(
              text,
              style: MaeveType.label.copyWith(fontSize: 12, color: color),
            ),
          ],
        ),
      ),
    );
  }
}

/// 状态 chip:设置行右侧的值(带文字,不只靠颜色)。
class StatusChip extends StatelessWidget {
  const StatusChip({
    super.key,
    required this.label,
    this.tone = MaeveTone.idle,
    this.filled = false,
  });

  final String label;
  final MaeveTone tone;
  final bool filled;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final color = toneColor(context, tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: filled ? color.withValues(alpha: .12) : null,
        borderRadius: BorderRadius.circular(MaeveRadius.pill),
        border: filled ? null : Border.all(color: colors.hair, width: .5),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (tone != MaeveTone.idle) ...[
            StatusDot(tone: tone, size: 4),
            const SizedBox(width: 5),
          ],
          Text(
            label,
            style: MaeveType.label.copyWith(
              fontSize: 11.5,
              color: filled ? color : colors.ink3,
            ),
          ),
        ],
      ),
    );
  }
}

/// 把 [MaeveColors] 直接递给需要它的老调用点(少写一层 `MaeveTheme.of`)。
extension MaeveColorsOf on BuildContext {
  MaeveColors get maeveColors => MaeveTheme.of(this).colors;
}

/// 工作进行中的一行字:强调色慢流渐变 + 极轻的明度呼吸。
///
/// 参考 desktop-for-step-code 的工作标签处理(品牌渐变慢流 + 立方呼吸),但遵守
/// Maeve 自己的「唯一彩色」:渐变只在强调色与墨色之间流动,不引入第二套色相。
/// 空闲时就是普通一行字;reduced-motion 时渐变静止、呼吸关闭。
///
/// 注意:进行中是无限动画,别在需要 `pumpAndSettle` 的测试里开 active。
class WorkingLine extends StatefulWidget {
  const WorkingLine({
    super.key,
    required this.text,
    required this.active,
    this.style,
  });

  final String text;
  final bool active;

  /// 传入的样式只取字号字重,颜色由这里给(渐变要吃文字形状)。
  final TextStyle? style;

  @override
  State<WorkingLine> createState() => _WorkingLineState();
}

class _WorkingLineState extends State<WorkingLine>
    with SingleTickerProviderStateMixin {
  late final AnimationController _flow = AnimationController(
    vsync: this,
    duration: MaeveMotion.breath,
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _sync();
  }

  @override
  void didUpdateWidget(WorkingLine oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.active != widget.active) _sync();
  }

  void _sync() {
    final run = widget.active && !AppMotion.of(context).reduced;
    if (run && !_flow.isAnimating) {
      _flow.repeat();
    } else if (!run && _flow.isAnimating) {
      _flow.stop();
    }
  }

  @override
  void dispose() {
    _flow.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final base = (widget.style ?? MaeveType.hint).copyWith(color: colors.ink2);
    if (!widget.active) return Text(widget.text, style: base);
    if (AppMotion.of(context).reduced) {
      return Text(widget.text, style: base.copyWith(color: colors.accent));
    }
    return AnimatedBuilder(
      animation: _flow,
      builder: (context, _) {
        // 渐变比文字宽一倍,平移一个身位:流过去再回来,不断线。
        final t = _flow.value * 2 - 1;
        return ShaderMask(
          shaderCallback: (bounds) => LinearGradient(
            begin: Alignment.centerLeft,
            end: Alignment.centerRight,
            colors: [
              colors.accent,
              colors.ink,
              colors.accent,
              colors.ink,
              colors.accent,
            ],
            stops: const [-.5, -.25, 0, .25, .5],
            transform: _FlowTransform(t),
          ).createShader(bounds),
          child: Text(widget.text, style: base.copyWith(color: colors.ink)),
        );
      },
    );
  }
}

/// 把渐变平移 [t] 个文字宽(负半周向左、正半周向右),接起来就是慢流。
class _FlowTransform extends GradientTransform {
  const _FlowTransform(this.t);

  final double t;

  @override
  Matrix4 transform(Rect bounds, {TextDirection? textDirection}) =>
      Matrix4.translationValues(bounds.width * t, 0, 0);
}

/// 空态扉页的标题:从左往右逐个字打出,实心块光标跟在最后一个字后面。
///
/// 宽度按整句预置(隐形全文占位),打字过程中不跳版;reduced-motion 时整句直接
/// 显示、不出光标。参考 desktop-for-step-code 首页标题的打字处理。
class TypedHeadline extends StatefulWidget {
  const TypedHeadline({
    super.key,
    required this.text,
    this.style,
    this.textAlign = TextAlign.center,
  });

  final String text;
  final TextStyle? style;
  final TextAlign textAlign;

  @override
  State<TypedHeadline> createState() => _TypedHeadlineState();
}

class _TypedHeadlineState extends State<TypedHeadline>
    with SingleTickerProviderStateMixin {
  late final AnimationController _type = AnimationController(
    vsync: this,
    duration: Duration(milliseconds: 90 * widget.text.characters.length),
  );

  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // MediaQuery 只能在 didChangeDependencies / build 里读,initState 里不行。
    if (AppMotion.of(context).reduced) {
      _type.value = 1;
    } else if (!_started) {
      _type.forward();
    }
    _started = true;
  }

  @override
  void didUpdateWidget(TypedHeadline oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.text != widget.text) {
      _type.duration = Duration(
        milliseconds: 90 * widget.text.characters.length,
      );
      if (AppMotion.of(context).reduced) {
        _type.value = 1;
      } else {
        _type.forward(from: 0);
      }
    }
  }

  @override
  void dispose() {
    _type.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final style = widget.style ?? MaeveType.display;
    final colors = MaeveTheme.of(context).colors;
    final characters = widget.text.characters;
    return Semantics(
      label: widget.text,
      excludeSemantics: true,
      child: AnimatedBuilder(
        animation: _type,
        builder: (context, _) {
          if (_type.isCompleted) {
            return Text(
              widget.text,
              key: const ValueKey('typed-headline-visible'),
              textAlign: widget.textAlign,
              style: style,
            );
          }
          final shown = (characters.length * _type.value).floor();
          return Stack(
            children: [
              // 隐形全文占位:宽度一次性给足,打字不跳版。
              Text(
                widget.text,
                textAlign: widget.textAlign,
                style: style.copyWith(color: Colors.transparent),
              ),
              Positioned.fill(
                child: RichText(
                  key: const ValueKey('typed-headline-visible'),
                  textAlign: widget.textAlign,
                  text: TextSpan(
                    style: style,
                    children: [
                      TextSpan(text: characters.take(shown).toString()),
                      // 实心块光标:和文字同色,盖在下一个字的位置上。
                      WidgetSpan(
                        child: ColoredBox(
                          color: colors.accent,
                          child: const SizedBox(width: 8, height: 3),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

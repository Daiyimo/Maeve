import 'package:flutter/material.dart';

import '../../design/maeve_motion.dart';
import '../../design/maeve_tokens.dart';
import '../../widgets/glass_surface.dart';
import 'pressable.dart';

/// 尺寸变化平滑过渡(展开/收起、消息进出)。
///
/// 不走 `AnimatedSize` 的默认 100ms,统一用结构档 240ms;减少动画时归零。
class SmoothSize extends StatelessWidget {
  const SmoothSize({
    super.key,
    required this.child,
    this.alignment = Alignment.topCenter,
  });

  final Widget child;
  final AlignmentGeometry alignment;

  @override
  Widget build(BuildContext context) {
    final motion = AppMotion.of(context);
    return AnimatedSize(
      duration: motion.standard,
      curve: MaeveMotion.std,
      alignment: alignment,
      child: child,
    );
  }
}

/// 由 `visible` 驱动的展开:高度 + 淡入一起走(思考块、附件 tray、引文)。
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
  final scheme = Theme.of(context).colorScheme;
  final dark = Theme.of(context).brightness == Brightness.dark;
  return switch (tone) {
    MaeveTone.idle => scheme.onSurfaceVariant,
    MaeveTone.running => scheme.primary,
    MaeveTone.ready => MaeveTokens.success(
      dark ? Brightness.dark : Brightness.light,
    ),
    MaeveTone.warning => MaeveTokens.warning(
      dark ? Brightness.dark : Brightness.light,
    ),
    MaeveTone.danger => scheme.error,
  };
}

/// 状态点:形状 + 颜色双编码 —— 空闲是空心环,进行中是实心 + 呼吸,就绪是实心。
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
    final hollow = widget.tone == MaeveTone.idle;
    final size = widget.size;
    return SizedBox.square(
      dimension: size * 2,
      child: Center(
        child: Stack(
          alignment: Alignment.center,
          children: [
            if (widget.tone == MaeveTone.running)
              AnimatedBuilder(
                animation: _breath,
                builder: (context, _) => Container(
                  width: size + 4 + _breath.value * 3,
                  height: size + 4 + _breath.value * 3,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: color.withValues(alpha: .16 - _breath.value * .08),
                  ),
                ),
              ),
            Container(
              width: size,
              height: size,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: hollow ? Colors.transparent : color,
                border: hollow ? Border.all(color: color, width: 1.2) : null,
              ),
            ),
          ],
        ),
      ),
    );
  }
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
    final style = Theme.of(context).textTheme.bodySmall;
    return Semantics(
      container: true,
      label: '状态：$text',
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          StatusDot(tone: tone, size: 6),
          const SizedBox(width: 4),
          ConstrainedBox(
            constraints: BoxConstraints(maxWidth: maxWidth),
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: style,
            ),
          ),
        ],
      ),
    );
  }
}

/// 空状态:图标 + 一句话 + 一个动作。列表空了、还没开始时用它,不要空白页。
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
    final scheme = Theme.of(context).colorScheme;
    final tint = tone ?? scheme.primary;
    return Padding(
      padding: EdgeInsets.symmetric(
        horizontal: MaeveSpace.xl,
        vertical: compact ? MaeveSpace.xxl : MaeveSpace.xxxl,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: compact ? 40 : 52,
            height: compact ? 40 : 52,
            decoration: BoxDecoration(
              color: tint.withValues(alpha: .12),
              borderRadius: BorderRadius.circular(MaeveRadius.md),
            ),
            child: Icon(icon, size: compact ? 20 : 24, color: tint),
          ),
          const SizedBox(height: MaeveSpace.lg),
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          if (body != null) ...[
            const SizedBox(height: MaeveSpace.sm),
            Text(
              body!,
              style: Theme.of(context).textTheme.bodyMedium
                  ?.copyWith(color: scheme.onSurfaceVariant),
            ),
          ],
          if (action != null) ...[
            const SizedBox(height: MaeveSpace.lg),
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
    final color = toneColor(context, tone);
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.md,
        MaeveSpace.sm + 2,
        MaeveSpace.md,
        MaeveSpace.sm + 2,
      ),
      child: Row(
        children: [
          Icon(icon, size: 17, color: color),
          const SizedBox(width: MaeveSpace.sm),
          Expanded(
            child: Text(text, style: Theme.of(context).textTheme.bodySmall),
          ),
          if (action != null) ...[
            const SizedBox(width: MaeveSpace.sm),
            action!,
          ],
        ],
      ),
    );
  }
}

/// 底部活动条:在做的事 + 停止。浮在阅读列上,不遮输入条。
///
/// 状态点 + 一句人话,不显示秒数(第 7 节 B3:状态行不走秒)。
class ActivityStrip extends StatelessWidget {
  const ActivityStrip({
    super.key,
    required this.text,
    this.onStop,
    this.step,
    this.tone = MaeveTone.running,
  });

  final String text;

  /// 第几步(可选,给自动化任务用)。
  final int? step;
  final VoidCallback? onStop;
  final MaeveTone tone;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      borderRadius: BorderRadius.circular(MaeveRadius.lg),
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.md,
        MaeveSpace.sm,
        MaeveSpace.sm,
        MaeveSpace.sm,
      ),
      child: Row(
        children: [
          StatusDot(tone: tone, size: 6),
          const SizedBox(width: MaeveSpace.sm),
          Expanded(
            child: Text(
              text,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          if (step != null) ...[
            Text(
              '第 $step 步',
              style: MaeveFonts.mono(
                context,
                size: 11,
                color: scheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: MaeveSpace.sm),
          ],
          if (onStop != null)
            Pressable(
              onTap: onStop,
              borderRadius: BorderRadius.circular(MaeveRadius.pill),
              haptic: null,
              semanticLabel: '停止',
              child: Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: scheme.onSurface.withValues(alpha: .06),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Icons.stop_rounded,
                  size: 16,
                  color: scheme.onSurface,
                ),
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
    final scheme = Theme.of(context).colorScheme;
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
              horizontal: MaeveSpace.md,
              vertical: 6,
            ),
            decoration: BoxDecoration(
              color: scheme.onSurface.withValues(alpha: .05),
              borderRadius: BorderRadius.circular(MaeveRadius.pill),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                StatusDot(
                  tone: widget.busy ? MaeveTone.running : MaeveTone.idle,
                  size: 6,
                ),
                const SizedBox(width: 6),
                Text(
                  widget.busy ? '正在思考' : '思考了 ${widget.steps} 步',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                if (canToggle) ...[
                  const SizedBox(width: 2),
                  AnimatedRotation(
                    turns: _open ? .5 : 0,
                    duration: motion.quick,
                    child: Icon(
                      Icons.expand_more,
                      size: 15,
                      color: scheme.onSurfaceVariant,
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
            padding: const EdgeInsets.fromLTRB(6, MaeveSpace.sm, 0, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final line in widget.lines)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 4),
                    child: Text(
                      line,
                      style: Theme.of(context).textTheme.bodySmall,
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
    final color = toneColor(context, tone);
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.pill),
      child: Container(
        padding: const EdgeInsets.fromLTRB(10, 6, 12, 6),
        decoration: BoxDecoration(
          color: color.withValues(alpha: .12),
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.shield_outlined, size: 14, color: color),
            const SizedBox(width: 6),
            Text(
              text,
              style: Theme.of(context).textTheme.labelMedium
                  ?.copyWith(color: color),
            ),
          ],
        ),
      ),
    );
  }
}

/// 自动同意胶囊:开着的时候一直可见,关掉就消失(不能默默开着)。
class AutoApprovePill extends StatelessWidget {
  const AutoApprovePill({super.key, this.onTap, this.scope = '本次会话'});

  final VoidCallback? onTap;
  final String scope;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final color = MaeveTokens.warning(
      dark ? Brightness.dark : Brightness.light,
    );
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.pill),
      child: Container(
        padding: const EdgeInsets.fromLTRB(10, 6, 12, 6),
        decoration: BoxDecoration(
          color: color.withValues(alpha: .14),
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.bolt_rounded, size: 14, color: color),
            const SizedBox(width: 4),
            Text(
              '自动同意 · $scope',
              style: Theme.of(context).textTheme.labelMedium
                  ?.copyWith(color: color),
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
    final color = toneColor(context, tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: filled ? color.withValues(alpha: .14) : null,
        borderRadius: BorderRadius.circular(MaeveRadius.pill),
        border: filled ? null : Border.all(color: color.withValues(alpha: .35)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (tone != MaeveTone.idle) ...[
            Container(
              width: 5,
              height: 5,
              decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            ),
            const SizedBox(width: 5),
          ],
          Text(
            label,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: filled
                  ? color
                  : Theme.of(context).colorScheme.onSurfaceVariant,
              letterSpacing: .2,
            ),
          ),
        ],
      ),
    );
  }
}

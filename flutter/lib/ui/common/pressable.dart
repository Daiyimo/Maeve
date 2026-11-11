import 'dart:async';

import 'package:flutter/material.dart';

import 'haptics.dart';

import '../../design/maeve_motion.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';

/// 可按压的东西:按下缩到 0.97 + 一次水波 + 可选触感。
///
/// 三条约定(与 Compose 侧同一个手感):
/// 1. 缩放 90ms、`pressScale 0.97`,按到底也不越过;
/// 2. 水波 90ms 起手;**滚动中不显示**(手指是在滚列表,不是点按钮);
/// 3. 缩放做在内容上,不做在会模糊的玻璃上 —— 每帧按新尺寸重采样背景太贵。
class Pressable extends StatefulWidget {
  const Pressable({
    super.key,
    required this.child,
    this.onTap,
    this.onLongPress,
    this.borderRadius = const BorderRadius.all(Radius.circular(MaeveRadius.md)),
    this.scale = true,
    this.ripple = true,
    this.haptic,
    this.enabled = true,
    this.semanticLabel,
    this.behavior = HitTestBehavior.opaque,
  });

  final Widget child;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;

  /// 水波裁剪用的圆角,跟着外层形状走。
  final BorderRadius borderRadius;

  /// 关掉缩放(整块玻璃面板不要缩)。
  final bool scale;

  final bool ripple;

  /// 点按时给的触感;null = 不震。
  final MaeveHaptic? haptic;

  final bool enabled;
  final String? semanticLabel;
  final HitTestBehavior behavior;

  @override
  State<Pressable> createState() => _PressableState();
}

class _PressableState extends State<Pressable> {
  bool _down = false;
  bool _rippleVisible = false;
  Alignment _light = Alignment.topLeft;
  Timer? _rippleTimer;

  bool get _scrolling {
    final scrollable = Scrollable.maybeOf(context);
    return scrollable?.position.isScrollingNotifier.value ?? false;
  }

  @override
  void dispose() {
    _rippleTimer?.cancel();
    super.dispose();
  }

  void _press() {
    if (!widget.enabled) return;
    setState(() => _down = true);
    // 滚动中不起水波:手指在滚,不在点。
    if (widget.ripple && !_scrolling) {
      _rippleTimer?.cancel();
      _rippleTimer = Timer(MaeveMotion.press, () {
        if (mounted && _down) setState(() => _rippleVisible = true);
      });
    }
  }

  void _release() {
    _rippleTimer?.cancel();
    if (!mounted) return;
    setState(() {
      _down = false;
      _rippleVisible = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final motion = AppMotion.of(context);
    final colors = MaeveTheme.of(context).colors;

    Widget content = widget.child;
    if (widget.ripple) {
      content = Stack(
        fit: StackFit.passthrough,
        children: [
          content,
          Positioned.fill(
            child: IgnorePointer(
              child: AnimatedOpacity(
                opacity: _rippleVisible ? 1 : 0,
                duration: motion.press,
                curve: Curves.easeOut,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: RadialGradient(
                      center: _light,
                      radius: 1.25,
                      colors: [
                        Colors.white.withValues(
                          alpha: colors.isDark ? .18 : .42,
                        ),
                        colors.accent.withValues(alpha: .06),
                        Colors.transparent,
                      ],
                    ),
                    borderRadius: widget.borderRadius,
                  ),
                ),
              ),
            ),
          ),
        ],
      );
    }

    if (widget.scale) {
      content = AnimatedScale(
        scale: _down ? motion.pressedScale : 1,
        duration: motion.press,
        curve: MaeveMotion.glide,
        child: content,
      );
    }

    return Semantics(
      button: widget.onTap != null,
      enabled: widget.enabled,
      label: widget.semanticLabel,
      child: GestureDetector(
        behavior: widget.behavior,
        onTapDown: (details) {
          final box = context.findRenderObject();
          if (box is RenderBox && box.hasSize) {
            _light = Alignment(
              (details.localPosition.dx / box.size.width * 2 - 1).clamp(-1, 1),
              (details.localPosition.dy / box.size.height * 2 - 1).clamp(-1, 1),
            );
          }
          _press();
        },
        onTapCancel: _release,
        onTapUp: (_) => _release(),
        onTap: widget.enabled && widget.onTap != null
            ? () {
                final haptic = widget.haptic;
                if (haptic != null) unawaited(MaeveHaptics.play(haptic));
                widget.onTap!();
              }
            : null,
        onLongPress: widget.enabled && widget.onLongPress != null
            ? () {
                _release();
                final haptic = widget.haptic;
                if (haptic != null) unawaited(MaeveHaptics.play(haptic));
                widget.onLongPress!();
              }
            : null,
        child: content,
      ),
    );
  }
}

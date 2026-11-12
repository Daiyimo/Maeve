import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';

import 'chat_page.dart';
import 'sessions_drawer.dart';
import '../design/maeve_motion.dart';
import '../design/maeve_surfaces.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

/// Chat and its conversation list share one navigation surface.
class MaeveShell extends StatefulWidget {
  const MaeveShell({super.key});
  @override
  State<MaeveShell> createState() => _MaeveShellState();
}

class _MaeveShellState extends State<MaeveShell>
    with SingleTickerProviderStateMixin {
  late final AnimationController _drawer = AnimationController(
    vsync: this,
    duration: MaeveMotion.emphasized,
  )..addListener(_syncOverlay);
  bool _folded = false, _overlayOpen = false, _dragging = false;
  double _dragStart = 0, _dragDelta = 0;
  bool get _wide => MediaQuery.sizeOf(context).width >= MaeveBreakpoints.wide;
  double get _width => math.min(
    300 + MediaQuery.viewPaddingOf(context).left,
    MediaQuery.sizeOf(context).width - 56,
  );

  @override
  void dispose() {
    _drawer.dispose();
    super.dispose();
  }

  void _syncOverlay() {
    final open = _drawer.value > 0;
    if (open != _overlayOpen && mounted) setState(() => _overlayOpen = open);
  }

  void _settle(bool open) => _drawer.animateTo(
    open ? 1 : 0,
    duration: open
        ? AppMotion.of(context).emphasized
        : AppMotion.of(context).standard,
    curve: open ? MaeveMotion.glide : MaeveMotion.glide.flipped,
  );
  void _close() => _settle(false);
  void _toggle() {
    FocusManager.instance.primaryFocus?.unfocus();
    if (_wide) {
      setState(() => _folded = !_folded);
    } else {
      _settle(_drawer.value < .5);
    }
  }

  void _start(DragStartDetails details) {
    _dragging = !_wide && (details.localPosition.dx <= 28 || _drawer.value > 0);
    _dragStart = _drawer.value;
    _dragDelta = 0;
    if (_dragging) FocusManager.instance.primaryFocus?.unfocus();
  }

  void _update(DragUpdateDetails details) {
    if (!_dragging) return;
    _dragDelta += details.delta.dx;
    _drawer.value = (_dragStart + _dragDelta / _width).clamp(0, 1);
  }

  void _end(DragEndDetails details) {
    if (!_dragging) return;
    _dragging = false;
    final velocity = details.velocity.pixelsPerSecond.dx;
    final target = velocity.abs() > 600 ? velocity > 0 : _drawer.value > .35;
    if (velocity.abs() > 600 && !AppMotion.of(context).reduced) {
      _drawer.animateWith(
        SpringSimulation(
          MaeveMotion.flingSpring,
          _drawer.value,
          target ? 1 : 0,
          velocity / _width,
        ),
      );
    } else {
      _settle(target);
    }
  }

  @override
  Widget build(BuildContext context) {
    final wide = _wide;
    final colors = MaeveTheme.of(context).colors;
    return PopScope(
      canPop: !_overlayOpen,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _close();
      },
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onHorizontalDragStart: _start,
          onHorizontalDragUpdate: _update,
          onHorizontalDragEnd: _end,
          child: AnimatedBuilder(
            animation: _drawer,
            builder: (context, _) => Stack(
              fit: StackFit.expand,
              children: [
                const Positioned.fill(child: MaeveBackdrop()),
                Positioned.fill(
                  child: Row(
                    children: [
                      if (wide)
                        AnimatedContainer(
                          duration: AppMotion.of(context).emphasized,
                          curve: MaeveMotion.glide,
                          width: _folded ? 0 : 288,
                          child: ClipRect(
                            child: OverflowBox(
                              alignment: Alignment.centerLeft,
                              minWidth: 288,
                              maxWidth: 288,
                              child: SessionsDrawer(
                                docked: true,
                                onClose: () => setState(() => _folded = true),
                              ),
                            ),
                          ),
                        ),
                      Expanded(child: ChatPage(onMenu: _toggle)),
                    ],
                  ),
                ),
                if (!wide && _drawer.value > 0) ...[
                  Positioned.fill(
                    child: GestureDetector(
                      key: const ValueKey('shell-scrim'),
                      onTap: _close,
                      child: ColoredBox(
                        color: colors.scrim.withValues(
                          alpha: colors.scrim.a * _drawer.value,
                        ),
                      ),
                    ),
                  ),
                  Positioned(
                    left: 0,
                    top: 0,
                    bottom: 0,
                    width: _width,
                    child: Transform.translate(
                      offset: Offset(-_width * (1 - _drawer.value), 0),
                      child: SessionsDrawer(onClose: _close),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

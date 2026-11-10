import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/physics.dart';

import '../design/maeve_motion.dart';
import '../design/maeve_surfaces.dart';
import '../design/maeve_tokens.dart';
import 'chat_page.dart';
import 'quick_panel.dart';
import 'sessions_drawer.dart';

/// B2 主壳:聊天页在底下,会话抽屉从左、QuickPanel 从右,两者互斥。
///
/// 手感和落位都靠**一个**进度值:
/// - 手指移动多少,面板就走多少(跟手);
/// - 松手才落位:开用 emphasized(340)+glide,iOS 式长尾减速;关用 standard(240);
/// - 甩出去用带 0.15 回弹的弹簧(不是曲线,曲线没有回弹);
/// - 拉过头最多 48px 橡皮筋;
/// - 拉开前先 unfocus,免得键盘和抽屉打架。
class MaeveShell extends StatefulWidget {
  const MaeveShell({super.key});

  @override
  State<MaeveShell> createState() => _MaeveShellState();
}

enum _Panel { none, drawer, quick }

class _MaeveShellState extends State<MaeveShell> with TickerProviderStateMixin {
  late final AnimationController _drawer = AnimationController(
    vsync: this,
    duration: MaeveMotion.emphasized,
  );
  late final AnimationController _quick = AnimationController(
    vsync: this,
    duration: MaeveMotion.emphasized,
  );

  /// 宽屏时列表可以折叠(≡ 变折叠/展开)。
  bool _folded = false;

  /// 只在 0 ↔ >0 的边沿变,PopScope 不必每帧重建。
  bool _overlayOpen = false;

  _Panel _drag = _Panel.none;
  double _dragStart = 0;
  double _dragDelta = 0;

  @override
  void initState() {
    super.initState();
    _drawer.addListener(_syncOverlay);
    _quick.addListener(_syncOverlay);
  }

  @override
  void dispose() {
    _drawer.dispose();
    _quick.dispose();
    super.dispose();
  }

  void _syncOverlay() {
    final open = _drawer.value > 0 || _quick.value > 0;
    if (open != _overlayOpen && mounted) {
      setState(() => _overlayOpen = open);
    }
  }

  bool get _wide => MediaQuery.sizeOf(context).width >= MaeveLayout.breakpoint;

  double get _drawerWidth {
    final left = MediaQuery.viewPaddingOf(context).left;
    final width = MediaQuery.sizeOf(context).width;
    return math.min(MaeveLayout.sidebarWidth + left, width - 56);
  }

  void _unfocus() => FocusManager.instance.primaryFocus?.unfocus();

  void _openDrawer() {
    _unfocus();
    _closeQuick();
    _drawer.animateTo(
      1,
      duration: AppMotion.of(context).emphasized,
      curve: MaeveMotion.glide,
    );
  }

  void _closeDrawer() => _drawer.animateTo(
    0,
    duration: AppMotion.of(context).standard,
    curve: MaeveMotion.glide.flipped,
  );

  void _openQuick() {
    _unfocus();
    _closeDrawer();
    _quick.animateTo(
      1,
      duration: AppMotion.of(context).emphasized,
      curve: MaeveMotion.glide,
    );
  }

  void _closeQuick() => _quick.animateTo(
    0,
    duration: AppMotion.of(context).standard,
    curve: MaeveMotion.glide.flipped,
  );

  void _toggleDrawer() {
    if (_wide) {
      setState(() => _folded = !_folded);
      return;
    }
    if (_drawer.value > .5) {
      _closeDrawer();
    } else {
      _openDrawer();
    }
  }

  // —— 跟手 ——
  void _onDragStart(DragStartDetails details) {
    final width = MediaQuery.sizeOf(context).width;
    final x = details.localPosition.dx;
    // 边缘优先:从右边拉开时,左边那个已经开着的直接让位(互斥)。
    if (x <= 28) {
      _drag = _Panel.drawer;
      _dragStart = _drawer.value;
      _quick.value = 0;
    } else if (x >= width - 28) {
      _drag = _Panel.quick;
      _dragStart = _quick.value;
      _drawer.value = 0;
    } else if (_drawer.value > 0) {
      _drag = _Panel.drawer;
      _dragStart = _drawer.value;
    } else if (_quick.value > 0) {
      _drag = _Panel.quick;
      _dragStart = _quick.value;
    } else {
      _drag = _Panel.none;
      return;
    }
    _dragDelta = 0;
    _unfocus();
  }

  void _onDragUpdate(DragUpdateDetails details) {
    if (_drag == _Panel.none) return;
    _dragDelta += details.delta.dx;
    // 橡皮筋:超过全开也只多走 48px。
    final overshoot = MaeveMotion.rubberBandLimit;
    if (_drag == _Panel.drawer) {
      final next = (_dragStart + _dragDelta / _drawerWidth).clamp(
        -48 / _drawerWidth,
        1 + overshoot / _drawerWidth,
      );
      _drawer.value = next;
      if (next > 0) _quick.value = 0;
    } else {
      final width = _drawerWidth;
      final next = (_dragStart - _dragDelta / width).clamp(
        -48 / width,
        1 + overshoot / width,
      );
      _quick.value = next;
      if (next > 0) _drawer.value = 0;
    }
  }

  void _onDragEnd(DragEndDetails details) {
    if (_drag == _Panel.none) return;
    final velocity = details.velocity.pixelsPerSecond.dx;
    final target = _drag;
    _drag = _Panel.none;
    if (target == _Panel.drawer) {
      final flungOut = velocity < -600;
      final flungIn = velocity > 600;
      if (flungOut) {
        _fling(_drawer, 0, velocity.abs());
      } else if (flungIn) {
        _fling(_drawer, 1, velocity.abs());
      } else {
        _settle(_drawer, _drawer.value > .35);
      }
    } else {
      final flungOut = velocity > 600;
      final flungIn = velocity < -600;
      if (flungOut) {
        _fling(_quick, 0, velocity.abs());
      } else if (flungIn) {
        _fling(_quick, 1, velocity.abs());
      } else {
        _settle(_quick, _quick.value > .35);
      }
    }
  }

  void _settle(AnimationController controller, bool open) {
    if (open) {
      controller.animateTo(
        1,
        duration: AppMotion.of(context).emphasized,
        curve: MaeveMotion.glide,
      );
    } else {
      controller.animateTo(
        0,
        duration: AppMotion.of(context).standard,
        curve: MaeveMotion.glide.flipped,
      );
    }
  }

  /// 甩出:唯一用弹簧的地方,带回弹。
  void _fling(AnimationController controller, double target, double velocity) {
    if (AppMotion.of(context).reduced) {
      controller.value = target;
      return;
    }
    controller.animateWith(
      SpringSimulation(
        MaeveMotion.flingSpring,
        controller.value,
        target,
        velocity / 1000,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final wide = width >= MaeveLayout.breakpoint;
    return PopScope(
      canPop: !_overlayOpen,
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        _closeDrawer();
        _closeQuick();
      },
      child: Scaffold(
        backgroundColor: Colors.transparent,
        // 跟手的手势挂在整棵树外面:抽屉拉开后有遮罩挡着,手势必须比遮罩更高,
        // 否则"开着的时候往回推"就推不动了。
        body: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onHorizontalDragStart: _onDragStart,
          onHorizontalDragUpdate: _onDragUpdate,
          onHorizontalDragEnd: _onDragEnd,
          child: AnimatedBuilder(
            animation: Listenable.merge([_drawer, _quick]),
            builder: (context, _) => Stack(
              fit: StackFit.expand,
              children: [
                const Positioned.fill(child: MaeveBackdrop()),
                Positioned.fill(
                  child: Row(
                    children: [
                      if (wide)
                        _DockedSidebar(
                          visible: !_folded,
                          width: MaeveLayout.sidebarWidth,
                          child: SessionsDrawer(
                            docked: true,
                            onClose: () => setState(() => _folded = true),
                          ),
                        ),
                      Expanded(
                        child: ChatPage(
                          onMenu: _toggleDrawer,
                          onQuickPanel: _openQuick,
                        ),
                      ),
                    ],
                  ),
                ),
                if (!wide) _scrim(context),
                if (!wide) _drawerLayer(context),
                _quickLayer(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _scrim(BuildContext context) {
    final surfaces = MaeveSurfaces.of(context);
    final progress = math.max(_drawer.value, _quick.value).clamp(0.0, 1.0);
    // 注意:这里必须返回 *定位* 的占位。非定位的 0×0 子件会让整个 Stack
    // 量到 0,聊天列表跟着变成 0 高 —— 这是"什么都没渲染"的经典坑。
    if (progress == 0) {
      return const Positioned(left: 0, top: 0, child: SizedBox.shrink());
    }
    return Positioned.fill(
      child: GestureDetector(
        key: const ValueKey('shell-scrim'),
        onTap: () {
          _closeDrawer();
          _closeQuick();
        },
        child: ColoredBox(
          color: surfaces.scrim.withValues(alpha: surfaces.scrim.a * progress),
        ),
      ),
    );
  }

  Widget _drawerLayer(BuildContext context) {
    final width = _drawerWidth;
    // 关着的时候不建:抽屉里那块 sheet 玻璃会一直采样背景,白白多一次模糊。
    if (_drawer.value == 0) {
      return const Positioned(left: 0, top: 0, child: SizedBox.shrink());
    }
    final offset = -width * (1 - _drawer.value);
    return Positioned(
      left: 0,
      top: 0,
      bottom: 0,
      width: width,
      child: Transform.translate(
        offset: Offset(offset, 0),
        child: SessionsDrawer(docked: false, onClose: _closeDrawer),
      ),
    );
  }

  Widget _quickLayer(BuildContext context) {
    final width = math.min(340.0, MediaQuery.sizeOf(context).width - 56);
    final offset = width * (1 - _quick.value);
    if (_quick.value == 0) {
      return const Positioned(left: 0, top: 0, child: SizedBox.shrink());
    }
    return Positioned(
      right: 0,
      top: 0,
      bottom: 0,
      width: width,
      child: Transform.translate(
        offset: Offset(offset, 0),
        child: QuickPanel(onClose: _closeQuick),
      ),
    );
  }
}

/// 宽屏常驻列表:折叠时宽度收掉,用同一条 glide 曲线。
class _DockedSidebar extends StatelessWidget {
  const _DockedSidebar({
    required this.visible,
    required this.width,
    required this.child,
  });

  final bool visible;
  final double width;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final motion = AppMotion.of(context);
    return AnimatedContainer(
      duration: motion.emphasized,
      curve: MaeveMotion.glide,
      width: visible ? width : 0,
      child: ClipRect(
        child: OverflowBox(
          alignment: Alignment.centerLeft,
          minWidth: width,
          maxWidth: width,
          child: child,
        ),
      ),
    );
  }
}

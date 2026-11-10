import 'package:flutter/material.dart';

/// 动效分档。数值与 Compose 侧 `MaeveMotion` 一致。
///
/// 三档语义,别跨档借用:反馈(≤160)只用于按压 / 淡入;结构(240–340)用于列表与折叠;
/// 弹层(380)用于底部弹层。`reduceMotion` 为真时**所有时长归零**(直接到位)。
abstract final class MaeveMotion {
  /// 按压缩放。
  static const pressMs = 90;

  /// 淡入淡出、图标切换。
  static const quickMs = 160;

  /// 列表项进出。
  static const standardMs = 240;

  /// 折叠 / 展开。
  static const collapseMs = 280;

  /// 抽屉落位、胶囊变形。
  static const emphasizedMs = 340;

  /// 底部弹层进。
  static const sheetInMs = 380;

  /// 停稳后模糊透出。
  static const glassInMs = 120;

  /// 呼吸。
  static const breathMs = 2400;

  /// 页面切换。
  static const pageMs = 220;

  /// 开关。
  static const switchMs = 200;

  /// 危险确认的长按时长:按住这么久才算同意。
  static const holdToConfirmMs = 1500;

  /// 按下时的缩放,不是整块变暗。
  static const pressScale = .97;

  static const press = Duration(milliseconds: pressMs);
  static const quick = Duration(milliseconds: quickMs);
  static const standard = Duration(milliseconds: standardMs);
  static const collapse = Duration(milliseconds: collapseMs);
  static const emphasized = Duration(milliseconds: emphasizedMs);
  static const sheetIn = Duration(milliseconds: sheetInMs);
  static const glassIn = Duration(milliseconds: glassInMs);
  static const breath = Duration(milliseconds: breathMs);
  static const page = Duration(milliseconds: pageMs);
  static const switchDuration = Duration(milliseconds: switchMs);

  /// 跟手后落位:起步快、收尾慢(iOS 长尾减速)。
  static const glide = Cubic(.32, .72, 0, 1);

  /// 减速曲线:页面推入、开关。
  static const decelerate = Cubic(0, 0, .2, 1);

  /// 折叠:进出一致。
  static const collapseCurve = Curves.easeInOutCubic;

  /// 弹性上限:拉过头最多这么多像素。
  static const rubberBandLimit = 48.0;

  /// 甩出用的弹簧(唯一带回弹的地方)。
  static final flingSpring = SpringDescription.withDampingRatio(
    mass: 1,
    stiffness: 400,
    ratio: .85,
  );
}

/// 当前上下文解析后的动效:减少动效时所有时长归 0。
///
/// ```dart
/// final motion = AppMotion.of(context);
/// AnimatedOpacity(duration: motion.quick, …)
/// ```
@immutable
class AppMotion {
  const AppMotion(this.reduced);

  /// 系统"移除动画"或"减少动态效果"打开。
  final bool reduced;

  static AppMotion of(BuildContext context) =>
      AppMotion(MediaQuery.disableAnimationsOf(context));

  Duration _d(Duration value) => reduced ? Duration.zero : value;

  Duration get press => _d(MaeveMotion.press);
  Duration get quick => _d(MaeveMotion.quick);
  Duration get standard => _d(MaeveMotion.standard);
  Duration get collapse => _d(MaeveMotion.collapse);
  Duration get emphasized => _d(MaeveMotion.emphasized);
  Duration get sheetIn => _d(MaeveMotion.sheetIn);
  Duration get glassIn => _d(MaeveMotion.glassIn);
  Duration get breath => _d(MaeveMotion.breath);
  Duration get page => _d(MaeveMotion.page);
  Duration get switchDuration => _d(MaeveMotion.switchDuration);

  /// 按压缩放:减少动效时保持 1(不缩放)。
  double get pressedScale => reduced ? 1 : MaeveMotion.pressScale;

  @override
  bool operator ==(Object other) =>
      other is AppMotion && other.reduced == reduced;

  @override
  int get hashCode => reduced.hashCode;
}

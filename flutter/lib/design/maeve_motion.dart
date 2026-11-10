import 'package:flutter/material.dart';

/// 动效 token:安静、短、跟手(第 4.3 节)。
///
/// 分四档用,不要跨档借用:反馈(≤160)只用于按压/淡入,结构(240–340)
/// 用于列表与折叠,弹层(380/260)用于底部弹层,页面(300/480)用于换页。
abstract final class MaeveMotion {
  // —— 反馈 ≤160ms ——
  /// 按压缩放、水波起始。
  static const press = Duration(milliseconds: 90);

  /// 淡入淡出、图标切换、复制对勾。
  static const quick = Duration(milliseconds: 160);

  // —— 结构 240–340 ——
  /// 列表项进出。
  static const standard = Duration(milliseconds: 240);

  /// 思考块折叠、展开。
  static const collapse = Duration(milliseconds: 280);

  /// 胶囊变形、抽屉落位。
  static const emphasized = Duration(milliseconds: 340);

  // —— 弹层 ——
  static const sheetIn = Duration(milliseconds: 380);
  static const sheetOut = Duration(milliseconds: 260);

  /// 停稳后模糊透出的淡入。
  static const glassIn = Duration(milliseconds: 120);

  // —— 页面 ——
  /// 引导步骤切换。
  static const page = Duration(milliseconds: 300);

  /// 二级页 iOS 式水平推入。
  static const push = Duration(milliseconds: 300);

  /// 慢速环境动画(空状态插图)。
  static const slow = Duration(milliseconds: 480);

  // —— 环境 ——
  /// 呼吸循环。
  static const breath = Duration(milliseconds: 2400);

  /// 首现逐项错开(前 8 项)。
  static const stagger = Duration(milliseconds: 30);

  /// 首现上移距离。
  static const rise = 8.0;

  // —— 曲线 ——
  /// Material 3 standard:Cubic(0.2, 0, 0, 1)。
  static const std = Cubic(.2, 0, 0, 1);

  /// 进场:减速到停。
  static const enter = Cubic(.05, .7, .1, 1);

  /// 退场:加速离开。
  static const exit = Cubic(.3, 0, .8, .15);

  /// 折叠:进出一致,来回都顺。
  static const collapseCurve = Curves.easeInOutCubic;

  /// iOS 长尾减速:抽屉/弹层落位用它,不用 linear。
  static const glide = Cubic(.32, .72, 0, 1);

  /// 底部弹层升起。
  static const sheetRise = Cubic(.2, .9, .25, 1);

  /// 下拉/横拉的橡皮筋上限(px)。
  static const rubberBandLimit = 48.0;

  /// 按下缩到 0.97,不越过。
  static const pressScale = .97;

  // —— 弹簧 ——
  /// 按压回弹(150ms,无回弹)。
  static final pressSpring = _spring(150, 0);

  /// 布局位移(350ms,无回弹)。
  static final layoutSpring = _spring(350, 0);

  /// 底部弹层落位(500ms,无回弹)。
  static final sheetSpring = _spring(500, 0);

  /// 甩出:唯一带回弹的弹簧(500ms,0.15)。
  static final flingSpring = _spring(500, .15);

  /// iOS 式弹簧:给"时长 + 回弹量",换算成质量 1 的刚度与阻尼比。
  static SpringDescription _spring(double milliseconds, double bounce) {
    final seconds = milliseconds / 1000;
    final omega = 2 * 3.141592653589793 / seconds;
    return SpringDescription.withDampingRatio(
      mass: 1,
      stiffness: omega * omega,
      ratio: (1 - bounce).clamp(0.05, 1.0),
    );
  }
}

/// 当前上下文解析后的动效:减少动画时所有时长归 0(循环动画停在静止帧)。
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
  Duration get sheetOut => _d(MaeveMotion.sheetOut);
  Duration get glassIn => _d(MaeveMotion.glassIn);
  Duration get page => _d(MaeveMotion.page);
  Duration get push => _d(MaeveMotion.push);
  Duration get slow => _d(MaeveMotion.slow);
  Duration get breath => _d(MaeveMotion.breath);

  /// 首现错开:第 [index] 项的延迟(前 8 项,之后不再递增)。
  Duration stagger(int index) =>
      reduced ? Duration.zero : MaeveMotion.stagger * index.clamp(0, 8);

  /// 按压缩放:减少动画时保持 1(不缩放)。
  double get pressedScale => reduced ? 1 : MaeveMotion.pressScale;

  @override
  bool operator ==(Object other) =>
      other is AppMotion && other.reduced == reduced;

  @override
  int get hashCode => reduced.hashCode;
}

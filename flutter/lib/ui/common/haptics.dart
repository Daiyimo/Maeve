import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// 触感五级。语义固定,不要按"手感轻重"临时挑一个。
enum MaeveHaptic {
  /// 入选、切换、按住起手(最轻)。
  tick,

  /// 普通按钮点按。
  light,

  /// 切换档位、拖动过阈值。
  medium,

  /// 危险操作完成、发送成功。
  heavy,

  /// 被拒绝:越界、不可用、权限不够。
  reject,
}

/// 全局唯一的触感出口。
///
/// - 设置里可以整体关掉([enabled]),关掉后零反馈,连调用都不发生。
/// - 系统不支持时静默降级(不抛异常、不占帧)。
/// - 业务代码**不要**直接调 `HapticFeedback`,统一走 [MaeveHaptics.play]。
abstract final class MaeveHaptics {
  /// 设置项"触感反馈"。关掉后所有调用直接返回。
  static bool enabled = true;

  /// 系统是否支持振动。第一次调用时探测一次,之后走缓存。
  static bool? _supported;

  /// 只探测,不真正震动 —— 进设置页时可以先问一句。
  static bool get isSupported => _supported ?? false;

  @visibleForTesting
  static void resetProbe() => _supported = null;

  static Future<void> play(MaeveHaptic level) async {
    if (!enabled) return;
    final supported = _supported ??= await _probe();
    if (!supported) return;
    try {
      await switch (level) {
        MaeveHaptic.tick => HapticFeedback.selectionClick(),
        MaeveHaptic.light => HapticFeedback.lightImpact(),
        MaeveHaptic.medium => HapticFeedback.mediumImpact(),
        MaeveHaptic.heavy => HapticFeedback.heavyImpact(),
        // 拒绝用两次轻击,手感是"不行",不是"成功"。
        MaeveHaptic.reject => _doubleTick(),
      };
    } on PlatformException {
      _supported = false;
    } on MissingPluginException {
      _supported = false;
    }
  }

  static Future<void> _doubleTick() async {
    await HapticFeedback.lightImpact();
    await Future<void>.delayed(const Duration(milliseconds: 70));
    await HapticFeedback.lightImpact();
  }

  /// 探测:平台通道在无振动硬件的设备上会返回 null 或抛异常。
  static Future<bool> _probe() async {
    try {
      await HapticFeedback.selectionClick();
      return true;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }
}

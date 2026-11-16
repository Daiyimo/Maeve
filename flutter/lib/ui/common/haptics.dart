import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../../bridge/maeve_bridge.dart';

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
/// - 真机上交给原生引擎(`maeve/ui` 的 `haptic` 方法):与悬浮胶囊、长按确认
///   共用同一台 VibrationEffect 引擎和同一份开关,五级手感全站一致。
///   Flutter 引擎自带的 HapticFeedback 在部分 ROM 上不振,只作未接桥时的兜底。
/// - 设置里可以整体关掉([enabled]),关掉后零反馈,连调用都不发生。
/// - 业务代码**不要**直接调 `HapticFeedback`,统一走 [MaeveHaptics.play]。
abstract final class MaeveHaptics {
  /// 设置项"触感反馈"。关掉后所有调用直接返回。
  static bool enabled = true;

  /// 原生通道是否可用。第一次调用时探测,之后走缓存;测试里可重置。
  static bool? _native;

  /// 测试与本地预览里没有宿主:置 true 后不再撞平台通道,直接走兜底。
  @visibleForTesting
  static bool offline = false;

  @visibleForTesting
  static void resetProbe() {
    _native = null;
    offline = false;
  }

  static Future<void> play(MaeveHaptic level) async {
    if (!enabled) return;
    if (offline) return _fallback(level);
    if (_native != false) {
      try {
        await MaeveBridge.methods.invokeMethod<void>('haptic', {
          'level': level.name,
        });
        _native = true;
        return;
      } on MissingPluginException {
        _native = false;
      } on PlatformException {
        // 通道在,这一下被门控拦下也算成功路径;不降级、不重复震。
        _native = true;
        return;
      } catch (_) {
        _native = false;
      }
    }
    await _fallback(level);
  }

  /// 未接桥时的兜底:Flutter 内建触感(测试环境静默)。
  static Future<void> _fallback(MaeveHaptic level) async {
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
      // 兜底也不支持时静默。
    } on MissingPluginException {
      // 同上。
    }
  }

  static Future<void> _doubleTick() async {
    await HapticFeedback.lightImpact();
    await Future<void>.delayed(const Duration(milliseconds: 70));
    await HapticFeedback.lightImpact();
  }
}

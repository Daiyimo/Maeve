import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/ui/common/haptics.dart';

/// 全部 widget 测试的统一前置:触感走离线兜底。
///
/// 真机上 MaeveHaptics 交给原生 `maeve/ui` 通道;测试里没有宿主,
/// 不置 offline 的话第一次 play 会撞 MissingPluginException 再降级,
/// 既慢又可能在个别用例里留下未决 Future。
Future<void> testExecutable(FutureOr<void> Function() testMain) async {
  setUp(() {
    MaeveHaptics.offline = true;
  });
  await testMain();
}

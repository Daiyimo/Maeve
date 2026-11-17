import 'dart:async';

import '../models/app_update.dart' show AppUpdate, GitHubAccelMode;
import 'maeve_bridge.dart';

/// 「获取更新」的桥接口。真机走 [HostUpdateBridge](宿主 MaeveUiPlugin),
/// 测试换假实现,页面代码一行不动 —— 与对话桥同一套约定。
abstract class MaeveUpdateBridge {
  const MaeveUpdateBridge();

  /// 读当前状态(不联网):存着的最近一次结果 + 下载进度 + 安装授权。
  Future<AppUpdate> state();

  /// 手动检查一次,等查完返回新状态。
  Future<AppUpdate> check();

  /// 开始下载新版安装包(宿主校验官方直链、大小与摘要)。
  Future<AppUpdate> download();

  /// 取消并删掉已下的包。
  Future<AppUpdate> cancel();

  /// 交给系统安装器。返回 installing / permission(先去开未知来源授权)。
  Future<String> install();

  /// 改「GitHub 加速」:检查更新问 GitHub 时域名怎么解析;返回改后的状态。
  Future<AppUpdate> setAccel(GitHubAccelMode mode, {String ip = ''});

  /// 状态变化(检查结果、下载进度)。没接桥时是空流,页面照样能手动刷新。
  Stream<AppUpdate> get changes => const Stream.empty();
}

class HostUpdateBridge extends MaeveUpdateBridge {
  const HostUpdateBridge();

  @override
  Future<AppUpdate> state() async => _parse(
    await MaeveBridge.methods.invokeMapMethod<Object?, Object?>('updateState'),
  );

  @override
  Future<AppUpdate> check() async => _parse(
    await MaeveBridge.methods.invokeMapMethod<Object?, Object?>('checkUpdate'),
  );

  @override
  Future<AppUpdate> download() async => _parse(
    await MaeveBridge.methods.invokeMapMethod<Object?, Object?>('downloadUpdate'),
  );

  @override
  Future<AppUpdate> cancel() async => _parse(
    await MaeveBridge.methods.invokeMapMethod<Object?, Object?>('cancelUpdate'),
  );

  @override
  Future<String> install() async {
    final reply = await MaeveBridge.methods
        .invokeMapMethod<Object?, Object?>('installUpdate');
    return reply?['state'] as String? ?? 'installing';
  }

  @override
  Future<AppUpdate> setAccel(GitHubAccelMode mode, {String ip = ''}) async =>
      _parse(await MaeveBridge.methods.invokeMapMethod<Object?, Object?>(
        'setGitHubAccel',
        {'mode': mode.name, 'ip': ip},
      ));

  /// 宿主状态流里 kind=update 的那部分;宿主状态(kind=status)不是更新,过滤掉。
  @override
  Stream<AppUpdate> get changes => MaeveBridge.status
      .where((e) => e is Map && e['kind'] == 'update' && e['update'] is Map)
      .map((e) => AppUpdate.fromMap((e as Map)['update'] as Map));

  AppUpdate _parse(Map<Object?, Object?>? map) {
    if (map == null) throw StateError('update_state_missing');
    return AppUpdate.fromMap(map);
  }
}

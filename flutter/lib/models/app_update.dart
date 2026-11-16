/// 「获取更新」的状态。全部来自宿主(检查、选包、校验都在原生 about 包里做),
/// 壳这边只负责显示,不自己判断"哪个版本更新"或"这个包能不能装"。
library;

/// 一个发布。[canDownload] 为真才谈得上应用内下载:
/// 宿主要求附件同时给了直链、字节数与 sha256,缺一样就只能去发布页。
class AppRelease {
  const AppRelease({
    required this.version,
    required this.name,
    this.notes = '',
    this.date,
    this.pageUrl = '',
    this.apkUrl,
    this.apkSize,
    this.canDownload = false,
    this.downloadUrl = '',
  });

  final String version, name, notes, pageUrl, downloadUrl;
  final String? date, apkUrl;
  final int? apkSize;
  final bool canDownload;

  factory AppRelease.fromMap(Map map) => AppRelease(
    version: map['version'] as String? ?? '',
    name: map['name'] as String? ?? '',
    notes: map['notes'] as String? ?? '',
    date: map['date'] as String?,
    pageUrl: map['pageUrl'] as String? ?? '',
    apkUrl: map['apkUrl'] as String?,
    apkSize: (map['apkSize'] as num?)?.toInt(),
    canDownload: map['canDownload'] == true,
    downloadUrl: map['downloadUrl'] as String? ?? '',
  );
}

/// 下载阶段。语义与原生 `DownloadPhase` 一一对应,不另造一套。
enum UpdatePhase { idle, downloading, paused, ready, failed }

/// 手动检查这一轮的结果。自动检查不改它(失败时不打扰用户)。
enum UpdateCheck { idle, checking, done, failed }

class AppUpdate {
  const AppUpdate({
    this.available = false,
    this.currentVersion = '',
    this.check = UpdateCheck.idle,
    this.manualRounds = 0,
    this.hasUpdate = false,
    this.latest,
    this.update,
    this.phase = UpdatePhase.idle,
    this.bytes = 0,
    this.total = -1,
    this.percent,
    this.canInstall = false,
    this.releasesUrl = '',
  });

  /// 查到过任何一个发布(不代表有新版)。
  final bool available;
  final String currentVersion, releasesUrl;
  final UpdateCheck check;

  /// 完成过几次手动检查:0 次时不该说"已是最新",因为根本还没查。
  final int manualRounds;
  final bool hasUpdate;
  final AppRelease? latest, update;
  final UpdatePhase phase;
  final int bytes, total;
  final int? percent;

  /// 系统「安装未知应用」授权。没授权时安装按钮先带用户去开。
  final bool canInstall;

  bool get checking => check == UpdateCheck.checking;
  bool get downloading =>
      phase == UpdatePhase.downloading || phase == UpdatePhase.paused;
  bool get ready => phase == UpdatePhase.ready;

  /// 有东西可删时才出现「删除下载」。
  bool get cancellable =>
      downloading || ready;

  /// 这一页该展示哪个发布:有新版看新版,没有就看最近查到的那个。
  AppRelease? get shown => update ?? latest;

  factory AppUpdate.fromMap(Map map) {
    final download = map['download'];
    final d = download is Map ? download : const {};
    return AppUpdate(
      available: map['available'] == true,
      currentVersion: map['currentVersion'] as String? ?? '',
      check: _enum(UpdateCheck.values, map['status'], UpdateCheck.idle),
      manualRounds: (map['manualRounds'] as num?)?.toInt() ?? 0,
      hasUpdate: map['hasUpdate'] == true,
      latest: map['latest'] is Map
          ? AppRelease.fromMap(map['latest'] as Map)
          : null,
      update: map['update'] is Map
          ? AppRelease.fromMap(map['update'] as Map)
          : null,
      phase: _enum(UpdatePhase.values, d['phase'], UpdatePhase.idle),
      bytes: (d['bytes'] as num?)?.toInt() ?? 0,
      total: (d['total'] as num?)?.toInt() ?? -1,
      percent: (d['percent'] as num?)?.toInt(),
      canInstall: map['canInstall'] == true,
      releasesUrl: map['releasesUrl'] as String? ?? '',
    );
  }

  static T _enum<T extends Enum>(List<T> values, Object? name, T fallback) =>
      values.firstWhere((v) => v.name == name, orElse: () => fallback);
}

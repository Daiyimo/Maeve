import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../bridge/maeve_update_bridge.dart';
import '../../design/maeve_motion.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../models/app_update.dart';
import '../../widgets/glass_surface.dart';
import '../../widgets/maeve_markdown.dart';
import '../common/app_page.dart';
import '../common/haptics.dart';
import '../common/status.dart';
import 'about_pages.dart';
import 'settings_components.dart';

/// 获取更新:查一次 GitHub 发布页,有新版就在应用里下、装。
///
/// **判断权全在宿主。** 哪个版本更新、挑哪个架构的包、下载完的包能不能装
/// (大小/摘要/包名/版本/签名),都由原生 about 包决定;这一页只把结果画出来。
/// 没接桥、连不上或发布里没有可用附件时,退回「在浏览器打开」——
/// 用户始终有一条能拿到新版的路,不会卡在这里。
class UpdatePage extends StatefulWidget {
  const UpdatePage({super.key, this.bridge});

  final MaeveUpdateBridge? bridge;

  @override
  State<UpdatePage> createState() => _UpdatePageState();
}

class _UpdatePageState extends State<UpdatePage> {
  late final MaeveUpdateBridge _bridge = widget.bridge ?? const HostUpdateBridge();

  AppUpdate _state = const AppUpdate();
  StreamSubscription<AppUpdate>? _sub;

  /// 读状态/下载/安装这类"正在处理"的占用;检查更新单独用 [_state.checking]。
  bool _busy = false;
  String? _error;
  String? _notice;
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
    _sub = _bridge.changes.listen(
      (next) {
        if (mounted) setState(() => _state = next);
      },
      onError: (_) {
        /* 流断了不影响手动检查;页面照样能刷新。 */
      },
    );
  }

  @override
  void dispose() {
    unawaited(_sub?.cancel());
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final next = await _bridge.state();
      if (mounted) {
        setState(() {
          _state = next;
          _loaded = true;
          _error = null;
        });
      }
    } catch (_) {
      // 没接桥(测试、纯 Flutter 预览)时不报错:页面仍给出浏览器入口。
      if (mounted) setState(() => _loaded = true);
    }
  }

  /// 一次操作:占住按钮、清掉上次说明、失败时如实说,不静默。
  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
      _notice = null;
    });
    try {
      await action();
    } on PlatformException catch (e) {
      if (mounted) {
        setState(() => _error = e.message ?? '更新未完成，请稍后重试。');
        unawaited(MaeveHaptics.play(MaeveHaptic.reject));
      }
    } catch (_) {
      if (mounted) {
        setState(() => _error = '更新未完成，请稍后重试。');
        unawaited(MaeveHaptics.play(MaeveHaptic.reject));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _check() => _run(() async {
    final next = await _bridge.check();
    if (!mounted) return;
    setState(() {
      _state = next;
      _loaded = true;
      _notice = switch (next.problem) {
        // 网络是通的、GitHub 上也问到了,只是没有已发布的版本:别说成"连不上"
        UpdateProblem.noRelease => 'GitHub 上还没有已发布的版本。',
        UpdateProblem.unreachable => '暂时连不上 GitHub，稍后再试。',
        // 失败但宿主没说原因(旧桥、异常路径):照旧说连不上,别冒充"已是最新"
        UpdateProblem.none when next.check == UpdateCheck.failed =>
          '暂时连不上 GitHub，稍后再试。',
        UpdateProblem.none => next.hasUpdate
            ? '发现新版本 ${next.update!.version}'
            : '已是最新版本。',
      };
    });
    unawaited(
      MaeveHaptics.play(
        next.hasUpdate ? MaeveHaptic.heavy : MaeveHaptic.light,
      ),
    );
  });

  Future<void> _download() => _run(() async {
    final next = await _bridge.download();
    if (mounted) setState(() => _state = next);
  });

  Future<void> _cancel() => _run(() async {
    final next = await _bridge.cancel();
    if (mounted) {
      setState(() {
        _state = next;
        _notice = '已删除下载的安装包。';
      });
    }
  });

  Future<void> _install() => _run(() async {
    final result = await _bridge.install();
    if (!mounted) return;
    setState(() {
      _notice = result == 'permission'
          ? '请先允许 Maeve 安装未知应用，回来后再次点「安装更新」。'
          : '已交给系统安装器，请在安装页确认。';
    });
    // 授权页回来时状态可能变了(用户开了开关),重读一次。
    await _load();
  });

  @override
  Widget build(BuildContext context) {
    final release = _state.shown;
    return AppPage.list(
      title: '获取更新',
      subtitle: '从 GitHub 发布页获取新版本',
      actions: [
        MaeveGlassButton(
          icon: Icons.refresh_rounded,
          tooltip: '检查更新',
          compact: true,
          enabled: !_busy && !_state.checking,
          haptic: MaeveHaptic.tick,
          onPressed: _check,
        ),
      ],
      children: [
        const SizedBox(height: MaeveSpace.s20),
        if (_error != null) ...[
          SettingsNotice(text: _error!, icon: Icons.error_outline_rounded),
          const SizedBox(height: MaeveSpace.s12),
        ],
        if (_notice != null) ...[
          SettingsNotice(text: _notice!, icon: Icons.info_outline),
          const SizedBox(height: MaeveSpace.s12),
        ],
        _VersionCard(state: _state, loaded: _loaded),
        const SizedBox(height: MaeveSpace.s12),
        MaeveGlassTextButton(
          label: _state.checking ? '正在检查…' : '检查更新',
          icon: Icons.system_update_alt_rounded,
          prominent: true,
          enabled: !_busy && !_state.checking,
          haptic: MaeveHaptic.light,
          onPressed: _check,
        ),
        if (release != null) ...[
          const SizedBox(height: MaeveSpace.s24),
          _ReleaseCard(
            release: release,
            state: _state,
            busy: _busy,
            onDownload: _download,
            onCancel: _cancel,
            onInstall: _install,
          ),
        ],
        const SizedBox(height: MaeveSpace.s24),
        _AccelSection(state: _state, bridge: _bridge, onChanged: _load),
        const SizedBox(height: MaeveSpace.s24),
        SettingsSection(
          title: '其他入口',
          rows: [
            SettingsTile(
              icon: Icons.open_in_new_rounded,
              title: '在浏览器打开',
              subtitle: 'GitHub 发布页与全部历史版本',
              onTap: () => openHostLink(context, 'updates', label: '更新'),
            ),
          ],
          footnote: '检查更新只联系 GitHub，不经过任何第三方服务器。下载的安装包会先核对大小、摘要与签名，再交给系统安装器确认。',
        ),
      ],
    );
  }
}

/// 当前版本 + 这次检查的结论。结论只在真的查过之后才说,没查过不冒充"已是最新"。
class _VersionCard extends StatelessWidget {
  const _VersionCard({required this.state, required this.loaded});

  final AppUpdate state;
  final bool loaded;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    // running 色调带呼吸(开口环 + 呼吸),只给**真的在动**的那一件事:正在检查。
    // 「有新版本」是个不变的事实,用就绪态的实心核 + 强调色洗层,不呼吸 ——
    // 让一个静态结论一直喘气既是语义错误,也让页面永远停不下来。
    final (label, tone) = switch (state.check) {
      UpdateCheck.checking => ('正在检查', MaeveTone.running),
      _ when state.hasUpdate => ('有新版本', MaeveTone.ready),
      // 没查到也分两种:仓库上没有发布,和这次没连上——说法不一样,用户才知道该干嘛
      UpdateCheck.failed when state.problem == UpdateProblem.noRelease =>
        ('尚未发布', MaeveTone.idle),
      UpdateCheck.failed => ('连不上 GitHub', MaeveTone.warning),
      UpdateCheck.done => ('已是最新', MaeveTone.ready),
      _ => (loaded ? '尚未检查' : '读取中', MaeveTone.idle),
    };
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      shape: MaeveShapes.card,
      // 有新版时淡淡洗一层强调色:颜色只是辅助,结论仍由 chip 的文字与形状说。
      tint: state.hasUpdate ? c.accent : null,
      tintAlpha: state.hasUpdate ? .10 : null,
      padding: const EdgeInsets.all(MaeveSpace.s20),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '当前版本',
                  style: MaeveType.eyebrow.copyWith(color: c.ink3),
                ),
                const SizedBox(height: MaeveSpace.s6),
                Text(
                  state.currentVersion.isEmpty ? 'Maeve' : state.currentVersion,
                  style: MaeveType.title.copyWith(color: c.ink),
                ),
              ],
            ),
          ),
          const SizedBox(width: MaeveSpace.s8),
          StatusChip(label: label, tone: tone, filled: true),
        ],
      ),
    );
  }
}

/// 查到的那个发布:版本号、发布日期、更新说明,以及下载/安装那一段。
class _ReleaseCard extends StatelessWidget {
  const _ReleaseCard({
    required this.release,
    required this.state,
    required this.busy,
    required this.onDownload,
    required this.onCancel,
    required this.onInstall,
  });

  final AppRelease release;
  final AppUpdate state;
  final bool busy;
  final VoidCallback onDownload, onCancel, onInstall;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final motion = AppMotion.of(context);
    final active = state.downloading;
    // 主按钮的文案与可点性由状态决定:下载中禁用(只能取消),下好了变安装。
    final (label, action, enabled) = switch (state.phase) {
      _ when busy => ('正在处理…', null, false),
      UpdatePhase.ready => ('安装更新', onInstall, true),
      UpdatePhase.downloading => (
        state.percent != null ? '正在下载 ${state.percent}%' : '正在下载…',
        null,
        false,
      ),
      UpdatePhase.paused => ('下载暂停，稍后自动继续…', null, false),
      _ when !release.canDownload => ('这个发布没有可下载的安装包', null, false),
      UpdatePhase.failed => ('重新下载', onDownload, true),
      _ => ('下载更新', onDownload, true),
    };
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      shape: MaeveShapes.card,
      padding: const EdgeInsets.all(MaeveSpace.s20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            state.hasUpdate ? '发现新版本 ${release.version}' : release.name,
            style: MaeveType.title.copyWith(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: c.ink,
            ),
          ),
          if (release.date != null) ...[
            const SizedBox(height: MaeveSpace.s4),
            Text(
              '${release.date} 发布',
              style: MaeveType.caption.copyWith(color: c.ink3),
            ),
          ],
          if (release.notes.isNotEmpty) ...[
            const SizedBox(height: MaeveSpace.s16),
            MaeveMarkdown(data: release.notes),
          ],
          const SizedBox(height: MaeveSpace.s20),
          if (active) ...[
            _DownloadBar(state: state),
            const SizedBox(height: MaeveSpace.s12),
          ],
          SizedBox(
            width: double.infinity,
            child: MaeveGlassTextButton(
              label: label,
              icon: state.ready
                  ? Icons.install_mobile_rounded
                  : Icons.download_rounded,
              prominent: true,
              enabled: enabled,
              haptic: MaeveHaptic.light,
              onPressed: action,
            ),
          ),
          if (state.cancellable) ...[
            const SizedBox(height: MaeveSpace.s8),
            SizedBox(
              width: double.infinity,
              child: MaeveGlassTextButton(
                label: '删除下载',
                icon: Icons.delete_outline_rounded,
                enabled: !busy,
                haptic: MaeveHaptic.medium,
                onPressed: onCancel,
              ),
            ),
          ],
          const SizedBox(height: MaeveSpace.s12),
          AnimatedSize(
            duration: motion.standard,
            curve: MaeveMotion.collapseCurve,
            alignment: Alignment.topCenter,
            child: Text(
              state.ready
                  ? '点「安装更新」后在系统安装页确认。首次安装需要允许 Maeve 安装未知应用。'
                  : release.canDownload
                  ? '下载完成后点「安装更新」，在系统安装页确认。'
                  : '这个发布没有带校验信息的安装包，请用下面的「在浏览器打开」获取。',
              style: MaeveType.hint.copyWith(color: c.ink3, height: 1.6),
            ),
          ),
        ],
      ),
    );
  }
}

/// 下载进度:一条细轨 + 已下/总量。总量未知时只写字节数,不假装有百分比。
class _DownloadBar extends StatelessWidget {
  const _DownloadBar({required this.state});

  final AppUpdate state;

  static String _mb(int bytes) => (bytes / (1024 * 1024)).toStringAsFixed(1);

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final motion = AppMotion.of(context);
    final fraction = state.percent == null
        ? null
        : (state.percent! / 100).clamp(0.0, 1.0);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
          child: SizedBox(
            height: 4,
            child: fraction == null
                ? LinearProgressIndicator(
                    backgroundColor: c.fill,
                    color: c.accent,
                    minHeight: 4,
                  )
                : TweenAnimationBuilder<double>(
                    tween: Tween(begin: 0, end: fraction),
                    duration: motion.standard,
                    curve: MaeveMotion.decelerate,
                    builder: (context, value, _) => Stack(
                      children: [
                        ColoredBox(color: c.fill),
                        FractionallySizedBox(
                          widthFactor: value,
                          child: ColoredBox(color: c.accent),
                        ),
                      ],
                    ),
                  ),
          ),
        ),
        const SizedBox(height: MaeveSpace.s6),
        Text(
          state.total > 0
              ? '${_mb(state.bytes)} / ${_mb(state.total)} MB'
              : '已下载 ${_mb(state.bytes)} MB',
          style: MaeveType.monoSmall.copyWith(color: c.ink3),
        ),
      ],
    );
  }
}
/// 「GitHub 加速」:连不上 GitHub 时,换个域名解析方式再试。
///
/// 只影响检查更新与下载安装问 GitHub 的那几个域名;别处(调模型、列模型)照旧走系统 DNS。
/// 自定义 IP 是 hosts 式的定点:从测 IP 的网站拿一个顺的填上;填域名等于没绕开 DNS,不收。
class _AccelSection extends StatefulWidget {
  const _AccelSection({
    required this.state,
    required this.bridge,
    required this.onChanged,
  });

  final AppUpdate state;
  final MaeveUpdateBridge bridge;
  final Future<void> Function() onChanged;

  @override
  State<_AccelSection> createState() => _AccelSectionState();
}

class _AccelSectionState extends State<_AccelSection> {
  late final TextEditingController _ip =
      TextEditingController(text: widget.state.accel.ip);
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _ip.dispose();
    super.dispose();
  }

  static const _labels = <String>[
    '跟随系统',
    '阿里 DoH',
    '腾讯 DoH',
    '自定义 IP',
  ];

  Future<void> _pick(GitHubAccelMode mode) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.bridge.setAccel(mode, ip: _ip.text.trim());
      await widget.onChanged();
    } on PlatformException catch (e) {
      if (mounted) setState(() => _error = e.message ?? '保存失败，请重试');
    } catch (_) {
      if (mounted) setState(() => _error = '保存失败，请重试');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final accel = widget.state.accel;
    return SettingsSection(
      title: 'GitHub 加速',
      rows: [
        SettingsSegmentTile(
          icon: Icons.speed_rounded,
          title: '域名解析',
          subtitle: '连不上 GitHub 时换一种;两处 DoH 都在国内',
          options: _labels,
          value: GitHubAccelMode.values.indexOf(accel.mode),
          enabled: !_busy,
          onChanged: (i) => _pick(GitHubAccelMode.values[i]),
        ),
        if (accel.mode == GitHubAccelMode.customIp)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: TextField(
              key: const ValueKey('github-accel-ip'),
              controller: _ip,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                hintText: '例如 20.205.243.166',
                labelText: 'GitHub IP',
                filled: true,
                fillColor: c.fill,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 14,
                  vertical: 14,
                ),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              onSubmitted: (_) => _pick(GitHubAccelMode.customIp),
            ),
          ),
      ],
      footnote: _error ??
          '只影响检查更新与下载安装;别处(调模型、列模型)照旧走系统 DNS。',
    );
  }
}

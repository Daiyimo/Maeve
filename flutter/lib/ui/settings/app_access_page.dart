import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../common/app_page.dart';
import '../common/haptics.dart';
import '../../widgets/glass_surface.dart';
import 'settings_components.dart';

class AppAccessPage extends StatefulWidget {
  const AppAccessPage({super.key});
  @override
  State<AppAccessPage> createState() => _AppAccessPageState();
}

class _AppAccessPageState extends State<AppAccessPage> {
  static const _channel = MethodChannel('maeve/settings');
  Map<dynamic, dynamic>? _state;
  String? _error;
  bool _busy = false;
  int _requestId = 0;
  String _query = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({
    String action = 'snapshot',
    String? pkg,
    bool? enabled,
  }) async {
    final requestId = ++_requestId;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final next = await _channel.invokeMapMethod('appAccess', {
        'action': action,
        'packageName': pkg,
        'enabled': enabled,
      });
      if (mounted && requestId == _requestId) {
        setState(() => _state = next);
      }
    } catch (_) {
      if (mounted && requestId == _requestId) {
        const message = '应用访问设置未能读取或保存，请重试。现有策略仍然有效。';
        setState(() => _error = message);
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text(message)));
      }
    } finally {
      if (mounted && requestId == _requestId) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final available = _state?['available'] == true;
    final visualFirstDefault = _state?['visualFirstDefault'] == true;
    final apps = (_state?['apps'] as List? ?? const [])
        .whereType<Map>()
        .where(
          (app) => '${app['label']} ${app['packageName']}'
              .toLowerCase()
              .contains(_query.toLowerCase()),
        )
        .toList();
    return AppPage(
      title: '应用访问',
      subtitle: '控制任务可以读取、操作或分享给哪些应用',
      child: ListView.builder(
        padding: const EdgeInsets.fromLTRB(16, 100, 16, 32),
        itemCount: apps.length + 1,
        itemBuilder: (context, index) {
          if (index == 0) {
            return Column(
              children: [
                const SettingsNotice(
                  text: '禁用后，正在运行的任务也会在下一次操作时重新检查。允许作为分享目标只开放分享，不开放读取与操作。分类不会改变权限。视觉模式从下一次读屏或操作开始生效。',
                  icon: Icons.verified_user_outlined,
                ),
                SettingsSection(
                  title: '读屏方式',
                  topSpacing: 24,
                  rows: [
                    SettingsSwitchTile(
                      key: const ValueKey('visual-first-default'),
                      icon: Icons.visibility_outlined,
                      title: '全局默认视觉优先',
                      subtitle: '已识别应用默认使用截图和本机 OCR；有可用视觉模型时也会使用，不读取无障碍树',
                      value: visualFirstDefault,
                      onChanged: available && !_busy
                          ? (value) => _load(
                              action: 'setVisualFirstDefault',
                              enabled: value,
                            )
                          : null,
                    ),
                  ],
                  footnote: '单应用设置优先于全局默认。StepX 与 Amoo 成长计划预置为 UI 树模式，可单独改回视觉优先。无法识别包名时保持树读屏；视觉优先不会在失败时自动回退界面树。',
                ),
                if (_error != null)
                  SettingsNotice(text: _error!, icon: Icons.info_outline),
                if (_state != null && !available)
                  const SettingsNotice(
                    text: '应用访问策略不可用，任务将保持受限。请先重试读取；不会自动恢复为全部允许。',
                    icon: Icons.lock_outline,
                  ),
                if (_busy) const LinearProgressIndicator(),
                TextField(
                  key: const ValueKey('app-access-search'),
                  decoration: InputDecoration(
                    hintText: '搜索应用或包名',
                    prefixIcon: const Icon(Icons.search_rounded),
                    filled: true,
                    fillColor: Theme.of(context)
                        .colorScheme
                        .surfaceContainerHighest,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 14,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: Theme.of(context).colorScheme.outlineVariant,
                      ),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(
                        color: Theme.of(context).colorScheme.primary,
                        width: 1.5,
                      ),
                    ),
                  ),
                  onChanged: (value) => setState(() => _query = value),
                ),
                MaeveGlassTextButton(
                  label: '重新读取',
                  compact: true,
                  enabled: !_busy,
                  haptic: MaeveHaptic.tick,
                  onPressed: _busy ? null : () => _load(action: 'reload'),
                ),
              ],
            );
          }
          final app = apps[index - 1], pkg = app['packageName'] as String;
          final blocked = app['blocked'] == true;
          final visualOverride = app['visualFirstOverride'] as bool?;
          final visualFirst = app['visualFirst'] == true;
          final editable = available && !_busy && app['protected'] != true;
          return SettingsSection(
            title: app['label'] as String? ?? pkg,
            rows: [
              SettingsSwitchTile(
                icon: Icons.apps_outlined,
                title: '允许读取与操作',
                value: !blocked,
                onChanged: editable
                    ? (value) =>
                          _load(action: 'setBlocked', pkg: pkg, enabled: !value)
                    : null,
              ),
              if (blocked)
                SettingsSwitchTile(
                  icon: Icons.share_outlined,
                  title: '仅允许作为分享目标',
                  value: app['shareAllowed'] == true,
                  onChanged: editable
                      ? (value) => _load(
                          action: 'setShareAllowed',
                          pkg: pkg,
                          enabled: value,
                        )
                      : null,
                ),
              SettingsSwitchTile(
                key: ValueKey('visual-first-$pkg'),
                icon: Icons.visibility_outlined,
                title: '视觉优先',
                subtitle: visualOverride == null
                    ? '跟随全局默认'
                    : (visualOverride ? '已单独开启' : '已单独关闭'),
                value: visualFirst,
                onChanged: editable
                    ? (value) => _load(
                        action: 'setVisualFirstOverride',
                        pkg: pkg,
                        enabled: value,
                      )
                    : null,
              ),
              if (visualOverride != null && editable)
                SettingsRow(
                  title: '恢复跟随全局默认',
                  onTap: () =>
                      _load(action: 'clearVisualFirstOverride', pkg: pkg),
                ),
            ],
            footnote:
                '$pkg · ${app['policySource']} · 视觉优先：${visualOverride == null ? '跟随全局' : '单应用覆盖'}${app['protected'] == true ? ' · 自身应用受核心保护' : ''}',
          );
        },
      ),
    );
  }
}

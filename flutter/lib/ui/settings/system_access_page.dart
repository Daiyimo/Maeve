import 'dart:async';

import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../common/app_page.dart';
import 'settings_components.dart';
import 'execution_settings_page.dart';
import 'capsule_toggle.dart';

class SystemAccessPage extends StatefulWidget {
  const SystemAccessPage({super.key});
  @override
  State<SystemAccessPage> createState() => _SystemAccessPageState();
}

class _SystemAccessPageState extends State<SystemAccessPage>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted) {
      unawaited(MaeveScope.read(context).refreshSettings());
    }
  }

  Future<void> _open(String target) async {
    try {
      await MaeveScope.read(context).settingsBridge.openAccess(target);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('未能打开系统设置，请重试')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context), state = controller.native;
    return AppPage.list(
      title: '系统授权',
      subtitle: '权限由 Android 管理，状态以设备为准',
      actions: [
        IconButton(
          tooltip: '刷新设备状态',
          onPressed: controller.refreshSettings,
          icon: const Icon(Icons.refresh_rounded, size: 20),
        ),
      ],
      children: [
        if (controller.settingsError != null) ...[
          const SizedBox(height: 20),
          SettingsNotice(
            text: controller.settingsError!,
            icon: Icons.info_outline,
          ),
        ],
        if (controller.settingsLoaded &&
            state.accessibility &&
            !state.accessibilityRunning)
          const SettingsNotice(
            text: '无障碍已启用，但服务尚未连接。可在系统无障碍设置中关闭后重新开启 Maeve，再返回刷新。',
            icon: Icons.info_outline,
          ),
        SettingsSection(
          title: '设备权限',
          rows: [
            SettingsTile(
              icon: Icons.accessibility_new_rounded,
              title: '无障碍服务',
              subtitle: '读取界面并执行操作',
              value: controller.settingsError != null
                  ? '暂不可读'
                  : controller.settingsLoaded
                  ? (state.accessibilityRunning
                        ? '已连接'
                        : state.accessibility
                        ? '等待连接'
                        : '未开启')
                  : '读取中',
              onTap: () => _open('accessibility'),
            ),
            SettingsTile(
              icon: Icons.picture_in_picture_alt_outlined,
              title: '悬浮窗',
              subtitle: '显示任务进度与确认卡',
              value: controller.settingsError != null
                  ? '暂不可读'
                  : controller.settingsLoaded
                  ? (state.overlay ? '已允许' : '未允许')
                  : '读取中',
              onTap: () => _open('overlay'),
            ),
            SettingsTile(
              icon: Icons.notifications_outlined,
              title: '通知',
              subtitle: '任务结果、确认与配对通知',
              value: controller.settingsError != null
                  ? '暂不可读'
                  : controller.settingsLoaded
                  ? (state.notifications ? '已允许' : '未允许')
                  : '读取中',
              onTap: () => _open('notifications'),
            ),
          ],
        ),
        SettingsSection(
          title: '手机助手',
          rows: [
            SettingsTile(
              icon: Icons.phonelink_ring_outlined,
              title: '后台操作连接',
              value: controller.settingsError != null
                  ? '暂不可读'
                  : controller.settingsLoaded
                  ? (state.helperRunning ? '运行中' : '未就绪')
                  : '读取中',
              enabled: true,
            ),
            if (state.phoneSupported)
              SettingsTile(
                icon: Icons.link_rounded,
                title: '通过无线调试配对',
                subtitle: state.notifications
                    ? '打开开发者选项，再进入无线调试'
                    : '先允许通知，以便在系统配对页输入配对码',
                onTap: () =>
                    _open(state.notifications ? 'pairing' : 'notifications'),
              ),
          ],
          footnote: !controller.settingsLoaded
              ? '请刷新以读取手机助手状态。'
              : state.phoneSupported
              ? '打开开发者选项后，开启“无线调试”，再选择“使用配对码配对设备”。无障碍服务运行时可自动读码，也可以从 Maeve 的配对通知输入。请先允许通知；未启用开发者选项时，先到“关于手机”连续点击版本号。'
              : '手机操作需要 Android 12 或更新版本。',
        ),
        SettingsSection(
          title: '其他设置',
          rows: [
            const CapsuleToggle(),
            SettingsTile(
              icon: Icons.verified_user_outlined,
              title: '操作确认方式',
              value: state.autonomous ? '自主' : '询问',
              onTap: () => AppPage.open(context, const ExecutionSettingsPage()),
            ),
            SettingsTile(
              icon: Icons.settings_outlined,
              title: 'Android 应用设置',
              onTap: () => _open('app'),
            ),
          ],
        ),
      ],
    );
  }
}

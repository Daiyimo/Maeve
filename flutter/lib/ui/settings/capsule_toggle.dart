import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import 'settings_components.dart';

/// The actual native overlay preference, shared by the settings entry points.
class CapsuleToggle extends StatefulWidget {
  const CapsuleToggle({super.key});
  @override
  State<CapsuleToggle> createState() => _CapsuleToggleState();
}

class _CapsuleToggleState extends State<CapsuleToggle> {
  bool _saving = false;
  Future<void> _set(bool enabled) async {
    setState(() => _saving = true);
    try {
      await MaeveScope.read(context).setCapsuleEnabled(enabled);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('悬浮胶囊开关未能保存，请重试。')));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return SettingsSwitchTile(
      key: const ValueKey('capsule-toggle'),
      icon: Icons.picture_in_picture_alt_outlined,
      title: '悬浮胶囊',
      subtitle: !controller.settingsLoaded
          ? '正在读取设备设置…'
          : controller.native.capsuleEnabled &&
                !controller.native.accessibilityRunning
          ? '已开启，无障碍服务连接后显示'
          : '在其他应用中显示；关闭不停止任务',
      value: controller.native.capsuleEnabled,
      enabled: controller.settingsLoaded && !_saving,
      onChanged: _set,
    );
  }
}

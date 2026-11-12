import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../common/app_page.dart';
import 'settings_components.dart';

class ExecutionSettingsPage extends StatefulWidget {
  const ExecutionSettingsPage({super.key});
  @override
  State<ExecutionSettingsPage> createState() => _ExecutionSettingsPageState();
}

class _ExecutionSettingsPageState extends State<ExecutionSettingsPage> {
  bool _saving = false;
  String? _error;
  Future<void> _select(bool enabled) async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await MaeveScope.read(context).setAutonomous(enabled);
    } catch (_) {
      if (mounted) setState(() => _error = '确认方式未能保存，请重试。');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return AppPage.list(
      title: '操作确认方式',
      subtitle: '控制手机任务如何请求你的同意',
      children: [
        const SizedBox(height: 20),
        if (_error != null)
          SettingsNotice(text: _error!, icon: Icons.info_outline),
        SettingsSection(
          title: '本机任务',
          rows: [
            SettingsTile(
              icon: Icons.touch_app_outlined,
              title: '询问模式',
              subtitle: '敏感操作、付款及特定应用的写入需要确认',
              enabled: controller.settingsLoaded && !_saving,
              trailing: Icon(
                !controller.native.autonomous
                    ? Icons.radio_button_checked
                    : Icons.radio_button_off,
              ),
              onTap: () => _select(false),
            ),
            SettingsTile(
              icon: Icons.play_circle_outline_rounded,
              title: '自主执行',
              subtitle: '连续执行手机操作，不再逐次确认；付款也不先询问',
              enabled: controller.settingsLoaded && !_saving,
              trailing: Icon(
                controller.native.autonomous
                    ? Icons.radio_button_checked
                    : Icons.radio_button_off,
              ),
              onTap: () => _select(true),
            ),
          ],
          footnote: '设置保存在本机。开启自主从后续步骤生效，当前待确认事项仍需按卡片处理。关闭自主后，新任务恢复询问；已按自主开始的任务，请停止后重新发起。',
        ),
        const FooterNote(
          text: '核心对自身应用、受保护应用和密码／验证码页面的限制仍然生效。电脑经 MCP 发起的任务沿用原生的独立授权规则。',
        ),
      ],
    );
  }
}

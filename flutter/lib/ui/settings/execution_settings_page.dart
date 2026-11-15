import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../common/app_page.dart';
import '../common/selection_field.dart';
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
    if (enabled && !MaeveScope.read(context).native.autonomous) {
      final accepted = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('开启自主执行'),
          content: const Text(
            'Maeve 将连续执行可恢复的普通操作，可能修改应用中的内容。发送、提交、删除、付款仍需单次确认；远程任务不能代替你授权。你可以随时停止任务或关闭此开关。',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('了解并开启'),
            ),
          ],
        ),
      );
      if (accepted != true || !mounted) return;
    }
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

  Future<void> _pace(String pace) async {
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await MaeveScope.read(context).setOperatingPace(pace);
    } catch (_) {
      if (mounted) setState(() => _error = '操作节奏未能保存，请重试。');
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
              subtitle: '连续执行普通操作；发送、提交、删除及付款仍需确认',
              enabled: controller.settingsLoaded && !_saving,
              trailing: Icon(
                controller.native.autonomous
                    ? Icons.radio_button_checked
                    : Icons.radio_button_off,
              ),
              onTap: () => _select(true),
            ),
          ],
          footnote: '设置保存在本机，切换从下一动作边界生效，并撤销尚未使用的单次授权。已经发出的动作不会因停止或关闭开关而撤回。',
        ),
        SettingsSection(
          title: '任务预算',
          rows: [
            MaeveSelectField<int>(
              initialValue: controller.native.taskStepLimit,
              decoration: const InputDecoration(labelText: '每个任务最多执行步数'),
              items:
                  ({
                        20,
                        40,
                        60,
                        100,
                        200,
                        500,
                        controller.native.taskStepLimit,
                      }.toList()..sort())
                      .map(
                        (value) => DropdownMenuItem(
                          value: value,
                          child: Text('$value 步'),
                        ),
                      )
                      .toList(),
              onChanged: _saving
                  ? null
                  : (value) async {
                      if (value == null) return;
                      setState(() {
                        _saving = true;
                        _error = null;
                      });
                      try {
                        await controller.setTaskStepLimit(value);
                      } catch (_) {
                        if (mounted) setState(() => _error = '任务预算未能保存，请重试。');
                      } finally {
                        if (mounted) setState(() => _saving = false);
                      }
                    },
            ),
          ],
          footnote: '下次任务生效。父任务与子任务共享步数、10 分钟主动执行时间和 120 次模型调用上限；等待你确认或修复权限时暂停主动计时。实际费用以服务商账单为准。',
        ),
        SettingsSection(
          title: '操作节奏',
          rows: [
            for (final option in const [
              ('fast', '快速', '取消额外页面停留，文字一次写入；仍等待界面反馈'),
              ('standard', '标准', '短暂停留；部分应用按既有规则放慢浏览'),
              ('human', '拟人', '更长的阅读停留与分段输入，适合需要从容操作时'),
            ])
              SettingsTile(
                icon: Icons.speed_rounded,
                title: option.$2,
                subtitle: option.$3,
                enabled: controller.settingsLoaded && !_saving,
                trailing: Icon(
                  controller.native.operatingPace == option.$1
                      ? Icons.radio_button_checked
                      : Icons.radio_button_off,
                ),
                onTap: () => _pace(option.$1),
              ),
          ],
          footnote: '下一次任务生效。操作节奏与确认方式独立；发送、提交等对外动作的间隔仍由核心控制。',
        ),
        const FooterNote(
          text: '核心对自身应用、受保护应用和密码／验证码页面的限制仍然生效。电脑经 MCP 发起的任务沿用原生的独立授权规则。',
        ),
      ],
    );
  }
}

import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import '../common/haptics.dart';
import '../common/status.dart';
import '../../widgets/glass_surface.dart';
import 'settings_components.dart';
import 'add_model_page.dart';
import 'model_usage_panel.dart';
export 'add_model_page.dart';

class ModelSettingsPage extends StatelessWidget {
  const ModelSettingsPage({super.key});
  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final c = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '模型配置',
      subtitle: '使用你自己的模型服务',
      children: [
        const SizedBox(height: 20),
        if (controller.settingsError != null)
          SettingsNotice(
            text: controller.settingsError!,
            icon: Icons.info_outline,
          ),
        if (controller.native.providerSnapshotAvailable &&
            const [
              'corrupt',
              'unavailable',
            ].contains(controller.native.providerState))
          MaeveGlassTextButton(
            icon: Icons.restore,
            label: '恢复最近完整模型配置',
            haptic: MaeveHaptic.tick,
            onPressed: () async {
              final confirmed = await showDialog<bool>(
                context: context,
                builder: (context) => MaeveGlassDialog(
                  title: const Text('恢复模型配置'),
                  content: const Text(
                    '将恢复最近一次完整保存的服务商、模型、角色与加密凭据。损坏文件会保留在本机，不会启动任务或恢复动作授权。',
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context, false),
                      child: const Text('取消'),
                    ),
                    FilledButton(
                      onPressed: () => Navigator.pop(context, true),
                      child: const Text('恢复'),
                    ),
                  ],
                ),
              );
              if (confirmed != true) return;
              try {
                await controller.recoverProviders();
              } catch (_) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('配置恢复失败，原文件已保留。')),
                  );
                }
              }
            },
          ),
        if (controller.native.models.isEmpty)
          const EmptyView(
            icon: Icons.auto_awesome_outlined,
            title: '还没有配置模型',
            body: '接入模型服务后，Maeve 才能思考和执行任务。',
          ),
        if (controller.native.models.isNotEmpty)
          SettingsSection(
            title: '已配置的模型',
            rows: [
              for (final model in controller.native.models)
                SettingsRow(
                  title: model.model,
                  subtitle: model.name.isEmpty
                      ? Uri.tryParse(model.baseUrl)?.host
                      : '${model.name}${model.baseUrl.contains('/step_plan/')
                            ? ' · Plan'
                            : model.baseUrl == 'https://api.stepfun.com/v1'
                            ? ' · API'
                            : ''}',
                  icon: Icons.auto_awesome_outlined,
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (controller.native.currentModelId == model.id)
                        Icon(Icons.check_rounded, color: c.accent, size: 20),
                      MaeveGlassButton(
                        icon: Icons.tune_rounded,
                        tooltip: '编辑模型 ${model.model}',
                        compact: true,
                        haptic: MaeveHaptic.tick,
                        onPressed: () =>
                            AppPage.open(context, AddModelPage(config: model)),
                      ),
                      MaeveGlassButton(
                        icon: Icons.delete_outline,
                        tooltip: '删除模型 ${model.model}',
                        compact: true,
                        haptic: MaeveHaptic.tick,
                        onPressed: () async {
                          final action = await showDialog<String>(
                            context: context,
                            builder: (context) => MaeveGlassDialog(
                              title: Text('删除 ${model.model}'),
                              content: const Text(
                                '删除模型会清理其角色引用，并保留同一服务商其他模型共用的密钥。删除整个服务商会一并删除其所有模型、登录凭据和本地密钥。正在运行的任务可能仍持有旧配置，建议先停止；本地删除不会撤销云端 Key。',
                              ),
                              actions: [
                                TextButton(
                                  onPressed: () => Navigator.pop(context),
                                  child: const Text('取消'),
                                ),
                                TextButton(
                                  onPressed: () =>
                                      Navigator.pop(context, 'provider'),
                                  child: const Text('删除整个服务商'),
                                ),
                                FilledButton(
                                  onPressed: () =>
                                      Navigator.pop(context, 'model'),
                                  child: const Text('仅删除此模型'),
                                ),
                              ],
                            ),
                          );
                          if (action == null) return;
                          try {
                            await controller.deleteModel(
                              model.id,
                              wholeProvider: action == 'provider',
                            );
                          } catch (_) {
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('删除未能保存，请重试。')),
                              );
                            }
                          }
                        },
                      ),
                    ],
                  ),
                  onTap: () async {
                    try {
                      await controller.selectModel(model.id);
                    } catch (_) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('模型切换失败，请重试')),
                        );
                      }
                    }
                  },
                ),
            ],
          ),
        const SizedBox(height: 24),
        MaeveGlassTextButton(
          label: '添加模型接入',
          icon: Icons.add_rounded,
          prominent: true,
          haptic: MaeveHaptic.light,
          onPressed: () => AppPage.open(context, const AddModelPage()),
        ),
        const SizedBox(height: 14),
        Text(
          '模型负责思考，Maeve 负责在设备上行动。密钥由本机安全存储保管。',
          style: MaeveType.caption.copyWith(color: c.ink2, height: 1.6),
        ),
        const SizedBox(height: 24),
        const ModelUsagePanel(),
      ],
    );
  }
}

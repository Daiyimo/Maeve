import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import '../common/status.dart';
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
                      IconButton(
                        tooltip: '编辑模型 ${model.model}',
                        icon: const Icon(Icons.tune_rounded, size: 20),
                        onPressed: () =>
                            AppPage.open(context, AddModelPage(config: model)),
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
        FilledButton.icon(
          onPressed: () => AppPage.open(context, const AddModelPage()),
          icon: const Icon(Icons.add_rounded, size: 18),
          label: const Text('添加模型接入'),
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

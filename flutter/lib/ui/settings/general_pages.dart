import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../common/app_page.dart';
import 'settings_components.dart';

class PerformancePage extends StatelessWidget {
  const PerformancePage({super.key});
  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return AppPage.list(
      title: '透明与动效',
      subtitle: '光与层次，也可以更安静',
      children: [
        SettingsSection(
          title: '透明效果',
          rows: [
            SettingsSwitchTile(
              icon: Icons.blur_on_outlined,
              title: '减少透明效果',
              subtitle: '使用更实的表面，降低背景采样开销',
              value: controller.reduceTransparency,
              onChanged: (_) => controller.toggleReduceTransparency(),
            ),
          ],
        ),
        const FooterNote(text: '界面始终遵循系统的“移除动画”和高对比度设置。减少透明后，层次、边光与文字对比度仍然保留。'),
      ],
    );
  }
}

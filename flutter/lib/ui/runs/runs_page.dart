import 'package:flutter/material.dart';

import '../../design/maeve_tokens.dart';
import '../common/app_page.dart';
import '../common/status.dart';
import '../settings/settings_components.dart';

/// 运行:B11 的独立页。原型阶段是视觉就绪态,没有真实执行历史。
class RunsPage extends StatelessWidget {
  const RunsPage({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '运行',
    subtitle: 'Agent 活动与任务进度',
    children: [
      EmptyView(
        icon: Icons.bolt_outlined,
        title: '可以开始第一次运行',
        body: '还没有记录任何 Agent 活动。让它替你做一件小事试试。',
        action: FilledButton(
          onPressed: () => ScaffoldMessenger.of(context)
            ..hideCurrentSnackBar()
            ..showSnackBar(const SnackBar(content: Text('运行记录将在接入执行层后出现。'))),
          child: const Text('看看能做什么'),
        ),
      ),
      const SizedBox(height: MaeveSpace.xl),
      SettingsSection(
        title: '会记录什么',
        rows: const [
          SettingsTile(
            icon: Icons.route_outlined,
            title: '每一步的动作',
            subtitle: '点按、输入、切换页面',
            value: '记录',
            enabled: false,
          ),
          SettingsTile(
            icon: Icons.screenshot_monitor_outlined,
            title: '屏幕内容',
            subtitle: '只在执行时按需读取，不进记录',
            value: '不留存',
            enabled: false,
          ),
          SettingsTile(
            icon: Icons.timer_outlined,
            title: '耗时',
            subtitle: '按步骤统计，不按秒刷屏',
            value: '统计',
            enabled: false,
          ),
        ],
        footnote: '运行记录目前是视觉原型，尚未接入持久化执行。',
      ),
      const SizedBox(height: MaeveSpace.xxl),
      Center(
        child: Text(
          '执行层由 Android 侧提供',
          style: MaeveFonts.mono(
            context,
            size: 10,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
      ),
    ],
  );
}

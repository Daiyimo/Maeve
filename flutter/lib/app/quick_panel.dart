import 'package:flutter/material.dart';

import '../design/maeve_tokens.dart';
import '../ui/common/app_page.dart';
import '../ui/common/pressable.dart';
import '../ui/memory/memory_page.dart';
import '../ui/more/more_pages.dart';
import '../ui/phone/phone_control_page.dart';
import '../ui/runs/runs_page.dart';
import '../ui/settings/settings_hub.dart';
import '../widgets/glass_surface.dart';
import '../widgets/maeve_mark.dart';
import 'maeve_controller.dart';

/// B7 QuickPanel:右侧快捷面板。和会话抽屉同一套跟手/落位,只是从另一边来。
///
/// 入口是用户自选的(这里给一组默认);⚙ 长按直接进设置搜索。
class QuickPanel extends StatelessWidget {
  const QuickPanel({super.key, required this.onClose});

  final VoidCallback onClose;

  void _open(BuildContext context, Widget page) {
    onClose();
    AppPage.open(context, page);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final controller = MaeveScope.of(context);
    return MaeveGlassSurface(
      tier: MaeveGlassTier.sheet,
      borderRadius: BorderRadius.zero,
      padding: EdgeInsets.zero,
      child: SafeArea(
        left: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.lg,
                MaeveSpace.md,
                MaeveSpace.sm,
                0,
              ),
              child: Row(
                children: [
                  const MaeveMark(size: 24),
                  const SizedBox(width: MaeveSpace.sm),
                  Text('快捷', style: maeveEyebrow(context)),
                  const Spacer(),
                  Pressable(
                    onTap: onClose,
                    borderRadius: BorderRadius.circular(MaeveRadius.pill),
                    semanticLabel: '关闭快捷面板',
                    child: SizedBox.square(
                      dimension: 36,
                      child: Icon(
                        Icons.close,
                        size: 19,
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: MaeveSpace.md),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.fromLTRB(
                  MaeveSpace.lg,
                  0,
                  MaeveSpace.lg,
                  MaeveSpace.lg,
                ),
                children: [
                  _entry(
                    context,
                    icon: Icons.language_rounded,
                    title: '网页版',
                    subtitle: '在浏览器里打开同一条会话',
                    onTap: () => _open(context, const WebViewPage()),
                  ),
                  _entry(
                    context,
                    icon: Icons.dns_outlined,
                    title: '远程主机',
                    subtitle: '把任务交给配对的机器',
                    onTap: () => _soon(context, '远程主机'),
                  ),
                  _entry(
                    context,
                    icon: Icons.extension_outlined,
                    title: '插件',
                    subtitle: '已装 0 个',
                    onTap: () => _soon(context, '插件'),
                  ),
                  _entry(
                    context,
                    icon: Icons.memory_rounded,
                    title: '引擎',
                    subtitle: '${controller.model} · ${controller.modelTier}',
                    onTap: () => _open(context, const SettingsHubPage()),
                  ),
                  _entry(
                    context,
                    icon: Icons.bolt_outlined,
                    title: '运行',
                    subtitle: 'Agent 活动与任务进度',
                    onTap: () => _open(context, const RunsPage()),
                  ),
                  _entry(
                    context,
                    icon: Icons.bookmark_border_rounded,
                    title: '记忆',
                    subtitle: '把有用的上下文留在身边',
                    onTap: () => _open(context, const MemoryPage()),
                  ),
                  _entry(
                    context,
                    icon: Icons.smartphone_rounded,
                    title: '手机操控',
                    subtitle: '让 Maeve 替你在应用里操作',
                    onTap: () => _open(context, const PhoneControlPage()),
                  ),
                  _entry(
                    context,
                    icon: Icons.terminal_rounded,
                    title: '终端',
                    subtitle: '沙箱里的命令行',
                    onTap: () => _open(context, const TerminalPage()),
                  ),
                  _entry(
                    context,
                    icon: Icons.groups_outlined,
                    title: '社区',
                    subtitle: '讨论、更新日志、反馈',
                    onTap: () => _open(context, const CommunityPage()),
                  ),
                ],
              ),
            ),
            Container(
              decoration: BoxDecoration(
                border: Border(
                  top: BorderSide(
                    color: scheme.outline.withValues(alpha: .5),
                    width: .5,
                  ),
                ),
              ),
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.lg,
                MaeveSpace.sm,
                MaeveSpace.lg,
                MaeveSpace.sm,
              ),
              child: Pressable(
                onTap: () => _open(context, const SettingsHubPage()),
                onLongPress: () =>
                    _open(context, const SettingsHubPage(openSearch: true)),
                borderRadius: BorderRadius.circular(MaeveRadius.sm),
                scale: false,
                semanticLabel: '设置，长按搜索设置',
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: MaeveSpace.sm),
                  child: Row(
                    children: [
                      Icon(
                        Icons.settings_outlined,
                        size: 18,
                        color: scheme.onSurfaceVariant,
                      ),
                      const SizedBox(width: MaeveSpace.sm),
                      Text('设置', style: Theme.of(context).textTheme.bodyLarge),
                      const SizedBox(width: 4),
                      Text(
                        '长按搜索',
                        style: Theme.of(context).textTheme.labelSmall,
                      ),
                      const Spacer(),
                      Icon(
                        Icons.chevron_right,
                        size: 18,
                        color: scheme.onSurfaceVariant,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _entry(
    BuildContext context, {
    required IconData icon,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 2),
      child: Pressable(
        onTap: onTap,
        scale: false,
        borderRadius: BorderRadius.circular(MaeveRadius.sm),
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: MaeveSpace.sm,
            vertical: MaeveSpace.sm + 2,
          ),
          child: Row(
            children: [
              Container(
                width: 30,
                height: 30,
                decoration: BoxDecoration(
                  color: scheme.primary.withValues(alpha: .12),
                  borderRadius: BorderRadius.circular(MaeveRadius.sm),
                ),
                child: Icon(icon, size: 16, color: scheme.primary),
              ),
              const SizedBox(width: MaeveSpace.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: Theme.of(context).textTheme.bodyLarge),
                    Text(
                      subtitle,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              Icon(
                Icons.chevron_right,
                size: 18,
                color: scheme.onSurfaceVariant,
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _soon(BuildContext context, String what) {
    onClose();
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text('$what 将通过 Android 平台桥接接入。')));
  }
}

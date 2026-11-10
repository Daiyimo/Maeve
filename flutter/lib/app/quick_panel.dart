import 'package:flutter/material.dart';

import '../widgets/maeve_mark.dart';

import 'maeve_controller.dart';

import '../design/maeve_shapes.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

import '../ui/common/app_page.dart';
import '../ui/common/pressable.dart';
import '../ui/memory/memory_page.dart';
import '../ui/more/more_pages.dart';
import '../ui/phone/phone_control_page.dart';
import '../ui/runs/runs_page.dart';
import '../ui/settings/settings_components.dart';
import '../ui/settings/settings_hub.dart';
import '../widgets/glass_surface.dart';

/// 快捷面板:右侧拉出。入口是**图标网格**,不是长列表 —— 一屏能看全。
///
/// 这是窄屏上唯一可靠的快捷入口:顶栏右侧那个网格键。
/// 边缘手势在系统手势导航下会被系统吃掉,不能当唯一入口。
class QuickPanel extends StatelessWidget {
  const QuickPanel({super.key, required this.onClose});

  final VoidCallback onClose;

  void _open(BuildContext context, Widget page) {
    onClose();
    AppPage.open(context, page);
  }

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final controller = MaeveScope.of(context);
    final entries = <({IconData icon, String title, String note, Widget page})>[
      (
        icon: Icons.language_rounded,
        title: '网页版',
        note: '浏览器里继续',
        page: const WebViewPage(),
      ),
      (
        icon: Icons.dns_outlined,
        title: '远程主机',
        note: '交给配对的机器',
        page: const SettingsHubPage(),
      ),
      (
        icon: Icons.extension_outlined,
        title: '插件',
        note: '0 个',
        page: const SettingsHubPage(),
      ),
      (
        icon: Icons.memory_rounded,
        title: '引擎',
        note: '${controller.model} · ${controller.modelTier}',
        page: const SettingsHubPage(),
      ),
      (
        icon: Icons.bolt_outlined,
        title: '运行',
        note: '活动与进度',
        page: const RunsPage(),
      ),
      (
        icon: Icons.bookmark_border_rounded,
        title: '记忆',
        note: '留下的上下文',
        page: const MemoryPage(),
      ),
      (
        icon: Icons.smartphone_rounded,
        title: '手机操控',
        note: '替你在应用里点',
        page: const PhoneControlPage(),
      ),
      (
        icon: Icons.terminal_rounded,
        title: '终端',
        note: '沙箱命令行',
        page: const TerminalPage(),
      ),
      (
        icon: Icons.groups_outlined,
        title: '社区',
        note: '讨论与更新',
        page: const CommunityPage(),
      ),
    ];

    return MaeveGlassSurface(
      tier: MaeveGlassTier.sheet,
      shape: const RoundedRectangleBorder(),
      padding: EdgeInsets.zero,
      child: SafeArea(
        left: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.s16,
                MaeveSpace.s12,
                MaeveSpace.s8,
                0,
              ),
              child: Row(
                children: [
                  const MaeveMark(size: 22),
                  const SizedBox(width: MaeveSpace.s8),
                  Text(
                    'QUICK',
                    style: MaeveType.eyebrow.copyWith(color: colors.ink3),
                  ),
                  const Spacer(),
                  Pressable(
                    onTap: onClose,
                    borderRadius: BorderRadius.circular(MaeveRadius.pill),
                    semanticLabel: '关闭快捷面板',
                    child: SizedBox.square(
                      dimension: 40,
                      child: Icon(Icons.close, size: 19, color: colors.ink3),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: MaeveSpace.s12),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  MaeveSpace.s16,
                  0,
                  MaeveSpace.s16,
                  MaeveSpace.s16,
                ),
                child: LayoutBuilder(
                  builder: (context, box) {
                    const gap = MaeveSpace.s8;
                    final columns = box.maxWidth >= 300 ? 2 : 1;
                    final width =
                        (box.maxWidth - gap * (columns - 1)) / columns;
                    return Wrap(
                      spacing: gap,
                      runSpacing: gap,
                      children: [
                        for (final entry in entries)
                          SizedBox(
                            width: width,
                            child: FeatureTile(
                              icon: entry.icon,
                              title: entry.title,
                              subtitle: entry.note,
                              onTap: () => _open(context, entry.page),
                            ),
                          ),
                      ],
                    );
                  },
                ),
              ),
            ),
            Container(
              decoration: BoxDecoration(
                border: Border(top: BorderSide(color: colors.hair, width: .5)),
              ),
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.s16,
                MaeveSpace.s6,
                MaeveSpace.s16,
                MaeveSpace.s6,
              ),
              child: Pressable(
                onTap: () => _open(context, const SettingsHubPage()),
                onLongPress: () =>
                    _open(context, const SettingsHubPage(openSearch: true)),
                borderRadius: BorderRadius.circular(MaeveRadius.sm),
                scale: false,
                semanticLabel: '设置，长按搜索设置',
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: MaeveSpace.s10),
                  child: Row(
                    children: [
                      Icon(
                        Icons.settings_outlined,
                        size: 18,
                        color: colors.ink2,
                      ),
                      const SizedBox(width: MaeveSpace.s8),
                      Text(
                        '设置',
                        style: MaeveType.body.copyWith(color: colors.ink),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        '长按搜索',
                        style: MaeveType.hint.copyWith(color: colors.ink3),
                      ),
                      const Spacer(),
                      RowChevron(),
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
}

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
/// 网格按"你要干什么"分成三段(连接 / 运行 / 关于),每段一个分组标题。
/// 上一版是九个同款方块铺满一屏:每个都长得一样,于是没有一个记得住、也找不到。
/// 分组之后,扫读路径变成"先找段,再找块"。
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
    // 分组顺序固定;组内顺序按使用频率,不按模块。
    final groups = <({String label, List<_PanelEntry> items})>[
      (
        label: '连接',
        items: [
          _PanelEntry(
            icon: Icons.language_rounded,
            title: '网页版',
            note: '浏览器里继续',
            page: const WebViewPage(),
          ),
          _PanelEntry(
            icon: Icons.dns_outlined,
            title: '远程主机',
            note: '交给配对的机器',
            page: const SettingsHubPage(),
          ),
          _PanelEntry(
            icon: Icons.memory_rounded,
            title: '引擎',
            note: '${controller.model} · ${controller.modelTier}',
            page: const SettingsHubPage(),
          ),
          _PanelEntry(
            icon: Icons.extension_outlined,
            title: '插件',
            note: '0 个',
            page: const SettingsHubPage(),
          ),
        ],
      ),
      (
        label: '运行',
        items: [
          _PanelEntry(
            icon: Icons.bolt_outlined,
            title: '运行',
            note: '活动与进度',
            page: const RunsPage(),
          ),
          _PanelEntry(
            icon: Icons.bookmark_border_rounded,
            title: '记忆',
            note: '留下的上下文',
            page: const MemoryPage(),
          ),
          _PanelEntry(
            icon: Icons.terminal_rounded,
            title: '终端',
            note: '沙箱命令行',
            page: const TerminalPage(),
          ),
        ],
      ),
      (
        label: '关于',
        items: [
          _PanelEntry(
            icon: Icons.groups_outlined,
            title: '社区',
            note: '讨论与更新',
            page: const CommunityPage(),
          ),
        ],
      ),
    ];

    return MaeveGlassSurface(
      tier: MaeveGlassTier.sheet,
      shape: const RoundedRectangleBorder(),
      framed: false,
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
                  MaeveMark(size: 20, color: colors.accent),
                  const SizedBox(width: MaeveSpace.s8),
                  Text(
                    '工作台',
                    style: MaeveType.largeTitle.copyWith(color: colors.ink),
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
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(
                  MaeveSpace.s16,
                  MaeveSpace.s4,
                  MaeveSpace.s16,
                  MaeveSpace.s16,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const SizedBox(height: 16),
                    Pressable(
                      onTap: () => _open(context, const PhoneControlPage()),
                      borderRadius: BorderRadius.circular(22),
                      scale: false,
                      child: MaeveGlassSurface(
                        // The surrounding sheet already samples the backdrop.
                        // Nested sampling in a scrolling card can feed back on GPUs.
                        tier: MaeveGlassTier.card,
                        shape: RoundedSuperellipseBorder(
                          borderRadius: BorderRadius.circular(24),
                        ),
                        tint: colors.accent,
                        tintAlpha: .12,
                        padding: const EdgeInsets.all(20),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Icon(
                                  Icons.phonelink_ring_outlined,
                                  size: 30,
                                  color: colors.accent,
                                ),
                                const Spacer(),
                                Icon(
                                  Icons.north_east_rounded,
                                  color: colors.accent,
                                  size: 20,
                                ),
                              ],
                            ),
                            const SizedBox(height: 20),
                            Text(
                              '手机操控',
                              style: MaeveType.title.copyWith(
                                color: colors.accent,
                                fontSize: 21,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              '一块屏幕留给你，另一块交给我。',
                              style: MaeveType.hint.copyWith(
                                color: colors.accent,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    for (final group in groups) ...[
                      Padding(
                        padding: const EdgeInsets.only(
                          top: MaeveSpace.s16,
                          bottom: MaeveSpace.s8,
                          left: 2,
                        ),
                        child: Text(
                          group.label,
                          style: MaeveType.label.copyWith(color: colors.ink3),
                        ),
                      ),
                      LayoutBuilder(
                        builder: (context, box) {
                          const gap = MaeveSpace.s8;
                          // 369dp 机型:面板 313 - 32 = 281dp。阈值压到 264,两张
                          // 136dp 的卡仍然放得下四字标题,不会退化成一列。
                          final columns = box.maxWidth >= 264 ? 2 : 1;
                          final width =
                              (box.maxWidth - gap * (columns - 1)) / columns;
                          return Wrap(
                            spacing: gap,
                            runSpacing: gap,
                            children: [
                              for (final entry in group.items)
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
                    ],
                  ],
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

/// 快捷面板里的一个入口(不导出的内部数据结构)。
class _PanelEntry {
  const _PanelEntry({
    required this.icon,
    required this.title,
    required this.note,
    required this.page,
  });

  final IconData icon;
  final String title;
  final String note;
  final Widget page;
}

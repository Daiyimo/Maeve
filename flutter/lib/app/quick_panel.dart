import 'package:flutter/material.dart';

import '../design/maeve_shapes.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../widgets/glass_surface.dart';
import '../widgets/maeve_mark.dart';
import '../ui/common/app_page.dart';
import '../ui/common/pressable.dart';
import '../ui/settings/model_settings_page.dart';
import '../ui/settings/system_access_page.dart';
import '../ui/settings/appearance_page.dart';
import '../ui/settings/settings_hub.dart';
import '../ui/settings/about_pages.dart';
import '../ui/settings/settings_components.dart';

class QuickPanel extends StatelessWidget {
  const QuickPanel({super.key, required this.onClose});
  final VoidCallback onClose;
  void _open(BuildContext context, Widget page) {
    onClose();
    AppPage.open(context, page);
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final entries = [
      (
        Icons.auto_awesome_outlined,
        '模型配置',
        '选择实际使用的模型',
        const ModelSettingsPage(),
      ),
      (
        Icons.phonelink_ring_outlined,
        '系统授权',
        '权限与助手连接',
        const SystemAccessPage(),
      ),
      (Icons.palette_outlined, '主题与配色', '调节此刻的氛围', const AppearancePage()),
      (Icons.info_outline_rounded, '关于 Maeve', '名字背后的故事', const AboutPage()),
    ];
    return MaeveGlassSurface(
      tier: MaeveGlassTier.sheet,
      shape: const RoundedRectangleBorder(),
      framed: false,
      child: SafeArea(
        left: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 16, 8, 16),
              child: Row(
                children: [
                  const MaeveMark(size: 25),
                  const SizedBox(width: 10),
                  Text(
                    '工作台',
                    style: MaeveType.largeTitle.copyWith(
                      color: c.ink,
                      fontSize: 27,
                    ),
                  ),
                  const Spacer(),
                  IconButton(
                    onPressed: onClose,
                    tooltip: '关闭快捷面板',
                    icon: Icon(Icons.close_rounded, size: 20, color: c.ink2),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(
                  horizontal: 18,
                  vertical: 12,
                ),
                children: [
                  Text(
                    '留给真正要用的事',
                    style: MaeveType.caption.copyWith(color: c.ink2),
                  ),
                  const SizedBox(height: 22),
                  for (final entry in entries) ...[
                    Pressable(
                      onTap: () => _open(context, entry.$4),
                      scale: false,
                      child: MaeveGlassSurface(
                        shape: MaeveShapes.card,
                        padding: const EdgeInsets.all(18),
                        child: Row(
                          children: [
                            IconTile(icon: entry.$1),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    entry.$2,
                                    style: MaeveType.bodyStrong.copyWith(
                                      color: c.ink,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    entry.$3,
                                    style: MaeveType.hint.copyWith(
                                      color: c.ink3,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Icon(
                              Icons.north_east_rounded,
                              color: c.ink3,
                              size: 17,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 14),
                  ],
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(18),
              child: TextButton.icon(
                onPressed: () => _open(context, const SettingsHubPage()),
                icon: const Icon(Icons.settings_outlined, size: 18),
                label: const Text('全部设置'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

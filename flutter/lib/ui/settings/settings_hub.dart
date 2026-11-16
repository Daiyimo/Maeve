import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_surfaces.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import 'settings_components.dart';
import 'appearance_page.dart';
import 'model_settings_page.dart';
import 'system_access_page.dart';
import 'amoo_page.dart';
import 'about_pages.dart';
import 'palette_strip.dart';
import 'execution_settings_page.dart';
import 'app_access_page.dart';
import 'run_history_page.dart';
import 'welcome_page.dart';

class SettingsHubPage extends StatelessWidget {
  const SettingsHubPage({super.key});
  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context),
        c = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '设置',
      subtitle: '让 Maeve 适合你的日常',
      children: [
        if (controller.settingsError != null) ...[
          SettingsNotice(
            text: controller.settingsError!,
            icon: Icons.info_outline,
          ),
          TextButton(
            onPressed: controller.refreshSettings,
            child: const Text('重新读取设备设置'),
          ),
        ],
        SettingsSection(
          title: '执行与设备',
          topSpacing: 12,
          rows: [
            SettingsTile(
              icon: Icons.receipt_long_outlined,
              title: '执行记录',
              subtitle: '实际步骤、截图与点击位置',
              onTap: () => AppPage.open(
                context,
                RunHistoryPage(bridge: controller.chatBridge),
              ),
            ),
            SettingsTile(
              icon: Icons.verified_user_outlined,
              title: '操作确认方式',
              subtitle: '手机任务的询问与自主执行',
              value: controller.native.autonomous ? '自主' : '询问',
              onTap: () => AppPage.open(context, const ExecutionSettingsPage()),
            ),
            SettingsTile(
              icon: Icons.auto_awesome_outlined,
              title: '模型配置',
              subtitle: '接入你自己的模型服务',
              value: controller.model,
              onTap: () => AppPage.open(context, const ModelSettingsPage()),
            ),
            SettingsTile(
              icon: Icons.phonelink_ring_outlined,
              title: '系统授权',
              subtitle: '权限与手机助手连接',
              onTap: () => AppPage.open(context, const SystemAccessPage()),
            ),
            SettingsTile(
              icon: Icons.admin_panel_settings_outlined,
              title: '应用访问',
              subtitle: '允许操作的应用与分享范围',
              onTap: () => AppPage.open(context, const AppAccessPage()),
            ),
            SettingsTile(
              icon: Icons.link_rounded,
              title: 'Amoo',
              subtitle: '接入 Amoo、微信对话与手机任务',
              onTap: () => AppPage.open(context, const AmooPage()),
            ),
          ],
        ),
        const SizedBox(height: 28),
        PaletteStrip(
          selected: controller.accentPreset,
          onSelected: controller.setAccent,
        ),
        SettingsSection(
          title: '外观与手感',
          rows: [
            SettingsTile(
              icon: Icons.palette_outlined,
              title: '主题与配色',
              value: controller.accentPreset.label,
              onTap: () => AppPage.open(context, const AppearancePage()),
            ),
            SettingsSegmentTile(
              icon: Icons.water_drop_outlined,
              title: '液态玻璃',
              subtitle: '选择玻璃的浓度：清透更轻，浓郁更实',
              options: const ['清透', '均衡', '浓郁'],
              value: controller.glassDensity.index,
              onChanged: (index) =>
                  controller.setGlassDensity(MaeveGlassDensity.values[index]),
            ),
            SettingsSwitchTile(
              icon: Icons.vibration_rounded,
              title: '触感反馈',
              value: controller.haptics,
              onChanged: (_) => controller.toggleHaptics(),
            ),
          ],
        ),
        const FooterNote(
          text: '界面始终遵循系统的“移除动画”和高对比度设置。切换浓度会改变填充、模糊与边光，文字对比度始终保留。',
        ),
        SettingsSection(
          title: '关于',
          rows: [
            SettingsTile(
              icon: Icons.info_outline_rounded,
              title: '关于 Maeve',
              subtitle: '名字与版本',
              onTap: () => AppPage.open(context, const AboutPage()),
            ),
            SettingsTile(
              icon: Icons.explore_outlined,
              title: '新手引导',
              onTap: () => AppPage.open(context, const WelcomePage()),
            ),
            SettingsTile(
              icon: Icons.system_update_alt_rounded,
              title: '获取更新',
              subtitle: '查看新版本与更新说明',
              onTap: () => openHostLink(context, 'updates', label: '更新'),
            ),
          ],
        ),
        const SizedBox(height: 30),
        Text(
          '下一幕，由你决定。',
          textAlign: TextAlign.center,
          style: MaeveType.hint.copyWith(color: c.ink3),
        ),
      ],
    );
  }
}

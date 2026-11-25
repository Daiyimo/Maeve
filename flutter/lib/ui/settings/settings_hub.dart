import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../widgets/glass_surface.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../design/maeve_shapes.dart';
import '../common/app_page.dart';
import '../common/haptics.dart';
import '../common/pressable.dart';
import 'settings_components.dart';
import 'appearance_page.dart';
import 'model_settings_page.dart';
import 'system_access_page.dart';
import 'amoo_page.dart';
import 'about_pages.dart';
import 'palette_strip.dart';
import 'phone_readiness_page.dart';
import 'execution_settings_page.dart';
import 'app_access_page.dart';
import 'run_history_page.dart';
import 'scheduled_tasks_page.dart';
import 'update_page.dart';
import 'welcome_page.dart';

class SettingsHubPage extends StatelessWidget {
  const SettingsHubPage({super.key});
  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final theme = MaeveTheme.of(context);
    final c = theme.colors;
    final readiness = PhoneReadiness.of(controller);
    final statusTone = !readiness.loaded || readiness.error != null
        ? Tone.neutral
        : readiness.ready
        ? Tone.ok
        : Tone.warn;
    // 入口卡统一用强调色紫洗层(与详情页头卡一致):语义色(琥珀/绿)在奶白玻璃上
    // 读作"黄块/绿块",不贴 Maeve 气质;状态由右侧值文字与图标表达。
    final statusTint = c.accent;
    return AppPage.list(
      title: '设置',
      subtitle: '让 Maeve 适合你的日常',
      children: [
        if (controller.settingsError != null) ...[
          SettingsNotice(
            text: controller.settingsError!,
            icon: Icons.info_outline,
          ),
          MaeveGlassTextButton(
            label: '重新读取设备设置',
            compact: true,
            haptic: MaeveHaptic.tick,
            onPressed: controller.refreshSettings,
          ),
        ],
        // 手机操作状态检测放在最上面:新装设备权限未授权时,这里是第一眼的提示。
        // 染色玻璃卡:语义色洗层让白色受光边与斜带在奶白画布上"显形"——
        // 白对白的卡档视觉上近乎隐形(见 visual-tokens 液态玻璃条款)。
        Padding(
          padding: const EdgeInsets.only(top: MaeveSpace.s4),
          child: Pressable(
            onTap: () => AppPage.open(context, const PhoneReadinessPage()),
            borderRadius: BorderRadius.circular(MaeveRadius.lg),
            haptic: MaeveHaptic.tick,
            semanticLabel: '手机操作状态检测',
            child: MaeveGlassSurface(
              tier: MaeveGlassTier.card,
              tint: statusTint,
              tintAlpha: .16,
              padding: const EdgeInsets.all(MaeveSpace.s16),
              child: Row(
                children: [
                  // 图标随今日色彩(中性=强调色);状态交给染色洗层与值文字。
                  IconTile(icon: Icons.health_and_safety_outlined),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '手机操作状态检测',
                          style: MaeveType.body.copyWith(color: c.ink),
                        ),
                        Text(
                          !readiness.loaded
                              ? '正在读取设备状态'
                              : readiness.ready
                              ? '权限与助手连接都已就绪'
                              : '无障碍、悬浮窗、通知与助手连接，点击逐项补齐',
                          style: MaeveType.caption.copyWith(color: c.ink3),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: MaeveSpace.s8),
                  RowValue(
                    text: !readiness.loaded
                        ? '读取中'
                        : readiness.error != null
                        ? '暂不可读'
                        : readiness.ready
                        ? '已就绪'
                        : '${readiness.pending} 项待完成',
                    color: statusTone == Tone.neutral
                        ? null
                        : toneColors(context, statusTone).$2,
                  ),
                  const SizedBox(width: MaeveSpace.s8),
                  const RowChevron(),
                ],
              ),
            ),
          ),
        ),
        SettingsSection(
          title: '执行与设备',
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
              icon: Icons.schedule_rounded,
              title: '定时任务',
              subtitle: '设置每日任务或立即执行',
              onTap: () => AppPage.open(context, const ScheduledTasksPage()),
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
              title: '连接更多',
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
              // 选项与档位同源:枚举顺序改了也不会静默错位。
              options: [for (final d in MaeveGlassDensity.values) d.label],
              value: MaeveGlassDensity.values.indexOf(controller.glassDensity),
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
              icon: Icons.privacy_tip_outlined,
              title: '隐私声明',
              subtitle: '数据去向与本机保存',
              onTap: () => AppPage.open(context, const PrivacyPage()),
            ),
            SettingsTile(
              icon: Icons.info_outline_rounded,
              title: '关于 Maeve',
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
              onTap: () => AppPage.open(context, const UpdatePage()),
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

import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_tokens.dart';
import '../../widgets/maeve_mark.dart';
import '../common/app_page.dart';
import '../common/status.dart';
import '../memory/memory_page.dart';
import '../phone/phone_control_page.dart';
import 'about_pages.dart';
import 'appearance_page.dart';
import 'general_pages.dart';
import 'settings_components.dart';

/// B8 设置 hub:搜索胶囊 + 一览卡 + 六组卡 + 底部品牌标。
///
/// 分组按"人要做的一件事"组织,不是按代码模块。hub 上**不放**任何会放宽保护
/// 或删数据的东西 —— 那些都在二级页里,而且要长按确认。
class SettingsHubPage extends StatefulWidget {
  const SettingsHubPage({super.key, this.openSearch = false});

  /// QuickPanel 长按 ⚙ 直接落到搜索。
  final bool openSearch;

  @override
  State<SettingsHubPage> createState() => _SettingsHubPageState();
}

class _SettingsHubPageState extends State<SettingsHubPage> {
  @override
  void initState() {
    super.initState();
    if (widget.openSearch) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _openSearch();
      });
    }
  }

  void _openSearch() => AppPage.open(context, const SettingsSearchPage());

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final scheme = Theme.of(context).colorScheme;
    final success = MaeveTokens.success(Theme.of(context).brightness);
    final warning = MaeveTokens.warning(Theme.of(context).brightness);

    return AppPage.list(
      title: '设置',
      subtitle: '外观、能力与主机',
      children: [
        SearchCapsuleButton(onTap: _openSearch),
        const SizedBox(height: MaeveSpace.lg),
        SettingsOverview(
          name: '本机',
          detail: '未登录 · 所有内容只留在这台设备',
          tone: controller.connected ? MaeveTone.ready : MaeveTone.idle,
          stats: [
            (label: '余额', value: '¥0.00', mono: true),
            (
              label: '引擎',
              value: '${controller.model} · ${controller.modelTier}',
              mono: false,
            ),
            (label: '版本', value: '0.1.0', mono: true),
          ],
          onTap: () => AppPage.open(context, const AccountPage()),
        ),
        const SizedBox(height: MaeveSpace.xl),
        SettingsSection(
          title: 'AI',
          rows: [
            SettingsTile(
              icon: Icons.auto_awesome,
              title: '模型与思考',
              subtitle: '能力档位决定它想多久',
              value: '${controller.model} · ${controller.modelTier}',
              onTap: () => AppPage.open(context, const EnginePage()),
            ),
            SettingsTile(
              icon: Icons.tune_rounded,
              title: '人格与提示词',
              subtitle: '它说话的方式',
              value: '默认',
              onTap: () => AppPage.open(context, const ProvidersPage()),
            ),
            SettingsTile(
              icon: Icons.bookmark_border_rounded,
              title: '记忆',
              value: controller.memories.isEmpty
                  ? '空'
                  : '${controller.memories.length} 条',
              mono: true,
              onTap: () => AppPage.open(context, const MemoryPage()),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.xl),
        SettingsSection(
          title: '能力',
          hue: success,
          rows: [
            SettingsTile(
              icon: Icons.smartphone_rounded,
              title: '手机操控',
              subtitle: '让它替你在应用里点',
              chip: StatusChip(
                label: '未开启',
                tone: MaeveTone.danger,
                filled: true,
              ),
              onTap: () => AppPage.open(context, const PhoneControlPage()),
            ),
            SettingsTile(
              icon: Icons.record_voice_over_outlined,
              title: '语音',
              value: '关闭',
              onTap: () => AppPage.open(context, const VoicePage()),
            ),
            SettingsTile(
              icon: Icons.notifications_none_rounded,
              title: '通知',
              value: '仅关键',
              onTap: () => AppPage.open(context, const NotificationPage()),
            ),
            SettingsTile(
              icon: Icons.visibility_outlined,
              title: '屏幕读取',
              subtitle: '无障碍服务授权',
              chip: const StatusChip(label: '未授权'),
              onTap: () => AppPage.open(context, const SystemAccessPage()),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.xl),
        SettingsSection(
          title: '外观',
          hue: warning,
          rows: [
            SettingsTile(
              icon: Icons.palette_outlined,
              title: '配色',
              value: controller.palette.label,
              onTap: () => AppPage.open(context, const AppearancePage()),
            ),
            SettingsTile(
              icon: Icons.dashboard_outlined,
              title: '界面',
              subtitle: '列表常驻、图标风格',
              value: '标准',
              onTap: () => AppPage.open(context, const InterfacePage()),
            ),
            SettingsTile(
              icon: Icons.bolt_outlined,
              title: '动效与性能',
              subtitle: '玻璃模糊可以关',
              value: controller.reduceTransparency ? '已降级' : '标准',
              onTap: () => AppPage.open(context, const PerformancePage()),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.xl),
        SettingsSection(
          title: '通用',
          hue: scheme.primary,
          rows: [
            SettingsTile(
              icon: Icons.chat_bubble_outline_rounded,
              title: '回复偏好',
              value: '简洁',
              onTap: () => AppPage.open(context, const ReplyPrefsPage()),
            ),
            SettingsTile(
              icon: Icons.lock_outline_rounded,
              title: '隐私',
              subtitle: '什么会离开这台设备',
              value: '本机优先',
              onTap: () => AppPage.open(context, const PrivacyPage()),
            ),
            SettingsSwitchTile(
              icon: Icons.vibration_rounded,
              title: '触感反馈',
              subtitle: '按一下有回应，关掉就完全安静',
              value: controller.haptics,
              onChanged: (_) => controller.toggleHaptics(),
            ),
            SettingsTile(
              icon: Icons.translate_rounded,
              title: '语言',
              value: '中文（简体）',
              onTap: () => AppPage.open(context, const LanguagePage()),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.xl),
        SettingsSection(
          title: '主机',
          hue: success,
          rows: [
            SettingsTile(
              icon: Icons.dns_outlined,
              title: '远程主机',
              subtitle: '把任务交给配对的机器',
              chip: const StatusChip(label: '未配置'),
              onTap: () => AppPage.open(context, const RemotePage()),
            ),
            SettingsTile(
              icon: Icons.memory_rounded,
              title: '引擎设置',
              subtitle: '上下文、超时、工具',
              onTap: () => AppPage.open(context, const EngineSettingsHub()),
            ),
            SettingsTile(
              icon: Icons.extension_outlined,
              title: '插件',
              value: '0',
              mono: true,
              onTap: () => AppPage.open(context, const PluginsPage()),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.xl),
        SettingsSection(
          title: '帮助与关于',
          rows: [
            SettingsTile(
              icon: Icons.monitor_heart_outlined,
              title: '诊断',
              subtitle: '日志与运行状态',
              onTap: () => AppPage.open(context, const DiagnosticsPage()),
            ),
            SettingsTile(
              icon: Icons.system_update_alt_rounded,
              title: '更新',
              value: '已是最新',
              onTap: () => AppPage.open(context, const UpdatePage()),
            ),
            SettingsTile(
              icon: Icons.info_outline_rounded,
              title: '关于 Maeve',
              value: '0.1.0',
              mono: true,
              onTap: () => AppPage.open(context, const AboutPage()),
            ),
            SettingsTile(
              icon: Icons.school_outlined,
              title: '重新查看首次引导',
              onTap: () =>
                  AppPage.open(context, const AboutPage(showIntro: true)),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.xxl),
        Center(
          child: Column(
            children: [
              // 底部居中 BrandMark 18,退到 textDisabled 的层级。
              Opacity(opacity: .38, child: const MaeveMark(size: 18)),
              const SizedBox(height: 6),
              Text(
                'Maeve 0.1.0 · 界面原型',
                style: MaeveFonts.mono(
                  context,
                  size: 11,
                  color: scheme.onSurfaceVariant.withValues(alpha: .7),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: MaeveSpace.xxl),
      ],
    );
  }
}

/// 设置搜索:输入即过滤,点结果直达那一页。
class SettingsSearchPage extends StatefulWidget {
  const SettingsSearchPage({super.key});

  @override
  State<SettingsSearchPage> createState() => _SettingsSearchPageState();
}

class _SettingsSearchPageState extends State<SettingsSearchPage> {
  final TextEditingController _input = TextEditingController();
  String _query = '';

  static final _index =
      <({String title, String group, IconData icon, Widget page})>[
        (
          title: '配色',
          group: '外观',
          icon: Icons.palette_outlined,
          page: const AppearancePage(),
        ),
        (
          title: '界面',
          group: '外观',
          icon: Icons.dashboard_outlined,
          page: const InterfacePage(),
        ),
        (
          title: '动效与性能',
          group: '外观',
          icon: Icons.bolt_outlined,
          page: const PerformancePage(),
        ),
        (
          title: '触感反馈',
          group: '通用',
          icon: Icons.vibration_rounded,
          page: const InterfacePage(),
        ),
        (
          title: '回复偏好',
          group: '通用',
          icon: Icons.chat_bubble_outline_rounded,
          page: const ReplyPrefsPage(),
        ),
        (
          title: '隐私',
          group: '通用',
          icon: Icons.lock_outline_rounded,
          page: const PrivacyPage(),
        ),
        (
          title: '语言',
          group: '通用',
          icon: Icons.translate_rounded,
          page: const LanguagePage(),
        ),
        (
          title: '语音',
          group: '能力',
          icon: Icons.record_voice_over_outlined,
          page: const VoicePage(),
        ),
        (
          title: '通知',
          group: '能力',
          icon: Icons.notifications_none_rounded,
          page: const NotificationPage(),
        ),
        (
          title: '屏幕读取',
          group: '能力',
          icon: Icons.visibility_outlined,
          page: const SystemAccessPage(),
        ),
        (
          title: '手机操控',
          group: '能力',
          icon: Icons.smartphone_rounded,
          page: const PhoneControlPage(),
        ),
        (
          title: '模型与思考',
          group: 'AI',
          icon: Icons.auto_awesome,
          page: const EnginePage(),
        ),
        (
          title: '引擎设置',
          group: '主机',
          icon: Icons.memory_rounded,
          page: const EngineSettingsHub(),
        ),
        (
          title: '远程主机',
          group: '主机',
          icon: Icons.dns_outlined,
          page: const RemotePage(),
        ),
        (
          title: '插件',
          group: '主机',
          icon: Icons.extension_outlined,
          page: const PluginsPage(),
        ),
        (
          title: '诊断',
          group: '帮助',
          icon: Icons.monitor_heart_outlined,
          page: const DiagnosticsPage(),
        ),
        (
          title: '关于',
          group: '帮助',
          icon: Icons.info_outline_rounded,
          page: const AboutPage(),
        ),
        (
          title: '后台保活',
          group: '帮助',
          icon: Icons.battery_saver_outlined,
          page: const BackgroundGuidePage(),
        ),
      ];

  @override
  void dispose() {
    _input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final query = _query.trim();
    final results = query.isEmpty
        ? _index
        : _index.where((entry) {
            final haystack = '${entry.title}${entry.group}'.toLowerCase();
            return haystack.contains(query.toLowerCase());
          }).toList();

    return AppPage(
      title: '搜索设置',
      padded: true,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          MaeveSpace.gutter,
          MaeveLayout.pageBarHeight + MaeveSpace.sm,
          MaeveSpace.gutter,
          MaeveSpace.xxl,
        ),
        children: [
          TextField(
            key: const ValueKey('settings-search'),
            controller: _input,
            autofocus: true,
            onChanged: (value) => setState(() => _query = value),
            style: Theme.of(context).textTheme.bodyLarge,
            decoration: InputDecoration(
              hintText: '搜一个设置项',
              prefixIcon: const Icon(Icons.search, size: 18),
              filled: true,
              fillColor: Theme.of(context).colorScheme.onSurface
                  .withValues(alpha: .05),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(MaeveRadius.pill),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: MaeveSpace.lg),
          if (results.isEmpty)
            EmptyView(
              icon: Icons.search_off_rounded,
              title: '没找到',
              body: '换个词试试，或者回上一页逐组找。',
              compact: true,
            )
          else
            for (final entry in results)
              ListPageRow(
                title: entry.title,
                subtitle: entry.group,
                leading: Icon(entry.icon, size: 19),
                onTap: () => AppPage.open(context, entry.page),
              ),
        ],
      ),
    );
  }
}

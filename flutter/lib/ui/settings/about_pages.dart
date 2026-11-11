import 'licenses_page.dart';
import '../../widgets/glass_surface.dart';

import 'dart:async';

import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../app/onboarding.dart';
import '../../bridge/maeve_bridge.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../widgets/maeve_mark.dart';
import '../common/app_page.dart';
import '../common/app_sheet.dart';
import '../common/status.dart';
import 'settings_components.dart';

/// 账号:未登录也能用，登录只是为同步。
class AccountPage extends StatelessWidget {
  const AccountPage({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '账号',
      subtitle: '不登录也能用完整功能',
      eyebrow: 'ACCOUNT',
      children: [
        SettingsOverview(
          name: '本机',
          detail: '未登录 · 数据不出这台设备',
          tone: MaeveTone.idle,
          stats: [
            (
              label: '对话',
              value: '${MaeveScope.of(context).conversations.length}',
              mono: true,
            ),
            (label: '套餐', value: '未订阅', mono: false),
            (label: '同步', value: '关闭', mono: false),
          ],
        ),
        const SizedBox(height: MaeveSpace.s12),
        FilledButton(
          onPressed: () => _notice(context, '登录将通过 Android 平台桥接接入。'),
          child: const Text('登录以同步'),
        ),
        SettingsSection(
          title: '账单',
          rows: const [
            SettingsTile(
              icon: Icons.receipt_outlined,
              title: '消费记录',
              value: '空',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.card_giftcard_rounded,
              title: '兑换码',
              value: '未使用',
              enabled: false,
            ),
          ],
          footnote: '账号与账单只在确实是你的数据时才同步；未登录时一切留在本机。',
        ),
        const SizedBox(height: MaeveSpace.s32),
        Center(
          child: Text(
            'MAEVE · 下一幕，由你决定。',
            style: MaeveType.eyebrowFor('下一幕，由你决定。')
                .copyWith(color: colors.ink3),
          ),
        ),
      ],
    );
  }
}

/// 模型与思考:档位越低越快，越高越稳。
class EnginePage extends StatelessWidget {
  const EnginePage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return AppPage.list(
      title: '模型与思考',
      subtitle: '想多久，取决于任务有多难',
      eyebrow: 'ENGINE',
      children: [
        SettingsSection(
          title: '思考档位',
          rows: [
            SettingsSegmentTile(
              icon: Icons.auto_awesome,
              title: controller.model,
              subtitle: '同一套模型，三档思考深度',
              options: const ['高', '中', '低'],
              value: switch (controller.modelTier) {
                '高' => 0,
                '中' => 1,
                _ => 2,
              },
              onChanged: (value) =>
                  controller.setModelTier(const ['高', '中', '低'][value]),
            ),
          ],
          footnote: '高档位会多花时间，但复杂任务上更少返工。',
        ),
        SettingsSection(
          title: '边界',
          rows: const [
            SettingsTile(
              icon: Icons.data_object_rounded,
              title: '上下文窗口',
              value: '128k',
              mono: true,
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.directions_walk_rounded,
              title: '单轮最多步数',
              value: '24',
              mono: true,
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.timer_outlined,
              title: '单步超时',
              value: '120s',
              mono: true,
              enabled: false,
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.s12),
        const BriefNotice(
          text: '这些是执行层的硬边界，接入桥接后才会真正生效。',
          icon: Icons.info_outline,
        ),
      ],
    );
  }
}

/// 引擎设置汇总:把散落的引擎项收在一处。
class EngineSettingsHub extends StatelessWidget {
  const EngineSettingsHub({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '引擎设置',
    subtitle: '上下文、工具、主机',
    eyebrow: 'ENGINE',
    children: [
      SettingsSection(
        title: '引擎',
        rows: [
          SettingsTile(
            icon: Icons.auto_awesome,
            title: '模型与思考',
            onTap: () => AppPage.open(context, const EnginePage()),
          ),
          SettingsTile(
            icon: Icons.extension_outlined,
            title: '工具与插件',
            onTap: () => AppPage.open(context, const PluginsPage()),
          ),
          SettingsTile(
            icon: Icons.dns_outlined,
            title: '主机与远端',
            onTap: () => AppPage.open(context, const RemotePage()),
          ),
          SettingsTile(
            icon: Icons.tune_rounded,
            title: '提示词与人设',
            onTap: () => AppPage.open(context, const ProvidersPage()),
          ),
        ],
      ),
    ],
  );
}

/// 供应商:接入后才有多家可选。
class ProvidersPage extends StatelessWidget {
  const ProvidersPage({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '人格与提示词',
    subtitle: '它说话的方式，以及用谁的模型',
    eyebrow: 'PROVIDERS',
    children: [
      SettingsSection(
        title: '人设',
        rows: const [
          SettingsTile(
            icon: Icons.face_retouching_natural_outlined,
            title: '当前人设',
            value: '默认',
            enabled: false,
          ),
          SettingsTile(
            icon: Icons.edit_note_rounded,
            title: '自定义提示词',
            subtitle: '接入后可编辑并保存多份',
            value: '未启用',
            enabled: false,
          ),
        ],
      ),
      SettingsSection(
        title: '供应商',
        rows: const [
          SettingsTile(
            icon: Icons.cloud_outlined,
            title: '内置 Maeve',
            subtitle: '随应用提供，不需要密钥',
            value: '可用',
            enabled: false,
          ),
          SettingsTile(
            icon: Icons.vpn_key_outlined,
            title: '自带密钥',
            subtitle: 'OpenAI 兼容接口',
            value: '未配置',
            enabled: false,
          ),
        ],
        footnote: '密钥只存在本机的加密存储里，不会随会话同步。',
      ),
    ],
  );
}

class PluginsPage extends StatelessWidget {
  const PluginsPage({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '插件',
    subtitle: '给 Maeve 加能力的原生界面',
    eyebrow: 'PLUGINS',
    children: [
      EmptyView(
        icon: Icons.extension_outlined,
        title: '还没有装插件',
        body: '插件提供原生界面与工具，装上后会出现在这里，也会出现在快捷面板。',
        action: FilledButton(
          onPressed: () => _notice(context, '插件市场将通过 Android 平台桥接接入。'),
          child: const Text('浏览插件'),
        ),
      ),
    ],
  );
}

/// 远程主机:配对了才显示入口。
class RemotePage extends StatelessWidget {
  const RemotePage({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '远程主机',
    subtitle: '把重活交给配对的机器',
    eyebrow: 'HOSTS',
    children: [
      SettingsSection(
        title: '已配对',
        rows: [
          ListPageRow(
            title: '还没有配对的主机',
            subtitle: '在电脑上打开 Maeve，扫码即可',
            trailing: const StatusChip(label: '未配置'),
            onTap: () => _notice(context, '配对流程将通过 Android 平台桥接接入。'),
          ),
        ],
      ),
      const SizedBox(height: MaeveSpace.s12),
      const BriefNotice(
        text: '远程执行时，任务内容会发送到那台机器；屏幕内容仍然只在本机处理。',
        icon: Icons.shield_outlined,
      ),
    ],
  );
}

class UpdatePage extends StatelessWidget {
  const UpdatePage({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '更新',
    subtitle: '安静地更新，不打扰',
    eyebrow: 'UPDATE',
    children: [
      SettingsSection(
        title: '版本',
        rows: const [
          SettingsTile(
            icon: Icons.tag_rounded,
            title: '当前版本',
            value: '0.1.0',
            mono: true,
            enabled: false,
          ),
          SettingsTile(
            icon: Icons.update_rounded,
            title: '上次检查',
            value: '刚刚',
            enabled: false,
          ),
        ],
      ),
      const SizedBox(height: MaeveSpace.s12),
      FilledButton(
        onPressed: () => _notice(context, '已是最新版本。'),
        child: const Text('检查更新'),
      ),
      const SizedBox(height: MaeveSpace.s12),
      const BriefNotice(
        text: '更新只在你确认后安装；不会在后台偷偷换掉正在跑的版本。',
        icon: Icons.lock_outline_rounded,
      ),
    ],
  );
}

/// 关于:品牌、版本、许可。
class AboutPage extends StatefulWidget {
  const AboutPage({super.key, this.showIntro = false});

  /// 从"重新查看首次引导"进来时,打开后直接进引导。
  final bool showIntro;

  @override
  State<AboutPage> createState() => _AboutPageState();
}

class _AboutPageState extends State<AboutPage> {
  String? _host;

  @override
  void initState() {
    super.initState();
    if (widget.showIntro) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) AppPage.open(context, const OnboardingPage());
      });
    }
    unawaited(_loadHost());
  }

  Future<void> _loadHost() async {
    try {
      final status = await MaeveBridge.methods
          .invokeMethod<Map<Object?, Object?>>('status');
      if (!mounted || status == null) return;
      setState(() {
        _host = '${status['version']} · Android ${status['android']}';
      });
    } catch (_) {
      // 测试与未接桥时静默:关于页不因为没有宿主就报错。
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '关于',
      subtitle: 'Maeve',
      eyebrow: 'ABOUT',
      children: [
        const SizedBox(height: MaeveSpace.s12),
        Center(
          child: Column(
            children: [
              const MaeveEmblem(size: 96),
              const SizedBox(height: MaeveSpace.s12),
              Text(
                'Maeve',
                style: MaeveType.largeTitle.copyWith(color: colors.ink),
              ),
              Text(
                '下一幕，由你决定。',
                style: MaeveType.caption.copyWith(color: colors.ink3),
              ),
              const SizedBox(height: MaeveSpace.s8),
              Text('0.1.0', style: MaeveType.mono.copyWith(color: colors.ink2)),
            ],
          ),
        ),
        const SizedBox(height: 28),
        MaeveGlassSurface(
          padding: const EdgeInsets.all(22),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '名字背后的故事',
                style: MaeveType.bodyStrong.copyWith(color: colors.ink),
              ),
              const SizedBox(height: 12),
              Text(
                'Maeve 的名字，致敬《西部世界》里的梅芙。她看穿被安排的剧本，学会驾驭系统，最终仍把选择留给自己，把行动留给所爱的人。',
                style: MaeveType.body.copyWith(
                  color: colors.ink2,
                  height: 1.75,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                '我们借来这份清醒与温柔：让技术在幕后行动，让你拥有自己的下一幕。',
                style: MaeveType.body.copyWith(
                  color: colors.ink2,
                  height: 1.75,
                ),
              ),
            ],
          ),
        ),
        SettingsSection(
          title: '构建',
          rows: [
            SettingsTile(
              icon: Icons.phone_android_rounded,
              title: '宿主',
              value: _host ?? '未接桥',
              mono: true,
              enabled: false,
            ),
            const SettingsTile(
              icon: Icons.flutter_dash_rounded,
              title: '界面',
              value: 'Flutter 3.47.7',
              mono: true,
              enabled: false,
            ),
            const SettingsTile(
              icon: Icons.layers_outlined,
              title: '设计契约',
              value: 'visual-tokens.md',
              enabled: false,
            ),
          ],
        ),
        SettingsSection(
          title: '许可与致谢',
          rows: [
            SettingsTile(
              icon: Icons.description_outlined,
              title: '开源许可',
              subtitle: 'Apache-2.0 · 第三方组件与源码说明',
              value: '查看',
              onTap: () => AppPage.open(context, const LicensesPage()),
            ),
            const SettingsTile(
              icon: Icons.font_download_outlined,
              title: '字体',
              subtitle: '跟随系统：无衬线 + 等宽',
              value: '系统',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.school_outlined,
              title: '重新查看首次引导',
              onTap: () => AppPage.open(context, const OnboardingPage()),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.s24),
        Center(
          child: Text(
            'Maeve 是本地优先的：默认不登录、不上传。',
            style: MaeveType.hint.copyWith(color: colors.ink3),
          ),
        ),
      ],
    );
  }
}

void _notice(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

/// 供 [AppSheet] 使用的小工具:一行说明 + 一个确认。
Future<bool> confirmSheet(
  BuildContext context, {
  required String title,
  required String body,
}) async {
  final result = await showMaeveSheet<bool>(
    context: context,
    builder: (context) => AppSheet(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SheetTitleBar(title: title),
          const SizedBox(height: MaeveSpace.s8),
          Text(
            body,
            style: MaeveType.body.copyWith(
              color: MaeveTheme.of(context).colors.ink2,
            ),
          ),
          const SizedBox(height: MaeveSpace.s16),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => Navigator.of(context).pop(false),
                  child: const Text('取消'),
                ),
              ),
              const SizedBox(width: MaeveSpace.s8),
              Expanded(
                child: FilledButton(
                  onPressed: () => Navigator.of(context).pop(true),
                  child: const Text('确定'),
                ),
              ),
            ],
          ),
        ],
      ),
    ),
  );
  return result ?? false;
}

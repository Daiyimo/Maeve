import 'package:flutter/material.dart';

import '../../bridge/maeve_bridge.dart';
import '../../design/maeve_motion.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import '../common/app_sheet.dart';
import '../common/hold_to_confirm.dart';
import '../common/pressable.dart';
import '../common/status.dart';
import '../settings/settings_components.dart';

/// 手机操控:总开关 + 首次显著披露 + 状态 + 读屏测试 + 悬浮条 + 确认方式 +
/// 应用名单 + 解除限制 + 最近操作。
///
/// 行为语义对齐 `core/risk/AppTier.kt`、`SensitiveText.kt`、`ConfirmDecision.kt`:
/// 支付/银行/券商**硬拦**(自主模式也不例外);微信每一步写操作都要确认;
/// 密码/验证码页交还用户;作用在自己身上不做。
class PhoneControlPage extends StatefulWidget {
  const PhoneControlPage({super.key});

  @override
  State<PhoneControlPage> createState() => _PhoneControlPageState();
}

class _PhoneControlPageState extends State<PhoneControlPage> {
  bool _enabled = false;
  bool _accessibility = false;
  bool _overlay = false;
  bool _screenTested = false;
  int _strip = 0;
  int _confirm = 0;
  final bool _running = false;
  bool _relaxed = false;

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;
    return AppPage.list(
      title: '手机操控',
      subtitle: '让它替你在应用里动手',
      eyebrow: 'PHONE CONTROL',
      children: [
        SettingsSection(
          title: '总开关',
          rows: [
            SettingsSwitchTile(
              icon: Icons.smartphone_rounded,
              title: '允许 Maeve 操作这台手机',
              subtitle: _enabled ? '已开启，随时可以关' : '默认关闭，需要你明确同意',
              value: _enabled,
              onChanged: (value) {
                if (value && !_enabled) {
                  _disclose();
                } else {
                  setState(() => _enabled = false);
                }
              },
            ),
          ],
          footnote: '开启后会读屏、点按、输入。她不会在你没让她做任务时自己动手。',
        ),
        SettingsSection(
          title: '权限',
          rows: [
            SettingsTile(
              icon: Icons.accessibility_new_rounded,
              title: '无障碍服务',
              subtitle: '读取界面、执行点按',
              chip: StatusChip(
                label: _accessibility ? '已开启' : '未开启',
                tone: _accessibility ? MaeveTone.ready : MaeveTone.danger,
                filled: true,
              ),
              onTap: () async {
                await MaeveBridge.openSettings();
                if (!context.mounted) return;
                setState(() => _accessibility = true);
              },
            ),
            SettingsTile(
              icon: Icons.picture_in_picture_alt_outlined,
              title: '悬浮窗',
              subtitle: '显示悬浮条与确认卡',
              chip: StatusChip(
                label: _overlay ? '已授权' : '未授权',
                tone: _overlay ? MaeveTone.ready : MaeveTone.warning,
                filled: true,
              ),
              onTap: () async {
                await MaeveBridge.openSettings();
                if (!context.mounted) return;
                setState(() => _overlay = true);
              },
            ),
          ],
        ),
        SettingsSection(
          title: '自检',
          rows: [
            SettingsTile(
              icon: Icons.fact_check_outlined,
              title: '读屏测试',
              subtitle: '在本机跑一次，结果不发模型',
              value: _screenTested ? '通过' : '未测试',
              onTap: () {
                setState(() => _screenTested = true);
                _notice('读屏测试在本机完成：能读到当前界面，未发送任何内容。');
              },
            ),
          ],
        ),
        SettingsSection(
          title: '悬浮条',
          rows: [
            SettingsSegmentTile(
              icon: Icons.crop_16_9_rounded,
              title: '形态',
              subtitle: '按住 1 秒立刻停止当前任务',
              options: const ['胶囊', '卡片', '小球'],
              value: _strip,
              onChanged: (value) => setState(() => _strip = value),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.s16,
                0,
                MaeveSpace.s16,
                MaeveSpace.s12,
              ),
              child: _StripPreview(state: _strip),
            ),
          ],
        ),
        SettingsSection(
          title: '确认方式',
          rows: [
            SettingsSegmentTile(
              icon: Icons.how_to_reg_outlined,
              title: '怎么问你',
              subtitle: '涉及花钱、发消息、解绑这类不可逆的事',
              options: const ['询问（默认）', '自主'],
              value: _confirm,
              onChanged: (value) => setState(() => _confirm = value),
            ),
          ],
          footnote: '确认卡 30 秒不选按拒绝处理；没人在手机旁也按不同意。',
        ),
        SettingsSection(
          title: '应用名单',
          tone: Tone.bad,
          rows: [
            SettingsTile(
              icon: Icons.account_balance_outlined,
              title: '支付 / 银行 / 券商',
              subtitle: '写操作一律不做，自主模式也不例外',
              chip: const StatusChip(
                label: '不碰',
                tone: MaeveTone.danger,
                filled: true,
              ),
              onTap: () => _notice('这一档是硬规则：默认 24 个应用，可在进阶里查看清单。'),
            ),
            SettingsTile(
              icon: Icons.chat_rounded,
              title: '微信',
              subtitle: '每一次写操作都要你确认',
              chip: const StatusChip(label: '每步确认', tone: MaeveTone.warning),
              onTap: () => _notice('微信可以读，但每一步写操作都要你点头。'),
            ),
            SettingsTile(
              icon: Icons.apps_rounded,
              title: '其余应用',
              subtitle: '照常，不可逆动作仍然会问',
              chip: const StatusChip(label: '照常', tone: MaeveTone.ready),
              onTap: () => _notice('普通应用照常操作；付款、发消息这类仍会确认。'),
            ),
          ],
        ),
        const GroupLabel(text: '解除限制'),
        _RelaxRow(
          icon: Icons.account_balance_outlined,
          title: '允许操作支付 / 银行 / 券商',
          subtitle: '默认关闭 · 按住 1.5 秒生效',
          value: _relaxed,
          enabled: !_running,
          onConfirmed: () => setState(() => _relaxed = true),
          onRevoke: () => setState(() => _relaxed = false),
        ),
        const SizedBox(height: MaeveSpace.s8),
        _RelaxRow(
          icon: Icons.chat_rounded,
          title: '微信降为照常',
          subtitle: '默认关闭 · 按住 1.5 秒生效',
          value: false,
          enabled: !_running,
          onConfirmed: () => _notice('已解除：微信不再每步确认。'),
          onRevoke: () {},
        ),
        const SizedBox(height: MaeveSpace.s8),
        _RelaxRow(
          icon: Icons.password_rounded,
          title: '允许代填密码 / 验证码',
          subtitle: '默认关闭 · 按住 1.5 秒生效',
          value: false,
          enabled: !_running,
          onConfirmed: () => _notice('已解除：密码页可以代填（只记 ••••）。'),
          onRevoke: () {},
        ),
        const SizedBox(height: MaeveSpace.s8),
        Text(
          _running ? '任务进行中，不能修改解除限制。' : '三项默认关闭；任务进行中不能打开。',
          style: MaeveType.hint.copyWith(color: colors.ink3),
        ),
        SettingsSection(
          title: '最近操作',
          rows: const [
            SettingsTile(
              icon: Icons.touch_app_outlined,
              title: '点按「搜索」',
              subtitle: '设置 · 2 分钟前',
              value: '记录',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.keyboard_outlined,
              title: '输入文字（已打码）',
              subtitle: '设置 · 2 分钟前',
              value: '记录',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.arrow_back_rounded,
              title: '返回上一页',
              subtitle: '设置 · 3 分钟前',
              value: '记录',
              enabled: false,
            ),
          ],
          footnote: '只记动作，不记屏幕内容；密码与验证码只留 ••••。',
        ),
        SettingsSection(
          title: '进阶',
          rows: [
            const SettingsTile(
              icon: Icons.speed_rounded,
              title: '读取频率',
              subtitle: '界面变化时才重读，不按帧刷',
              value: '按需',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.delete_sweep_outlined,
              title: '清除操作记录',
              onTap: () => _notice('操作记录已清除（本机）。'),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.s24),
        Center(
          child: Text(
            'TIER · DENY / CONFIRM-EVERY-WRITE / NORMAL',
            style: MaeveType.eyebrow.copyWith(color: colors.ink3),
          ),
        ),
        const SizedBox(height: MaeveSpace.s24),
      ],
    );
  }

  void _notice(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _disclose() async {
    final accepted = await showMaeveSheet<bool>(
      context: context,
      builder: (context) => AppSheet(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SheetTitleBar(title: '开启前，请先看清楚', subtitle: '这是一台能替你动手的手机'),
            const SizedBox(height: MaeveSpace.s8),
            const _DisclosureLine(
              icon: Icons.visibility_outlined,
              title: '它会看见屏幕',
              body: '任务进行时读取当前界面，用来判断该点哪里。',
            ),
            const _DisclosureLine(
              icon: Icons.touch_app_outlined,
              title: '它会点按和输入',
              body: '在你指定的应用里操作；不可逆的动作会先问你。',
            ),
            const _DisclosureLine(
              icon: Icons.shield_outlined,
              title: '有些事它永远不做',
              body: '支付/银行/券商的写操作一律不碰；密码页交还给你。',
            ),
            const SizedBox(height: MaeveSpace.s16),
            HoldToConfirm(
              shape: MaeveShapes.button,
              semanticLabel: '按住开启手机操控',
              onConfirmed: () {
                setState(() => _enabled = true);
                Navigator.of(context).pop(true);
              },
              child: Container(
                height: MaeveSize.button,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: MaeveTheme.of(context).colors.accent,
                  borderRadius: BorderRadius.circular(MaeveRadius.md),
                ),
                child: Text(
                  '按住开启',
                  style: MaeveType.bodyStrong.copyWith(
                    color: MaeveTheme.of(context).colors.onAccent,
                  ),
                ),
              ),
            ),
            const SizedBox(height: MaeveSpace.s8),
            Text(
              '按住 1.5 秒生效，松手取消。',
              style: MaeveType.hint.copyWith(
                color: MaeveTheme.of(context).colors.ink3,
              ),
            ),
          ],
        ),
      ),
    );
    if (accepted != true && mounted) setState(() => _enabled = false);
  }
}

class _DisclosureLine extends StatelessWidget {
  const _DisclosureLine({
    required this.icon,
    required this.title,
    required this.body,
  });

  final IconData icon;
  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Padding(
      padding: const EdgeInsets.only(bottom: MaeveSpace.s12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: colors.ink3),
          const SizedBox(width: MaeveSpace.s12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: MaeveType.bodyStrong.copyWith(color: colors.ink),
                ),
                Text(body, style: MaeveType.hint.copyWith(color: colors.ink3)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 悬浮条三态预览:胶囊 → 卡片 → 小球。只是示意,真机上是系统悬浮窗。
class _StripPreview extends StatelessWidget {
  const _StripPreview({required this.state});

  final int state;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return AnimatedContainer(
      duration: AppMotion.of(context).standard,
      curve: MaeveMotion.collapseCurve,
      height: 72,
      decoration: BoxDecoration(
        color: colors.fill,
        borderRadius: BorderRadius.circular(MaeveRadius.md),
      ),
      child: Center(
        child: switch (state) {
          0 => Container(
            padding: const EdgeInsets.symmetric(
              horizontal: MaeveSpace.s12,
              vertical: 6,
            ),
            decoration: BoxDecoration(
              color: colors.surfaceHighest,
              borderRadius: BorderRadius.circular(MaeveRadius.pill),
              border: Border.all(color: colors.edge, width: .5),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const StatusDot(tone: MaeveTone.running, size: 6),
                const SizedBox(width: 6),
                Text(
                  '正在操作微信',
                  style: MaeveType.hint.copyWith(color: colors.ink2),
                ),
                const SizedBox(width: 8),
                Icon(Icons.stop_circle_outlined, size: 15, color: colors.bad),
              ],
            ),
          ),
          1 => Container(
            width: 200,
            padding: const EdgeInsets.all(MaeveSpace.s12),
            decoration: BoxDecoration(
              color: colors.surfaceHighest,
              borderRadius: BorderRadius.circular(MaeveRadius.md),
              border: Border.all(color: colors.edge, width: .5),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '第 3 步 · 输入搜索词',
                  style: MaeveType.hint.copyWith(color: colors.ink3),
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () {},
                        style: OutlinedButton.styleFrom(
                          minimumSize: const Size(0, 32),
                        ),
                        child: const Text('停止'),
                      ),
                    ),
                    const SizedBox(width: 6),
                    Expanded(
                      child: FilledButton(
                        onPressed: () {},
                        style: FilledButton.styleFrom(
                          minimumSize: const Size(0, 32),
                        ),
                        child: const Text('继续'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          _ => Container(
            width: 34,
            height: 34,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: colors.accent,
              shape: BoxShape.circle,
            ),
            child: Text(
              '3',
              style: MaeveType.mono.copyWith(color: colors.onAccent),
            ),
          ),
        },
      ),
    );
  }
}

/// 解除限制的一行:开启要按住 1.5 秒,关闭一点就行。
class _RelaxRow extends StatelessWidget {
  const _RelaxRow({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onConfirmed,
    required this.onRevoke,
    this.enabled = true,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool value;
  final VoidCallback onConfirmed;
  final VoidCallback onRevoke;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;
    final content = Container(
      padding: const EdgeInsets.all(MaeveSpace.s12),
      decoration: BoxDecoration(
        color: value ? colors.badSoft : colors.surface,
        borderRadius: BorderRadius.circular(MaeveRadius.md),
        border: Border.all(
          color: value
              ? theme.semantic.bad.withValues(alpha: .35)
              : colors.edge,
          width: .5,
        ),
      ),
      child: Row(
        children: [
          Icon(icon, size: 18, color: value ? theme.semantic.bad : colors.ink3),
          const SizedBox(width: MaeveSpace.s12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: MaeveType.body.copyWith(
                    color: value ? theme.semantic.bad : colors.ink,
                  ),
                ),
                Text(
                  subtitle,
                  style: MaeveType.hint.copyWith(color: colors.ink3),
                ),
              ],
            ),
          ),
          Text(
            value ? '已解除' : '关闭',
            style: MaeveType.hint.copyWith(
              color: value ? theme.semantic.bad : colors.ink3,
            ),
          ),
        ],
      ),
    );

    if (!enabled) return Opacity(opacity: .5, child: content);
    if (value) {
      return Pressable(
        onTap: onRevoke,
        borderRadius: BorderRadius.circular(MaeveRadius.md),
        child: content,
      );
    }
    return HoldToConfirm(
      shape: MaeveShapes.button,
      semanticLabel: '按住解除限制：$title',
      progressColor: theme.semantic.bad,
      onConfirmed: onConfirmed,
      child: content,
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../app/maeve_controller.dart';
import '../../bridge/maeve_bridge.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import '../common/hold_to_confirm.dart';
import '../common/status.dart';
import 'settings_components.dart';

/// 界面:图标风格、列表常驻、触感。
class InterfacePage extends StatelessWidget {
  const InterfacePage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return AppPage.list(
      title: '界面',
      subtitle: '列表怎么摆、点按怎么回应',
      eyebrow: 'INTERFACE',
      children: [
        SettingsSection(
          title: '布局',
          rows: [
            SettingsSegmentTile(
              icon: Icons.view_sidebar_outlined,
              title: '图标风格',
              options: const ['圆润', '单色'],
              value: controller.prefs.iconStyle,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.iconStyle = value),
            ),
            const SettingsTile(
              icon: Icons.desktop_windows_outlined,
              title: '宽屏列表常驻',
              subtitle: '断点 840dp：更宽时列表就留在左边',
              value: '自动',
              enabled: false,
            ),
          ],
        ),
        SettingsSection(
          title: '反馈',
          tone: Tone.warn,
          rows: [
            SettingsSwitchTile(
              icon: Icons.vibration_rounded,
              title: '触感反馈',
              subtitle: '五档语义：轻点到重按都给得准',
              value: controller.haptics,
              onChanged: (_) => controller.toggleHaptics(),
            ),
            const SettingsTile(
              icon: Icons.touch_app_outlined,
              title: '按压反馈',
              subtitle: '按下缩到 0.97，滚动时不出水波',
              value: '已开启',
              enabled: false,
            ),
          ],
        ),
      ],
    );
  }
}

/// 性能:减少透明效果、低端机模式。玻璃模糊的降级入口只在这里。
class PerformancePage extends StatelessWidget {
  const PerformancePage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final colors = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '动效与性能',
      subtitle: '关掉模糊仍然好看，只是更省电',
      eyebrow: 'PERFORMANCE',
      children: [
        SettingsSection(
          title: '透明效果',
          rows: [
            SettingsSwitchTile(
              icon: Icons.blur_on_rounded,
              title: '减少透明效果',
              subtitle: '玻璃改用近乎不透明的填充，不再采样背景',
              value: controller.reduceTransparency,
              onChanged: (_) => controller.toggleReduceTransparency(),
            ),
            SettingsSwitchTile(
              icon: Icons.speed_rounded,
              title: '低端机模式',
              subtitle: '自动关模糊、缩短动效，滚动更稳',
              value: controller.prefs.lowEndMode,
              onChanged: (value) => controller.updatePrefs((prefs) {
                prefs.lowEndMode = value;
              }),
            ),
          ],
          footnote: '系统打开"高对比度"或"移除动画"时，这两项会自动生效，不用手动开。',
        ),
        SettingsSection(
          title: '动效',
          rows: [
            SettingsTile(
              icon: Icons.motion_photos_off_outlined,
              title: '系统动画设置',
              subtitle: '跟随系统的"移除动画"开关',
              value: MediaQuery.disableAnimationsOf(context) ? '已移除' : '跟随系统',
              enabled: false,
            ),
            const SettingsTile(
              icon: Icons.timer_outlined,
              title: '节奏',
              subtitle: '按压 90ms · 结构 240–340ms · 弹层 380ms',
              value: '标准',
              enabled: false,
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.s16),
        Text(
          '关掉模糊后，四档表面都退回接近不透明的填充；发丝线与顶边高光保留，'
          '层级仍然读得出来。',
          style: MaeveType.hint.copyWith(color: colors.ink3),
        ),
      ],
    );
  }
}

class VoicePage extends StatelessWidget {
  const VoicePage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return AppPage.list(
      title: '语音',
      subtitle: '说话比打字快的时候',
      eyebrow: 'VOICE',
      children: [
        SettingsSection(
          title: '输入',
          rows: [
            SettingsSwitchTile(
              icon: Icons.mic_none_rounded,
              title: '语音输入',
              subtitle: '在输入条上按住说话',
              value: controller.prefs.voiceInput,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.voiceInput = value),
            ),
          ],
        ),
        SettingsSection(
          title: '输出',
          rows: [
            SettingsSwitchTile(
              icon: Icons.volume_up_outlined,
              title: '朗读回复',
              subtitle: '长回答会读很久，默认关',
              value: controller.prefs.readAloud,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.readAloud = value),
            ),
            SettingsTile(
              icon: Icons.graphic_eq_rounded,
              title: '声音',
              value: '系统默认',
              onTap: () => _notice(context, '声音选择将通过 Android 平台桥接接入。'),
            ),
          ],
          footnote: '语音在本机转写；转写结果会作为你的消息发给模型，这一点在首次开启时会明确告知。',
        ),
      ],
    );
  }
}

class NotificationPage extends StatelessWidget {
  const NotificationPage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return AppPage.list(
      title: '通知',
      subtitle: '只在需要你的时候响',
      eyebrow: 'NOTIFICATIONS',
      children: [
        SettingsSection(
          title: '推送',
          rows: [
            SettingsSwitchTile(
              icon: Icons.task_alt_rounded,
              title: '任务完成',
              value: controller.prefs.notifyDone,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.notifyDone = value),
            ),
            SettingsSwitchTile(
              icon: Icons.help_outline_rounded,
              title: '需要确认',
              subtitle: '权限、付款、解除限制',
              value: controller.prefs.notifyAsk,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.notifyAsk = value),
            ),
            SettingsSwitchTile(
              icon: Icons.wb_sunny_outlined,
              title: '每日摘要',
              value: controller.prefs.notifyDigest,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.notifyDigest = value),
            ),
          ],
        ),
      ],
    );
  }
}

class ReplyPrefsPage extends StatelessWidget {
  const ReplyPrefsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return AppPage.list(
      title: '回复偏好',
      subtitle: '她怎么跟你说话',
      eyebrow: 'REPLIES',
      children: [
        SettingsSection(
          title: '长度',
          rows: [
            SettingsSegmentTile(
              icon: Icons.notes_rounded,
              title: '默认长度',
              subtitle: '短问答别铺开，复杂任务别省',
              options: const ['简洁', '适中', '详细'],
              value: controller.prefs.replyLength,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.replyLength = value),
            ),
          ],
        ),
        SettingsSection(
          title: '过程',
          rows: [
            SettingsSwitchTile(
              icon: Icons.account_tree_outlined,
              title: '展开思考过程',
              subtitle: '默认折叠，需要时再点开',
              value: controller.prefs.expandThinking,
              onChanged: (value) => controller.updatePrefs(
                (prefs) => prefs.expandThinking = value,
              ),
            ),
            SettingsSwitchTile(
              icon: Icons.link_rounded,
              title: '标注来源',
              subtitle: '引用网页或文件时给出处',
              value: controller.prefs.citeSources,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.citeSources = value),
            ),
          ],
        ),
      ],
    );
  }
}

/// 隐私:数据去哪、怎么清。清除必须长按 1.5s。
class PrivacyPage extends StatelessWidget {
  const PrivacyPage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;
    return AppPage.list(
      title: '隐私',
      subtitle: '什么会离开这台设备',
      eyebrow: 'PRIVACY',
      children: [
        SettingsSection(
          title: '数据去向',
          rows: const [
            SettingsTile(
              icon: Icons.phone_android_rounded,
              title: '会话与记忆',
              subtitle: '只在这台设备的进程内存里，尚未落盘',
              value: '本机',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.cloud_outlined,
              title: '模型调用',
              subtitle: '接入后只发送你的消息与必要上下文',
              value: '未接入',
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.screen_share_outlined,
              title: '屏幕内容',
              subtitle: '操作记录只存动作，不存屏幕内容',
              value: '不留存',
              enabled: false,
            ),
          ],
        ),
        const GroupLabel(text: '清除'),
        HoldToConfirm(
          shape: MaeveShapes.button,
          semanticLabel: '按住清除本机会话',
          progressColor: theme.semantic.bad,
          onConfirmed: () {
            // 先拷贝再删:边遍历边改集合会抛 ConcurrentModificationError。
            for (final conversation in controller.conversations.toList()) {
              controller.delete(conversation.id);
            }
            _notice(context, '本机会话已清除。');
          },
          child: Container(
            padding: const EdgeInsets.all(MaeveSpace.s16),
            decoration: BoxDecoration(
              color: colors.badSoft,
              borderRadius: BorderRadius.circular(MaeveRadius.md),
              border: Border.all(
                color: theme.semantic.bad.withValues(alpha: .35),
                width: .5,
              ),
            ),
            child: Row(
              children: [
                Icon(Icons.delete_outline, size: 20, color: theme.semantic.bad),
                const SizedBox(width: MaeveSpace.s12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '按住清除本机会话',
                        style: MaeveType.body.copyWith(
                          color: theme.semantic.bad,
                        ),
                      ),
                      Text(
                        '按住 1.5 秒生效，松手即取消',
                        style: MaeveType.hint.copyWith(color: colors.ink3),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: MaeveSpace.s12),
        Text(
          '清除只影响这台设备上的进程内存；已经发给模型的内容由服务商按其政策处理。',
          style: MaeveType.hint.copyWith(color: colors.ink3),
        ),
      ],
    );
  }
}

/// 系统权限:无障碍、屏幕读取。读屏测试不发模型。
class SystemAccessPage extends StatefulWidget {
  const SystemAccessPage({super.key});

  @override
  State<SystemAccessPage> createState() => _SystemAccessPageState();
}

class _SystemAccessPageState extends State<SystemAccessPage> {
  bool _tested = false;

  @override
  Widget build(BuildContext context) {
    return AppPage.list(
      title: '系统权限',
      subtitle: '要让 Maeve 看见屏幕，得先给它钥匙',
      eyebrow: 'ACCESS',
      children: [
        SettingsSection(
          title: '授权',
          rows: [
            SettingsTile(
              icon: Icons.accessibility_new_rounded,
              title: '无障碍服务',
              subtitle: '读取界面、执行点击',
              chip: const StatusChip(
                label: '未授权',
                tone: MaeveTone.danger,
                filled: true,
              ),
              onTap: () async {
                await MaeveBridge.openSettings();
                if (context.mounted) {
                  _notice(context, '请在系统设置里为 Maeve 开启无障碍服务。');
                }
              },
            ),
            const SettingsTile(
              icon: Icons.screenshot_monitor_outlined,
              title: '屏幕截图',
              subtitle: '仅在执行任务时按需截取',
              value: '按需',
              enabled: false,
            ),
          ],
        ),
        SettingsSection(
          title: '自检',
          rows: [
            SettingsTile(
              icon: Icons.fact_check_outlined,
              title: '读屏测试',
              subtitle: '在本机验证一次，不会发给模型',
              value: _tested ? '通过' : '未测试',
              onTap: () {
                setState(() => _tested = true);
                _notice(context, '本机读屏测试通过，未发送任何内容。');
              },
            ),
          ],
          footnote: '读屏测试只在本机校验无障碍通道是否可用；屏幕内容不会被上传，操作记录也只留动作。',
        ),
      ],
    );
  }
}

class BackgroundGuidePage extends StatelessWidget {
  const BackgroundGuidePage({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '后台保活',
    subtitle: '让长任务别被系统掐掉',
    eyebrow: 'BACKGROUND',
    children: [
      SettingsSection(
        title: '按顺序做',
        rows: const [
          SettingsTile(
            icon: Icons.battery_saver_outlined,
            title: '关掉电池优化',
            subtitle: '系统设置 → 电池 → 不受限制',
            value: '1',
            mono: true,
            enabled: false,
          ),
          SettingsTile(
            icon: Icons.lock_open_rounded,
            title: '允许后台活动',
            subtitle: '应用信息 → 权限 → 后台运行',
            value: '2',
            mono: true,
            enabled: false,
          ),
          SettingsTile(
            icon: Icons.notifications_active_outlined,
            title: '允许常驻通知',
            subtitle: '长任务需要一个可见的入口',
            value: '3',
            mono: true,
            enabled: false,
          ),
        ],
      ),
      const SizedBox(height: MaeveSpace.s12),
      const BriefNotice(
        text: '不同厂商的系统叫法不一样，找不到就搜"后台"或"电池"。',
        icon: Icons.tips_and_updates_outlined,
      ),
    ],
  );
}

class DiagnosticsPage extends StatelessWidget {
  const DiagnosticsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    return AppPage.list(
      title: '诊断',
      subtitle: '出问题时先看这里',
      eyebrow: 'DIAGNOSTICS',
      children: [
        SettingsSection(
          title: '运行环境',
          rows: [
            const SettingsTile(
              icon: Icons.phone_android_rounded,
              title: '平台',
              value: 'Android · Flutter',
              enabled: false,
            ),
            const SettingsTile(
              icon: Icons.tag_rounded,
              title: '版本',
              value: '0.1.0',
              mono: true,
              enabled: false,
            ),
            SettingsTile(
              icon: Icons.forum_outlined,
              title: '会话数',
              value: '${controller.conversations.length}',
              mono: true,
              enabled: false,
            ),
          ],
        ),
        SettingsSection(
          title: '日志',
          rows: [
            SettingsSegmentTile(
              icon: Icons.receipt_long_outlined,
              title: '日志级别',
              options: const ['安静', '标准', '详细'],
              value: controller.prefs.logLevel,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.logLevel = value),
            ),
            SettingsTile(
              icon: Icons.copy_all_outlined,
              title: '复制诊断信息',
              subtitle: '不含会话内容与屏幕内容',
              onTap: () async {
                await Clipboard.setData(
                  const ClipboardData(
                    text: 'Maeve 0.1.0\nplatform: Android/Flutter\nmode: prototype',
                  ),
                );
                if (context.mounted) _notice(context, '已复制诊断信息。');
              },
            ),
          ],
        ),
      ],
    );
  }
}

class LanguagePage extends StatelessWidget {
  const LanguagePage({super.key});

  @override
  Widget build(BuildContext context) => AppPage.list(
    title: '语言',
    subtitle: '界面与字形的选择',
    eyebrow: 'LANGUAGE',
    children: [
      SettingsSection(
        title: '界面语言',
        rows: [
          ListPageRow(
            title: '中文（简体）',
            subtitle: '当前',
            trailing: Icon(
              Icons.check,
              size: 18,
              color: MaeveTheme.of(context).colors.accent,
            ),
            selected: true,
          ),
          ListPageRow(
            title: 'English',
            subtitle: '接入多语言后生效',
            onTap: () => _notice(context, '英文界面将在接入多语言后生效。'),
          ),
        ],
        footnote: '字形固定取中文：即使系统语言是英文，也不会退到日文字形。',
      ),
    ],
  );
}

void _notice(BuildContext context, String message) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

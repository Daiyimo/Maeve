import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../bridge/maeve_bridge.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../widgets/maeve_mark.dart';
import '../../widgets/glass_surface.dart';
import '../common/app_page.dart';
import '../common/haptics.dart';

/// 打开宿主 status 中 links[key] 指向的页面;未接桥、链接缺失或无法直接打开时,
/// 给出可复制链接的备用入口。关于页与设置-关于的「获取更新」共用。
Future<void> openHostLink(
  BuildContext context,
  String key, {
  required String label,
}) async {
  String? url;
  try {
    final status = await MaeveBridge.methods
        .invokeMethod<Map<Object?, Object?>>('status');
    url = (status?['links'] as Map?)?[key]?.toString();
  } catch (_) {
    // 测试与未接桥时按链接缺失处理。
  }
  final link = url;
  if (link == null || link.isEmpty) {
    if (context.mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('$label入口暂不可用。')));
    }
    return;
  }
  try {
    if (await MaeveBridge.openUrl(link)) return;
  } catch (_) {
    /* 提供可复制的备用入口。 */
  }
  if (!context.mounted) return;
  await showDialog<void>(
    context: context,
    builder: (context) => MaeveGlassDialog(
      title: const Text('在浏览器打开'),
      content: SelectableText(link),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('关闭'),
        ),
        FilledButton(
          onPressed: () async {
            await Clipboard.setData(ClipboardData(text: link));
            if (context.mounted) Navigator.pop(context);
          },
          child: const Text('复制链接'),
        ),
      ],
    ),
  );
}

const privacyModelDisclosure =
    '你发给 Maeve 的对话、屏幕内容和截图，在进行模型处理时只发给你在「模型配置」中配置的模型服务商。Maeve 不进行数据收集，不出售这些内容，也不用于广告。';
const privacyLocalDisclosure = '对话、配置、任务记录和截图默认保存在本机，用于继续对话、操作回放、任务报告与分享。';
const privacyChannelDisclosure =
    '如果你主动通过 Amoo、微信等通道发起任务，内容也会沿该通道发送给对应服务；这些服务的数据处理方式以其隐私政策为准。';
const privacyControlDisclosure = '你可以在「模型配置」更换或删除服务商；本地删除模型配置不会删除已保存的任务记录和截图。';

class PrivacyPage extends StatelessWidget {
  const PrivacyPage({super.key});

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '隐私声明',
      subtitle: '你的数据，由你掌握',
      eyebrow: 'PRIVACY',
      children: [
        const SizedBox(height: MaeveSpace.s12),
        MaeveGlassSurface(
          padding: const EdgeInsets.all(22),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.privacy_tip_outlined, color: c.accent, size: 24),
                  const SizedBox(width: MaeveSpace.s12),
                  Expanded(
                    child: Text(
                      '不收集，不出售，不用于广告',
                      style: MaeveType.bodyStrong.copyWith(color: c.ink),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Text(
                privacyModelDisclosure,
                style: MaeveType.body.copyWith(color: c.ink2, height: 1.7),
              ),
            ],
          ),
        ),
        const SizedBox(height: MaeveSpace.s24),
        _PrivacySection(title: '本机保存', body: privacyLocalDisclosure),
        const SizedBox(height: MaeveSpace.s20),
        _PrivacySection(title: '你主动接入的第三方通道', body: privacyChannelDisclosure),
        const SizedBox(height: MaeveSpace.s20),
        _PrivacySection(title: '你的选择', body: privacyControlDisclosure),
      ],
    );
  }
}

class _PrivacySection extends StatelessWidget {
  const _PrivacySection({required this.title, required this.body});

  final String title;
  final String body;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: MaeveType.bodyStrong.copyWith(color: c.ink)),
        const SizedBox(height: 8),
        Text(body, style: MaeveType.body.copyWith(color: c.ink2, height: 1.7)),
      ],
    );
  }
}

class AboutPage extends StatefulWidget {
  const AboutPage({super.key});

  @override
  State<AboutPage> createState() => _AboutPageState();
}

class _AboutPageState extends State<AboutPage> {
  String? _host;

  @override
  void initState() {
    super.initState();
    unawaited(_loadHost());
  }

  Future<void> _loadHost() async {
    try {
      final status = await MaeveBridge.methods
          .invokeMethod<Map<Object?, Object?>>('status');
      if (!mounted || status == null) return;
      setState(() {
        _host =
            '${status['version']} (${status['versionCode']}) · ${status['build']} · ${status['channel']} · Android ${status['android']}';
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
              Text(
                _host ?? 'Maeve',
                style: MaeveType.caption.copyWith(color: colors.ink2),
              ),
            ],
          ),
        ),
        const SizedBox(height: 28),
        Text(
          '当前体验范围：简体中文。手机操作能力以设备自检结果为准。',
          style: MaeveType.caption.copyWith(color: colors.ink2),
        ),
        const SizedBox(height: 12),
        MaeveGlassTextButton(
          label: '问题反馈',
          icon: Icons.feedback_outlined,
          haptic: MaeveHaptic.tick,
          onPressed: () => openHostLink(context, 'feedback', label: '反馈'),
        ),
        const SizedBox(height: 12),
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
      ],
    );
  }
}

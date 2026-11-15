import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../bridge/maeve_bridge.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../widgets/maeve_mark.dart';
import '../../widgets/glass_surface.dart';
import '../common/app_page.dart';

class AboutPage extends StatefulWidget {
  const AboutPage({super.key});

  @override
  State<AboutPage> createState() => _AboutPageState();
}

class _AboutPageState extends State<AboutPage> {
  String? _host;
  Map<String, String> _links = const {};

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
        _links = Map<String, String>.from(status['links'] as Map? ?? const {});
        _host =
            '${status['version']} (${status['versionCode']}) · ${status['build']} · ${status['channel']} · Android ${status['android']}';
      });
    } catch (_) {
      // 测试与未接桥时静默:关于页不因为没有宿主就报错。
    }
  }

  Future<void> _openSupport(String key) async {
    final url = _links[key];
    if (url == null) return;
    try {
      if (await MaeveBridge.openUrl(url)) return;
    } catch (_) {
      /* 提供可复制的备用入口。 */
    }
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('在浏览器打开'),
        content: SelectableText(url),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('关闭'),
          ),
          FilledButton(
            onPressed: () async {
              await Clipboard.setData(ClipboardData(text: url));
              if (context.mounted) Navigator.pop(context);
            },
            child: const Text('复制链接'),
          ),
        ],
      ),
    );
  }

  Future<void> _basicCheck() async {
    try {
      final report = await MaeveBridge.methods.invokeMapMethod<String, dynamic>(
        'basicSelfCheck',
      );
      if (!mounted || report == null) return;
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('设备与权限检查'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('仅检查设备信息与系统授权。虚拟屏幕、实际操作、浏览器和沙箱尚未验证；不会发送消息或更改系统设置。'),
                const SizedBox(height: 16),
                for (final row in report['rows'] as List? ?? [])
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${row['label']}：${row['status'] == 'pass' ? '通过' : '需检查'}',
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                        if (row['advice'] != null)
                          Text(row['advice'] as String),
                      ],
                    ),
                  ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('关闭'),
            ),
          ],
        ),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('设备状态暂不可读，请重试。')));
      }
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
        Wrap(
          spacing: 12,
          runSpacing: 8,
          children: [
            OutlinedButton.icon(
              onPressed: _links.containsKey('updates')
                  ? () => _openSupport('updates')
                  : null,
              icon: const Icon(Icons.system_update_alt_rounded),
              label: const Text('获取更新'),
            ),
            OutlinedButton.icon(
              onPressed: _links.containsKey('feedback')
                  ? () => _openSupport('feedback')
                  : null,
              icon: const Icon(Icons.feedback_outlined),
              label: const Text('问题反馈'),
            ),
          ],
        ),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: _basicCheck,
          icon: const Icon(Icons.verified_outlined),
          label: const Text('设备与权限检查'),
        ),
        OutlinedButton.icon(
          icon: const Icon(Icons.fact_check_outlined),
          label: const Text('预览本地诊断'),
          onPressed: () async {
            try {
              final text = await MaeveBridge.methods.invokeMethod<String>(
                'diagnostics',
              );
              if (!context.mounted || text == null) return;
              await showDialog<void>(
                context: context,
                builder: (context) => AlertDialog(
                  title: const Text('本地诊断预览'),
                  content: SingleChildScrollView(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          '仅包含版本、构建和能力状态，不包含对话、截图、模型地址、账号或密钥。复制后由你决定分享给谁，不会自动上传。',
                        ),
                        const SizedBox(height: 12),
                        SelectableText(text),
                      ],
                    ),
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: const Text('关闭'),
                    ),
                    FilledButton(
                      onPressed: () async {
                        await Clipboard.setData(ClipboardData(text: text));
                        if (context.mounted) Navigator.pop(context);
                      },
                      child: const Text('复制诊断'),
                    ),
                  ],
                ),
              );
            } catch (_) {
              if (context.mounted) {
                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(const SnackBar(content: Text('诊断暂时不可用，请重试。')));
              }
            }
          },
        ),
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

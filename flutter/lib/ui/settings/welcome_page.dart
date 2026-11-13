import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../widgets/maeve_mark.dart';
import '../../widgets/glass_surface.dart';
import '../common/app_page.dart';
import 'model_settings_page.dart';
import 'system_access_page.dart';

class WelcomePage extends StatefulWidget {
  const WelcomePage({super.key, this.firstRun = false});
  final bool firstRun;
  @override
  State<WelcomePage> createState() => _WelcomePageState();
}

class _WelcomePageState extends State<WelcomePage> {
  bool _saving = false;
  String? _error;
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '认识 Maeve',
      subtitle: '把想做的事，变成下一步行动',
      children: [
        const SizedBox(height: 28),
        const Center(child: MaeveEmblem(size: 96)),
        const SizedBox(height: 32),
        MaeveGlassSurface(
          padding: const EdgeInsets.all(22),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('下一幕，由你决定。', style: MaeveType.title.copyWith(color: c.ink)),
              const SizedBox(height: 16),
              Text(
                '接入模型，连接手机，再用一句话告诉 Maeve 你的目标。执行的每一步，都可以在记录里查看。',
                style: MaeveType.body.copyWith(color: c.ink2, height: 1.7),
              ),
              const SizedBox(height: 24),
              OutlinedButton.icon(
                onPressed: () =>
                    AppPage.open(context, const ModelSettingsPage()),
                icon: const Icon(Icons.auto_awesome_outlined),
                label: const Text('配置模型'),
              ),
              const SizedBox(height: 10),
              OutlinedButton.icon(
                onPressed: () =>
                    AppPage.open(context, const SystemAccessPage()),
                icon: const Icon(Icons.phone_android_rounded),
                label: const Text('连接手机助手'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text('关于你的数据', style: MaeveType.bodyStrong.copyWith(color: c.ink)),
        const SizedBox(height: 10),
        Text(
          '对话与配置保存在设备上。执行任务时，必要的内容会交给你配置的模型服务。',
          style: MaeveType.caption.copyWith(color: c.ink2, height: 1.7),
        ),
        const SizedBox(height: 28),
        if (_error != null)
          Text(_error!, style: MaeveType.caption.copyWith(color: c.bad)),
        if (widget.firstRun)
          FilledButton(
            onPressed: _saving
                ? null
                : () async {
                    setState(() {
                      _saving = true;
                      _error = null;
                    });
                    try {
                      await MaeveScope.read(context).completeOnboarding();
                    } catch (_) {
                      if (mounted) setState(() => _error = '暂时未能保存，请重试。');
                    } finally {
                      if (mounted) setState(() => _saving = false);
                    }
                  },
            child: Text(_saving ? '正在准备…' : '开始使用'),
          ),
        const SizedBox(height: 24),
      ],
    );
  }
}

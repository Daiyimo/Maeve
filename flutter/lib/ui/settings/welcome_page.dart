import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../../widgets/maeve_mark.dart';
import '../../widgets/glass_surface.dart';
import '../common/app_page.dart';
import '../common/haptics.dart';
import 'model_settings_page.dart';
import 'phone_readiness_page.dart';
import 'about_pages.dart';

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
              MaeveGlassTextButton(
                label: '配置模型',
                icon: Icons.auto_awesome_outlined,
                haptic: MaeveHaptic.tick,
                onPressed: () =>
                    AppPage.open(context, const ModelSettingsPage()),
              ),
              const SizedBox(height: 10),
              MaeveGlassTextButton(
                label: '连接手机助手',
                icon: Icons.phone_android_rounded,
                haptic: MaeveHaptic.tick,
                onPressed: () =>
                    AppPage.open(context, const PhoneReadinessPage()),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Text('关于你的数据', style: MaeveType.bodyStrong.copyWith(color: c.ink)),
        const SizedBox(height: 10),
        Text(
          privacyModelDisclosure,
          style: MaeveType.caption.copyWith(color: c.ink2, height: 1.7),
        ),
        const SizedBox(height: 10),
        MaeveGlassTextButton(
          label: '查看隐私声明',
          icon: Icons.privacy_tip_outlined,
          haptic: MaeveHaptic.tick,
          onPressed: () => AppPage.open(context, const PrivacyPage()),
        ),
        const SizedBox(height: 28),
        if (_error != null)
          Text(_error!, style: MaeveType.caption.copyWith(color: c.bad)),
        if (widget.firstRun)
          MaeveGlassTextButton(
            label: _saving ? '正在准备…' : '开始使用',
            prominent: true,
            enabled: !_saving,
            haptic: MaeveHaptic.light,
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
          ),
        const SizedBox(height: 24),
      ],
    );
  }
}

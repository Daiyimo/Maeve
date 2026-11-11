import 'package:flutter/material.dart';

import '../bridge/maeve_bridge.dart';

import '../widgets/maeve_mark.dart';

import 'maeve_controller.dart';

import '../design/maeve_accent.dart';
import '../design/maeve_motion.dart';
import '../design/maeve_shapes.dart';
import '../design/maeve_surfaces.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

import '../ui/common/pressable.dart';
import '../ui/common/status.dart';

/// 首启引导:欢迎 → 运行时 → 外观 → 权限 → 密钥 → 完成。
///
/// 步骤条只数**要回答的问题**(4 个:运行时 / 外观 / 屏幕权限 / 密钥),
/// 欢迎与完成不计;后三个都可以"稍后",不挡人进门。
class OnboardingPage extends StatefulWidget {
  const OnboardingPage({super.key});

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

enum _Step { welcome, runtime, appearance, access, apiKey, finishing }

class _OnboardingPageState extends State<OnboardingPage> {
  _Step _step = _Step.welcome;
  bool _remote = false;
  bool _appearanceDone = false;
  bool _accessDone = false;
  String? _key;
  bool _keyAnswered = false;
  final TextEditingController _keyInput = TextEditingController();

  static const _questions = 4;

  /// 运行时永远有答案(默认本机);其余三个答过才算。
  int get _answered =>
      1 + [_appearanceDone, _accessDone, _keyAnswered].where((d) => d).length;

  bool get _counting =>
      _step == _Step.runtime ||
      _step == _Step.appearance ||
      _step == _Step.access ||
      _step == _Step.apiKey;

  @override
  void dispose() {
    _keyInput.dispose();
    super.dispose();
  }

  void _next() {
    final order = _Step.values;
    final index = order.indexOf(_step);
    if (index == order.length - 1) {
      Navigator.of(context).maybePop();
      return;
    }
    setState(() => _step = order[index + 1]);
  }

  void _back() {
    final order = _Step.values;
    final index = order.indexOf(_step);
    if (index == 0) {
      Navigator.of(context).maybePop();
      return;
    }
    setState(() => _step = order[index - 1]);
  }

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Stack(
        children: [
          const Positioned.fill(child: MaeveBackdrop()),
          SafeArea(
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                    MaeveSpace.s8,
                    MaeveSpace.s8,
                    MaeveSpace.s16,
                    0,
                  ),
                  child: Row(
                    children: [
                      if (_step != _Step.welcome)
                        Pressable(
                          onTap: _back,
                          borderRadius: BorderRadius.circular(MaeveRadius.pill),
                          semanticLabel: '上一步',
                          child: SizedBox.square(
                            dimension: 44,
                            child: Icon(
                              Icons.arrow_back_ios_new_rounded,
                              size: 16,
                              color: colors.ink,
                            ),
                          ),
                        ),
                      const Spacer(),
                      if (_counting)
                        Text(
                          '$_answered / $_questions',
                          style: MaeveType.mono.copyWith(color: colors.ink3),
                        ),
                    ],
                  ),
                ),
                Expanded(
                  child: AnimatedSwitcher(
                    duration: AppMotion.of(context).page,
                    switchInCurve: MaeveMotion.decelerate,
                    switchOutCurve: Curves.easeIn,
                    transitionBuilder: (child, animation) => FadeTransition(
                      opacity: animation,
                      child: SlideTransition(
                        position: Tween<Offset>(
                          begin: const Offset(.04, 0),
                          end: Offset.zero,
                        ).animate(animation),
                        child: child,
                      ),
                    ),
                    child: KeyedSubtree(
                      key: ValueKey(_step),
                      child: _body(context),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                    MaeveSpace.s16,
                    MaeveSpace.s8,
                    MaeveSpace.s16,
                    MaeveSpace.s20,
                  ),
                  child: _footer(context),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _body(BuildContext context) {
    final content = switch (_step) {
      _Step.welcome => _WelcomeStep(),
      _Step.runtime => _RuntimeStep(
        remote: _remote,
        onPick: (remote) => setState(() => _remote = remote),
      ),
      _Step.appearance => _AppearanceStep(
        onDone: () => setState(() => _appearanceDone = true),
      ),
      _Step.access => _AccessStep(
        onDone: () => setState(() => _accessDone = true),
      ),
      _Step.apiKey => _ApiKeyStep(
        controller: _keyInput,
        onDone: (value) => setState(() {
          _key = value;
          _keyAnswered = true;
        }),
      ),
      _Step.finishing => _FinishingStep(keyProvided: (_key ?? '').isNotEmpty),
    };
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.s16,
        MaeveSpace.s24,
        MaeveSpace.s16,
        MaeveSpace.s20,
      ),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: content,
        ),
      ),
    );
  }

  Widget _footer(BuildContext context) {
    final controller = MaeveScope.of(context);
    final isLast = _step == _Step.finishing;
    final label = switch (_step) {
      _Step.welcome => '开始',
      _Step.finishing => '进入 Maeve',
      _ => '继续',
    };
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: Row(
          children: [
            if (_counting)
              TextButton(onPressed: _next, child: const Text('稍后')),
            const Spacer(),
            FilledButton(
              onPressed: () {
                if (isLast) {
                  controller.create();
                  Navigator.of(context).maybePop();
                  return;
                }
                _next();
              },
              child: Text(label),
            ),
          ],
        ),
      ),
    );
  }
}

class _StepScaffold extends StatelessWidget {
  const _StepScaffold({
    required this.eyebrow,
    required this.title,
    required this.body,
    this.child,
  });

  final String eyebrow;
  final String title;
  final String body;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              width: 4,
              height: 4,
              decoration: BoxDecoration(
                color: colors.accent,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 5),
            Text(
              eyebrow,
              style: MaeveType.eyebrowFor(eyebrow).copyWith(color: colors.ink3),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.s8),
        Text(title, style: MaeveType.largeTitle.copyWith(color: colors.ink)),
        const SizedBox(height: MaeveSpace.s8),
        Text(body, style: MaeveType.body.copyWith(color: colors.ink2)),
        if (child != null) ...[const SizedBox(height: MaeveSpace.s20), child!],
      ],
    );
  }
}

class _WelcomeStep extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 112,
          height: 112,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient: RadialGradient(
              colors: [
                colors.accentSoft,
                colors.accentSoft.withValues(alpha: 0),
              ],
            ),
          ),
          child: MaeveMark(
            size: 56,
            state: MarkState.armed,
            color: colors.accent,
          ),
        ),
        const SizedBox(height: MaeveSpace.s24),
        Text(
          'SHE RUNS THE LOOP',
          style: MaeveType.eyebrow.copyWith(color: colors.ink3),
        ),
        const SizedBox(height: MaeveSpace.s10),
        Text(
          '一个念头，\n从这里开始。',
          style: MaeveType.display.copyWith(
            fontSize: 36,
            height: 42 / 36,
            color: colors.ink,
          ),
        ),
        const SizedBox(height: MaeveSpace.s16),
        Text(
          'Maeve 住在你的设备里：她在另一块屏上把事办完，你的主屏始终是你的。'
          '接下来四个问题，决定她默认怎么工作。',
          style: MaeveType.body.copyWith(color: colors.ink2),
        ),
        const SizedBox(height: MaeveSpace.s24),
        const BriefNotice(
          text: '不登录也能用完整功能；登录只是为了同步。',
          icon: Icons.lock_outline_rounded,
        ),
      ],
    );
  }
}

class _RuntimeStep extends StatelessWidget {
  const _RuntimeStep({required this.remote, required this.onPick});

  final bool remote;
  final ValueChanged<bool> onPick;

  @override
  Widget build(BuildContext context) => _StepScaffold(
    eyebrow: '问题 1',
    title: '让它在哪儿跑',
    body: '本机最省心；远程适合长任务和重活。之后随时能改。',
    child: Column(
      children: [
        _OptionCard(
          icon: Icons.phone_android_rounded,
          title: '本机',
          subtitle: '在这台 Android 设备上，离线也能用',
          selected: !remote,
          onTap: () => onPick(false),
        ),
        const SizedBox(height: MaeveSpace.s8),
        _OptionCard(
          icon: Icons.dns_outlined,
          title: '远程主机',
          subtitle: '需要先在电脑上配对',
          selected: remote,
          onTap: () => onPick(true),
        ),
      ],
    ),
  );
}

class _OptionCard extends StatelessWidget {
  const _OptionCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.selected,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      child: AnimatedContainer(
        duration: AppMotion.of(context).quick,
        padding: const EdgeInsets.all(MaeveSpace.s12),
        decoration: BoxDecoration(
          color: selected ? colors.accentSoft : colors.surface,
          borderRadius: BorderRadius.circular(MaeveRadius.md),
          border: Border.all(
            color: selected ? colors.accent : colors.edge,
            width: selected ? 1 : .5,
          ),
        ),
        child: Row(
          children: [
            Icon(icon, size: 20, color: selected ? colors.accent : colors.ink3),
            const SizedBox(width: MaeveSpace.s12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: MaeveType.bodyStrong.copyWith(color: colors.ink),
                  ),
                  Text(
                    subtitle,
                    style: MaeveType.caption.copyWith(color: colors.ink3),
                  ),
                ],
              ),
            ),
            if (selected)
              Icon(Icons.check_circle, size: 18, color: colors.accent),
          ],
        ),
      ),
    );
  }
}

class _AppearanceStep extends StatelessWidget {
  const _AppearanceStep({required this.onDone});

  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final colors = MaeveTheme.of(context).colors;
    return _StepScaffold(
      eyebrow: '问题 2',
      title: '看起来像什么',
      body: '六套配色，来自甜水镇的意象。选中的这套现在就会用上。',
      child: Column(
        children: [
          Wrap(
            spacing: MaeveSpace.s8,
            runSpacing: MaeveSpace.s8,
            children: [
              for (final preset in MaeveAccentPreset.values)
                Pressable(
                  onTap: () {
                    controller.setAccent(preset);
                    onDone();
                  },
                  borderRadius: BorderRadius.circular(MaeveRadius.pill),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: MaeveSpace.s12,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: controller.accentPreset == preset
                          ? colors.accentSoft
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(MaeveRadius.pill),
                      border: Border.all(color: colors.edge, width: .5),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 12,
                          height: 12,
                          decoration: BoxDecoration(
                            color: deriveAccent(preset).lightSeed,
                            shape: BoxShape.circle,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          preset.label,
                          style: MaeveType.label.copyWith(
                            fontSize: 12.5,
                            color: colors.ink,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: MaeveSpace.s12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () {
                    controller.setMode(ThemeMode.light);
                    onDone();
                  },
                  child: const Text('浅色'),
                ),
              ),
              const SizedBox(width: MaeveSpace.s8),
              Expanded(
                child: OutlinedButton(
                  onPressed: () {
                    controller.setMode(ThemeMode.dark);
                    onDone();
                  },
                  child: const Text('深色'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _AccessStep extends StatelessWidget {
  const _AccessStep({required this.onDone});

  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) => _StepScaffold(
    eyebrow: '问题 3',
    title: '要不要让它看见屏幕',
    body: '看见了才能替你在应用里点。不给也能用，只是她只能动嘴。',
    child: Column(
      children: [
        _OptionCard(
          icon: Icons.visibility_outlined,
          title: '去开启无障碍',
          subtitle: '在系统设置里为 Maeve 打开，随时可以关',
          selected: false,
          onTap: () async {
            await MaeveBridge.openSettings();
            onDone();
          },
        ),
        const SizedBox(height: MaeveSpace.s8),
        _OptionCard(
          icon: Icons.schedule_rounded,
          title: '稍后再说',
          subtitle: '之后在「设置 → 能力」里开',
          selected: true,
          onTap: onDone,
        ),
        const SizedBox(height: MaeveSpace.s12),
        const BriefNotice(
          text: '密码页、支付页始终交还给你；读屏测试不会发给模型。',
          icon: Icons.shield_outlined,
        ),
      ],
    ),
  );
}

class _ApiKeyStep extends StatelessWidget {
  const _ApiKeyStep({required this.controller, required this.onDone});

  final TextEditingController controller;
  final ValueChanged<String?> onDone;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return _StepScaffold(
      eyebrow: '问题 4 · 可选',
      title: '用自己的密钥',
      body: '不填也行：内置引擎够日常用。密钥只存在本机加密存储里。',
      child: Column(
        children: [
          TextField(
            controller: controller,
            obscureText: true,
            style: MaeveType.body.copyWith(color: colors.ink),
            decoration: InputDecoration(
              hintText: 'sk-...',
              filled: true,
              fillColor: colors.surface,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(MaeveRadius.md),
                borderSide: BorderSide.none,
              ),
            ),
          ),
          const SizedBox(height: MaeveSpace.s12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => onDone(null),
                  child: const Text('跳过'),
                ),
              ),
              const SizedBox(width: MaeveSpace.s8),
              Expanded(
                child: FilledButton(
                  onPressed: () => onDone(controller.text.trim()),
                  child: const Text('保存'),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _FinishingStep extends StatelessWidget {
  const _FinishingStep({required this.keyProvided});

  final bool keyProvided;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const MaeveMark(size: 60, state: MarkState.armed),
        const SizedBox(height: MaeveSpace.s20),
        Text('准备好了', style: MaeveType.display.copyWith(color: colors.ink)),
        const SizedBox(height: MaeveSpace.s10),
        Text(
          '说第一句话就行。之后想改任何设置，从左边的列表进设置。',
          style: MaeveType.body.copyWith(color: colors.ink2),
        ),
        const SizedBox(height: MaeveSpace.s20),
        BriefNotice(
          text: keyProvided ? '已记下你的密钥（仅本次会话，尚未写入磁盘）。' : '使用内置引擎，不需要密钥。',
          icon: Icons.vpn_key_outlined,
        ),
        const SizedBox(height: MaeveSpace.s8),
        const BriefNotice(
          text: '这个版本是界面原型：回复是本机示范，尚未接入真实模型。',
          icon: Icons.info_outline,
        ),
      ],
    );
  }
}

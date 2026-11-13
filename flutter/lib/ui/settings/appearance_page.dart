import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_accent.dart';
import '../../design/maeve_colors.dart';
import '../../design/maeve_motion.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/app_page.dart';
import '../common/haptics.dart';
import '../common/pressable.dart';
import 'settings_components.dart';

/// 外观:六套配色、主题模式、图标风格,带一块实时预览。
///
/// 预览用的是**正在选的那套**从 OKLCH 现推的颜色(不是预制截图)——
/// 所以在哪一档上看到的对比度,就是切过去之后的对比度。
class AppearancePage extends StatelessWidget {
  const AppearancePage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final colors = MaeveTheme.of(context).colors;
    return AppPage.list(
      title: '外观',
      subtitle: '选择适合你的色彩与明暗',
      eyebrow: 'APPEARANCE',
      children: [
        SettingsSection(
          title: '明暗',
          rows: [
            SettingsSegmentTile(
              icon: Icons.brightness_6_outlined,
              title: '主题模式',
              subtitle: '选择清透的浅色、安静的深色，或跟随系统。',
              options: const ['深色', '浅色', '跟随系统'],
              value: switch (controller.mode) {
                ThemeMode.dark => 0,
                ThemeMode.light => 1,
                ThemeMode.system => 2,
              },
              onChanged: (value) => controller.setMode(switch (value) {
                0 => ThemeMode.dark,
                1 => ThemeMode.light,
                _ => ThemeMode.system,
              }),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.s8),
        const GroupLabel(text: '配色'),
        MaeveAccentGrid(
          selected: controller.accentPreset,
          brightness: Theme.of(context).brightness,
          onSelected: controller.setAccent,
        ),
        const SizedBox(height: MaeveSpace.s8),
        const GroupLabel(text: '预览'),
        _Preview(
          accent: deriveAccent(controller.accentPreset),
          brightness: Theme.of(context).brightness,
        ),
        const SizedBox(height: MaeveSpace.s10),
        Text(
          '色彩会一起改变背景的光、玻璃的温度与细节。选一个此刻喜欢的氛围。',
          style: MaeveType.hint.copyWith(color: colors.ink3),
        ),
        const SizedBox(height: MaeveSpace.s24),
      ],
    );
  }
}

/// 六套配色:两/三列色卡,选中是强调色描边 + 勾。
class MaeveAccentGrid extends StatelessWidget {
  const MaeveAccentGrid({
    super.key,
    required this.selected,
    required this.onSelected,
    required this.brightness,
  });

  final MaeveAccentPreset selected;
  final ValueChanged<MaeveAccentPreset> onSelected;
  final Brightness brightness;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, box) {
        const gap = MaeveSpace.s8;
        final columns = box.maxWidth >= 520 ? 3 : 2;
        final width = (box.maxWidth - gap * (columns - 1)) / columns;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (final preset in MaeveAccentPreset.values)
              SizedBox(
                width: width,
                child: _PresetCard(
                  preset: preset,
                  selected: preset == selected,
                  brightness: brightness,
                  onTap: () => onSelected(preset),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _PresetCard extends StatelessWidget {
  const _PresetCard({
    required this.preset,
    required this.selected,
    required this.brightness,
    required this.onTap,
  });

  final MaeveAccentPreset preset;
  final bool selected;
  final Brightness brightness;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final accent = deriveAccent(preset);
    final dark = brightness == Brightness.dark;
    final seed = dark ? accent.darkSeed : accent.lightSeed;
    final bubble = dark ? accent.darkBubble : accent.lightBubble;
    final glow = dark ? accent.darkGlow : accent.lightGlow;
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      haptic: MaeveHaptic.light,
      child: AnimatedContainer(
        duration: AppMotion.of(context).quick,
        padding: const EdgeInsets.all(MaeveSpace.s12),
        decoration: BoxDecoration(
          // 选中只加一点点强调色洗染 + 一道亮边:六张色卡里出现一整块淡紫实底,
          // 会让"当前配色"看起来像一个新按钮。
          color: selected
              ? colors.accent.withValues(alpha: .09)
              : colors.surfaceHigh,
          borderRadius: BorderRadius.circular(MaeveRadius.md),
          border: Border.all(
            color: selected ? seed : colors.edge,
            width: selected ? 1.6 : .5,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                _Swatch(color: seed),
                const SizedBox(width: 4),
                _Swatch(color: bubble),
                const SizedBox(width: 4),
                _Swatch(color: glow),
                const Spacer(),
                if (selected)
                  Icon(Icons.check_circle_rounded, size: 16, color: seed),
              ],
            ),
            const SizedBox(height: MaeveSpace.s10),
            Text(
              preset.label,
              style: MaeveType.bodyStrong.copyWith(color: colors.ink),
            ),
            const SizedBox(height: 1),
            // 第二行是**这枚种子的读数**,不是又一句"轻点切换"。
            // 六张卡各写一遍"轻点切换",等于什么都没说;色值至少能对比。
            Text(
              selected
                  ? '正在使用'
                  : switch (preset) {
                      MaeveAccentPreset.reverie => '清醒而自由',
                      MaeveAccentPreset.dolores => '柔软的力量',
                      MaeveAccentPreset.maze => '安静生长',
                      MaeveAccentPreset.pearl => '清透与轻盈',
                      MaeveAccentPreset.sublime => '夜色渐深',
                      MaeveAccentPreset.graphite => '光的本色',
                    },
              style: MaeveType.monoSmall.copyWith(
                fontSize: 10.5,
                color: selected ? colors.accent : colors.ink3,
                letterSpacing: .3,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Swatch extends StatelessWidget {
  const _Swatch({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    width: 16,
    height: 16,
    decoration: BoxDecoration(
      color: color,
      shape: BoxShape.circle,
      border: Border.all(color: Colors.black.withValues(alpha: .08), width: .5),
    ),
  );
}

/// 迷你预览:一条你的气泡 + 一条回答(竖线)+ 一枚主按钮。
class _Preview extends StatelessWidget {
  const _Preview({required this.accent, required this.brightness});

  final MaeveAccent accent;
  final Brightness brightness;

  @override
  Widget build(BuildContext context) {
    final colors = brightness == Brightness.dark
        ? MaeveColors.dark(accent)
        : MaeveColors.light(accent);
    return Container(
      padding: const EdgeInsets.all(MaeveSpace.s16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [colors.bgTop, colors.bgBottom],
        ),
        borderRadius: BorderRadius.circular(MaeveRadius.lg),
        border: Border.all(color: colors.edge, width: .5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: colors.surfaceHigh,
                borderRadius: const BorderRadius.only(
                  topLeft: Radius.circular(MaeveRadius.lg),
                  topRight: Radius.circular(MaeveRadius.lg),
                  bottomRight: Radius.circular(MaeveRadius.xs),
                  bottomLeft: Radius.circular(MaeveRadius.lg),
                ),
                border: Border.all(color: colors.edge, width: .5),
              ),
              child: Text(
                '帮我把这件事理清楚',
                style: MaeveType.body.copyWith(color: colors.ink),
              ),
            ),
          ),
          const SizedBox(height: MaeveSpace.s12),
          Container(
            decoration: BoxDecoration(
              border: Border(left: BorderSide(color: colors.rule, width: 1)),
            ),
            padding: const EdgeInsets.only(left: 15),
            child: Text(
              '先说结论，再拆成三步。',
              style: MaeveType.script.copyWith(color: colors.ink),
            ),
          ),
          const SizedBox(height: MaeveSpace.s12),
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: MaeveSpace.s16,
                  vertical: 8,
                ),
                // 和真实的 FilledButton 一致(墨底):预览必须说真话 ——
                // 按强调色画一个"假主按钮",切过去就会发现不是那样。
                decoration: BoxDecoration(
                  color: colors.accent,
                  borderRadius: BorderRadius.circular(MaeveRadius.md),
                ),
                child: Text(
                  '继续',
                  style: MaeveType.bodyStrong.copyWith(
                    fontSize: 14.5,
                    color: colors.onAccent,
                  ),
                ),
              ),
              const SizedBox(width: MaeveSpace.s8),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: MaeveSpace.s16,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: colors.fill,
                  borderRadius: BorderRadius.circular(MaeveRadius.md),
                  border: Border.all(color: colors.edge, width: .5),
                ),
                child: Text(
                  '换个方向',
                  style: MaeveType.bodyStrong.copyWith(
                    fontSize: 14.5,
                    color: colors.ink,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

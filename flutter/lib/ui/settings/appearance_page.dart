import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_palette.dart';
import '../../design/maeve_tokens.dart';
import '../common/app_page.dart';
import '../common/haptics.dart';
import '../common/pressable.dart';
import 'settings_components.dart';

/// B9 外观:六套配色、主题模式、图标风格,带一块实时预览。
class AppearancePage extends StatelessWidget {
  const AppearancePage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final scheme = Theme.of(context).colorScheme;
    return AppPage.list(
      title: '外观',
      subtitle: '配色会同时换掉背景、光晕与气泡',
      children: [
        SettingsSection(
          title: '主题模式',
          rows: [
            SettingsSegmentTile(
              icon: Icons.brightness_6_outlined,
              title: '明暗',
              subtitle: '深色更省电，也更安静',
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
        const SizedBox(height: MaeveSpace.xl),
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 0, 4, MaeveSpace.sm),
          child: Text('配色', style: maeveEyebrow(context)),
        ),
        MaevePaletteGrid(
          selected: controller.palette,
          onSelected: controller.setPalette,
        ),
        const SizedBox(height: MaeveSpace.xl),
        SettingsSection(
          title: '图标',
          rows: [
            SettingsSegmentTile(
              icon: Icons.interests_outlined,
              title: '图标风格',
              subtitle: '单色更克制，圆润更柔和',
              options: const ['圆润', '单色'],
              value: controller.prefs.iconStyle,
              onChanged: (value) =>
                  controller.updatePrefs((prefs) => prefs.iconStyle = value),
            ),
          ],
        ),
        const SizedBox(height: MaeveSpace.xl),
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 0, 4, MaeveSpace.sm),
          child: Text('预览', style: maeveEyebrow(context)),
        ),
        _Preview(
          palette: controller.palette,
          brightness: Theme.of(context).brightness,
        ),
        const SizedBox(height: MaeveSpace.md),
        Text(
          '换配色只换色相与光晕，文字对比度保持同一档 —— 所以每一套都同样好读。',
          style: Theme.of(context).textTheme.bodySmall
              ?.copyWith(color: scheme.onSurfaceVariant),
        ),
        const SizedBox(height: MaeveSpace.xxl),
      ],
    );
  }
}

/// 六套配色:3 列色卡,选中是主色描边 + 勾。
class MaevePaletteGrid extends StatelessWidget {
  const MaevePaletteGrid({
    super.key,
    required this.selected,
    required this.onSelected,
  });

  final MaevePalette selected;
  final ValueChanged<MaevePalette> onSelected;

  @override
  Widget build(BuildContext context) {
    final brightness = Theme.of(context).brightness;
    return LayoutBuilder(
      builder: (context, box) {
        const gap = MaeveSpace.md;
        final columns = box.maxWidth >= 520 ? 3 : 2;
        final width = (box.maxWidth - gap * (columns - 1)) / columns;
        return Wrap(
          spacing: gap,
          runSpacing: gap,
          children: [
            for (final palette in MaevePalette.all)
              SizedBox(
                width: width,
                child: _PaletteCard(
                  palette: palette,
                  selected: palette.id == selected.id,
                  brightness: brightness,
                  onTap: () => onSelected(palette),
                ),
              ),
          ],
        );
      },
    );
  }
}

class _PaletteCard extends StatelessWidget {
  const _PaletteCard({
    required this.palette,
    required this.selected,
    required this.brightness,
    required this.onTap,
  });

  final MaevePalette palette;
  final bool selected;
  final Brightness brightness;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final neutrals = palette.neutrals(brightness);
    final seed = palette.primaryOf(brightness);
    return Pressable(
      onTap: onTap,
      onLongPress: null,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      haptic: MaeveHaptic.light,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 160),
        padding: const EdgeInsets.all(MaeveSpace.md),
        decoration: BoxDecoration(
          color: neutrals.surface,
          borderRadius: BorderRadius.circular(MaeveRadius.md),
          border: Border.all(
            color: selected ? seed : scheme.outline.withValues(alpha: .55),
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
                _Swatch(color: palette.bubbleOf(brightness)),
                const SizedBox(width: 4),
                _Swatch(color: palette.glow),
                const Spacer(),
                if (selected)
                  Icon(Icons.check_circle_rounded, size: 16, color: seed),
              ],
            ),
            const SizedBox(height: MaeveSpace.sm),
            Text(
              palette.label,
              style: Theme.of(context).textTheme.titleSmall
                  ?.copyWith(color: neutrals.ink),
            ),
            Text(
              selected ? '当前配色' : '轻点切换',
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: neutrals.secondary),
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

/// 迷你会话预览:一个用户气泡 + 一条回答 + 一颗主按钮。
class _Preview extends StatelessWidget {
  const _Preview({required this.palette, required this.brightness});

  final MaevePalette palette;
  final Brightness brightness;

  @override
  Widget build(BuildContext context) {
    final neutrals = palette.neutrals(brightness);
    final seed = palette.primaryOf(brightness);
    return Container(
      padding: const EdgeInsets.all(MaeveSpace.lg),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [neutrals.backdropTop, neutrals.backdropBottom],
        ),
        borderRadius: BorderRadius.circular(MaeveRadius.lg),
        border: Border.all(
          color: Theme.of(context).colorScheme.outline.withValues(alpha: .4),
          width: .5,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: Container(
              padding: const EdgeInsets.symmetric(
                horizontal: MaeveSpace.md,
                vertical: MaeveSpace.sm,
              ),
              decoration: BoxDecoration(
                color: palette.bubbleOf(brightness),
                borderRadius: BorderRadius.circular(MaeveRadius.lg),
              ),
              child: Text(
                '帮我把这件事理清楚',
                style: Theme.of(context).textTheme.bodyMedium
                    ?.copyWith(color: neutrals.bubbleInk),
              ),
            ),
          ),
          const SizedBox(height: MaeveSpace.sm),
          Row(
            children: [
              Container(
                width: 18,
                height: 18,
                decoration: BoxDecoration(
                  color: seed.withValues(alpha: .2),
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: MaeveSpace.sm),
              Expanded(
                child: Text(
                  '先说结论，再拆成三步。',
                  style: Theme.of(context).textTheme.bodyMedium
                      ?.copyWith(color: neutrals.ink),
                ),
              ),
            ],
          ),
          const SizedBox(height: MaeveSpace.md),
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: MaeveSpace.md,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: seed,
                  borderRadius: BorderRadius.circular(MaeveRadius.pill),
                ),
                child: Text(
                  '继续',
                  style: Theme.of(context).textTheme.labelMedium
                      ?.copyWith(color: palette.onPrimaryOf(brightness)),
                ),
              ),
              const SizedBox(width: MaeveSpace.sm),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: MaeveSpace.md,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: palette.container,
                  borderRadius: BorderRadius.circular(MaeveRadius.pill),
                ),
                child: Text(
                  '换个方向',
                  style: Theme.of(context).textTheme.labelMedium
                      ?.copyWith(color: palette.onContainer),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

import 'package:flutter/material.dart';

import '../../design/maeve_accent.dart';
import '../../widgets/glass_surface.dart';
import '../../design/maeve_colors.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import '../common/pressable.dart';

class PaletteStrip extends StatelessWidget {
  const PaletteStrip({
    super.key,
    required this.selected,
    required this.onSelected,
  });
  final MaeveAccentPreset selected;
  final ValueChanged<MaeveAccentPreset> onSelected;
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return MaeveGlassSurface(
      shape: RoundedSuperellipseBorder(borderRadius: BorderRadius.circular(22)),
      padding: const EdgeInsets.fromLTRB(12, 16, 12, 10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('今日色彩', style: MaeveType.bodyStrong.copyWith(color: c.ink)),
              const Spacer(),
              Text(
                selected.label,
                style: MaeveType.caption.copyWith(color: c.accent),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              for (final preset in MaeveAccentPreset.values)
                Expanded(
                  child: Tooltip(
                    message: preset.label,
                    child: Pressable(
                      onTap: () => onSelected(preset),
                      semanticLabel: '切换${preset.label}配色',
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: Center(
                          child: _PaletteDot(
                            preset: preset,
                            selected: preset == selected,
                          ),
                        ),
                      ),
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

class _PaletteDot extends StatelessWidget {
  const _PaletteDot({required this.preset, required this.selected});
  final MaeveAccentPreset preset;
  final bool selected;
  @override
  Widget build(BuildContext context) {
    final accent = deriveAccent(preset);
    final dark = Theme.of(context).brightness == Brightness.dark;
    final c = dark ? MaeveColors.dark(accent) : MaeveColors.light(accent);
    return Container(
      width: 36,
      height: 36,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(
          color: selected ? c.accent : Colors.transparent,
          width: 1.5,
        ),
      ),
      child: Container(
        decoration: BoxDecoration(shape: BoxShape.circle, color: c.accent),
        child: selected
            ? Icon(Icons.check_rounded, color: c.onAccent, size: 17)
            : null,
      ),
    );
  }
}

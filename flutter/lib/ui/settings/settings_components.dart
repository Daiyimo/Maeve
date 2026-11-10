import 'package:flutter/material.dart';

import '../../design/maeve_motion.dart';
import '../../design/maeve_tokens.dart';
import '../../widgets/glass_surface.dart';
import '../common/pressable.dart';
import '../common/status.dart';

/// 设置页顶部的搜索胶囊:点一下进搜索页,不在 hub 里直接打字。
class SearchCapsuleButton extends StatelessWidget {
  const SearchCapsuleButton({
    super.key,
    required this.onTap,
    this.hint = '搜索设置',
  });

  final VoidCallback onTap;
  final String hint;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.pill),
      semanticLabel: hint,
      child: Container(
        height: 40,
        padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.md),
        decoration: BoxDecoration(
          color: scheme.onSurface.withValues(alpha: .05),
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
          border: Border.all(
            color: scheme.outline.withValues(alpha: .55),
            width: .5,
          ),
        ),
        child: Row(
          children: [
            Icon(Icons.search, size: 17, color: scheme.onSurfaceVariant),
            const SizedBox(width: MaeveSpace.sm),
            Expanded(
              child: Text(
                hint,
                style: Theme.of(context).textTheme.bodyMedium
                    ?.copyWith(color: scheme.onSurfaceVariant),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 设置 hub 顶部的一览卡:账号 / 余额 / 引擎。
class SettingsOverview extends StatelessWidget {
  const SettingsOverview({
    super.key,
    required this.name,
    required this.detail,
    required this.stats,
    this.tone = MaeveTone.ready,
    this.onTap,
    this.avatar,
  });

  final String name;
  final String detail;

  /// 三列统计:标题 / 值 / 是否等宽数字。
  final List<({String label, String value, bool mono})> stats;
  final MaeveTone tone;
  final VoidCallback? onTap;
  final Widget? avatar;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      borderRadius: BorderRadius.circular(MaeveRadius.lg),
      child: Column(
        children: [
          Pressable(
            onTap: onTap,
            borderRadius: BorderRadius.circular(MaeveRadius.lg),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.lg,
                MaeveSpace.md + 2,
                MaeveSpace.lg,
                MaeveSpace.md + 2,
              ),
              child: Row(
                children: [
                  avatar ??
                      Container(
                        width: 38,
                        height: 38,
                        decoration: BoxDecoration(
                          color: scheme.primary.withValues(alpha: .14),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.person_outline,
                          size: 19,
                          color: scheme.primary,
                        ),
                      ),
                  const SizedBox(width: MaeveSpace.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.titleSmall,
                        ),
                        const SizedBox(height: 1),
                        Text(
                          detail,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  StatusChip(
                    label: switch (tone) {
                      MaeveTone.ready => '已连接',
                      MaeveTone.running => '运行中',
                      MaeveTone.warning => '待处理',
                      MaeveTone.danger => '异常',
                      MaeveTone.idle => '离线',
                    },
                    tone: tone,
                    filled: true,
                  ),
                ],
              ),
            ),
          ),
          Container(height: .5, color: scheme.outline.withValues(alpha: .6)),
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: MaeveSpace.lg,
              vertical: MaeveSpace.md,
            ),
            child: Row(
              children: [
                for (final stat in stats)
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          stat.label,
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          stat.value,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: stat.mono
                              ? MaeveFonts.mono(context, size: 13)
                              : Theme.of(context).textTheme.titleSmall,
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 一组设置卡:一个眉标 + 一张 lg 圆角卡,组内行共享一个色相。
class SettingsSection extends StatelessWidget {
  const SettingsSection({
    super.key,
    required this.title,
    required this.rows,
    this.hue,
    this.action,
    this.footnote,
  });

  final String title;

  /// 组内每一行(共用同一个色相槽)。
  final List<Widget> rows;

  /// 组内图标色相。null = 用主色。
  final Color? hue;

  final Widget? action;
  final String? footnote;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final divided = <Widget>[];
    for (var i = 0; i < rows.length; i++) {
      if (i > 0) {
        divided.add(
          Container(
            height: .5,
            margin: const EdgeInsets.only(left: 52),
            color: scheme.outline.withValues(alpha: .5),
          ),
        );
      }
      divided.add(rows[i]);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(4, 0, 4, MaeveSpace.sm),
          child: Row(
            children: [
              Text(title, style: maeveEyebrow(context)),
              const Spacer(),
              ?action,
            ],
          ),
        ),
        SettingsHue(
          hue: hue ?? scheme.primary,
          child: MaeveGlassSurface(
            tier: MaeveGlassTier.card,
            borderRadius: BorderRadius.circular(MaeveRadius.lg),
            padding: const EdgeInsets.symmetric(vertical: MaeveSpace.xs),
            child: Column(children: divided),
          ),
        ),
        if (footnote != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(4, MaeveSpace.sm, 4, 0),
            child: Text(
              footnote!,
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: scheme.onSurfaceVariant),
            ),
          ),
      ],
    );
  }
}

/// 组内色相槽:一张卡里所有行的图标块共用同一个色相。
class SettingsHue extends InheritedWidget {
  const SettingsHue({super.key, required this.hue, required super.child});

  final Color hue;

  static Color? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<SettingsHue>()?.hue;

  @override
  bool updateShouldNotify(SettingsHue oldWidget) => oldWidget.hue != hue;
}

/// 设置行:图标块 + 标题(+副标题) + 右侧值。
class SettingsTile extends StatelessWidget {
  const SettingsTile({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.value,
    this.mono = false,
    this.chip,
    this.trailing,
    this.hue,
    this.onTap,
    this.destructive = false,
    this.enabled = true,
  });

  final IconData icon;
  final String title;
  final String? subtitle;

  /// 右侧值。数字用 [mono] 走等宽 + tabular。
  final String? value;
  final bool mono;

  /// 状态 chip(优先于 value 展示)。
  final StatusChip? chip;
  final Widget? trailing;

  /// 图标块色相。null = 组内共享(由 [SettingsSection] 传下来)。
  final Color? hue;

  final VoidCallback? onTap;
  final bool destructive;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final tint = destructive
        ? scheme.error
        : (hue ?? SettingsHue.maybeOf(context) ?? scheme.primary);
    return SettingsRow(
      enabled: enabled,
      onTap: enabled ? onTap : null,
      icon: icon,
      tint: tint,
      title: title,
      subtitle: subtitle,
      destructive: destructive,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          ?chip,
          if (chip == null && value != null)
            Text(
              value!,
              style: mono
                  ? MaeveFonts.mono(
                      context,
                      size: 12.5,
                      color: scheme.onSurfaceVariant,
                    )
                  : Theme.of(context).textTheme.bodyMedium
                        ?.copyWith(color: scheme.onSurfaceVariant),
            ),
          ?trailing,
          if (onTap != null && enabled && trailing == null) ...[
            const SizedBox(width: 2),
            Icon(
              Icons.chevron_right,
              size: 18,
              color: scheme.onSurfaceVariant.withValues(alpha: .8),
            ),
          ],
        ],
      ),
    );
  }
}

/// 开关行:整行可点,右侧是开关态文案 + 开关(双编码)。
class SettingsSwitchTile extends StatelessWidget {
  const SettingsSwitchTile({
    super.key,
    required this.icon,
    required this.title,
    required this.value,
    required this.onChanged,
    this.subtitle,
    this.hue,
    this.enabled = true,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final Color? hue;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      toggled: value,
      child: SettingsRow(
        enabled: enabled,
        onTap: enabled && onChanged != null ? () => onChanged!(!value) : null,
        icon: icon,
        tint:
            hue ??
            SettingsHue.maybeOf(context) ??
            Theme.of(context).colorScheme.primary,
        title: title,
        subtitle: subtitle,
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              value ? '已开启' : '已关闭',
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(width: MaeveSpace.sm),
            Switch.adaptive(
              value: value,
              onChanged: enabled ? onChanged : null,
              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
          ],
        ),
      ),
    );
  }
}

/// 分段行:两三个互斥档位。选中态用主色填充 + 文字加粗(不只靠颜色)。
class SettingsSegmentTile extends StatelessWidget {
  const SettingsSegmentTile({
    super.key,
    required this.icon,
    required this.title,
    required this.options,
    required this.value,
    required this.onChanged,
    this.subtitle,
    this.hue,
    this.enabled = true,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final List<String> options;
  final int value;
  final ValueChanged<int>? onChanged;
  final Color? hue;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.md,
        MaeveSpace.sm + 2,
        MaeveSpace.md,
        MaeveSpace.md,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              SettingsGlyph(
                icon: icon,
                tint: hue ?? SettingsHue.maybeOf(context) ?? scheme.primary,
              ),
              const SizedBox(width: MaeveSpace.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: Theme.of(context).textTheme.bodyLarge),
                    if (subtitle != null)
                      Text(
                        subtitle!,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: MaeveSpace.md),
          Row(
            children: [
              for (var i = 0; i < options.length; i++)
                Expanded(
                  child: Padding(
                    padding: EdgeInsets.only(
                      left: i == 0 ? 0 : MaeveSpace.xs,
                      right: i == options.length - 1 ? 0 : MaeveSpace.xs,
                    ),
                    child: Pressable(
                      onTap: enabled && onChanged != null
                          ? () => onChanged!(i)
                          : null,
                      borderRadius: BorderRadius.circular(MaeveRadius.sm),
                      scale: false,
                      child: AnimatedContainer(
                        duration: AppMotion.of(context).quick,
                        curve: Curves.easeOut,
                        height: 34,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: i == value
                              ? scheme.primary.withValues(alpha: .16)
                              : scheme.onSurface.withValues(alpha: .04),
                          borderRadius: BorderRadius.circular(MaeveRadius.sm),
                          border: Border.all(
                            color: i == value
                                ? scheme.primary.withValues(alpha: .55)
                                : Colors.transparent,
                            width: .8,
                          ),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            if (i == value) ...[
                              Icon(
                                Icons.check,
                                size: 13,
                                color: scheme.primary,
                              ),
                              const SizedBox(width: 3),
                            ],
                            Text(
                              options[i],
                              style: Theme.of(context).textTheme.labelMedium
                                  ?.copyWith(
                                    color: i == value
                                        ? scheme.primary
                                        : scheme.onSurfaceVariant,
                                  ),
                            ),
                          ],
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

/// 设置行的通用骨架(图标块 + 两行文字 + 右侧)。
class SettingsRow extends StatelessWidget {
  const SettingsRow({
    super.key,
    required this.icon,
    required this.tint,
    required this.title,
    this.subtitle,
    this.trailing,
    this.onTap,
    this.enabled = true,
    this.destructive = false,
  });

  final IconData icon;
  final Color tint;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool enabled;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Pressable(
      onTap: onTap,
      enabled: enabled,
      scale: false,
      borderRadius: BorderRadius.circular(MaeveRadius.sm),
      child: Opacity(
        opacity: enabled ? 1 : .45,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            MaeveSpace.md,
            MaeveSpace.sm + 2,
            MaeveSpace.md,
            MaeveSpace.sm + 2,
          ),
          child: Row(
            children: [
              SettingsGlyph(icon: icon, tint: tint),
              const SizedBox(width: MaeveSpace.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: Theme.of(context).textTheme.bodyLarge
                          ?.copyWith(color: destructive ? scheme.error : null),
                    ),
                    if (subtitle != null) ...[
                      const SizedBox(height: 1),
                      Text(
                        subtitle!,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ],
                ),
              ),
              if (trailing != null) ...[
                const SizedBox(width: MaeveSpace.sm),
                Flexible(child: trailing!),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// 设置行的图标块:30×30、sm 圆角、组色相淡底。
class SettingsGlyph extends StatelessWidget {
  const SettingsGlyph({super.key, required this.icon, required this.tint});

  final IconData icon;
  final Color tint;

  @override
  Widget build(BuildContext context) => Container(
    width: 30,
    height: 30,
    decoration: BoxDecoration(
      color: tint.withValues(alpha: .13),
      borderRadius: BorderRadius.circular(MaeveRadius.sm),
    ),
    child: Icon(icon, size: 16, color: tint),
  );
}

/// 功能入口块(hub 里的"能力"区):图标 + 标题 + 副标题 + 可选徽标。
class FeatureTile extends StatelessWidget {
  const FeatureTile({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.hue,
    this.badge,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final Color? hue;
  final String? badge;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final tint = hue ?? scheme.primary;
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      scale: false,
      child: Container(
        padding: const EdgeInsets.all(MaeveSpace.md),
        decoration: BoxDecoration(
          color: scheme.onSurface.withValues(alpha: .035),
          borderRadius: BorderRadius.circular(MaeveRadius.md),
          border: Border.all(
            color: scheme.outline.withValues(alpha: .45),
            width: .5,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                SettingsGlyph(icon: icon, tint: tint),
                const Spacer(),
                if (badge != null)
                  Text(
                    badge!,
                    style: Theme.of(context).textTheme.labelSmall
                        ?.copyWith(color: tint),
                  ),
              ],
            ),
            const SizedBox(height: MaeveSpace.md),
            Text(title, style: Theme.of(context).textTheme.titleSmall),
            if (subtitle != null) ...[
              const SizedBox(height: 2),
              Text(
                subtitle!,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// 列表页的一行(会话、插件、日志):比 [SettingsTile] 更轻,没有图标块。
class ListPageRow extends StatelessWidget {
  const ListPageRow({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.trailing,
    this.onTap,
    this.selected = false,
  });

  final String title;
  final String? subtitle;
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Pressable(
      onTap: onTap,
      scale: false,
      borderRadius: BorderRadius.circular(MaeveRadius.sm),
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: MaeveSpace.md,
          vertical: MaeveSpace.sm + 2,
        ),
        decoration: BoxDecoration(
          color: selected ? scheme.primary.withValues(alpha: .10) : null,
          borderRadius: BorderRadius.circular(MaeveRadius.sm),
        ),
        child: Row(
          children: [
            if (leading != null) ...[
              leading!,
              const SizedBox(width: MaeveSpace.md),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                    ),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 1),
                    Text(
                      subtitle!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ],
              ),
            ),
            if (trailing != null) ...[
              const SizedBox(width: MaeveSpace.sm),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';

import '../../design/maeve_colors.dart';
import '../../design/maeve_motion.dart';
import '../../design/maeve_shapes.dart';
import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';

import '../../widgets/glass_surface.dart';
import '../common/pressable.dart';
import '../common/status.dart';

/// 右栏规则(A9):value / chip 与箭头之间**永远 8**;行本身右内边距 16;
/// value 单行、省略号、最多占行宽的 55%。开关行把"箭头"换成开关,间距不变。
abstract final class RowMetrics {
  static const gap = MaeveSpace.s8;
  static const endPadding = MaeveSpace.s16;
  static const textStartWithIcon = 60.0;
  static const maxValueFraction = .55;
}

/// 分组小标题:比正文更轻的一行说明,不当"仪表面板"。
class GroupLabel extends StatelessWidget {
  const GroupLabel({super.key, required this.text, this.trailing});

  final String text;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(
      top: MaeveSpace.s20,
      bottom: MaeveSpace.s8,
      left: 2,
      right: 2,
    ),
    child: Row(
      children: [
        Text(
          text,
          style: MaeveType.label.copyWith(
            fontSize: 12.5,
            color: MaeveTheme.of(context).colors.ink3,
          ),
        ),
        const Spacer(),
        ?trailing,
      ],
    ),
  );
}

/// 分组下面的说明,和卡片里的文字对齐。
class FooterNote extends StatelessWidget {
  const FooterNote({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(
      MaeveSpace.s16,
      MaeveSpace.s8,
      MaeveSpace.s16,
      0,
    ),
    child: Text(
      text,
      style: MaeveType.hint.copyWith(color: MaeveTheme.of(context).colors.ink3),
    ),
  );
}

/// 图标块的颜色语义。普通行中性,只有状态上色。
enum Tone { neutral, accent, ok, warn, bad }

(Color, Color) toneColors(BuildContext context, Tone tone) {
  final theme = MaeveTheme.of(context);
  final c = theme.colors;
  return switch (tone) {
    Tone.neutral => (c.neutralSoft, c.ink2),
    Tone.accent => (c.accentSoft, c.accent),
    Tone.ok => (c.okSoft, theme.semantic.ok),
    Tone.warn => (c.warnSoft, theme.semantic.warn),
    Tone.bad => (c.badSoft, theme.semantic.bad),
  };
}

/// 组内色相槽:组里所有行的图标块共用同一个色调。
///
/// 默认 [Tone.neutral] —— 普通行的图标**不上色**,只有状态才该有颜色;
/// 需要分组的组(能力、主机)可以把整组染成同一色调。
class SettingsHue extends InheritedWidget {
  const SettingsHue({super.key, required this.tone, required super.child});

  final Tone tone;

  static Tone of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<SettingsHue>()?.tone ??
      Tone.neutral;

  @override
  bool updateShouldNotify(SettingsHue oldWidget) => oldWidget.tone != tone;
}

/// 行左边的图标块:30、圆角 7,里面图形 17。
class IconTile extends StatelessWidget {
  const IconTile({super.key, required this.icon, this.tone = Tone.neutral});

  final IconData icon;
  final Tone tone;

  @override
  Widget build(BuildContext context) {
    final (bg, fg) = toneColors(context, tone);
    return Container(
      width: MaeveSize.tile,
      height: MaeveSize.tile,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(7),
      ),
      child: Icon(icon, size: MaeveSize.tileIcon * .95, color: fg),
    );
  }
}

/// 行尾的值:无中文自动等宽(读数感自动扩散),单行、省略号。
class RowValue extends StatelessWidget {
  const RowValue({
    super.key,
    required this.text,
    this.color,
    this.bold = false,
  });

  final String text;
  final Color? color;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Text(
      text,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      textAlign: TextAlign.end,
      style: MaeveType.valueFor(text).copyWith(
        color: color ?? colors.ink3,
        fontWeight: bold ? FontWeight.w500 : null,
      ),
    );
  }
}

/// 行尾的小箭头:点进去还有一页。
class RowChevron extends StatelessWidget {
  const RowChevron({super.key});

  @override
  Widget build(BuildContext context) => Icon(
    Icons.chevron_right,
    size: MaeveSize.smallIcon,
    color: MaeveTheme.of(context).colors.ink3,
  );
}

/// 列表行:图标块 + 标题(+副标题) + 右栏。
class SettingsRow extends StatelessWidget {
  const SettingsRow({
    super.key,
    required this.title,
    this.icon,
    this.subtitle,
    this.trailing,
    this.onTap,
    this.onLongPress,
    this.enabled = true,
    this.destructive = false,
    this.tone,
    this.first = false,
  });

  final String title;
  final IconData? icon;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final bool enabled;
  final bool destructive;
  final Tone? tone;

  /// 分组首行不画上分隔线。
  final bool first;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final resolvedTone = tone ?? SettingsHue.of(context);
    final start = icon != null
        ? RowMetrics.textStartWithIcon
        : RowMetrics.endPadding;
    return Opacity(
      opacity: enabled ? 1 : .45,
      child: Pressable(
        onTap: enabled ? onTap : null,
        onLongPress: enabled ? onLongPress : null,
        scale: false,
        borderRadius: BorderRadius.circular(MaeveRadius.sm),
        child: Container(
          decoration: BoxDecoration(
            border: first
                ? null
                : Border(top: BorderSide(color: colors.hair, width: .5)),
          ),
          constraints: const BoxConstraints(minHeight: MaeveSize.rowMin),
          padding: const EdgeInsets.symmetric(
            horizontal: RowMetrics.endPadding,
          ),
          child: Row(
            children: [
              if (icon != null) ...[
                IconTile(icon: icon!, tone: resolvedTone),
                const SizedBox(width: 14),
              ] else
                SizedBox(width: start - RowMetrics.endPadding),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 11),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: MaeveType.body.copyWith(
                          color: destructive ? colors.bad : colors.ink,
                        ),
                      ),
                      if (subtitle != null) ...[
                        const SizedBox(height: 2),
                        Text(
                          subtitle!,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: MaeveType.caption.copyWith(color: colors.ink3),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              if (trailing != null) ...[
                const SizedBox(width: RowMetrics.gap),
                ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth:
                        MediaQuery.sizeOf(context).width *
                        RowMetrics.maxValueFraction,
                  ),
                  child: trailing,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// 设置行:图标块 + 标题 + 右栏(value / chip / 箭头)。
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
    this.tone,
    this.onTap,
    this.destructive = false,
    this.enabled = true,
    this.first = false,
  });

  final IconData icon;
  final String title;
  final String? subtitle;

  /// 右侧值。数字自动等宽([mono] 强制等宽)。
  final String? value;
  final bool mono;

  /// 状态 chip(优先于 value 展示)。
  final StatusChip? chip;
  final Widget? trailing;
  final Tone? tone;
  final VoidCallback? onTap;
  final bool destructive;
  final bool enabled;
  final bool first;

  @override
  Widget build(BuildContext context) => SettingsRow(
    first: first,
    title: title,
    subtitle: subtitle,
    icon: icon,
    tone: tone,
    enabled: enabled,
    destructive: destructive,
    onTap: onTap,
    trailing: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (chip != null)
          chip!
        else if (value != null)
          Flexible(
            child: RowValue(text: value!, bold: mono),
          ),
        if (onTap != null && enabled && trailing == null) ...[
          const SizedBox(width: RowMetrics.gap),
          const RowChevron(),
        ],
        ?trailing,
      ],
    ),
  );
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
    this.tone,
    this.enabled = true,
    this.first = false,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final Tone? tone;
  final bool enabled;
  final bool first;

  @override
  Widget build(BuildContext context) => Semantics(
    toggled: value,
    child: SettingsRow(
      first: first,
      title: title,
      subtitle: subtitle,
      icon: icon,
      tone: tone,
      enabled: enabled,
      onTap: enabled && onChanged != null ? () => onChanged!(!value) : null,
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            value ? '已开启' : '已关闭',
            style: MaeveType.hint.copyWith(
              color: MaeveTheme.of(context).colors.ink3,
            ),
          ),
          const SizedBox(width: RowMetrics.gap),
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

/// 分段行:两三个互斥档位。选中态用强调色填充 + 文字加粗(不只靠颜色)。
class SettingsSegmentTile extends StatelessWidget {
  const SettingsSegmentTile({
    super.key,
    required this.icon,
    required this.title,
    required this.options,
    required this.value,
    required this.onChanged,
    this.subtitle,
    this.tone,
    this.enabled = true,
    this.first = false,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final List<String> options;
  final int value;
  final ValueChanged<int>? onChanged;
  final Tone? tone;
  final bool enabled;
  final bool first;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Container(
      decoration: BoxDecoration(
        border: first
            ? null
            : Border(top: BorderSide(color: colors.hair, width: .5)),
      ),
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.s16,
        MaeveSpace.s12,
        MaeveSpace.s16,
        MaeveSpace.s12,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconTile(icon: icon, tone: tone ?? SettingsHue.of(context)),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: MaeveType.body.copyWith(color: colors.ink),
                    ),
                    if (subtitle != null)
                      Text(
                        subtitle!,
                        style: MaeveType.caption.copyWith(color: colors.ink3),
                      ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: MaeveSpace.s10),
          Row(
            children: [
              for (var i = 0; i < options.length; i++)
                Expanded(
                  child: Padding(
                    padding: EdgeInsets.only(
                      left: i == 0 ? 0 : MaeveSpace.s4,
                      right: i == options.length - 1 ? 0 : MaeveSpace.s4,
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
                        height: 32,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          color: i == value ? colors.accent : colors.fill,
                          borderRadius: BorderRadius.circular(MaeveRadius.sm),
                        ),
                        child: Text(
                          options[i],
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: MaeveType.label.copyWith(
                            fontSize: 12.5,
                            color: i == value ? colors.onAccent : colors.ink3,
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

/// 一组设置卡:一个分组标题 + 一张卡,卡内行与行之间一道发丝线。
class SettingsSection extends StatelessWidget {
  const SettingsSection({
    super.key,
    required this.title,
    required this.rows,
    this.tone,
    this.footnote,
    this.trailing,
  });

  final String title;
  final List<Widget> rows;

  /// 组内图标色调(默认中性)。
  final Tone? tone;
  final String? footnote;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      GroupLabel(text: title, trailing: trailing),
      SettingsHue(
        tone: tone ?? Tone.neutral,
        child: MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          shape: MaeveShapes.card,
          padding: const EdgeInsets.symmetric(vertical: MaeveSpace.s4),
          child: Column(children: rows),
        ),
      ),
      if (footnote != null) FooterNote(text: footnote!),
    ],
  );
}

/// 设置页顶部的一览卡:账号 / 余额 / 引擎。
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
  final List<({String label, String value, bool mono})> stats;
  final MaeveTone tone;
  final VoidCallback? onTap;
  final Widget? avatar;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return MaeveGlassSurface(
      tier: MaeveGlassTier.card,
      shape: MaeveShapes.card,
      child: Column(
        children: [
          Pressable(
            onTap: onTap,
            borderRadius: BorderRadius.circular(MaeveRadius.lg),
            scale: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.s16,
                MaeveSpace.s12,
                MaeveSpace.s16,
                MaeveSpace.s12,
              ),
              child: Row(
                children: [
                  avatar ??
                      Container(
                        width: 36,
                        height: 36,
                        decoration: BoxDecoration(
                          color: colors.accentSoft,
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          Icons.person_outline,
                          size: 18,
                          color: colors.accent,
                        ),
                      ),
                  const SizedBox(width: MaeveSpace.s12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: MaeveType.bodyStrong.copyWith(
                            color: colors.ink,
                          ),
                        ),
                        const SizedBox(height: 1),
                        Text(
                          detail,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: MaeveType.hint.copyWith(color: colors.ink3),
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
          Container(height: .5, color: colors.hair),
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: MaeveSpace.s16,
              vertical: MaeveSpace.s12,
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
                          style: MaeveType.eyebrowFor(stat.label)
                              .copyWith(color: colors.ink3),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          stat.value,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: stat.mono
                              ? MaeveType.mono.copyWith(
                                  fontSize: 13,
                                  color: colors.ink,
                                )
                              : MaeveType.body.copyWith(color: colors.ink),
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

/// 设置页顶部的搜索胶囊:点一下进搜索页。
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
    final colors = MaeveTheme.of(context).colors;
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      semanticLabel: hint,
      child: Container(
        height: 40,
        padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.s12),
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(MaeveRadius.md),
          border: Border.all(color: colors.edge, width: .5),
        ),
        child: Row(
          children: [
            Icon(Icons.search, size: 17, color: colors.ink3),
            const SizedBox(width: MaeveSpace.s8),
            Expanded(
              child: Text(
                hint,
                style: MaeveType.body.copyWith(color: colors.ink3),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 功能入口块(hub 里的"能力"区)。
class FeatureTile extends StatelessWidget {
  const FeatureTile({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.tone,
    this.badge,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final Tone? tone;
  final String? badge;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    final resolved = tone ?? SettingsHue.of(context);
    final (bg, fg) = toneColors(context, resolved);
    return Pressable(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      scale: false,
      child: Container(
        padding: const EdgeInsets.all(MaeveSpace.s12),
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(MaeveRadius.md),
          border: Border.all(color: colors.edge, width: .5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: MaeveSize.tile,
                  height: MaeveSize.tile,
                  decoration: BoxDecoration(
                    color: bg,
                    borderRadius: BorderRadius.circular(7),
                  ),
                  child: Icon(icon, size: 16, color: fg),
                ),
                const Spacer(),
                if (badge != null)
                  Text(
                    badge!,
                    style: MaeveType.eyebrowFor(badge!)
                        .copyWith(color: colors.ink3),
                  ),
              ],
            ),
            const SizedBox(height: MaeveSpace.s10),
            Text(
              title,
              style: MaeveType.bodyStrong.copyWith(color: colors.ink),
            ),
            if (subtitle != null) ...[
              const SizedBox(height: 2),
              Text(
                subtitle!,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: MaeveType.hint.copyWith(color: colors.ink3),
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
    final colors = MaeveTheme.of(context).colors;
    return Pressable(
      onTap: onTap,
      scale: false,
      borderRadius: BorderRadius.circular(MaeveRadius.sm),
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: MaeveSpace.s12,
          vertical: MaeveSpace.s10,
        ),
        decoration: BoxDecoration(
          color: selected ? colors.accentSoft : null,
          borderRadius: BorderRadius.circular(MaeveRadius.sm),
        ),
        child: Row(
          children: [
            if (leading != null) ...[
              leading!,
              const SizedBox(width: MaeveSpace.s12),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: MaeveType.body.copyWith(
                      color: colors.ink,
                      fontWeight: selected ? FontWeight.w500 : FontWeight.w400,
                    ),
                  ),
                  if (subtitle != null) ...[
                    const SizedBox(height: 1),
                    Text(
                      subtitle!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: MaeveType.hint.copyWith(color: colors.ink3),
                    ),
                  ],
                ],
              ),
            ),
            if (trailing != null) ...[
              const SizedBox(width: RowMetrics.gap),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}

/// 分组卡里自己排版的块之间的一道发丝线。
class Hairline extends StatelessWidget {
  const Hairline({super.key, this.start = RowMetrics.textStartWithIcon});

  final double start;

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(left: start),
    child: Container(height: .5, color: MaeveTheme.of(context).colors.hair),
  );
}

/// 供页面取色的小工具。
extension SettingsColors on BuildContext {
  MaeveColors get settingsColors => MaeveTheme.of(this).colors;
}

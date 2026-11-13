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
  const GroupLabel({
    super.key,
    required this.text,
    this.trailing,
    this.topSpacing = MaeveSpace.s32,
  });

  final String text;
  final Widget? trailing;
  final double topSpacing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(
      top: topSpacing,
      bottom: MaeveSpace.s12,
      left: 2,
      right: 2,
    ),
    child: Row(
      children: [
        Text(
          text,
          style: MaeveType.bodyStrong.copyWith(
            color: MaeveTheme.of(context).colors.ink,
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
///
/// **一个分组里所有行的图标必须是同一个色调。** 上一版按分组上色(能力=绿、
/// 外观=梅子),一屏里同时出现梅子、绿、琥珀三种低饱和色 —— 每种都只有一点点,
/// 结果不是"丰富",是"脏"。现在默认全中性,颜色只留给真正表示状态的那一个 chip。
enum Tone { neutral, accent, ok, warn, bad }

(Color, Color) toneColors(BuildContext context, Tone tone) {
  final theme = MaeveTheme.of(context);
  final c = theme.colors;
  return switch (tone) {
    // 中性图标块要用 [MaeveColors.greySoft] 而不是 neutralSoft:后者只有 6% 墨,
    // 叠在玻璃上几乎看不见,整排图标像浮在空气里。
    Tone.neutral => (c.accent.withValues(alpha: .09), c.accent),
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

/// 行左边的图标块:30、圆角 9,里面图形 17。
///
/// 它是一块"安静的印章",不是按钮:中性底 + 墨色图形,只靠一点点色差和字形
/// 提供可扫读的锚点。
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
        borderRadius: BorderRadius.circular(9),
      ),
      child: Icon(icon, size: 17, color: fg),
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

/// 告诉行"你在组里的第几个":首行不画上分隔线。
class _RowPosition extends InheritedWidget {
  const _RowPosition({required this.isFirst, required super.child});

  final bool isFirst;

  static bool firstOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_RowPosition>()?.isFirst ??
      false;

  @override
  bool updateShouldNotify(_RowPosition oldWidget) =>
      oldWidget.isFirst != isFirst;
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
    final isFirst = first || _RowPosition.firstOf(context);
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
            border: isFirst
                ? null
                : Border(top: BorderSide(color: colors.hair, width: .5)),
          ),
          constraints: const BoxConstraints(minHeight: MaeveSize.rowMin),
          padding: const EdgeInsets.symmetric(
            horizontal: RowMetrics.endPadding,
          ),
          child: LayoutBuilder(
            builder: (context, rowBox) => Row(
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
                            style: MaeveType.caption.copyWith(
                              color: colors.ink3,
                            ),
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
                      maxWidth: rowBox.maxWidth * RowMetrics.maxValueFraction,
                    ),
                    child: trailing,
                  ),
                ],
              ],
            ),
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

/// 开关行:整行可点,右侧只有开关。
///
/// 上一版在开关左边又写一句"已开启/已关闭":开关本身 + 读屏的 `toggled` 语义已经
/// 表达了两遍,第三遍只是把行内文字挤成两行("按一下有回应,关掉就完 / 全安静")。
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
      trailing: Switch.adaptive(
        value: value,
        onChanged: enabled ? onChanged : null,
        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
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
        border: (first || _RowPosition.firstOf(context))
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
                        constraints: const BoxConstraints(minHeight: 44),
                        padding: const EdgeInsets.symmetric(
                          vertical: 8,
                          horizontal: 4,
                        ),
                        alignment: Alignment.center,
                        decoration: BoxDecoration(
                          // 选中态是"淡淡洗一层 + 一道强调色描边 + 强调色文字",
                          // 不是一整块强调色实底 —— 三个分段里有一个是实心色块,
                          // 整行就会重得像个开关面板。
                          color: i == value
                              ? colors.accent.withValues(alpha: .16)
                              : colors.fill,
                          borderRadius: BorderRadius.circular(MaeveRadius.sm),
                          border: Border.all(
                            color: i == value
                                ? colors.accent.withValues(alpha: .42)
                                : Colors.transparent,
                            width: .75,
                          ),
                        ),
                        child: Text(
                          options[i],
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: MaeveType.label.copyWith(
                            fontSize: 12.5,
                            color: i == value ? colors.accent : colors.ink3,
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
    this.topSpacing = MaeveSpace.s32,
  });

  final String title;
  final double topSpacing;
  final List<Widget> rows;

  /// 组内图标色调(默认中性)。
  final Tone? tone;
  final String? footnote;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      GroupLabel(text: title, trailing: trailing, topSpacing: topSpacing),
      SettingsHue(
        tone: tone ?? Tone.neutral,
        child: MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          shape: MaeveShapes.card,
          padding: const EdgeInsets.symmetric(vertical: MaeveSpace.s4),
          child: Column(
            children: [
              for (var i = 0; i < rows.length; i++)
                _RowPosition(isFirst: i == 0, child: rows[i]),
            ],
          ),
        ),
      ),
      if (footnote != null) FooterNote(text: footnote!),
    ],
  );
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

class SettingsNotice extends StatelessWidget {
  const SettingsNotice({super.key, required this.text, required this.icon});
  final String text;
  final IconData icon;
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return MaeveGlassSurface(
      padding: const EdgeInsets.all(16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: c.ink2),
          const SizedBox(width: 10),
          Expanded(
            child: Text(text, style: MaeveType.caption.copyWith(color: c.ink2)),
          ),
        ],
      ),
    );
  }
}

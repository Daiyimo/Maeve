import 'package:flutter/material.dart';

import '../../design/maeve_theme.dart';
import '../../design/maeve_type.dart';
import 'app_sheet.dart';

/// 所有表单选择使用同一张可搜索的选择面板，长名称无需挤进浮动菜单。
class MaeveSelectField<T> extends StatelessWidget {
  const MaeveSelectField({
    super.key,
    this.initialValue,
    required this.items,
    required this.decoration,
    required this.onChanged,
  });
  final T? initialValue;
  final List<DropdownMenuItem<T>> items;
  final InputDecoration decoration;
  final ValueChanged<T?>? onChanged;
  String label(DropdownMenuItem<T> item) => item.child is Text
      ? (item.child as Text).data ?? '${item.value}'
      : '${item.value}';

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final selected = items.where((i) => i.value == initialValue).firstOrNull;
    return Semantics(
      button: true,
      enabled: onChanged != null,
      label:
          '${decoration.labelText}：${selected == null ? '请选择' : label(selected)}',
      child: Material(
        color: c.glassCard,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(18),
          side: BorderSide(color: c.glassEdge),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(18),
          onTap: onChanged == null
              ? null
              : () async {
                  FocusScope.of(context).unfocus();
                  final value = await showMaeveSheet<T>(
                    context: context,
                    builder: (_) => _SelectionPanel<T>(
                      title: decoration.labelText ?? '选择',
                      items: items,
                      selected: initialValue,
                      label: label,
                    ),
                  );
                  if (value != null && context.mounted) onChanged?.call(value);
                },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        decoration.labelText ?? '选择',
                        style: MaeveType.hint.copyWith(color: c.ink3),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        selected == null ? '请选择' : label(selected),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: MaeveType.bodyStrong.copyWith(
                          color: onChanged == null ? c.ink3 : c.ink,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Icon(Icons.unfold_more_rounded, color: c.ink3, size: 20),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SelectionPanel<T> extends StatefulWidget {
  const _SelectionPanel({
    required this.title,
    required this.items,
    required this.selected,
    required this.label,
  });
  final String title;
  final List<DropdownMenuItem<T>> items;
  final T? selected;
  final String Function(DropdownMenuItem<T>) label;
  @override
  State<_SelectionPanel<T>> createState() => _SelectionPanelState<T>();
}

class _SelectionPanelState<T> extends State<_SelectionPanel<T>> {
  String query = '';
  late final ScrollController scroll;
  @override
  void initState() {
    super.initState();
    final index = widget.items.indexWhere((i) => i.value == widget.selected);
    scroll = ScrollController(
      initialScrollOffset: ((index - 2).clamp(0, widget.items.length) * 60)
          .toDouble(),
    );
  }

  @override
  void dispose() {
    scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    final options = widget.items
        .where(
          (i) => widget.label(i).toLowerCase().contains(query.toLowerCase()),
        )
        .toList();
    final height =
        (MediaQuery.sizeOf(context).height * .72 -
                MediaQuery.viewInsetsOf(context).bottom)
            .clamp(160.0, 640.0);
    final panelHeight = widget.items.length > 8
        ? height
        : (widget.items.length *
                      64 *
                      MediaQuery.textScalerOf(context).scale(1) +
                  100)
              .clamp(160.0, height);
    return AppSheet(
      child: SafeArea(
        top: false,
        child: SizedBox(
          height: panelHeight,
          child: Column(
            children: [
              SheetTitleBar(
                title: widget.title,
                actions: [
                  IconButton(
                    tooltip: '关闭选择',
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close_rounded),
                  ),
                ],
              ),
              if (widget.items.length > 8)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: TextField(
                    key: const ValueKey('selection-search'),
                    onChanged: (value) {
                      if (scroll.hasClients) scroll.jumpTo(0);
                      setState(() => query = value);
                    },
                    decoration: InputDecoration(
                      hintText: '搜索${widget.title}',
                      prefixIcon: const Icon(Icons.search_rounded),
                      filled: true,
                      fillColor: c.fill,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide.none,
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide.none,
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide(
                          color: c.accent.withValues(alpha: .4),
                        ),
                      ),
                    ),
                  ),
                ),
              const SizedBox(height: 8),
              Expanded(
                child: options.isEmpty
                    ? Center(
                        child: Text(
                          '没有匹配的选项',
                          style: MaeveType.hint.copyWith(color: c.ink3),
                        ),
                      )
                    : ListView.separated(
                        controller: scroll,
                        itemCount: options.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 4),
                        itemBuilder: (context, index) {
                          final option = options[index],
                              selected = option.value == widget.selected;
                          return Material(
                            color: selected ? c.accentSoft : Colors.transparent,
                            borderRadius: BorderRadius.circular(14),
                            child: ListTile(
                              enabled: option.enabled,
                              selected: selected,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(14),
                              ),
                              title: Text(
                                widget.label(option),
                                style: MaeveType.body.copyWith(
                                  color: selected ? c.accent : c.ink,
                                ),
                              ),
                              trailing: selected
                                  ? Icon(
                                      Icons.check_rounded,
                                      color: c.accent,
                                      size: 20,
                                    )
                                  : null,
                              onTap: () => Navigator.pop(context, option.value),
                            ),
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class MaeveSegments<T> extends StatelessWidget {
  const MaeveSegments({
    super.key,
    required this.value,
    required this.options,
    required this.onChanged,
    this.disabled = const {},
  });
  final T value;
  final Map<T, String> options;
  final ValueChanged<T>? onChanged;
  final Set<T> disabled;
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: c.fill,
        borderRadius: BorderRadius.circular(15),
      ),
      child: Row(
        children: [
          for (final option in options.entries)
            Expanded(
              child: Semantics(
                selected: option.key == value,
                button: true,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 2),
                  child: Material(
                    color: option.key == value
                        ? c.surfaceHigh
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(11),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(11),
                      onTap: onChanged == null || disabled.contains(option.key)
                          ? null
                          : () => onChanged!(option.key),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 10,
                        ),
                        child: Text(
                          option.value,
                          textAlign: TextAlign.center,
                          style: MaeveType.bodyStrong.copyWith(
                            color:
                                onChanged == null ||
                                    disabled.contains(option.key)
                                ? c.ink3
                                : option.key == value
                                ? c.accent
                                : c.ink2,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

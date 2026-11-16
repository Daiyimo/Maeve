import 'package:flutter/material.dart';

import 'maeve_controller.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/conversation.dart';
import '../widgets/glass_surface.dart';
import '../widgets/maeve_mark.dart';
import '../ui/common/app_page.dart';
import '../ui/common/app_sheet.dart';
import '../ui/common/haptics.dart';
import '../ui/common/pressable.dart';
import '../ui/settings/settings_components.dart';
import '../ui/settings/settings_hub.dart';

class SessionsDrawer extends StatefulWidget {
  const SessionsDrawer({super.key, this.docked = false, required this.onClose});
  final VoidCallback onClose;
  final bool docked;
  @override
  State<SessionsDrawer> createState() => _SessionsDrawerState();
}

class _SessionsDrawerState extends State<SessionsDrawer> {
  final _selected = <String>{};
  bool _selecting = false;
  Future<void> _confirmDelete(
    MaeveController controller,
    List<String> ids,
  ) async {
    var running = 0, schedules = 0;
    try {
      for (final id in ids) {
        final impact = await controller.chatBridge.deleteImpact(id);
        if (impact['runningTask'] == true) running++;
        schedules += (impact['schedules'] as num?)?.toInt() ?? 0;
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('无法读取删除影响，请重试。')));
      }
      return;
    }
    if (!mounted) return;
    final subject = ids.length == 1 ? '这段对话' : '选中的 ${ids.length} 段对话';
    // 只提实际会发生的:普通对话不说「停止 0 段对话、移除 0 个定时任务」。
    final consequences = <String>[
      if (running > 0) '停止 $running 段对话的运行或排队任务',
      if (schedules > 0) '移除 $schedules 个定时任务',
    ];
    final detail = consequences.isEmpty
        ? '将删除$subject及其工作区附件。全局执行统计保留。'
        : '将${consequences.join('，')}，并删除$subject及其工作区附件。全局执行统计保留。';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => MaeveGlassDialog(
        title: Text(ids.length == 1 ? '删除这段对话？' : '删除选中的 ${ids.length} 段对话？'),
        content: Text(detail),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    final failed = <String>[];
    for (final id in ids) {
      try {
        await controller.delete(id);
      } catch (_) {
        failed.add(id);
      }
    }
    if (!mounted) return;
    setState(() {
      _selected
        ..clear()
        ..addAll(failed);
      _selecting = failed.isNotEmpty;
    });
    if (failed.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${failed.length} 段对话尚未完整删除，请重试。')),
      );
    }
  }

  void _rowMenu(MaeveController controller, Conversation conversation) {
    showMaeveSheet<void>(
      context: context,
      builder: (context) => AppSheet(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SheetTitleBar(title: conversation.title, subtitle: '会话操作'),
            ListPageRow(
              title: conversation.pinned ? '取消置顶' : '置顶',
              leading: const Icon(Icons.push_pin_outlined, size: 19),
              onTap: () {
                controller.togglePin(conversation.id);
                Navigator.pop(context);
              },
            ),
            ListPageRow(
              title: '多选',
              leading: const Icon(Icons.checklist_rounded, size: 19),
              onTap: () {
                Navigator.pop(context);
                setState(() {
                  _selecting = true;
                  _selected.add(conversation.id);
                });
              },
            ),
            ListPageRow(
              title: '删除',
              leading: const Icon(Icons.delete_outline_rounded, size: 19),
              onTap: () {
                Navigator.pop(context);
                _confirmDelete(controller, [conversation.id]);
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context),
        c = MaeveTheme.of(context).colors;
    final conversations = controller.visibleConversations;
    return DecoratedBox(
      key: const ValueKey('conversation-sidebar'),
      decoration: BoxDecoration(
        color: c.isDark
            ? c.bg
            : Color.alphaBlend(c.accent.withValues(alpha: .015), Colors.white),
        border: Border(right: BorderSide(color: c.rule, width: .5)),
      ),
      child: SafeArea(
        right: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 4, 4),
              child: Row(
                children: [
                  const MaeveMark(size: 24),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      _selecting ? '已选 ${_selected.length}' : 'Maeve',
                      style: MaeveType.title.copyWith(
                        color: c.ink,
                        fontSize: 15,
                      ),
                    ),
                  ),
                  MaeveGlassButton(
                    icon: Icons.close_rounded,
                    tooltip: _selecting ? '取消多选' : '关闭对话列表',
                    compact: true,
                    haptic: MaeveHaptic.tick,
                    onPressed: _selecting
                        ? () => setState(() {
                            _selecting = false;
                            _selected.clear();
                          })
                        : widget.onClose,
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14),
              child: _selecting
                  ? SizedBox(
                      width: double.infinity,
                      child: MaeveGlassTextButton(
                        label: '删除选中对话',
                        icon: Icons.delete_outline_rounded,
                        prominent: true,
                        enabled: _selected.isNotEmpty,
                        haptic: MaeveHaptic.light,
                        onPressed: _selected.isEmpty
                            ? null
                            : () => _confirmDelete(
                                controller,
                                _selected.toList(),
                              ),
                      ),
                    )
                  : SizedBox(
                      width: double.infinity,
                      child: MaeveGlassTextButton(
                        label: '新对话',
                        icon: Icons.add_rounded,
                        prominent: true,
                        haptic: MaeveHaptic.light,
                        key: const ValueKey('new-conversation'),
                        onPressed: () {
                          controller.create();
                          widget.onClose();
                        },
                      ),
                    ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 8, 14, 4),
              child: TextField(
                key: const ValueKey('conversation-search'),
                onChanged: controller.setSearch,
                style: MaeveType.body.copyWith(fontSize: 13.5, color: c.ink),
                decoration: InputDecoration(
                  hintText: '搜索标题',
                  filled: true,
                  fillColor: c.surface,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(color: c.rule),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide(color: c.accent, width: 1.5),
                  ),
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 10,
                  ),
                  prefixIcon: const Icon(Icons.search_rounded, size: 18),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 0, 14, 8),
              child: Row(
                children: [
                  for (final item in [(0, '全部'), (1, '置顶')]) ...[
                    MaeveGlassTextButton(
                      label: item.$2,
                      compact: true,
                      prominent: controller.listFilter == item.$1,
                      haptic: MaeveHaptic.tick,
                      onPressed: () => controller.setFilter(item.$1),
                    ),
                    const SizedBox(width: MaeveSpace.s8),
                  ],
                ],
              ),
            ),
            Expanded(
              child: conversations.isEmpty
                  ? Padding(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const SizedBox(height: 24),
                          Icon(
                            Icons.chat_bubble_outline_rounded,
                            color: c.accent,
                            size: 25,
                          ),
                          const SizedBox(height: 16),
                          Text(
                            controller.search.isEmpty ? '还没有对话' : '没有匹配的对话',
                            style: MaeveType.bodyStrong.copyWith(color: c.ink),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '每一次新的想法，都可以从这里开始。',
                            style: MaeveType.caption.copyWith(color: c.ink3),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                      itemCount: conversations.length,
                      itemBuilder: (context, index) {
                        final item = conversations[index];
                        return Pressable(
                          onTap: () {
                            if (_selecting) {
                              setState(() {
                                if (!_selected.add(item.id)) {
                                  _selected.remove(item.id);
                                }
                              });
                            } else {
                              controller.select(item.id);
                              widget.onClose();
                            }
                          },
                          onLongPress: () => _rowMenu(controller, item),
                          scale: false,
                          child: Container(
                            key: ValueKey('conversation-row-${item.id}'),
                            constraints: const BoxConstraints(minHeight: 44),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 10,
                              vertical: 10,
                            ),
                            decoration: BoxDecoration(
                              color: controller.activeId == item.id
                                  ? c.accent.withValues(alpha: .07)
                                  : null,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              children: [
                                if (_selecting) ...[
                                  Icon(
                                    _selected.contains(item.id)
                                        ? Icons.check_circle_rounded
                                        : Icons.circle_outlined,
                                    size: 20,
                                    color: c.accent,
                                  ),
                                  const SizedBox(width: 10),
                                ],
                                Expanded(
                                  child: Text(
                                    item.title,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: MaeveType.body.copyWith(
                                      color: c.ink,
                                      fontSize: 14,
                                      height: 1.4,
                                    ),
                                  ),
                                ),
                                if (item.pinned) ...[
                                  const SizedBox(width: 8),
                                  Icon(
                                    Icons.push_pin_rounded,
                                    size: 14,
                                    color: c.accent,
                                  ),
                                ],
                              ],
                            ),
                          ),
                        );
                      },
                    ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              child: MaeveGlassTextButton(
                label: '设置',
                icon: Icons.settings_outlined,
                haptic: MaeveHaptic.tick,
                onPressed: () {
                  widget.onClose();
                  AppPage.open(context, const SettingsHubPage());
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

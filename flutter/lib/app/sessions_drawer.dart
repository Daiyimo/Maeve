import 'package:flutter/material.dart';

import 'maeve_controller.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/conversation.dart';
import '../widgets/glass_surface.dart';
import '../widgets/maeve_mark.dart';
import '../ui/common/app_page.dart';
import '../ui/common/app_sheet.dart';
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
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(ids.length == 1 ? '删除这段对话？' : '删除选中的 ${ids.length} 段对话？'),
        content: const Text('对话会从设备上的记录中移除，无法恢复。'),
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
    for (final id in ids) {
      controller.delete(id);
    }
    setState(() {
      _selected.clear();
      _selecting = false;
    });
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
    return MaeveGlassSurface(
      tier: MaeveGlassTier.sheet,
      shape: const RoundedRectangleBorder(),
      framed: false,
      child: SafeArea(
        right: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 8, 16),
              child: Row(
                children: [
                  const MaeveMark(size: 24),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      _selecting ? '已选 ${_selected.length}' : 'Maeve',
                      style: MaeveType.title.copyWith(color: c.ink),
                    ),
                  ),
                  IconButton(
                    tooltip: _selecting ? '取消多选' : '关闭对话列表',
                    onPressed: _selecting
                        ? () => setState(() {
                            _selecting = false;
                            _selected.clear();
                          })
                        : widget.onClose,
                    icon: Icon(Icons.close_rounded, size: 20, color: c.ink2),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18),
              child: _selecting
                  ? FilledButton.icon(
                      onPressed: _selected.isEmpty
                          ? null
                          : () =>
                                _confirmDelete(controller, _selected.toList()),
                      icon: const Icon(Icons.delete_outline_rounded, size: 18),
                      label: const Text('删除选中对话'),
                    )
                  : FilledButton.icon(
                      onPressed: () {
                        controller.create();
                        widget.onClose();
                      },
                      icon: const Icon(Icons.add_rounded, size: 18),
                      label: const Text('新对话'),
                      key: const ValueKey('new-conversation'),
                    ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 16, 18, 8),
              child: TextField(
                key: const ValueKey('conversation-search'),
                onChanged: controller.setSearch,
                decoration: const InputDecoration(
                  hintText: '搜索对话',
                  prefixIcon: Icon(Icons.search_rounded, size: 18),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(18, 6, 18, 16),
              child: Row(
                children: [
                  for (final item in [(0, '全部'), (1, '置顶')]) ...[
                    ChoiceChip(
                      label: Text(item.$2),
                      selected: controller.listFilter == item.$1,
                      onSelected: (_) => controller.setFilter(item.$1),
                    ),
                    const SizedBox(width: 8),
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
                      padding: const EdgeInsets.symmetric(horizontal: 12),
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
                            constraints: const BoxConstraints(minHeight: 64),
                            padding: const EdgeInsets.symmetric(
                              horizontal: 14,
                              vertical: 12,
                            ),
                            decoration: BoxDecoration(
                              color: controller.activeId == item.id
                                  ? c.accentSoft
                                  : null,
                              borderRadius: BorderRadius.circular(16),
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
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: MaeveType.body.copyWith(
                                      color: c.ink,
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
              padding: const EdgeInsets.all(18),
              child: TextButton.icon(
                onPressed: () {
                  widget.onClose();
                  AppPage.open(context, const SettingsHubPage());
                },
                icon: const Icon(Icons.settings_outlined, size: 18),
                label: const Text('设置'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

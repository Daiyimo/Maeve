import 'package:flutter/material.dart';

import '../widgets/maeve_mark.dart';

import 'maeve_controller.dart';

import '../design/maeve_colors.dart';
import '../design/maeve_motion.dart';
import '../design/maeve_shapes.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

import '../models/conversation.dart';
import '../ui/common/app_page.dart';
import '../ui/common/app_sheet.dart';
import '../ui/common/haptics.dart';
import '../ui/common/pressable.dart';
import '../ui/common/status.dart';
import '../ui/settings/settings_components.dart';
import '../ui/settings/settings_hub.dart';
import '../widgets/glass_surface.dart';

/// 会话抽屉:品牌行 + 新对话 + 搜索 + 工作区 + 状态筛选 + 分组列表 + 身份条。
///
/// 分组顺序固定:置顶 → 文件夹 → 按天(今天/昨天/更早)。未读有徽标,
/// 当前会话用强调色淡淡洗一层(不是描边)。
class SessionsDrawer extends StatefulWidget {
  const SessionsDrawer({super.key, required this.onClose, this.docked = false});

  final VoidCallback onClose;

  /// 宽屏常驻:不显示关闭钮,显示折叠钮。
  final bool docked;

  @override
  State<SessionsDrawer> createState() => _SessionsDrawerState();
}

class _SessionsDrawerState extends State<SessionsDrawer> {
  final TextEditingController _search = TextEditingController();
  bool _selecting = false;
  final Set<String> _selected = {};

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final theme = MaeveTheme.of(context);
    final colors = theme.colors;

    return MaeveGlassSurface(
      tier: MaeveGlassTier.sheet,
      shape: const RoundedRectangleBorder(),
      padding: EdgeInsets.zero,
      child: SafeArea(
        right: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _brandRow(context, controller, colors),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.s16,
                MaeveSpace.s8,
                MaeveSpace.s16,
                0,
              ),
              child: Column(
                children: [
                  if (_selecting)
                    _selectionBar(context, controller)
                  else
                    _newConversationButton(context, controller),
                  const SizedBox(height: MaeveSpace.s12),
                  _searchField(context, controller, colors),
                  const SizedBox(height: MaeveSpace.s8),
                  _workspaceRow(context, controller, colors),
                  const SizedBox(height: MaeveSpace.s4),
                  _filterStrip(context, controller),
                ],
              ),
            ),
            const SizedBox(height: MaeveSpace.s8),
            Expanded(child: _list(context, controller, colors)),
            if (controller.archivedConversations.isNotEmpty)
              _archivedHint(context, controller),
            _identityBar(context, controller, colors),
          ],
        ),
      ),
    );
  }

  Widget _brandRow(
    BuildContext context,
    MaeveController controller,
    MaeveColors colors,
  ) => Padding(
    padding: const EdgeInsets.fromLTRB(
      MaeveSpace.s16,
      MaeveSpace.s12,
      MaeveSpace.s8,
      0,
    ),
    child: Row(
      children: [
        const MaeveMark(size: 24),
        const SizedBox(width: MaeveSpace.s8),
        Text('MAEVE', style: MaeveType.eyebrow.copyWith(color: colors.ink3)),
        const Spacer(),
        Pressable(
          onTap: () {
            if (_selecting) {
              setState(() {
                _selecting = false;
                _selected.clear();
              });
            } else {
              widget.onClose();
            }
          },
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
          semanticLabel: _selecting ? '退出多选' : '收起列表',
          child: SizedBox.square(
            dimension: 40,
            child: Icon(
              _selecting
                  ? Icons.close
                  : (widget.docked ? Icons.menu_open : Icons.close),
              size: 19,
              color: colors.ink3,
            ),
          ),
        ),
      ],
    ),
  );

  Widget _newConversationButton(
    BuildContext context,
    MaeveController controller,
  ) {
    final colors = MaeveTheme.of(context).colors;
    return Pressable(
      key: const ValueKey('new-conversation'),
      onTap: () {
        controller.create();
        widget.onClose();
      },
      haptic: MaeveHaptic.light,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      child: Container(
        height: MaeveSize.button,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: colors.accent,
          borderRadius: BorderRadius.circular(MaeveRadius.md),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.add_rounded, size: 18, color: colors.onAccent),
            const SizedBox(width: 6),
            Text(
              '新对话',
              style: MaeveType.bodyStrong.copyWith(
                fontSize: 15.5,
                color: colors.onAccent,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _selectionBar(BuildContext context, MaeveController controller) {
    final colors = MaeveTheme.of(context).colors;
    return SizedBox(
      height: MaeveSize.button,
      child: Row(
        children: [
          Text(
            '已选 ${_selected.length}',
            style: MaeveType.bodyStrong.copyWith(color: colors.ink),
          ),
          const Spacer(),
          TextButton(
            onPressed: _selected.isEmpty
                ? null
                : () {
                    for (final id in _selected.toList()) {
                      controller.archive(id);
                    }
                    setState(() {
                      _selected.clear();
                      _selecting = false;
                    });
                  },
            child: const Text('归档'),
          ),
          TextButton(
            onPressed: _selected.isEmpty
                ? null
                : () {
                    for (final id in _selected.toList()) {
                      controller.delete(id);
                    }
                    setState(() {
                      _selected.clear();
                      _selecting = false;
                    });
                  },
            style: TextButton.styleFrom(foregroundColor: colors.bad),
            child: const Text('删除'),
          ),
        ],
      ),
    );
  }

  Widget _searchField(
    BuildContext context,
    MaeveController controller,
    MaeveColors colors,
  ) => Container(
    height: 40,
    padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.s12),
    decoration: BoxDecoration(
      color: colors.surface,
      borderRadius: BorderRadius.circular(MaeveRadius.md),
      border: Border.all(color: colors.edge, width: .5),
    ),
    child: Row(
      children: [
        Icon(Icons.search, size: 16, color: colors.ink3),
        const SizedBox(width: 6),
        Expanded(
          child: TextField(
            key: const ValueKey('conversation-search'),
            controller: _search,
            onChanged: controller.setSearch,
            style: MaeveType.body.copyWith(color: colors.ink),
            decoration: InputDecoration(
              isDense: true,
              border: InputBorder.none,
              hintText: '搜索对话',
              hintStyle: MaeveType.body.copyWith(color: colors.ink3),
            ),
          ),
        ),
      ],
    ),
  );

  Widget _workspaceRow(
    BuildContext context,
    MaeveController controller,
    MaeveColors colors,
  ) => Pressable(
    onTap: () => _showWorkspaceSheet(context, controller),
    borderRadius: BorderRadius.circular(MaeveRadius.sm),
    scale: false,
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 2),
      child: Row(
        children: [
          Icon(Icons.computer_rounded, size: 14, color: colors.ink3),
          const SizedBox(width: 6),
          Text('本机工作区', style: MaeveType.hint.copyWith(color: colors.ink3)),
          const SizedBox(width: 4),
          Icon(Icons.expand_more, size: 14, color: colors.ink3),
        ],
      ),
    ),
  );

  Widget _filterStrip(BuildContext context, MaeveController controller) {
    const labels = ['全部', '未读', '置顶'];
    final colors = MaeveTheme.of(context).colors;
    return Row(
      children: [
        for (var i = 0; i < labels.length; i++)
          Padding(
            padding: const EdgeInsets.only(right: 6),
            child: Pressable(
              onTap: () => controller.setFilter(i),
              borderRadius: BorderRadius.circular(MaeveRadius.pill),
              child: AnimatedContainer(
                duration: AppMotion.of(context).quick,
                padding: const EdgeInsets.symmetric(
                  horizontal: MaeveSpace.s10,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: controller.listFilter == i
                      ? colors.accentSoft
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(MaeveRadius.pill),
                ),
                child: Text(
                  labels[i],
                  style: MaeveType.label.copyWith(
                    fontSize: 12,
                    color: controller.listFilter == i
                        ? colors.accent
                        : colors.ink3,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }

  Widget _list(
    BuildContext context,
    MaeveController controller,
    MaeveColors colors,
  ) {
    final items = controller.visibleConversations;
    if (items.isEmpty) {
      return EmptyView(
        icon: Icons.forum_outlined,
        title: controller.search.isEmpty ? '还没有对话' : '没有匹配的对话',
        body: controller.search.isEmpty ? '从上面新建一个，或者换个筛选。' : '换个关键词试试。',
        compact: true,
      );
    }
    final sections = _group(controller, items);
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.s8,
        0,
        MaeveSpace.s8,
        MaeveSpace.s8,
      ),
      children: [
        for (final section in sections) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(10, MaeveSpace.s12, 10, 4),
            child: Text(
              section.label,
              style: MaeveType.label.copyWith(
                fontSize: 12.5,
                color: colors.ink3,
              ),
            ),
          ),
          for (final conversation in section.items)
            _ConversationRow(
              conversation: conversation,
              active: conversation.id == controller.activeId,
              selecting: _selecting,
              selected: _selected.contains(conversation.id),
              onTap: () {
                if (_selecting) {
                  setState(() {
                    if (!_selected.add(conversation.id)) {
                      _selected.remove(conversation.id);
                    }
                  });
                  return;
                }
                controller.select(conversation.id);
                widget.onClose();
              },
              onLongPress: () =>
                  _showRowSheet(context, controller, conversation),
            ),
        ],
      ],
    );
  }

  List<({String label, List<Conversation> items})> _group(
    MaeveController controller,
    List<Conversation> items,
  ) {
    final pinned = items.where((item) => item.pinned).toList();
    final folders = <String, List<Conversation>>{};
    final days = <String, List<Conversation>>{};
    for (final item in items.where((item) => !item.pinned)) {
      if (item.folder != null) {
        folders.putIfAbsent(item.folder!, () => []).add(item);
      } else {
        days.putIfAbsent(_dayLabel(item.updatedAt), () => []).add(item);
      }
    }
    return [
      if (pinned.isNotEmpty) (label: '置顶', items: pinned),
      for (final entry in folders.entries)
        (label: entry.key, items: entry.value),
      for (final entry in days.entries) (label: entry.key, items: entry.value),
    ];
  }

  String _dayLabel(DateTime time) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final day = DateTime(time.year, time.month, time.day);
    final diff = today.difference(day).inDays;
    if (diff <= 0) return '今天';
    if (diff == 1) return '昨天';
    return '更早';
  }

  Widget _archivedHint(BuildContext context, MaeveController controller) {
    final colors = MaeveTheme.of(context).colors;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.s16,
        0,
        MaeveSpace.s16,
        MaeveSpace.s4,
      ),
      child: Pressable(
        onTap: () => _showArchivedSheet(context, controller),
        borderRadius: BorderRadius.circular(MaeveRadius.sm),
        scale: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 6),
          child: Row(
            children: [
              Icon(Icons.inventory_2_outlined, size: 14, color: colors.ink3),
              const SizedBox(width: 6),
              Text(
                '已归档 ${controller.archivedConversations.length}',
                style: MaeveType.hint.copyWith(color: colors.ink3),
              ),
              const Spacer(),
              Icon(Icons.chevron_right, size: 16, color: colors.ink3),
            ],
          ),
        ),
      ),
    );
  }

  Widget _identityBar(
    BuildContext context,
    MaeveController controller,
    MaeveColors colors,
  ) => Container(
    padding: const EdgeInsets.fromLTRB(
      MaeveSpace.s16,
      MaeveSpace.s8,
      MaeveSpace.s8,
      MaeveSpace.s8,
    ),
    decoration: BoxDecoration(
      border: Border(top: BorderSide(color: colors.hair, width: .5)),
    ),
    child: Row(
      children: [
        StatusDot(
          tone: controller.connected ? MaeveTone.ready : MaeveTone.idle,
          size: 6,
        ),
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            '本机 · Android',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: MaeveType.hint.copyWith(color: colors.ink3),
          ),
        ),
        const Spacer(),
        Pressable(
          onTap: () {
            widget.onClose();
            AppPage.open(context, const SettingsHubPage());
          },
          borderRadius: BorderRadius.circular(MaeveRadius.pill),
          semanticLabel: '设置',
          child: SizedBox.square(
            dimension: 36,
            child: Icon(Icons.settings_outlined, size: 18, color: colors.ink2),
          ),
        ),
      ],
    ),
  );

  Future<void> _showRowSheet(
    BuildContext context,
    MaeveController controller,
    Conversation conversation,
  ) => showMaeveSheet<void>(
    context: context,
    builder: (context) => AppSheet(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SheetTitleBar(title: conversation.title, subtitle: '会话操作'),
          const SizedBox(height: MaeveSpace.s8),
          ListPageRow(
            title: conversation.pinned ? '取消置顶' : '置顶',
            leading: Icon(
              conversation.pinned ? Icons.push_pin_outlined : Icons.push_pin,
              size: 19,
            ),
            onTap: () {
              controller.togglePin(conversation.id);
              Navigator.of(context).pop();
            },
          ),
          ListPageRow(
            title: '多选',
            leading: const Icon(Icons.checklist_rounded, size: 19),
            onTap: () {
              Navigator.of(context).pop();
              setState(() => _selecting = true);
            },
          ),
          ListPageRow(
            title: '归档',
            leading: const Icon(Icons.inventory_2_outlined, size: 19),
            onTap: () {
              controller.archive(conversation.id);
              Navigator.of(context).pop();
            },
          ),
          ListPageRow(
            title: '删除',
            leading: Icon(
              Icons.delete_outline,
              size: 19,
              color: MaeveTheme.of(context).colors.bad,
            ),
            onTap: () {
              controller.delete(conversation.id);
              Navigator.of(context).pop();
            },
          ),
        ],
      ),
    ),
  );

  Future<void> _showArchivedSheet(
    BuildContext context,
    MaeveController controller,
  ) => showMaeveSheet<void>(
    context: context,
    builder: (context) => AppSheet(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SheetTitleBar(title: '已归档', subtitle: '恢复后回到列表'),
          const SizedBox(height: MaeveSpace.s8),
          for (final conversation in controller.archivedConversations)
            ListPageRow(
              title: conversation.title,
              trailing: TextButton(
                onPressed: () {
                  controller.restore(conversation.id);
                  Navigator.of(context).pop();
                },
                child: const Text('恢复'),
              ),
            ),
        ],
      ),
    ),
  );

  Future<void> _showWorkspaceSheet(
    BuildContext context,
    MaeveController controller,
  ) => showMaeveSheet<void>(
    context: context,
    builder: (context) => AppSheet(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SheetTitleBar(title: '工作区', subtitle: '会话按工作区分开'),
          const SizedBox(height: MaeveSpace.s8),
          ListPageRow(
            title: '本机',
            subtitle: '运行在这台 Android 设备上',
            selected: true,
            leading: const Icon(Icons.computer_rounded, size: 19),
            trailing: const StatusChip(label: '当前', tone: MaeveTone.ready),
            onTap: () => Navigator.of(context).pop(),
          ),
          ListPageRow(
            title: '远程主机',
            subtitle: '接入后显示已配对的机器',
            leading: const Icon(Icons.dns_outlined, size: 19),
            trailing: const StatusChip(label: '未配置'),
            onTap: () {
              Navigator.of(context).pop();
              AppPage.open(context, const SettingsHubPage());
            },
          ),
        ],
      ),
    ),
  );
}

class _ConversationRow extends StatelessWidget {
  const _ConversationRow({
    required this.conversation,
    required this.active,
    required this.selecting,
    required this.selected,
    required this.onTap,
    required this.onLongPress,
  });

  final Conversation conversation;
  final bool active;
  final bool selecting;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onLongPress;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return Padding(
      padding: const EdgeInsets.only(bottom: 2),
      child: Pressable(
        onTap: onTap,
        onLongPress: onLongPress,
        scale: false,
        borderRadius: BorderRadius.circular(MaeveRadius.sm),
        child: AnimatedContainer(
          duration: AppMotion.of(context).quick,
          padding: const EdgeInsets.fromLTRB(
            10,
            MaeveSpace.s8,
            10,
            MaeveSpace.s8,
          ),
          decoration: BoxDecoration(
            color: selected
                ? colors.accent.withValues(alpha: .16)
                : (active ? colors.accentSoft : null),
            borderRadius: BorderRadius.circular(MaeveRadius.sm),
          ),
          child: Row(
            children: [
              if (selecting)
                Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: Icon(
                    selected
                        ? Icons.check_circle_rounded
                        : Icons.radio_button_unchecked,
                    size: 17,
                    color: selected ? colors.accent : colors.ink3,
                  ),
                ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        if (conversation.pinned)
                          Padding(
                            padding: const EdgeInsets.only(right: 4),
                            child: Icon(
                              Icons.push_pin,
                              size: 11,
                              color: colors.ink3,
                            ),
                          ),
                        Expanded(
                          child: Text(
                            conversation.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: MaeveType.body.copyWith(
                              color: colors.ink,
                              fontWeight: active
                                  ? FontWeight.w500
                                  : FontWeight.w400,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 1),
                    Text(
                      _time(conversation.updatedAt),
                      style: MaeveType.monoSmall.copyWith(color: colors.ink3),
                    ),
                  ],
                ),
              ),
              if (conversation.unread > 0)
                Container(
                  margin: const EdgeInsets.only(left: 6),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 5,
                    vertical: 1,
                  ),
                  decoration: BoxDecoration(
                    color: colors.accent,
                    borderRadius: BorderRadius.circular(MaeveRadius.pill),
                  ),
                  child: Text(
                    '${conversation.unread}',
                    style: MaeveType.monoSmall.copyWith(
                      fontSize: 10,
                      color: colors.onAccent,
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  String _time(DateTime time) {
    final now = DateTime.now();
    final diff = now.difference(time);
    if (diff.inMinutes < 1) return '刚刚';
    if (diff.inMinutes < 60) return '${diff.inMinutes} 分钟前';
    if (diff.inHours < 24) {
      return '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}';
    }
    if (diff.inDays == 1) return '昨天';
    return '${time.month} 月 ${time.day} 日';
  }
}

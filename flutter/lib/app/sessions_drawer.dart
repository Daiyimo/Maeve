import 'package:flutter/material.dart';

import '../design/maeve_motion.dart';
import '../design/maeve_tokens.dart';
import '../models/conversation.dart';
import '../ui/common/app_page.dart';
import '../ui/common/app_sheet.dart';
import '../ui/common/haptics.dart';
import '../ui/common/pressable.dart';
import '../ui/common/status.dart';
import '../ui/settings/settings_components.dart';
import '../ui/settings/settings_hub.dart';
import '../widgets/glass_surface.dart';
import '../widgets/maeve_mark.dart';
import 'maeve_controller.dart';

/// B6 会话抽屉:品牌行 + 新对话 + 搜索 + 工作区 + 状态筛选 + 分组列表 + 身份条。
///
/// 分组顺序固定:置顶 → 文件夹 → 按天(今天/昨天/更早)。未读有徽标,
/// 当前会话用主色 9% 软填充(不是描边),按压时压到 18%。
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
    final surfaces = MaeveSurfaces.of(context);
    final scheme = Theme.of(context).colorScheme;

    return MaeveGlassSurface(
      tier: MaeveGlassTier.sheet,
      borderRadius: BorderRadius.zero,
      padding: EdgeInsets.zero,
      child: SafeArea(
        right: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _brandRow(context, controller, scheme),
            Padding(
              padding: const EdgeInsets.fromLTRB(
                MaeveSpace.lg,
                MaeveSpace.sm,
                MaeveSpace.lg,
                0,
              ),
              child: Column(
                children: [
                  if (_selecting)
                    _selectionBar(context, controller)
                  else
                    _newConversationButton(context, controller),
                  const SizedBox(height: MaeveSpace.md),
                  _searchField(context, controller, scheme),
                  const SizedBox(height: MaeveSpace.md),
                  _workspaceRow(context, controller, scheme),
                  const SizedBox(height: MaeveSpace.sm),
                  _filterStrip(context, controller),
                ],
              ),
            ),
            const SizedBox(height: MaeveSpace.sm),
            Expanded(child: _list(context, controller, scheme)),
            if (controller.archivedConversations.isNotEmpty)
              _archivedHint(context, controller),
            _identityBar(context, controller, scheme, surfaces),
          ],
        ),
      ),
    );
  }

  Widget _brandRow(
    BuildContext context,
    MaeveController controller,
    ColorScheme scheme,
  ) => Padding(
    padding: const EdgeInsets.fromLTRB(
      MaeveSpace.lg,
      MaeveSpace.md,
      MaeveSpace.sm,
      0,
    ),
    child: Row(
      children: [
        const MaeveMark(size: 26),
        const SizedBox(width: MaeveSpace.sm),
        Text('MAEVE', style: maeveEyebrow(context)),
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
            dimension: 36,
            child: Icon(
              _selecting
                  ? Icons.close
                  : (widget.docked ? Icons.menu_open : Icons.close),
              size: 19,
              color: scheme.onSurfaceVariant,
            ),
          ),
        ),
      ],
    ),
  );

  Widget _newConversationButton(
    BuildContext context,
    MaeveController controller,
  ) => Pressable(
    key: const ValueKey('new-conversation'),
    onTap: () {
      controller.create();
      widget.onClose();
    },
    haptic: MaeveHaptic.light,
    borderRadius: BorderRadius.circular(MaeveRadius.md),
    child: Container(
      height: MaeveLayout.touch,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.primary,
        borderRadius: BorderRadius.circular(MaeveRadius.md),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.add_rounded,
            size: 18,
            color: Theme.of(context).colorScheme.onPrimary,
          ),
          const SizedBox(width: 6),
          Text(
            '新对话',
            style: Theme.of(context).textTheme.labelLarge
                ?.copyWith(color: Theme.of(context).colorScheme.onPrimary),
          ),
        ],
      ),
    ),
  );

  Widget _selectionBar(BuildContext context, MaeveController controller) =>
      SizedBox(
        height: MaeveLayout.touch,
        child: Row(
          children: [
            Text(
              '已选 ${_selected.length}',
              style: Theme.of(context).textTheme.titleSmall,
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
              style: TextButton.styleFrom(
                foregroundColor: Theme.of(context).colorScheme.error,
              ),
              child: const Text('删除'),
            ),
          ],
        ),
      );

  Widget _searchField(
    BuildContext context,
    MaeveController controller,
    ColorScheme scheme,
  ) => Container(
    height: 38,
    padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.md),
    decoration: BoxDecoration(
      color: scheme.onSurface.withValues(alpha: .05),
      borderRadius: BorderRadius.circular(MaeveRadius.pill),
      border: Border.all(
        color: scheme.outline.withValues(alpha: .5),
        width: .5,
      ),
    ),
    child: Row(
      children: [
        Icon(Icons.search, size: 16, color: scheme.onSurfaceVariant),
        const SizedBox(width: 6),
        Expanded(
          child: TextField(
            key: const ValueKey('conversation-search'),
            controller: _search,
            onChanged: controller.setSearch,
            style: Theme.of(context).textTheme.bodyMedium,
            decoration: InputDecoration(
              isDense: true,
              border: InputBorder.none,
              hintText: '搜索对话',
              hintStyle: Theme.of(context).textTheme.bodyMedium
                  ?.copyWith(color: scheme.onSurfaceVariant),
            ),
          ),
        ),
      ],
    ),
  );

  Widget _workspaceRow(
    BuildContext context,
    MaeveController controller,
    ColorScheme scheme,
  ) => Pressable(
    onTap: () => _showWorkspaceSheet(context, controller),
    borderRadius: BorderRadius.circular(MaeveRadius.sm),
    scale: false,
    child: Padding(
      padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 4),
      child: Row(
        children: [
          Icon(
            Icons.computer_rounded,
            size: 15,
            color: scheme.onSurfaceVariant,
          ),
          const SizedBox(width: 6),
          Text('本机工作区', style: Theme.of(context).textTheme.bodySmall),
          const SizedBox(width: 4),
          Icon(Icons.expand_more, size: 15, color: scheme.onSurfaceVariant),
        ],
      ),
    ),
  );

  Widget _filterStrip(BuildContext context, MaeveController controller) {
    const labels = ['全部', '未读', '置顶'];
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
                  horizontal: MaeveSpace.md,
                  vertical: 4,
                ),
                decoration: BoxDecoration(
                  color: controller.listFilter == i
                      ? Theme.of(context).colorScheme.primary
                            .withValues(alpha: .14)
                      : Colors.transparent,
                  borderRadius: BorderRadius.circular(MaeveRadius.pill),
                ),
                child: Text(
                  labels[i],
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: controller.listFilter == i
                        ? Theme.of(context).colorScheme.primary
                        : Theme.of(context).colorScheme.onSurfaceVariant,
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
    ColorScheme scheme,
  ) {
    final items = controller.visibleConversations;
    if (items.isEmpty) {
      return Column(
        children: [
          Expanded(
            child: EmptyView(
              icon: Icons.forum_outlined,
              title: controller.search.isEmpty ? '还没有对话' : '没有匹配的对话',
              body: controller.search.isEmpty ? '从上面新建一个，或者换个筛选。' : '换个关键词试试。',
              compact: true,
            ),
          ),
        ],
      );
    }
    final sections = _group(controller, items);
    return ListView(
      padding: const EdgeInsets.fromLTRB(
        MaeveSpace.sm,
        0,
        MaeveSpace.sm,
        MaeveSpace.sm,
      ),
      children: [
        for (final section in sections) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(10, MaeveSpace.sm, 10, 4),
            child: Text(section.label, style: maeveEyebrow(context)),
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

  Widget _archivedHint(BuildContext context, MaeveController controller) =>
      Padding(
        padding: const EdgeInsets.fromLTRB(
          MaeveSpace.lg,
          0,
          MaeveSpace.lg,
          MaeveSpace.xs,
        ),
        child: Pressable(
          onTap: () => _showArchivedSheet(context, controller),
          borderRadius: BorderRadius.circular(MaeveRadius.sm),
          scale: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: Row(
              children: [
                Icon(
                  Icons.inventory_2_outlined,
                  size: 15,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 6),
                Text(
                  '已归档 ${controller.archivedConversations.length}',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const Spacer(),
                Icon(
                  Icons.chevron_right,
                  size: 16,
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ],
            ),
          ),
        ),
      );

  Widget _identityBar(
    BuildContext context,
    MaeveController controller,
    ColorScheme scheme,
    MaeveSurfaces surfaces,
  ) => Container(
    padding: const EdgeInsets.fromLTRB(
      MaeveSpace.lg,
      MaeveSpace.sm,
      MaeveSpace.sm,
      MaeveSpace.sm,
    ),
    decoration: BoxDecoration(
      border: Border(
        top: BorderSide(color: scheme.outline.withValues(alpha: .5), width: .5),
      ),
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
            style: Theme.of(context).textTheme.bodySmall,
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
            dimension: 32,
            child: Icon(
              Icons.settings_outlined,
              size: 18,
              color: scheme.onSurfaceVariant,
            ),
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
          const SizedBox(height: MaeveSpace.sm),
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
              color: Theme.of(context).colorScheme.error,
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
          const SizedBox(height: MaeveSpace.sm),
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
          const SizedBox(height: MaeveSpace.sm),
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
    final scheme = Theme.of(context).colorScheme;
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
            MaeveSpace.sm + 2,
            MaeveSpace.sm,
            MaeveSpace.sm,
            MaeveSpace.sm,
          ),
          decoration: BoxDecoration(
            // 当前会话是主色 9% 软填充;多选选中压到 18%。
            color: selected
                ? scheme.primary.withValues(alpha: .18)
                : (active ? scheme.primary.withValues(alpha: .09) : null),
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
                    color: selected ? scheme.primary : scheme.onSurfaceVariant,
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
                              size: 12,
                              color: scheme.onSurfaceVariant,
                            ),
                          ),
                        Expanded(
                          child: Text(
                            conversation.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodyMedium
                                ?.copyWith(
                                  fontWeight: active
                                      ? FontWeight.w600
                                      : FontWeight.w400,
                                ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 1),
                    Text(
                      _time(conversation.updatedAt),
                      style: Theme.of(context).textTheme.bodySmall,
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
                    color: scheme.primary,
                    borderRadius: BorderRadius.circular(MaeveRadius.pill),
                  ),
                  child: Text(
                    '${conversation.unread}',
                    style: MaeveFonts.mono(
                      context,
                      size: 10,
                      color: scheme.onPrimary,
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
    if (diff.inHours < 1) return '${diff.inMinutes} 分钟前';
    if (diff.inDays < 1) {
      return '${time.hour}:${time.minute.toString().padLeft(2, '0')}';
    }
    if (diff.inDays == 1) return '昨天';
    return '${time.month} 月 ${time.day} 日';
  }
}

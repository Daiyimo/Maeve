import 'package:flutter/material.dart';

import '../../app/maeve_controller.dart';
import '../../design/maeve_surfaces.dart';
import '../../design/maeve_tokens.dart';
import '../common/app_page.dart';
import '../common/status.dart';
import '../settings/settings_components.dart';

/// 记忆:B11 的独立页。原型阶段只在进程内存里。
class MemoryPage extends StatelessWidget {
  const MemoryPage({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = MaeveScope.of(context);
    final surfaces = MaeveSurfaces.of(context);
    return AppPage.list(
      title: '记忆',
      subtitle: '把有用的上下文留在身边',
      actions: [
        IconButton(
          key: const ValueKey('add-memory'),
          tooltip: '添加记忆',
          onPressed: () => _addMemory(context, controller),
          icon: const Icon(Icons.add, size: 20),
        ),
      ],
      children: [
        if (controller.memories.isEmpty)
          EmptyView(
            icon: Icons.bookmark_border_rounded,
            title: '还没有保存任何内容',
            body: '写一条便签，记住这个会话里的偏好或项目细节。',
            action: FilledButton.icon(
              onPressed: () => _addMemory(context, controller),
              icon: const Icon(Icons.add, size: 18),
              label: const Text('添加一条记忆'),
            ),
          )
        else
          SettingsSection(
            title: '已保存',
            rows: [
              for (final memory in controller.memories)
                SettingsTile(
                  icon: Icons.bookmark_outline_rounded,
                  title: memory,
                  trailing: IconButton(
                    tooltip: '删除记忆',
                    onPressed: () => controller.removeMemory(memory),
                    icon: const Icon(Icons.delete_outline, size: 18),
                  ),
                ),
            ],
          ),
        const SizedBox(height: MaeveSpace.lg),
        BriefNotice(
          text: '记忆只存在于本次 Flutter 会话，尚未写入磁盘。',
          icon: Icons.info_outline,
          tone: MaeveTone.idle,
        ),
        const SizedBox(height: MaeveSpace.xxl),
        Text(
          '对比度 ${surfaces.chrome.fill.a.toStringAsFixed(2)} · 玻璃 ${surfaces.blurEnabled ? '开' : '关'}',
          style: MaeveFonts.mono(
            context,
            size: 10,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }

  void _addMemory(BuildContext context, MaeveController controller) {
    final input = TextEditingController();
    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('添加一条记忆'),
        content: TextField(
          key: const ValueKey('memory-input'),
          controller: input,
          autofocus: true,
          maxLines: 3,
          decoration: const InputDecoration(hintText: '写一条有用的便签'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () {
              controller.addMemory(input.text);
              Navigator.of(context).pop();
            },
            child: const Text('保存'),
          ),
        ],
      ),
    );
  }
}

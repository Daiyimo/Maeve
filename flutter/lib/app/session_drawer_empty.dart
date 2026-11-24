import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

class DrawerEmptyState extends StatelessWidget {
  const DrawerEmptyState({super.key, required this.searching});

  final bool searching;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SizedBox(height: 24),
          Icon(Icons.chat_bubble_outline_rounded, color: c.accent, size: 25),
          const SizedBox(height: 16),
          Text(
            searching ? '没有匹配的对话' : '还没有对话',
            style: MaeveType.bodyStrong.copyWith(color: c.ink),
          ),
          const SizedBox(height: 8),
          Text(
            '每一次新的想法，都可以从这里开始。',
            style: MaeveType.caption.copyWith(color: c.ink3),
          ),
        ],
      ),
    );
  }
}

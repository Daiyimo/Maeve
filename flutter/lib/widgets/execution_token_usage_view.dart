import 'package:flutter/material.dart';

import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';
import '../models/token_usage.dart';

/// 执行记录里的 Token 读数：统一 M，保留 1 位，总数加粗。
class ExecutionTokenUsageView extends StatelessWidget {
  const ExecutionTokenUsageView({
    super.key,
    required this.usage,
    this.compact = false,
  });

  final TokenUsage usage;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    String amount(int value) =>
        '${(value.clamp(0, 1 << 62) / 1000000).toStringAsFixed(1)} M';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          usage.reported ? amount(usage.total) : '用量尚未返回',
          style: (compact ? MaeveType.caption : MaeveType.title).copyWith(
            color: c.ink,
            fontWeight: FontWeight.w600,
          ),
        ),
        if (usage.reported) ...[
          const SizedBox(height: 6),
          Text(
            '输入 ${amount(usage.input)} · 输出 ${amount(usage.output)} · 缓存命中 ${amount(usage.cached)}',
            style: MaeveType.hint.copyWith(color: c.ink2, height: 1.5),
          ),
        ],
        if (usage.unreported > 0)
          Text(
            '${usage.unreported} 次调用未返回完整用量',
            style: MaeveType.hint.copyWith(color: c.ink3),
          ),
      ],
    );
  }
}

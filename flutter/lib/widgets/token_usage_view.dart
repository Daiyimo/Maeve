import 'package:flutter/material.dart';

import '../models/token_usage.dart';
import '../design/maeve_theme.dart';
import '../design/maeve_type.dart';

class TokenUsageView extends StatelessWidget {
  const TokenUsageView({super.key, required this.usage, this.compact = false});
  final TokenUsage usage;
  final bool compact;
  @override
  Widget build(BuildContext context) {
    final c = MaeveTheme.of(context).colors;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          usage.reported ? '${tokenNumber(usage.total)} tokens' : '用量尚未返回',
          style: (compact ? MaeveType.caption : MaeveType.title).copyWith(
            color: c.ink,
          ),
        ),
        if (usage.reported) ...[
          const SizedBox(height: 6),
          Text(
            '输入 ${tokenNumber(usage.input)} · 输出 ${tokenNumber(usage.output)} · 缓存命中 ${tokenNumber(usage.cached)}',
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

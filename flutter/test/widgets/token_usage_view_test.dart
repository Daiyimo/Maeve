import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/models/token_usage.dart';
import 'package:maeve_flutter/widgets/execution_token_usage_view.dart';
import 'package:maeve_flutter/widgets/token_usage_view.dart';

import '../ui/components_test.dart' show host;

void main() {
  testWidgets('Execution token usage uses bold millions with one decimal', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(
        ExecutionTokenUsageView(
          usage: TokenUsage(input: 1234567, output: 234567, cached: 100000),
        ),
      ),
    );
    expect(find.text('1.5 M'), findsOneWidget);
    expect(find.text('输入 1.2 M · 输出 0.2 M · 缓存命中 0.1 M'), findsOneWidget);
    final total = tester.widget<Text>(find.text('1.5 M'));
    expect(total.style?.fontWeight, FontWeight.w600);
  });

  testWidgets('Settings token usage keeps exact token counts', (tester) async {
    await tester.pumpWidget(
      host(
        TokenUsageView(usage: TokenUsage(input: 100, output: 20, cached: 90)),
      ),
    );
    expect(find.text('120 tokens'), findsOneWidget);
    expect(find.textContaining('缓存命中 90'), findsOneWidget);
  });
}

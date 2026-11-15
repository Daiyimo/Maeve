import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/models/execution.dart';
import 'package:maeve_flutter/widgets/execution_view.dart';

import 'components_test.dart' show host;

void main() {
  List<ExecutionStep> steps(int n) => List.generate(
    n,
    (i) => ExecutionStep(
      step: i + 1,
      title: '正在执行第 ${i + 1} 步',
      reason: '检查当前页面',
      detail: '操作已送达',
    ),
  );

  testWidgets('Live records stay bounded and follow the newest step', (
    tester,
  ) async {
    await tester.pumpWidget(host(LiveExecutionFeed(steps: steps(3))));
    final rect = tester.getRect(
      find.byKey(const ValueKey('live-execution-feed')),
    );
    await tester.pumpWidget(host(LiveExecutionFeed(steps: steps(100))));
    await tester.pump();
    expect(
      tester.getRect(find.byKey(const ValueKey('live-execution-feed'))).height,
      rect.height,
    );
    expect(find.text('100  正在执行第 100 步').hitTestable(), findsOneWidget);
    expect(find.text('01  正在执行第 1 步'), findsNothing);
    expect(find.text('正在跟随最新'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Reading older records keeps position until return to latest', (
    tester,
  ) async {
    await tester.pumpWidget(host(LiveExecutionFeed(steps: steps(20))));
    final list = find.byKey(const ValueKey('live-execution-list'));
    final scroll = find.descendant(of: list, matching: find.byType(Scrollable));
    final position = tester.state<ScrollableState>(scroll).position;
    position.jumpTo(460);
    await tester.pump();
    expect(find.text('回到最新'), findsOneWidget);
    final old = position.pixels;
    await tester.pumpWidget(host(LiveExecutionFeed(steps: steps(23))));
    await tester.pump();
    expect(position.pixels, closeTo(old + 3 * 92, .1));
    await tester.tap(find.text('回到最新'));
    await tester.pumpAndSettle();
    expect(position.pixels, 0);
    expect(find.text('23  正在执行第 23 步').hitTestable(), findsOneWidget);
  });

  testWidgets('Long records fit large text without overflowing', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(
        Builder(
          builder: (context) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: TextScaler.linear(2)),
            child: LiveExecutionFeed(
              steps: [
                ExecutionStep(
                  step: 1,
                  title: '这是需要跨越多行的很长的执行步骤名称' * 5,
                  reason: '检查页面' * 10,
                  detail: '操作详情' * 10,
                ),
              ],
            ),
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
  });
}

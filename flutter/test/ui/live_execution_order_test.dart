import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/models/execution.dart';
import 'package:maeve_flutter/widgets/execution_view.dart';

import 'components_test.dart' show host;

void main() {
  testWidgets('Live records run from top to bottom', (tester) async {
    final steps = List.generate(
      3,
      (i) => ExecutionStep(step: i + 1, title: '步骤 ${i + 1}'),
    );
    await tester.pumpWidget(
      host(
        SizedBox(
          height: 400,
          child: LiveExecutionFeed(steps: steps, height: 380),
        ),
      ),
    );
    await tester.pump();
    final first = tester.getTopLeft(find.text('01  步骤 1')).dy;
    final second = tester.getTopLeft(find.text('02  步骤 2')).dy;
    final third = tester.getTopLeft(find.text('03  步骤 3')).dy;
    expect(first, lessThan(second));
    expect(second, lessThan(third));
  });
}

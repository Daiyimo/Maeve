import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/widgets/live_elapsed_text.dart';

import 'components_test.dart' show host;

void main() {
  testWidgets('Waiting model elapsed time ticks and stays bold', (
    tester,
  ) async {
    await tester.pumpWidget(host(LiveElapsedText(elapsedMs: 60000)));
    expect(find.text('1 分 0 秒'), findsOneWidget);
    await tester.pump(const Duration(seconds: 6));
    expect(find.text('1 分 6 秒'), findsOneWidget);
    final timer = tester.widget<Text>(find.text('1 分 6 秒'));
    expect(timer.style?.fontWeight, FontWeight.w600);
  });
}

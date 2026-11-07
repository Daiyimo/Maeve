import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/main.dart';

void main() {
  testWidgets('Maeve shell renders the host greeting', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    expect(find.text('A HOST WITH A VOICE'), findsOneWidget);
    expect(find.text('Ask Maeve anything'), findsOneWidget);
  });
}

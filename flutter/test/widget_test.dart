import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/main.dart';

void main() {
  testWidgets('chat shell renders composer and greeting', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    expect(find.text('A HOST WITH A VOICE'), findsOneWidget);
    expect(find.text('One thought, from here.'), findsOneWidget);
    expect(find.byKey(const ValueKey('message-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('send-message')), findsOneWidget);
  });

  testWidgets('drawer navigates to settings', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    expect(find.text('PREFERENCES'), findsOneWidget);
    expect(
      find.text('Flutter interface · Android capabilities'),
      findsOneWidget,
    );
    expect(
      find.textContaining('Runtime backdrop blur is disabled'),
      findsOneWidget,
    );
  });

  testWidgets('sending a message shows user and demo reply', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.enterText(
      find.byKey(const ValueKey('message-input')),
      'A test message',
    );
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();
    expect(find.text('A test message'), findsOneWidget);
    expect(find.text('Maeve is thinking'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pumpAndSettle();
    expect(find.text('I have it. The next move is yours.'), findsOneWidget);
  });

  testWidgets('creates a new conversation from the drawer', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('new-conversation')));
    await tester.pumpAndSettle();
    expect(find.text('New conversation'), findsOneWidget);
  });
}

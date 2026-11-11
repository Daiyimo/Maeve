import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/ui/settings/licenses_page.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const notices = {
    'entries': [
      {
        'name': 'Maeve',
        'license': 'Apache-2.0',
        'note': 'Preserved attribution',
      },
    ],
    'sourceOffer': 'Complete corresponding source is available.',
    'documents': [
      {'name': 'NOTICE.txt', 'text': 'Full packaged attribution text'},
    ],
  };
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  Future<void> mount(WidgetTester tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    addTearDown(
      () => messenger.setMockMethodCallHandler(MaeveBridge.methods, null),
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveTheme(brightness: Brightness.dark),
        builder: (context, child) => MaeveThemeScope(
          brightness: Brightness.dark,
          accent: deriveAccent(MaeveAccentPreset.reverie),
          child: child!,
        ),
        home: const LicensesPage(),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('Packaged notice text is reachable from the Flutter list', (
    tester,
  ) async {
    messenger.setMockMethodCallHandler(MaeveBridge.methods, (call) async {
      expect(call.method, 'legal_notices');
      return notices;
    });
    await mount(tester);
    expect(find.text('Maeve'), findsOneWidget);
    expect(find.text('Apache-2.0'), findsOneWidget);
    await tester.tap(find.text('NOTICE.txt'));
    await tester.pumpAndSettle();
    expect(find.text('Full packaged attribution text'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('A failed notice read offers a retry and recovers', (
    tester,
  ) async {
    var attempts = 0;
    messenger.setMockMethodCallHandler(MaeveBridge.methods, (_) async {
      if (++attempts == 1) throw PlatformException(code: 'read_failed');
      return notices;
    });
    await mount(tester);
    expect(find.text('许可说明暂时未能读取'), findsOneWidget);
    await tester.tap(find.text('重试'));
    await tester.pumpAndSettle();
    expect(attempts, 2);
    expect(find.text('NOTICE.txt'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_tokens.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

/// Hosts [child] in a themed app with optional surface overrides, so the glass
/// chain can be inspected without standing up the whole shell.
Widget _host(
  Widget child, {
  Brightness brightness = Brightness.dark,
  bool blurEnabled = true,
  bool highContrast = false,
}) {
  return MaterialApp(
    theme: maeveTheme(brightness, blurEnabled: blurEnabled),
    home: MediaQuery(
      data: MediaQueryData(highContrast: highContrast),
      child: Builder(builder: (context) => child),
    ),
  );
}

void main() {
  testWidgets('chrome glass samples the backdrop, card glass does not', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        const Column(
          children: [
            MaeveGlassSurface(
              tier: MaeveGlassTier.chrome,
              child: Text('chrome'),
            ),
            MaeveGlassSurface(tier: MaeveGlassTier.card, child: Text('card')),
          ],
        ),
      ),
    );
    expect(find.text('chrome'), findsOneWidget);
    expect(find.text('card'), findsOneWidget);
    // Only the chrome tier blurs: cards lie on a static backdrop and a
    // BackdropFilter there would burn GPU for nothing.
    expect(find.byType(BackdropFilter), findsOneWidget);
  });

  testWidgets('reduce transparency drops every backdrop filter', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          child: Text('chrome'),
        ),
        blurEnabled: false,
      ),
    );
    expect(find.text('chrome'), findsOneWidget);
    expect(find.byType(BackdropFilter), findsNothing);
  });

  testWidgets('high contrast drops the backdrop filter too', (tester) async {
    await tester.pumpWidget(
      _host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.sheet,
          child: Text('sheet'),
        ),
        highContrast: true,
      ),
    );
    expect(find.text('sheet'), findsOneWidget);
    expect(find.byType(BackdropFilter), findsNothing);
  });

  testWidgets('a moving glass keeps its filter but stops sampling', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          settled: 0,
          child: Text('moving'),
        ),
      ),
    );
    // The filter layer stays mounted (the subtree is not rebuilt), it just
    // stops sampling the backdrop while the surface moves.
    final filter = tester.widget<BackdropFilter>(find.byType(BackdropFilter));
    expect(filter.enabled, isFalse);
    expect(find.text('moving'), findsOneWidget);
  });

  testWidgets('blur override can force a card to sample the backdrop', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          blur: true,
          child: Text('forced'),
        ),
      ),
    );
    expect(find.byType(BackdropFilter), findsOneWidget);
  });

  test('settled fill interpolates from the opaque fallback to the glass', () {
    final style = MaeveSurfaces.dark.chrome;
    final moving = Color.lerp(style.fallbackFill, style.fill, 0)!;
    final settled = Color.lerp(style.fallbackFill, style.fill, 1)!;
    // While moving the surface hides behind a near-opaque fill, so the blur
    // appearing later never pops; settled it is the plain glass fill.
    expect(moving.a, greaterThan(settled.a));
    expect(settled, style.fill);
  });

  test('glass style lerp interpolates every channel', () {
    final a = MaeveSurfaces.light.chrome;
    final b = MaeveSurfaces.dark.chrome;
    final mid = MaeveGlassStyle.lerp(a, b, 0.5);
    expect(mid.blurSigma, closeTo((a.blurSigma + b.blurSigma) / 2, 1e-9));
    expect(mid.saturation, closeTo((a.saturation + b.saturation) / 2, 1e-9));
    expect(mid.fill, Color.lerp(a.fill, b.fill, 0.5));
    expect(mid.fallbackFill, Color.lerp(a.fallbackFill, b.fallbackFill, 0.5));
    expect(mid.hairline, Color.lerp(a.hairline, b.hairline, 0.5));
    expect(mid.shadows.length, a.shadows.length);
  });

  test(
    'forBrightness without blur keeps the tier data but flips the switch',
    () {
      final reduced = MaeveSurfaces.forBrightness(
        Brightness.dark,
        blurEnabled: false,
      );
      expect(reduced.blurEnabled, isFalse);
      // The style data survives so switching back needs no rebuild of styles.
      expect(reduced.chrome.blurSigma, MaeveSurfaces.dark.chrome.blurSigma);
      expect(MaeveSurfaces.dark.blurEnabled, isTrue);
      expect(MaeveSurfaces.light.chrome.blurSigma, greaterThan(0));
      expect(MaeveSurfaces.light.card.blurSigma, 0);
    },
  );

  test('surfaces lerp crosses brightness without losing the backdrop', () {
    final mid = MaeveSurfaces.light.lerp(MaeveSurfaces.dark, 0.5);
    expect(
      mid.backdropTop,
      Color.lerp(
        MaeveSurfaces.light.backdropTop,
        MaeveSurfaces.dark.backdropTop,
        0.5,
      ),
    );
    expect(
      mid.chrome.fill,
      Color.lerp(
        MaeveSurfaces.light.chrome.fill,
        MaeveSurfaces.dark.chrome.fill,
        0.5,
      ),
    );
    expect(mid.blurEnabled, isTrue);
  });
}

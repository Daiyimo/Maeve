import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/widgets/maeve_mark.dart';

Widget host(MarkState state, {bool reduce = false, bool visible = true}) =>
    MaterialApp(
      home: MediaQuery(
        data: MediaQueryData(disableAnimations: reduce),
        child: MaeveThemeScope(
          brightness: Brightness.dark,
          accent: deriveAccent(MaeveAccentPreset.reverie),
          child: TickerMode(
            enabled: visible,
            child: Center(child: MaeveMark(size: 96, state: state)),
          ),
        ),
      ),
    );

void main() {
  testWidgets('Idle is still; real work animates; finishing stops the ticker', (
    tester,
  ) async {
    await tester.pumpWidget(host(MarkState.idle));
    await tester.pumpAndSettle();
    expect(tester.binding.hasScheduledFrame, isFalse);
    await tester.pumpWidget(host(MarkState.running));
    await tester.pump(const Duration(milliseconds: 500));
    final paint = tester.widget<CustomPaint>(
      find.descendant(
        of: find.byType(MaeveMark),
        matching: find.byType(CustomPaint),
      ),
    );
    final painter = paint.painter! as MaeveMarkPainter;
    expect(painter.running, isTrue);
    final start = painter.phase!.value;
    await tester.pump(const Duration(milliseconds: 500));
    expect(painter.phase!.value, isNot(start));
    await tester.pumpWidget(host(MarkState.idle));
    await tester.pumpAndSettle();
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  for (final (reduce, visible) in [(true, true), (false, false)]) {
    testWidgets(
      'Executing glyph stays still when reduce=$reduce, visible=$visible',
      (tester) async {
        await tester.pumpWidget(
          host(MarkState.running, reduce: reduce, visible: visible),
        );
        await tester.pumpAndSettle();
        expect(tester.binding.hasScheduledFrame, isFalse);
        final painter =
            tester
                    .widget<CustomPaint>(
                      find.descendant(
                        of: find.byType(MaeveMark),
                        matching: find.byType(CustomPaint),
                      ),
                    )
                    .painter!
                as MaeveMarkPainter;
        expect(painter.running, isTrue);
        expect(painter.phase!.value, 0);
      },
    );
  }

  test(
    'All sizes and both states leave the eclipse core transparent',
    () async {
      for (final side in [16, 88, 256]) {
        for (final running in [false, true]) {
          final recorder = ui.PictureRecorder();
          MaeveMarkPainter(
            const Color(0xFFCBB7FF),
            running: running,
          ).paint(Canvas(recorder), Size.square(side.toDouble()));
          final picture = recorder.endRecording();
          final bitmap = await picture.toImage(side, side);
          final pixels = (await bitmap.toByteData(
            format: ui.ImageByteFormat.rawRgba,
          ))!;
          var painted = 0;
          for (var y = 0; y < side; y++) {
            for (var x = 0; x < side; x++) {
              final alpha = pixels.getUint8((y * side + x) * 4 + 3);
              if (alpha > 0) painted++;
              if ((Offset(x + .5, y + .5) - Offset(side / 2, side / 2))
                      .distance <
                  side * .30) {
                expect(alpha, 0, reason: 'Core at $side, running=$running');
              }
            }
          }
          expect(painted, greaterThan(side));
          bitmap.dispose();
          picture.dispose();
        }
      }
    },
  );
}

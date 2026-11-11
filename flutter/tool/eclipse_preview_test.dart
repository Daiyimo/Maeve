import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_colors.dart';
import 'package:maeve_flutter/widgets/maeve_mark.dart';

/// Optional renderer preview. These are actual vector frames, not generated art.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const output = String.fromEnvironment(
    'MAEVE_ECLIPSE_OUTPUT',
    defaultValue: 'build/eclipse-previews',
  );
  test(
    'Render equilibrium and executing phase samples in both modes',
    () async {
      final accent = deriveAccent(MaeveAccentPreset.reverie);
      for (final dark in [true, false]) {
        final colors = dark
            ? MaeveColors.dark(accent)
            : MaeveColors.light(accent);
        for (var frame = -1; frame < 20; frame++) {
          final recorder = ui.PictureRecorder();
          final canvas = Canvas(recorder)
            ..drawColor(colors.bg, BlendMode.src)
            ..translate(32, 32);
          MaeveMarkPainter(
            colors.accent,
            running: frame >= 0,
            phase: AlwaysStoppedAnimation(frame < 0 ? 0 : frame / 20),
          ).paint(canvas, const Size.square(448));
          final picture = recorder.endRecording();
          final bitmap = await picture.toImage(512, 512);
          final png = await bitmap.toByteData(format: ui.ImageByteFormat.png);
          final mode = dark ? 'dark' : 'light';
          final name = frame < 0
              ? 'idle'
              : 'running-${frame.toString().padLeft(2, '0')}';
          final file = File('$output/$mode-$name.png');
          await file.parent.create(recursive: true);
          await file.writeAsBytes(png!.buffer.asUint8List());
          bitmap.dispose();
          picture.dispose();
        }
      }
    },
  );
}

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_colors.dart';
import 'package:maeve_flutter/design/maeve_surfaces.dart';

Map<String, dynamic> _tokens() {
  for (final path in [
    '../shared/glass_tokens.json',
    'shared/glass_tokens.json',
  ]) {
    final file = File(path);
    if (file.existsSync()) {
      return jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
    }
  }
  throw StateError('shared/glass_tokens.json not found');
}

double _num(Object? value) => (value as num).toDouble();

void _expectClose(String label, double actual, double expected) {
  expect(actual, closeTo(expected, 0.0001), reason: label);
}

void main() {
  test('Flutter tiers mirror shared/glass_tokens.json for every density', () {
    final tokens = _tokens();
    final densities = {
      for (final density in MaeveGlassDensity.values)
        density.id: tokens['densityScale'][density.id] as Map<String, dynamic>,
    };
    final tiers = tokens['tiers'] as Map<String, dynamic>;
    final tone = tokens['tone'] as Map<String, dynamic>;
    final accent = deriveAccent(MaeveAccentPreset.reverie);

    for (final dark in [false, true]) {
      final mode = dark ? 'dark' : 'light';
      final colors = dark
          ? MaeveColors.dark(accent)
          : MaeveColors.light(accent);
      for (final density in MaeveGlassDensity.values) {
        final scale = densities[density.id]!;
        final surfaces = buildSurfaces(colors, density: density);
        for (final tier in MaeveGlassTier.values) {
          final expected = tiers[tier.name] as Map<String, dynamic>;
          final style = surfaces.tier(tier);
          final prefix = '${tier.name}/$mode/${density.id}';
          _expectClose(
            '$prefix fill',
            style.fill.a,
            (_num(expected['fillAlpha'][mode]) * _num(scale['tint'])).clamp(
              0,
              .92,
            ),
          );
          _expectClose(
            '$prefix blur',
            style.blurSigma,
            _num(expected['blurSigma']) * _num(scale['blur']),
          );
          _expectClose(
            '$prefix fallback blur',
            style.fallbackBlurSigma,
            _num(expected['fallbackBlurSigma']) * _num(scale['blur']),
          );
          _expectClose(
            '$prefix saturation',
            style.saturation,
            _num(expected['saturation']) * _num(scale['saturation']),
          );
          _expectClose(
            '$prefix refraction',
            style.refractionStrength,
            _num(expected['refractionStrength']) * _num(scale['refraction']),
          );
          _expectClose(
            '$prefix refraction width',
            style.refractionWidth,
            _num(expected['refractionWidth']) * _num(scale['refraction']),
          );
          _expectClose(
            '$prefix aberration',
            style.chromaticAberration,
            _num(expected['chromaticAberration']) * _num(scale['refraction']),
          );
          _expectClose(
            '$prefix specular',
            style.specularStrength,
            _num(expected['specularStrength']) * _num(scale['light']),
          );
          _expectClose(
            '$prefix hairline',
            style.hairline.a,
            _num(expected['hairlineAlpha'][mode]),
          );
          _expectClose(
            '$prefix rim',
            style.rim.a,
            _num(expected['rimAlpha'][mode]) * _num(scale['light']),
          );
          _expectClose(
            '$prefix sheen',
            style.sheen.a,
            _num(expected['sheenAlpha'][mode]) * _num(scale['light']),
          );
          _expectClose(
            '$prefix rim fraction',
            style.rimFraction,
            _num(expected['rimFraction']),
          );
          _expectClose(
            '$prefix sheen stop',
            style.sheenStop,
            _num(expected['sheenStop']),
          );
          _expectClose('$prefix band', style.band, _num(expected['band']));
          _expectClose(
            '$prefix catch',
            style.catchLight.a,
            _num(expected['catchLightAlpha'][mode]) * _num(scale['light']),
          );
          _expectClose(
            '$prefix bevel top',
            style.bevelTop.a,
            _num(expected['bevelTopAlpha'][mode]) * _num(scale['light']),
          );
          _expectClose(
            '$prefix bevel side',
            style.bevelSide.a,
            _num(expected['bevelSideAlpha'][mode]) * _num(scale['light']),
          );
          _expectClose(
            '$prefix bevel base',
            style.bevelBase.a,
            _num(expected['bevelBaseAlpha'][mode]) * _num(scale['light']),
          );
          _expectClose(
            '$prefix brightness',
            style.brightness,
            _num(tone[mode]['brightness']),
          );
          _expectClose(
            '$prefix contrast',
            style.contrast,
            _num(tone[mode]['contrast']),
          );
          _expectClose('$prefix fallback alpha', style.fallbackFill.a, 1);

          final shadow = expected['shadow'] as Map<String, dynamic>;
          final shadowAlpha = _num((shadow['alpha'] as Map)[mode]);
          if (_num(shadow['blur']) <= 0 || shadowAlpha <= 0) {
            expect(style.shadows, isEmpty, reason: '$prefix shadow');
          } else {
            expect(style.shadows, hasLength(1), reason: '$prefix shadow');
            _expectClose(
              '$prefix shadow blur',
              style.shadows.single.blurRadius,
              _num(shadow['blur']),
            );
            _expectClose(
              '$prefix shadow y',
              style.shadows.single.offset.dy,
              _num(shadow['offsetY']),
            );
            _expectClose(
              '$prefix shadow alpha',
              style.shadows.single.color.a,
              shadowAlpha,
            );
          }
        }
      }
    }
  });

  test('disabled blur keeps opaque fallback and zero sampling sigmas', () {
    final colors = MaeveColors.dark(deriveAccent(MaeveAccentPreset.reverie));
    final surfaces = buildSurfaces(colors, blurEnabled: false);
    for (final tier in MaeveGlassTier.values) {
      final style = surfaces.tier(tier);
      expect(style.blurSigma, 0);
      expect(style.fallbackBlurSigma, 0);
      expect(style.refractionStrength, 0);
      expect(style.refractionWidth, 0);
      expect(style.chromaticAberration, 0);
      expect(style.specularStrength, 0);
      expect(style.fallbackFill.a, 1);
    }
  });
}

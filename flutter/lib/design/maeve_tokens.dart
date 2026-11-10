import 'package:flutter/material.dart';

import 'maeve_surfaces.dart';

abstract final class MaeveTokens {
  static const accent = Color(0xFF9B72B8);
  static const accentStrong = Color(0xFF76508F);
  static const success = Color(0xFF5F8C73);
  static const warning = Color(0xFFC18A43);
  static const danger = Color(0xFFB65C64);

  static const lightCanvas = Color(0xFFF3F2F7);
  static const lightSurface = Color(0xFFF9F8FB);
  static const lightElevated = Color(0xFFFFFFFF);
  static const lightInk = Color(0xFF26232D);
  static const lightSecondary = Color(0xFF706B79);
  static const lightLine = Color(0x1F514B5C);

  static const darkCanvas = Color(0xFF201D27);
  static const darkSurface = Color(0xFF292530);
  static const darkElevated = Color(0xFF332E3A);
  static const darkInk = Color(0xFFF1EEF5);
  static const darkSecondary = Color(0xFFB5AFBE);
  static const darkLine = Color(0x35D7CFDF);

  static const pagePadding = 22.0;
  static const sidebarWidth = 286.0;
  static const railWidth = 72.0;
  static const contentMaxWidth = 860.0;
  static const radiusSmall = 10.0;
  static const radiusMedium = 16.0;
  static const radiusLarge = 24.0;
  static const radiusPill = 999.0;

  static const motionFast = Duration(milliseconds: 140);
  static const motionStandard = Duration(milliseconds: 220);
  static const motionSheet = Duration(milliseconds: 320);
}

ThemeData maeveTheme(Brightness brightness, {bool blurEnabled = true}) {
  final dark = brightness == Brightness.dark;
  final canvas = dark ? MaeveTokens.darkCanvas : MaeveTokens.lightCanvas;
  final surface = dark ? MaeveTokens.darkSurface : MaeveTokens.lightSurface;
  final elevated = dark ? MaeveTokens.darkElevated : MaeveTokens.lightElevated;
  final ink = dark ? MaeveTokens.darkInk : MaeveTokens.lightInk;
  final secondary = dark
      ? MaeveTokens.darkSecondary
      : MaeveTokens.lightSecondary;
  final line = dark ? MaeveTokens.darkLine : MaeveTokens.lightLine;
  final scheme = ColorScheme(
    brightness: brightness,
    primary: dark ? const Color(0xFFC6A4DB) : MaeveTokens.accentStrong,
    onPrimary: Colors.white,
    secondary: MaeveTokens.accent,
    onSecondary: Colors.white,
    error: MaeveTokens.danger,
    onError: Colors.white,
    surface: surface,
    onSurface: ink,
    surfaceContainerHighest: elevated,
    onSurfaceVariant: secondary,
    outline: line,
    outlineVariant: line,
  );
  final baseText = Typography.material2021(platform: TargetPlatform.android)
      .black;

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: canvas,
    dividerColor: line,
    extensions: <ThemeExtension<dynamic>>[
      MaeveSurfaces.forBrightness(brightness, blurEnabled: blurEnabled),
    ],
    textTheme: baseText
        .apply(bodyColor: ink, displayColor: ink)
        .copyWith(
          bodyMedium: baseText.bodyMedium?.copyWith(color: ink, height: 1.5),
          bodySmall: baseText.bodySmall?.copyWith(
            color: secondary,
            height: 1.4,
          ),
          titleLarge: baseText.titleLarge?.copyWith(
            color: ink,
            fontWeight: FontWeight.w600,
          ),
          titleMedium: baseText.titleMedium?.copyWith(
            color: ink,
            fontWeight: FontWeight.w600,
          ),
        ),
    appBarTheme: AppBarTheme(
      backgroundColor: canvas,
      foregroundColor: ink,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
    ),
    cardTheme: CardThemeData(
      color: elevated,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(MaeveTokens.radiusMedium),
        side: BorderSide(color: line),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: elevated.withValues(alpha: dark ? .62 : .78),
      hintStyle: TextStyle(color: secondary),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(MaeveTokens.radiusMedium),
        borderSide: BorderSide(color: line),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(MaeveTokens.radiusMedium),
        borderSide: BorderSide(color: line),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(MaeveTokens.radiusMedium),
        borderSide: BorderSide(color: scheme.primary, width: 1.5),
      ),
    ),
    listTileTheme: ListTileThemeData(
      iconColor: secondary,
      textColor: ink,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(MaeveTokens.radiusSmall),
      ),
    ),
  );
}

TextStyle maeveEyebrow(BuildContext context) => TextStyle(
  color: Theme.of(context).colorScheme.onSurfaceVariant,
  fontSize: 10,
  fontWeight: FontWeight.w700,
  letterSpacing: 1.2,
);

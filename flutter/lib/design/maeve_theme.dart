import 'package:flutter/material.dart';

import 'maeve_accent.dart';
import 'maeve_colors.dart';
import 'maeve_motion.dart';
import 'maeve_semantic.dart';
import 'maeve_shapes.dart';
import 'maeve_surfaces.dart';
import 'maeve_type.dart';

/// 一屏所需的全部设计数据:颜色、语义色、四档表面、当前动效。
///
/// 用 InheritedWidget 而不是 ThemeExtension:换配色是"当场换、不重建界面"，
/// 不需要把四十个色位逐个 lerp 一遍。
@immutable
class MaeveThemeData {
  const MaeveThemeData({
    required this.colors,
    required this.semantic,
    required this.surfaces,
    required this.motion,
    required this.accent,
  });

  final MaeveColors colors;
  final MaeveSemantic semantic;
  final MaeveSurfaces surfaces;
  final AppMotion motion;
  final MaeveAccent accent;
}

/// 设计系统的入口:页面一律从这里取色与表面,不写死色值。
class MaeveTheme extends InheritedWidget {
  const MaeveTheme({super.key, required this.data, required super.child});

  final MaeveThemeData data;

  static MaeveThemeData of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<MaeveTheme>();
    assert(scope != null, 'MaeveTheme 不在树上:页面必须挂在 MaeveApp 下');
    return scope!.data;
  }

  /// 只读一次,不订阅(回调里取色用)。
  static MaeveThemeData read(BuildContext context) {
    final scope = context.getInheritedWidgetOfExactType<MaeveTheme>();
    assert(scope != null, 'MaeveTheme 不在树上');
    return scope!.data;
  }

  @override
  bool updateShouldNotify(MaeveTheme old) =>
      old.data != data || old.data.colors != data.colors;
}

/// 装配一套主题:手写 ColorScheme(设计契约明确禁止 `ColorScheme.fromSeed`)。
///
/// [blurEnabled] 为 false 时四档表面全部走 fallbackFill,且不建模糊层。
ThemeData maeveTheme({
  required Brightness brightness,
  MaeveAccent? accent,
  bool blurEnabled = true,
  bool reducedMotion = false,
}) {
  final resolvedAccent = accent ?? deriveAccent(MaeveAccentPreset.reverie);
  final colors = brightness == Brightness.dark
      ? MaeveColors.dark(resolvedAccent)
      : MaeveColors.light(resolvedAccent);
  final semantic = MaeveSemantic.of(brightness);
  final text = maeveTextTheme(colors);

  final scheme = ColorScheme(
    brightness: brightness,
    primary: colors.accent,
    onPrimary: colors.onAccent,
    primaryContainer: colors.accentContainer,
    onPrimaryContainer: colors.onAccentContainer,
    secondary: colors.accent,
    onSecondary: colors.onAccent,
    secondaryContainer: colors.accentSoft,
    onSecondaryContainer: colors.ink,
    tertiary: semantic.info,
    onTertiary: colors.bg,
    error: colors.bad,
    onError: colors.onAccent,
    surface: colors.bg,
    onSurface: colors.ink,
    surfaceContainerLowest: colors.surfaceLowest,
    surfaceContainerLow: colors.surface,
    surfaceContainer: colors.surface,
    surfaceContainerHigh: colors.surfaceHigh,
    surfaceContainerHighest: colors.surfaceHighest,
    onSurfaceVariant: colors.ink3,
    outline: colors.ink3,
    outlineVariant: colors.hair,
    shadow: colors.floatShadow,
    scrim: colors.scrim,
    inverseSurface: colors.ink,
    onInverseSurface: colors.bg,
    inversePrimary: colors.accent,
    // 深度靠发丝线与外阴影,不靠 tint。
    surfaceTint: Colors.transparent,
  );

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: colors.bg,
    canvasColor: colors.bg,
    dividerColor: colors.hair,
    splashFactory: NoSplash.splashFactory,
    textTheme: text,
    primaryTextTheme: text,
    appBarTheme: AppBarTheme(
      backgroundColor: colors.bg.withValues(alpha: .92),
      foregroundColor: colors.ink,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      centerTitle: true,
      titleTextStyle: text.titleLarge,
    ),
    cardTheme: CardThemeData(
      color: colors.surface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: MaeveShapes.outlined(colors.glassEdge, radius: MaeveRadius.lg),
      margin: EdgeInsets.zero,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: colors.surfaceHighest,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: MaeveShapes.dialog,
      titleTextStyle: text.titleLarge,
      contentTextStyle: text.bodyMedium,
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: RoundedRectangleBorder(),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: false,
      isDense: true,
      hintStyle: MaeveType.body.copyWith(color: colors.ink3),
      contentPadding: const EdgeInsets.symmetric(
        horizontal: MaeveSpace.s12,
        vertical: MaeveSpace.s10,
      ),
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: colors.accent,
        foregroundColor: colors.onAccent,
        minimumSize: const Size(0, MaeveSize.button),
        padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.s20),
        shape: MaeveShapes.button,
        textStyle: MaeveType.bodyStrong.copyWith(fontSize: 15.5),
        elevation: 0,
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: colors.accent,
        minimumSize: const Size(0, MaeveSize.touch),
        shape: MaeveShapes.button,
        textStyle: MaeveType.bodyStrong.copyWith(fontSize: 14.5),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: colors.ink,
        minimumSize: const Size(0, MaeveSize.button),
        side: BorderSide(color: colors.edge, width: .75),
        shape: MaeveShapes.button,
        textStyle: MaeveType.bodyStrong.copyWith(fontSize: 15.5),
      ),
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
        (states) =>
            states.contains(WidgetState.selected) ? colors.onAccent : colors.bg,
      ),
      trackColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? colors.accent
            : colors.greySoft,
      ),
      trackOutlineColor: WidgetStateProperty.all(Colors.transparent),
    ),
    listTileTheme: ListTileThemeData(
      iconColor: colors.ink2,
      textColor: colors.ink,
      minVerticalPadding: MaeveSpace.s12,
      shape: MaeveShapes.tile,
      titleTextStyle: text.bodyLarge,
      subtitleTextStyle: text.bodySmall,
      contentPadding: const EdgeInsets.symmetric(horizontal: MaeveSpace.s16),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: colors.isDark ? colors.surfaceHighest : colors.ink,
      contentTextStyle: MaeveType.body.copyWith(
        color: colors.isDark ? colors.ink : colors.bg,
      ),
      behavior: SnackBarBehavior.floating,
      elevation: 0,
      shape: MaeveShapes.button,
      insetPadding: const EdgeInsets.all(MaeveSpace.s16),
    ),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(
        color: (colors.isDark ? colors.surfaceHighest : colors.ink).withValues(
          alpha: .94,
        ),
        borderRadius: BorderRadius.circular(MaeveRadius.sm),
      ),
      textStyle: MaeveType.caption.copyWith(
        color: colors.isDark ? colors.ink : colors.bg,
      ),
      waitDuration: const Duration(milliseconds: 600),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: colors.accent,
      linearTrackColor: colors.hair,
      circularTrackColor: Colors.transparent,
    ),
    dividerTheme: DividerThemeData(
      color: colors.hair,
      thickness: .5,
      space: .5,
    ),
    sliderTheme: SliderThemeData(
      activeTrackColor: colors.accent,
      inactiveTrackColor: colors.hair,
      thumbColor: colors.accent,
      overlayColor: colors.accent.withValues(alpha: .12),
    ),
  );
}

/// 把一屏包进设计系统。
class MaeveThemeScope extends StatelessWidget {
  const MaeveThemeScope({
    super.key,
    required this.brightness,
    required this.accent,
    this.blurEnabled = true,
    required this.child,
  });

  final Brightness brightness;
  final MaeveAccent accent;
  final bool blurEnabled;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colors = brightness == Brightness.dark
        ? MaeveColors.dark(accent)
        : MaeveColors.light(accent);
    return MaeveTheme(
      data: MaeveThemeData(
        colors: colors,
        semantic: MaeveSemantic.of(brightness),
        surfaces: buildSurfaces(colors, blurEnabled: blurEnabled),
        motion: AppMotion(MediaQuery.disableAnimationsOf(context)),
        accent: accent,
      ),
      child: child,
    );
  }
}

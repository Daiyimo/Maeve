import 'package:flutter/material.dart';

import 'maeve_palette.dart';
import 'oklch.dart';

/// 圆角六档(连续曲率 Squircle,不是普通圆角矩形)。用途固定,不随页面变。
abstract final class MaeveRadius {
  /// 行内代码、角标、进度条端头。
  static const xs = 6.0;

  /// Chip、工具图标块、小按钮、菜单项高亮。
  static const sm = 10.0;

  /// 卡片、代码井、输入框、菜单、主按钮。
  static const md = 14.0;

  /// 用户气泡、审批卡、设置分组卡。
  static const lg = 20.0;

  /// 输入条(composer)、底部弹层、对话框。
  static const xl = 28.0;

  /// 胶囊:半径 = 高一半。
  static const pill = 999.0;
}

/// 派生形状。裁切与形状必须来自同一个实例,否则模糊边界与填充边界差 1px。
abstract final class MaeveShapes {
  static const card = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(MaeveRadius.md)),
  );
  static const bubble = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(MaeveRadius.lg)),
  );
  static const dialog = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(MaeveRadius.xl)),
  );

  /// 底部弹层 / 抽屉:只有顶两角是 xl。
  static const sheet = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.vertical(top: Radius.circular(MaeveRadius.xl)),
  );

  /// 只有顶角的卡片(输入条与列表的贴边件)。
  static const topRounded = RoundedSuperellipseBorder(
    borderRadius: BorderRadius.vertical(top: Radius.circular(MaeveRadius.xl)),
  );

  static RoundedSuperellipseBorder round(double radius) =>
      RoundedSuperellipseBorder(
        borderRadius: BorderRadius.all(Radius.circular(radius)),
      );

  static RoundedSuperellipseBorder outlined(
    Color color, {
    double radius = MaeveRadius.md,
  }) => RoundedSuperellipseBorder(
    borderRadius: BorderRadius.all(Radius.circular(radius)),
    side: BorderSide(color: color),
  );
}

/// 间距:4 的倍数为主。
abstract final class MaeveSpace {
  static const xxs = 2.0;
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 20.0;
  static const xxl = 24.0;
  static const xxxl = 32.0;

  /// 手机左右留白。
  static const gutter = 16.0;

  /// 同轮内消息间距。
  static const inTurn = 8.0;

  /// 换轮间距。
  static const betweenTurns = 24.0;

  /// 正文最大阅读宽(平板/横屏居中)。
  static const readingWidth = 720.0;

  /// 浮动件离阅读列的边距。
  static const floatInset = 12.0;

  /// 浮动件宽度(=720−2×12)。
  static const floatingWidth = 696.0;
}

/// 布局常量。
abstract final class MaeveLayout {
  /// 断点:≥840 列表常驻,<840 抽屉拉开。
  static const breakpoint = 840.0;

  static const railWidth = 72.0;
  static const sidebarWidth = 286.0;

  /// 聊天顶栏高(胶囊高 44 + 上下留白)。
  static const headerHeight = 56.0;

  /// 二级页标题栏高。
  static const pageBarHeight = 56.0;

  /// 最小可点区域。
  static const touch = 44.0;
}

/// 语义色 + 主题装配。
abstract final class MaeveTokens {
  /// 浅色主色白字、深色主色同色相深墨字(见 [MaevePalette])。
  static Color success(Brightness brightness) => brightness == Brightness.dark
      ? Oklch.toColor(l: .78, c: .09, hue: 155)
      : Oklch.toColor(l: .50, c: .085, hue: 155);

  static Color warning(Brightness brightness) => brightness == Brightness.dark
      ? Oklch.toColor(l: .80, c: .10, hue: 72)
      : Oklch.toColor(l: .52, c: .11, hue: 72);

  static Color danger(Brightness brightness) => brightness == Brightness.dark
      ? Oklch.toColor(l: .72, c: .11, hue: 22)
      : Oklch.toColor(l: .50, c: .13, hue: 22);
}

/// 装配主题:[palette] 决定配色,[blurEnabled] 决定玻璃能不能模糊。
ThemeData maeveTheme(
  Brightness brightness, {
  MaevePalette palette = MaevePalette.reverie,
  bool blurEnabled = true,
}) {
  final dark = brightness == Brightness.dark;
  final neutrals = palette.neutrals(brightness);
  final scheme = palette.scheme(brightness);
  final textTheme = maeveTextTheme(brightness, neutrals);

  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: scheme,
    scaffoldBackgroundColor: neutrals.canvas,
    canvasColor: neutrals.canvas,
    dividerColor: neutrals.line,
    splashFactory: NoSplash.splashFactory,
    extensions: <ThemeExtension<dynamic>>[
      palette.surfaces(brightness, blurEnabled: blurEnabled),
    ],
    textTheme: textTheme,
    primaryTextTheme: textTheme,
    appBarTheme: AppBarTheme(
      backgroundColor: Colors.transparent,
      foregroundColor: neutrals.ink,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      centerTitle: true,
      titleTextStyle: textTheme.titleMedium,
    ),
    cardTheme: CardThemeData(
      color: neutrals.surface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: MaeveShapes.outlined(neutrals.hairline),
      margin: EdgeInsets.zero,
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: neutrals.elevated,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: MaeveShapes.dialog,
      titleTextStyle: textTheme.titleLarge,
      contentTextStyle: textTheme.bodyMedium,
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: Colors.transparent,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: MaeveShapes.sheet,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: false,
      hintStyle: textTheme.bodyMedium?.copyWith(color: neutrals.tertiary),
      contentPadding: const EdgeInsets.symmetric(
        horizontal: MaeveSpace.md,
        vertical: MaeveSpace.md,
      ),
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        minimumSize: const Size(0, MaeveLayout.touch),
        padding: const EdgeInsets.symmetric(horizontal: MaeveSpace.xl),
        shape: MaeveShapes.round(MaeveRadius.md),
        textStyle: textTheme.labelLarge,
        elevation: 0,
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: scheme.primary,
        minimumSize: const Size(0, MaeveLayout.touch),
        shape: MaeveShapes.round(MaeveRadius.md),
        textStyle: textTheme.labelLarge,
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: neutrals.ink,
        minimumSize: const Size(0, MaeveLayout.touch),
        side: BorderSide(color: neutrals.line),
        shape: MaeveShapes.round(MaeveRadius.md),
        textStyle: textTheme.labelLarge,
      ),
    ),
    switchTheme: SwitchThemeData(
      thumbColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? scheme.onPrimary
            : neutrals.surface,
      ),
      trackColor: WidgetStateProperty.resolveWith(
        (states) => states.contains(WidgetState.selected)
            ? scheme.primary
            : neutrals.tertiary.withValues(alpha: .45),
      ),
      trackOutlineColor: WidgetStateProperty.all(Colors.transparent),
    ),
    listTileTheme: ListTileThemeData(
      iconColor: neutrals.secondary,
      textColor: neutrals.ink,
      minVerticalPadding: MaeveSpace.md,
      shape: MaeveShapes.round(MaeveRadius.sm),
      titleTextStyle: textTheme.bodyLarge,
      subtitleTextStyle: textTheme.bodySmall,
      contentPadding: const EdgeInsets.symmetric(horizontal: MaeveSpace.md),
    ),
    snackBarTheme: SnackBarThemeData(
      backgroundColor: dark ? neutrals.elevated : neutrals.ink,
      contentTextStyle: textTheme.bodyMedium?.copyWith(
        color: dark ? neutrals.ink : neutrals.canvas,
      ),
      behavior: SnackBarBehavior.floating,
      elevation: 0,
      shape: MaeveShapes.round(MaeveRadius.md),
      insetPadding: const EdgeInsets.all(MaeveSpace.lg),
    ),
    tooltipTheme: TooltipThemeData(
      decoration: BoxDecoration(
        color: (dark ? neutrals.elevated : neutrals.ink).withValues(alpha: .94),
        borderRadius: BorderRadius.circular(MaeveRadius.sm),
      ),
      textStyle: textTheme.bodySmall?.copyWith(
        color: dark ? neutrals.ink : neutrals.canvas,
      ),
      waitDuration: const Duration(milliseconds: 600),
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: scheme.primary,
      linearTrackColor: neutrals.line,
      circularTrackColor: Colors.transparent,
    ),
    dividerTheme: DividerThemeData(
      color: neutrals.line,
      thickness: .5,
      space: .5,
    ),
  );
}

/// 字重只用 400/600 两档(大号展示字 300),不用衬线标题。
TextTheme maeveTextTheme(Brightness brightness, MaeveNeutrals neutrals) {
  final ink = neutrals.ink;
  final secondary = neutrals.secondary;
  TextStyle style(
    double size,
    FontWeight weight, {
    double height = 1.45,
    Color? color,
    double spacing = 0,
  }) => TextStyle(
    fontSize: size,
    fontWeight: weight,
    height: height,
    color: color ?? ink,
    letterSpacing: spacing,
  );

  return TextTheme(
    displayLarge: style(34, FontWeight.w300, height: 1.2),
    displayMedium: style(28, FontWeight.w300, height: 1.25),
    displaySmall: style(24, FontWeight.w600, height: 1.3),
    headlineMedium: style(22, FontWeight.w600, height: 1.3),
    headlineSmall: style(20, FontWeight.w600, height: 1.35),
    titleLarge: style(18, FontWeight.w600, height: 1.35),
    titleMedium: style(16, FontWeight.w600, height: 1.4),
    titleSmall: style(14.5, FontWeight.w600, height: 1.35),
    bodyLarge: style(15.5, FontWeight.w400, height: 1.55),
    bodyMedium: style(14.5, FontWeight.w400, height: 1.5, spacing: .1),
    bodySmall: style(12.5, FontWeight.w400, height: 1.4, color: secondary),
    labelLarge: style(14, FontWeight.w600, height: 1.3),
    labelMedium: style(12, FontWeight.w600, height: 1.3, spacing: .2),
    labelSmall: style(
      11,
      FontWeight.w600,
      height: 1.3,
      spacing: .6,
      color: secondary,
    ),
  );
}

/// 小标签(全大写英文 / 中文眉标)。所有页面统一走这里,避免各页字重乱飞。
TextStyle maeveEyebrow(BuildContext context) {
  final theme = Theme.of(context);
  return (theme.textTheme.labelSmall ?? const TextStyle()).copyWith(
    color: theme.colorScheme.onSurfaceVariant,
    letterSpacing: 1.1,
  );
}

/// 等宽字体(MaeveSemantic)。打包 JetBrains Mono(OFL),关掉编程连字。
///
/// 业务代码**不要**写 `fontFamily` 字面量,统一走这个出口:
/// ```dart
/// Text('v0.1.0', style: MaeveFonts.mono(context))
/// ```
abstract final class MaeveFonts {
  static const family = 'JetBrainsMono';

  /// 版本号 / 统计 / 路径 / 代码。
  static TextStyle mono(
    BuildContext context, {
    double size = 12.5,
    FontWeight weight = FontWeight.w500,
    Color? color,
    double height = 1.4,
  }) {
    final theme = Theme.of(context);
    return TextStyle(
      fontFamily: family,
      fontSize: size,
      fontWeight: weight,
      height: height,
      color: color ?? theme.colorScheme.onSurface,
      // 数字等宽,版本号不跳动;连字关掉,`->` 与 `!=` 保持原样。
      fontFeatures: const [
        FontFeature.tabularFigures(),
        FontFeature.disable('liga'),
        FontFeature.disable('calt'),
      ],
    );
  }
}

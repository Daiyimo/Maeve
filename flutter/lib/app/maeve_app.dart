import 'package:flutter/material.dart';

import 'maeve_shell.dart';

import 'maeve_controller.dart';

import '../design/maeve_theme.dart';

import 'package:flutter_localizations/flutter_localizations.dart';

/// 应用外壳:主题、语言、全局状态。
class MaeveApp extends StatefulWidget {
  const MaeveApp({super.key});

  @override
  State<MaeveApp> createState() => _MaeveAppState();
}

class _MaeveAppState extends State<MaeveApp> {
  final MaeveController controller = MaeveController();

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller,
    builder: (context, _) => MaeveScope(
      controller: controller,
      child: MaterialApp(
        title: 'Maeve',
        debugShowCheckedModeBanner: false,
        themeMode: controller.mode,
        // 中文优先:非中文系统也要拿到中文字形,不能退到日文字形。
        locale: const Locale('zh', 'CN'),
        supportedLocales: const [Locale('zh', 'CN'), Locale('en', 'US')],
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        theme: maeveTheme(
          brightness: Brightness.light,
          accent: controller.accent,
          blurEnabled: !controller.reduceTransparency,
        ),
        darkTheme: maeveTheme(
          brightness: Brightness.dark,
          accent: controller.accent,
          blurEnabled: !controller.reduceTransparency,
        ),
        // 设计系统的入口放在 Theme 里面一层:这样亮度是**解析后**的
        // (跟随系统时也对),换配色当场生效。
        builder: (context, child) => MaeveThemeScope(
          brightness: Theme.of(context).brightness,
          accent: controller.accent,
          blurEnabled: !controller.reduceTransparency,
          child: child ?? const SizedBox.shrink(),
        ),
        home: const MaeveShell(),
      ),
    ),
  );
}

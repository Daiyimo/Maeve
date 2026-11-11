import 'dart:async';

import 'package:flutter/material.dart';

import 'maeve_shell.dart';

import 'maeve_controller.dart';

import '../bridge/maeve_bridge.dart';

import '../design/maeve_theme.dart';

import 'package:flutter_localizations/flutter_localizations.dart';

/// 应用外壳:主题、语言、全局状态。
class MaeveApp extends StatefulWidget {
  const MaeveApp({super.key, this.controller});

  /// 注入控制器(测试里带假桥用);不传就用真的。
  final MaeveController? controller;

  @override
  State<MaeveApp> createState() => _MaeveAppState();
}

class _MaeveAppState extends State<MaeveApp> {
  late final MaeveController controller =
      widget.controller ?? MaeveController();

  StreamSubscription? _entries;

  @override
  void initState() {
    super.initState();
    controller.attach();
    _entries = MaeveBridge.entries.listen(_onEntry);
  }

  /// 通知点击 / 深链接:{kind, threadId}。壳永显对话页,入口就是切到那个对话。
  void _onEntry(dynamic event) {
    if (event is! Map) return;
    final threadId = event['threadId'];
    if (threadId is! String || threadId.isEmpty) return;
    controller.openThread(threadId);
  }

  @override
  void dispose() {
    _entries?.cancel();
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

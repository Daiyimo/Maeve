import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/ui/settings/app_access_page.dart';
import 'package:maeve_flutter/ui/settings/settings_components.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('maeve/settings');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  Map<String, Object?> initialState() => {
    'available': true,
    'revision': 0,
    'visualFirstDefault': true,
    'apps': [
      {
        'packageName': 'com.tencent.jkchess',
        'label': '金铲铲之战',
        'blocked': false,
        'shareAllowed': false,
        'protected': false,
        'policySource': '默认允许',
        'visualFirstOverride': null,
        'visualFirst': true,
      },
      {
        'packageName': 'com.daiyimo.maeve',
        'label': 'Maeve',
        'blocked': false,
        'shareAllowed': false,
        'protected': true,
        'policySource': '默认允许',
        'visualFirstOverride': null,
        'visualFirst': true,
      },
    ],
  };

  Future<List<MethodCall>> mount(
    WidgetTester tester,
    Map<String, Object?> state, {
    Future<Map<String, Object?>> Function(MethodCall call)? onCall,
  }) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (onCall != null) return onCall(call);
      final args = call.arguments as Map? ?? const {};
      final action = args['action'] as String?;
      if (action == 'setVisualFirstDefault') {
        state['visualFirstDefault'] = args['enabled'] as bool;
        for (final app in state['apps'] as List) {
          if (app['visualFirstOverride'] == null) {
            app['visualFirst'] = state['visualFirstDefault'];
          }
        }
      }
      if (action == 'setVisualFirstOverride') {
        final pkg = args['packageName'] as String;
        final enabled = args['enabled'] as bool;
        for (final app in state['apps'] as List) {
          if (app['packageName'] == pkg) {
            app['visualFirstOverride'] = enabled;
            app['visualFirst'] = enabled;
          }
        }
      }
      if (action == 'clearVisualFirstOverride') {
        final pkg = args['packageName'] as String;
        for (final app in state['apps'] as List) {
          if (app['packageName'] == pkg) {
            app['visualFirstOverride'] = null;
            app['visualFirst'] = state['visualFirstDefault'];
          }
        }
      }
      return state;
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveTheme(brightness: Brightness.dark),
        builder: (context, child) => MaeveThemeScope(
          brightness: Brightness.dark,
          accent: deriveAccent(MaeveAccentPreset.reverie),
          child: child!,
        ),
        home: const AppAccessPage(),
      ),
    );
    await tester.pumpAndSettle();
    return calls;
  }

  testWidgets('global default and per-app override can be reset', (
    tester,
  ) async {
    final state = initialState();
    final calls = await mount(tester, state);
    expect(find.text('全局默认视觉优先'), findsOneWidget);
    expect(find.text('跟随全局默认'), findsNWidgets(2));
    expect(
      tester
          .widget<SettingsSwitchTile>(
            find.byKey(const ValueKey('visual-first-com.daiyimo.maeve')),
          )
          .onChanged,
      isNull,
    );

    await tester.tap(find.byKey(const ValueKey('visual-first-default')));
    await tester.pumpAndSettle();
    expect(calls.last.method, 'appAccess');
    expect(calls.last.arguments['action'], 'setVisualFirstDefault');
    expect(calls.last.arguments['enabled'], false);

    await tester.tap(
      find.byKey(const ValueKey('visual-first-com.tencent.jkchess')),
    );
    await tester.pumpAndSettle();
    expect(calls.last.arguments['action'], 'setVisualFirstOverride');
    expect(calls.last.arguments['packageName'], 'com.tencent.jkchess');
    expect(calls.last.arguments['enabled'], true);
    expect(find.text('已单独开启'), findsOneWidget);

    await tester.tap(find.text('恢复跟随全局默认'));
    await tester.pumpAndSettle();
    expect(calls.last.arguments['action'], 'clearVisualFirstOverride');
    expect(find.text('跟随全局默认'), findsNWidgets(2));
    expect(
      tester
          .widget<SettingsSwitchTile>(
            find.byKey(const ValueKey('visual-first-com.tencent.jkchess')),
          )
          .value,
      false,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('changing global default preserves an app override', (
    tester,
  ) async {
    final state = initialState();
    final apps = state['apps'] as List;
    apps.first['visualFirstOverride'] = false;
    apps.first['visualFirst'] = false;
    final calls = await mount(tester, state);
    expect(find.text('已单独关闭'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('visual-first-default')));
    await tester.pumpAndSettle();
    expect(calls.last.arguments['action'], 'setVisualFirstDefault');
    expect(apps.first['visualFirstOverride'], false);
    expect(apps.first['visualFirst'], false);
    expect(find.text('已单独关闭'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('failed reload shows a snackbar and keeps the prior state', (
    tester,
  ) async {
    final state = initialState();
    await mount(
      tester,
      state,
      onCall: (call) async {
        final args = call.arguments as Map? ?? const {};
        if (args['action'] == 'reload') {
          throw PlatformException(code: 'save_failed');
        }
        return state;
      },
    );

    await tester.tap(find.text('重新读取'));
    await tester.pumpAndSettle();

    const message = '应用访问设置未能读取或保存，请重试。现有策略仍然有效。';
    expect(find.byType(SnackBar), findsOneWidget);
    expect(find.text(message), findsWidgets);
    expect(find.text('全局默认视觉优先'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a stale response cannot overwrite the latest request', (
    tester,
  ) async {
    final state = initialState();
    final first = Completer<Map<String, Object?>>();
    final second = Completer<Map<String, Object?>>();
    var reloads = 0;
    await mount(
      tester,
      state,
      onCall: (call) {
        final args = call.arguments as Map? ?? const {};
        if (args['action'] != 'reload') return Future.value(state);
        reloads++;
        return reloads == 1 ? first.future : second.future;
      },
    );

    final button = tester.widget<MaeveGlassTextButton>(
      find.widgetWithText(MaeveGlassTextButton, '重新读取'),
    );
    final firstReload = button.onPressed!;
    final secondReload = button.onPressed!;
    firstReload();
    await tester.pump();
    secondReload();
    await tester.pump();

    second.complete(Map<String, Object?>.from(state));
    await tester.pumpAndSettle();
    expect(find.byType(LinearProgressIndicator), findsNothing);

    first.completeError(PlatformException(code: 'stale_failure'));
    await tester.pumpAndSettle();
    expect(find.byType(SnackBar), findsNothing);
    expect(find.text('应用访问设置未能读取或保存，请重试。现有策略仍然有效。'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}

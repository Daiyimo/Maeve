// Optional local visual review, excluded from the regular test/ suite.
// flutter test tool/preview_test.dart --dart-define=MAEVE_PREVIEW_FONT=/path/font.ttf
import 'dart:io';
import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:maeve_flutter/app/maeve_app.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/bridge/maeve_bridge.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';

import '../test/support/fake_chat_bridge.dart';
import '../test/support/fake_settings_bridge.dart';
import '../test/support/visual_scenarios.dart';

void main() {
  const fontPath = String.fromEnvironment('MAEVE_PREVIEW_FONT');
  const output = String.fromEnvironment(
    'MAEVE_PREVIEW_OUTPUT',
    defaultValue: 'build/previews',
  );
  setUpAll(() async {
    if (fontPath.isEmpty) {
      throw ArgumentError('Set MAEVE_PREVIEW_FONT to a local CJK font.');
    }
    final bytes = Uint8List.fromList(await File(fontPath).readAsBytes());
    for (final family in [
      'Roboto',
      'Ahem',
      'monospace',
      'sans-serif',
      'Noto Sans CJK SC',
    ]) {
      await (FontLoader(
        family,
      )..addFont(Future.value(ByteData.sublistView(bytes)))).load();
    }
    final sdk = Platform.environment['FLUTTER_ROOT'];
    if (sdk == null) {
      throw ArgumentError('Set FLUTTER_ROOT to load Material icons.');
    }
    final icons = await File(
      '$sdk/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf',
    ).readAsBytes();
    await (FontLoader(
      'MaterialIcons',
    )..addFont(Future.value(ByteData.sublistView(icons)))).load();
  });
  for (final mode in [ThemeMode.dark, ThemeMode.light]) {
    testWidgets('Capture $mode', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      tester.view.padding = const FakeViewPadding(top: 28, bottom: 24);
      addTearDown(tester.view.reset);
      final bridge = FakeChatBridge();
      final controller = MaeveController(
        bridge: bridge,
        settingsBridge: FakeSettingsBridge(mode: mode.name),
      )..mode = mode;
      const key = ValueKey('preview');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      final entries = MethodChannel(MaeveBridge.entryEvents.name);
      messenger.setMockMethodCallHandler(entries, (_) async => null);
      messenger.setMockMethodCallHandler(MaeveBridge.methods, (call) async {
        if (call.method == 'status') return {'version': 'visual preview'};
        if (call.method == 'legal_notices') {
          return {
            'documents': [
              {
                'name': 'NOTICE.txt',
                'text': 'Visual preview of bundled notices',
              },
            ],
            'sourceOffer': '完整许可与源码提供说明随应用打包，可在真机查阅。',
            'entries': [
              {
                'name': 'Maeve',
                'license': 'Apache-2.0',
                'note': 'Visual fixture',
              },
            ],
          };
        }
        return null;
      });
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox.shrink());
        await bridge.dispose();
        messenger.setMockMethodCallHandler(entries, null);
        messenger.setMockMethodCallHandler(MaeveBridge.methods, null);
      });
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaeveApp(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      Future<void> capture(String name) async {
        expect(tester.takeException(), isNull);
        final boundary = tester.renderObject<RenderRepaintBoundary>(
          find.byKey(key),
        );
        await tester.runAsync(() async {
          final bitmap = await boundary.toImage(pixelRatio: 2);
          final png = await bitmap.toByteData(format: ui.ImageByteFormat.png);
          final file = File('$output/${mode.name}-$name.png');
          await file.parent.create(recursive: true);
          await file.writeAsBytes(png!.buffer.asUint8List());
          bitmap.dispose();
        });
      }

      await capture('chat');
      for (final preset in MaeveAccentPreset.values) {
        controller.setAccent(preset);
        await tester.pumpAndSettle();
        await capture('palette-${preset.id}');
      }
      controller.setAccent(MaeveAccentPreset.reverie);
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('快捷面板'));
      await tester.pumpAndSettle();
      await capture('workbench');
      await tester.tap(find.byTooltip('关闭快捷面板'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('打开对话列表'));
      await tester.pumpAndSettle();
      await capture('drawer');
      await tester.tap(find.text('设置'));
      await tester.pumpAndSettle();
      await capture('settings');
      final navigator = tester.state<NavigatorState>(
        find.byType(Navigator).first,
      );
      navigator.pop();
      await tester.pumpAndSettle();
      for (final page in visualPages.entries) {
        unawaited(navigator.push(maevePageRoute((_) => page.value)));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 600));
        await capture(page.key);
        final scrollables = find.descendant(
          of: find.byType(page.value.runtimeType),
          matching: find.byType(Scrollable),
        );
        if (scrollables.evaluate().isNotEmpty) {
          final position = tester
              .state<ScrollableState>(scrollables.first)
              .position;
          if (position.maxScrollExtent > 0) {
            position.jumpTo(position.maxScrollExtent);
            await tester.pump(const Duration(milliseconds: 300));
            await capture('${page.key}-bottom');
          }
        }
        navigator.pop();
        await tester.pumpAndSettle();
      }
    });
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/app/chat_page.dart';
import 'package:maeve_flutter/app/maeve_controller.dart';
import 'package:maeve_flutter/app/maeve_shell.dart';
import 'package:maeve_flutter/app/sessions_drawer.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

import 'support/fake_chat_bridge.dart';

void main() {
  for (final brightness in Brightness.values) {
    testWidgets(
      'Drawer uses half-opacity glass and legible $brightness controls',
      (tester) async {
        final bridge = FakeChatBridge();
        final controller = MaeveController(bridge: bridge);
        addTearDown(() async {
          await tester.pumpWidget(const SizedBox.shrink());
          controller.dispose();
          await bridge.dispose();
        });
        final accent = deriveAccent(MaeveAccentPreset.reverie);
        await tester.pumpWidget(
          MaeveScope(
            controller: controller,
            child: MaterialApp(
              theme: maeveTheme(brightness: brightness, accent: accent),
              home: MaeveThemeScope(
                brightness: brightness,
                accent: accent,
                child: Scaffold(
                  body: Align(
                    alignment: Alignment.centerLeft,
                    child: SizedBox(
                      width: 288,
                      child: SessionsDrawer(onClose: () {}),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();

        final sidebar = find.byKey(const ValueKey('conversation-sidebar'));
        final theme = MaeveTheme.of(tester.element(sidebar));
        final colors = theme.colors;
        final surface = tester.widget<MaeveGlassSurface>(sidebar);
        expect(surface.tint, colors.bg);
        expect(surface.tintAlpha, brightness == Brightness.dark ? .39 : .42);
        final effectiveOpacity = Color.alphaBlend(
          colors.bg.withValues(alpha: surface.tintAlpha!),
          theme.surfaces.sheet.fill,
        ).a;
        expect(effectiveOpacity, closeTo(.5, .002));

        final newConversation = tester.widget<MaeveGlassTextButton>(
          find.byKey(const ValueKey('new-conversation')),
        );
        expect(newConversation.fill, colors.accentContainer);
        expect(newConversation.foreground, colors.onAccentContainer);

        final search = tester.widget<TextField>(
          find.byKey(const ValueKey('conversation-search')),
        );
        expect(
          search.decoration?.fillColor,
          colors.isDark ? colors.surfaceHigh : colors.surfaceLowest,
        );

        final allFilter = tester.widget<MaeveGlassTextButton>(
          find.widgetWithText(MaeveGlassTextButton, '全部'),
        );
        expect(allFilter.prominent, isFalse);
        expect(allFilter.fill, colors.accentContainer);
      },
    );
  }

  testWidgets('Opening the overlay drawer fades the page under the glass', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final bridge = FakeChatBridge();
    final controller = MaeveController(bridge: bridge);
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await bridge.dispose();
    });
    final accent = deriveAccent(MaeveAccentPreset.reverie);
    await tester.pumpWidget(
      MaeveScope(
        controller: controller,
        child: MaterialApp(
          theme: maeveTheme(brightness: Brightness.light, accent: accent),
          home: MaeveThemeScope(
            brightness: Brightness.light,
            accent: accent,
            child: const MaeveShell(),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    Opacity pageOpacity() => tester.widget<Opacity>(
      find
          .ancestor(of: find.byType(ChatPage), matching: find.byType(Opacity))
          .first,
    );
    expect(pageOpacity().opacity, 1);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pumpAndSettle();
    expect(pageOpacity().opacity, 0);
    await tester.tapAt(const Offset(380, 450));
    await tester.pumpAndSettle();
    expect(pageOpacity().opacity, 1);
  });
}

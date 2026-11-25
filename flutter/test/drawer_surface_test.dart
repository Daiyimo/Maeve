import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
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

  testWidgets('Overlay drawer keeps the same glass as the page fades', (
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
    const frame = ValueKey('drawer-animation-frame');
    await tester.pumpWidget(
      MaeveScope(
        controller: controller,
        child: MaterialApp(
          theme: maeveTheme(brightness: Brightness.light, accent: accent),
          home: MaeveThemeScope(
            brightness: Brightness.light,
            accent: accent,
            child: const RepaintBoundary(key: frame, child: MaeveShell()),
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
    Color drawerFill() {
      final drawer = find.byKey(const ValueKey('conversation-sidebar'));
      final surface = tester.widget<MaeveGlassSurface>(drawer);
      final fill = tester.widget<DecoratedBox>(
        find
            .descendant(
              of: drawer,
              matching: find.byWidgetPredicate(
                (widget) =>
                    widget is DecoratedBox &&
                    widget.decoration is ShapeDecoration &&
                    (widget.decoration as ShapeDecoration).shape ==
                        surface.shape,
              ),
            )
            .first,
      );
      return (fill.decoration as ShapeDecoration).color!;
    }

    final drawer = find.byKey(const ValueKey('conversation-sidebar'));
    final glassFilter = find.descendant(
      of: drawer,
      matching: find.byType(BackdropFilter),
    );
    Future<List<int>> drawerPixel() async {
      final boundary = tester.renderObject<RenderRepaintBoundary>(
        find.byKey(frame),
      );
      return (await tester.runAsync(() async {
        final image = await boundary.toImage();
        try {
          final pixels = (await image.toByteData(
            format: ui.ImageByteFormat.rawRgba,
          ))!;
          final offset = (655 * image.width + 70) * 4;
          return [for (var i = 0; i < 3; i++) pixels.getUint8(offset + i)];
        } finally {
          image.dispose();
        }
      }))!;
    }

    void expectSameTone(List<int> actual, List<int> reference) {
      for (var i = 0; i < actual.length; i++) {
        expect(actual[i], closeTo(reference[i], 3));
      }
    }

    expect(pageOpacity().opacity, 1);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));
    expect(pageOpacity().opacity, allOf(greaterThan(0), lessThan(1)));
    final openingFill = drawerFill();
    final openingPixel = await drawerPixel();
    expect(glassFilter, findsWidgets);
    await tester.pumpAndSettle();
    expect(pageOpacity().opacity, 0);
    expect(openingFill, drawerFill());
    expect(glassFilter, findsWidgets);
    final settledPixel = await drawerPixel();
    expectSameTone(openingPixel, settledPixel);
    await tester.tapAt(const Offset(380, 450));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));
    expect(pageOpacity().opacity, allOf(greaterThan(0), lessThan(1)));
    expect(drawerFill(), openingFill);
    expect(glassFilter, findsWidgets);
    expectSameTone(await drawerPixel(), settledPixel);
    await tester.pumpAndSettle();
    expect(pageOpacity().opacity, 1);
  });
}

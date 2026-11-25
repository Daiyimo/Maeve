import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/app/chat_page.dart';
import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/main.dart';
import 'package:maeve_flutter/ui/common/app_page.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

import 'support/fake_chat_bridge.dart';

Future<void> pumpApp(
  WidgetTester tester, {
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final entryChannel = MethodChannel(MaeveBridge.entryEvents.name);
  messenger.setMockMethodCallHandler(entryChannel, (_) async => null);
  final bridge = FakeChatBridge();
  addTearDown(() async {
    await tester.pumpWidget(const SizedBox.shrink());
    await bridge.dispose();
    messenger.setMockMethodCallHandler(entryChannel, null);
  });
  await tester.pumpWidget(
    MaeveApp(controller: MaeveController(bridge: bridge)),
  );
  await tester.pumpAndSettle();
}

bool _isOpaqueCanvas(Widget widget, Color canvas) {
  final Color? color;
  if (widget is Container) {
    color = widget.color;
  } else if (widget is ColoredBox) {
    color = widget.color;
  } else if (widget is DecoratedBox && widget.decoration is BoxDecoration) {
    color = (widget.decoration as BoxDecoration).color;
  } else {
    return false;
  }
  return color != null &&
      color.a == 1 &&
      color.r == canvas.r &&
      color.g == canvas.g &&
      color.b == canvas.b;
}

bool _hasOpaqueFade(Widget widget, Color canvas) {
  final decoration = widget is Container
      ? widget.decoration
      : widget is DecoratedBox
      ? widget.decoration
      : null;
  if (decoration is! BoxDecoration) return false;
  final gradient = decoration.gradient;
  return gradient is LinearGradient &&
      gradient.colors.any(
        (color) =>
            color.a >= .92 &&
            color.r == canvas.r &&
            color.g == canvas.g &&
            color.b == canvas.b,
      );
}

void main() {
  testWidgets('TopBar has no opaque canvas parent before its filter', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveTheme(brightness: Brightness.dark),
        home: MaeveThemeScope(
          brightness: Brightness.dark,
          accent: deriveAccent(MaeveAccentPreset.reverie),
          child: const Scaffold(body: TopBar(title: '玻璃顶栏', showBack: false)),
        ),
      ),
    );
    final context = tester.element(find.byType(TopBar));
    final canvas = MaeveTheme.of(context).colors.bg;
    expect(
      find.descendant(
        of: find.byType(TopBar),
        matching: find.byWidgetPredicate(
          (widget) => _isOpaqueCanvas(widget, canvas),
        ),
      ),
      findsNothing,
    );
    final surface = tester.widget<MaeveGlassSurface>(
      find.descendant(
        of: find.byType(TopBar),
        matching: find.byType(MaeveGlassSurface),
      ),
    );
    expect(surface.tier, MaeveGlassTier.chrome);
    expect(surface.framed, isFalse);
    expect(find.byType(BackdropFilter), findsOneWidget);
  });

  testWidgets('Composer has no .92/1 fade and shares the shell group', (
    tester,
  ) async {
    await pumpApp(tester);
    final context = tester.element(find.byType(ChatPage));
    final canvas = MaeveTheme.of(context).colors.bg;
    expect(
      find.descendant(
        of: find.byType(ChatPage),
        matching: find.byWidgetPredicate(
          (widget) => _hasOpaqueFade(widget, canvas),
        ),
      ),
      findsNothing,
    );
    final input = find.byKey(const ValueKey('message-input'));
    final composer = tester.widget<MaeveGlassSurface>(
      find.ancestor(of: input, matching: find.byType(MaeveGlassSurface)).first,
    );
    expect(composer.tier, MaeveGlassTier.chrome);
    expect(composer.grouped, isTrue);
    expect(find.byType(BackdropGroup), findsOneWidget);

    final filters = tester.widgetList<BackdropFilter>(
      find.descendant(
        of: find.byType(ChatPage),
        matching: find.byType(BackdropFilter),
      ),
    );
    expect(filters.length, 2);
    final topBarSurface = tester.widget<MaeveGlassSurface>(
      find.descendant(
        of: find.byType(TopBar),
        matching: find.byType(MaeveGlassSurface),
      ),
    );
    expect(topBarSurface.grouped, isTrue);
    expect(composer.grouped, isTrue);
  });

  testWidgets('Drawer uses one independent filter without an opaque base', (
    tester,
  ) async {
    await pumpApp(tester, size: const Size(1200, 900));
    final sidebar = find.byKey(const ValueKey('conversation-sidebar'));
    expect(sidebar, findsOneWidget);
    final surface = tester.widget<MaeveGlassSurface>(sidebar);
    expect(surface.tier, MaeveGlassTier.sheet);
    expect(surface.grouped, isFalse);
    expect(
      find.descendant(of: sidebar, matching: find.byType(BackdropFilter)),
      findsOneWidget,
    );
    final context = tester.element(sidebar);
    final canvas = MaeveTheme.of(context).colors.bg;
    expect(
      find.descendant(
        of: sidebar,
        matching: find.byWidgetPredicate(
          (widget) => _isOpaqueCanvas(widget, canvas),
        ),
      ),
      findsNothing,
    );
  });

  testWidgets('Overlay drawer keeps sampling as its animation settles', (
    tester,
  ) async {
    await pumpApp(tester);
    await tester.tap(find.byTooltip('打开对话列表'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    final sidebar = find.byKey(const ValueKey('conversation-sidebar'));
    expect(sidebar, findsOneWidget);
    final filter = find.descendant(
      of: sidebar,
      matching: find.byType(BackdropFilter),
    );
    expect(filter, findsOneWidget);
    final movingFilter = tester.element(filter);

    await tester.pumpAndSettle();
    expect(tester.element(filter), same(movingFilter));
  });
}

import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/main.dart';

void main() {
  testWidgets('chat shell renders composer and greeting', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    expect(find.text('A HOST WITH A VOICE'), findsOneWidget);
    expect(find.text('One thought, from here.'), findsOneWidget);
    expect(find.byKey(const ValueKey('message-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('send-message')), findsOneWidget);
  });

  test('armed mark puts the tick inside the ring opening', () {
    // The opening runs from the end of the arc back around to its start.
    final openingStart = MaeveMarkPainter.arcStart + MaeveMarkPainter.arcSweep;
    final openingEnd = MaeveMarkPainter.arcStart + 2 * math.pi;
    final openingSize = openingEnd - openingStart;

    // The tick direction must fall inside that opening, not on the arc.
    expect(MaeveMarkPainter.gapDirection, greaterThan(openingStart));
    expect(MaeveMarkPainter.gapDirection, lessThan(openingEnd));
    // A gap narrower than ~60 degrees reads as a sloppy closed ring.
    expect(openingSize, greaterThan(math.pi / 3));

    // The tick spans from .9r down to the inner disk edge, so it stays outside
    // the filled centre instead of poking through it.
    expect(MaeveMarkPainter.fillRatio, lessThan(.9));
  });

  testWidgets('armed mark paints differently from idle', (tester) async {
    // Rasterization blocks inside flutter_tester, so compare the recorded
    // draw commands instead: same input must not yield the same ops.
    List<String> ops(MarkState state) {
      final recorder = ui.PictureRecorder();
      final canvas = Canvas(recorder);
      final spy = _RecordingCanvas(canvas);
      MaeveMarkPainter(
        const Color(0xFFF1EEF5),
        const Color(0xFF201D27),
        armed: state == MarkState.armed,
      ).paint(spy, const Size.square(58));
      return spy.ops;
    }

    final armed = ops(MarkState.armed);
    final idle = ops(MarkState.idle);

    // Idle draws a closed ring; armed draws an arc plus a tick.
    expect(idle.any((op) => op.startsWith('circle')), isTrue);
    expect(armed.any((op) => op.startsWith('arc')), isTrue);
    expect(armed.any((op) => op.startsWith('line')), isTrue);
    expect(idle.any((op) => op.startsWith('arc')), isFalse);
    expect(armed, isNot(equals(idle)));
  });

  testWidgets('drawer navigates to settings', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    // The top bar also shows a PREFERENCES eyebrow once the page is active.
    expect(find.text('PREFERENCES'), findsWidgets);
    expect(find.text('Light appearance'), findsOneWidget);
    expect(
      find.text('Flutter interface · Android capabilities'),
      findsOneWidget,
    );
    expect(
      find.textContaining('Runtime backdrop blur is disabled'),
      findsOneWidget,
    );
  });

  testWidgets('sending a message shows user and demo reply', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.enterText(
      find.byKey(const ValueKey('message-input')),
      'A test message',
    );
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();
    expect(find.text('A test message'), findsOneWidget);
    expect(find.text('Maeve is thinking'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pumpAndSettle();
    expect(find.text('I have it. The next move is yours.'), findsOneWidget);
  });

  testWidgets('creates a new conversation from the drawer', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('new-conversation')));
    await tester.pumpAndSettle();
    expect(find.text('New conversation'), findsOneWidget);
  });

  testWidgets('search filters the conversation list', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();
    expect(find.text('A new beginning'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('conversation-search')),
      'calmer',
    );
    await tester.pumpAndSettle();
    expect(find.text('Build a calmer workspace'), findsOneWidget);
    expect(find.text('A new beginning'), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('conversation-search')),
      'no such conversation',
    );
    await tester.pumpAndSettle();
    expect(find.text('No matching conversations'), findsOneWidget);
    expect(find.text('A new beginning'), findsNothing);
  });

  testWidgets('stop action cancels the pending reply', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.enterText(
      find.byKey(const ValueKey('message-input')),
      'Please stop',
    );
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();
    expect(find.text('Maeve is thinking'), findsOneWidget);

    await tester.tap(find.byTooltip('Stop generation'));
    await tester.pumpAndSettle();
    expect(find.text('Maeve is thinking'), findsNothing);

    await tester.pump(const Duration(milliseconds: 600));
    await tester.pumpAndSettle();
    expect(find.text('I have it. The next move is yours.'), findsNothing);
  });

  testWidgets('adds and deletes a memory', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Memory'));
    await tester.pumpAndSettle();
    expect(find.text('Nothing saved yet'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('add-memory')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('memory-input')),
      'Prefers short answers',
    );
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(find.text('Prefers short answers'), findsOneWidget);
    expect(find.text('Nothing saved yet'), findsNothing);

    await tester.tap(find.byTooltip('Delete memory'));
    await tester.pumpAndSettle();
    expect(find.text('Prefers short answers'), findsNothing);
    expect(find.text('Nothing saved yet'), findsOneWidget);
  });

  testWidgets('theme toggle switches the active brightness', (tester) async {
    // The toggle lives in the wide-layout rail, so widen the test surface.
    tester.view.physicalSize = const Size(1200, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaeveApp());
    Brightness shellBrightness() =>
        Theme.of(tester.element(find.byType(MaeveShell))).brightness;

    expect(shellBrightness(), Brightness.dark);

    await tester.tap(find.byTooltip('Toggle theme'));
    await tester.pumpAndSettle();
    expect(shellBrightness(), Brightness.light);
  });

  testWidgets('narrow drawer opens, switches page and closes', (tester) async {
    // Phone geometry: the drawer is the only navigation here.
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('new-conversation')), findsOneWidget);

    await tester.tap(find.text('Runs'));
    await tester.pumpAndSettle();
    expect(find.text('RUN HISTORY'), findsWidgets);
    // Navigating closes the drawer.
    expect(find.byKey(const ValueKey('new-conversation')), findsNothing);
  });

  testWidgets('drawer scrim dismisses without changing page', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('drawer-scrim')),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('new-conversation')), findsNothing);
    expect(find.text('A HOST WITH A VOICE'), findsOneWidget);
  });

  testWidgets('wide rail navigates between pages', (tester) async {
    tester.view.physicalSize = const Size(1200, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('Memory'));
    await tester.pumpAndSettle();
    expect(find.text('PERSONAL CONTEXT'), findsWidgets);

    await tester.tap(find.byTooltip('Runs'));
    await tester.pumpAndSettle();
    expect(find.text('RUN HISTORY'), findsWidgets);

    await tester.tap(find.byTooltip('Settings'));
    await tester.pumpAndSettle();
    expect(find.text('Light appearance'), findsOneWidget);
  });

  testWidgets('switching conversation loads its own messages', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.enterText(
      find.byKey(const ValueKey('message-input')),
      'Only in this thread',
    );
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pumpAndSettle();
    expect(find.text('Only in this thread'), findsOneWidget);

    await tester.tap(find.byTooltip('Open conversations'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Build a calmer workspace'));
    await tester.pumpAndSettle();

    // The greeting returns because the other conversation is still empty and
    // the previous thread's messages must not leak in.
    expect(find.text('A HOST WITH A VOICE'), findsOneWidget);
    expect(find.text('Only in this thread'), findsNothing);
  });
}

/// Records the draw calls a painter issues, without rasterizing anything.
class _RecordingCanvas implements Canvas {
  _RecordingCanvas(this._inner);

  final Canvas _inner;
  final List<String> ops = [];

  @override
  void drawCircle(Offset c, double radius, Paint paint) {
    ops.add('circle(${c.dx},${c.dy},$radius)');
    _inner.drawCircle(c, radius, paint);
  }

  @override
  void drawArc(
    Rect rect,
    double startAngle,
    double sweepAngle,
    bool useCenter,
    Paint paint,
  ) {
    ops.add('arc($startAngle,$sweepAngle)');
    _inner.drawArc(rect, startAngle, sweepAngle, useCenter, paint);
  }

  @override
  void drawLine(Offset p1, Offset p2, Paint paint) {
    ops.add('line(${p1.dx},${p1.dy})->(${p2.dx},${p2.dy})');
    _inner.drawLine(p1, p2, paint);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnsupportedError('Unexpected canvas call ${invocation.memberName}');
}

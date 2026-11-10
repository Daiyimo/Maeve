import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/main.dart';
import 'package:maeve_flutter/widgets/maeve_mark.dart';

void main() {
  testWidgets('chat shell renders composer and greeting', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    expect(find.text('有声音的宿主'), findsOneWidget);
    expect(find.text('一个念头，从这里开始。'), findsOneWidget);
    expect(find.byKey(const ValueKey('message-input')), findsOneWidget);
    expect(find.byKey(const ValueKey('send-message')), findsOneWidget);
  });

  test('bridge sends open_settings on the maeve/ui channel', () async {
    final calls = <MethodCall>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(const MethodChannel('maeve/ui'), (
      call,
    ) async {
      calls.add(call);
      return null;
    });

    await MaeveBridge.openSettings();

    expect(calls.map((call) => call.method), ['open_settings']);
    messenger.setMockMethodCallHandler(const MethodChannel('maeve/ui'), null);
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
    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();
    // The top bar also shows a PREFERENCES eyebrow once the page is active.
    expect(find.text('偏好'), findsWidgets);
    expect(find.text('浅色外观'), findsOneWidget);
    expect(find.text('Flutter 界面 · Android 能力'), findsOneWidget);
    expect(find.textContaining('有界背景模糊'), findsOneWidget);
    expect(find.text('减少透明效果'), findsOneWidget);
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
    expect(find.text('Maeve 正在思考'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pumpAndSettle();
    expect(find.text('我收到了，接下来交给你。'), findsOneWidget);
  });

  testWidgets('creates a new conversation from the drawer', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('new-conversation')));
    await tester.pumpAndSettle();
    expect(find.text('新对话'), findsOneWidget);
  });

  testWidgets('search filters the conversation list', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();
    expect(find.text('新的开始'), findsOneWidget);

    await tester.enterText(
      find.byKey(const ValueKey('conversation-search')),
      '安静',
    );
    await tester.pumpAndSettle();
    expect(find.text('搭一个安静的工作台'), findsOneWidget);
    expect(find.text('新的开始'), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('conversation-search')),
      'no such conversation',
    );
    await tester.pumpAndSettle();
    expect(find.text('没有匹配的对话'), findsOneWidget);
    expect(find.text('新的开始'), findsNothing);
  });

  testWidgets('stop action cancels the pending reply', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.enterText(
      find.byKey(const ValueKey('message-input')),
      'Please stop',
    );
    await tester.tap(find.byKey(const ValueKey('send-message')));
    await tester.pump();
    expect(find.text('Maeve 正在思考'), findsOneWidget);

    await tester.tap(find.byTooltip('停止生成'));
    await tester.pumpAndSettle();
    expect(find.text('Maeve 正在思考'), findsNothing);

    await tester.pump(const Duration(milliseconds: 600));
    await tester.pumpAndSettle();
    expect(find.text('我收到了，接下来交给你。'), findsNothing);
  });

  testWidgets('adds and deletes a memory', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('记忆'));
    await tester.pumpAndSettle();
    expect(find.text('还没有保存任何内容'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('add-memory')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('memory-input')),
      'Prefers short answers',
    );
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();
    expect(find.text('Prefers short answers'), findsOneWidget);
    expect(find.text('还没有保存任何内容'), findsNothing);

    await tester.tap(find.byTooltip('删除记忆'));
    await tester.pumpAndSettle();
    expect(find.text('Prefers short answers'), findsNothing);
    expect(find.text('还没有保存任何内容'), findsOneWidget);
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

    await tester.tap(find.byTooltip('切换主题'));
    await tester.pumpAndSettle();
    expect(shellBrightness(), Brightness.light);
  });

  testWidgets('reduce-transparency switch drops the composer blur', (
    tester,
  ) async {
    await tester.pumpWidget(const MaeveApp());
    // The composer is a chrome surface over the scrolling chat list, so it is
    // the one backdrop filter on the chat page.
    expect(find.byType(BackdropFilter), findsOneWidget);

    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('设置'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('减少透明效果'));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();
    // The chat page is reached through a conversation row, not a destination.
    await tester.tap(find.text('选择之道'));
    await tester.pumpAndSettle();
    expect(find.byType(BackdropFilter), findsNothing);
  });

  testWidgets('narrow drawer opens, switches page and closes', (tester) async {
    // Phone geometry: the drawer is the only navigation here.
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('new-conversation')), findsOneWidget);

    await tester.tap(find.text('运行'));
    await tester.pumpAndSettle();
    expect(find.text('运行记录'), findsWidgets);
    // Navigating closes the drawer.
    expect(find.byKey(const ValueKey('new-conversation')), findsNothing);
  });

  testWidgets('drawer scrim dismisses without changing page', (tester) async {
    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();

    await tester.tap(
      find.byKey(const ValueKey('drawer-scrim')),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('new-conversation')), findsNothing);
    expect(find.text('有声音的宿主'), findsOneWidget);
  });

  testWidgets('wide rail navigates between pages', (tester) async {
    tester.view.physicalSize = const Size(1200, 900);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(const MaeveApp());
    await tester.tap(find.byTooltip('记忆'));
    await tester.pumpAndSettle();
    expect(find.text('个人上下文'), findsWidgets);

    await tester.tap(find.byTooltip('运行'));
    await tester.pumpAndSettle();
    expect(find.text('运行记录'), findsWidgets);

    await tester.tap(find.byTooltip('设置'));
    await tester.pumpAndSettle();
    expect(find.text('浅色外观'), findsOneWidget);
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

    await tester.tap(find.byTooltip('打开对话'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('搭一个安静的工作台'));
    await tester.pumpAndSettle();

    // The greeting returns because the other conversation is still empty and
    // the previous thread's messages must not leak in.
    expect(find.text('有声音的宿主'), findsOneWidget);
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

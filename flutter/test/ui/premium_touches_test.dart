import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/ui/common/status.dart';
import 'package:maeve_flutter/ui/common/pressable.dart';
import 'package:maeve_flutter/widgets/maeve_mark.dart';

void main() {
  Widget host(Widget child, {bool reducedMotion = false}) => MaterialApp(
    theme: maeveTheme(brightness: Brightness.dark),
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: reducedMotion),
      child: MaeveThemeScope(
        brightness: Brightness.dark,
        accent: deriveAccent(MaeveAccentPreset.reverie),
        child: child ?? const SizedBox.shrink(),
      ),
    ),
    home: Scaffold(body: Center(child: child)),
  );

  const visibleKey = ValueKey('typed-headline-visible');

  testWidgets(
    'Primary button keeps platform typography without inherited decoration',
    (tester) async {
      await tester.pumpWidget(
        host(
          DefaultTextStyle(
            style: const TextStyle(decoration: TextDecoration.underline),
            child: FilledButton(onPressed: () {}, child: const Text('继续')),
          ),
        ),
      );
      final label = tester.widget<RichText>(
        find.descendant(of: find.text('继续'), matching: find.byType(RichText)),
      );
      expect(label.text.style!.fontFamily, isNotEmpty);
      expect(label.text.style!.decoration, TextDecoration.none);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'Eclipse entrance settles once and reduced motion starts settled',
    (tester) async {
      await tester.pumpWidget(host(const MaeveEmblem()));
      final opacity = find.descendant(
        of: find.byType(MaeveEmblem),
        matching: find.byType(Opacity),
      );
      expect(tester.widget<Opacity>(opacity).opacity, 0);
      await tester.pumpAndSettle();
      expect(tester.widget<Opacity>(opacity).opacity, 1);
      expect(tester.binding.hasScheduledFrame, isFalse);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(host(const MaeveEmblem(), reducedMotion: true));
      expect(tester.widget<Opacity>(opacity).opacity, 1);
      await tester.pumpAndSettle();
      expect(tester.binding.hasScheduledFrame, isFalse);
    },
  );

  testWidgets('Glass feedback places light at the touch and releases it', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(
        Pressable(onTap: () {}, child: const SizedBox(width: 200, height: 80)),
      ),
    );
    final target = find.byType(Pressable);
    final rect = tester.getRect(target);
    final gesture = await tester.startGesture(
      rect.topLeft + const Offset(20, 16),
    );
    await tester.pump(const Duration(milliseconds: 250));
    await tester.pump(const Duration(milliseconds: 160));
    final surface = find.descendant(
      of: target,
      matching: find.byType(DecoratedBox),
    );
    final decoration =
        tester.widget<DecoratedBox>(surface).decoration as BoxDecoration;
    final gradient = decoration.gradient! as RadialGradient;
    expect((gradient.center as Alignment).x, closeTo(-.8, .05));
    expect((gradient.center as Alignment).y, closeTo(-.6, .05));
    expect(
      tester
          .widget<AnimatedOpacity>(
            find.descendant(of: target, matching: find.byType(AnimatedOpacity)),
          )
          .opacity,
      1,
    );
    await gesture.up();
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<AnimatedOpacity>(
            find.descendant(of: target, matching: find.byType(AnimatedOpacity)),
          )
          .opacity,
      0,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Headline advances each frame, keeps its size and finishes', (
    tester,
  ) async {
    const text = '一个念头，\n从这里开始。';
    await tester.pumpWidget(host(const TypedHeadline(text: text)));
    final headline = find.byType(TypedHeadline);
    final size = tester.getSize(headline);
    final first = tester
        .widget<RichText>(find.byKey(visibleKey))
        .text
        .toPlainText();
    await tester.pump(const Duration(milliseconds: 360));
    final next = tester
        .widget<RichText>(find.byKey(visibleKey))
        .text
        .toPlainText();
    expect(next.length, greaterThan(first.length));
    expect(next, isNot(contains('开始')));
    expect(tester.getSize(headline), size);
    await tester.pumpAndSettle();
    expect(tester.widget<Text>(find.byKey(visibleKey)).data, text);
    expect(tester.getSize(headline), size);
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  testWidgets('Headline respects reduced motion on its first frame', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(const TypedHeadline(text: '完整标题'), reducedMotion: true),
    );
    expect(tester.widget<Text>(find.byKey(visibleKey)).data, '完整标题');
    expect(
      find.descendant(
        of: find.byType(TypedHeadline),
        matching: find.byType(Stack),
      ),
      findsNothing,
    );
    await tester.pumpAndSettle();
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  testWidgets('Enabling reduced motion finishes an active headline', (
    tester,
  ) async {
    await tester.pumpWidget(host(const TypedHeadline(text: '完整标题')));
    await tester.pump(const Duration(milliseconds: 90));
    await tester.pumpWidget(
      host(const TypedHeadline(text: '完整标题'), reducedMotion: true),
    );
    expect(tester.widget<Text>(find.byKey(visibleKey)).data, '完整标题');
    await tester.pumpAndSettle();
    expect(tester.binding.hasScheduledFrame, isFalse);
  });

  testWidgets('A changed headline restarts and types whole graphemes', (
    tester,
  ) async {
    await tester.pumpWidget(host(const TypedHeadline(text: '旧标题')));
    await tester.pumpAndSettle();
    const text = '👩🏽‍💻 新标题';
    await tester.pumpWidget(host(const TypedHeadline(text: text)));
    await tester.pump(const Duration(milliseconds: 100));
    final partial = tester
        .widget<RichText>(find.byKey(visibleKey))
        .text
        .toPlainText();
    expect(partial, startsWith('👩🏽‍💻'));
    expect(partial, isNot(contains('旧标题')));
    await tester.pumpAndSettle();
    expect(tester.widget<Text>(find.byKey(visibleKey)).data, text);
  });

  testWidgets('Headline exposes one complete label while typing', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    try {
      await tester.pumpWidget(host(const TypedHeadline(text: '完整标题')));
      expect(find.bySemanticsLabel('完整标题'), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 180));
      expect(find.bySemanticsLabel('完整标题'), findsOneWidget);
    } finally {
      semantics.dispose();
    }
  });

  testWidgets(
    'Working line animates only while active and releases its ticker',
    (tester) async {
      await tester.pumpWidget(
        host(const WorkingLine(text: '本机 · 就绪', active: false)),
      );
      expect(find.byType(ShaderMask), findsNothing);
      await tester.pumpWidget(
        host(const WorkingLine(text: '正在思考', active: true)),
      );
      expect(find.byType(ShaderMask), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 500));
      expect(tester.binding.hasScheduledFrame, isTrue);
      await tester.pumpWidget(
        host(const WorkingLine(text: '已完成', active: false)),
      );
      await tester.pumpAndSettle();
      expect(find.byType(ShaderMask), findsNothing);
      expect(find.text('已完成'), findsOneWidget);
      expect(tester.binding.hasScheduledFrame, isFalse);
    },
  );

  testWidgets('Working line with reduced motion settles even when active', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(const WorkingLine(text: '正在思考', active: true), reducedMotion: true),
    );
    await tester.pumpAndSettle();
    expect(find.byType(ShaderMask), findsNothing);
    expect(find.text('正在思考'), findsOneWidget);
    expect(tester.binding.hasScheduledFrame, isFalse);
  });
}

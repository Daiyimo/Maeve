import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_colors.dart';
import 'package:maeve_flutter/design/maeve_shapes.dart';
import 'package:maeve_flutter/design/maeve_surfaces.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/ui/common/haptics.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

/// 玻璃链路的验收:哪一档模糊、降级怎么走、会动的玻璃什么时候停止采样。
Widget host(
  Widget child, {
  Brightness brightness = Brightness.dark,
  bool blurEnabled = true,
  bool highContrast = false,
}) => MaterialApp(
  theme: maeveTheme(brightness: brightness),
  home: MediaQuery(
    data: MediaQueryData(highContrast: highContrast),
    child: MaeveThemeScope(
      brightness: brightness,
      accent: deriveAccent(MaeveAccentPreset.reverie),
      blurEnabled: blurEnabled,
      child: Scaffold(body: Center(child: child)),
    ),
  ),
);

void main() {
  testWidgets('chrome / sheet 取样,card / pill 不取样', (tester) async {
    await tester.pumpWidget(
      host(
        const Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            MaeveGlassSurface(
              tier: MaeveGlassTier.chrome,
              child: Text('chrome'),
            ),
            MaeveGlassSurface(tier: MaeveGlassTier.card, child: Text('card')),
            MaeveGlassSurface(tier: MaeveGlassTier.pill, child: Text('pill')),
          ],
        ),
      ),
    );
    expect(find.text('chrome'), findsOneWidget);
    expect(find.text('card'), findsOneWidget);
    expect(find.text('pill'), findsOneWidget);
    // 只有 chrome 建了一层采样;card / pill 长在滚动内容里,取样会玻璃套玻璃。
    expect(find.byType(BackdropFilter), findsOneWidget);
  });

  testWidgets('关模糊(降级路径):一个 BackdropFilter 都不建', (tester) async {
    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(tier: MaeveGlassTier.chrome, child: Text('x')),
        blurEnabled: false,
      ),
    );
    expect(find.byType(BackdropFilter), findsNothing);
    expect(find.text('x'), findsOneWidget);
  });

  testWidgets('液态玻璃浓度从主题流到表面', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveTheme(brightness: Brightness.dark),
        home: MaeveThemeScope(
          brightness: Brightness.dark,
          accent: deriveAccent(MaeveAccentPreset.reverie),
          glassDensity: MaeveGlassDensity.dense,
          child: Builder(
            builder: (context) {
              final surfaces = MaeveTheme.of(context).surfaces;
              expect(surfaces.density, MaeveGlassDensity.dense);
              // 浓郁档:填充更厚、模糊更强,但每一档都仍然半透明。
              expect(surfaces.chrome.fill.a, greaterThan(.5));
              expect(surfaces.chrome.fill.a, lessThan(1));
              expect(surfaces.chrome.blurSigma, greaterThan(24));
              return const SizedBox();
            },
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('液态玻璃圆钮:prominent 也带受光边与触感', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      host(
        MaeveGlassButton(
          icon: Icons.arrow_upward_rounded,
          tooltip: '发送消息',
          prominent: true,
          haptic: MaeveHaptic.light,
          onPressed: () => taps++,
        ),
      ),
    );
    expect(find.byTooltip('发送消息'), findsOneWidget);
    // 实色底仍然画边(受光边 + 斜带 + 顶边提亮):CustomPaint 的前景 painter。
    final paints = tester.widgetList<CustomPaint>(find.byType(CustomPaint));
    expect(paints.where((p) => p.foregroundPainter != null), isNotEmpty);
    await tester.tap(find.byTooltip('发送消息'));
    await tester.pumpAndSettle();
    expect(taps, 1);
  });

  testWidgets('系统高对比度也退化成不透明填充', (tester) async {
    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(tier: MaeveGlassTier.sheet, child: Text('s')),
        highContrast: true,
      ),
    );
    expect(find.byType(BackdropFilter), findsNothing);
  });

  testWidgets('会动的玻璃保留 filter 但停止采样', (tester) async {
    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          settled: 0,
          child: Text('moving'),
        ),
      ),
    );
    final filter = tester.widget<BackdropFilter>(find.byType(BackdropFilter));
    expect(filter.enabled, isFalse);
  });

  testWidgets('card 可以被强制开模糊(sigma 兜底 14)', (tester) async {
    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          blur: true,
          child: Text('forced'),
        ),
      ),
    );
    expect(find.byType(BackdropFilter), findsOneWidget);
  });

  testWidgets('shared 采样:同一 BackdropGroup 下的多件只采一次', (tester) async {
    await tester.pumpWidget(
      host(
        BackdropGroup(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: const [
              MaeveGlassSurface(
                tier: MaeveGlassTier.chrome,
                grouped: true,
                child: Text('a'),
              ),
              MaeveGlassSurface(
                tier: MaeveGlassTier.chrome,
                grouped: true,
                child: Text('b'),
              ),
            ],
          ),
        ),
      ),
    );
    // 仍然是两个 filter 节点,但都在同一个 group 里(一组只采样一次)。
    expect(find.byType(BackdropGroup), findsOneWidget);
    expect(find.byType(BackdropFilter), findsNWidgets(2));
  });

  testWidgets('形状决定裁剪:会模糊的玻璃必须按形状裁', (tester) async {
    // 只有会模糊的玻璃才裁剪(模糊产物不能出形状);不模糊的卡不裁,省一次离屏。
    await tester.pumpWidget(
      host(
        const Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            MaeveGlassSurface(
              tier: MaeveGlassTier.chrome,
              shape: MaeveShapes.pill,
              child: Text('p'),
            ),
            MaeveGlassSurface(
              tier: MaeveGlassTier.chrome,
              shape: MaeveShapes.card,
              child: Text('c'),
            ),
          ],
        ),
      ),
    );
    expect(find.byType(ClipRRect), findsWidgets);
    expect(find.byType(ClipRSuperellipse), findsWidgets);
  });

  test('四档材质:降级填充接近不透明,模糊只在两档', () {
    final s = buildSurfaces(
      MaeveColors.dark(deriveAccent(MaeveAccentPreset.reverie)),
      blurEnabled: false,
    );
    for (final style in [s.chrome, s.sheet, s.card, s.pill]) {
      expect(style.blurSigma, 0);
      expect(style.fallbackFill.a, greaterThanOrEqualTo(.98));
    }
  });
}

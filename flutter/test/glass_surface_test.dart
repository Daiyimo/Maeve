import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:maeve_flutter/design/maeve_accent.dart';
import 'package:maeve_flutter/design/maeve_colors.dart';
import 'package:maeve_flutter/design/maeve_shapes.dart';
import 'package:maeve_flutter/design/maeve_surfaces.dart';
import 'package:maeve_flutter/design/maeve_theme.dart';
import 'package:maeve_flutter/design/maeve_motion.dart';
import 'package:maeve_flutter/ui/common/haptics.dart';
import 'package:maeve_flutter/ui/common/pressable.dart';
import 'package:maeve_flutter/widgets/glass_surface.dart';

/// 玻璃链路的验收:哪一档模糊、降级怎么走、会动的玻璃什么时候停止采样。
Widget host(
  Widget child, {
  Brightness brightness = Brightness.dark,
  bool blurEnabled = true,
  bool highContrast = false,
  MaeveGlassDensity density = MaeveGlassDensity.balanced,
}) => MaterialApp(
  theme: maeveTheme(brightness: brightness),
  home: MediaQuery(
    data: MediaQueryData(highContrast: highContrast),
    child: MaeveThemeScope(
      brightness: brightness,
      accent: deriveAccent(MaeveAccentPreset.reverie),
      blurEnabled: blurEnabled,
      glassDensity: density,
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
            MaeveGlassSurface(tier: MaeveGlassTier.sheet, child: Text('sheet')),
            MaeveGlassSurface(tier: MaeveGlassTier.card, child: Text('card')),
            MaeveGlassSurface(tier: MaeveGlassTier.pill, child: Text('pill')),
          ],
        ),
      ),
    );
    expect(find.text('chrome'), findsOneWidget);
    expect(find.text('sheet'), findsOneWidget);
    expect(find.text('card'), findsOneWidget);
    expect(find.text('pill'), findsOneWidget);
    // chrome / sheet 采样;card / pill 长在滚动内容里,不玻璃套玻璃。
    expect(find.byType(BackdropFilter), findsNWidgets(2));
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
              // 共享契约:浓郁档只做倍率缩放,不恢复旧的高磨砂参数。
              expect(surfaces.chrome.fill.a, closeTo(.14 * 1.45, 0.0001));
              expect(surfaces.chrome.blurSigma, closeTo(3 * 1.35, 0.0001));
              expect(surfaces.chrome.saturation, closeTo(1.28 * 1.10, 0.0001));
              expect(
                surfaces.chrome.refractionStrength,
                closeTo(14 * 1.15, 0.0001),
              );
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

  testWidgets('会动的玻璃不创建采样层,停稳后才采样', (tester) async {
    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          settled: 0,
          child: Text('moving'),
        ),
      ),
    );
    expect(find.byType(BackdropFilter), findsNothing);

    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          settled: .999,
          child: Text('moving'),
        ),
      ),
    );
    expect(find.byType(BackdropFilter), findsNothing);

    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          settled: 1,
          child: Text('settled'),
        ),
      ),
    );
    expect(find.byType(BackdropFilter), findsOneWidget);
  });

  testWidgets('card 可以被强制开模糊,但契约光学参数仍为零', (tester) async {
    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.card,
          blur: true,
          shaderEnabled: false,
          child: Text('forced'),
        ),
      ),
    );
    expect(find.byType(BackdropFilter), findsOneWidget);
    final style = buildSurfaces(
      MaeveColors.dark(deriveAccent(MaeveAccentPreset.reverie)),
    ).card;
    expect(style.blurSigma, 0);
    expect(style.fallbackBlurSigma, 0);
    expect(style.refractionStrength, 0);
    expect(style.chromaticAberration, 0);
    expect(style.specularStrength, 0);
  });

  testWidgets('shader 不支持时回退原生 blur+tone', (tester) async {
    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          shaderEnabled: false,
          child: Text('fallback'),
        ),
      ),
    );
    expect(find.byType(BackdropFilter), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('unframed chrome 保留边/斜带/顶光,只省略外阴影', (tester) async {
    await tester.pumpWidget(
      host(
        const MaeveGlassSurface(
          tier: MaeveGlassTier.chrome,
          framed: false,
          shaderEnabled: false,
          child: SizedBox(width: 120, height: 40),
        ),
      ),
    );
    final paints = tester.widgetList<CustomPaint>(find.byType(CustomPaint));
    expect(paints.where((p) => p.foregroundPainter != null), isNotEmpty);
    expect(paints.where((p) => p.painter != null), isNotEmpty);
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
      expect(style.fallbackBlurSigma, 0);
      expect(style.refractionStrength, 0);
      expect(style.refractionWidth, 0);
      expect(style.chromaticAberration, 0);
      expect(style.specularStrength, 0);
      expect(style.fallbackFill.a, greaterThanOrEqualTo(.98));
    }
  });

  testWidgets('玻璃圆钮与文字钮:Pressable 按压反馈(缩放回弹)', (tester) async {
    await tester.pumpWidget(
      host(
        Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            MaeveGlassButton(
              icon: Icons.add_rounded,
              tooltip: '新对话',
              haptic: MaeveHaptic.light,
              onPressed: () {},
            ),
            MaeveGlassTextButton(
              label: '停止任务',
              icon: Icons.stop_circle_outlined,
              haptic: MaeveHaptic.light,
              onPressed: () {},
            ),
          ],
        ),
      ),
    );
    // 两种按钮都接 Pressable:触点径向流光 + 内容缩放,不是干巴巴的 InkWell。
    expect(
      find.descendant(
        of: find.byType(MaeveGlassButton),
        matching: find.byType(Pressable),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(
        of: find.byType(MaeveGlassTextButton),
        matching: find.byType(Pressable),
      ),
      findsOneWidget,
    );
    final scaleFinder = find.descendant(
      of: find.byType(MaeveGlassButton),
      matching: find.byType(AnimatedScale),
    );
    final gesture = await tester.startGesture(
      tester.getCenter(find.byType(MaeveGlassButton)),
    );
    await tester.pump(const Duration(milliseconds: 150));
    expect(
      tester.widget<AnimatedScale>(scaleFinder).scale,
      MaeveMotion.pressScale,
    );
    await gesture.up();
    await tester.pumpAndSettle();
    expect(tester.widget<AnimatedScale>(scaleFinder).scale, 1);
  });

  testWidgets('筛选 chip:选中态有勾选标记与语义选中', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(
      host(
        MaeveGlassTextButton(
          label: '全部',
          compact: true,
          selected: true,
          prominent: true,
          onPressed: () {},
        ),
      ),
    );
    // 色彩不是状态唯一线索:勾选标记 + Semantics.selected。
    expect(find.byIcon(Icons.check_rounded), findsOneWidget);
    final node = tester.getSemantics(find.byType(MaeveGlassTextButton));
    expect(node.flagsCollection.isSelected, ui.Tristate.isTrue);
    expect(node.flagsCollection.isButton, isTrue);
    handle.dispose();
  });

  testWidgets('玻璃对话框:居中限宽不铺满,遮罩可点关,有 alert 语义', (tester) async {
    final handle = tester.ensureSemantics();
    // 与真实应用同构:MaeveThemeScope 在 MaterialApp.builder 里包住 Navigator,
    // 对话框路由挂在根 Navigator 上,也必须能拿到设计系统。
    await tester.pumpWidget(
      MaterialApp(
        theme: maeveTheme(brightness: Brightness.dark),
        builder: (context, child) => MaeveThemeScope(
          brightness: Brightness.dark,
          accent: deriveAccent(MaeveAccentPreset.reverie),
          child: child ?? const SizedBox.shrink(),
        ),
        home: Scaffold(
          body: Center(
            child: Builder(
              builder: (context) => ElevatedButton(
                onPressed: () => showDialog<bool>(
                  context: context,
                  builder: (context) => MaeveGlassDialog(
                    title: const Text('删除这段对话？'),
                    content: const Text('删除后无法恢复。'),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context, false),
                        child: const Text('取消'),
                      ),
                    ],
                  ),
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    // Flutter 3.47 的 showDialog 默认铺满窗口:显式 Dialog 参数必须压过它。
    final rect = tester.getRect(find.byType(MaeveGlassSurface));
    final screen = tester.view.physicalSize / tester.view.devicePixelRatio;
    expect(rect.width, lessThanOrEqualTo(560));
    expect(rect.height, lessThan(screen.height));
    expect(rect.top, greaterThan(0));
    expect((rect.center.dy - screen.height / 2).abs(), lessThan(2));
    final node = tester.getSemantics(find.byType(MaeveGlassDialog));
    expect(node.flagsCollection.namesRoute, isTrue);
    // 居中卡片留出遮罩:点"外面"能关。
    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();
    expect(find.byType(MaeveGlassDialog), findsNothing);
    handle.dispose();
  });

  test('balanced optics mirror shared/glass_tokens.json', () {
    for (final brightness in Brightness.values) {
      final dark = brightness == Brightness.dark;
      final colors = MaeveColors.light(deriveAccent(MaeveAccentPreset.reverie));
      final surfaces = buildSurfaces(
        dark
            ? MaeveColors.dark(deriveAccent(MaeveAccentPreset.reverie))
            : colors,
      );
      final chrome = surfaces.chrome;
      final sheet = surfaces.sheet;
      expect(chrome.fill.a, closeTo(dark ? .14 : .10, 0.0001));
      expect(sheet.fill.a, closeTo(dark ? .18 : .14, 0.0001));
      expect(chrome.blurSigma, 3);
      expect(chrome.fallbackBlurSigma, 6);
      expect(chrome.saturation, 1.28);
      expect(chrome.refractionStrength, 14);
      expect(chrome.refractionWidth, 16);
      expect(chrome.chromaticAberration, .65);
      expect(chrome.specularStrength, .55);
      expect(sheet.blurSigma, 4);
      expect(sheet.fallbackBlurSigma, 8);
      expect(sheet.saturation, 1.34);
      expect(sheet.refractionStrength, 18);
      expect(sheet.refractionWidth, 20);
      expect(sheet.chromaticAberration, .85);
      expect(sheet.specularStrength, .65);
      expect(chrome.brightness, dark ? .94 : 1.04);
      expect(chrome.contrast, 1.02);
      expect(chrome.rim.a, closeTo(dark ? .32 : .55, 0.0001));
      expect(chrome.sheen.a, closeTo(dark ? .06 : .14, 0.0001));
      expect(chrome.catchLight.a, closeTo(dark ? .36 : .65, 0.0001));
      expect(chrome.bevelTop.a, closeTo(dark ? .14 : .30, 0.0001));
      expect(chrome.bevelSide.a, closeTo(dark ? .06 : .14, 0.0001));
      expect(chrome.bevelBase.a, closeTo(dark ? .12 : .06, 0.0001));
      expect(sheet.rim.a, closeTo(dark ? .36 : .62, 0.0001));
      expect(sheet.catchLight.a, closeTo(dark ? .40 : .70, 0.0001));
      expect(sheet.bevelTop.a, closeTo(dark ? .16 : .34, 0.0001));
      expect(sheet.bevelSide.a, closeTo(dark ? .07 : .16, 0.0001));
      expect(sheet.bevelBase.a, closeTo(dark ? .14 : .07, 0.0001));
    }
  });

  test('density scales tint blur saturation light and refraction', () {
    final colors = MaeveColors.dark(deriveAccent(MaeveAccentPreset.reverie));
    final clear = buildSurfaces(
      colors,
      density: MaeveGlassDensity.clear,
    ).chrome;
    final dense = buildSurfaces(
      colors,
      density: MaeveGlassDensity.dense,
    ).chrome;
    expect(clear.fill.a, closeTo(.14 * .45, 0.0001));
    expect(clear.blurSigma, closeTo(3 * .70, 0.0001));
    expect(clear.saturation, closeTo(1.28 * .90, 0.0001));
    expect(clear.refractionStrength, closeTo(14 * .85, 0.0001));
    expect(clear.refractionWidth, closeTo(16 * .85, 0.0001));
    expect(clear.chromaticAberration, closeTo(.65 * .85, 0.0001));
    expect(clear.specularStrength, closeTo(.55 * .60, 0.0001));
    expect(dense.fill.a, closeTo(.14 * 1.45, 0.0001));
    expect(dense.blurSigma, closeTo(3 * 1.35, 0.0001));
    expect(dense.saturation, closeTo(1.28 * 1.10, 0.0001));
    expect(dense.refractionStrength, closeTo(14 * 1.15, 0.0001));
    expect(dense.refractionWidth, closeTo(16 * 1.15, 0.0001));
    expect(dense.chromaticAberration, closeTo(.65 * 1.15, 0.0001));
    expect(dense.specularStrength, closeTo(.55 * 1.40, 0.0001));
  });

  test('card and pill never sample by default', () {
    final surfaces = buildSurfaces(
      MaeveColors.light(deriveAccent(MaeveAccentPreset.reverie)),
    );
    for (final style in [surfaces.card, surfaces.pill]) {
      expect(style.blurs, isFalse);
      expect(style.blurSigma, 0);
      expect(style.fallbackBlurSigma, 0);
      expect(style.refractionStrength, 0);
      expect(style.refractionWidth, 0);
      expect(style.chromaticAberration, 0);
      expect(style.specularStrength, 0);
    }
  });

  test('copyWith and equality include every optical field', () {
    final style = buildSurfaces(
      MaeveColors.dark(deriveAccent(MaeveAccentPreset.reverie)),
    ).chrome;
    final copied = style.copyWith(
      fallbackBlurSigma: style.fallbackBlurSigma,
      refractionStrength: style.refractionStrength,
      refractionWidth: style.refractionWidth,
      chromaticAberration: style.chromaticAberration,
      specularStrength: style.specularStrength,
    );
    expect(copied, style);
    expect(copied.hashCode, style.hashCode);
    expect(copied.copyWith(refractionStrength: 99), isNot(style));
    expect(copied.copyWith(chromaticAberration: 99), isNot(style));
    expect(copied.copyWith(specularStrength: 99), isNot(style));
  });
}

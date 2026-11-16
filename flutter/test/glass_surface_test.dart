import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show RenderRepaintBoundary;
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

  testWidgets('浅色清透与均衡:玻璃像素可辨(薄烟对白洗)', (tester) async {
    // 奶白画布上纯缩放透明度不可辨(真机量过 ≤6/765):清透掺 5% 墨后,
    // 与均衡的像素差必须肉眼可读。这里在测试渲染器里锁住这条底线。
    Future<List<int>> capture(MaeveGlassDensity density) async {
      final key = GlobalKey();
      await tester.pumpWidget(
        MaterialApp(
          theme: maeveTheme(brightness: Brightness.light),
          home: MaeveThemeScope(
            brightness: Brightness.light,
            accent: deriveAccent(MaeveAccentPreset.reverie),
            glassDensity: density,
            child: Builder(
              builder: (context) {
                final c = MaeveTheme.of(context).colors;
                return Scaffold(
                  backgroundColor: c.bg,
                  body: Center(
                    child: RepaintBoundary(
                      key: key,
                      child: const MaeveGlassSurface(
                        tier: MaeveGlassTier.chrome,
                        child: SizedBox(width: 200, height: 60),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final boundary =
          key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      late List<int> bytes;
      await tester.runAsync(() async {
        final image = await boundary.toImage(pixelRatio: 1);
        final data = await image.toByteData(format: ui.ImageByteFormat.rawRgba);
        bytes = data!.buffer.asUint8List().toList();
        image.dispose();
      });
      return bytes;
    }

    final clear = await capture(MaeveGlassDensity.clear);
    final balanced = await capture(MaeveGlassDensity.balanced);
    expect(clear.length, balanced.length);
    var sum = 0;
    for (var i = 0; i < clear.length; i += 4) {
      sum +=
          (clear[i] - balanced[i]).abs() +
          (clear[i + 1] - balanced[i + 1]).abs() +
          (clear[i + 2] - balanced[i + 2]).abs();
    }
    final meanDiff = sum / (clear.length / 4);
    expect(meanDiff, greaterThanOrEqualTo(12), reason: '清透↔均衡 $meanDiff/765');
  });
}

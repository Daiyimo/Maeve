import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import 'maeve_colors.dart';
import 'maeve_theme.dart';

/// Four optical densities. Chrome and sheets blur the content beneath them;
/// cards and pills share the directional highlight without per-row sampling.
enum MaeveGlassTier { chrome, sheet, card, pill }

/// 一种表面材质的全部参数。
@immutable
class MaeveGlassStyle {
  const MaeveGlassStyle({
    required this.fill,
    required this.fallbackFill,
    required this.hairline,
    required this.rim,
    required this.sheen,
    this.sheenStop = .55,
    this.blurSigma = 0,
    this.saturation = 1,
    this.hairlineWidth = .75,
    this.rimFraction = .30,
    this.shadows = const [],
  });

  /// 开模糊时的填充(带透明度)。
  final Color fill;

  /// 关模糊时的填充:必须接近不透明,否则文字会飘在背后的内容上。
  final Color fallbackFill;

  /// 发丝描边:界定玻璃边缘。
  final Color hairline;
  final double hairlineWidth;

  /// 顶边高光:光从上方打在玻璃边上,往里渐隐。
  final Color rim;

  /// 顶边高光最多占元素高度的比例 —— 28dp 的药丸上不该整块都是高光。
  final double rimFraction;

  /// 玻璃内部自上而下的一道提亮。
  ///
  /// 这是这一版和上一版最大的差别:上一版玻璃只有"半透明填充 + 一圈描边",
  /// 看起来像一块灰色塑料。真实玻璃是被**上方光源照到**的 —— 顶边亮、往下收,
  /// 于是表面有了方向感,相邻两块玻璃即使填充相同也能靠光读出前后。
  final Color sheen;

  /// 提亮收到透明的位置(占高度的比例)。
  final double sheenStop;

  /// 高斯模糊 σ(逻辑像素)。0 = 不模糊。
  final double blurSigma;

  /// 模糊后的饱和度增益(iOS 式透色)。
  final double saturation;

  /// 只画在形状**外部**的投影(玻璃内部不被阴影弄脏)。
  final List<BoxShadow> shadows;

  bool get blurs => blurSigma > 0;
}

/// 四档表面。
@immutable
class MaeveSurfaces {
  const MaeveSurfaces({
    required this.chrome,
    required this.sheet,
    required this.card,
    required this.pill,
    required this.blurEnabled,
  });

  final MaeveGlassStyle chrome;
  final MaeveGlassStyle sheet;
  final MaeveGlassStyle card;
  final MaeveGlassStyle pill;

  /// 总开关:关掉后所有层级走 fallbackFill,不再创建模糊层。
  final bool blurEnabled;

  MaeveGlassStyle tier(MaeveGlassTier tier) => switch (tier) {
    MaeveGlassTier.chrome => chrome,
    MaeveGlassTier.sheet => sheet,
    MaeveGlassTier.card => card,
    MaeveGlassTier.pill => pill,
  };
}

/// 由颜色和模糊开关出四档表面。数值与 Compose 侧 `buildSurfaces` 逐项一致。
///
/// 唯一一处刻意的偏差:降级填充(`fallbackFill`)按"玻璃色压在画布上再取不透明"
/// 算,而不是把半透明填充的 alpha 直接改成 0.97。后者在深色下会把 8% 白的表面
/// 变成 97% 白 —— 一关模糊,顶栏就白成一块塑料。降级只是"不透明",不是"变白"。
///
/// 投影统一**暖阴影**(浅色下 [MaeveColors.floatShadow] 是暖褐色):中性灰阴影
/// 压在奶色纸上会发蓝,整块卡片看起来像贴上去的。
MaeveSurfaces buildSurfaces(MaeveColors colors, {bool blurEnabled = true}) {
  Color opaque(Color fill) => Color.alphaBlend(fill, colors.bg);
  // 深色下投影几乎看不出来,靠发丝线和 sheen 立层次;浅色下投影是主要的层次手段。
  final shadow = colors.floatShadow;
  final dark = colors.isDark;
  return MaeveSurfaces(
    blurEnabled: blurEnabled,
    chrome: MaeveGlassStyle(
      fill: colors.glass,
      fallbackFill: opaque(colors.glass),
      hairline: colors.glassEdge,
      rim: colors.glassRim.withValues(alpha: dark ? .36 : .75),
      sheen: colors.sheen,
      sheenStop: .62,
      blurSigma: blurEnabled ? 24 : 0,
      saturation: 1.40,
      rimFraction: .46,
      shadows: [
        BoxShadow(
          color: shadow.withValues(alpha: dark ? .14 : .04),
          blurRadius: 24,
          offset: const Offset(0, 6),
        ),
      ],
    ),
    sheet: MaeveGlassStyle(
      fill: colors.glassSheet,
      fallbackFill: opaque(colors.glassSheet),
      hairline: colors.glassEdge,
      rim: colors.glassRim,
      sheen: colors.sheen,
      sheenStop: .34,
      blurSigma: blurEnabled ? 30 : 0,
      saturation: 1.55,
      rimFraction: .18,
      shadows: [
        BoxShadow(
          color: shadow.withValues(alpha: dark ? .30 : .12),
          blurRadius: 28,
          offset: const Offset(0, 12),
        ),
      ],
    ),
    card: MaeveGlassStyle(
      fill: colors.glassCard,
      fallbackFill: opaque(colors.glassCard),
      hairline: colors.glassEdge,
      rim: colors.glassRim,
      sheen: colors.sheen,
      sheenStop: .50,
      rimFraction: .40,
    ),
    pill: MaeveGlassStyle(
      fill: colors.glassPill,
      fallbackFill: opaque(colors.glassPill),
      hairline: colors.glassEdge,
      rim: colors.glassRim,
      sheen: colors.sheen,
      sheenStop: .50,
      rimFraction: .50,
    ),
  );
}

/// 全屏页面背景:**暖色竖渐变 + 三团极光 + 一盏灯 + 四角暗角**。
///
/// 毛玻璃需要有东西可透:纯色背景模糊之后还是那个纯色,只是糊了一点,看不出是玻璃。
/// 背景因此分成四层,顺序不能换:
///
/// 1. **竖渐变**——顶部略亮、底部略沉,给整屏一个自然的光向。
/// 2. **极光**——三团低饱和色斑(一颗跟主题色、一颗邻近色、一颗固定冷色),
///    给玻璃提供折射原料。玻璃件因此能读出位置感:左上角的卡和右下角的卡透出来的
///    颜色不一样。
/// 3. **光源**[MaeveColors.bloom]——一小团更亮、更集中的光。上一版只有极光,
///    整屏读作"一块脏印子";补上光源之后背景才像"被照亮的房间",不像"染了色"。
/// 4. **暗角**[MaeveColors.vignette]——四角压暗。没有暗角,极光会均匀地糊满整屏,
///    边界消失,纵深也没了。
///
/// **同一主题内所有页共用同一张背景**,切页面时背景不动。
class MaeveBackdrop extends StatelessWidget {
  const MaeveBackdrop({super.key, this.glow = true});

  final bool glow;

  @override
  Widget build(BuildContext context) {
    final colors = MaeveTheme.of(context).colors;
    return CustomPaint(
      painter: _AtmospherePainter(
        top: colors.bgTop,
        mid: colors.bg,
        bottom: colors.bgBottom,
        primary: colors.glowPrimary,
        secondary: colors.glowSecondary,
        tertiary: colors.glowTertiary,
        bloom: colors.bloom,
        vignette: colors.vignette,
        glow: glow,
      ),
      size: Size.infinite,
    );
  }
}

class _AtmospherePainter extends CustomPainter {
  const _AtmospherePainter({
    required this.top,
    required this.mid,
    required this.bottom,
    required this.primary,
    required this.secondary,
    required this.tertiary,
    required this.bloom,
    required this.vignette,
    required this.glow,
  });

  final Color top;
  final Color mid;
  final Color bottom;
  final Color primary;
  final Color secondary;
  final Color tertiary;
  final Color bloom;
  final Color vignette;
  final bool glow;

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final rect = Offset.zero & size;
    canvas.drawRect(
      rect,
      Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [top, mid, bottom],
          stops: const [0, .52, 1],
        ).createShader(rect),
    );
    if (!glow) return;

    final short = math.min(size.width, size.height);
    void haze(Color color, Offset center, double radius) {
      if (color.a <= 0) return;
      canvas.drawRect(
        rect,
        Paint()
          ..shader = ui.Gradient.radial(center, radius, [
            color,
            color.withValues(alpha: 0),
          ]),
      );
    }

    // 极光:左上为主,右侧稍后,底部一团冷色收尾。
    // 半径用 .9 / .7 —— 再大就糊满整屏,底色(暖炭)被彻底盖住,读作"紫灰"。
    haze(primary, Offset(size.width * .12, size.height * .02), short * .92);
    haze(secondary, Offset(size.width * 1.02, size.height * .34), short * .70);
    haze(tertiary, Offset(size.width * .30, size.height * 1.06), short * .90);
    // 灯:偏右上,不压住标题。
    haze(bloom, Offset(size.width * .84, size.height * .05), short * .38);

    // 暗角:从中心到四角,只在最外 30% 里压暗。
    canvas.drawRect(
      rect,
      Paint()
        ..shader = ui.Gradient.radial(
          size.center(Offset.zero),
          math.sqrt(size.width * size.width + size.height * size.height) / 2,
          [
            vignette.withValues(alpha: 0),
            vignette.withValues(alpha: 0),
            vignette,
          ],
          [0, .58, 1],
        ),
    );
  }

  @override
  bool shouldRepaint(_AtmospherePainter old) =>
      old.top != top ||
      old.mid != mid ||
      old.bottom != bottom ||
      old.primary != primary ||
      old.secondary != secondary ||
      old.tertiary != tertiary ||
      old.bloom != bloom ||
      old.vignette != vignette ||
      old.glow != glow;
}

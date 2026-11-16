import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import 'maeve_colors.dart';
import 'maeve_theme.dart';

/// Four optical densities. Chrome and sheets blur the content beneath them;
/// cards and pills share the directional highlight without per-row sampling.
enum MaeveGlassTier { chrome, sheet, card, pill }

/// 液态玻璃的**浓度**(glasswork 的 `--density` / `--m-k`: tint 的厚薄)。
///
/// 同一个材质,三档厚薄:
/// - [clear] 清透:填充更少、模糊更弱、边光更柔 —— 只剩"模糊 + 边 + 影"的净玻璃;
/// - [balanced] 均衡:默认,设计契约的基准数值;
/// - [dense] 浓郁:填充更厚、模糊更强、边光更亮 —— 更接近一块有色的厚玻璃。
///
/// 文字对比度不随浓度掉:每一档的填充都仍然半透明,墨色叠上去依旧过 4.5
/// (见 `test/theme/palette_contrast_test.dart`)。
enum MaeveGlassDensity {
  clear('clear', '清透', 0.0),
  balanced('balanced', '均衡', 0.5),
  dense('dense', '浓郁', 1.0);

  const MaeveGlassDensity(this.id, this.label, this.value);

  /// 持久化与桥接用的 id(原生 prefs 同值)。
  final String id;

  /// 设置页里的中文档位名。
  final String label;

  /// 浓度原值(0 薄 → 1 厚)。
  final double value;

  static MaeveGlassDensity byId(String? id) => values.firstWhere(
    (d) => d.id == id,
    orElse: () => MaeveGlassDensity.balanced,
  );
}

/// 浓度 → (填充, 模糊, 饱和度, 边光) 四项倍率。均衡档恒为 1 —— 默认数值不变。
(double, double, double, double) _densityScale(MaeveGlassDensity density) =>
    switch (density) {
      MaeveGlassDensity.clear => (.45, .70, .90, .60),
      MaeveGlassDensity.balanced => (1, 1, 1, 1),
      MaeveGlassDensity.dense => (1.45, 1.35, 1.10, 1.40),
    };

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
    this.brightness = 1,
    this.contrast = 1,
    this.catchLight = _none,
    this.bevelTop = _none,
    this.bevelSide = _none,
    this.bevelBase = _none,
    this.band = 6,
  });

  static const _none = Color(0x00000000);

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

  // ---------- 液态玻璃:光从哪来、边有多厚 ----------
  //
  // glasswork 的一片玻璃是四条声明:background(色)、backdrop-filter
  // (模糊 + 饱和度 + 亮度 + 对比度)、box-shadow(受光边 + 斜面带 + 外影)、
  // color(墨)。前两条已经在 fill / blurSigma / saturation 里;后两条拆成下面
  // 这几个字段,由 glass_surface.dart 的边光 painter 画出来。

  /// 采样后的亮度增益:浅色 >1(背后的画面被提亮,像光透过净玻璃),
  /// 深色 <1(背后的画面被压暗,像烟色玻璃)。1 = 不动。
  final double brightness;

  /// 采样后的对比度增益。1 = 不动。
  final double contrast;

  /// 定向受光边:左上接住光、右下渐隐(比 [rim] 强,给玻璃一个明确的光向)。
  final Color catchLight;

  /// 顶边内斜带:光打在玻璃上沿,往里几像素渐隐。
  final Color bevelTop;

  /// 左右内斜带:玻璃的"厚度",比顶带弱。
  final Color bevelSide;

  /// 底边内斜带:背光面的一道暗(玻璃压在内容上的那一边)。
  final Color bevelBase;

  /// 斜带宽度(逻辑像素)。带子始终贴在边内几像素 —— 太宽会把背后的颜色洗白。
  final double band;

  bool get blurs => blurSigma > 0;

  /// 复制一份并改几个字段(派生 prominent 按钮材质、按浓度缩放时用)。
  MaeveGlassStyle copyWith({
    Color? fill,
    Color? fallbackFill,
    Color? hairline,
    double? hairlineWidth,
    Color? rim,
    double? rimFraction,
    Color? sheen,
    double? sheenStop,
    double? blurSigma,
    double? saturation,
    List<BoxShadow>? shadows,
    double? brightness,
    double? contrast,
    Color? catchLight,
    Color? bevelTop,
    Color? bevelSide,
    Color? bevelBase,
    double? band,
  }) => MaeveGlassStyle(
    fill: fill ?? this.fill,
    fallbackFill: fallbackFill ?? this.fallbackFill,
    hairline: hairline ?? this.hairline,
    hairlineWidth: hairlineWidth ?? this.hairlineWidth,
    rim: rim ?? this.rim,
    rimFraction: rimFraction ?? this.rimFraction,
    sheen: sheen ?? this.sheen,
    sheenStop: sheenStop ?? this.sheenStop,
    blurSigma: blurSigma ?? this.blurSigma,
    saturation: saturation ?? this.saturation,
    shadows: shadows ?? this.shadows,
    brightness: brightness ?? this.brightness,
    contrast: contrast ?? this.contrast,
    catchLight: catchLight ?? this.catchLight,
    bevelTop: bevelTop ?? this.bevelTop,
    bevelSide: bevelSide ?? this.bevelSide,
    bevelBase: bevelBase ?? this.bevelBase,
    band: band ?? this.band,
  );
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
    this.density = MaeveGlassDensity.balanced,
  });

  final MaeveGlassStyle chrome;
  final MaeveGlassStyle sheet;
  final MaeveGlassStyle card;
  final MaeveGlassStyle pill;

  /// 总开关:关掉后所有层级走 fallbackFill,不再创建模糊层。
  final bool blurEnabled;

  /// 当前浓度(设置页"液态玻璃"档位)。
  final MaeveGlassDensity density;

  MaeveGlassStyle tier(MaeveGlassTier tier) => switch (tier) {
    MaeveGlassTier.chrome => chrome,
    MaeveGlassTier.sheet => sheet,
    MaeveGlassTier.card => card,
    MaeveGlassTier.pill => pill,
  };
}

/// 由颜色、模糊开关与**浓度**出四档表面。均衡档数值与 Compose 侧
/// `buildSurfaces` 逐项一致。
///
/// 唯一一处刻意的偏差:降级填充(`fallbackFill`)按"玻璃色压在画布上再取不透明"
/// 算,而不是把半透明填充的 alpha 直接改成 0.97。后者在深色下会把 8% 白的表面
/// 变成 97% 白 —— 一关模糊,顶栏就白成一块塑料。降级只是"不透明",不是"变白"。
///
/// 投影统一**暖阴影**(浅色下 [MaeveColors.floatShadow] 是暖褐色):中性灰阴影
/// 压在奶色纸上会发蓝,整块卡片看起来像贴上去的。
///
/// [density] 同时缩放四件东西:填充的厚薄、模糊的 σ、饱和度增益、边光与斜带的
/// 亮度(见 [_densityScale])。均衡档 = 1,所以默认外观不变。
MaeveSurfaces buildSurfaces(
  MaeveColors colors, {
  bool blurEnabled = true,
  MaeveGlassDensity density = MaeveGlassDensity.balanced,
}) {
  Color opaque(Color fill) => Color.alphaBlend(fill, colors.bg);
  // 深色下投影几乎看不出来,靠发丝线和 sheen 立层次;浅色下投影是主要的层次手段。
  final shadow = colors.floatShadow;
  final dark = colors.isDark;
  final (tintK, blurK, satK, lightK) = _densityScale(density);

  /// 填充按浓度调厚薄(仍然半透明 —— 不透明就永远成不了玻璃)。
  ///
  /// **浓郁不是"更白的白"**:浅色画布本身就是奶白,白玻璃再浓也只是一片更白的白,
  /// 三档因此看不出区别(真机量过:像素只差 8/765)。glasswork 的厚玻璃是**烟色**
  /// 的(深色材质 #1e1e22),所以浓郁档掺一点墨色 —— 读作"更厚的一块有色玻璃",
  /// 而不是"更亮的一片白"。深色画布上玻璃本就是中间调,直接提高不透明度就够。
  Color fill(Color base) {
    final scaled = base.withValues(alpha: (base.a * tintK).clamp(0, .92));
    if (density != MaeveGlassDensity.dense || dark) return scaled;
    return Color.alphaBlend(colors.ink.withValues(alpha: .12), scaled);
  }

  /// 边光 / 斜带按浓度调亮度。
  Color light(Color base, double alpha) =>
      base.withValues(alpha: (alpha * lightK).clamp(0, 1));
  double sigma(double base) => blurEnabled ? base * blurK : 0;
  double sat(double base) => base * satK;

  // 液态玻璃的"光":浅色玻璃把背后的画面提亮(光透过净玻璃),深色玻璃压暗
  // (烟色玻璃)。受光边在左上,右下渐隐;斜带贴在边内几像素,读出玻璃的厚度。
  // 高光一律纯白:暖色高光会让玻璃发黄,读作"旧"(与 MaeveColors 同约定)。
  const white = Color(0xFFFFFFFF);
  final brightness = dark ? .92 : 1.06;
  final catchLight = light(white, dark ? .34 : .80);
  final bevelTop = light(white, dark ? .16 : .42);
  final bevelSide = light(white, dark ? .07 : .20);
  final bevelBase = light(colors.ink, dark ? .10 : .05);

  return MaeveSurfaces(
    blurEnabled: blurEnabled,
    density: density,
    chrome: MaeveGlassStyle(
      fill: fill(colors.glass),
      fallbackFill: opaque(colors.glass),
      hairline: colors.glassEdge,
      rim: light(colors.glassRim, colors.glassRim.a * (dark ? .65 : 1)),
      sheen: light(colors.sheen, colors.sheen.a),
      sheenStop: .62,
      blurSigma: sigma(24),
      saturation: sat(1.40),
      rimFraction: .46,
      brightness: brightness,
      contrast: 1,
      catchLight: catchLight,
      bevelTop: bevelTop,
      bevelSide: bevelSide,
      bevelBase: bevelBase,
      band: 6,
      shadows: [
        BoxShadow(
          color: shadow.withValues(alpha: dark ? .14 : .04),
          blurRadius: 24,
          offset: const Offset(0, 6),
        ),
      ],
    ),
    sheet: MaeveGlassStyle(
      fill: fill(colors.glassSheet),
      fallbackFill: opaque(colors.glassSheet),
      hairline: colors.glassEdge,
      rim: light(colors.glassRim, colors.glassRim.a * .70),
      sheen: light(colors.sheen, colors.sheen.a),
      sheenStop: .34,
      blurSigma: sigma(30),
      saturation: sat(1.55),
      rimFraction: .18,
      brightness: brightness,
      contrast: 1,
      catchLight: catchLight,
      bevelTop: bevelTop,
      bevelSide: bevelSide,
      bevelBase: bevelBase,
      band: 7,
      shadows: [
        BoxShadow(
          color: shadow.withValues(alpha: dark ? .30 : .12),
          blurRadius: 28,
          offset: const Offset(0, 12),
        ),
      ],
    ),
    card: MaeveGlassStyle(
      fill: fill(colors.glassCard),
      fallbackFill: opaque(colors.glassCard),
      hairline: colors.glassEdge,
      rim: light(colors.glassRim, colors.glassRim.a * .38),
      sheen: light(colors.sheen, colors.sheen.a),
      sheenStop: .50,
      rimFraction: .40,
      brightness: brightness,
      contrast: 1,
      catchLight: catchLight,
      bevelTop: bevelTop,
      bevelSide: bevelSide,
      bevelBase: bevelBase,
      band: 5,
    ),
    pill: MaeveGlassStyle(
      fill: fill(colors.glassPill),
      fallbackFill: opaque(colors.glassPill),
      hairline: colors.glassEdge,
      rim: light(colors.glassRim, colors.glassRim.a * .45),
      sheen: light(colors.sheen, colors.sheen.a),
      sheenStop: .50,
      rimFraction: .50,
      brightness: brightness,
      contrast: 1,
      catchLight: catchLight,
      bevelTop: bevelTop,
      bevelSide: bevelSide,
      bevelBase: bevelBase,
      band: 5,
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

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
/// - [clear] 清透:填充更少、模糊更弱、边光更柔;浅色下掺 5% 墨色,读作
///   "极淡烟色的薄玻璃"——奶白画布上白对白,纯降透明度肉眼不可辨;
/// - [balanced] 均衡:默认,设计契约的基准数值;
/// - [dense] 浓郁:填充更厚、模糊更强、边光更亮,浅色下掺 12% 墨色——
///   一块有色的厚玻璃。
///
/// 文字对比度不随浓度掉:每一档的填充都仍然半透明,墨色叠上去依旧过 4.5
/// (见 `test/theme/palette_contrast_test.dart`)。
enum MaeveGlassDensity {
  clear('clear', '清透'),
  balanced('balanced', '均衡'),
  dense('dense', '浓郁');

  const MaeveGlassDensity(this.id, this.label);

  /// 持久化与桥接用的 id(原生 prefs 同值)。
  final String id;

  /// 设置页里的中文档位名。
  final String label;

  static MaeveGlassDensity byId(String? id) => values.firstWhere(
    (d) => d.id == id,
    orElse: () => MaeveGlassDensity.balanced,
  );
}

/// Dart mirror of shared/glass_tokens.json. Keep these values in sync
/// with the native/Compose contract; do not tune them independently.
class _DensityTokens {
  const _DensityTokens({
    required this.tint,
    required this.blur,
    required this.saturation,
    required this.light,
    required this.refraction,
  });

  final double tint;
  final double blur;
  final double saturation;
  final double light;
  final double refraction;
}

const _clearDensity = _DensityTokens(
  tint: .45,
  blur: .70,
  saturation: .90,
  light: .60,
  refraction: .85,
);
const _balancedDensity = _DensityTokens(
  tint: 1,
  blur: 1,
  saturation: 1,
  light: 1,
  refraction: 1,
);
const _denseDensity = _DensityTokens(
  tint: 1.45,
  blur: 1.35,
  saturation: 1.10,
  light: 1.40,
  refraction: 1.15,
);

class _TierTokens {
  const _TierTokens({
    required this.fillLight,
    required this.fillDark,
    required this.blurSigma,
    required this.fallbackBlurSigma,
    required this.saturation,
    required this.refractionStrength,
    required this.refractionWidth,
    required this.chromaticAberration,
    required this.specularStrength,
    required this.hairlineLight,
    required this.hairlineDark,
    required this.rimLight,
    required this.rimDark,
    required this.sheenLight,
    required this.sheenDark,
    required this.rimFraction,
    required this.sheenStop,
    required this.band,
    required this.catchLightLight,
    required this.catchLightDark,
    required this.bevelTopLight,
    required this.bevelTopDark,
    required this.bevelSideLight,
    required this.bevelSideDark,
    required this.bevelBaseLight,
    required this.bevelBaseDark,
    required this.shadowBlur,
    required this.shadowOffsetY,
    required this.shadowLight,
    required this.shadowDark,
  });

  final double fillLight;
  final double fillDark;
  final double blurSigma;
  final double fallbackBlurSigma;
  final double saturation;
  final double refractionStrength;
  final double refractionWidth;
  final double chromaticAberration;
  final double specularStrength;
  final double hairlineLight;
  final double hairlineDark;
  final double rimLight;
  final double rimDark;
  final double sheenLight;
  final double sheenDark;
  final double rimFraction;
  final double sheenStop;
  final double band;
  final double catchLightLight;
  final double catchLightDark;
  final double bevelTopLight;
  final double bevelTopDark;
  final double bevelSideLight;
  final double bevelSideDark;
  final double bevelBaseLight;
  final double bevelBaseDark;
  final double shadowBlur;
  final double shadowOffsetY;
  final double shadowLight;
  final double shadowDark;
}

const _chromeTokens = _TierTokens(
  fillLight: .10,
  fillDark: .14,
  blurSigma: 3,
  fallbackBlurSigma: 6,
  saturation: 1.28,
  refractionStrength: 14,
  refractionWidth: 16,
  chromaticAberration: .65,
  specularStrength: .55,
  hairlineLight: .14,
  hairlineDark: .20,
  rimLight: .55,
  rimDark: .32,
  sheenLight: .14,
  sheenDark: .06,
  rimFraction: .46,
  sheenStop: .62,
  band: 6,
  catchLightLight: .65,
  catchLightDark: .36,
  bevelTopLight: .30,
  bevelTopDark: .14,
  bevelSideLight: .14,
  bevelSideDark: .06,
  bevelBaseLight: .06,
  bevelBaseDark: .12,
  shadowBlur: 24,
  shadowOffsetY: 6,
  shadowLight: .08,
  shadowDark: .18,
);

const _sheetTokens = _TierTokens(
  fillLight: .14,
  fillDark: .18,
  blurSigma: 4,
  fallbackBlurSigma: 8,
  saturation: 1.34,
  refractionStrength: 18,
  refractionWidth: 20,
  chromaticAberration: .85,
  specularStrength: .65,
  hairlineLight: .16,
  hairlineDark: .22,
  rimLight: .62,
  rimDark: .36,
  sheenLight: .16,
  sheenDark: .07,
  rimFraction: .22,
  sheenStop: .34,
  band: 7,
  catchLightLight: .70,
  catchLightDark: .40,
  bevelTopLight: .34,
  bevelTopDark: .16,
  bevelSideLight: .16,
  bevelSideDark: .07,
  bevelBaseLight: .07,
  bevelBaseDark: .14,
  shadowBlur: 28,
  shadowOffsetY: 12,
  shadowLight: .14,
  shadowDark: .26,
);

const _cardTokens = _TierTokens(
  fillLight: .12,
  fillDark: .16,
  blurSigma: 0,
  fallbackBlurSigma: 0,
  saturation: 1,
  refractionStrength: 0,
  refractionWidth: 0,
  chromaticAberration: 0,
  specularStrength: 0,
  hairlineLight: .12,
  hairlineDark: .18,
  rimLight: .38,
  rimDark: .24,
  sheenLight: .10,
  sheenDark: .05,
  rimFraction: .40,
  sheenStop: .50,
  band: 5,
  catchLightLight: .48,
  catchLightDark: .28,
  bevelTopLight: .22,
  bevelTopDark: .12,
  bevelSideLight: .10,
  bevelSideDark: .05,
  bevelBaseLight: .05,
  bevelBaseDark: .10,
  shadowBlur: 14,
  shadowOffsetY: 3,
  shadowLight: .05,
  shadowDark: .10,
);

const _pillTokens = _TierTokens(
  fillLight: .16,
  fillDark: .20,
  blurSigma: 0,
  fallbackBlurSigma: 0,
  saturation: 1,
  refractionStrength: 0,
  refractionWidth: 0,
  chromaticAberration: 0,
  specularStrength: 0,
  hairlineLight: .14,
  hairlineDark: .20,
  rimLight: .45,
  rimDark: .28,
  sheenLight: .12,
  sheenDark: .05,
  rimFraction: .50,
  sheenStop: .50,
  band: 5,
  catchLightLight: .52,
  catchLightDark: .30,
  bevelTopLight: .24,
  bevelTopDark: .12,
  bevelSideLight: .11,
  bevelSideDark: .05,
  bevelBaseLight: .05,
  bevelBaseDark: .10,
  shadowBlur: 0,
  shadowOffsetY: 0,
  shadowLight: 0,
  shadowDark: 0,
);

_DensityTokens _densityScale(MaeveGlassDensity density) => switch (density) {
  MaeveGlassDensity.clear => _clearDensity,
  MaeveGlassDensity.balanced => _balancedDensity,
  MaeveGlassDensity.dense => _denseDensity,
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
    this.fallbackBlurSigma = 0,
    this.saturation = 1,
    this.refractionStrength = 0,
    this.refractionWidth = 0,
    this.chromaticAberration = 0,
    this.specularStrength = 0,
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

  /// Shader 不可用时的原生模糊 σ(逻辑像素)。
  final double fallbackBlurSigma;

  /// 模糊后的饱和度增益(iOS 式透色)。
  final double saturation;

  /// 边缘折射位移(逻辑像素)。
  final double refractionStrength;

  /// 边缘折射带宽度(逻辑像素)。
  final double refractionWidth;

  /// RGB 色散强度(逻辑像素)。
  final double chromaticAberration;

  /// Fresnel/镜面高光强度。
  final double specularStrength;

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
    double? fallbackBlurSigma,
    double? saturation,
    double? refractionStrength,
    double? refractionWidth,
    double? chromaticAberration,
    double? specularStrength,
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
    fallbackBlurSigma: fallbackBlurSigma ?? this.fallbackBlurSigma,
    saturation: saturation ?? this.saturation,
    refractionStrength: refractionStrength ?? this.refractionStrength,
    refractionWidth: refractionWidth ?? this.refractionWidth,
    chromaticAberration: chromaticAberration ?? this.chromaticAberration,
    specularStrength: specularStrength ?? this.specularStrength,
    shadows: shadows ?? this.shadows,
    brightness: brightness ?? this.brightness,
    contrast: contrast ?? this.contrast,
    catchLight: catchLight ?? this.catchLight,
    bevelTop: bevelTop ?? this.bevelTop,
    bevelSide: bevelSide ?? this.bevelSide,
    bevelBase: bevelBase ?? this.bevelBase,
    band: band ?? this.band,
  );

  /// 值相等:painter 的 shouldRepaint 靠它判断要不要重绘。主题每次重建都出
  /// 新实例,没有值相等就退化成"每次必重绘"(Compose 侧 GlassStyle 是 data class)。
  @override
  bool operator ==(Object other) =>
      other is MaeveGlassStyle &&
      other.fill == fill &&
      other.fallbackFill == fallbackFill &&
      other.hairline == hairline &&
      other.hairlineWidth == hairlineWidth &&
      other.rim == rim &&
      other.rimFraction == rimFraction &&
      other.sheen == sheen &&
      other.sheenStop == sheenStop &&
      other.blurSigma == blurSigma &&
      other.fallbackBlurSigma == fallbackBlurSigma &&
      other.saturation == saturation &&
      other.refractionStrength == refractionStrength &&
      other.refractionWidth == refractionWidth &&
      other.chromaticAberration == chromaticAberration &&
      other.specularStrength == specularStrength &&
      other.brightness == brightness &&
      other.contrast == contrast &&
      other.catchLight == catchLight &&
      other.bevelTop == bevelTop &&
      other.bevelSide == bevelSide &&
      other.bevelBase == bevelBase &&
      other.band == band &&
      _sameShadows(other.shadows, shadows);

  static bool _sameShadows(List<BoxShadow> a, List<BoxShadow> b) {
    if (identical(a, b)) return true;
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(
    Object.hash(
      fill,
      fallbackFill,
      hairline,
      hairlineWidth,
      rim,
      rimFraction,
      sheen,
      sheenStop,
      blurSigma,
      fallbackBlurSigma,
      saturation,
      refractionStrength,
      refractionWidth,
      chromaticAberration,
      specularStrength,
    ),
    Object.hash(
      brightness,
      contrast,
      catchLight,
      bevelTop,
      bevelSide,
      bevelBase,
      band,
    ),
    Object.hashAll(shadows),
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

/// Builds the four Flutter tiers from the shared optical contract.
/// Density scales tint, blur, saturation, light and refraction independently.
MaeveSurfaces buildSurfaces(
  MaeveColors colors, {
  bool blurEnabled = true,
  MaeveGlassDensity density = MaeveGlassDensity.balanced,
}) {
  Color opaque(Color fill) => Color.alphaBlend(fill, colors.bg);
  final shadow = colors.floatShadow;
  final dark = colors.isDark;
  final scale = _densityScale(density);
  const white = Color(0xFFFFFFFF);

  Color light(Color base, double alpha) =>
      base.withValues(alpha: (alpha * scale.light).clamp(0, 1));

  MaeveGlassStyle style(_TierTokens tokens, Color base) {
    final fill = base.withValues(
      alpha: ((dark ? tokens.fillDark : tokens.fillLight) * scale.tint).clamp(
        0,
        .92,
      ),
    );
    final shadowAlpha = dark ? tokens.shadowDark : tokens.shadowLight;
    return MaeveGlassStyle(
      fill: fill,
      fallbackFill: opaque(fill),
      hairline: colors.glassEdge.withValues(
        alpha: dark ? tokens.hairlineDark : tokens.hairlineLight,
      ),
      rim: light(white, dark ? tokens.rimDark : tokens.rimLight),
      sheen: light(white, dark ? tokens.sheenDark : tokens.sheenLight),
      sheenStop: tokens.sheenStop,
      blurSigma: blurEnabled ? tokens.blurSigma * scale.blur : 0,
      fallbackBlurSigma: blurEnabled
          ? tokens.fallbackBlurSigma * scale.blur
          : 0,
      saturation: tokens.saturation * scale.saturation,
      refractionStrength: blurEnabled
          ? tokens.refractionStrength * scale.refraction
          : 0,
      refractionWidth: blurEnabled
          ? tokens.refractionWidth * scale.refraction
          : 0,
      chromaticAberration: blurEnabled
          ? tokens.chromaticAberration * scale.refraction
          : 0,
      specularStrength: blurEnabled ? tokens.specularStrength * scale.light : 0,
      rimFraction: tokens.rimFraction,
      brightness: dark ? .94 : 1.04,
      contrast: 1.02,
      catchLight: light(
        white,
        dark ? tokens.catchLightDark : tokens.catchLightLight,
      ),
      bevelTop: light(white, dark ? tokens.bevelTopDark : tokens.bevelTopLight),
      bevelSide: light(
        white,
        dark ? tokens.bevelSideDark : tokens.bevelSideLight,
      ),
      bevelBase: light(
        colors.ink,
        dark ? tokens.bevelBaseDark : tokens.bevelBaseLight,
      ),
      band: tokens.band,
      shadows: tokens.shadowBlur <= 0 || shadowAlpha <= 0
          ? const []
          : [
              BoxShadow(
                color: shadow.withValues(alpha: shadowAlpha),
                blurRadius: tokens.shadowBlur,
                offset: Offset(0, tokens.shadowOffsetY),
              ),
            ],
    );
  }

  return MaeveSurfaces(
    blurEnabled: blurEnabled,
    density: density,
    chrome: style(_chromeTokens, colors.glass),
    sheet: style(_sheetTokens, colors.glassSheet),
    card: style(_cardTokens, colors.glassCard),
    pill: style(_pillTokens, colors.glassPill),
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
